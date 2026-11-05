package dev.netlab.core;

import java.security.*;
import java.time.*;
import java.util.*;

public final class RouterCommands {
  private static void spec(List<CommandGrammar.Spec> s, String words, int min, int max, String usage) {
    s.add(new CommandGrammar.Spec(words, min, max, usage));
  }
  public static List<CommandGrammar.Spec> specs(Console c, Device d) {
    var s = new ArrayList<CommandGrammar.Spec>();
    if (c.mode < 2) {
      spec(s,"show crypto netlab-vpn",0,0,"");
      for (String show : List.of("startup-config", "version", "interfaces", "ipv6 interface brief",
               "ipv6 interface", "ipv6 route", "ipv6 neighbors", "ip protocols", "arp", "access-lists",
               "users", "history", "clock", "ip ssh", "cdp neighbors", "cdp neighbors detail", "ip ospf",
               "ip ospf neighbor", "ip ospf interface", "ip route ospf", "ip eigrp neighbors",
               "ip eigrp topology", "ip route eigrp", "ip dhcp binding", "ip dhcp pool", "ip dhcp conflict",
               "ip nat translations", "ip nat statistics", "flash:")) {
        if (show.equals("interfaces"))
          spec(s, "show " + show, 0, 2, "[INTERFACE]");
        else
          spec(s, "show " + show, 0, 0, "");
      }
      spec(s, "show ip interface", 1, 2, "INTERFACE");
      spec(s, "show controllers serial", 1, 1, "NUMBER");
      spec(s, "traceroute", 1, 1, "IP");
      spec(s, "ssh", 3, 3, "-l USER IP");
      spec(s, "telnet", 1, 1, "IP");
      if (c.mode == 1) {
        spec(s, "copy", 2, 2, "SOURCE DESTINATION");
        spec(s, "write", 0, 0, "");
        spec(s, "write memory", 0, 0, "");
        spec(s, "erase startup-config", 0, 0, "");
        spec(s, "reload", 0, 0, "");
        spec(s, "dir flash:", 0, 0, "");
        spec(s, "clear ip nat translation", 1, 1, "*");
      }
    } else {
      if (c.mode == 2) {
        spec(s,"crypto netlab-vpn peer",3,3,"IP secret SECRET");
        spec(s,"no crypto netlab-vpn peer",1,1,"IP");
        for (String word :
            List.of("ip domain-lookup", "service password-encryption", "ipv6 unicast-routing", "cdp run")) {
          spec(s, word, 0, 0, "");
          spec(s, "no " + word, 0, 0, "");
        }
        spec(s, "enable secret", 1, 1, "PASSWORD");
        spec(s, "no enable secret", 0, 0, "");
        spec(s, "banner motd", 1, 64, "#TEXT#");
        spec(s, "username", 3, 5, "USER [privilege LEVEL] secret PASSWORD");
        spec(s, "no username", 1, 1, "USER");
        spec(s, "ip domain-name", 1, 1, "DOMAIN");
        spec(s, "ip name-server", 1, 4, "IP...");
        spec(s, "ip host", 2, 2, "NAME IP");
        spec(s, "line console", 1, 1, "0");
        spec(s, "line vty", 2, 2, "FIRST LAST");
        spec(s, "crypto key generate rsa", 0, 2, "[modulus BITS]");
        spec(s, "ip ssh version", 1, 1, "2");
        spec(s, "router rip", 0, 0, "");
        spec(s, "router ospf", 1, 1, "PROCESS");
        spec(s, "router eigrp", 1, 1, "AS");
        spec(s, "ip dhcp excluded-address", 1, 2, "FIRST [LAST]");
        spec(s, "ip dhcp pool", 1, 1, "NAME");
        spec(s, "access-list", 3, 12, "NUMBER permit|deny RULE");
        spec(s, "no access-list", 1, 1, "NUMBER");
        spec(s, "ip access-list standard", 1, 1, "NAME");
        spec(s, "ip access-list extended", 1, 1, "NAME");
        spec(s, "no ip route", 3, 4, "NETWORK MASK NEXT_HOP [AD]");
        spec(s, "ip nat inside source static", 2, 5, "[tcp] LOCAL [PORT] GLOBAL [PORT]");
        spec(s, "ip nat inside source list", 3, 4, "ACL pool POOL | ACL interface INTERFACE overload");
        spec(s, "ip nat pool", 5, 5, "NAME FIRST LAST netmask MASK");
        spec(s, "ipv6 route", 2, 3, "PREFIX [INTERFACE] NEXT_HOP");
        spec(s, "no ipv6 route", 2, 3, "PREFIX [INTERFACE] NEXT_HOP");
      }
      if (c.mode == 3) {
        spec(s, "description", 1, 64, "TEXT");
        spec(s, "no description", 0, 0, "");
        spec(s, "no ip address", 0, 0, "");
        spec(s, "duplex", 1, 1, "auto|full|half");
        spec(s, "speed", 1, 1, "auto|10|100|1000");
        spec(s, "bandwidth", 1, 1, "KBPS");
        spec(s, "ip helper-address", 1, 1, "IP");
        spec(s, "encapsulation", 1, 3, "hdlc|ppp|dot1Q VLAN [native]");
        spec(s, "clock rate", 1, 1, "BPS");
        spec(s, "ppp authentication", 1, 1, "pap|chap");
        spec(s, "ppp pap sent-username", 3, 3, "USER password PASSWORD");
        spec(s, "ip ospf", 1, 3, "PROCESS area AREA | cost COST | priority PRIORITY");
        spec(s, "ip access-group", 2, 2, "ACL in|out");
        spec(s, "no ip access-group", 2, 2, "ACL in|out");
        spec(s, "ip nat inside", 0, 0, "");
        spec(s, "ip nat outside", 0, 0, "");
        spec(s, "ipv6 enable", 0, 0, "");
        spec(s, "ipv6 address", 1, 2, "PREFIX|autoconfig|ADDRESS link-local");
        spec(s, "cdp enable", 0, 0, "");
        spec(s, "no cdp enable", 0, 0, "");
      }
      if (c.mode == 4) {
        spec(s, "password", 1, 1, "PASSWORD");
        spec(s, "login", 0, 0, "");
        spec(s, "login local", 0, 0, "");
        spec(s, "no login", 0, 0, "");
        spec(s, "logging synchronous", 0, 0, "");
        spec(s, "exec-timeout", 2, 2, "MINUTES SECONDS");
        spec(s, "transport input", 1, 2, "ssh|telnet [ssh|telnet]");
        spec(s, "access-class", 2, 2, "ACL in");
      }
      if (c.mode == 5) {
        var r = d.os().routing.get(c.context);
        if (r != null) {
          if (r.protocol.equals("rip"))
            spec(s, "version", 1, 1, "2");
          if (r.protocol.equals("ospf")) {
            spec(s, "router-id", 1, 1, "IP");
            spec(s, "network", 4, 4, "IP WILDCARD area AREA");
          } else
            spec(s, "network", r.protocol.equals("rip") ? 1 : 2, r.protocol.equals("rip") ? 1 : 2,
                "IP [WILDCARD]");
          if(!r.protocol.equals("ospf"))spec(s, "no auto-summary", 0, 0, "");
          spec(s, "passive-interface", 1, 2, "INTERFACE");
          spec(s, "no passive-interface", 1, 2, "INTERFACE");
          if(!r.protocol.equals("eigrp"))spec(s, "default-information originate", 0, r.protocol.equals("ospf")?1:0, r.protocol.equals("ospf")?"[always]":"");
          if (r.protocol.equals("eigrp"))
            spec(s, "variance", 1, 1, "MULTIPLIER");
        }
      }
      if (c.mode == 6) {
        spec(s, "network", 2, 2, "NETWORK MASK");
        spec(s, "default-router", 1, 1, "IP");
        spec(s, "dns-server", 1, 1, "IP");
        spec(s, "domain-name", 1, 1, "DOMAIN");
      }
      if (c.mode == 7) {
        spec(s, "permit", 1, 11, "RULE");
        spec(s, "deny", 1, 11, "RULE");
      }
    }
    return s;
  }
  private static boolean kw(String value, String full) {
    return !value.isEmpty() && full.startsWith(value.toLowerCase(Locale.ROOT));
  }
  public static String execute(Console c, Device d, Network n, long id, String line) {
    String[] a = line.split("\\s+");
    String cmd = line.toLowerCase(Locale.ROOT);
    RouterState os = d.os();
    if (a[0].equals("copy") && a.length == 3) {
      try {
        cmd = "copy " + CommandGrammar.keyword(a[1], List.of("running-config", "startup-config", "tftp:"))
            + " " + CommandGrammar.keyword(a[2], List.of("running-config", "startup-config", "tftp:"));
      } catch (IllegalArgumentException e) {
        return "% " + e.getMessage();
      }
    }
    try {
      if(cmd.startsWith("crypto netlab-vpn ")||cmd.startsWith("no crypto netlab-vpn ")||cmd.equals("show crypto netlab-vpn"))
        return n.vpnCommand==null?"% VPN configuration requires a server world":n.vpnCommand.apply(id,line);
      if (cmd.equals("enable") && !os.enableSecret.isEmpty()) {
        c.pending = "enable";
        return "Password:";
      }
      if (cmd.equals("show running-config"))
        return showConfiguration(config(d));
      if (cmd.equals("show startup-config"))
        return os.startup.isEmpty() ? "startup-config is not present"
                                    : config(ConfigStore.restore(os.startup));
      if (cmd.equals("show version"))
        return "NetLab Router runtime 0.8 — IOS-like teaching simulation\n"+(d.hardware==null?"Legacy router":d.hardware.profile)+"; "+d.physicalPortCount()+" physical connectors\n"
            + "configurable Serial, Loopback and subinterfaces\nUptime: "
            + Math.max(0, (System.currentTimeMillis() - os.bootMillis) / 1000)
            + " seconds\nRouting converges synchronously; simplified RIP/OSPF/EIGRP, not vendor firmware.";
      if (cmd.equals("show clock"))
        return Instant.now().toString();
      if (cmd.equals("show history"))
        return String.join("\n", c.history);
      if (cmd.equals("show users")) {
        var out = new StringBuilder("    Line       User\n");
        os.activeUsers.forEach((session, label) -> {
          int timeout = label.startsWith("vty") ? vty(d).timeout : os.line("console 0").timeout;
          if (timeout == 0
              || System.currentTimeMillis() - os.sessionTimes.getOrDefault(session, 0L) < timeout * 1000L)
            out.append(session.equals(c.sessionId) ? "* " : "  ").append(label).append('\n');
        });
        return out.toString();
      }
      if (cmd.equals("show ip ssh"))
        return "SSH "
            + (os.rsaPublic.isEmpty() ? "disabled: no RSA keys" : "enabled, version " + os.sshVersion)
            + "\nVTY transport: " + os.line("vty 0 4").transport;
      if (cmd.equals("show interfaces") || cmd.startsWith("show interfaces ")
          || cmd.startsWith("show ip interface ")) {
        if (cmd.equals("show ip interface brief"))
          return null;
        int start = cmd.startsWith("show ip interface ") ? 3 : 2;
        if (a.length > start) {
          int index = d.findInterface(String.join("", Arrays.asList(a).subList(start, a.length)), false);
          if (index < 0)
            throw new IllegalArgumentException("Interface does not exist");
          return interfaceInfo(d, n, id, index);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < d.ports.size(); i++)
          if (!d.ports.get(i).hidden)
            out.append(interfaceInfo(d, n, id, i)).append('\n');
        return out.toString();
      }
      if (cmd.startsWith("show controllers serial ")) {
        int index = d.findInterface("Serial" + a[3], false);
        if (index < 0)
          throw new IllegalArgumentException("Serial interface does not exist");
        var p = d.ports.get(index);
        return d.portName(index) + " simulated " + n.serialRole(id, index) + ", clock rate " + p.clock
            + "\nEncapsulation " + p.encapsulation + ", authentication " + p.authentication;
      }
      if (cmd.equals("show ip route") || cmd.equals("show ipv6 route") || cmd.equals("show ip route ospf")
          || cmd.equals("show ip route eigrp"))
        return n.routeTable(id, cmd.contains("ipv6"),
            cmd.endsWith("ospf")        ? "ospf"
                : cmd.endsWith("eigrp") ? "eigrp"
                                        : "");
      if (cmd.startsWith("show ipv6 interface")) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < d.ports.size(); i++) {
          var p = d.ports.get(i);
          out.append(d.portName(i))
              .append(" ")
              .append(n.interfaceUp(id, i) ? "up" : "down")
              .append("\n  ")
              .append(p.ipv6)
              .append("\n  ")
              .append(p.ipv6Enabled || !p.ipv6.isEmpty() ? n.linkLocal(id, i) : "IPv6 disabled")
              .append('\n');
        }
        return out.toString();
      }
      if (cmd.equals("show arp"))
        return table(os.arp, "Protocol IPv4 / MAC / interface (learned by simulated traffic)");
      if (cmd.equals("show ipv6 neighbors"))
        return table(os.neighbors6, "IPv6 neighbor / link-layer address");
      if (cmd.equals("show access-lists")) {
        var out = new StringBuilder();
        os.acls.forEach((name, acl)
                            -> out.append(acl.type).append(" ACL ").append(name).append('\n').append(
                                AccessLists.show(acl)));
        return out.length() == 0 ? "No access lists configured" : out.toString();
      }
      if (cmd.startsWith("show cdp neighbors"))
        return n.cdp(id, cmd.endsWith("detail"));
      if (cmd.equals("show ip protocols"))
        return n.protocols(id);
      if (cmd.startsWith("show ip ospf"))
        return n.routingInfo(id, "ospf", cmd);
      if (cmd.startsWith("show ip eigrp"))
        return n.routingInfo(id, "eigrp", cmd);
      if (cmd.startsWith("show ip dhcp "))
        return n.dhcpInfo(id, cmd);
      if (cmd.equals("show ip nat translations")) {
        var out = new StringBuilder("Pro Inside local -> Inside global / destination\n");
        for (var t : os.translations)
          out.append(t.protocol())
              .append(' ')
              .append(t.inside())
              .append(':')
              .append(t.insidePort())
              .append(" -> ")
              .append(t.outside())
              .append(':')
              .append(t.outsidePort())
              .append(" / ")
              .append(t.peer())
              .append('\n');
        for (var r : os.nat)
          if (r.type().startsWith("static"))
            out.append(r.type()).append(' ').append(r.local()).append(" -> ").append(r.global()).append('\n');
        return out.toString();
      }
      if (cmd.equals("show ip nat statistics"))
        return "Active translations: " + os.translations.size() + "; hits: " + os.natHits
            + "; misses: " + os.natMisses;
      if (cmd.equals("clear ip nat translation *")) {
        os.translations.removeIf(t -> !t.permanent());
        return "Dynamic translations cleared";
      }
      if (cmd.equals("show flash:") || cmd.equals("dir flash:"))
        return "Directory of flash:/\n  startup-config (" + os.startup.length() + " bytes)\n"
            + String.join("\n", os.flash.keySet());
      if (cmd.equals("write") || cmd.equals("write memory")
          || cmd.equals("copy running-config startup-config")) {
        os.startup = ConfigStore.snapshot(d);
        return "Building configuration...\n[OK]";
      }
      if (cmd.equals("erase startup-config")) {
        os.startup = "";
        return "Startup configuration erased. Running configuration retained.";
      }
      if (cmd.equals("reload")) {
        String startup = os.startup;
        Device saved = startup.isEmpty() ? new Device(d.kind) : ConfigStore.restore(startup);
        ConfigStore.replace(d, saved);
        d.os().bootMillis = System.currentTimeMillis();
        d.os().translations.clear();
        d.os().arp.clear();
        d.os().neighbors6.clear();
        d.os().leases.clear();
        d.os().conflicts.clear();
        for (var p : d.ports) {
          p.sent = 0;
          p.received = 0;
          p.drops = 0;
          if (p.dhcp)
            p.ip = "";
        }
        c.mode = 0;
        c.selected = -1;
        c.pending = "";
        return "Device reloaded from " + (startup.isEmpty() ? "factory defaults" : "startup-config");
      }
      if (cmd.equals("copy startup-config running-config")) {
        if (os.startup.isEmpty())
          throw new IllegalArgumentException("No startup-config");
        merge(d, ConfigStore.restore(os.startup));
        return "Saved configuration merged into running-config";
      }
      if (cmd.equals("copy running-config tftp:") || cmd.equals("copy tftp: running-config")) {
        c.pending = cmd.contains("running-config tftp:") ? "tftp-put-host" : "tftp-get-host";
        return "Address or name of remote host []?";
      }
      if (cmd.startsWith("traceroute "))
        return n.traceroute(id, n.resolve(id, a[1]));
      if (a[0].equals("ping"))
        return n.ping(id, n.resolve(id, a[1])).text();
      if (a[0].equals("ssh") || a[0].equals("telnet"))
        return connect(c, d, n, id, a);
      if (cmd.equals("no ip domain-lookup")) {
        os.domainLookup = false;
        return "";
      }
      if (cmd.equals("ip domain-lookup")) {
        os.domainLookup = true;
        return "";
      }
      if (cmd.equals("service password-encryption")) {
        os.passwordEncryption = true;
        return "";
      }
      if (cmd.equals("no service password-encryption")) {
        os.passwordEncryption = false;
        return "";
      }
      if (cmd.equals("no enable secret")) {
        os.enableSecret = "";
        return "";
      }
      if (cmd.startsWith("enable secret ")) {
        os.enableSecret = ConfigStore.secret(a[2]);
        return "";
      }
      if (cmd.startsWith("banner motd ")) {
        String text = line.substring("banner motd ".length());
        if (text.length() < 2 || text.charAt(0) != text.charAt(text.length() - 1))
          throw new IllegalArgumentException("Use the same delimiter around the banner");
        os.banner = text.substring(1, text.length() - 1);
        return "";
      }
      if (a[0].equals("username")) {
        int privilege = 1, offset = 2;
        if (a.length > 3 && kw(a[2], "privilege")) {
          privilege = Addressing.number(a[3], 0, 15);
          offset = 4;
        }
        if (a.length != offset + 2 || !kw(a[offset], "secret"))
          throw new IllegalArgumentException("username USER [privilege LEVEL] secret PASSWORD");
        os.users.put(a[1], ConfigStore.secret(a[offset + 1]));
        os.chapSecrets.put(a[1], ConfigStore.type7(a[offset + 1]));
        os.privileges.put(a[1], privilege);
        return "";
      }
      if (cmd.startsWith("no username ")) {
        os.users.remove(a[2]);
        os.chapSecrets.remove(a[2]);
        os.privileges.remove(a[2]);
        return "";
      }
      if (cmd.startsWith("ip domain-name ")) {
        os.domain = a[2];
        return "";
      }
      if (cmd.startsWith("ip name-server ")) {
        for (int i = 2; i < a.length; i++) Device.ip(a[i]);
        os.dns = new ArrayList<>(Arrays.asList(a).subList(2, a.length));
        return "";
      }
      if (cmd.startsWith("ip host ")) {
        Device.ip(a[3]);
        os.hosts.put(a[2], a[3]);
        return "";
      }
      if (cmd.startsWith("line ")) {
        if (a[1].equals("console")) {
          if (!a[2].equals("0"))
            throw new IllegalArgumentException("Only console 0 exists");
          c.context = "console 0";
        } else {
          int first = Addressing.number(a[2], 0, 15), last = Addressing.number(a[3], first, 15);
          c.context = "vty " + first + " " + last;
        }
        os.line(c.context);
        c.mode = 4;
        return "";
      }
      if (cmd.startsWith("crypto key generate rsa")) {
        if (os.domain.isEmpty() || d.hostname.equals("Router"))
          throw new IllegalArgumentException("Configure hostname and ip domain-name first");
        if (a.length == 4) {
          c.pending = "rsa";
          return "How many bits in the modulus [2048]?";
        }
        if (a.length != 6 || !kw(a[4], "modulus"))
          throw new IllegalArgumentException("Expected modulus BITS");
        return keys(os, a[5]);
      }
      if (cmd.startsWith("ip ssh version ")) {
        if (!a[3].equals("2"))
          throw new IllegalArgumentException("Only SSH version 2 is supported");
        os.sshVersion = 2;
        return "";
      }
      if (c.mode == 4)
        return line(c, d, a, cmd);
      if (cmd.startsWith("router ")) {
        String protocol = a[1], process = protocol.equals("rip") ? "0" : a[2];
        if (!protocol.equals("rip"))
          Addressing.number(process, 1, 65535);
        c.context = protocol + ":" + process;
        os.routing.computeIfAbsent(c.context, key -> new RouterState.Routing(protocol, process));
        c.mode = 5;
        return "";
      }
      if (c.mode == 5)
        return routing(c, d, a, cmd);
      if (cmd.startsWith("ip dhcp excluded-address ")) {
        Device.ip(a[3]);
        String last = a.length == 5 ? a[4] : a[3];
        Device.ip(last);
        if (Device.ip(last) < Device.ip(a[3]))
          throw new IllegalArgumentException("Invalid DHCP exclusion range");
        os.excluded.add(new String[] {a[3], last});
        return "";
      }
      if (cmd.startsWith("ip dhcp pool ")) {
        c.context = a[3];
        os.pools.computeIfAbsent(c.context, key -> new RouterState.Pool());
        c.mode = 6;
        return "";
      }
      if (c.mode == 6) {
        var pool = os.pools.get(c.context);
        switch (a[0]) {
          case "network" -> {
            Device.mask(a[2]);
            if (!Device.networkAddress(a[1], a[2]).equals(a[1]))
              throw new IllegalArgumentException("Inconsistent network and mask");
            pool.network = a[1];
            pool.mask = a[2];
          }
          case "default-router" -> {
            Device.ip(a[1]);
            pool.gateway = a[1];
          }
          case "dns-server" -> {
            Device.ip(a[1]);
            pool.dns = a[1];
          }
          case "domain-name" -> pool.domain = a[1];
          default -> {
            return null;
          }
        }
        return "";
      }
      if (cmd.startsWith("no access-list ")) {
        os.acls.remove(a[2]);
        return "";
      }
      if (cmd.startsWith("ip access-list ")) {
        c.context = a[3];
        var acl = os.acls.computeIfAbsent(c.context, key -> new RouterState.Acl(a[2]));
        if (!acl.type.equals(a[2]))
          throw new IllegalArgumentException("ACL already exists with another type");
        c.mode = 7;
        return "";
      }
      if (a[0].equals("access-list")) {
        int number = Addressing.number(a[1], 1, 199);
        String type = number < 100 ? "standard" : "extended";
        var rule = AccessLists.parse(type, String.join(" ", Arrays.asList(a).subList(2, a.length)));
        os.acls.computeIfAbsent(a[1], key -> new RouterState.Acl(type)).rules.add(rule);
        return "";
      }
      if (c.mode == 7 && (a[0].equals("permit") || a[0].equals("deny"))) {
        var acl = os.acls.get(c.context);
        acl.rules.add(AccessLists.parse(acl.type, line));
        return "";
      }
      if (cmd.equals("ipv6 unicast-routing")) {
        os.ipv6Routing = true;
        return "";
      }
      if (cmd.equals("no ipv6 unicast-routing")) {
        os.ipv6Routing = false;
        return "";
      }
      if (cmd.startsWith("ipv6 route ") || cmd.startsWith("no ipv6 route ")) {
        int offset = a[0].equals("no") ? 3 : 2;
        Addressing.prefix6(a[offset]);
        String next = a[a.length - 1], iface = a.length - offset == 3 ? a[offset + 1] : "";
        Addressing.ipv6(next);
        if (next.toLowerCase().startsWith("fe80:") && iface.isEmpty())
          throw new IllegalArgumentException("Link-local next hop requires an interface");
        if (!iface.isEmpty()) {
          int p = d.findInterface(iface, false);
          if (p < 0)
            throw new IllegalArgumentException("Interface does not exist");
          iface = d.portName(p);
        }
        var route = new RouterState.Route6(a[offset], next, iface);
        if (offset == 3)
          os.routes6.remove(route);
        else if (!os.routes6.contains(route))
          os.routes6.add(route);
        return "";
      }
      if (cmd.equals("cdp run")) {
        os.cdp = true;
        return "";
      }
      if (cmd.equals("no cdp run")) {
        os.cdp = false;
        return "";
      }
      if (c.mode == 2 && cmd.startsWith("ip nat "))
        return nat(d, a, cmd);
      if (cmd.startsWith("no ip route ") || cmd.startsWith("ip route "))
        return staticRoute(d, a);
      if (c.mode == 3)
        return iface(c, d, n, id, a, cmd, line);
      return null;
    } catch (IllegalArgumentException e) {
      return "% " + e.getMessage();
    }
  }
  private static String table(Map<String, String> rows, String title) {
    StringBuilder out = new StringBuilder(title + "\n");
    rows.forEach((k, v) -> out.append(k).append("  ").append(v).append('\n'));
    return out.toString();
  }
  private static String interfaceInfo(Device d, Network n, long id, int i) {
    var p = d.ports.get(i);
    return d.portName(i) + " is " + (p.up ? "up" : "administratively down") + ", line protocol is "
        + (n.interfaceUp(id, i) ? "up" : "down") + "\n  Description: " + p.description
        + "\n  Internet address: " + (p.ip.isEmpty() ? "unassigned" : p.ip + " mask " + p.mask) + "\n  BW "
        + p.bandwidth + " Kbit, duplex " + p.duplex + ", speed " + p.speed + "\n  " + p.received
        + " packets input, " + p.sent + " packets output, " + p.drops + " drops\n  ACL in: " + p.aclIn
        + ", out: " + p.aclOut;
  }
  private static String keys(RouterState os, String bits) {
    int size = bits.isBlank() ? 2048 : Addressing.number(bits, 1024, 4096);
    if (size % 256 != 0)
      throw new IllegalArgumentException("RSA size must be a multiple of 256");
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(size);
      var pair = generator.generateKeyPair();
      os.rsaPublic = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
      os.rsaPrivate = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
      return "RSA " + size + "-bit key pair generated. SSH uses simulated authenticated sessions.";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
  private static String line(Console c, Device d, String[] a, String cmd) {
    var l = d.os().line(c.context);
    if (a[0].equals("password")) {
      l.password = ConfigStore.type7(a[1]);
      return "";
    }
    if (cmd.equals("login") || cmd.equals("login local")) {
      l.login = cmd.endsWith("local") ? "local" : "password";
      return "";
    }
    if (cmd.equals("no login")) {
      l.login = "";
      return "";
    }
    if (cmd.equals("logging synchronous")) {
      l.synchronous = true;
      return "";
    }
    if (a[0].equals("exec-timeout")) {
      l.timeout = Addressing.number(a[1], 0, 35791) * 60 + Addressing.number(a[2], 0, 59);
      return "";
    }
    if (cmd.startsWith("transport input ")) {
      var protocols = new LinkedHashSet<String>();
      for (int i = 2; i < a.length; i++) {
        if (kw(a[i], "ssh"))
          protocols.add("ssh");
        else if (kw(a[i], "telnet"))
          protocols.add("telnet");
        else
          throw new IllegalArgumentException("Expected ssh or telnet");
      }
      l.transport = String.join(" ", protocols);
      return "";
    }
    if (a[0].equals("access-class")) {
      if (!kw(a[2], "in"))
        throw new IllegalArgumentException("Only inbound VTY access-class is supported");
      l.access = a[1];
      return "";
    }
    return null;
  }
  private static String routing(Console c, Device d, String[] a, String cmd) {
    var r = d.os().routing.get(c.context);
    if (a[0].equals("version")) {
      if (!a[1].equals("2"))
        throw new IllegalArgumentException("Only RIP version 2 is modeled");
      r.version = 2;
      return "";
    }
    if (a[0].equals("router-id")) {
      Device.ip(a[1]);
      r.routerId = a[1];
      return "";
    }
    if (a[0].equals("network")) {
      Device.ip(a[1]);
      String wildcard = r.protocol.equals("rip") ? Addressing.wildcard(Addressing.classMask(a[1])) : a[2];
      Device.ip(wildcard);
      String area = "0";
      if (r.protocol.equals("ospf")) {
        if (!kw(a[3], "area"))
          throw new IllegalArgumentException("Expected area AREA");
        area = area(a[4]);
      }
      var announcement = new RouterState.Announcement(a[1], wildcard, area);
      if (!r.networks.contains(announcement))
        r.networks.add(announcement);
      return "";
    }
    if (cmd.equals("no auto-summary")) {
      r.autoSummary = false;
      return "";
    }
    if (cmd.startsWith("passive-interface ") || cmd.startsWith("no passive-interface ")) {
      int offset = a[0].equals("no") ? 2 : 1;
      String name = String.join("", Arrays.asList(a).subList(offset, a.length));
      int index = d.findInterface(name, false);
      if (index < 0)
        throw new IllegalArgumentException("Interface does not exist");
      if (offset == 2)
        r.passive.remove(d.portName(index));
      else
        r.passive.add(d.portName(index));
      return "";
    }
    if (cmd.startsWith("default-information originate")) {
      if (a.length > 2 && !kw(a[2], "always"))
        throw new IllegalArgumentException("Expected always");
      r.originate = true;
      r.always = a.length > 2;
      return "";
    }
    if (a[0].equals("variance")) {
      r.variance = Addressing.number(a[1], 1, 128);
      return "";
    }
    return null;
  }
  private static String area(String value) {
    return value.contains(".") ? Long.toString(Device.ip(value))
                               : Integer.toUnsignedString(Addressing.number(value, 0, Integer.MAX_VALUE));
  }
  private static String iface(Console c, Device d, Network n, long id, String[] a, String cmd, String line) {
    var p = d.ports.get(c.selected);
    if (cmd.startsWith("description ")) {
      p.description = line.substring(12);
      return "";
    }
    if (cmd.equals("no description")) {
      p.description = "";
      return "";
    }
    if (cmd.equals("no ip address")) {
      p.ip = "";
      p.dhcp = false;
      return "";
    }
    if (cmd.equals("ip address dhcp")) {
      p.dhcp = true;
      p.ip = "";
      return n.renewDhcp(id, c.selected);
    }
    if (a[0].equals("duplex")) {
      p.duplex = CommandGrammar.keyword(a[1], List.of("auto", "full", "half"));
      return "";
    }
    if (a[0].equals("speed")) {
      p.speed = CommandGrammar.keyword(a[1], List.of("auto", "10", "100", "1000"));
      return "";
    }
    if (a[0].equals("bandwidth")) {
      p.bandwidth = Addressing.number(a[1], 1, 100000000);
      return "";
    }
    if (cmd.startsWith("ip helper-address ")) {
      Device.ip(a[2]);
      p.helper = a[2];
      return "";
    }
    if (a[0].equals("encapsulation")) {
      if (kw(a[1], "dot1q")) {
        if (p.parent < 0 || a.length < 3)
          throw new IllegalArgumentException("dot1Q requires a subinterface and VLAN");
        p.vlan = Addressing.number(a[2], 1, 4094);
        p.nativeVlan = a.length == 4 && kw(a[3], "native");
        if (a.length == 4 && !p.nativeVlan)
          throw new IllegalArgumentException("Expected native");
        p.encapsulation = "dot1Q";
      } else {
        if (!d.portName(c.selected).startsWith("Serial"))
          throw new IllegalArgumentException("HDLC/PPP require Serial");
        if (kw(a[1], "ppp"))
          p.encapsulation = "ppp";
        else if (kw(a[1], "hdlc"))
          p.encapsulation = "hdlc";
        else
          throw new IllegalArgumentException("Unsupported encapsulation");
      }
      return "";
    }
    if (cmd.startsWith("clock rate ")) {
      if (!d.portName(c.selected).startsWith("Serial"))
        throw new IllegalArgumentException("clock rate requires a DCE Serial port");
      if (n.serialRole(id, c.selected).equals("DTE"))
        throw new IllegalArgumentException("clock rate is configured on the DCE side only");
      p.clock = Addressing.number(a[2], 1200, 8000000);
      return "";
    }
    if (cmd.startsWith("ppp ")) {
      if (!d.portName(c.selected).startsWith("Serial"))
        throw new IllegalArgumentException("PPP requires Serial");
      if (a[1].equals("authentication")) {
        if (!kw(a[2], "pap") && !kw(a[2], "chap"))
          throw new IllegalArgumentException("Expected pap or chap");
        p.authentication = kw(a[2], "pap") ? "pap" : "chap";
      } else {
        if (!kw(a[4], "password"))
          throw new IllegalArgumentException("Expected password PASSWORD");
        p.papUser = a[3];
        p.papPassword = ConfigStore.type7(a[5]);
      }
      return "";
    }
    if (cmd.startsWith("ip ospf ")) {
      if (kw(a[2], "cost")) {
        if (a.length != 4)
          throw new IllegalArgumentException("Expected cost VALUE");
        p.ospfCost = Addressing.number(a[3], 1, 65535);
        p.ospfCostManual = true;
      } else if (kw(a[2], "priority")) {
        if (a.length != 4)
          throw new IllegalArgumentException("Expected priority VALUE");
        p.ospfPriority = Addressing.number(a[3], 0, 255);
      } else {
        if (a.length != 5 || !kw(a[3], "area"))
          throw new IllegalArgumentException("Expected PROCESS area AREA");
        Addressing.number(a[2], 1, 65535);
        p.ospfProcess = a[2];
        p.ospfArea = area(a[4]);
        d.os().routing.computeIfAbsent("ospf:" + a[2], key -> new RouterState.Routing("ospf", a[2]));
      }
      return "";
    }
    if (cmd.startsWith("ip access-group ") || cmd.startsWith("no ip access-group ")) {
      int offset = a[0].equals("no") ? 3 : 2;
      String direction = a[offset + 1];
      if (kw(direction, "in"))
        p.aclIn = offset == 3 ? "" : a[offset];
      else if (kw(direction, "out"))
        p.aclOut = offset == 3 ? "" : a[offset];
      else
        throw new IllegalArgumentException("Expected in or out");
      return "";
    }
    if (cmd.equals("ip nat inside") || cmd.equals("ip nat outside")) {
      p.nat = a[2];
      return "";
    }
    if (cmd.equals("ipv6 enable")) {
      p.ipv6Enabled = true;
      return "";
    }
    if (cmd.startsWith("ipv6 address ")) {
      if (kw(a[2], "autoconfig")) {
        p.autoconfig = true;
        p.ipv6Enabled = true;
        return n.autoconfig(id, c.selected);
      }
      if (a.length == 4) {
        if (!kw(a[3], "link-local") || !a[2].toLowerCase(Locale.ROOT).startsWith("fe80:"))
          throw new IllegalArgumentException("Expected IPv6 link-local address");
        Addressing.ipv6(a[2]);
        p.linkLocal = a[2];
      } else {
        Addressing.prefix6(a[2]);
        p.ipv6 = a[2];
      }
      p.ipv6Enabled = true;
      return "";
    }
    if (cmd.equals("cdp enable")) {
      p.cdp = true;
      return "";
    }
    if (cmd.equals("no cdp enable")) {
      p.cdp = false;
      return "";
    }
    return null;
  }
  private static String staticRoute(Device d, String[] a) {
    boolean remove = a[0].equals("no");
    int k = remove ? 3 : 2;
    String network = a[k], mask = a[k + 1], hop = a[k + 2], iface = "";
    Device.mask(mask);
    if (!Device.networkAddress(network, mask).equals(network))
      throw new IllegalArgumentException("Inconsistent network and mask");
    if (!hop.matches("[0-9.]+")) {
      int index = d.findInterface(hop, false);
      if (index < 0)
        throw new IllegalArgumentException("Interface does not exist");
      iface = d.portName(index);
      hop = "";
    } else
      Device.ip(hop);
    int ad = a.length > k + 3 ? Addressing.number(a[k + 3], 1, 255) : 1;
    final String next = hop, output = iface;
    if (remove)
      d.routes.removeIf(r
          -> r.network().equals(network) && r.mask().equals(mask) && r.nextHop().equals(next)
              && r.iface().equals(output));
    else {
      d.routes.removeIf(r
          -> r.network().equals(network) && r.mask().equals(mask) && r.nextHop().equals(next)
              && r.iface().equals(output));
      d.routes.add(new Device.Route(network, mask, hop, ad, iface));
    }
    return "";
  }
  private static String nat(Device d, String[] a, String cmd) {
    var os = d.os();
    if (cmd.startsWith("ip nat inside source static ")) {
      if (kw(a[5], "tcp")) {
        if (a.length != 10)
          throw new IllegalArgumentException("static tcp LOCAL LPORT GLOBAL GPORT");
        Device.ip(a[6]);
        Device.ip(a[8]);
        os.nat.add(new RouterState.NatRule(
            "static-tcp", a[6], a[8], "", "", "", AccessLists.port(a[7]), AccessLists.port(a[9])));
      } else {
        if (a.length != 7)
          throw new IllegalArgumentException("static LOCAL GLOBAL");
        Device.ip(a[5]);
        Device.ip(a[6]);
        os.nat.add(new RouterState.NatRule("static", a[5], a[6], "", "", "", -1, -1));
      }
      return "";
    }
    if (cmd.startsWith("ip nat pool ")) {
      Device.ip(a[4]);
      Device.ip(a[5]);
      if (!kw(a[6], "netmask"))
        throw new IllegalArgumentException("Expected netmask MASK");
      Device.mask(a[7]);
      if (Device.ip(a[4]) > Device.ip(a[5]))
        throw new IllegalArgumentException("Invalid NAT pool range");
      os.natPools.put(a[3], new String[] {a[4], a[5], a[7]});
      return "";
    }
    if (cmd.startsWith("ip nat inside source list ")) {
      if (kw(a[6], "pool")) {
        if (a.length != 8)
          throw new IllegalArgumentException("Expected ACL pool NAME");
        os.nat.add(new RouterState.NatRule("dynamic", "", "", a[5], a[7], "", -1, -1));
      } else if (kw(a[6], "interface")) {
        if (a.length != 9 || !kw(a[8], "overload"))
          throw new IllegalArgumentException("Expected ACL interface INTERFACE overload");
        int port = d.findInterface(a[7], false);
        if (port < 0)
          throw new IllegalArgumentException("Interface does not exist");
        os.nat.add(new RouterState.NatRule("pat", "", "", a[5], "", d.portName(port), -1, -1));
      } else
        throw new IllegalArgumentException("Expected pool or interface");
      return "";
    }
    return null;
  }
  public static String pending(Console c, Device d, Network n, long id, String text) {
    String operation = c.pending;
    if (operation.equals("enable")) {
      c.pending = "";
      if (ConfigStore.verify(text, d.os().enableSecret)) {
        c.mode = 1;
        return "";
      }
      return "% Bad secrets";
    }
    if (operation.equals("rsa")) {
      c.pending = "";
      return keys(d.os(), text);
    }
    if (operation.startsWith("tftp-")) {
      if (operation.endsWith("host")) {
        String address = n.resolve(id, text.strip());
        var probe = n.probe(id, address, "udp", 69);
        if (!probe.success())
          throw new IllegalArgumentException(probe.text());
        c.transferPeer = n.findAddress(address);
        c.pending = operation.replace("host", "file");
        return (operation.contains("put") ? "Destination" : "Source") + " filename [" + d.hostname
            + "-confg]?";
      }
      Device peer = n.devices.get(c.transferPeer);
      if (peer == null)
        throw new IllegalArgumentException("TFTP host is offline");
      String name = text.isBlank() ? d.hostname + "-confg" : text.strip();
      c.pending = "";
      if (operation.contains("put")) {
        peer.os().flash.put(name, ConfigStore.snapshot(d, false));
        n.changed.add(c.transferPeer);
        return "Configuration copied to simulated TFTP host: " + name;
      }
      String file = peer.os().flash.get(name);
      if (file == null)
        throw new IllegalArgumentException("File not found on TFTP host");
      merge(d, ConfigStore.restore(file));
      return "TFTP configuration merged into running-config";
    }
    if (operation.equals("remote-user")) {
      c.user = text.strip();
      c.pending = "remote-password";
      return "Password:";
    }
    if (operation.equals("remote-password")) {
      Device remote = n.devices.get(c.loginPeer);
      c.pending = "";
      if (remote == null)
        throw new IllegalArgumentException("Remote device disconnected");
      var line = vty(remote);
      boolean okay = line.login.equals("local")
          ? ConfigStore.verify(text, remote.os().users.getOrDefault(c.user, ""))
          : !line.password.isEmpty() && ConfigStore.untype7(line.password).equals(text);
      if (!okay)
        throw new IllegalArgumentException("Authentication failed");
      c.remoteId = c.loginPeer;
      c.mode = remote.os().privileges.getOrDefault(c.user, 1) == 15 ? 1 : 0;
      c.selected = -1;
      return remote.os().banner + "\nConnected to " + remote.hostname;
    }
    if (operation.equals("console-user")) {
      c.user = text.strip();
      c.pending = "console-password";
      return "Password:";
    }
    if (operation.equals("console-password")) {
      var line = d.os().line("console 0");
      c.pending = "";
      boolean okay = line.login.equals("local")
          ? ConfigStore.verify(text, d.os().users.getOrDefault(c.user, ""))
          : !line.password.isEmpty() && ConfigStore.untype7(line.password).equals(text);
      if (!okay) {
        c.pending = line.login.equals("local") ? "console-user" : "console-password";
        return "% Authentication failed";
      }
      c.authenticated = true;
      c.mode = line.login.equals("local") && d.os().privileges.getOrDefault(c.user, 1) == 15 ? 1 : 0;
      return "";
    }
    throw new IllegalArgumentException("Unknown interactive operation");
  }
  public static RouterState.Line vty(Device d) {
    return d.os()
        .lines.entrySet()
        .stream()
        .filter(e -> e.getKey().startsWith("vty "))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseGet(() -> d.os().line("vty 0 4"));
  }
  private static String connect(Console c, Device d, Network n, long id, String[] a) {
    boolean ssh = a[0].equals("ssh");
    if (ssh && !a[1].equals("-l"))
      throw new IllegalArgumentException("ssh -l USER IP");
    String address = n.resolve(id, a[a.length - 1]);
    var test = n.probe(id, address, "tcp", ssh ? 22 : 23);
    if (!test.success())
      throw new IllegalArgumentException(test.text());
    long peerId = test.path().getLast();
    Device peer = n.devices.get(peerId);
    if (peer == null || !(peer.kind.equals("router")||peer.kind.equals("switch")))
      throw new IllegalArgumentException("Remote router or switch required");
    var line = vty(peer);
    if (!line.transport.contains(ssh ? "ssh" : "telnet"))
      throw new IllegalArgumentException("VTY transport is disabled");
    if (ssh && (peer.os().rsaPublic.isEmpty() || line.login.isEmpty()))
      throw new IllegalArgumentException("SSH requires RSA keys and VTY authentication");
    String source = n.sourceAddress(id, address);
    if (!AccessLists.allows(peer, line.access, source, address, "tcp", ssh ? 22 : 23))
      throw new IllegalArgumentException("VTY access-class denied connection");
    c.localMode = c.mode;
    c.loginPeer = peerId;
    c.user = ssh ? a[2] : "remote";
    if (line.login.isEmpty()) {
      c.remoteId = peerId;
      c.mode = 0;
      return "Connected to " + peer.hostname;
    }
    c.pending = ssh || line.login.equals("password") ? "remote-password" : "remote-user";
    return c.pending.endsWith("password") ? "Password:" : "Username:";
  }
  public static void merge(Device target, Device source) {
    RouterState old = target.os(), fresh = source.os();
    for (var r : target.routes)
      if (!source.routes.contains(r))
        source.routes.add(r);
    for (int i = 0; i < target.ports.size(); i++)
      if (source.findInterface(target.portName(i), false) < 0)
        source.ports.add(target.ports.get(i));
    old.chapSecrets.forEach(fresh.chapSecrets::putIfAbsent);
    old.users.forEach(fresh.users::putIfAbsent);
    old.privileges.forEach(fresh.privileges::putIfAbsent);
    old.hosts.forEach(fresh.hosts::putIfAbsent);
    old.lines.forEach(fresh.lines::putIfAbsent);
    old.routing.forEach(fresh.routing::putIfAbsent);
    old.pools.forEach(fresh.pools::putIfAbsent);
    old.natPools.forEach(fresh.natPools::putIfAbsent);
    old.flash.forEach(fresh.flash::putIfAbsent);
    old.acls.forEach((name, acl) -> {
      var to = fresh.acls.computeIfAbsent(name, key -> new RouterState.Acl(acl.type));
      for (var r : acl.rules)
        if (!to.rules.contains(r))
          to.rules.add(r);
    });
    for (var r : old.nat)
      if (!fresh.nat.contains(r))
        fresh.nat.add(r);
    for (var r : old.routes6)
      if (!fresh.routes6.contains(r))
        fresh.routes6.add(r);
    fresh.arp = old.arp;
    fresh.neighbors6 = old.neighbors6;
    fresh.leases = old.leases;
    fresh.conflicts = old.conflicts;
    fresh.translations = old.translations;
    fresh.natHits = old.natHits;
    fresh.natMisses = old.natMisses;
    fresh.bootMillis = old.bootMillis;
    fresh.activeUsers = old.activeUsers;
    fresh.sessionTimes = old.sessionTimes;
    if (fresh.rsaPrivate.isEmpty()) {
      fresh.rsaPrivate = old.rsaPrivate;
      fresh.rsaPublic = old.rsaPublic;
    }
    for (int i = 0; i < source.ports.size(); i++) {
      var p = source.ports.get(i);
      int oldIndex = target.findInterface(source.portName(i), false);
      if (oldIndex >= 0) {
        var before = target.ports.get(oldIndex);
        p.sent = before.sent;
        p.received = before.received;
        p.drops = before.drops;
        if (p.dhcp && before.dhcp)
          p.ip = before.ip;
        if (p.autoconfig && before.autoconfig)
          p.ipv6 = before.ipv6;
      }
    }
    ConfigStore.replace(target, source);
  }
  public static String config(Device d) {
    var s=new StringBuilder(globalConfig(d));
    for(int i:InterfaceOrder.visible(d))s.append(interfaceConfig(d,i));
    return s.append(tailConfig(d,true)).toString();
  }
  static String showConfiguration(String body){return "Building configuration...\n\nCurrent configuration : "+body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+" bytes\n!\n"+body;}
  static String globalConfig(Device d) {
    var os = d.os();
    var s = new StringBuilder("hostname " + d.hostname + "\n");
    if (!os.domainLookup)
      s.append("no ip domain-lookup\n");
    if (os.passwordEncryption)
      s.append("service password-encryption\n");
    if (!os.enableSecret.isEmpty())
      s.append("enable secret netlab-sha256 ").append(os.enableSecret).append('\n');
    if (!os.banner.isEmpty())
      s.append("banner motd #").append(os.banner).append("#\n");
    if (!os.domain.isEmpty())
      s.append("ip domain-name ").append(os.domain).append('\n');
    if (!os.dns.isEmpty())
      s.append("ip name-server ").append(String.join(" ", os.dns)).append('\n');
    os.hosts.forEach((k, v) -> s.append("ip host ").append(k).append(' ').append(v).append('\n'));
    os.users.forEach((k, v)
                         -> s.append("username ")
                             .append(k)
                             .append(" privilege ")
                             .append(os.privileges.getOrDefault(k, 1))
                             .append(" secret netlab-sha256 ")
                             .append(v)
                             .append('\n'));
    if (!d.gateway.isEmpty() && d.ports.stream().noneMatch(p -> p.dhcp))
      s.append("ip default-gateway ").append(d.gateway).append('\n');
    if (os.ipv6Routing)
      s.append("ipv6 unicast-routing\n");
    if (!os.cdp)
      s.append("no cdp run\n");
    return s.toString();
  }
  static String interfaceConfig(Device d,int i) {
    var s=new StringBuilder();
      var p = d.ports.get(i);
      if (p.hidden || p.console)
        return "";
      s.append("interface ").append(d.portName(i)).append('\n');
      if (!p.description.isEmpty())
        s.append(" description ").append(p.description).append('\n');
      if(p.l2)s.append(" switchport mode ").append(p.trunk?"trunk":p.switchMode).append("\n switchport access vlan ").append(p.accessVlan)
          .append("\n switchport trunk native vlan ").append(p.trunkNative).append("\n switchport trunk allowed vlan ").append(p.allowedVlans).append('\n');
      s.append(p.dhcp           ? " ip address dhcp\n"
               : p.ip.isEmpty() ? " no ip address\n"
                                : " ip address " + p.ip + " " + p.mask + "\n")
          .append(p.up ? " no shutdown\n" : " shutdown\n");
      s.append(" duplex ")
          .append(p.duplex)
          .append("\n speed ")
          .append(p.speed)
          .append("\n bandwidth ")
          .append(p.bandwidth)
          .append('\n');
      if (!p.helper.isEmpty())
        s.append(" ip helper-address ").append(p.helper).append('\n');
      if (p.vlan > 0 && d.kind.equals("router"))
        s.append(" encapsulation dot1Q ").append(p.vlan).append(p.nativeVlan ? " native" : "").append('\n');
      if (d.portName(i).startsWith("Serial")) {
        s.append(" clock rate ")
            .append(p.clock)
            .append("\n encapsulation ")
            .append(p.encapsulation)
            .append('\n');
        if (!p.authentication.isEmpty())
          s.append(" ppp authentication ").append(p.authentication).append('\n');
        if (!p.papUser.isEmpty())
          s.append(" ppp pap sent-username ")
              .append(p.papUser)
              .append(" password 7 ")
              .append(p.papPassword)
              .append('\n');
      }
      if (!p.aclIn.isEmpty())
        s.append(" ip access-group ").append(p.aclIn).append(" in\n");
      if (!p.aclOut.isEmpty())
        s.append(" ip access-group ").append(p.aclOut).append(" out\n");
      if (!p.nat.isEmpty())
        s.append(" ip nat ").append(p.nat).append('\n');
      if (p.ipv6Enabled)
        s.append(" ipv6 enable\n");
      if (!p.ipv6.isEmpty() && !p.autoconfig)
        s.append(" ipv6 address ").append(p.ipv6).append('\n');
      if (!p.linkLocal.isEmpty())
        s.append(" ipv6 address ").append(p.linkLocal).append(" link-local\n");
      if (p.autoconfig)
        s.append(" ipv6 address autoconfig\n");
      if (!p.ospfProcess.isEmpty())
        s.append(" ip ospf ").append(p.ospfProcess).append(" area ").append(p.ospfArea).append('\n');
      if (p.ospfCost != 1)
        s.append(" ip ospf cost ").append(p.ospfCost).append('\n');
      if (p.ospfPriority != 1)
        s.append(" ip ospf priority ").append(p.ospfPriority).append('\n');
      if (!p.cdp)
        s.append(" no cdp enable\n");
      s.append("!\n");
    return s.toString();
  }
  static String tailConfig(Device d,boolean includeLines) {
    var os=d.os();var s=new StringBuilder();
    for (var r : d.routes)
      s.append("ip route ")
          .append(r.network())
          .append(' ')
          .append(r.mask())
          .append(' ')
          .append(r.iface().isEmpty() ? r.nextHop() : r.iface())
          .append(r.distance() == 1 ? "" : " " + r.distance())
          .append('\n');
    for (var r : os.routes6)
      s.append("ipv6 route ")
          .append(r.prefix())
          .append(' ')
          .append(r.iface().isEmpty() ? "" : r.iface() + " ")
          .append(r.next())
          .append('\n');
    if (includeLines) os.lines.forEach((name, l) -> {
      s.append("line ").append(name).append('\n');
      if (!l.password.isEmpty())
        s.append(" password ")
            .append(os.passwordEncryption ? "7 " + l.password : ConfigStore.untype7(l.password))
            .append('\n');
      if (!l.login.isEmpty())
        s.append(" login").append(l.login.equals("local") ? " local" : "").append('\n');
      if (l.synchronous)
        s.append(" logging synchronous\n");
      s.append(" exec-timeout ")
          .append(l.timeout / 60)
          .append(' ')
          .append(l.timeout % 60)
          .append("\n transport input ")
          .append(l.transport)
          .append('\n');
      if (!l.access.isEmpty())
        s.append(" access-class ").append(l.access).append(" in\n");
      s.append("!\n");
    });
    os.routing.forEach((key, r) -> {
      s.append("router ").append(r.protocol).append(r.protocol.equals("rip") ? "" : " " + r.id).append('\n');
      if (r.protocol.equals("rip"))
        s.append(" version ").append(r.version).append('\n');
      if (!r.routerId.isEmpty())
        s.append(" router-id ").append(r.routerId).append('\n');
      for (var a : r.networks)
        s.append(" network ")
            .append(a.address())
            .append(r.protocol.equals("rip") ? "" : " " + a.wildcard())
            .append(r.protocol.equals("ospf") ? " area " + a.area() : "")
            .append('\n');
      if (!r.autoSummary)
        s.append(" no auto-summary\n");
      for (String p : r.passive) s.append(" passive-interface ").append(p).append('\n');
      if (r.originate)
        s.append(" default-information originate").append(r.always ? " always" : "").append('\n');
      if (r.variance != 1)
        s.append(" variance ").append(r.variance).append('\n');
      s.append("!\n");
    });
    for (var range : os.excluded)
      s.append("ip dhcp excluded-address ").append(range[0]).append(' ').append(range[1]).append('\n');
    os.pools.forEach((name, p) -> {
      s.append("ip dhcp pool ").append(name).append('\n');
      if (!p.network.isEmpty())
        s.append(" network ").append(p.network).append(' ').append(p.mask).append('\n');
      if (!p.gateway.isEmpty())
        s.append(" default-router ").append(p.gateway).append('\n');
      if (!p.dns.isEmpty())
        s.append(" dns-server ").append(p.dns).append('\n');
      if (!p.domain.isEmpty())
        s.append(" domain-name ").append(p.domain).append('\n');
      s.append("!\n");
    });
    os.acls.forEach((name, acl)
                        -> s.append("ip access-list ")
                            .append(acl.type)
                            .append(' ')
                            .append(name)
                            .append('\n')
                            .append(AccessLists.show(acl))
                            .append("!\n"));
    os.natPools.forEach((name, p)
                            -> s.append("ip nat pool ")
                                .append(name)
                                .append(' ')
                                .append(p[0])
                                .append(' ')
                                .append(p[1])
                                .append(" netmask ")
                                .append(p[2])
                                .append('\n'));
    for (var r : os.nat) {
      s.append("ip nat inside source ");
      switch (r.type()) {
        case "static" -> s.append("static ").append(r.local()).append(' ').append(r.global());
        case "static-tcp" ->
          s.append("static tcp ")
              .append(r.local())
              .append(' ')
              .append(r.localPort())
              .append(' ')
              .append(r.global())
              .append(' ')
              .append(r.globalPort());
        case "dynamic" -> s.append("list ").append(r.acl()).append(" pool ").append(r.pool());
        case "pat" ->
          s.append("list ").append(r.acl()).append(" interface ").append(r.iface()).append(" overload");
      }
      s.append('\n');
    }
    if (!os.rsaPublic.isEmpty())
      s.append("ip ssh version ").append(os.sshVersion).append("\n! RSA public key configured\n");
    return s.append("end\n").toString();
  }
}
