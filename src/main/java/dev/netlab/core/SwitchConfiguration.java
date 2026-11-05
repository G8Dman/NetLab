package dev.netlab.core;

/** Human-readable display; startup/TFTP snapshots retain the entire configuration. */
public final class SwitchConfiguration {
    public static String render(Device d){
        var os=d.os();var s=new StringBuilder(RouterCommands.config(d));
        s.append("vtp mode ").append(os.vtpMode).append("\nvtp version ").append(os.vtpVersion).append('\n');
        if(!os.vtpDomain.isEmpty())s.append("vtp domain ").append(os.vtpDomain).append('\n');
        if(!os.vtpPassword.isEmpty())s.append("vtp password 7 ").append(os.vtpPassword).append('\n');
        s.append("spanning-tree mode ").append(os.spanningMode).append('\n');
        os.bridgePriorities.forEach((v,p)->s.append("spanning-tree vlan ").append(v).append(" priority ").append(p).append('\n'));
        if(os.spanningPortfastDefault)s.append("spanning-tree portfast default\n");
        if(os.spanningBpduGuardDefault)s.append("spanning-tree portfast bpduguard default\n");
        os.vlans.forEach((v,name)->s.append("vlan ").append(v).append("\n name ").append(name).append("\n!\n"));
        for(int i=0;i<d.ports.size();i++){
            var p=d.ports.get(i);if(p.hidden||p.console)continue;s.append("interface ").append(d.portName(i)).append('\n');
            if(!p.virtual){
                s.append(" switchport mode ").append(p.trunk?"trunk":p.switchMode).append('\n');
                s.append(" switchport access vlan ").append(p.accessVlan!=1?p.accessVlan:p.vlan>0?p.vlan:1).append('\n');
                s.append(" switchport trunk native vlan ").append(p.trunkNative).append('\n');
                s.append(" switchport trunk allowed vlan ").append(p.allowedVlans).append('\n');
                if(p.nonegotiate)s.append(" switchport nonegotiate\n");if(p.voiceVlan>0)s.append(" switchport voice vlan ").append(p.voiceVlan).append('\n');
                if(p.channelGroup>0)s.append(" channel-group ").append(p.channelGroup).append(" mode ").append(p.channelMode).append('\n');
                if(p.portSecurity){s.append(" switchport port-security\n switchport port-security maximum ").append(p.securityMaximum).append("\n switchport port-security violation ").append(p.violation).append('\n');
                    if(!p.secureMac.isEmpty())s.append(" switchport port-security mac-address ").append(p.secureMac).append('\n');
                    if(p.sticky)s.append(" switchport port-security mac-address sticky\n");
                    for(String mac:p.stickyMacs)s.append(" switchport port-security mac-address sticky ").append(mac).append('\n');
                }
                if(p.portfast)s.append(" spanning-tree portfast\n");
                if(!p.bpduGuardMode.equals("default"))s.append(" spanning-tree bpduguard ").append(p.bpduGuardMode).append('\n');
                p.stpCosts.forEach((v,c)->s.append(" spanning-tree vlan ").append(v).append(" cost ").append(c).append('\n'));
                p.stpPriorities.forEach((v,c)->s.append(" spanning-tree vlan ").append(v).append(" port-priority ").append(c).append('\n'));
                if(p.dhcpTrust)s.append(" ip dhcp snooping trust\n");if(p.dhcpRate>0)s.append(" ip dhcp snooping limit rate ").append(p.dhcpRate).append('\n');
            }s.append("!\n");
        }
        s.append("mac address-table aging-time ").append(os.macAging).append('\n');
        os.staticMacs.forEach((key,port)->s.append("mac address-table static ").append(key).append(" interface ").append(port).append('\n'));
        if(os.dhcpSnooping)s.append("ip dhcp snooping\nip dhcp snooping vlan ").append(os.dhcpSnoopingVlans).append('\n');
        if(!os.dhcpSnoopingOption)s.append("no ip dhcp snooping information option\n");
        os.spanSources.forEach((k,v)->s.append("monitor session ").append(k).append(" source interface ").append(v).append('\n'));
        os.spanDestinations.forEach((k,v)->s.append("monitor session ").append(k).append(" destination interface ").append(v).append('\n'));
        return s.toString();
    }
}
