package dev.netlab.core;

import java.util.*;

public final class AccessLists {
  private record Address(String value, String wildcard, int end) {}
  private static Address address(String[] a, int index) {
    if (index >= a.length)
      throw new IllegalArgumentException("ACL address required");
    if ("any".startsWith(a[index].toLowerCase(Locale.ROOT)))
      return new Address("0.0.0.0", "255.255.255.255", index + 1);
    if ("host".startsWith(a[index].toLowerCase(Locale.ROOT))) {
      if (index + 1 >= a.length)
        throw new IllegalArgumentException("Host address required");
      Device.ip(a[index + 1]);
      return new Address(a[index + 1], "0.0.0.0", index + 2);
    }
    Device.ip(a[index]);
    String wildcard = index + 1 < a.length && a[index + 1].matches("[0-9.]+") ? a[index + 1] : "0.0.0.0";
    Device.ip(wildcard);
    return new Address(a[index], wildcard,
        index
            + (wildcard.equals("0.0.0.0") && !(index + 1 < a.length && a[index + 1].equals(wildcard)) ? 1
                                                                                                      : 2));
  }
  public static RouterState.AclRule parse(String type, String text) {
    String[] a = text.strip().split("\\s+");
    boolean permit = "permit".startsWith(a[0].toLowerCase(Locale.ROOT));
    if (!permit && !"deny".startsWith(a[0].toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("permit or deny required");
    boolean extended = type.equals("extended");
    String protocol = extended
        ? a.length > 1 ? CommandGrammar.keyword(a[1], List.of("ip", "icmp", "tcp", "udp")) : ""
        : "ip";
    if (!List.of("ip", "icmp", "tcp", "udp").contains(protocol))
      throw new IllegalArgumentException("Supported ACL protocols: ip, icmp, tcp, udp");
    Address src = address(a, extended ? 2 : 1),
            dst = extended ? address(a, src.end) : new Address("0.0.0.0", "255.255.255.255", src.end);
    int port = -1, index = dst.end;
    if (index < a.length) {
      if (!"eq".startsWith(a[index].toLowerCase(Locale.ROOT)) || index + 2 != a.length
          || !List.of("tcp", "udp").contains(protocol))
        throw new IllegalArgumentException("Expected eq PORT");
      port = port(a[index + 1]);
    }
    return new RouterState.AclRule(permit, protocol, src.value, src.wildcard, dst.value, dst.wildcard, port);
  }
  public static int port(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "www", "http" -> 80;
      case "https" -> 443;
      case "ssh" -> 22;
      case "telnet" -> 23;
      case "domain" -> 53;
      default -> Addressing.number(value, 0, 65535);
    };
  }
  public static boolean allows(
      Device device, String acl, String source, String destination, String protocol, int port) {
    if (acl == null || acl.isEmpty())
      return true;
    RouterState.Acl list = device.os().acls.get(acl);
    if (list == null)
      return true; // IOS ignores a reference until its ACL exists.
    for (var rule : list.rules)
      if ((rule.protocol().equals("ip") || rule.protocol().equals(protocol))
          && Addressing.matches(source, rule.source(), rule.sourceWildcard())
          && Addressing.matches(destination, rule.destination(), rule.destinationWildcard())
          && (rule.port() < 0 || rule.port() == port))
        return rule.permit();
    return false;
  }
  public static String show(RouterState.Acl acl) {
    StringBuilder s = new StringBuilder();
    for (var r : acl.rules)
      s.append(r.permit() ? "permit " : "deny ")
          .append(acl.type.equals("extended") ? r.protocol() + " " : "")
          .append(r.source())
          .append(' ')
          .append(r.sourceWildcard())
          .append(acl.type.equals("extended") ? " " + r.destination() + " " + r.destinationWildcard() : "")
          .append(r.port() < 0 ? "" : " eq " + r.port())
          .append('\n');
    return s.toString();
  }
}
