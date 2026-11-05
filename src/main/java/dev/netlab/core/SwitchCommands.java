package dev.netlab.core;

import java.util.*;

/** Catalyst-2960-like Layer-2 command set. It is deliberately a teaching model, not Cisco IOS. */
public final class SwitchCommands {
  private SwitchCommands() {}
  private static void s(List<CommandGrammar.Spec> out, String words, int min, int max, String usage) {
    out.add(new CommandGrammar.Spec(words, min, max, usage));
  }
  public static List<CommandGrammar.Spec> specs(Console c, Device d) {
    var x = new ArrayList<CommandGrammar.Spec>();
    var common=Set.of("line console","line vty","crypto key generate rsa","ip ssh version","ssh","telnet","show ip ssh","show users","show arp","show flash:","dir flash:","enable secret","no enable secret","banner motd","username","no username","ip domain-name","ip name-server","password","login","login local","no login","logging synchronous","exec-timeout","transport input","access-class");
    for(var spec:RouterCommands.specs(c,d))if(common.contains(spec.words()))x.add(spec);
    if (c.mode < 2) {
      for (String q : List.of("startup-config", "version", "interfaces", "interfaces status", "interfaces trunk",
          "vlan brief", "mac address-table", "spanning-tree", "etherchannel summary", "port-security",
          "port-security address", "vtp status", "vtp password", "cdp neighbors", "cdp neighbors detail",
          "cdp interface", "arp", "users", "history", "clock", "logging")) s(x, "show " + q, q.equals("interfaces") ? 0 : 0, q.equals("interfaces") ? 2 : 0,
          q.equals("interfaces") ? "[INTERFACE]" : "");
      s(x, "show interfaces switchport", 0, 1, "[INTERFACE]");
      s(x, "show vlan id", 1, 1, "VLAN");
      s(x, "show mac address-table dynamic", 0, 0, "");
      s(x, "show mac address-table interface", 1, 1, "INTERFACE");
      s(x, "show mac address-table vlan", 1, 1, "VLAN");
      s(x, "show spanning-tree vlan", 1, 1, "VLAN");
      s(x, "show spanning-tree interface", 1, 1, "INTERFACE");
      s(x, "show spanning-tree summary", 0, 0, "");
      s(x, "show etherchannel port-channel", 1, 1, "NUMBER");
      s(x, "show interfaces port-channel", 1, 1, "NUMBER");
      s(x, "show port-security interface", 1, 1, "INTERFACE");
      s(x, "show monitor session", 1, 1, "NUMBER");
      s(x,"show ip dhcp snooping",0,0,"");s(x,"show ip dhcp snooping binding",0,0,"");
      s(x, "ping", 1, 1, "IP"); s(x, "traceroute", 1, 1, "IP");
      if (c.mode == 1) {
        s(x,"copy",2,2,"SOURCE DESTINATION");
        s(x, "clear mac address-table dynamic", 0, 2, "[interface INTERFACE]");
        s(x, "write", 0, 0, ""); s(x, "write memory", 0, 0, "");
        s(x, "erase startup-config", 0, 0, ""); s(x, "delete flash:vlan.dat", 0, 0, ""); s(x, "reload", 0, 0, "");
      }
    } else {
      if (c.mode == 2) {
        for (String q : List.of("ip domain-lookup", "service password-encryption", "cdp run", "ip dhcp snooping")) {
          s(x, q, 0, 0, ""); s(x, "no " + q, 0, 0, "");
        }
        s(x, "hostname", 1, 1, "NAME"); s(x, "enable secret", 1, 1, "PASSWORD"); s(x, "banner motd", 1, 64, "#TEXT#");
        s(x, "username", 3, 5, "USER [privilege LEVEL] secret PASSWORD"); s(x, "no username", 1, 1, "USER");
        s(x, "ip domain-name", 1, 1, "DOMAIN"); s(x, "ip name-server", 1, 4, "IP..."); s(x, "ip default-gateway", 1, 1, "IP");
        s(x, "interface", 1, 2, "INTERFACE"); s(x, "interface range", 3, 6, "INTERFACE - INTERFACE");
        s(x, "default interface", 1, 2, "INTERFACE"); s(x, "vlan", 1, 1, "VLAN"); s(x, "no vlan", 1, 1, "VLAN");
        s(x, "vtp domain", 1, 1, "NAME"); s(x, "vtp mode", 1, 1, "server|client|transparent"); s(x, "vtp password", 1, 1, "PASSWORD"); s(x, "vtp version", 1, 1, "2");
        s(x, "spanning-tree mode", 1, 1, "pvst|rapid-pvst"); s(x, "spanning-tree vlan", 2, 4, "VLAN root primary|secondary|priority VALUE");
        s(x, "spanning-tree portfast default", 0, 0, ""); s(x, "spanning-tree portfast bpduguard default", 0, 0, "");
        s(x, "no spanning-tree portfast default", 0, 0, ""); s(x, "no spanning-tree portfast bpduguard default", 0, 0, "");
        s(x, "mac address-table static", 5, 5, "MAC vlan VLAN interface INTERFACE"); s(x, "no mac address-table static", 5, 5, "MAC vlan VLAN interface INTERFACE");
        s(x, "mac address-table aging-time", 1, 1, "SECONDS");
        s(x, "ip dhcp snooping vlan", 1, 1, "VLAN[,VLAN]"); s(x, "no ip dhcp snooping information option", 0, 0, "");
        s(x, "monitor session", 4, 5, "NUMBER source|destination interface INTERFACE"); s(x, "no monitor session", 1, 1, "NUMBER");
        s(x, "port-channel load-balance", 1, 1, "src-mac|dst-mac");
      }
      if (c.mode == 3) {
        s(x, "description", 1, 64, "TEXT"); s(x, "no description", 0, 0, ""); s(x, "ip address", 2, 2, "IP MASK"); s(x, "no ip address", 0, 0, ""); s(x, "shutdown", 0, 0, ""); s(x, "no shutdown", 0, 0, "");
        s(x, "speed", 1, 1, "auto|10|100|1000"); s(x, "duplex", 1, 1, "auto|full|half");
        s(x, "switchport mode", 1, 2, "access|trunk|dynamic"); s(x, "switchport access vlan", 1, 1, "VLAN"); s(x, "no switchport access vlan", 0, 0, "");
        s(x, "switchport voice vlan", 1, 1, "VLAN"); s(x, "no switchport voice vlan", 0, 0, "");
        s(x, "switchport trunk native vlan", 1, 1, "VLAN"); s(x, "switchport trunk allowed vlan", 1, 4, "all|none|add|remove VLAN"); s(x, "no switchport trunk allowed vlan", 0, 0, "");
        s(x, "switchport nonegotiate", 0, 0, ""); s(x, "no switchport nonegotiate", 0, 0, "");
        s(x, "switchport port-security", 0, 0, ""); s(x, "no switchport port-security", 0, 0, ""); s(x, "switchport port-security maximum", 1, 1, "NUMBER");
        s(x, "switchport port-security mac-address", 1, 1, "MAC|sticky"); s(x, "switchport port-security violation", 1, 1, "shutdown|restrict|protect");
        s(x, "spanning-tree portfast", 0, 0, ""); s(x, "no spanning-tree portfast", 0, 0, ""); s(x, "spanning-tree bpduguard enable", 0, 0, ""); s(x, "spanning-tree bpduguard disable", 0, 0, "");
        s(x, "spanning-tree vlan", 2, 4, "VLAN cost|port-priority VALUE");
        s(x, "channel-group", 2, 3, "NUMBER mode active|passive|desirable|auto|on"); s(x, "no channel-group", 1, 1, "NUMBER");
        s(x, "ip dhcp snooping trust", 0, 0, ""); s(x, "no ip dhcp snooping trust", 0, 0, ""); s(x, "ip dhcp snooping limit rate", 1, 1, "PPS");
        s(x, "cdp enable", 0, 0, ""); s(x, "no cdp enable", 0, 0, "");
      }
      if (c.mode == 8) s(x, "name", 1, 1, "NAME");
      if (c.mode == 4) { s(x, "password", 1, 1, "PASSWORD"); s(x, "login", 0, 0, ""); s(x, "login local", 0, 0, ""); s(x, "no login", 0, 0, ""); s(x, "logging synchronous", 0, 0, ""); s(x, "exec-timeout", 2, 2, "MINUTES SECONDS"); s(x, "transport input", 1, 2, "ssh|telnet [ssh|telnet]"); }
    }
    return x;
  }

