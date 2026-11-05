package dev.netlab.core;

import java.util.*;

public final class Console {
  public int mode = 0, selected = -1;
  public final String sessionId = UUID.randomUUID().toString();
  public int localMode = 1;
  public String context = "", pending = "", user = "console";
  public long remoteId = Long.MIN_VALUE, loginPeer, transferPeer, lastActivity = System.currentTimeMillis();
  public boolean authenticated = true, initialized;
  public long loginEpoch;
  public final List<String> history = new ArrayList<>();
  private Device promptDevice;
  public String prompt(Device fallback) {
    Device d = remoteId != Long.MIN_VALUE && promptDevice != null ? promptDevice : fallback;
    if (pending.endsWith("password") || pending.equals("enable"))
      return "Password:";
    if (pending.endsWith("user"))
      return "Username:";
    if (!pending.isEmpty())
      return "> ";
    String suffix = switch (mode) {
      case 0 -> ">";
      case 1 -> "#";
      case 2 -> "(config)#";
      case 3 -> "(config-if)#";
      case 4 -> "(config-line)#";
      case 5 -> "(config-router)#";
      case 6 -> "(dhcp-config)#";
      case 7 -> "(config-std-nacl)#";
      case 8 -> "(config-vlan)#";
      default -> "#";
    };
    return d.hostname + suffix;
  }
  public String open(Device d) {
    if (!d.kind.equals("router")&&!d.kind.equals("switch"))
      return "";
    var line = d.os().line("console 0");
    boolean expired = line.timeout > 0 && System.currentTimeMillis() - lastActivity > line.timeout * 1000L;
    if (!initialized || expired) {
      loginEpoch++;
      initialized = true;
      mode = 0;
      selected = -1;
      remoteId = Long.MIN_VALUE;
      authenticated = line.login.isEmpty();
      pending = authenticated ? "" : line.login.equals("local") ? "console-user" : "console-password";
    }
    lastActivity = System.currentTimeMillis();
    touch(d, false);
    return d.os().banner;
  }
  public void touch(Device d, boolean remote) {
    d.os().activeUsers.put(sessionId, (remote ? "vty " : "con0 ") + user);
    d.os().sessionTimes.put(sessionId, System.currentTimeMillis());
  }
  public String echo(String command) {
    if (pending.endsWith("password") || pending.equals("enable"))
      return "<hidden>";
    return sensitive(command) ? "<credential command hidden>" : command;
  }
  public static boolean sensitive(String command) {
    if (command.isBlank())
      return false;
    String[] words = command.toLowerCase(Locale.ROOT).strip().split("\\s+");
    return "password".startsWith(words[0])
        || (("enable".startsWith(words[0]) || "username".startsWith(words[0]))
            && java.util.Arrays.stream(words).skip(1).anyMatch(w -> !w.isEmpty() && "secret".startsWith(w)))
        || ("ppp".startsWith(words[0])
            && java.util.Arrays.stream(words).skip(1).anyMatch(w -> !w.isEmpty() && "password".startsWith(w)))
        || command.toLowerCase(Locale.ROOT).matches(".*\\b(secret|password)\\b.*")
        || command.toLowerCase(Locale.ROOT).startsWith("mobile pair ");
  }
  public CommandGrammar.Completion complete(Device d, String prefix) {
    return pending.isEmpty()
        ? CommandGrammar.complete(
              this, remoteId != Long.MIN_VALUE && promptDevice != null ? promptDevice : d, prefix)
        : new CommandGrammar.Completion(prefix, List.of());
  }
  public String execute(Device d, Network n, long id, String line) {
    if (!initialized)
      open(d);
    if (line.equals("\u001a")) {
      pending = "";
      if (mode >= 2) {
        mode = 1;
        selected = -1;
        context = "";
      }
      return "";
    }
    if (remoteId != Long.MIN_VALUE) {
      Device peer = n.devices.get(remoteId);
      if (peer == null || !Hardware.powered(peer)) {
        remoteId = Long.MIN_VALUE;
        mode = 0;
        return "% Remote device disconnected";
      }
      d = peer;
      id = remoteId;
      promptDevice = peer;
    }
    if (d.kind.equals("router")||d.kind.equals("switch")) {
      var access = remoteId == Long.MIN_VALUE ? d.os().line("console 0") : RouterCommands.vty(d);
      if (access.timeout > 0 && System.currentTimeMillis() - lastActivity > access.timeout * 1000L) {
        initialized = false;
        open(d);
        return "% Session idle timeout";
      }
    }
    lastActivity = System.currentTimeMillis();
    touch(d, remoteId != Long.MIN_VALUE);
    if (!pending.isEmpty()) {
      try {
        String response = RouterCommands.pending(this, d, n, id, line);
        n.changed.add(id);
        if (remoteId != Long.MIN_VALUE) {
          promptDevice = n.devices.get(remoteId);
          touch(promptDevice, true);
        }
        return response;
      } catch (IllegalArgumentException e) {
        pending = "";
        return "% " + e.getMessage();
      }
    }
    if (!authenticated) {
      pending = "console-user";
      return "Username:";
    }
    if (!line.isBlank()) {
      history.add(echo(line));
      while (history.size() > 50) history.removeFirst();
    }
    if (line.isBlank())
      return "";
    if (line.strip().equals("?") || line.strip().equalsIgnoreCase("help"))
      return CommandGrammar.help(this, d);
    if (line.stripTrailing().endsWith("?")) {
      return CommandGrammar.contextHelp(this, d, line.stripTrailing().substring(0, line.stripTrailing().length() - 1));
    }
    if (d.kind.equals("switch") && (line.strip().equalsIgnoreCase("show ip dhcp snooping")
        || line.strip().equalsIgnoreCase("show ip dhcp snooping binding")))
      return SwitchCommands.execute(this, d, n, id, line.strip().toLowerCase(Locale.ROOT));
    var resolved = CommandGrammar.resolve(this, d, line);
    if (!resolved.error().isEmpty())
      return resolved.error();
    line = resolved.value();
    if (line.startsWith("do "))
      line = line.substring(3);
    return executeCanonical(d, n, id, line);
  }
  private String executeCanonical(Device d, Network n, long id, String line) {
    String[] a = line.trim().split("\\s+");
    String cmd = line.trim().toLowerCase(Locale.ROOT);
    try {
      if (d.kind.equals("router")) {
        if(d.ports.stream().anyMatch(p->p.l2)&&((mode==3&&selected>=0&&d.ports.get(selected).l2)
            ||line.startsWith("vlan ")||line.startsWith("no vlan ")||mode==8||line.startsWith("show vlan")||line.startsWith("show mac"))){
          if(line.startsWith("ip address "))return "% L2 port: assign IP to interface Vlan instead";
          String switched=SwitchCommands.execute(this,d,n,id,line);if(switched!=null){n.changed.add(id);return switched;}
        }
        String result = RouterCommands.execute(this, d, n, id, line);
        n.changed.add(id);
        if (remoteId != Long.MIN_VALUE)
          promptDevice = n.devices.get(remoteId);
        if (result != null)
          return result;
      }
      if (d.kind.equals("switch")) {
        String result = SwitchCommands.execute(this, d, n, id, line);
        n.changed.add(id);
        if (result != null)
          return result;
        result=RouterCommands.execute(this,d,n,id,line);
        if(result!=null)return result;
      }
      if (d.kind.equals("pc")) {
        String result = PCCommands.execute(this, d, n, id, line);
        n.changed.add(id);
        if (result != null)
          return result;
      }
      if (cmd.equals("enable") || cmd.equals("en")) {
        mode = 1;
        return "";
      }
      if (cmd.equals("disable")) {
        mode = 0;
        selected = -1;
        return "";
      }
      if (cmd.equals("end")) {
        mode = 1;
        selected = -1;
        return "";
      }
      if (cmd.equals("exit")) {
        if (mode < 2 && remoteId != Long.MIN_VALUE) {
          d.os().activeUsers.remove(sessionId);
          d.os().sessionTimes.remove(sessionId);
          remoteId = Long.MIN_VALUE;
          promptDevice = null;
          mode = localMode;
          user = "console";
          return "Connection closed";
        }
        mode = mode >= 3 ? 2 : Math.max(0, mode - 1);
        selected = -1;
        context = "";
        return "";
      }
      if (cmd.equals("configure terminal") || cmd.equals("conf t")) {
        if (mode != 1)
          return "% Enter enable first";
        mode = 2;
        return "Enter configuration commands, one per line.";
      }
      if (cmd.equals("show ip interface brief")) {
        var s = new StringBuilder("Interface                 IP-Address       Admin   Link\n");
        for (int i = 0; i < d.ports.size(); i++) {
          var p = d.ports.get(i);
          if (p.hidden)
            continue;
          s.append(String.format("%-25s %-16s %-7s %s%n", d.portName(i), p.ip.isEmpty() ? "unassigned" : p.ip,
              p.up ? "up" : "down", n.linkUp(id, i) ? "up" : "down"));
        }
        return s.toString();
      }
      if (cmd.equals("show running-config")) {
        var s = new StringBuilder("hostname " + d.hostname + "\n");
        for (int i = 0; i < d.ports.size(); i++) {
          var p = d.ports.get(i);
          s.append("interface " + d.portName(i) + "\n "
              + (p.ip.isEmpty() ? "no ip address" : "ip address " + p.ip + " " + p.mask) + "\n "
              + (p.up ? "no shutdown" : "shutdown") + "\n");
        }
        if (!d.gateway.isEmpty())
          s.append("ip default-gateway " + d.gateway + "\n");
        for (var r : d.routes)
          s.append("ip route " + r.network() + " " + r.mask() + " " + r.nextHop() + "\n");
        return s.toString();
      }
      if (cmd.equals("show ip route")) {
        var s = new StringBuilder("C = connected, S = static\n");
        for (int i = 0; i < d.ports.size(); i++) {
          var p = d.ports.get(i);
          if (p.up && !p.ip.isEmpty())
            s.append("C " + Device.networkAddress(p.ip, p.mask) + " mask " + p.mask + " via " + d.portName(i)
                + "\n");
        }
        for (var r : d.routes)
          s.append("S " + r.network() + " mask " + r.mask() + " via " + r.nextHop() + "\n");
        return s.toString();
      }
      if (cmd.equals("ipconfig dhcp") && d.kind.equals("pc")) {
        var p = d.ports.getFirst();
        p.up = true;
        p.dhcp = true;
        p.ip = "";
        n.changed.add(id);
        return n.renewDhcp(id, 0);
      }
      if (cmd.startsWith("switchport ") && d.kind.equals("switch") && mode == 3) {
        var p = d.ports.get(selected);
        if (cmd.equals("switchport mode trunk"))
          p.trunk = true;
        else if (cmd.equals("switchport mode access"))
          p.trunk = false;
        else
          p.vlan = Addressing.number(a[a.length - 1], 1, 4094);
        n.changed.add(id);
        return "";
      }
      if (a[0].equalsIgnoreCase("ping") && a.length == 2)
        return n.ping(id, a[1]).text();
      if (a[0].equalsIgnoreCase("ipconfig") && (a.length == 3 || a.length == 4) && d.kind.equals("pc")) {
        Device.ip(a[1]);
        Device.mask(a[2]);
        if (a.length == 4)
          Device.ip(a[3]);
        var p = d.ports.getFirst();
        p.ip = a[1];
        p.mask = a[2];
        p.up = true;
        d.gateway = a.length == 4 ? a[3] : "";
        return "IPv4 configuration applied.";
      }
      if (mode < 2)
        return "% Configuration requires: enable, configure terminal";
      if (a[0].equalsIgnoreCase("hostname") && a.length == 2 && a[1].matches("[a-zA-Z0-9_-]{1,32}")) {
        d.hostname = a[1];
        return "";
      }
      if (a[0].equalsIgnoreCase("interface")) {
        int index =
            d.findInterface(String.join("", Arrays.asList(a).subList(1, a.length)), d.kind.equals("router") || d.kind.equals("switch"));
        if (index < 0)
          return "% Interface does not exist";
        selected = index;
        context = "";
        mode = 3;
        return "";
      }
      if (cmd.startsWith("ip route ") && a.length == 5 && DeviceKinds.router(d.kind)) {
        Device.ip(a[2]);
        Device.mask(a[3]);
        Device.ip(a[4]);
        if (!Device.networkAddress(a[2], a[3]).equals(a[2]))
          return "% Inconsistent network and mask";
        if (d.routes.size() >= 128
            && d.routes.stream().noneMatch(r -> r.network().equals(a[2]) && r.mask().equals(a[3])))
          return "% Prototype limit: 128 static routes";
        d.routes.removeIf(r -> r.network().equals(a[2]) && r.mask().equals(a[3]));
        d.routes.add(new Device.Route(a[2], a[3], a[4]));
        return "";
      }
      if (cmd.startsWith("ip default-gateway ") && a.length == 3) {
        Device.ip(a[2]);
        d.gateway = a[2];
        return "";
      }
      if (mode != 3 || selected < 0)
        return "% Select an interface first";
      var p = d.ports.get(selected);
      if (cmd.equals("no shutdown") || cmd.equals("no shut")) {
        p.up = true;
        return "% Interface enabled";
      }
      if (cmd.equals("shutdown")) {
        p.up = false;
        return "% Interface administratively down";
      }
      if (cmd.startsWith("ip address ") && a.length == 4 && !d.kind.equals("switch")) {
        Device.ip(a[2]);
        Device.mask(a[3]);
        p.ip = a[2];
        p.mask = a[3];
        p.dhcp = false;
        return "";
      }
      return "% Unsupported command. Type ?";
    } catch (IllegalArgumentException e) {
      return "% " + e.getMessage();
    }
  }
}
