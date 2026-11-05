package dev.netlab.core;

import java.util.*;

/** Pure Java teaching model. No Cisco binaries or host networking. */
public final class Device implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
  public String kind, hostname;
  public String identity=UUID.randomUUID().toString();
  public Map<String,String> extensions=new HashMap<>();
  public List<Port> ports = new ArrayList<>();
  public String gateway = "";
  public List<Route> routes = new ArrayList<>();
  public RouterState router;
  public Hardware hardware;
  public Wireless wireless;
  public int preferredPort;
  public RouterState os() {
    if (router == null)
      router = new RouterState();
    if (router.activeUsers == null)
      router.activeUsers = new LinkedHashMap<>();
    if (router.sessionTimes == null)
      router.sessionTimes = new LinkedHashMap<>();
    if(router.learnedMacs==null)router.learnedMacs=new LinkedHashMap<>();
    if(router.bridgePriorities==null)router.bridgePriorities=new TreeMap<>();
    if(router.secureLearned==null)router.secureLearned=new LinkedHashMap<>();
    if(router.securityViolations==null)router.securityViolations=new LinkedHashMap<>();
    if(router.snoopingBindings==null)router.snoopingBindings=new LinkedHashMap<>();
    if(router.dhcpWindows==null)router.dhcpWindows=new LinkedHashMap<>();
    if(router.switchLog==null)router.switchLog=new ArrayList<>();
    return router;
  }
  public void normalize() {
    if(extensions==null)extensions=new HashMap<>();
    if(identity==null||identity.isBlank())identity=UUID.randomUUID().toString();
    for(Port p:ports)if(p.console){p.virtual=false;p.up=!p.hidden;}
    for(Port p:ports){
      if(p.medium==null)p.medium="copper";if(p.hardwareSlot==null)p.hardwareSlot="";
      if(p.maxMbps<=0&&p.medium.equals("copper")&&(p.name==null||!p.name.startsWith("Serial")))p.maxMbps=p.name!=null&&p.name.startsWith("FastEthernet")?100:1000;
      if(p.stickyMacs==null)p.stickyMacs=new LinkedHashSet<>();
      if(p.stpCosts==null)p.stpCosts=new TreeMap<>();if(p.stpPriorities==null)p.stpPriorities=new TreeMap<>();
      if(p.allowedVlans==null)p.allowedVlans="all";if(p.channelMode==null)p.channelMode="";
      if(p.switchMode==null)p.switchMode=p.trunk?"trunk":"access";if(p.bpduGuardMode==null)p.bpduGuardMode="default";
    }
    if (routes == null)
      routes = new ArrayList<>();
    for (int i = 0; i < ports.size(); i++)
      if (ports.get(i).name == null) {
        Port old = ports.get(i), fresh = new Port(old.up);
        fresh.ip = old.ip;
        fresh.mask = old.mask;
        ports.set(i, fresh);
      }
    if (DeviceKinds.console(kind)
        && ports.stream().noneMatch(p -> p.console)) {
      Port consolePort = new Port(true);
      consolePort.name = kind.equals("pc") ? "RS232" : "Console";
      consolePort.console = true;
      consolePort.virtual = false;
      ports.add(consolePort);
    }
    Hardware.normalizePortNames(this);
  }
  public int physicalPortIndex(int slot) {
    for (int i = 0; i < ports.size(); i++)
      if (!ports.get(i).virtual && !ports.get(i).hidden && !ports.get(i).medium.equals("wireless") && slot-- == 0)
        return i;
    return -1;
  }
  public int physicalPortCount() {
    int count = 0;
    for (Port p : ports)
      if (!p.virtual&&!p.hidden&&!p.medium.equals("wireless"))
        count++;
    return count;
  }
  public int physicalSlot(int index) {
    int count = 0;
    for (int i = 0; i < index; i++)
      if (!ports.get(i).virtual&&!ports.get(i).hidden&&!ports.get(i).medium.equals("wireless"))
        count++;
    return count;
  }
  public static final class Port implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public String ip = "", mask = "255.255.255.0";
    public boolean hidden, trunk, ospfCostManual, up, virtual, console, dhcp, ipv6Enabled, autoconfig, nativeVlan,
        cdp = true, portSecurity, sticky, dhcpTrust, portfast, bpduGuard;
    public String name = "", description = "", duplex = "auto", speed = "auto", helper = "", switchMode = "access",
                  allowedVlans = "all", violation = "shutdown", secureMac = "",
                  encapsulation = "hdlc", authentication = "", papUser = "", papPassword = "", aclIn = "",
                  aclOut = "", nat = "", ipv6 = "", linkLocal = "", ospfProcess = "", ospfArea = "0";
    public int parent = -1, vlan = 0, voiceVlan = 0, channelGroup = 0, securityMaximum = 1,
               bandwidth = 1000000, clock = 64000, ospfCost = 1, ospfPriority = 1;
    public long received, sent, drops;
    public String medium="copper", hardwareSlot="";
    public int maxMbps=1000;
    public boolean l2;
    public int accessVlan = 1, trunkNative = 1, dhcpRate;
    public String channelMode = "", bpduGuardMode = "default";
    public boolean nonegotiate, errDisabled;
    public Set<String> stickyMacs = new LinkedHashSet<>();
    public Map<Integer,Integer> stpCosts = new TreeMap<>(), stpPriorities = new TreeMap<>();

    public Port(boolean up) {
      this.up = up;
    }
    public Port(){this(false);}
  }
  public record Route(String network, String mask, String nextHop, int ad, String iface)
      implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public Route(String network, String mask, String nextHop) {
      this(network, mask, nextHop, 1, "");
    }
    public Route {
      if (iface == null)
        iface = "";
    }
    public int distance() {
      return ad == 0 ? 1 : ad;
    }
  }
  public Device(String kind) {
    this.kind = kind;
    if(kind.equals("energy")||kind.equals("energy_source")){hostname=kind.equals("energy")?"Energy Cabinet":"Creative FE Source";return;}
    if(kind.equals("ap")){
      hostname="AccessPoint";wireless=new Wireless();
      var ethernet=new Port(true);ethernet.name="GigabitEthernet0";ethernet.l2=true;ports.add(ethernet);
      var radio=new Port(true);radio.name="Wireless0";radio.medium="wireless";radio.maxMbps=54;radio.bandwidth=54000;radio.l2=true;ports.add(radio);return;
    }
    if (kind.equals("router"))
      this.router = new RouterState();
    hostname = kind.equals("traffic")?"TrafficControl":kind.equals("pc") ? "PC" : kind.equals("router") ? "Router" : "Switch";
    for (int i = 0; i < (kind.equals("switch") ? 4 : kind.equals("router") ? 2 : 1); i++)
      ports.add(new Port(!kind.equals("router")));
    if (kind.equals("router") || kind.equals("switch") || kind.equals("pc")||kind.equals("traffic")||Set.of("server","endpoint","sniffer").contains(kind)) {
      Port consolePort = new Port(true);
      consolePort.name = kind.equals("pc") ? "RS232" : "Console";
      consolePort.console = true;
      consolePort.virtual = false;
      ports.add(consolePort);
    }
  }
  public String portName(int i) {
    String name = ports.get(i).name;
    return name != null && !name.isEmpty() ? name : kind.equals("pc") ? "eth0" : "GigabitEthernet0/" + i;
  }
  public int findInterface(String value, boolean create) {
    String input = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    // Fixed hardware names such as TX-IF, RX-IF and BUC-TX have no numeric suffix.
    // Console remains configured through line console, never as a data interface.
    for(int i=0;i<ports.size();i++)
      if(!ports.get(i).hidden&&!ports.get(i).console
          &&portName(i).replaceAll("\\s+", "").equalsIgnoreCase(input))return i;
    var m = java.util.regex.Pattern.compile("([a-z-]+)([0-9]+(?:/[0-9]+)*(?:\\.[0-9]+)?)").matcher(input);
    if (!m.matches())
      return -1;
    String family = m.group(1), number = m.group(2);
    List<String> types = List.of("gigabitethernet", "fastethernet", "serial", "loopback", "vlan", "port-channel", "ethernet", "eth");
    String type = types.stream().filter(t -> t.startsWith(family)).findFirst().orElse("");
    for (int i = 0; i < ports.size(); i++) {
      String canonical = portName(i).toLowerCase(Locale.ROOT);
      String f = canonical.replaceAll("[0-9].*", "");
      if ((!ports.get(i).hidden || create)
          && (f.startsWith(family) || (f.equals("eth") && type.equals("ethernet")))
          && canonical.substring(f.length()).equals(number)) {
        if (create && ports.get(i).hidden) {
          if(hardware!=null&&!ports.get(i).virtual)continue;
          ports.get(i).hidden = false;
          if (canonical.startsWith("loopback"))
            ports.get(i).up = true;
        }
        return i;
      }
    }
    if (!create || (!kind.equals("router") && !kind.equals("switch")) || ports.size() >= 64 || type.isEmpty())
      return -1;
    if(hardware!=null&&type.equals("serial"))return -1;
    Port port = new Port(type.equals("loopback"));
    port.name = switch (type) {
      case "gigabitethernet" -> "GigabitEthernet" + number;
      case "fastethernet" -> "FastEthernet" + number;
      default -> Character.toUpperCase(type.charAt(0)) + type.substring(1) + number;
    };
    if ((kind.equals("switch")||ports.stream().anyMatch(p->p.l2)) && type.equals("vlan") && number.matches("[0-9]+")) {
      port.name = "Vlan" + number;
      port.virtual = true;
      port.up = true;
    } else if (kind.equals("switch") && type.equals("port-channel") && number.matches("[0-9]+")) {
      port.name = "Port-channel" + number;
      port.virtual = true;
      port.up = true;
    } else if (type.equals("loopback") && number.matches("[0-9]+")) {
      port.virtual = true;
    } else if (type.equals("serial") && number.matches("[0-9]+/[0-9]+/[0-9]+")) {
    } else if (type.equals("gigabitethernet") && number.contains(".")) {
      int parent = findInterface("GigabitEthernet" + number.split("\\.")[0], false);
      if (parent < 0)
        return -1;
      port.virtual = true;
      port.parent = parent;
      port.up = true;
    } else
      return -1;
    ports.add(port);
    return ports.size() - 1;
  }
  public static long ip(String s) {
    String[] octets = s.split("\\.", -1);
    if (octets.length != 4)
      throw new IllegalArgumentException("Invalid IPv4 address");
    long n = 0;
    for (String o : octets) {
      if (!o.matches("[0-9]{1,3}"))
        throw new IllegalArgumentException("Invalid IPv4 address");
      int v = Integer.parseInt(o);
      if (v > 255)
        throw new IllegalArgumentException("Invalid IPv4 address");
      n = (n << 8) | v;
    }
    return n;
  }
  public static long mask(String s) {
    long n = ip(s), inverse = (~n) & 0xffffffffL;
    if ((inverse & (inverse + 1)) != 0)
      throw new IllegalArgumentException("Subnet mask must be contiguous");
    return n;
  }
  public static String networkAddress(String address, String netmask) {
    long n = ip(address) & mask(netmask);
    return ((n >>> 24) & 255) + "." + ((n >>> 16) & 255) + "." + ((n >>> 8) & 255) + "." + (n & 255);
  }
  public static boolean same(String a, String b, String m) {
    return (ip(a) & mask(m)) == (ip(b) & mask(m));
  }
}