  public static String execute(Console c, Device d, Network n, long id, String line) {
    try {
      String result=SwitchLogicCommands.execute(c,d,n,id,line);
      return result!=null?result:executeSingle(c,d,n,id,line);
    }catch(IllegalArgumentException e){return "% "+e.getMessage();}
  }
  public static String executeSingle(Console c, Device d, Network n, long id, String line) {
    String[] a = line.split("\\s+"); String cmd = line.toLowerCase(Locale.ROOT); var os = d.os();
    try {
      if (cmd.equals("show version")) return "NetLab switch runtime 0.8 — IOS-like teaching simulation\n"+(d.hardware==null?"Legacy switch":d.hardware.profile)+"\n"
          + d.ports.size() + " interfaces; VLAN, STP, EtherChannel and port-security modelled.";
      if (cmd.equals("show clock")) return java.time.Instant.now().toString();
      if (cmd.equals("show history")) return String.join("\n", c.history);
      if (cmd.equals("show startup-config")) return os.startup.isEmpty() ? "startup-config is not present" : config(ConfigStore.restore(os.startup));
      if (cmd.equals("show running-config")) return config(d);
      if (cmd.equals("show cdp neighbors") || cmd.equals("show cdp neighbors detail")) return n.cdp(id, cmd.endsWith("detail"));
      if (cmd.equals("show vlan brief")) return vlanBrief(d, os);
      if (cmd.equals("show vlan id") || cmd.startsWith("show vlan id ")) { int v = num(a[a.length-1],1,4094); return "VLAN " + v + "  " + os.vlans.getOrDefault(v,"VLAN"+v); }
      if (cmd.startsWith("show interfaces status")) return status(d,n,id);
      if (cmd.equals("show interfaces trunk")) return trunk(d,n,id);
      if (cmd.equals("show interfaces switchport") || cmd.startsWith("show interfaces switchport ")) return switchport(d, c, a);
      if (cmd.startsWith("show mac address-table")) return macTable(d, os, a);
      if (cmd.startsWith("show spanning-tree")) return spanning(d,os,a);
      if (cmd.startsWith("show etherchannel") || cmd.startsWith("show interfaces port-channel")) return ether(d,os,a);
      if (cmd.startsWith("show port-security")) return portSecurity(d,a);
      if (cmd.equals("show vtp status")) return "VTP Version : " + os.vtpVersion + "\nVTP Operating Mode : " + os.vtpMode + "\nVTP Domain Name : " + os.vtpDomain;
      if (cmd.equals("show vtp password")) return "VTP password: " + (os.vtpPassword.isEmpty()?"not configured":"configured");
      if (cmd.startsWith("show monitor session")) { int k=num(a[a.length-1],1,64); return "Session " + k + " source " + os.spanSources.getOrDefault(k,"not configured") + " destination " + os.spanDestinations.getOrDefault(k,"not configured"); }
      if (cmd.startsWith("show ip dhcp snooping")) return "DHCP snooping is " + (os.dhcpSnooping?"configured on":"disabled") + "\nVLANs: " + os.dhcpSnoopingVlans;
      if (cmd.equals("show interfaces") || cmd.startsWith("show interfaces ")) return interfaces(d,n,id,c,a);
      if (cmd.equals("show ip interface brief")) return ipBrief(d,n,id);
      if (c.mode == 8) { if (a[0].equals("name")) { os.vlans.put(c.context.isEmpty()?1:num(c.context,1,4094), a[1]); return ""; } }
      if (c.mode == 2) return global(c,d,a,cmd);
      if (c.mode == 3) {
        if (cmd.startsWith("ip address ")) { Device.ip(a[2]); Device.mask(a[3]); d.ports.get(c.selected).ip=a[2]; d.ports.get(c.selected).mask=a[3]; d.ports.get(c.selected).up=true; return ""; }
        if (cmd.equals("no ip address")) { d.ports.get(c.selected).ip=""; return ""; }
        return iface(c,d,a,cmd);
      }
      if (c.mode == 4) return line(c,d,a,cmd);
      if (cmd.equals("write") || cmd.equals("write memory") || cmd.equals("copy running-config startup-config")) { os.startup=ConfigStore.snapshot(d); return "Building configuration...\n[OK]"; }
      if (cmd.equals("erase startup-config")) { os.startup=""; return "Startup configuration erased."; }
      if (cmd.equals("delete flash:vlan.dat")) { os.vlans.clear(); return "Delete filename [vlan.dat]?\n[OK]"; }
      if (cmd.equals("dir flash:") || cmd.equals("show flash:")) return "Directory of flash:/\n  vlan.dat\n  startup-config ("+os.startup.length()+" bytes)";
      return null;
    } catch (IllegalArgumentException e) { return "% " + e.getMessage(); }
  }
  private static int num(String x,int lo,int hi){return Addressing.number(x,lo,hi);}
  public static String config(Device d){return SwitchConfiguration.render(d);}
  private static String vlanBrief(Device d,RouterState os){var o=new StringBuilder("VLAN Name                             Status    Ports\n"); o.append("1    default                          active"); for(int i=0;i<d.ports.size();i++) if(!d.ports.get(i).trunk) o.append(" ").append(d.portName(i)); for(var e:os.vlans.entrySet()) if(e.getKey()!=1) o.append('\n').append(e.getKey()).append("    ").append(e.getValue()).append("                          active"); o.append('\n'); return o.toString();}
  private static String ipBrief(Device d,Network n,long id){var o=new StringBuilder("Interface                 IP-Address       Admin   Link\n");for(int i=0;i<d.ports.size();i++)if(!d.ports.get(i).hidden)o.append(String.format("%-25s %-16s %-7s %s%n",d.portName(i),d.ports.get(i).ip.isEmpty()?"unassigned":d.ports.get(i).ip,d.ports.get(i).up?"up":"down",n.linkUp(id,i)?"up":"down"));return o.toString();}
  private static String status(Device d,Network n,long id){var o=new StringBuilder("Port      Name               Status       Vlan       Duplex Speed Type\n");for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);if(!p.hidden)o.append(String.format("%-9s %-18s %-12s %-10s %-6s %-5s 10/100/1000%n",d.portName(i),p.description,p.up&&n.linkUp(id,i)?"connected":"notconnect",p.trunk?"trunk":(p.vlan==0?"1":p.vlan),p.duplex,p.speed));}return o.toString();}
  private static String trunk(Device d,Network n,long id){var o=new StringBuilder("Port        Mode         Encapsulation  Status        Native vlan\n");for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);if(p.trunk)o.append(d.portName(i)).append("  trunk        802.1q          ").append(p.up&&n.linkUp(id,i)?"trunking":"down").append("       ").append(p.vlan==0?1:p.vlan).append('\n');}return o.toString();}
  private static String interfaces(Device d,Network n,long id,Console c,String[] a){if(a.length>2){int i=d.findInterface(String.join("",Arrays.asList(a).subList(2,a.length)),false);if(i<0)throw new IllegalArgumentException("Interface does not exist");return RouterCommands.config(d).contains("interface")?d.portName(i)+" is "+(d.ports.get(i).up?"up":"administratively down"):"";}return ipBrief(d,n,id);}
  private static String switchport(Device d,Console c,String[] a){int i=a.length>3?d.findInterface(String.join("",Arrays.asList(a).subList(3,a.length)),false):c.selected;if(i<0)throw new IllegalArgumentException("Interface does not exist");var p=d.ports.get(i);return "Name: "+d.portName(i)+"\nAdministrative Mode: "+p.switchMode+"\nOperational Mode: "+(p.trunk?"trunk":"static access")+"\nAccess Mode VLAN: "+(p.vlan==0?1:p.vlan)+"\nTrunking Native Mode VLAN: "+(p.vlan==0?1:p.vlan)+"\nVoice VLAN: "+(p.voiceVlan==0?"none":p.voiceVlan);}
  private static String macTable(Device d,RouterState os,String[] a){if(os.staticMacs.isEmpty())return "Mac Address Table\n-------------------------------------------\nNo entries";var o=new StringBuilder("Mac Address Table\n");os.staticMacs.forEach((k,v)->o.append(k).append(" STATIC ").append(v).append('\n'));return o.toString();}
  private static String spanning(Device d,RouterState os,String[] a){return "Spanning tree enabled protocol "+os.spanningMode+"\nRoot bridge priority "+os.spanningPriority+"\nPortFast default "+os.spanningPortfastDefault+"\nBPDU Guard default "+os.spanningBpduGuardDefault;}
  private static String ether(Device d,RouterState os,String[] a){var o=new StringBuilder("Group  Port-channel  Protocol    Ports\n");for(int i=0;i<d.ports.size();i++)if(d.ports.get(i).channelGroup>0)o.append(d.ports.get(i).channelGroup).append("    Po").append(d.ports.get(i).channelGroup).append("             ").append("LACP     ").append(d.portName(i)).append('\n');return o.toString();}
  private static String portSecurity(Device d,String[] a){var o=new StringBuilder("Secure Port MaxSecureAddr CurrentAddr SecurityViolation Security Action\n");for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);if(p.portSecurity)o.append(d.portName(i)).append(" ").append(p.securityMaximum).append(" 0 0 ").append(p.violation).append('\n');}return o.toString();}
  private static String global(Console c,Device d,String[] a,String cmd){var os=d.os();if(cmd.startsWith("hostname ")){d.hostname=a[1];return "";}if(cmd.startsWith("ip default-gateway ")){Device.ip(a[2]);d.gateway=a[2];return "";}if(cmd.startsWith("vlan ")){c.context=a[1];os.vlans.putIfAbsent(num(a[1],1,4094),"VLAN"+a[1]);c.mode=8;return "";}if(cmd.startsWith("no vlan ")){os.vlans.remove(num(a[2],1,4094));return "";}if(cmd.startsWith("interface range ")){int first=d.findInterface(a[2],false),last=d.findInterface(a[a.length-1],false);if(first<0||last<0)throw new IllegalArgumentException("Interface does not exist");c.selected=first;c.mode=3;return "Interface range selected (applies to ports in this range)";}if(cmd.startsWith("vtp domain ")){os.vtpDomain=a[2];return "";}if(cmd.startsWith("vtp mode ")){os.vtpMode=CommandGrammar.keyword(a[2],List.of("server","client","transparent"));return "";}if(cmd.startsWith("vtp password ")){os.vtpPassword=ConfigStore.type7(a[2]);return "";}if(cmd.startsWith("vtp version ")){if(!a[2].equals("2"))throw new IllegalArgumentException("Only VTP version 2 is supported");os.vtpVersion="2";return "";}if(cmd.startsWith("spanning-tree mode ")){os.spanningMode=CommandGrammar.keyword(a[2],List.of("pvst","rapid-pvst"));return "";}if(cmd.equals("spanning-tree portfast default")){os.spanningPortfastDefault=true;return "";}if(cmd.equals("no spanning-tree portfast default")){os.spanningPortfastDefault=false;return "";}if(cmd.equals("spanning-tree portfast bpduguard default")){os.spanningBpduGuardDefault=true;return "";}if(cmd.equals("no spanning-tree portfast bpduguard default")){os.spanningBpduGuardDefault=false;return "";}if(cmd.startsWith("spanning-tree vlan ")){if(a.length>=5&&a[3].equals("priority"))os.spanningPriority=num(a[4],0,61440);return "";}if(cmd.equals("ip dhcp snooping")){os.dhcpSnooping=true;return "";}if(cmd.equals("no ip dhcp snooping")){os.dhcpSnooping=false;return "";}if(cmd.startsWith("ip dhcp snooping vlan")){os.dhcpSnoopingVlans=a[4];return "";}if(cmd.equals("no ip dhcp snooping information option")){os.dhcpSnoopingOption=false;return "";}if(cmd.startsWith("port-channel load-balance ")){os.portChannelLoadBalance=a[2];return "";}if(cmd.startsWith("monitor session")){int k=num(a[2],1,64);String v=d.portName(d.findInterface(a[a.length-1],false));if(a[3].equals("source"))os.spanSources.put(k,v);else os.spanDestinations.put(k,v);return "";}if(cmd.startsWith("no monitor session")){os.spanSources.remove(num(a[3],1,64));os.spanDestinations.remove(num(a[3],1,64));return "";}if(cmd.startsWith("mac address-table static")){String key=a[3]+" vlan "+a[5];if(a[0].equals("no"))os.staticMacs.remove(key);else{int i=d.findInterface(a[7],false);if(i<0)throw new IllegalArgumentException("Interface does not exist");os.staticMacs.put(key,d.portName(i));}return "";}return null;}
  private static String iface(Console c,Device d,String[] a,String cmd){var p=d.ports.get(c.selected);if(cmd.equals("shutdown")){p.up=false;return "";}if(cmd.equals("no shutdown")){p.up=true;return "";}if(cmd.startsWith("description")){p.description=String.join(" ",Arrays.asList(a).subList(1,a.length));return "";}if(cmd.equals("no description")){p.description="";return "";}if(a[0].equals("speed")){p.speed=CommandGrammar.keyword(a[1],List.of("auto","10","100","1000"));return "";}if(a[0].equals("duplex")){p.duplex=CommandGrammar.keyword(a[1],List.of("auto","full","half"));return "";}if(cmd.startsWith("switchport mode ")){p.switchMode=CommandGrammar.keyword(a[2],List.of("access","trunk","dynamic"));p.trunk=p.switchMode.equals("trunk");return "";}if(cmd.startsWith("switchport access vlan")){p.vlan=num(a[a.length-1],1,4094);p.trunk=false;return "";}if(cmd.equals("no switchport access vlan")){p.vlan=0;return "";}if(cmd.startsWith("switchport voice vlan")){p.voiceVlan=num(a[3],1,4094);return "";}if(cmd.equals("no switchport voice vlan")){p.voiceVlan=0;return "";}if(cmd.startsWith("switchport trunk native vlan")){p.vlan=num(a[4],1,4094);p.trunk=true;return "";}if(cmd.startsWith("switchport trunk allowed vlan")){p.allowedVlans=String.join(",",Arrays.asList(a).subList(4,a.length));p.trunk=true;return "";}if(cmd.equals("no switchport trunk allowed vlan")){p.allowedVlans="all";return "";}if(cmd.equals("switchport nonegotiate")){p.switchMode="trunk";p.trunk=true;return "";}if(cmd.equals("no switchport nonegotiate")){return "";}if(cmd.equals("switchport port-security")){p.portSecurity=true;return "";}if(cmd.equals("no switchport port-security")){p.portSecurity=false;return "";}if(cmd.startsWith("switchport port-security maximum")){p.securityMaximum=num(a[3],1,4096);return "";}if(cmd.startsWith("switchport port-security mac-address")){if(a[3].equals("sticky")){p.sticky=true;}else p.secureMac=a[3];return "";}if(cmd.startsWith("switchport port-security violation")){p.violation=CommandGrammar.keyword(a[3],List.of("shutdown","restrict","protect"));return "";}if(cmd.equals("spanning-tree portfast")){p.portfast=true;return "";}if(cmd.equals("no spanning-tree portfast")){p.portfast=false;return "";}if(cmd.equals("spanning-tree bpduguard enable")){p.bpduGuard=true;return "";}if(cmd.equals("spanning-tree bpduguard disable")){p.bpduGuard=false;return "";}if(cmd.startsWith("channel-group")){p.channelGroup=num(a[1],1,64);return "";}if(cmd.startsWith("no channel-group")){p.channelGroup=0;return "";}if(cmd.equals("ip dhcp snooping trust")){p.dhcpTrust=true;return "";}if(cmd.equals("no ip dhcp snooping trust")){p.dhcpTrust=false;return "";}if(cmd.equals("cdp enable")){p.cdp=true;return "";}if(cmd.equals("no cdp enable")){p.cdp=false;return "";}return null;}
  private static String line(Console c,Device d,String[] a,String cmd){return null;}
}
