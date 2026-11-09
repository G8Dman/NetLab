package dev.netlab.core;

public final class CoreTest {
  private static int checks;
  static void check(boolean condition, String name) {
    if (!condition)
      throw new AssertionError(name);
    checks++;
  }
  static void address(Device d, int p, String ip) {
    d.ports.get(p).ip = ip;
    d.ports.get(p).up = true;
  }
  static void wire(Network n, long a, int ap, long b, int bp) {
    n.wires.add(new Network.Wire(new Network.Endpoint(a, ap), new Network.Endpoint(b, bp), 3));
  }
  public static void main(String[] args) {
    var n = new Network();
    var a = new Device("pc");
    var sw = new Device("switch");
    var r = new Device("router");
    var b = new Device("pc");
    n.devices.put(1L, a);
    n.devices.put(2L, sw);
    n.devices.put(3L, r);
    n.devices.put(4L, b);
    address(a, 0, "192.168.1.2");
    address(r, 0, "192.168.1.1");
    address(r, 1, "192.168.2.1");
    address(b, 0, "192.168.2.2");
    a.gateway = "192.168.1.1";
    b.gateway = "192.168.2.1";
    wire(n, 1, 0, 2, 0);
    wire(n, 2, 1, 3, 0);
    wire(n, 3, 1, 4, 0);
    check(n.linkUp(1, 0), "physical link up");
    check(n.ping(1, "192.168.2.2").success(), "routed echo and reply");
    check(n.ping(1, "192.168.2.2").path().size() == 4, "path includes switch and router");
    r.ports.get(1).up = false;
    check(!n.linkUp(3, 1), "physical link follows remote admin status");
    check(!n.ping(1, "192.168.2.2").success(), "shutdown blocks forwarding");
    r.ports.get(1).up = true;
    b.gateway = "";
    check(!n.ping(1, "192.168.2.2").success(), "missing return route fails");
    b.gateway = "192.168.2.1";
    a.gateway = "";
    check(!n.ping(1, "192.168.2.2").success(), "missing source gateway fails");
    a.gateway = "192.168.1.1";
    check(n.ping(1, "192.168.1.1").success(), "same LAN echo");
    check(!n.ping(1, "192.168.1.99").success(), "unresolved ARP fails");
    n.wires.removeLast();
    check(!n.ping(1, "192.168.2.2").success(), "broken cable fails");
    wire(n, 3, 1, 4, 0);
    sw.ports.get(1).up = false;
    check(!n.ping(1, "192.168.2.2").success(), "switch port shutdown fails");
    sw.ports.get(1).up = true;
    address(b, 0, "10.0.0.2");
    address(r, 1, "10.0.0.1");
    b.gateway = "10.0.0.1";
    check(n.ping(1, "10.0.0.2").success(), "connected router networks forward");
    var cli = new Console();
    var d = new Device("router");
    check(cli.execute(d, n, 3, "ip address 1.2.3.4 255.255.255.0").contains("requires"), "CLI mode guard");
    cli.execute(d, n, 3, "enable");
    cli.execute(d, n, 3, "conf t");
    cli.execute(d, n, 3, "int g0/0");
    cli.execute(d, n, 3, "ip address 172.16.0.1 255.255.0.0");
    cli.execute(d, n, 3, "no shutdown");
    check(d.ports.getFirst().up && d.ports.getFirst().ip.equals("172.16.0.1"), "CLI config changes device");
    check(cli.execute(d, n, 3, "ip address 999.1.1.1 255.255.255.0").startsWith("%"),
        "invalid address rejected");
    check(cli.execute(d, n, 3, "ip address 1.2.3.4 255.0.255.0").startsWith("%"),
        "noncontiguous mask rejected");
    check(d.ports.getFirst().ip.equals("172.16.0.1"), "invalid config keeps previous address");
    cli.execute(d, n, 3, "end");
    cli.execute(d, n, 3, "conf t");
    cli.execute(d, n, 3, "ip route 0.0.0.0 0.0.0.0 172.16.0.2");
    check(d.routes.size() == 1, "static route command");
    // Add another routed subnet behind a second router; verify static route necessity.
    var r2 = new Device("router");
    address(r2, 0, "10.0.0.2");
    address(r2, 1, "172.20.0.1");
    n.devices.put(4L, r2);
    var remote = new Device("pc");
    address(remote, 0, "172.20.0.2");
    remote.gateway = "172.20.0.1";
    n.devices.put(5L, remote);
    wire(n, 4, 1, 5, 0);
    check(!n.ping(1, "172.20.0.2").success(), "remote subnet requires static route");
    r.routes.add(new Device.Route("172.20.0.0", "255.255.255.0", "10.0.0.2"));
    r2.routes.add(new Device.Route("192.168.1.0", "255.255.255.0", "10.0.0.1"));
    check(n.ping(1, "172.20.0.2").success(), "static routes across two routers");
    check(cli.execute(d, n, 3, "ip route 10.0.0.2 255.255.255.0 172.16.0.2").contains("Inconsistent"),
        "host bits in static network rejected");
    r.routes.add(new Device.Route("0.0.0.0", "0.0.0.0", "10.0.0.2"));
    r2.routes.add(new Device.Route("0.0.0.0", "0.0.0.0", "10.0.0.1"));
    check(n.ping(1, "8.8.8.8").text().contains("TTL"), "routing loop stops at TTL");
    check(Device.networkAddress("172.16.5.21", "255.255.0.0").equals("172.16.0.0"),
        "canonical connected route");
    var switchCli = new Console();
    var switchDevice = new Device("switch");
    check(switchCli.execute(switchDevice, n, 2, "e").contains("Ambiguous"), "ambiguous prefixes rejected");
    check(switchCli.mode == 0, "ambiguous command leaves mode intact");
    check(switchCli.complete(switchDevice, "en").value().equals("enable "), "user mode enable completion");
    switchCli.execute(switchDevice, n, 2, "EN");
    check(switchCli.complete(switchDevice, "conf t").value().equals("configure terminal "),
        "multiword completion");
    check(
        switchCli.execute(switchDevice, n, 2, "conf").contains("Incomplete"), "missing subcommand rejected");
    check(switchCli.mode == 1, "incomplete command leaves mode intact");
    check(switchCli.complete(switchDevice, "SH ip i").value().equals("show ip interface "),
        "nested keyword completion");
    check(switchCli.complete(switchDevice, "sh ip ").choices().size() >= 2,
        "ambiguous completion lists choices");
    check(switchCli.execute(switchDevice, n, 2, "sh ip int b").contains("GigabitEthernet0/3"),
        "show shorthand executes");
    check(switchCli.execute(switchDevice, n, 2, "sh ru").contains("hostname Switch"),
        "running config shorthand executes");
    switchCli.execute(switchDevice, n, 2, "conf t");
    for (int length = 1; length <= "gigabitethernet".length(); length++) {
      String family = "gigabitethernet".substring(0, length);
      check(
          switchCli.execute(switchDevice, n, 2, "int " + family + "0/0").isEmpty() && switchCli.selected == 0,
          "interface family prefix " + family);
    }
    switchCli.execute(switchDevice, n, 2, "int GIG 0/2");
    check(switchCli.selected == 2, "separate family and port number");
    check(switchCli.complete(switchDevice, "int gig0/3").value().equals("interface GigabitEthernet0/3 "),
        "interface alias completion");
    check(switchCli.complete(switchDevice, "int gig 0/1").value().equals("interface GigabitEthernet0/1 "),
        "separated interface completion");
    check(switchCli.complete(switchDevice, "int g").choices().size() == 4, "all switch ports suggested");
    check(switchCli.complete(switchDevice, "int gig0/9").choices().isEmpty(), "missing port not suggested");
    check(switchCli.selected == 2, "completion never executes interface selection");
    check(switchCli.execute(switchDevice, n, 2, "int gig0/10").contains("does not exist")
            && switchCli.selected == 2,
        "port numbers are exact");
    switchCli.execute(switchDevice, n, 2, "sh");
    check(!switchDevice.ports.get(2).up, "shutdown abbreviation in interface context");
    switchCli.execute(switchDevice, n, 2, "n s");
    check(switchDevice.ports.get(2).up, "no shutdown abbreviation");
    check(switchCli.execute(switchDevice, n, 2, "do sh ip int b").contains("GigabitEthernet0/2"),
        "do show in configuration context");
    switchCli.execute(switchDevice, n, 2, "ho STUDY-SW");
    check(switchDevice.hostname.equals("STUDY-SW"), "argument case retained");
    check(switchCli.complete(switchDevice, "en").value().equals("end "), "end completion depends on mode");
    switchCli.execute(switchDevice, n, 2, "en");
    check(switchCli.mode == 1, "end abbreviation in configuration context");
    switchCli.execute(switchDevice, n, 2, "disa");
    check(switchCli.mode == 0, "disable shorthand");
    var pcCli = new Console();
    var host = new Device("pc");
    pcCli.execute(host, n, 1, "ipc 10.1.2.3 255.255.255.0");
    check(host.ports.getFirst().ip.equals("10.1.2.3"), "PC ipconfig shorthand");
    check(CommandGrammar.interfaceIndex(host, "ethernet0") == 0, "PC interface alias");
    var updateSwitch = new Device("switch");
    var updateCli = new Console();
    updateCli.execute(updateSwitch,n,2,"enable");
    updateCli.execute(updateSwitch,n,2,"configure terminal");
    check(updateCli.execute(updateSwitch,n,2,"vtp domain SCHOOL").isEmpty()
        && updateSwitch.os().vtpDomain.equals("SCHOOL"), "switch VTP command applies arguments");
    check(updateCli.execute(updateSwitch,n,2,"vtp mode transparent").isEmpty()
        && updateSwitch.os().vtpMode.equals("transparent"), "switch VTP mode changes");
    updateCli.execute(updateSwitch,n,2,"vlan 10");
    updateCli.execute(updateSwitch,n,2,"name USERS");
    check(updateSwitch.os().vlans.get(10).equals("USERS"), "switch VLAN mode keeps name");
    var pcUpdate = new Device("pc");
    var pcUpdateCli = new Console();
    pcUpdateCli.execute(pcUpdate,n,1,"ipconfig 10.1.2.3 255.255.255.0 10.1.2.1");
    check(pcUpdateCli.execute(pcUpdate,n,1,"ipconfig /all").contains("10.1.2.3"), "PC configuration displayed");
    check(!pcUpdateCli.execute(pcUpdate,n,1,"ssh -l admin 10.1.2.1").contains("Invalid IPv4"),
        "PC SSH does not enter static address parser");
    var consolePc=new Device("pc");var consoleRouter=new Device("router");var consoleSwitch=new Device("switch");
    check(ConsoleLink.compatible(consolePc,1,consoleRouter,2),"RS232 to router Console");
    check(ConsoleLink.compatible(consoleSwitch,4,consolePc,1),"switch Console to RS232 reversed endpoints");
    check(!ConsoleLink.compatible(consolePc,0,consoleRouter,2),"Ethernet cannot provide console access");
    check(!ConsoleLink.compatible(consoleRouter,2,consoleSwitch,4),"two Console ports cannot connect");
    check(!ConsoleLink.compatible(consolePc,1,new Device("pc"),1),"RS232 pair is not a device console");
    var consoleNet=new Network();consoleNet.devices.put(11L,consolePc);consoleNet.devices.put(12L,consoleRouter);
    wire(consoleNet,11,1,12,2);
    check(!consoleNet.linkUp(11,1)&&consoleNet.lanPeers(11,1).isEmpty(),"console cable carries no Ethernet frames");
    checks += RouterTest.run();
    checks += SwitchingTest.run();
    checks += HardwareTest.run();
    checks += CliRegressionTest.run();
    checks += SwitchPresentationTest.run();
    checks += ConfigurationSaveTest.run();
    checks += WirelessTest.run();
    checks += AutomationTest.run();
    checks += PortLayoutTest.run();
    checks += OrientationAndHostTest.run();
    checks += ConsoleDiagnosticsTest.run();
    checks += TrafficControlTest.run();
    checks += TrafficConsoleTest.run();
    checks += CommandAuditTest.run();
    checks += RackAutomationTest.run();
    checks += AutomationDemoTest.run();
    checks += PowerAndPortableTest.run();
    checks += WallCablePathTest.run();
    checks += ModuleFabricationTest.run();
    checks += ManagementTest.run();
    checks += ItemPacketTest.run();
    checks += MobileTest.run();
    checks += PhoneNetworkTest.run();
    checks += PortableDevicesTest.run();
    checks += CableLedgerTest.run();
    checks += ServerThermalTest.run();
    checks += SputnikTest.run();
    checks += MediaTest.run();
    checks += dev.netlab.sputnik.client.NativeBrowserLibrariesTest.run();
    System.out.println("NetLab: " + checks + " core checks passed");
  }
}
