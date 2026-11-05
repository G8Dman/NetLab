package dev.netlab.core;

import java.util.*;

/** Packet Tracer PC/Laptop command prompt model. */
public final class PCCommands {
  private PCCommands() {}
  private static void s(List<CommandGrammar.Spec> x,String w,int min,int max,String u){x.add(new CommandGrammar.Spec(w,min,max,u));}
  public static List<CommandGrammar.Spec> specs(Device d) {
    var x=new ArrayList<CommandGrammar.Spec>();
    s(x,"ipconfig",0,3,"IP MASK [GATEWAY] or /all|/release|/renew"); s(x,"ipv6config",0,0,"");
    s(x,"arp",0,1,"[-a]"); s(x,"netstat",0,0,""); s(x,"ping",1,1,"HOST"); s(x,"tracert",1,1,"HOST"); s(x,"nslookup",1,1,"NAME");
    s(x,"ssh",3,3,"-l USER IP"); s(x,"telnet",1,1,"IP"); s(x,"dir",0,0,""); s(x,"delete",1,1,"FILE"); s(x,"ftp",1,1,"IP");
    s(x,"snmpget",1,8,"PARAMETERS"); s(x,"snmpgetbulk",1,8,"PARAMETERS"); s(x,"snmpset",1,8,"PARAMETERS");
    return x;
  }
  public static String execute(Console c,Device d,Network n,long id,String line) {
    String[] a=line.trim().split("\\s+"); String cmd=line.trim().toLowerCase(Locale.ROOT); var p=d.ports.get(Wireless.clientPort(d));
    try {
      if(a[0].equals("ipconfig") && a.length>=3 && !a[1].startsWith("/")){Device.ip(a[1]);Device.mask(a[2]);if(a.length>3)Device.ip(a[3]);p.ip=a[1];p.mask=a[2];p.dhcp=false;p.up=true;d.gateway=a.length>3?a[3]:"";return "IPv4 configuration applied.";}
      if(cmd.equals("ipconfig")||cmd.equals("ipconfig /all")) return ipconfig(d,n,id,cmd.endsWith("/all"));
      if(cmd.equals("ipconfig /release")){p.ip="";p.dhcp=false;d.gateway="";return "IPv4 address released.";}
      if(cmd.equals("ipconfig /renew")){p.dhcp=true;p.up=true;p.ip="";return n.renewDhcp(id,Wireless.clientPort(d));}
      if(cmd.equals("ipv6config")) return "IPv6 Address: "+(p.ipv6.isEmpty()?"unassigned":p.ipv6)+"\nLink-local: "+n.linkLocal(id,Wireless.clientPort(d))+"\nGateway: "+d.gateway;
      if(cmd.equals("arp")||cmd.equals("arp -a")) return pAddress(d.os().arp,"ARP table");
      if(cmd.equals("netstat")) return "% Transport session table is not implemented in NetLab.";
      if(cmd.startsWith("ping ")) return n.ping(id,n.resolve(id,a[1])).text();
      if(cmd.startsWith("tracert ")) return n.traceroute(id,n.resolve(id,a[1]));
      if(cmd.startsWith("nslookup ")) { String ip=d.os().pcDns.get(a[1]);if(ip==null)ip=n.resolve(id,a[1]);return "Name: "+a[1]+"\nAddress: "+ip; }
      if(cmd.equals("dir")) return d.os().pcFiles.isEmpty()?"Directory of C:\\ is empty":String.join("\n",d.os().pcFiles.keySet());
      if(cmd.startsWith("delete ")) { if(d.os().pcFiles.remove(a[1])==null) return "File not found - "+a[1]; return "Deleted "+a[1]; }
      if(cmd.startsWith("ftp ")) return "% FTP file transfer is not implemented in NetLab.";
      if(cmd.startsWith("ssh ")||cmd.startsWith("telnet ")) return RouterCommands.execute(c,d,n,id,line);
      if(cmd.startsWith("snmp")) return "% SNMP agents and MIB operations are not implemented in NetLab.";
      return null;
    } catch(IllegalArgumentException e){return "% "+e.getMessage();}
  }
  private static String ipconfig(Device d,Network n,long id,boolean all){var p=d.ports.get(Wireless.clientPort(d));return "Ethernet adapter "+d.portName(Wireless.clientPort(d))+":\n   IPv4 Address . . . . . : "+(p.ip.isEmpty()?"0.0.0.0":p.ip)+"\n   Subnet Mask . . . . . : "+p.mask+"\n   Default Gateway . . . : "+d.gateway+(all?"\n   DNS Servers . . . . . : "+String.join(", ",d.os().dns)+"\n   DHCP Enabled . . . .  : "+p.dhcp:" ");}
  private static String pAddress(Map<String,String> m,String title){if(m.isEmpty())return title+"\nNo entries";var o=new StringBuilder(title).append('\n');m.forEach((k,v)->o.append(k).append("  ").append(v).append('\n'));return o.toString();}
}
