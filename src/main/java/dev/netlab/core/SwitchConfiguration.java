package dev.netlab.core;

import java.util.*;

/** IOS-style display of configured values. Binary snapshots retain all active and inactive settings. */
public final class SwitchConfiguration {
    private SwitchConfiguration(){}
    public static String showRunning(Device d){return RouterCommands.showConfiguration(render(d));}
    public static String render(Device d){
        var os=d.os();var s=new StringBuilder("! NetLab configuration (IOS-like syntax)\nversion ")
            .append(d.hardware!=null&&d.hardware.profile.equals("3650-24PS")?"16.12":"12.2").append('\n');
        if(!os.passwordEncryption)s.append("no service password-encryption\n");
        s.append("!\n").append(RouterCommands.globalConfig(d)).append("!\n");
        if(!os.vtpMode.equals("server"))s.append("vtp mode ").append(os.vtpMode).append('\n');
        if(!os.vtpVersion.equals("1"))s.append("vtp version ").append(os.vtpVersion).append('\n');
        if(!os.vtpDomain.isEmpty())s.append("vtp domain ").append(os.vtpDomain).append('\n');
        if(!os.vtpPassword.isEmpty())s.append("vtp password 7 ").append(os.vtpPassword).append('\n');
        s.append("spanning-tree mode ").append(os.spanningMode).append("\nspanning-tree extend system-id\n");
        os.bridgePriorities.forEach((v,p)->s.append("spanning-tree vlan ").append(v).append(" priority ").append(p).append('\n'));
        if(os.spanningPortfastDefault)s.append("spanning-tree portfast default\n");
        if(os.spanningBpduGuardDefault)s.append("spanning-tree portfast bpduguard default\n");
        s.append("!\n");
        // In VTP server/client normal-range VLANs live in vlan.dat, rather than show run.
        os.vlans.forEach((v,name)->{if(os.vtpMode.equals("transparent")||v>1005){s.append("vlan ").append(v).append('\n');if(!name.equals("VLAN"+v)&&!name.equals(String.format("VLAN%04d",v)))s.append(" name ").append(name).append('\n');s.append("!\n");}});
        for(int i:InterfaceOrder.visible(d)){
            var p=d.ports.get(i);s.append("interface ").append(d.portName(i)).append('\n');
            if(!p.description.isEmpty())s.append(" description ").append(p.description).append('\n');
            if(!p.virtual){
                String mode=p.trunk?"trunk":p.switchMode;if(!mode.equals("dynamic auto"))s.append(" switchport mode ").append(mode).append('\n');
                int access=p.accessVlan!=1?p.accessVlan:p.vlan>0?p.vlan:1;
                int nativeVlan=p.trunkNative!=1?p.trunkNative:p.vlan>0?p.vlan:1;
                if(access!=1)s.append(" switchport access vlan ").append(access).append('\n');
                if(nativeVlan!=1)s.append(" switchport trunk native vlan ").append(nativeVlan).append('\n');
                if(!p.allowedVlans.equals("all"))s.append(" switchport trunk allowed vlan ").append(p.allowedVlans).append('\n');
                if(p.nonegotiate)s.append(" switchport nonegotiate\n");if(p.voiceVlan>0)s.append(" switchport voice vlan ").append(p.voiceVlan).append('\n');
                if(p.channelGroup>0)s.append(" channel-group ").append(p.channelGroup).append(" mode ").append(p.channelMode).append('\n');
                if(p.portSecurity){s.append(" switchport port-security\n");if(p.securityMaximum!=1)s.append(" switchport port-security maximum ").append(p.securityMaximum).append('\n');if(!p.violation.equals("shutdown"))s.append(" switchport port-security violation ").append(p.violation).append('\n');
                    if(!p.secureMac.isEmpty())s.append(" switchport port-security mac-address ").append(p.secureMac).append('\n');
                    if(p.sticky)s.append(" switchport port-security mac-address sticky\n");
                    for(String mac:p.stickyMacs)s.append(" switchport port-security mac-address sticky ").append(mac).append('\n');
                }
                if(p.portfast)s.append(" spanning-tree portfast\n");
                if(!p.bpduGuardMode.equals("default"))s.append(" spanning-tree bpduguard ").append(p.bpduGuardMode).append('\n');
                p.stpCosts.forEach((v,c)->s.append(" spanning-tree vlan ").append(v).append(" cost ").append(c).append('\n'));
                p.stpPriorities.forEach((v,c)->s.append(" spanning-tree vlan ").append(v).append(" port-priority ").append(c).append('\n'));
                if(p.dhcpTrust)s.append(" ip dhcp snooping trust\n");if(p.dhcpRate>0)s.append(" ip dhcp snooping limit rate ").append(p.dhcpRate).append('\n');
            }
            // Keep supported L3/security attributes; suppress only known default/display duplicates.
            for(String line:RouterCommands.interfaceConfig(d,i).split("\n")){
                if(!line.startsWith(" ")||line.startsWith(" description ")||line.startsWith(" switchport "))continue;
                if(line.equals(" no shutdown")||line.equals(" duplex auto")||line.equals(" speed auto"))continue;
                if(line.equals(" no ip address")&&!p.virtual)continue;
                if(line.equals(" bandwidth "+(p.maxMbps>0?p.maxMbps*1000:64)))continue;
                s.append(line).append('\n');
            }
            s.append("!\n");
        }
        if(os.macAging!=300)s.append("mac address-table aging-time ").append(os.macAging).append('\n');
        os.staticMacs.forEach((key,port)->s.append("mac address-table static ").append(key).append(" interface ").append(port).append('\n'));
        if(!os.portChannelLoadBalance.equals("src-mac"))s.append("port-channel load-balance ").append(os.portChannelLoadBalance).append('\n');
        if(os.dhcpSnooping)s.append("ip dhcp snooping\nip dhcp snooping vlan ").append(os.dhcpSnoopingVlans).append('\n');
        if(!os.dhcpSnoopingOption)s.append("no ip dhcp snooping information option\n");
        os.spanSources.forEach((k,v)->s.append("monitor session ").append(k).append(" source interface ").append(v).append('\n'));
        os.spanDestinations.forEach((k,v)->s.append("monitor session ").append(k).append(" destination interface ").append(v).append('\n'));
        String tail=RouterCommands.tailConfig(d,false);s.append(tail.substring(0,tail.length()-"end\n".length()));
        var lines=new LinkedHashMap<String,RouterState.Line>();
        lines.put("console 0",os.lines.getOrDefault("console 0",new RouterState.Line()));
        lines.put("vty 0 4",os.lines.getOrDefault("vty 0 4",new RouterState.Line()));
        lines.put("vty 5 15",os.lines.getOrDefault("vty 5 15",new RouterState.Line()));
        lines.putAll(os.lines);
        lines.forEach((name,line)->appendLine(s,name,line,os.passwordEncryption));
        return s.append("end\n").toString();
    }
    private static void appendLine(StringBuilder s,String name,RouterState.Line line,boolean encrypted){
        s.append("line ").append(name.equals("console 0")?"con 0":name).append('\n');
        if(!line.password.isEmpty())s.append(" password ").append(encrypted?"7 "+line.password:ConfigStore.untype7(line.password)).append('\n');
        if(!line.login.isEmpty())s.append(" login").append(line.login.equals("local")?" local":"").append('\n');
        else if(name.startsWith("vty"))s.append(" no login\n");
        if(line.synchronous)s.append(" logging synchronous\n");
        if(line.timeout!=600)s.append(" exec-timeout ").append(line.timeout/60).append(' ').append(line.timeout%60).append('\n');
        if(name.startsWith("vty")&&!line.transport.equals("telnet"))s.append(" transport input ").append(line.transport).append('\n');
        if(!line.access.isEmpty())s.append(" access-class ").append(line.access).append(" in\n");
        s.append("!\n");
    }
}
