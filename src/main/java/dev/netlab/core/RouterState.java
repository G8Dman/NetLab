package dev.netlab.core;

import java.io.*;
import java.util.*;

/** Persistent teaching NOS configuration and operational tables. */
public final class RouterState implements Serializable {
  private static final long serialVersionUID = 1L;
  public boolean domainLookup = true, passwordEncryption = false, ipv6Routing = false, cdp = true;
  public String domain = "", enableSecret = "", banner = "", startup = "", rsaPublic = "", rsaPrivate = "";
  public int sshVersion = 2;
  public long bootMillis = System.currentTimeMillis();
  public Map<String, String> users = new LinkedHashMap<>(), hosts = new LinkedHashMap<>();
  public Map<String, Integer> privileges = new LinkedHashMap<>();
  public Map<String, String> chapSecrets = new LinkedHashMap<>();
  public List<String> dns = new ArrayList<>();
  public Map<String, Line> lines = new LinkedHashMap<>();
  public Map<String, Routing> routing = new LinkedHashMap<>();
  public Map<String, Pool> pools = new LinkedHashMap<>();
  public List<String[]> excluded = new ArrayList<>();
  public Map<String, Lease> leases = new LinkedHashMap<>();
  public Set<String> conflicts = new LinkedHashSet<>();
  public Map<String, Acl> acls = new LinkedHashMap<>();
  public List<NatRule> nat = new ArrayList<>();
  public Map<String, String[]> natPools = new LinkedHashMap<>();
  public List<Translation> translations = new ArrayList<>();
  public List<Route6> routes6 = new ArrayList<>();
  public Map<String, String> flash = new LinkedHashMap<>();
  public Map<String, String> arp = new LinkedHashMap<>(), neighbors6 = new LinkedHashMap<>();
  public Map<String, String> pcFiles = new LinkedHashMap<>(), pcDns = new LinkedHashMap<>();
  public boolean firewallEnabled = true, ipv6FirewallEnabled = true;
  // Layer-2 switch configuration and operational teaching state.
  public Map<Integer, String> vlans = new TreeMap<>();
  public String vtpDomain = "", vtpMode = "server", vtpPassword = "", vtpVersion = "1";
  public String spanningMode = "pvst";
  public int spanningPriority = 32768;
  public boolean spanningPortfastDefault, spanningBpduGuardDefault, dhcpSnooping, dhcpSnoopingOption = true;
  public String dhcpSnoopingVlans = "";
  public Map<Integer, String> spanSources = new LinkedHashMap<>(), spanDestinations = new LinkedHashMap<>();
  public String portChannelLoadBalance = "src-mac";
  public Map<String, String> staticMacs = new LinkedHashMap<>();
  public Map<String, MacEntry> learnedMacs = new LinkedHashMap<>();
  public Map<Integer,Integer> bridgePriorities = new TreeMap<>();
  public Map<Integer,Set<String>> secureLearned = new LinkedHashMap<>();
  public Map<Integer,Long> securityViolations = new LinkedHashMap<>();
  public Map<String,String> snoopingBindings = new LinkedHashMap<>();
  public Map<Integer,long[]> dhcpWindows = new LinkedHashMap<>();
  public List<String> switchLog = new ArrayList<>();
  public int macAging = 300, vtpRevision;
  public record MacEntry(int port,long learned) implements Serializable {}
  public transient Map<String, String> activeUsers = new LinkedHashMap<>();
  public transient Map<String, Long> sessionTimes = new LinkedHashMap<>();
  public long natHits, natMisses;
  public static final class Line implements Serializable {
    private static final long serialVersionUID = 1L;
    public String password = "", login = "", transport = "telnet", access = "";
    public boolean synchronous;
    public int timeout = 600;
  }
  public static final class Routing implements Serializable {
    private static final long serialVersionUID = 1L;
    public String protocol, id, routerId = "";
    public int version = 1, variance = 1;
    public boolean autoSummary = true, originate, always;
    public List<Announcement> networks = new ArrayList<>();
    public Set<String> passive = new LinkedHashSet<>();
    public Routing(String protocol, String id) {
      this.protocol = protocol;
      this.id = id;
    }
  }
  public record Announcement(String address, String wildcard, String area) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public static final class Pool implements Serializable {
    private static final long serialVersionUID = 1L;
    public String network = "", mask = "255.255.255.0", gateway = "", dns = "", domain = "";
  }
  public record Lease(String address, String pool, String client, String gateway, String dns)
      implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public static final class Acl implements Serializable {
    private static final long serialVersionUID = 1L;
    public String type;
    public List<AclRule> rules = new ArrayList<>();
    public Acl(String type) {
      this.type = type;
    }
  }
  public record AclRule(boolean permit, String protocol, String source, String sourceWildcard,
      String destination, String destinationWildcard, int port) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public record NatRule(String type, String local, String global, String acl, String pool, String iface,
      int localPort, int globalPort) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public record Translation(String protocol, String inside, String outside, String peer, int insidePort,
      int outsidePort, boolean permanent) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public record Route6(String prefix, String next, String iface) implements Serializable {
    private static final long serialVersionUID = 1L;
  }
  public Line line(String name) {
    return lines.computeIfAbsent(name, k -> new Line());
  }
}
