package dev.netlab.core;

import dev.netlab.automation.core.AutomationCli;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class RackAutomationTest {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    private static void denied(Runnable work,String text){try{work.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(text);}
    private static void wire(Network n,long a,int ap,long b,int bp){n.wires.add(new Network.Wire(new Network.Endpoint(a,ap),new Network.Endpoint(b,bp),4));}
    private static void address(Device d,int p,String ip){d.ports.get(p).ip=ip;d.ports.get(p).up=true;}
    public static int run(){
        checks=0;var n=new Network();var client=new Device("pc");
        for(String kind:List.of("server","endpoint","sniffer")){
            var d=new Device(kind);check(d.ports.size()==2&&d.ports.get(1).console,"automation device has RJ45 and Console: "+kind);
            check(ConsoleLink.compatible(client,1,d,1),"PC physical console supports "+kind);
            var ports=PortLayout.jacks(d,true,1);check(ports.size()==2&&ports.stream().allMatch(j->j.port()>=128&&j.y()>=.62),"upper rack port layout for "+kind);
        }
        var server=new Device("server");n.devices.put(1L,server);var cli=new Console();
        for(String cmd:List.of("enable","configure terminal","hostname SERVER","interface gig0/0","ip address 10.0.2.2 255.255.255.0","no shutdown","exit","ip default-gateway 10.0.2.1","end"))check(!cli.execute(server,n,1,cmd).contains("Invalid"),"server IOS-like console: "+cmd);
        check(server.hostname.equals("SERVER")&&server.gateway.equals("10.0.2.1")&&server.ports.getFirst().ip.equals("10.0.2.2"),"console changes server L3 configuration");
        check(AutomationCli.parse("server-address 10.0.2.2",2,"endpoint").args().getFirst().equals("10.0.2.2"),"endpoint manager configured in global mode");
        denied(()->AutomationCli.parse("server-address 10.0.2.2",0,"endpoint"),"endpoint manager requires config mode");
        denied(()->AutomationCli.parse("automation run",2,"endpoint"),"endpoint cannot become a management server");
        denied(()->AutomationCli.parse("transfer 10.0.1.2 10.0.1.3 minecraft:iron_ingot 4",0,"server"),"transfers require privileged EXEC");
        check(AutomationCli.parse("automation rule 10.0.1.2 10.0.1.3 minecraft:iron_ingot 16 4",2,"server").args().size()==5,"programmable rule accepted");
        check(AutomationCli.parse("ip address 10.0.1.2 255.255.255.0",3,"endpoint")==null,"standard interface configuration delegates to core console");
        check(AutomationCli.parse("transfer-fluid 10.0.1.2 10.0.1.3 minecraft:water 1000",1,"server").args().size()==4,"server console accepts fluid transfer with mB volume");
        denied(()->AutomationCli.parse("transfer-fluid 10.0.1.2 10.0.1.3 minecraft:water 1000",0,"server"),"fluid transfer requires enable");
        denied(()->AutomationCli.parse("transfer-fluid 10.0.1.2 10.0.1.3 minecraft:water 1000",1,"endpoint"),"fluid transfer is executed by management server");
        n=new Network();var src=new Device("endpoint");var sw1=new Device("switch");var r1=new Device("router");var r2=new Device("router");var sw2=new Device("switch");
        n.devices.put(1L,src);n.devices.put(2L,sw1);n.devices.put(3L,r1);n.devices.put(4L,r2);n.devices.put(5L,sw2);n.devices.put(6L,server);
        address(src,0,"10.0.1.2");src.gateway="10.0.1.1";address(r1,0,"10.0.1.1");address(r1,1,"172.16.0.1");address(r2,0,"172.16.0.2");address(r2,1,"10.0.2.1");
        r1.routes.add(new Device.Route("10.0.2.0","255.255.255.0","172.16.0.2"));r2.routes.add(new Device.Route("10.0.1.0","255.255.255.0","172.16.0.1"));
        wire(n,1,0,2,0);wire(n,2,1,3,0);wire(n,3,1,4,0);wire(n,4,1,5,0);wire(n,5,1,6,0);
        byte[] clear="REGISTER_STATUS inventory=iron_ingot count=12".getBytes(StandardCharsets.UTF_8);
        check(n.transmit(1,"10.0.2.2","tcp",5420,clear).result().success(),"multiinterface request routes through switches and gateways to server IP");
        var channel=new CryptoChannel(CryptoChannel.derive("Router-only-test-secret",new byte[0]));var transform=new RouterTunnel(new VpnPeers.Tunnel(3,4,channel));n.routerPayload=transform;n.traffic.clear();
        var delivery=n.transmit(1,"10.0.2.2","tcp",5420,clear);
        check(delivery.result().success()&&transform.completed()&&Arrays.equals(delivery.payload(),clear),"receiving router restores plaintext before server delivery");
        check(!new String(transform.envelope(),StandardCharsets.UTF_8).contains("iron_ingot"),"WAN envelope hides inventory content");
        check(n.traffic.stream().anyMatch(e->e.from().device()==2&&e.to().device()==3&&e.protocol().equals("TCP")),"source LAN carries ordinary TCP, not endpoint-encrypted UDP");
        check(n.traffic.stream().anyMatch(e->e.from().device()==3&&e.to().device()==4&&e.protocol().equals("UDP")),"only router-to-router WAN uses encrypted UDP");
        check(n.traffic.stream().anyMatch(e->e.from().device()==5&&e.to().device()==6&&e.protocol().equals("TCP")),"server LAN receives ordinary TCP after router decryption");
        src.gateway="";n.routerPayload=(id,p)->p;check(!n.transmit(1,"10.0.2.2","tcp",5420,clear).result().success(),"endpoint cannot bypass missing gateway to remote server");
        server.extensions.put("automation","{\"manager\":\"10.0.2.2\"}");var restored=ConfigStore.restore(ConfigStore.snapshot(server));check(restored.extensions.equals(server.extensions),"device configuration snapshot preserves addon state");
        return checks;
    }
}
