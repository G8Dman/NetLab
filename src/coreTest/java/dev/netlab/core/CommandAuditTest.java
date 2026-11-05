package dev.netlab.core;

import java.util.*;

/** All advertised forms have explanations/examples; effects are checked separately from grammar. */
public final class CommandAuditTest {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;int forms=0;
        for(String kind:List.of("router","switch","pc"))for(int mode=0;mode<=8;mode++){
            if(kind.equals("pc")&&mode!=0||kind.equals("router")&&mode==8||kind.equals("switch")&&Set.of(5,6,7).contains(mode))continue;
            for(String protocol:mode==5?List.of("rip","ospf","eigrp"):List.of("")){
                var template=new Device(kind);var context=new Console();context.mode=mode;context.selected=0;context.context=protocol;
                if(mode==5)template.os().routing.put(protocol,new RouterState.Routing(protocol,"1"));
                for(var spec:CommandGrammar.reference(context,template)){
                    var entry=CommandManual.entry(spec.words());check(entry!=null&&!entry.purpose().isBlank(),"manual missing "+spec.words());
                    String args=CommandManual.arguments(spec,context,template);String command=spec.words()+(args.isBlank()?"":" "+args);
                    check(CommandGrammar.resolve(context,template,command).error().isBlank(),"manual example rejected: "+kind+"/"+mode+" "+command);
                    var device=new Device(kind);var n=new Network();n.devices.put(1L,device);var cli=new Console();cli.initialized=true;cli.mode=mode;cli.selected=0;cli.context=mode==8?"10":mode==6?"STUDENTS":mode==7?"STUDENTS":protocol;
                    if(mode==5)device.os().routing.put(protocol,new RouterState.Routing(protocol,"1"));
                    if(mode==6)device.os().pools.put("STUDENTS",new RouterState.Pool());
                    if(mode==7)device.os().acls.put("STUDENTS",new RouterState.Acl("standard"));
                    String output;
                    try{output=cli.execute(device,n,1,command);}catch(RuntimeException e){throw new AssertionError("command crashed: "+kind+"/"+mode+" "+command,e);}
                    check(output!=null,"null result: "+command);forms++;
                    check(!output.contains("Unsupported command")&&!output.contains("Invalid command at")&&!output.contains("Select an interface first"),"advertised command has no handler: "+kind+"/"+mode+" "+command+" => "+output);
                }
            }
        }
        System.out.println("Command audit: "+forms+" advertised device/mode forms, documented examples and execution without crashes (precondition refusals allowed)");
        var n=new Network();var sw=new Device("switch");n.devices.put(1L,sw);var c=new Console();c.initialized=true;c.mode=2;
        check(c.execute(sw,n,1,"mac address-table static 0011.2233.4455 vlan 10 interface gig0/0").isBlank(),"static MAC accepted");
        check(sw.os().staticMacs.containsKey("001122334455 vlan 10"),"static MAC installed");
        c.mode=1;check(c.execute(sw,n,1,"show mac address-table vlan 20").indexOf("STATIC")<0,"static table VLAN filter");
        check(c.execute(sw,n,1,"show interfaces switchport").contains("Access VLAN"),"all-port switchport display works without selection");
        c.mode=2;check(c.execute(sw,n,1,"no mac address-table static 0011.2233.4455 vlan 10 interface gig0/0").isBlank()&&sw.os().staticMacs.isEmpty(),"static MAC removed");
        c.execute(sw,n,1,"interface gig0/0");c.execute(sw,n,1,"switchport mode trunk");c.execute(sw,n,1,"switchport access vlan 10");check(sw.ports.getFirst().trunk,"access VLAN assignment does not change trunk mode");
        sw.ports.getFirst().medium="fiber";sw.ports.getFirst().maxMbps=100;sw.ports.getFirst().hardwareSlot="nm0";c.mode=2;c.execute(sw,n,1,"default interface gig0/0");
        check(sw.ports.getFirst().medium.equals("fiber")&&sw.ports.getFirst().maxMbps==100&&sw.ports.getFirst().hardwareSlot.equals("nm0"),"default interface preserves hardware");
        var pc=new Device("pc");var pcli=new Console();n.devices.put(2L,pc);
        check(CommandGrammar.resolve(pcli,pc,"enable").error().startsWith("%"),"PC does not advertise IOS enable");
        for(String command:List.of("ftp 192.0.2.1","snmpget 192.0.2.1","snmpgetbulk 192.0.2.1","snmpset 192.0.2.1"))check(pcli.execute(pc,n,2,command).contains("not implemented"),"unsupported service cannot report success: "+command);
        check(pcli.execute(pc,n,2,"ssh -l student 192.0.2.1").startsWith("%"),"unreachable PC SSH fails rather than simulating success");
        for(String kind:List.of("router","switch")){
            var remote=new Device(kind);var net=new Network();var host=new Device("pc");net.devices.put(1L,host);net.devices.put(2L,remote);
            host.ports.getFirst().ip="192.168.10.2";host.ports.getFirst().up=true;remote.ports.getFirst().ip="192.168.10.1";remote.ports.getFirst().up=true;
            net.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),4));
            if(kind.equals("switch")){remote.ports.getFirst().ip="";int svi=remote.findInterface("Vlan1",true);remote.ports.get(svi).ip="192.168.10.1";remote.ports.get(svi).up=true;}
            remote.os().rsaPublic="test-public-key";remote.os().users.put("student",ConfigStore.secret("Student-password-123"));remote.os().privileges.put("student",15);
            var vty=RouterCommands.vty(remote);vty.login="local";vty.transport="ssh telnet";
            var terminal=new Console();check(terminal.execute(host,net,1,"ssh -l student 192.168.10.1").contains("Password:"),"PC reaches SSH authentication on "+kind);
            check(terminal.execute(host,net,1,"Student-password-123").contains("Connected")&&terminal.remoteId==2,"PC SSH authenticates to "+kind);
            terminal.execute(host,net,1,"configure terminal");terminal.execute(host,net,1,"hostname REMOTE");check(remote.hostname.equals("REMOTE"),"PC remote command actually changes "+kind);
            terminal=new Console();check(terminal.execute(host,net,1,"telnet 192.168.10.1").contains("Username:"),"PC Telnet prompts for local user on "+kind);terminal.execute(host,net,1,"student");check(terminal.execute(host,net,1,"Student-password-123").contains("Connected")&&terminal.remoteId==2,"PC Telnet authenticates on "+kind);
            host.os().dns.add("192.168.10.1");remote.os().hosts.put("classroom.local","192.168.10.50");
            check(new Console().execute(host,net,1,"nslookup classroom.local").contains("192.168.10.50"),"PC DNS uses reachable configured server on "+kind);
            host.ports.getFirst().up=false;check(new Console().execute(host,net,1,"nslookup classroom.local").startsWith("%"),"unreachable DNS server cannot produce false success");
        }
        return checks;
    }
}
