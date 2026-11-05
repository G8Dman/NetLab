package dev.netlab.core;

import java.util.*;

/**
 * Instant convergence, bounded shortest-path teaching model. No hello timers,
 * LSA/DUAL packet implementation or cross-protocol redistribution.
 */
public final class RoutingModel {
  public record Learned(
      String network, String mask, String nextHop, int port, int distance, int metric, String protocol) {}
  public record Edge(long id, String key, int localPort, int remotePort, int metric) {}
  private record
      Visit(long id, String key, int cost, int hops, int firstPort, String firstIp, List<Long> path) {}
  public static String routerId(Device d, RouterState.Routing r) {
    if (!r.routerId.isEmpty())
      return r.routerId;
    String best = "0.0.0.0";
    boolean loopback = false;
    for (int i = 0; i < d.ports.size(); i++) {
      var p = d.ports.get(i);
      if (!p.up || p.ip.isEmpty())
        continue;
      boolean lo = d.portName(i).startsWith("Loopback");
      if (lo && !loopback) {
        loopback = true;
        best = "0.0.0.0";
      }
      if (lo == loopback && Device.ip(p.ip) > Device.ip(best))
        best = p.ip;
    }
    return best;
  }
  public static boolean eligible(Device d, RouterState.Routing r, int port) {
    var p = d.ports.get(port);
    if (p.ip.isEmpty())
      return false;
    if (r.protocol.equals("ospf") && p.ospfProcess.equals(r.id))
      return true;
    for (var network : r.networks)
      if (Addressing.matches(p.ip, network.address(), network.wildcard()))
        return true;
    return false;
  }
  public static String area(Device d, RouterState.Routing r, int port) {
    var p = d.ports.get(port);
    if (p.ospfProcess.equals(r.id))
      return p.ospfArea;
    for (var a : r.networks)
      if (Addressing.matches(p.ip, a.address(), a.wildcard()))
        return a.area();
    return "0";
  }
  public static List<Edge> neighbors(Network n, long id, String key) {
    var d = n.devices.get(id);
    var r = d.os().routing.get(key);
    var out = new ArrayList<Edge>();
    if (r == null || r.protocol.equals("rip") && r.version != 2)
      return out;
    for (int p = 0; p < d.ports.size(); p++)
      if (n.interfaceUp(id, p) && eligible(d, r, p)) {
        if (!r.protocol.equals("rip") && r.passive.contains(d.portName(p)))
          continue;
        for (var h : n.lanPeers(id, p)) {
          var other = n.devices.get(h.end().device());
          if (!other.kind.equals("router"))
            continue;
          var a = d.ports.get(p);
          var b = other.ports.get(h.end().port());
          if (b.ip.isEmpty() || !Device.same(a.ip, b.ip, a.mask) || !Device.same(a.ip, b.ip, b.mask))
            continue;
          for (var entry : other.os().routing.entrySet()) {
            var peer = entry.getValue();
            if (!peer.protocol.equals(r.protocol) || !eligible(other, peer, h.end().port())
                || peer.passive.contains(other.portName(h.end().port())))
              continue;
            if (r.protocol.equals("rip") && peer.version != 2
                || r.protocol.equals("eigrp") && !r.id.equals(peer.id))
              continue;
            if (r.protocol.equals("ospf")
                && (!a.mask.equals(b.mask) || !area(d, r, p).equals(area(other, peer, h.end().port()))
                    || routerId(d, r).equals(routerId(other, peer))
                    || a.ospfPriority == 0 && b.ospfPriority == 0))
              continue;
            int metric = r.protocol.equals("rip") ? 1
                : r.protocol.equals("ospf")
                ? (a.ospfCostManual ? a.ospfCost : Math.max(1, 100000 / Math.max(1, a.bandwidth)))
                : Math.max(1, 10000000 / Math.max(1, a.bandwidth));
            out.add(new Edge(h.end().device(), entry.getKey(), p, h.end().port(), metric));
          }
        }
      }
    return out;
  }
  public static List<Learned> routes(Network n, long source) {
    Device origin = n.devices.get(source);
    var out = new ArrayList<Learned>();
    for (var initial : origin.os().routing.entrySet()) {
      String protocol = initial.getValue().protocol;
      var queue = new PriorityQueue<Visit>(Comparator.comparingInt(Visit::cost));
      var best = new HashMap<String, Integer>();
      queue.add(new Visit(source, initial.getKey(), 0, 0, -1, "", List.of(source)));
      int work = 0;
      while (!queue.isEmpty() && work++ < 4096) {
        var v = queue.remove();
        String state = v.id + ":" + v.key + ":" + v.firstPort;
        if (best.getOrDefault(state, Integer.MAX_VALUE) <= v.cost)
          continue;
        best.put(state, v.cost);
        Device d = n.devices.get(v.id);
        var r = d.os().routing.get(v.key);
        if (r == null)
          continue;
        if (v.id != source) {
          int distance = protocol.equals("rip") ? 120 : protocol.equals("ospf") ? 110 : 90;
          for (int p = 0; p < d.ports.size(); p++)
            if (n.interfaceUp(v.id, p) && eligible(d, r, p)) {
              var iface = d.ports.get(p);
              String mask =
                  (!protocol.equals("ospf") && r.autoSummary) ? Addressing.classMask(iface.ip) : iface.mask;
              String network = Device.networkAddress(iface.ip, mask);
              out.add(new Learned(network, mask, v.firstIp, v.firstPort, distance, v.cost, protocol));
            }
          if (r.originate && (r.always || n.hasStaticDefault(v.id)))
            out.add(new Learned("0.0.0.0", "0.0.0.0", v.firstIp, v.firstPort, distance, v.cost, protocol));
        }
        if (protocol.equals("rip") && v.hops >= 15)
          continue;
        for (var edge : neighbors(n, v.id, v.key))
          if (!v.path.contains(edge.id)) {
            var path = new ArrayList<>(v.path);
            path.add(edge.id);
            String next = v.id == source ? n.devices.get(edge.id).ports.get(edge.remotePort).ip : v.firstIp;
            queue.add(new Visit(edge.id, edge.key, v.cost + edge.metric, v.hops + 1,
                v.id == source ? edge.localPort : v.firstPort, next, path));
          }
      }
    }
    return out.stream().distinct().toList();
  }
}
