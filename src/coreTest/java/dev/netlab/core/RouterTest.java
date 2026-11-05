package dev.netlab.core;

import java.util.*;

/** End-to-end CLI/state/forwarding regression scenarios, with no host sockets. */
public final class RouterTest {
  private static int checks;
  private static void check(boolean ok, String text) {
    if (!ok)
      throw new AssertionError(text);
    checks++;
  }
  private static String cmd(Console c, Device d, Network n, long id, String text) {
    String result = c.execute(d, n, id, text);
    if (result.startsWith("%") && !result.startsWith("% Interface enabled")
        && !result.startsWith("% Interface administratively"))
      throw new AssertionError(text + " -> " + result);
    return result;
  }
  private static void config(Console c, Device d, Network n, long id, String... lines) {
    if (c.mode == 0)
      cmd(c, d, n, id, "enable");
    if (c.mode < 2)
      cmd(c, d, n, id, "conf t");
    for (String line : lines) cmd(c, d, n, id, line);
    cmd(c, d, n, id, "end");
  }
  private static void wire(Network n, long a, int ap, long b, int bp) {
    n.wires.add(new Network.Wire(new Network.Endpoint(a, ap), new Network.Endpoint(b, bp), 3));
  }
  private static void address(Device d, int p, String ip) {
    d.ports.get(p).ip = ip;
    d.ports.get(p).up = true;
  }
  private static final class Lab {
    final Network n = new Network();
    final Device a = new Device("pc"), r1 = new Device("router"), r2 = new Device("router"),
                 b = new Device("pc"), sw = new Device("switch"), server = new Device("pc");
    final Console c1 = new Console(), c2 = new Console();
    Lab() {
      n.devices.put(1L, a);
      n.devices.put(2L, r1);
      n.devices.put(3L, r2);
      n.devices.put(4L, b);
      n.devices.put(5L, sw);
      n.devices.put(6L, server);
      config(c1, r1, n, 2, "hostname R1", "int g0/0", "ip add 192.168.1.1 255.255.255.0", "no sh", "int g0/1",
          "ip add 10.0.0.1 255.255.255.0", "no sh");
      config(c2, r2, n, 3, "hostname R2", "int g0/0", "ip add 10.0.0.2 255.255.255.0", "no sh", "int g0/1",
          "ip add 192.168.2.1 255.255.255.0", "no sh");
      address(a, 0, "192.168.1.2");
      a.gateway = "192.168.1.1";
      address(b, 0, "192.168.2.2");
      b.gateway = "192.168.2.1";
      address(server, 0, "10.0.0.10");
      server.gateway = "10.0.0.1";
      wire(n, 1, 0, 2, 0);
      wire(n, 2, 1, 5, 0);
      wire(n, 3, 0, 5, 1);
      wire(n, 6, 0, 5, 2);
      wire(n, 3, 1, 4, 0);
    }
    void staticRoutes() {
      config(c1, r1, n, 2, "ip route 192.168.2.0 255.255.255.0 10.0.0.2 5");
      config(c2, r2, n, 3, "ip route 192.168.1.0 255.255.255.0 10.0.0.1");
    }
  }
  public static int run() {
    Lab l = new Lab();
    l.staticRoutes();
    check(l.n.ping(1, "192.168.2.2").success(), "CLI static routing with AD");
    check(cmd(l.c1, l.r1, l.n, 2, "sh int g0/0").contains("packets input"), "interface counters");
    check(cmd(l.c1, l.r1, l.n, 2, "sh ip int g0/1").contains("10.0.0.1"),
        "IP interface leaf and brief coexist");
    check(cmd(l.c1, l.r1, l.n, 2, "sh ip int b").contains("GigabitEthernet0/1"),
        "brief literal wins over interface parameter");
    check(cmd(l.c1, l.r1, l.n, 2, "traceroute 192.168.2.2").contains("R2"),
        "traceroute follows actual forwarding");
    check(!l.r1.os().arp.isEmpty(), "traffic populates ARP cache");
    config(l.c1, l.r1, l.n, 2, "ip host target 192.168.2.2", "no ip domain-lookup");
    check(cmd(l.c1, l.r1, l.n, 2, "ping target").contains("Reply from"), "local host lookup");
    check(l.c1.execute(l.r1, l.n, 2, "ping missing").startsWith("%"), "unknown names do not access host DNS");
    config(l.c1, l.r1, l.n, 2, "banner motd #Учебный стенд#", "service password-encryption",
        "username pupil privilege 15 secret lab-pass", "line console 0", "password console-pass",
        "logging synchronous", "exec-timeout 10 0", "exit", "ip domain-name school.lab");
    String running = cmd(l.c1, l.r1, l.n, 2, "sh run");
    check(
        running.contains("password 7 ") && running.contains("Учебный стенд") && !running.contains("lab-pass"),
        "rendered secure configuration");
    check(ConfigStore.untype7(ConfigStore.type7("тест-pass")).equals("тест-pass"), "type 7 reversible UTF8");
    cmd(l.c1, l.r1, l.n, 2, "copy run start");
    String startup = l.r1.os().startup;
    check(!startup.isEmpty() && ConfigStore.restore(startup).hostname.equals("R1"), "startup snapshot");
    config(l.c1, l.r1, l.n, 2, "username extra secret extra-pass", "hostname TEMP");
    cmd(l.c1, l.r1, l.n, 2, "copy start run");
    check(l.r1.hostname.equals("R1") && l.r1.os().users.containsKey("extra"),
        "running-config merge preserves additional users");
    cmd(l.c1, l.r1, l.n, 2, "reload");
    check(l.r1.hostname.equals("R1") && !l.r1.os().users.containsKey("extra"),
        "reload replaces running state with startup");
    cmd(l.c1, l.r1, l.n, 2, "enable");
    cmd(l.c1, l.r1, l.n, 2, "erase start");
    check(l.r1.os().startup.isEmpty() && l.r1.hostname.equals("R1"), "erase preserves running config");
    config(l.c1, l.r1, l.n, 2, "enable secret enable-pass");
    cmd(l.c1, l.r1, l.n, 2, "disable");
    cmd(l.c1, l.r1, l.n, 2, "enable");
    check(l.c1.prompt(l.r1).equals("Password:"), "enable password prompt");
    check(
        l.c1.execute(l.r1, l.n, 2, "wrong").contains("Bad secrets") && l.c1.mode == 0, "wrong enable secret");
    cmd(l.c1, l.r1, l.n, 2, "enable");
    cmd(l.c1, l.r1, l.n, 2, "enable-pass");
    check(l.c1.mode == 1, "correct enable secret");
    check(!cmd(l.c1, l.r1, l.n, 2, "show history").contains("enable-pass"), "history redacts secrets");
    cmd(l.c1, l.r1, l.n, 2, "copy run tftp:");
    cmd(l.c1, l.r1, l.n, 2, "10.0.0.10");
    cmd(l.c1, l.r1, l.n, 2, "r1.cfg");
    check(l.server.os().flash.containsKey("r1.cfg"), "TFTP put stores configuration on simulated host");
    config(l.c1, l.r1, l.n, 2, "hostname AGAIN");
    cmd(l.c1, l.r1, l.n, 2, "copy tftp: run");
    cmd(l.c1, l.r1, l.n, 2, "10.0.0.10");
    cmd(l.c1, l.r1, l.n, 2, "r1.cfg");
    check(l.r1.hostname.equals("R1"), "TFTP get merges saved configuration");
    config(l.c2, l.r2, l.n, 3, "ip domain-name school.lab", "username pupil privilege 15 secret lab-pass",
        "crypto key generate rsa modulus 1024", "line vty 0 4", "login local", "transport input ssh", "exit");
    cmd(l.c1, l.r1, l.n, 2, "ssh -l pupil 10.0.0.2");
    cmd(l.c1, l.r1, l.n, 2, "lab-pass");
    check(l.c1.remoteId == 3 && l.c1.mode == 1, "SSH authentication and remote target");
    config(l.c1, l.r1, l.n, 2, "hostname REMOTE");
    check(l.r2.hostname.equals("REMOTE") && l.n.changed.contains(3L), "remote commands change remote router");
    cmd(l.c1, l.r1, l.n, 2, "exit");
    check(l.c1.remoteId == Long.MIN_VALUE, "remote exit returns to local console");
    check(l.c1.execute(l.r1, l.n, 2, "telnet 10.0.0.2").contains("disabled"), "VTY transport enforcement");
    config(l.c1, l.r1, l.n, 2, "int lo0", "ip add 172.16.0.1 255.255.255.255", "description Loopback-test",
        "exit");
    check(l.n.ping(2, "172.16.0.1").success(), "loopback is operational without a physical cable");
    config(l.c1, l.r1, l.n, 2, "int s0/0/0", "ip add 172.20.0.1 255.255.255.252", "clock rate 64000",
        "encapsulation ppp", "ppp authentication pap", "no sh", "exit", "username peer secret ppp-pass");
    config(l.c2, l.r2, l.n, 3, "int s0/0/0", "ip add 172.20.0.2 255.255.255.252", "encapsulation ppp",
        "ppp pap sent-username peer password ppp-pass", "no sh");
    int serial = l.r1.findInterface("s0/0/0", false), serial2 = l.r2.findInterface("s0/0/0", false);
    wire(l.n, 2, serial, 3, serial2);
    check(l.n.linkUp(2, serial), "PPP PAP negotiated on Serial");
    l.r2.ports.get(serial2).papPassword = ConfigStore.type7("bad");
    check(!l.n.linkUp(2, serial), "PPP authentication mismatch drops link");
    check(cmd(l.c1, l.r1, l.n, 2, "show controllers serial 0/0/0").contains("64000"),
        "serial controller inspection");
    check(cmd(l.c1, l.r1, l.n, 2, "show cdp neighbors detail").contains("Switch"),
        "CDP reports physical switch neighbor");
    // Each protocol must install and withdraw routes, rather than only store commands.
    for (String protocol : List.of("ospf", "eigrp", "rip")) {
      Lab dyn = new Lab();
      String router = protocol.equals("rip")
          ? "router rip"
          : "router " + protocol + " " + (protocol.equals("ospf") ? "1" : "42");
      String other = protocol.equals("ospf") ? "router ospf 9" : router;
      List<String> a = new ArrayList<>(List.of(router)), b = new ArrayList<>(List.of(other));
      if (protocol.equals("rip")) {
        a.addAll(List.of("version 2", "network 10.0.0.0", "network 192.168.1.0", "no auto-summary"));
        b.addAll(List.of("version 2", "network 10.0.0.0", "network 192.168.2.0", "no auto-summary"));
      } else {
        String area = protocol.equals("ospf") ? " area 0" : "";
        a.add("network 0.0.0.0 255.255.255.255" + area);
        b.add("network 0.0.0.0 255.255.255.255" + area);
        if (protocol.equals("eigrp")) {
          a.add("no auto-summary");
          b.add("no auto-summary");
        }
      }
      config(dyn.c1, dyn.r1, dyn.n, 2, a.toArray(String[] ::new));
      config(dyn.c2, dyn.r2, dyn.n, 3, b.toArray(String[] ::new));
      check(dyn.n.ping(1, "192.168.2.2").success(), protocol + " learned routes forward traffic");
      check(!RoutingModel.routes(dyn.n, 2).isEmpty(), protocol + " route table populated");
      config(dyn.c1, dyn.r1, dyn.n, 2, router, "passive-interface g0/1");
      check(!dyn.n.ping(1, "192.168.2.2").success(), protocol + " passive state affects delivery");
    }
    Lab acl = new Lab();
    acl.staticRoutes();
    config(acl.c1, acl.r1, acl.n, 2, "ip access-list extended FILTER", "permit tcp any any eq 80",
        "deny icmp any any", "exit", "int g0/0", "ip access-group FILTER in");
    check(!acl.n.ping(1, "192.168.2.2").success(), "ACL blocks ICMP");
    check(acl.n.probe(1, "192.168.2.2", "tcp", 80).success(), "ACL permits TCP port 80");
    check(!acl.n.probe(1, "192.168.2.2", "tcp", 22).success(), "ACL implicit deny");
    config(acl.c1, acl.r1, acl.n, 2, "int g0/0", "no ip access-group FILTER in");
    check(acl.n.ping(1, "192.168.2.2").success(), "ACL detach restores traffic");
    Lab dhcp = new Lab();
    config(dhcp.c1, dhcp.r1, dhcp.n, 2, "ip dhcp excluded-address 192.168.1.1 192.168.1.20",
        "ip dhcp pool LAN", "network 192.168.1.0 255.255.255.0", "default-router 192.168.1.1",
        "dns-server 10.0.0.10", "domain-name school.lab");
    dhcp.a.ports.getFirst().ip = "";
    dhcp.a.ports.getFirst().dhcp = true;
    check(!dhcp.n.renewDhcp(1, 0).startsWith("%") && dhcp.a.ports.getFirst().ip.equals("192.168.1.21"),
        "DHCP pool and exclusions");
    check(dhcp.r1.os().leases.size() == 1, "DHCP binding table");
    config(dhcp.c1, dhcp.r1, dhcp.n, 2, "ip dhcp pool REMOTE", "network 192.168.2.0 255.255.255.0",
        "default-router 192.168.2.1");
    config(dhcp.c2, dhcp.r2, dhcp.n, 3, "int g0/1", "ip helper-address 10.0.0.1");
    dhcp.b.ports.getFirst().ip = "";
    dhcp.b.ports.getFirst().dhcp = true;
    check(!dhcp.n.renewDhcp(4, 0).startsWith("%"), "DHCP relay via helper address");
    Lab nat = new Lab();
    nat.staticRoutes();
    nat.r2.routes.clear();
    config(nat.c1, nat.r1, nat.n, 2, "access-list 1 permit 192.168.1.0 0.0.0.255", "int g0/0",
        "ip nat inside", "int g0/1", "ip nat outside", "exit",
        "ip nat inside source list 1 interface g0/1 overload");
    check(nat.n.ping(1, "192.168.2.2").success(), "PAT enables reply without private return route");
    check(!nat.r1.os().translations.isEmpty(), "PAT translation table populated");
    byte[] payload=new byte[]{0,1,2,-1,42};var patDelivery=nat.n.transmit(1,"192.168.2.2","tcp",5420,payload);
    check(patDelivery.result().success()&&Arrays.equals(payload,patDelivery.payload()),"PAT preserves application payload");
    cmd(nat.c1, nat.r1, nat.n, 2, "clear ip nat translation *");
    check(nat.r1.os().translations.isEmpty(), "clear dynamic translations");
    nat.r1.os().nat.clear();
    config(nat.c1, nat.r1, nat.n, 2, "ip nat inside source static 192.168.1.2 10.0.0.50");
    check(nat.n.ping(4, "10.0.0.50").success(), "static NAT inbound and echo reply");
    config(nat.c1, nat.r1, nat.n, 2, "ip nat inside source static tcp 192.168.1.2 80 10.0.0.60 8080");
    check(nat.n.probe(4, "10.0.0.60", "tcp", 8080).path().getLast() == 1L,
        "TCP port forwarding targets internal host");
    var forwarded=nat.n.transmit(4,"10.0.0.60","tcp",8080,payload);
    check(forwarded.result().success()&&forwarded.destination()==1&&Arrays.equals(payload,forwarded.payload()),"inbound port forwarding preserves application payload");
    nat.r1.os().nat.clear();
    nat.r1.os().translations.clear();
    config(nat.c1, nat.r1, nat.n, 2, "ip nat pool PUBLIC 10.0.0.100 10.0.0.110 netmask 255.255.255.0",
        "ip nat inside source list 1 pool PUBLIC");
    check(nat.n.ping(1, "192.168.2.2").success()
            && nat.r1.os().translations.getFirst().outside().equals("10.0.0.100"),
        "dynamic NAT pool");
    Lab six = new Lab();
    config(six.c1, six.r1, six.n, 2, "ipv6 unicast-routing", "int g0/0", "ipv6 enable",
        "ipv6 address 2001:db8:1::1/64", "int g0/1", "ipv6 address 2001:db8:12::1/64", "exit",
        "ipv6 route 2001:db8:2::/64 2001:db8:12::2");
    config(six.c2, six.r2, six.n, 3, "ipv6 unicast-routing", "int g0/0", "ipv6 address 2001:db8:12::2/64",
        "int g0/1", "ipv6 address 2001:db8:2::1/64", "exit", "ipv6 route 2001:db8:1::/64 2001:db8:12::1");
    check(six.n.ping(2, "2001:db8:2::1").success(), "IPv6 static routing and echo reply");
    six.a.ports.getFirst().autoconfig = true;
    check(!six.n.autoconfig(1, 0).startsWith("%") && six.a.ports.getFirst().ipv6.endsWith("/64"),
        "SLAAC from adjacent router prefix");
    config(six.c1, six.r1, six.n, 2, "int g0/1", "ipv6 address fe80::1 link-local");
    config(six.c2, six.r2, six.n, 3, "int g0/0", "ipv6 address fe80::2 link-local");
    check(six.c1.execute(six.r1, six.n, 2, "conf t").isEmpty() == false, "configuration mode entered");
    check(six.c1.execute(six.r1, six.n, 2, "ipv6 route ::/0 fe80::2").contains("requires an interface"),
        "link-local next hop needs output interface");
    cmd(six.c1, six.r1, six.n, 2, "ipv6 route ::/0 g0/1 fe80::2");
    cmd(six.c1, six.r1, six.n, 2, "end");
    // Router-on-a-stick with two access VLANs and one trunk.
    var vlan = new Network();
    var router = new Device("router");
    var switchDevice = new Device("switch");
    var pc10 = new Device("pc");
    var pc20 = new Device("pc");
    vlan.devices.put(1L, pc10);
    vlan.devices.put(2L, router);
    vlan.devices.put(3L, switchDevice);
    vlan.devices.put(4L, pc20);
    var cli = new Console();
    config(cli, router, vlan, 2, "int g0/0", "no sh", "int g0/0.10", "encapsulation dot1Q 10",
        "ip add 192.168.10.1 255.255.255.0", "int g0/0.20", "encapsulation dot1Q 20",
        "ip add 192.168.20.1 255.255.255.0");
    switchDevice.ports.get(0).trunk = true;
    switchDevice.ports.get(1).vlan = 10;
    switchDevice.ports.get(2).vlan = 20;
    address(pc10, 0, "192.168.10.2");
    pc10.gateway = "192.168.10.1";
    address(pc20, 0, "192.168.20.2");
    pc20.gateway = "192.168.20.1";
    wire(vlan, 2, 0, 3, 0);
    wire(vlan, 1, 0, 3, 1);
    wire(vlan, 4, 0, 3, 2);
    check(vlan.ping(1, "192.168.20.2").success(), "802.1Q router-on-a-stick forwards between VLANs");
    switchDevice.ports.get(0).trunk = false;
    check(!vlan.ping(1, "192.168.20.2").success(), "missing trunk blocks tagged frames");
    for (String view :
        List.of("show startup-config", "show version", "show interfaces", "show ipv6 interface brief",
            "show ipv6 interface", "show ipv6 route", "show ipv6 neighbors", "show ip protocols", "show arp",
            "show access-lists", "show users", "show history", "show clock", "show ip ssh",
            "show cdp neighbors", "show ip ospf", "show ip ospf neighbor", "show ip ospf interface",
            "show ip route ospf", "show ip eigrp neighbors", "show ip eigrp topology", "show ip route eigrp",
            "show ip dhcp binding", "show ip dhcp pool", "show ip dhcp conflict", "show ip nat translations",
            "show ip nat statistics", "dir flash:", "show flash:")) {
      check(!cmd(l.c1, l.r1, l.n, 2, view).isEmpty(), "inspection command: " + view);
    }
    config(l.c1, l.r1, l.n, 2, "ip domain-lookup", "ip name-server 10.0.0.10");
    l.server.os().hosts.put("remote.school.lab", "192.168.2.2");
    check(cmd(l.c1, l.r1, l.n, 2, "ping remote.school.lab").contains("Reply"), "simulated DNS server lookup");
    config(l.c1, l.r1, l.n, 2, "no cdp run");
    check(cmd(l.c1, l.r1, l.n, 2, "show cdp neighbors").contains("not enabled"), "global CDP disable");
    config(l.c1, l.r1, l.n, 2, "cdp run");
    config(l.c1, l.r1, l.n, 2, "no ip route 192.168.2.0 255.255.255.0 10.0.0.2");
    check(l.r1.routes.stream().noneMatch(r -> r.network().equals("192.168.2.0")),
        "no ip route removes forwarding route");
    config(l.c1, l.r1, l.n, 2, "line console 0", "login local");
    var auth = new Console();
    auth.open(l.r1);
    check(auth.prompt(l.r1).equals("Username:"), "console username prompt");
    cmd(auth, l.r1, l.n, 2, "pupil");
    cmd(auth, l.r1, l.n, 2, "lab-pass");
    check(auth.authenticated && auth.mode == 1, "console local login with privilege 15");
    config(l.c2, l.r2, l.n, 3, "access-list 2 deny host 10.0.0.1", "access-list 2 permit any", "line vty 0 4",
        "access-class 2 in");
    check(l.c1.execute(l.r1, l.n, 2, "ssh -l pupil 10.0.0.2").contains("access-class"),
        "VTY access-class blocks remote console");
    check(l.c1.echo("en sec fake-pass").equals("<credential command hidden>"),
        "abbreviated secret input redacted");
    config(l.c1, l.r1, l.n, 2, "int g0/1");
    cmd(l.c1, l.r1, l.n, 2, "conf t");
    check(l.c1.complete(l.r1, "line v").value().equals("line vty "), "new mode keyword completion");
    check(l.c1.complete(l.r1, "copy r").value().equals("copy running-config ") == false,
        "copy unavailable in configuration mode");
    cmd(l.c1, l.r1, l.n, 2, "\u001a");
    check(l.c1.mode == 1, "Ctrl+Z returns to privileged mode");
    check(l.c1.complete(l.r1, "copy r").value().equals("copy running-config "), "copy argument completion");
    var reset = new Device("router");
    int loop = reset.findInterface("lo5", true), hardware = reset.findInterface("s0/0/0", true);
    reset.ports.get(hardware).ip = "10.10.10.1";
    reset.ports.get(hardware).up = true;
    reset.os().startup = "";
    ConfigStore.replace(reset, new Device("router"));
    check(reset.findInterface("s0/0/0", false) == hardware && !reset.ports.get(hardware).up,
        "reload retains physical serial slot IDs");
    check(reset.findInterface("lo5", false) < 0 && reset.findInterface("lo5", true) == loop,
        "deleted loopback can be recreated without shifting cables");
    check(!l.n.traceroute(2, "10.0.0.2").contains("Switch"), "traceroute excludes Layer 2 switches");
    check(l.c1.complete(l.r1, "sh ip int g0/0").value().equals("show ip interface GigabitEthernet0/0 "),
        "interface parameter completion next to brief literal");
    config(acl.c1, acl.r1, acl.n, 2, "ip access-list extended FILTER");
    cmd(acl.c1, acl.r1, acl.n, 2, "conf t");
    cmd(acl.c1, acl.r1, acl.n, 2, "ip access-list extended FILTER");
    check(acl.c1.complete(acl.r1, "perm t").value().equals("permit tcp "), "ACL protocol completion");
    check(acl.c1.complete(acl.r1, "permit tcp any ").choices().size() == 2, "ACL destination completion");
    cmd(acl.c1, acl.r1, acl.n, 2, "end");
    config(l.c1, l.r1, l.n, 2, "username REMOTE secret chap-pass", "int s0/0/0", "ppp authentication chap");
    config(l.c2, l.r2, l.n, 3, "username R1 secret chap-pass", "int s0/0/0", "ppp authentication chap");
    check(l.n.linkUp(2, serial), "PPP CHAP shared secrets");
    check(!Addressing.mac(1L << 38, 0).equals(Addressing.mac(2L << 38, 0)),
        "MAC addresses incorporate Minecraft X coordinates");
    var remapped = new java.util.HashMap<Long, Device>();
    six.n.devices.forEach((id, device) -> remapped.put(id << 38, device));
    var links = new java.util.ArrayList<Network.Wire>();
    for (var edge : six.n.wires)
      links.add(new Network.Wire(new Network.Endpoint(edge.a().device() << 38, edge.a().port()),
          new Network.Endpoint(edge.b().device() << 38, edge.b().port()), edge.length()));
    six.n.devices.clear();
    six.n.devices.putAll(remapped);
    six.n.wires.clear();
    six.n.wires.addAll(links);
    Addressing.ipv6(six.n.linkLocal(2L << 38, 0));
    six.a.ports.getFirst().ipv6 = "";
    check(!six.n.autoconfig(1L << 38, 0).startsWith("%"), "SLAAC at real Minecraft coordinate IDs");
    check(six.n.ping(1L << 38, "2001:db8:2::1").success(),
        "SLAAC default gateway and IPv6 return route at world IDs");
    return checks;
  }
  public static void main(String[] args) {
    System.out.println(run());
  }
}
