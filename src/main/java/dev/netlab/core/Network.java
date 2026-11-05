package dev.netlab.core;

import java.util.*;

/**
 * Discrete teaching model: Ethernet/VLAN reachability, IPv4/IPv6 forwarding,
 * ACLs, NAT and synchronous routing convergence. No host network traffic.
 */
public final class Network {
  public record Endpoint(long device, int port) {}
  public record Wire(Endpoint a, Endpoint b, double length,String type) {
    public Wire(Endpoint a,Endpoint b,double length){this(a,b,length,"auto");}
  }
  public record Result(boolean success, String text, List<Long> path) {}
  public record Hop(Endpoint end, List<Long> path,long arrival) {}
  public record Choice(int port, String nextHop, int prefix, int distance, int metric, String protocol) {}
  private record Frame(Endpoint end, int tag, List<Long> path,long arrival) {}
  public record
      Packet(String source, String destination, String protocol, int sourcePort, int destinationPort,byte[] payload) {
        Packet(String source,String destination,String protocol,int sourcePort,int destinationPort){this(source,destination,protocol,sourcePort,destinationPort,new byte[0]);}
      }
  private record Delivery(Result result, Packet packet, long device) {}
  public final Map<Long, Device> devices = new HashMap<>();
  public final List<Wire> wires = new ArrayList<>();
  public final Set<Long> changed = new HashSet<>();
  public record Traffic(Endpoint from,Endpoint to,String protocol,int vlan,boolean dropped,long start,long finish,int bytes) {}
  public interface SpeedPolicy {double speed(Endpoint from,Endpoint to,int vlan,String protocol);}
  public SpeedPolicy speedPolicy=(a,b,v,p)->1;
  public PacketClock clock=new PacketClock();
  public long cursor;
  public boolean deferCounters;
  private int frameBytes=64;
  public final List<Traffic> traffic=new ArrayList<>();
  public final List<java.util.function.BooleanSupplier> deliveryGuards=new ArrayList<>();
  public interface LinkTransit {long send(Network network,Wire wire,Endpoint from,long ready,int bytes,String protocol);}
  public final Map<String,LinkTransit> linkTransits=new HashMap<>();
  private byte[] currentPayload=new byte[0];
  public record CapturePayload(byte[] bytes,boolean encrypted){}
  public final Map<Traffic,CapturePayload> wirePayloads=new HashMap<>();
  private boolean currentEncrypted;
  public byte[] currentPayload(){return currentPayload.clone();}
  public final Switching switching=new Switching(this);
  public java.util.function.BiFunction<Long,String,String> vpnCommand;
  public java.util.function.BiFunction<Long,Packet,Packet> routerPayload=(id,packet)->packet;
  private boolean recording;
  private String frameSource="",frameDestination="",frameProtocol="ARP";
  private String payloadProtocol="ICMP";
  public double mbps = 100, latencyMs = 1;
  private boolean refreshing;
  public boolean linkUp(long id, int port) {
    Device d = devices.get(id);
    if (d == null || !Hardware.powered(d) || port < 0 || port >= d.ports.size() || d.ports.get(port).hidden || d.ports.get(port).console || d.ports.get(port).errDisabled || !d.ports.get(port).up)
      return false;
    if (d.ports.get(port).virtual)
      return interfaceUp(id, port);
    for (Wire w : wires) {
      Endpoint other = w.a.equals(new Endpoint(id, port)) ? w.b
          : w.b.equals(new Endpoint(id, port))            ? w.a
                                                          : null;
      if (other == null)
        continue;
      Device peer = devices.get(other.device);
      if (peer == null || !Hardware.powered(peer) || other.port >= peer.ports.size() || peer.ports.get(other.port).hidden || !peer.ports.get(other.port).up
          ||!CableKind.compatible(w.type,d,port,peer,other.port))
        continue;
      var a = d.ports.get(port);
      var b = peer.ports.get(other.port);
      if(w.type.equals("wifi")){
        Device client=d.kind.equals("pc")?d:peer;Device ap=d.kind.equals("pc")?peer:d;
        if(!Wireless.authenticated(client,ap)||w.length>Wireless.state(ap).range)continue;
      }
      if(CableKind.medium(d,port).equals("fiber")&&a.maxMbps!=b.maxMbps)continue;
      if(CableKind.medium(d,port).equals("phone")||CableKind.medium(d,port).equals("async"))continue;
      if(Math.min(a.maxMbps,b.maxMbps)>0&&(!a.speed.equals("auto")&&Integer.parseInt(a.speed)>Math.min(a.maxMbps,b.maxMbps)
          ||!b.speed.equals("auto")&&Integer.parseInt(b.speed)>Math.min(a.maxMbps,b.maxMbps)))continue;
      if (!a.speed.equals("auto") && !b.speed.equals("auto") && !a.speed.equals(b.speed))
        continue;
      if (d.portName(port).startsWith("Serial") || peer.portName(other.port).startsWith("Serial")) {
        if (!d.portName(port).startsWith("Serial") || !peer.portName(other.port).startsWith("Serial")
            || a.clock <= 0 || b.clock <= 0 || !a.encapsulation.equals(b.encapsulation))
          continue;
        if (a.encapsulation.equals("ppp") && (!ppp(d, peer, a, b) || !ppp(peer, d, b, a)))
          continue;
      }
      return true;
    }
    return false;
  }
  public String serialRole(long id, int port) {
    for (var w : wires) {
      if (w.a.equals(new Endpoint(id, port)))
        return w.type.equals("serial_dte")?"DTE":"DCE";
      if (w.b.equals(new Endpoint(id, port)))
        return w.type.equals("serial_dte")?"DCE":"DTE";
    }
    return "disconnected";
  }
  public boolean hasStaticDefault(long id) {
    var d = devices.get(id);
    return d.routes.stream().anyMatch(r -> {
      int p = r.iface().isEmpty() ? directPort(id, r.nextHop()) : d.findInterface(r.iface(), false);
      return r.network().equals("0.0.0.0") && r.mask().equals("0.0.0.0") && p >= 0 && interfaceUp(id, p);
    });
  }
  private boolean ppp(Device local, Device peer, Device.Port a, Device.Port b) {
    if (a.authentication.isEmpty())
      return true;
    if (a.authentication.equals("pap"))
      return !b.papPassword.isEmpty()
          && ConfigStore.verify(
              ConfigStore.untype7(b.papPassword), local.os().users.getOrDefault(b.papUser, ""));
    String secret = peer.os().chapSecrets.getOrDefault(local.hostname, "");
    return !secret.isEmpty()
        && ConfigStore.verify(ConfigStore.untype7(secret), local.os().users.getOrDefault(peer.hostname, ""));
  }
  public boolean interfaceUp(long id, int port) {
    var d = devices.get(id);
    if (d == null || port < 0 || port >= d.ports.size())
      return false;
    var p = d.ports.get(port);
    if (!Hardware.powered(d)||p.hidden||p.console || !p.up)
      return false;
    if (p.virtual) {
      if(d.portName(port).startsWith("Vlan")){
        int vlan=Integer.parseInt(d.portName(port).substring(4));var blocked=switching.blocked(vlan);
        for(int i=0;i<d.ports.size();i++)if(switching.carries(id,i,vlan)&&!blocked.contains(new Endpoint(id,i)))return true;
        return false;
      }
      if (p.parent >= 0)
        return p.vlan > 0 && linkUp(id, p.parent);
      return d.portName(port).startsWith("Loopback");
    }
    return linkUp(id, port);
  }
  public List<Hop> lanPeers(long from, int port) {
    Device origin = devices.get(from);
    if (origin == null || !interfaceUp(from, port))
      return List.of();
    var p = origin.ports.get(port);
    boolean svi=origin.portName(port).startsWith("Vlan");
    if (p.virtual && p.parent < 0 && !svi)
      return List.of();
    int physical = p.parent >= 0 ? p.parent : port, tag = p.parent >= 0 && !p.nativeVlan ? p.vlan : 0;
    var queue = new ArrayDeque<Frame>();
    var seen = new HashSet<String>();
    var peers = new ArrayList<Hop>();
    var blocked=new HashMap<Integer,Set<Endpoint>>();
    if(svi){int vlan=Integer.parseInt(origin.portName(port).substring(4));var stp=switching.blocked(vlan);
      for(int out=0;out<origin.ports.size();out++)if(switching.carries(from,out,vlan)&&!stp.contains(new Endpoint(from,out)))
        cross(queue,new Endpoint(from,out),switching.trunk(from,out)&&vlan!=switching.nativeVlan(origin.ports.get(out))?vlan:0,new ArrayList<>(List.of(from)),cursor);
    }else queue.add(new Frame(new Endpoint(from, physical), tag, new ArrayList<>(List.of(from)),cursor));
    while (!queue.isEmpty() && seen.size() < 4096) {
      var frame = queue.remove();
      String key = frame.end.device + ":" + frame.end.port + ":" + frame.tag;
      if (!seen.add(key))
        continue;
      Device d = devices.get(frame.end.device);
      if (d == null || !linkUp(frame.end.device, frame.end.port))
        continue;
      boolean start = frame.end.equals(new Endpoint(from, physical)) && frame.path.size() == 1;
      boolean layer2=d.kind.equals("switch")||d.ports.get(frame.end.port).l2;
      if (!start && !layer2) {
        for (int i = 0; i < d.ports.size(); i++) {
          var logical = d.ports.get(i);
          boolean matches = logical.parent == frame.end.port
              ? logical.vlan > 0 && (logical.nativeVlan ? frame.tag == 0 : frame.tag == logical.vlan)
              : i == frame.end.port && frame.tag == 0;
          if (matches && interfaceUp(frame.end.device, i))
            peers.add(new Hop(new Endpoint(frame.end.device, i), frame.path,frame.arrival));
        }
      }
      if (!start && layer2) {
        var input = d.ports.get(frame.end.port);
        boolean trunkIn=switching.trunk(frame.end.device,frame.end.port);
        if (!trunkIn && frame.tag != 0)
          continue;
        int vlan = frame.tag == 0 ? trunkIn?switching.nativeVlan(input):switching.accessVlan(input) : frame.tag;
        var blockedPorts=blocked.computeIfAbsent(vlan,switching::blocked);
        if(!switching.carries(frame.end.device,frame.end.port,vlan)||blockedPorts.contains(frame.end)||!switching.channelUp(frame.end.device,frame.end.port)){
          if(recording){input.drops++;dropAt(frame.end);changed.add(frame.end.device);}continue;
        }
        for(int logical=0;logical<d.ports.size();logical++)if(d.portName(logical).equals("Vlan"+vlan)&&d.ports.get(logical).up)
          peers.add(new Hop(new Endpoint(frame.end.device,logical),frame.path,frame.arrival));
        if(recording){
          if(!switching.security(frame.end.device,frame.end.port,frameSource)){dropAt(frame.end);continue;}
          switching.learn(frame.end.device,frame.end.port,vlan,frameSource);
          mirror(frame.end,vlan,frame.arrival);
          if(frameProtocol.startsWith("DHCP")&&d.os().dhcpSnooping&&input.dhcpRate>0){
            long second=System.currentTimeMillis()/1000;var window=d.os().dhcpWindows.computeIfAbsent(frame.end.port,k->new long[]{second,0});
            if(window[0]!=second){window[0]=second;window[1]=0;}
            if(++window[1]>input.dhcpRate){input.errDisabled=true;input.drops++;dropAt(frame.end);Switching.log(d,"DHCP rate exceeded "+d.portName(frame.end.port));continue;}
          }
          if(frameProtocol.equals("DHCP-OFFER")&&d.os().dhcpSnooping&&Switching.vlans(d.os().dhcpSnoopingVlans).contains(vlan)&&!input.dhcpTrust){input.drops++;dropAt(frame.end);continue;}
        }
        int known=recording&&!frameDestination.isEmpty()?switching.lookup(frame.end.device,vlan,frameDestination):-1;
        int knownGroup=known>=0?d.ports.get(known).channelGroup:0;
        var usedGroups=new HashSet<Integer>();
        for (int out = 0; out < d.ports.size(); out++) {
          var output = d.ports.get(out);
          boolean sharedRadio=out==frame.end.port&&output.medium.equals("wireless")&&Wireless.accessPoint(d)&&!Wireless.state(d).isolated;
          if (out == frame.end.port&&!sharedRadio || known>=0&&known!=out&&(knownGroup==0||knownGroup!=output.channelGroup) || !switching.carries(frame.end.device,out,vlan)
              ||blockedPorts.contains(new Endpoint(frame.end.device,out))||!switching.channelUp(frame.end.device,out)
              ||input.channelGroup>0&&input.channelGroup==output.channelGroup)
            continue;
          if(output.channelGroup>0&&(out!=switching.member(frame.end.device,output.channelGroup,vlan,recording?frameSource:"",recording?frameDestination:"",blockedPorts)||!usedGroups.add(output.channelGroup)))continue;
          int nativeVlan = switching.nativeVlan(output);
          boolean trunkOut=switching.trunk(frame.end.device,out);
          if (!trunkOut && vlan != switching.accessVlan(output))
            continue;
          cross(queue, new Endpoint(frame.end.device, out), trunkOut && vlan != nativeVlan ? vlan : 0,
              frame.path,frame.arrival);
        }
      } else if (start)
        cross(queue, frame.end, frame.tag, frame.path,frame.arrival);
    }
    return peers;
  }
  private void cross(ArrayDeque<Frame> queue, Endpoint from, int tag, List<Long> path,long ready) {
    for (Wire w : wires) {
      Endpoint other = w.a.equals(from) ? w.b : w.b.equals(from) ? w.a : null;
      if (other != null) {
        if(recording&&(w.type.equals("wifi")||w.type.equals("cellular"))&&!frameDestination.isEmpty()&&devices.get(other.device).kind.equals("pc")
            &&!Switching.mac(frameDestination).equals(Switching.mac(Addressing.mac(other.device,other.port))))continue;
        long arrival=recording?(linkTransits.containsKey(w.type)?linkTransits.get(w.type).send(this,w,from,ready,frameBytes,frameProtocol):recordHop(from,other,frameProtocol,tag,ready,w.length)):ready;
        var copy = new ArrayList<>(path);
        if (copy.getLast() != other.device)
          copy.add(other.device);
        queue.add(new Frame(other, tag, copy,arrival));
      }
    }
  }
  private long recordHop(Endpoint from,Endpoint to,String protocol,int tag,long ready,double length){
    int vlan=effectiveVlan(from,to,tag);
    var p=devices.get(from.device).ports.get(from.port);var q=devices.get(to.device).ports.get(to.port);
    double capacity=Math.min(p.maxMbps,q.maxMbps);if(!p.speed.equals("auto"))capacity=Math.min(capacity,Double.parseDouble(p.speed));if(!q.speed.equals("auto"))capacity=Math.min(capacity,Double.parseDouble(q.speed));
    if(CableKind.medium(devices.get(from.device),from.port).equals("serial"))capacity=(serialRole(from.device,from.port).equals("DCE")?p.clock:q.clock)/1_000_000.0;
    int bytes=protocol.endsWith("ARP")||protocol.endsWith("NDP")?64:protocol.contains("DHCP")?300:frameBytes;
    var slot=clock.send(from,ready,bytes,Math.min(mbps,capacity),length,latencyMs,speedPolicy.speed(from,to,vlan,protocol));
    if(!deferCounters){p.sent++;q.received++;changed.add(from.device);changed.add(to.device);}
    if(traffic.size()<2048){var event=new Traffic(from,to,protocol,vlan,false,slot.start(),slot.finish(),bytes);traffic.add(event);wirePayloads.put(event,new CapturePayload(currentPayload,currentEncrypted));}
    return slot.finish();
  }
  private int effectiveVlan(Endpoint from,Endpoint to,int tag){
    if(tag>0)return tag;
    for(var e:List.of(from,to)){var d=devices.get(e.device);if(d.kind.equals("switch")||d.kind.equals("router")&&d.ports.get(e.port).l2){var p=d.ports.get(e.port);return switching.trunk(e.device,e.port)?switching.nativeVlan(p):switching.accessVlan(p);}}
    // A one-SSID AP bridges untagged radio frames into the VLAN of its wired uplink.
    for(var e:List.of(from,to))if(Wireless.accessPoint(devices.get(e.device)))for(var w:wires){
      Endpoint peer=w.a.device==e.device?w.b:w.b.device==e.device?w.a:null;if(peer==null||w.type.equals("wifi"))continue;var d=devices.get(peer.device);var p=d.ports.get(peer.port);
      if(d.kind.equals("switch")||d.kind.equals("router")&&p.l2)return switching.trunk(peer.device,peer.port)?switching.nativeVlan(p):switching.accessVlan(p);
    }
    var p=devices.get(from.device).ports.get(from.port);return switching.trunk(from.device,from.port)?switching.nativeVlan(p):switching.accessVlan(p);
  }
  private void dropAt(Endpoint end){for(int i=traffic.size()-1;i>=0;i--){var t=traffic.get(i);if(t.to.equals(end)){traffic.set(i,new Traffic(t.from,t.to,t.protocol,t.vlan,true,t.start,t.finish,t.bytes));break;}}}
  private void mirror(Endpoint input,int vlan,long ready){
    var d=devices.get(input.device);d.os().spanSources.forEach((session,name)->{
      if(!name.equals(d.portName(input.port)))return;String dest=d.os().spanDestinations.get(session);if(dest==null)return;
      int p=d.findInterface(dest,false);if(p<0||!linkUp(input.device,p))return;var e=switching.peer(input.device,p);
      if(e!=null&&traffic.size()<2048){double length=wires.stream().filter(w->w.a.equals(e)||w.b.equals(e)).mapToDouble(Wire::length).findFirst().orElse(1);recordHop(new Endpoint(input.device,p),e,"SPAN/"+frameProtocol,vlan,ready,length);}
    });
  }
  private Hop arp(long from, int port, String address) {
    boolean previous=recording;recording=true;
    try{return arpRecorded(from,port,address);}finally{recording=previous;}
  }
  private Hop arpRecorded(long from,int port,String address) {
    int firstAttempt=traffic.size();
    boolean six = address.contains(":");
    var origin=devices.get(from);String cached=six?origin.os().neighbors6.get(address):origin.os().arp.get(address);
    if(origin.ports.get(port).medium.equals("cellular")){
      frameSource=Addressing.mac(from,port);frameDestination="";frameProtocol=payloadProtocol;
      for(var wire:wires){var local=new Endpoint(from,port);var peer=wire.a.equals(local)?wire.b:wire.b.equals(local)?wire.a:null;if(peer==null||!wire.type.equals("cellular")||!devices.containsKey(peer.device))continue;var p=devices.get(peer.device).ports.get(peer.port);if(!p.ip.equals(address))continue;frameDestination=Addressing.mac(peer.device,peer.port);var hop=lanPeers(from,port).stream().filter(h->h.end.equals(peer)).findFirst().orElse(null);if(hop!=null)cursor=Math.max(cursor,hop.arrival);return hop;}return null;
    }
    if(cached!=null){
      frameSource=Addressing.mac(from,port);frameDestination=cached.split(" ")[0];frameProtocol=payloadProtocol;
      for(var h:lanPeers(from,port)){var d=devices.get(h.end.device);var p=d.ports.get(h.end.port);
        if(six?(!p.ipv6.isEmpty()&&Addressing.ipv6(p.ipv6.split("/")[0]).equals(Addressing.ipv6(address)))
          ||p.ipv6Enabled&&Addressing.ipv6(linkLocal(h.end.device,h.end.port)).equals(Addressing.ipv6(address))
          :p.ip.equals(address)||natAddress(d,p,address)){cursor=Math.max(cursor,h.arrival);return h;}
      }
      if(six)origin.os().neighbors6.remove(address);else origin.os().arp.remove(address);
    }
    frameSource=Addressing.mac(from,port);frameDestination="";frameProtocol=six?"NDP":"ARP";
    for (var h : lanPeers(from, port)) {
      Device d = devices.get(h.end.device);
      var p = d.ports.get(h.end.port);
      boolean matches = six
          ? (!p.ipv6.isEmpty() && Addressing.ipv6(p.ipv6.split("/")[0]).equals(Addressing.ipv6(address)))
              || (p.ipv6Enabled)
                  && Addressing.ipv6(linkLocal(h.end.device, h.end.port)).equals(Addressing.ipv6(address))
          : p.ip.equals(address) || natAddress(d, p, address);
      if (matches) {
        cursor=Math.max(cursor,h.arrival);
        Device source = devices.get(from);
        String mac = Addressing.mac(h.end.device, h.end.port);
        if (six)
          source.os().neighbors6.put(address, mac);
        else
          source.os().arp.put(address, mac + " " + source.portName(port));
        changed.add(from);
        frameSource=Addressing.mac(h.end.device,h.end.port);frameDestination=Addressing.mac(from,port);
        var replies=lanPeers(h.end.device,h.end.port);cursor=Math.max(cursor,replies.stream().filter(peer->peer.end.device==from).mapToLong(Hop::arrival).min().orElse(cursor));
        frameSource=Addressing.mac(from,port);frameDestination=Addressing.mac(h.end.device,h.end.port);frameProtocol=payloadProtocol;
        var delivery=lanPeers(from,port).stream().filter(peer->peer.end.equals(h.end)).findFirst().orElse(null);if(delivery!=null)cursor=Math.max(cursor,delivery.arrival);return delivery;
      }
    }
    cursor=Math.max(cursor,traffic.subList(firstAttempt,traffic.size()).stream().mapToLong(Traffic::finish).max().orElse(cursor))+1_000_000_000L;
    return null;
  }
  private boolean natAddress(Device d, Device.Port p, String address) {
    return p.nat.equals("outside")
        && (d.os().nat.stream().anyMatch(r -> r.global().equals(address))
            || d.os().translations.stream().anyMatch(t -> t.outside().equals(address)));
  }
  public long findAddress(String address) {
    for (var e : devices.entrySet())
      for (int i = 0; i < e.getValue().ports.size(); i++)
        if (owns(e.getKey(), i, address))
          return e.getKey();
    return Long.MIN_VALUE;
  }
  private boolean owns(long id, int index, String address) {
    var p = devices.get(id).ports.get(index);
    if (!interfaceUp(id, index))
      return false;
    if (!address.contains(":"))
      return p.ip.equals(address);
    return (!p.ipv6.isEmpty() && Addressing.ipv6(p.ipv6.split("/")[0]).equals(Addressing.ipv6(address)))
        || ((p.ipv6Enabled || !p.ipv6.isEmpty())
            && Addressing.ipv6(linkLocal(id, index)).equals(Addressing.ipv6(address)));
  }
  private List<Choice> choices(long id, String destination) {
    Device d = devices.get(id);
    var choices = new ArrayList<Choice>();
    boolean six = destination.contains(":");
    for (int p = 0; p < d.ports.size(); p++)
      if (interfaceUp(id, p)&&(!d.kind.equals("pc")||p==Wireless.clientPort(d))) {
        var iface = d.ports.get(p);
        if (six) {
          if (!iface.ipv6.isEmpty() && Addressing.same6(destination, iface.ipv6))
            choices.add(new Choice(p, destination, Addressing.prefix6(iface.ipv6), 0, 0, "connected"));
          if (destination.toLowerCase(Locale.ROOT).startsWith("fe80:")
              && (iface.ipv6Enabled || !iface.ipv6.isEmpty()))
            choices.add(new Choice(p, destination, 64, 0, 0, "connected"));
        } else if (!iface.ip.isEmpty() && Device.same(iface.ip, destination, iface.mask))
          choices.add(new Choice(p, d.kind.equals("pc")&&iface.medium.equals("cellular")&&!d.gateway.isBlank()?d.gateway:destination, Long.bitCount(Device.mask(iface.mask)), 0, 0, "connected"));
      }
    if (d.kind.equals("switch"))
      return choices;
    if (six) {
      for (var r : d.os().routes6)
        if (Addressing.same6(destination, r.prefix())) {
          int p = !r.iface().isEmpty() ? d.findInterface(r.iface(), false) : directPort(id, r.next());
          if (p >= 0 && interfaceUp(id, p))
            choices.add(new Choice(p, r.next(), Addressing.prefix6(r.prefix()), 1, 0, "static"));
        }
    } else {
      for (var r : d.routes)
        if (Device.same(destination, r.network(), r.mask())) {
          int p = !r.iface().isEmpty() ? d.findInterface(r.iface(), false) : directPort(id, r.nextHop());
          if (p >= 0 && interfaceUp(id, p))
            choices.add(new Choice(p, r.nextHop().isEmpty() ? destination : r.nextHop(),
                Long.bitCount(Device.mask(r.mask())), r.distance(), 0, "static"));
        }
      if (!d.gateway.isEmpty()) {
        int p = directPort(id, d.gateway);
        if (p >= 0)
          choices.add(new Choice(p, d.gateway, 0, 254, 0, "gateway"));
      }
      if (d.kind.equals("router"))
        for (var r : RoutingModel.routes(this, id))
          if (Device.same(destination, r.network(), r.mask()))
            choices.add(new Choice(r.port(), r.nextHop(), Long.bitCount(Device.mask(r.mask())), r.distance(),
                r.metric(), r.protocol()));
    }
    return choices;
  }
  private int directPort(long id, String next) {
    var d = devices.get(id);
    for (int p = 0; p < d.ports.size(); p++)
      if (interfaceUp(id, p)&&(!d.kind.equals("pc")||p==Wireless.clientPort(d))) {
        var i = d.ports.get(p);
        if (next.contains(":")) {
          if (!i.ipv6.isEmpty() && Addressing.same6(next, i.ipv6))
            return p;
        } else if (!i.ip.isEmpty() && Device.same(i.ip, next, i.mask))
          return p;
      }
    return -1;
  }
  private Choice route(long id, String destination) {
    var all = choices(id, destination);
    all.sort(Comparator.comparingInt(Choice::prefix)
            .reversed()
            .thenComparingInt(Choice::distance)
            .thenComparingInt(Choice::metric));
    if (all.isEmpty())
      return null;
    Choice first = all.getFirst();
    if (first.protocol.equals("eigrp")) {
      int variance = devices.get(id)
                         .os()
                         .routing.values()
                         .stream()
                         .filter(r -> r.protocol.equals("eigrp"))
                         .mapToInt(r -> r.variance)
                         .max()
                         .orElse(1);
      var feasible = all.stream()
                         .filter(r
                             -> r.protocol.equals("eigrp") && r.prefix == first.prefix
                                 && r.metric <= first.metric * variance)
                         .toList();
      if (feasible.size() > 1)
        return feasible.stream()
            .min(Comparator.comparingLong(r -> devices.get(id).ports.get(r.port).sent))
            .orElse(first);
    }
    return first;
  }
  public String sourceAddress(long id, String dest) {
    Choice c = route(id, dest);
    if (c == null)
      return "";
    var p = devices.get(id).ports.get(c.port);
    return dest.contains(":") ? p.ipv6.isEmpty() ? linkLocal(id, c.port) : p.ipv6.split("/")[0] : p.ip;
  }
  public String resolve(long id, String value) {
    if (value.contains(":")) {
      Addressing.ipv6(value);
      return value;
    }
    try {
      Device.ip(value);
      return value;
    } catch (IllegalArgumentException ignored) {
    }
    var os = devices.get(id).os();
    if (os.hosts.containsKey(value))
      return os.hosts.get(value);
    if (os.domainLookup)
      for (String server : os.dns) {
        long peer = findAddress(server);
        if (peer != Long.MIN_VALUE && probe(id, server, "udp", 53).success()) {
          String found = devices.get(peer).os().hosts.get(value);
          if (found != null)
            return found;
        }
      }
    throw new IllegalArgumentException("Unknown host: " + value);
  }
  private Delivery walk(long start, Packet initial) {
    frameBytes=Math.max(64,initial.payload.length+64);
    long current = start;
    int incoming = -1;
    Packet packet = initial;
    var path = new ArrayList<Long>();
    path.add(start);
    for (int ttl = 0; ttl < 32; ttl++) {
      Device d = devices.get(current);
      if (d == null)
        break;
      boolean six = packet.destination.contains(":");
      if (incoming >= 0) {
        var in = d.ports.get(incoming);
        if(in.virtual)in.received++;
        changed.add(current);
        if (!six
            && !AccessLists.allows(
                d, in.aclIn, packet.source, packet.destination, packet.protocol, packet.destinationPort)) {
          in.drops++;
          return failed("Inbound ACL denied at " + d.hostname, path, packet, current);
        }
        if (!six && in.nat.equals("outside"))
          packet = inbound(d, packet);
      }
      if(DeviceKinds.router(d.kind))packet=routerPayload.apply(current,packet);
      currentPayload=packet.payload.clone();
      currentEncrypted=routerPayload instanceof RouterTunnel tunnel&&tunnel.protectedPayload();
      frameBytes=Math.max(64,packet.payload.length+64);
      boolean local = false;
      for (int i = 0; i < d.ports.size(); i++)
        if (owns(current, i, packet.destination)) {
          local = true;
          break;
        }
      if (local)
        return new Delivery(new Result(true, "Delivered", path), packet, current);
      if (current != start && !DeviceKinds.router(d.kind))
        return failed("Destination is not a router", path, packet, current);
      if (six && current != start && !d.os().ipv6Routing)
        return failed("IPv6 unicast routing disabled at " + d.hostname, path, packet, current);
      Choice c = route(current, packet.destination);
      if (c == null)
        return failed("No route at " + d.hostname, path, packet, current);
      var out = d.ports.get(c.port);
      if (!six && incoming >= 0 && d.ports.get(incoming).nat.equals("inside") && out.nat.equals("outside"))
        packet = outbound(d, packet);
      if (!six
          && !AccessLists.allows(
              d, out.aclOut, packet.source, packet.destination, packet.protocol, packet.destinationPort)) {
        out.drops++;
        return failed("Outbound ACL denied at " + d.hostname, path, packet, current);
      }
      payloadProtocol=packet.protocol.toUpperCase(Locale.ROOT);
      Hop hop = arp(current, c.port, c.nextHop);
      if (hop == null)
        return failed("ARP unresolved for " + c.nextHop + " at " + d.hostname
                + " (cable, subnet, VLAN, PPP or interface down)",
            path, packet, current);
      if(out.virtual)out.sent++;
      changed.add(current);
      path.addAll(hop.path.subList(1, hop.path.size()));
      current = hop.end.device;
      incoming = hop.end.port;
    }
    return failed("TTL exceeded / routing loop", path, packet, current);
  }
  private static Delivery failed(String message, List<Long> path, Packet p, long id) {
    return new Delivery(new Result(false, message, List.copyOf(path)), p, id);
  }
  private Packet inbound(Device d, Packet p) {
    for (var r : d.os().nat)
      if ((r.type().equals("static")
              || r.type().equals("static-tcp") && p.protocol.equals("tcp")
                  && r.globalPort() == p.destinationPort)
          && r.global().equals(p.destination)) {
        d.os().natHits++;
        return new Packet(p.source, r.local(), p.protocol, p.sourcePort,
            r.type().equals("static") ? p.destinationPort : r.localPort(),p.payload);
      }
    for (var t : d.os().translations)
      if (t.protocol().equals(p.protocol) && t.outside().equals(p.destination)
          && (p.protocol.equals("icmp") || t.outsidePort() == p.destinationPort)) {
        d.os().natHits++;
        return new Packet(p.source, t.inside(), p.protocol, p.sourcePort, t.insidePort(),p.payload);
      }
    return p;
  }
  private Packet outbound(Device d, Packet p) {
    var os = d.os();
    for (var r : os.nat) {
      String global = "";
      int port = p.sourcePort;
      if (r.type().equals("static") && r.local().equals(p.source))
        global = r.global();
      else if (r.type().equals("static-tcp") && r.local().equals(p.source) && p.protocol.equals("tcp")
          && r.localPort() == p.sourcePort) {
        global = r.global();
        port = r.globalPort();
      } else if ((r.type().equals("dynamic") || r.type().equals("pat")) && os.acls.containsKey(r.acl())
          && AccessLists.allows(d, r.acl(), p.source, p.destination, p.protocol, p.destinationPort)) {
        var old = os.translations.stream()
                      .filter(t
                          -> t.inside().equals(p.source) && t.insidePort() == p.sourcePort
                              && t.protocol().equals(p.protocol))
                      .findFirst();
        if (old.isPresent()) {
          global = old.get().outside();
          port = old.get().outsidePort();
        } else if (r.type().equals("pat")) {
          int iface = d.findInterface(r.iface(), false);
          if (iface >= 0) {
            global = d.ports.get(iface).ip;
            port = 40000 + os.translations.size() % 20000;
          }
        } else {
          String[] pool = os.natPools.get(r.pool());
          if (pool != null)
            for (long candidate = Device.ip(pool[0]), end = Math.min(Device.ip(pool[1]), candidate + 4095);
                candidate <= end; candidate++) {
              String ip = Addressing.ip(candidate);
              if (os.translations.stream().noneMatch(t -> t.outside().equals(ip))) {
                global = ip;
                break;
              }
            }
        }
      }
      if (!global.isEmpty()) {
        String translated = global;
        int translatedPort = port;
        if (os.translations.stream().noneMatch(t
                -> t.inside().equals(p.source) && t.outside().equals(translated)
                    && t.protocol().equals(p.protocol) && t.insidePort() == p.sourcePort))
          os.translations.add(new RouterState.Translation(p.protocol, p.source, global, p.destination,
              p.sourcePort, translatedPort, r.type().startsWith("static")));
        os.natHits++;
        return new Packet(global, p.destination, p.protocol, port, p.destinationPort,p.payload);
      }
    }
    os.natMisses++;
    return p;
  }
  public Result probe(long from, String destination, String protocol, int port) {
    refreshServices();
    String source = sourceAddress(from, destination);
    if (source.isEmpty())
      return new Result(false, "No active source interface / no route", List.of(from));
    return walk(from, new Packet(source, destination, protocol, 12345, port)).result;
  }
  public record ApplicationDelivery(Result result,byte[] payload,long destination) {}
  /** Route planning only: no test TCP/UDP packets, ARP learning or wire-time reservation. */
  public Result routePath(long from,String destination){
    boolean previous=recording;recording=false;
    try{
      long current=from;var path=new ArrayList<Long>();path.add(from);var seen=new HashSet<Long>();
      for(int ttl=0;ttl<32;ttl++){
        if(!seen.add(current)||!devices.containsKey(current))return new Result(false,"Routing loop/source unavailable",path);
        var d=devices.get(current);for(int p=0;p<d.ports.size();p++)if(owns(current,p,destination))return new Result(true,"Route planned",List.copyOf(path));
        if(current!=from&&!DeviceKinds.router(d.kind))return new Result(false,"Destination is not a router",path);
        var choice=route(current,destination);if(choice==null)return new Result(false,"No route at "+d.hostname,path);
        Hop hop=null;for(var peer:lanPeers(current,choice.port())){var remote=devices.get(peer.end.device);var port=remote.ports.get(peer.end.port);if(port.ip.equals(choice.nextHop())||natAddress(remote,port,choice.nextHop())){hop=peer;break;}}
        if(hop==null)return new Result(false,"Next hop unavailable: "+choice.nextHop(),path);path.addAll(hop.path.subList(1,hop.path.size()));current=hop.end.device;
      }return new Result(false,"TTL exceeded",path);
    }finally{recording=previous;}
  }
  public ApplicationDelivery transmit(long from,String destination,String protocol,int port,byte[] payload){
    if(payload.length>65536)throw new IllegalArgumentException("Application payload exceeds 64 KiB");
    String source=sourceAddress(from,destination);
    if(source.isEmpty())return new ApplicationDelivery(new Result(false,"No active source interface / no route",List.of(from)),new byte[0],Long.MIN_VALUE);
    var delivery=walk(from,new Packet(source,destination,protocol,12345,port,payload.clone()));
    return new ApplicationDelivery(delivery.result,delivery.packet.payload.clone(),delivery.device);
  }
  public Result ping(long from, String destination) {
    return pingRecorded(from,destination);
  }
  private Result pingRecorded(long from,String destination) {
    refreshServices();
    long began=cursor;
    boolean six = destination.contains(":");
    if (six)
      Addressing.ipv6(destination);
    else
      Device.ip(destination);
    Device origin = devices.get(from);
    if (origin == null)
      return new Result(false, "Source unavailable", List.of());
    for (int i = 0; i < origin.ports.size(); i++)
      if (owns(from, i, destination))
        return new Result(true, "Reply from " + destination + ": local interface", List.of(from));
    String source = sourceAddress(from, destination);
    if (source.isEmpty())
      return new Result(false, "No active source interface / no route", List.of(from));
    var request = walk(from, new Packet(source, destination, six ? "icmpv6" : "icmp", 12345, 0));
    if (!request.result.success)
      return request.result;
    var reply = walk(request.device,
        new Packet(request.packet.destination, request.packet.source, six ? "icmpv6" : "icmp", 0,
            request.packet.sourcePort));
    if (!reply.result.success || reply.device != from)
      return new Result(
          false, "Echo reached destination, reply failed: " + reply.result.text, request.result.path);
    double time = (cursor-began)/1_000_000.0;
    String names =
        String.join(" -> ", request.result.path.stream().map(id -> devices.get(id).hostname).toList());
    return new Result(true,
        String.format(Locale.ROOT,
            "Reply from %s: bytes=64 time=%.3f ms (simulated)\nPath: %s\nEcho reply route verified.",
            destination, time, names),
        request.result.path);
  }
  private double delay(List<Long> path) {
    double time = 0;
    for (int i = 1; i < path.size(); i++) {
      double speed = mbps;
      long a = path.get(i - 1), b = path.get(i);
      for (var wire : wires)
        if (wire.a.device == a && wire.b.device == b || wire.a.device == b && wire.b.device == a) {
          var left = devices.get(wire.a.device);
          var right = devices.get(wire.b.device);
          var p = left.ports.get(wire.a.port);
          var q = right.ports.get(wire.b.port);
          double capacity = left.portName(wire.a.port).startsWith("Serial")
              ? (serialRole(wire.a.device, wire.a.port).equals("DCE") ? p.clock : q.clock) / 1000000.0
              : Math.min(p.speed.equals("auto") ? 1000 : Double.parseDouble(p.speed),
                    q.speed.equals("auto") ? 1000 : Double.parseDouble(q.speed));
          if(!left.portName(wire.a.port).startsWith("Serial"))capacity=Math.min(capacity,Math.min(p.maxMbps,q.maxMbps));
          speed = Math.min(speed, capacity);
          break;
        }
      time += latencyMs + 64 * 8 / (Math.max(.001, speed) * 1000);
    }
    return time;
  }
  public String traceroute(long id, String address) {
    Result result = probe(id, address, address.contains(":") ? "icmpv6" : "icmp", 0);
    StringBuilder s = new StringBuilder("Tracing route to " + address + "\n");
    int hop = 1;
    for (long node : result.path)
      if (node != id && !devices.get(node).kind.equals("switch")&&!devices.get(node).kind.equals("ap"))
        s.append(hop++).append("  ").append(devices.get(node).hostname).append('\n');
    return s.append(result.success ? "Destination reached" : result.text).toString();
  }
  public String linkLocal(long id, int port) {
    var p = devices.get(id).ports.get(port);
    if (!p.linkLocal.isEmpty())
      return p.linkLocal;
    long identity = Addressing.identity(id, port);
    return String.format("fe80::%x:%x:%x:%x", (identity >>> 48) & 65535, (identity >>> 32) & 65535,
        (identity >>> 16) & 65535, identity & 65535);
  }

