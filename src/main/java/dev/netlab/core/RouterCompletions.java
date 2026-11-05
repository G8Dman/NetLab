package dev.netlab.core;

import java.util.*;

/** Parameter suggestions for the same supported command catalog. */
public final class RouterCompletions {
  private static List<String> aclOptions(String type, List<String> args) {
    int offset = 0;
    if (type.equals("extended")) {
      if (args.isEmpty())
        return List.of("ip", "icmp", "tcp", "udp");
      offset = 1;
    }
    for (int address = 0; address < (type.equals("extended") ? 2 : 1); address++) {
      if (args.size() == offset)
        return List.of("any", "host");
      String first = args.get(offset).toLowerCase(Locale.ROOT);
      int count = "any".startsWith(first) ? 1 : 2;
      if (args.size() < offset + count)
        return List.of();
      offset += count;
    }
    return type.equals("extended") && args.size() == offset
            && ("tcp".startsWith(args.getFirst()) || "udp".startsWith(args.getFirst()))
        ? List.of("eq")
        : List.of();
  }
  public static CommandGrammar.Completion complete(
      Console cli, Device d, CommandGrammar.Spec spec, List<String> input, String fragment, String original) {
    int count = spec.words().split(" ").length;
    List<String> args = input.subList(Math.min(count, input.size()), input.size());
    int index = args.size();
    String word = spec.words();
    List<String> choices = new ArrayList<>();
    boolean interfaces = false;
    if (word.equals("permit") || word.equals("deny")) {
      var acl = d.os().acls.get(cli.context);
      if (acl != null)
        choices = aclOptions(acl.type, args);
    } else if (word.equals("access-list")) {
      if (index == 0)
        choices = List.of("1", "100");
      else if (index == 1)
        choices = List.of("permit", "deny");
      else {
        try {
          int number = Integer.parseInt(args.getFirst());
          choices = aclOptions(number < 100 ? "standard" : "extended", args.subList(2, args.size()));
        } catch (NumberFormatException ignored) {
        }
      }
    } else if (word.equals("no access-list") && index == 0)
      choices = new ArrayList<>(d.os().acls.keySet());
    else if (word.equals("show controllers serial")) {
      choices = new ArrayList<>();
      for (int i = 0; i < d.ports.size(); i++)
        if (d.portName(i).startsWith("Serial"))
          choices.add(d.portName(i).substring(6));
    } else if (word.equals("copy"))
      choices = List.of("running-config", "startup-config", "tftp:");
    else if (word.equals("username")) {
      if (index == 1)
        choices = List.of("privilege", "secret");
      else if (index == 3)
        choices = List.of("secret");
    } else if (word.equals("crypto key generate rsa")) {
      if (index == 0)
        choices = List.of("modulus");
      else if (index == 1)
        choices = List.of("1024", "2048", "3072", "4096");
    } else if (word.equals("transport input"))
      choices = List.of("ssh", "telnet");
    else if (word.equals("duplex"))
      choices = List.of("auto", "full", "half");
    else if (word.equals("speed"))
      choices = List.of("auto", "10", "100", "1000");
    else if (word.equals("encapsulation")) {
      if (index == 0)
        choices = List.of("dot1Q", "hdlc", "ppp");
      else if (index == 2)
        choices = List.of("native");
    } else if (word.equals("ppp authentication"))
      choices = List.of("pap", "chap");
    else if (word.equals("ppp pap sent-username") && index == 1)
      choices = List.of("password");
    else if (word.equals("ip ospf")) {
      if (index == 0)
        choices = List.of("cost", "priority");
      else if (index == 1 && !args.getFirst().equals("cost") && !args.getFirst().equals("priority"))
        choices = List.of("area");
    } else if (word.equals("network") && cli.mode == 5
        && d.os().routing.get(cli.context).protocol.equals("ospf") && index == 2)
      choices = List.of("area");
    else if (word.equals("default-information originate"))
      choices = List.of("always");
    else if (word.equals("ip access-group") || word.equals("no ip access-group")
        || word.equals("access-class")) {
      if (index == 0)
        choices = new ArrayList<>(d.os().acls.keySet());
      else if (index == 1)
        choices = word.equals("access-class") ? List.of("in") : List.of("in", "out");
    } else if (word.equals("ip nat inside source list")) {
      if (index == 0)
        choices = new ArrayList<>(d.os().acls.keySet());
      if (index == 1)
        choices = List.of("pool", "interface");
      if (index == 2) {
        if ("pool".startsWith(args.get(1)))
          choices = new ArrayList<>(d.os().natPools.keySet());
        else
          interfaces = true;
      }
      if (index == 3)
        choices = List.of("overload");
    } else if (word.equals("ip nat pool") && index == 3)
      choices = List.of("netmask");
    else if (word.equals("ip nat inside source static") && index == 0)
      choices = List.of("tcp");
    else if (word.equals("ipv6 address")) {
      if (index == 0)
        choices = List.of("autoconfig");
      if (index == 1)
        choices = List.of("link-local");
    } else if (word.equals("ip address") && index == 0)
      choices = List.of("dhcp");
    else if (word.equals("version"))
      choices = List.of("2");
    else if (word.equals("ip ssh version"))
      choices = List.of("2");
    else if (word.equals("clear ip nat translation"))
      choices = List.of("*");
    else if (word.equals("ssh") && index == 0)
      choices = List.of("-l");
    else if (word.equals("ip dhcp pool") && index == 0)
      choices = new ArrayList<>(d.os().pools.keySet());
    else if (word.equals("no username") && index == 0)
      choices = new ArrayList<>(d.os().users.keySet());
    else if ((word.equals("ip route") || word.equals("no ip route")) && index == 2)
      interfaces = true;
    else if ((word.equals("ipv6 route") || word.equals("no ipv6 route")) && index == 1)
      interfaces = true;
    else if (word.endsWith("passive-interface") || word.equals("show interfaces")
        || word.equals("show ip interface") || word.equals("do show interfaces")
        || word.equals("do show ip interface"))
      interfaces = true;
    if (interfaces) {
      choices = new ArrayList<>();
      for (int i = 0; i < d.ports.size(); i++) {
        if (d.ports.get(i).hidden)
          continue;
        String name = d.portName(i), family = name.toLowerCase(Locale.ROOT).replaceAll("[0-9].*", "");
        String f = fragment.toLowerCase(Locale.ROOT).replaceAll("[0-9].*", "");
        String digits = fragment.substring(f.length());
        if (family.startsWith(f) && name.substring(family.length()).startsWith(digits)
            || name.toLowerCase(Locale.ROOT).startsWith(fragment.toLowerCase(Locale.ROOT)))
          choices.add(name);
      }
    } else {
      String prefix = fragment.toLowerCase(Locale.ROOT);
      choices = choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    if (choices.isEmpty())
      return new CommandGrammar.Completion(original, List.of());
    String base = spec.words() + (args.isEmpty() ? "" : " " + String.join(" ", args)) + " ";
    var values = choices.stream().distinct().sorted().map(s -> base + s + " ").toList();
    String common = values.getFirst();
    for (String choice : values)
      while (!choice.startsWith(common)) common = common.substring(0, common.length() - 1);
    return new CommandGrammar.Completion(values.size() == 1 ? values.getFirst() : common, values);
  }
}
