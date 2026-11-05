package dev.netlab.core;

import java.util.*;

/** Shared EXEC save syntax for both network equipment and addon devices. */
public final class ConfigurationCommands {
    private ConfigurationCommands() {}
    public static boolean isSave(String canonical) {
        if (List.of("write", "write memory", "memory write").contains(canonical)) return true;
        var words=canonical.split("\\s+");
        if(words.length!=3||!words[0].equals("copy"))return false;
        return !words[1].isEmpty()&&"running-config".startsWith(words[1])
            &&!words[2].isEmpty()&&"startup-config".startsWith(words[2]);
    }
    public static String save(Device device) {
        device.os().startup=ConfigStore.snapshot(device);
        return "Building configuration...\n[OK]";
    }
    public static List<CommandGrammar.Spec> specs(Console cli,Device device) {
        if(cli.mode==0||device.kind.equals("pc"))return List.of();
        String prefix=cli.mode>=2?"do ":"";
        boolean vendor=Set.of("router","switch").contains(device.kind);
        var out=new ArrayList<CommandGrammar.Spec>();
        for(String words:List.of("write","write memory","memory write")){
            if(prefix.isEmpty()&&vendor&&words.startsWith("write"))continue;
            out.add(new CommandGrammar.Spec(prefix+words,0,0,""));
        }
        if(cli.mode>=2||!vendor)
            out.add(new CommandGrammar.Spec(prefix+"copy running-config startup-config",0,0,""));
        return out;
    }
}
