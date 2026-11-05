package dev.netlab.core;

import java.util.*;

/** Shared EXEC semantics must also work on equipment supplied by addons. */
public final class ConfigurationSaveTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static int run(){
        checks=0;
        for(String kind:List.of("router","switch","server","endpoint","traffic","mobile_core","mobile_bbu","mobile_rru","mobile_sim_station","sputnik_gateway")){
            var d=new Device(kind);var cli=new Console();var n=new Network();n.devices.put(1L,d);
            check(cli.execute(d,n,1,"write memory").startsWith("%")&&d.os().startup.isEmpty(),kind+": user EXEC cannot save");
            cli.execute(d,n,1,"enable");
            check(cli.complete(d,"wr").value().strip().equals("write"),kind+": Tab completes write");
            check(cli.complete(d,"wr mem").value().strip().equals("write memory"),kind+": Tab completes memory keyword");
            for(String cmd:List.of("write memory","wr","wr mem","WRITE MEMORY","memory write","copy run start")){
                d.hostname="Saved-"+checks;d.extensions.put("test.setting","running");
                check(cli.execute(d,n,1,cmd).contains("[OK]"),kind+": "+cmd);
                var restored=ConfigStore.restore(d.os().startup);
                check(restored.hostname.equals(d.hostname)&&restored.extensions.get("test.setting").equals("running"),kind+": save includes addon configuration");
            }
            String startup=d.os().startup;
            for(String bad:List.of("write banana","write memory extra","memory write extra"))
                check(cli.execute(d,n,1,bad).startsWith("%")&&d.os().startup.equals(startup),kind+": invalid save does not overwrite startup");
            cli.execute(d,n,1,"configure terminal");d.hostname="Do-Saved";
            check(cli.execute(d,n,1,"write memory").startsWith("%"),kind+": bare write is an EXEC command");
            check(cli.execute(d,n,1,"do wr mem").contains("[OK]")&&cli.mode==2,kind+": do write preserves configuration mode");
            check(ConfigStore.restore(d.os().startup).hostname.equals("Do-Saved"),kind+": do saves current configuration");
            check(cli.execute(d,n,1,"do copy run start").contains("[OK]")&&cli.mode==2,kind+": do copy saves current configuration");
        }
        var secured=new Device("router");secured.os().enableSecret=ConfigStore.secret("write memory");
        var cli=new Console();var n=new Network();n.devices.put(1L,secured);cli.execute(secured,n,1,"enable");
        check(cli.pending.equals("enable"),"enable password requested");
        cli.execute(secured,n,1,"write memory");
        check(cli.mode==1&&secured.os().startup.isEmpty(),"password spelling write memory is consumed as a password, never as a save command");
        return checks;
    }
}