  public String autoconfig(long id, int port) {
    Device client = devices.get(id);
    var p = client.ports.get(port);
    for (var h : lanPeers(id, port)) {
      Device server = devices.get(h.end.device);
      var source = server.ports.get(h.end.port);
      if (server.kind.equals("router") && server.os().ipv6Routing && !source.ipv6.isEmpty()
          && Addressing.prefix6(source.ipv6) == 64) {
        java.math.BigInteger prefix = Addressing.ipv6(source.ipv6.split("/")[0]).shiftRight(64).shiftLeft(64);
        java.math.BigInteger value =
            prefix.or(java.math.BigInteger.valueOf(Addressing.identity(id, port) & Long.MAX_VALUE));
        StringBuilder text = new StringBuilder();
        for (int group = 7; group >= 0; group--) {
          if (group < 7)
            text.append(':');
          text.append(value.shiftRight(group * 16).and(java.math.BigInteger.valueOf(65535)).toString(16));
        }
        p.ipv6 = text + "/64";
        p.ipv6Enabled = true;
        client.os().routes6.removeIf(r -> r.prefix().equals("::/0"));
        client.os().routes6.add(
            new RouterState.Route6("::/0", linkLocal(h.end.device, h.end.port), client.portName(port)));
        changed.add(id);
        return "SLAAC address: " + p.ipv6;
      }
    }
    return "% SLAAC pending: no reachable router advertisement with a /64 prefix";
  }
  public void refreshServices() {
    if (refreshing)
      return;
    refreshing = true;
    try {
      switching.syncVtp();
      for (var e : devices.entrySet())
        for (int p = 0; p < e.getValue().ports.size(); p++) {
          if(e.getValue().kind.equals("pc")&&p!=Wireless.clientPort(e.getValue()))continue;
          var iface = e.getValue().ports.get(p);
          if (iface.up && iface.dhcp && iface.ip.isEmpty())
            renewDhcp(e.getKey(), p);
          if (iface.up && iface.autoconfig && iface.ipv6.isEmpty())
            autoconfig(e.getKey(), p);
        }
    } finally {
      refreshing = false;
    }
  }
  public String renewDhcp(long id, int port) {
    Device client = devices.get(id);
    var iface = client.ports.get(port);
    if (!interfaceUp(id, port))
      return "% DHCP pending: interface is down";
    String key = id + ":" + port;
    boolean wasRecording=recording;recording=true;frameSource=Addressing.mac(id,port);frameDestination="";frameProtocol="DHCP-DISCOVER";
    List<Hop> peers;
    try{peers=lanPeers(id,port);}finally{recording=wasRecording;}
    record Offer(Device server, long id, String hint,Endpoint response,long arrival) {}
    var servers = new ArrayList<Offer>();
    for (var h : peers) {
      Device peer = devices.get(h.end.device);
      var p = peer.ports.get(h.end.port);
      if (!peer.os().pools.isEmpty())
        servers.add(new Offer(peer, h.end.device, p.ip,h.end,h.arrival));
      if (!p.helper.isEmpty()) {
        cursor=Math.max(cursor,h.arrival);
        long serverId = findAddress(p.helper);
        if (serverId != Long.MIN_VALUE && probe(h.end.device, p.helper, "udp", 67).success())
          servers.add(new Offer(devices.get(serverId), serverId, p.ip,h.end,cursor));
      }
    }
    for (var offer : servers) {
      cursor=Math.max(cursor,offer.arrival);
      recording=true;frameSource=Addressing.mac(offer.response.device,offer.response.port);frameDestination=Addressing.mac(id,port);frameProtocol="DHCP-OFFER";
      boolean allowed;
      try{var response=lanPeers(offer.response.device,offer.response.port).stream().filter(h->h.end.equals(new Endpoint(id,port))).findFirst();allowed=response.isPresent();if(allowed)cursor=Math.max(cursor,response.get().arrival);}finally{recording=wasRecording;}
      if(!allowed)continue;
      for (var entry : offer.server.os().pools.entrySet()) {
        var pool = entry.getValue();
        if (pool.network.isEmpty() || offer.hint.isEmpty()
            || !Device.same(offer.hint, pool.network, pool.mask))
          continue;
        String selected = "";
        var old = offer.server.os().leases.get(key);
        if (old != null && Device.same(old.address(), pool.network, pool.mask)
            && !inExcluded(offer.server, old.address())) {
          long owner = peers.stream().filter(h->devices.get(h.end.device).ports.get(h.end.port).ip.equals(old.address())).mapToLong(h->h.end.device).findFirst().orElse(Long.MIN_VALUE);
          if (owner == Long.MIN_VALUE || owner == id)
            selected = old.address();
        }
        if (selected.isEmpty()) {
          long network = Device.ip(pool.network) & Device.mask(pool.mask),
               last = network + (Device.mask(pool.mask) ^ 0xffffffffL);
          for (long candidate = network + 1, end = Math.min(last - 1, network + 4096); candidate <= end;
              candidate++) {
            String address = Addressing.ip(candidate);
            if (inExcluded(offer.server, address) || address.equals(pool.gateway))
              continue;
            if (peers.stream().anyMatch(h->devices.get(h.end.device).ports.get(h.end.port).ip.equals(address))) {
              offer.server.os().conflicts.add(address);
              continue;
            }
            if (offer.server.os().leases.values().stream().anyMatch(l -> l.address().equals(address)))
              continue;
            selected = address;
            break;
          }
        }
        if (selected.isEmpty())
          continue;
        iface.ip = selected;
        iface.mask = pool.mask;
        client.gateway = pool.gateway;
        if (!pool.dns.isEmpty())
          client.os().dns = new ArrayList<>(List.of(pool.dns));
        client.os().domain = pool.domain;
        offer.server.os().leases.put(
            key, new RouterState.Lease(selected, entry.getKey(), key, pool.gateway, pool.dns));
        changed.add(id);
        changed.add(offer.id);
        for(var peer:peers)for(long sw:peer.path)if(devices.get(sw).kind.equals("switch")&&devices.get(sw).os().dhcpSnooping){devices.get(sw).os().snoopingBindings.put(key,selected+" "+Addressing.mac(id,port));changed.add(sw);}
        return "DHCP address: " + selected + " " + pool.mask + " gateway " + pool.gateway;
      }
    }
    cursor=Math.max(cursor,peers.stream().mapToLong(Hop::arrival).max().orElse(cursor))+1_000_000_000L;
    return "% DHCP pending: no usable offer";
  }
  private boolean inExcluded(Device server, String address) {
    long n = Device.ip(address);
    for (var range : server.os().excluded)
      if (n >= Device.ip(range[0]) && n <= Device.ip(range[1]))
        return true;
    return false;
  }
  public String dhcpInfo(long id, String command) {
    refreshServices();
    var os = devices.get(id).os();
    var out = new StringBuilder();
    if (command.endsWith("binding")) {
      out.append("IP address       Client identifier      Pool\n");
      for (var l : os.leases.values())
        out.append(l.address()).append("   ").append(l.client()).append("   ").append(l.pool()).append('\n');
    } else if (command.endsWith("conflict"))
      out.append("Conflicting addresses:\n").append(String.join("\n", os.conflicts));
    else
      os.pools.forEach((name, p)
                           -> out.append(name)
                               .append(": ")
                               .append(p.network)
                               .append(' ')
                               .append(p.mask)
                               .append(" gateway ")
                               .append(p.gateway)
                               .append(" leases ")
                               .append(os.leases.values().stream().filter(l -> l.pool().equals(name)).count())
                               .append('\n'));
    return out.length() == 0 ? "No DHCP pools" : out.toString();
  }
  public String routeTable(long id, boolean six, String filter) {
    refreshServices();
    Device d = devices.get(id);
    var out = new StringBuilder("C connected, S static, R RIP, O OSPF, D EIGRP\n");
    for (int p = 0; p < d.ports.size(); p++)
      if (interfaceUp(id, p)) {
        var i = d.ports.get(p);
        if (six && !i.ipv6.isEmpty())
          out.append("C ").append(i.ipv6).append(" via ").append(d.portName(p)).append('\n');
        else if (!six && filter.isEmpty() && !i.ip.isEmpty())
          out.append("C ")
              .append(Device.networkAddress(i.ip, i.mask))
              .append('/')
              .append(Long.bitCount(Device.mask(i.mask)))
              .append(" via ")
              .append(d.portName(p))
              .append('\n');
      }
    if (six) {
      for (var r : d.os().routes6)
        out.append("S ")
            .append(r.prefix())
            .append(" via ")
            .append(r.next())
            .append(' ')
            .append(r.iface())
            .append('\n');
    } else {
      if (filter.isEmpty() && !d.gateway.isEmpty())
        out.append("S* 0.0.0.0/0 [254/0] via ").append(d.gateway).append(" (gateway/DHCP)\n");
      if (filter.isEmpty())
        for (var r : d.routes) {
          String next = r.iface().isEmpty() ? r.nextHop() : r.iface();
          int p = r.iface().isEmpty() ? directPort(id, r.nextHop()) : d.findInterface(r.iface(), false);
          out.append("S ")
              .append(r.network())
              .append('/')
              .append(Long.bitCount(Device.mask(r.mask())))
              .append(" [")
              .append(r.distance())
              .append("/0] via ")
              .append(next)
              .append(p >= 0 && interfaceUp(id, p) ? "" : " (inactive)")
              .append('\n');
        }
      for (var r : RoutingModel.routes(this, id))
        if (filter.isEmpty() || r.protocol().equals(filter))
          out.append(switch (r.protocol()) {
               case "ospf" -> "O ";
               case "eigrp" -> "D ";
               default -> "R ";
             })
              .append(r.network())
              .append('/')
              .append(Long.bitCount(Device.mask(r.mask())))
              .append(" [")
              .append(r.distance())
              .append('/')
              .append(r.metric())
              .append("] via ")
              .append(r.nextHop())
              .append(", ")
              .append(d.portName(r.port()))
              .append('\n');
    }
    return out.toString();
  }
  public String protocols(long id) {
    var out = new StringBuilder("Synchronous educational routing model\n");
    for (var r : devices.get(id).os().routing.values())
      out.append(r.protocol.toUpperCase(Locale.ROOT))
          .append(' ')
          .append(r.id)
          .append(" version ")
          .append(r.version)
          .append(" router-id ")
          .append(RoutingModel.routerId(devices.get(id), r))
          .append(" auto-summary ")
          .append(r.autoSummary)
          .append(" variance ")
          .append(r.variance)
          .append("\n Networks: ")
          .append(r.networks)
          .append("\n Passive: ")
          .append(r.passive)
          .append('\n');
    return out.toString();
  }
  public String routingInfo(long id, String protocol, String cmd) {
    var out = new StringBuilder();
    Device d = devices.get(id);
    for (var entry : d.os().routing.entrySet())
      if (entry.getValue().protocol.equals(protocol)) {
        var r = entry.getValue();
        out.append(protocol.toUpperCase(Locale.ROOT))
            .append(" process ")
            .append(r.id)
            .append(" router-id ")
            .append(RoutingModel.routerId(d, r))
            .append('\n');
        if (cmd.contains("neighbor")) {
          for (var edge : RoutingModel.neighbors(this, id, entry.getKey()))
            out.append(devices.get(edge.id()).hostname)
                .append(' ')
                .append(devices.get(edge.id()).ports.get(edge.remotePort()).ip)
                .append(" ")
                .append(protocol.equals("ospf") ? "FULL" : "UP")
                .append(" via ")
                .append(d.portName(edge.localPort()))
                .append('\n');
        } else if (cmd.endsWith("interface")) {
          for (int p = 0; p < d.ports.size(); p++)
            if (RoutingModel.eligible(d, r, p))
              out.append(d.portName(p))
                  .append(" area ")
                  .append(RoutingModel.area(d, r, p))
                  .append(" cost ")
                  .append(d.ports.get(p).ospfCost)
                  .append(" priority ")
                  .append(d.ports.get(p).ospfPriority)
                  .append(r.passive.contains(d.portName(p)) ? " PASSIVE" : "")
                  .append('\n');
        } else if (cmd.endsWith("topology"))
          out.append(routeTable(id, false, protocol));
        else
          out.append("Networks: ")
              .append(r.networks)
              .append("\nNeighbors: ")
              .append(RoutingModel.neighbors(this, id, entry.getKey()).size())
              .append('\n');
      }
    return out.length() == 0 ? "No " + protocol.toUpperCase(Locale.ROOT) + " process configured"
                             : out.toString();
  }
  public String cdp(long id, boolean detail) {
    Device d = devices.get(id);
    var out = new StringBuilder("Device ID / Local interface / Remote interface\n");
    if (!d.os().cdp)
      return "CDP is not enabled";
    for (var w : wires) {
      Endpoint local = w.a.device == id ? w.a : w.b.device == id ? w.b : null;
      if (local == null)
        continue;
      Endpoint remote = local.equals(w.a) ? w.b : w.a;
      Device peer = devices.get(remote.device);
      if (peer == null || !linkUp(id, local.port) || !d.ports.get(local.port).cdp || !peer.os().cdp
          || !peer.ports.get(remote.port).cdp)
        continue;
      out.append(peer.hostname)
          .append(" / ")
          .append(d.portName(local.port))
          .append(" / ")
          .append(peer.portName(remote.port))
          .append('\n');
      if (detail)
        out.append("  Platform: NetLab ")
            .append(peer.kind)
            .append("; addresses: ")
            .append(peer.ports.stream().filter(p -> !p.ip.isEmpty()).map(p -> p.ip).toList())
            .append('\n');
    }
    return out.toString();
  }
}
