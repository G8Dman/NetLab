package dev.netlab.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Front-view order, single-block configuration and independent access/native/allowed state. */
public final class SwitchPresentationTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static Device chassis(){var d=new Device("switch");Hardware.ensure(d).powered=false;Hardware.changeProfile(d,"2960-24TT");return d;}
    private static void command(Console c,Device d,Network n,String text){String result=c.execute(d,n,1,text);if(result.startsWith("%"))throw new AssertionError(text+": "+result);}
    private static String block(String config,String name){String marker="interface "+name+"\n";int at=config.indexOf(marker);if(at<0)throw new AssertionError("Missing "+name);int end=config.indexOf("\n!",at);return config.substring(at,end<0?config.length():end);}
    private static PortLayout.Jack jack(Device d,List<PortLayout.Jack> jacks,String name){int p=d.findInterface(name,false);return jacks.stream().filter(j->j.port()%128==p).findFirst().orElseThrow();}
    public static int run(){checks=0;var d=chassis();var ids=new ArrayList<>(d.ports);
        for(boolean rack:List.of(false,true))for(int bay:rack?List.of(0,1):List.of(0)){
            var j=PortLayout.jacks(d,rack,bay);
            for(int odd=1;odd<24;odd+=2){var upper=jack(d,j,"FastEthernet0/"+odd);var lower=jack(d,j,"FastEthernet0/"+(odd+1));
                check(Math.abs(upper.x()-lower.x())<1e-10&&upper.y()>lower.y(),"2960 pairs odd above even");
                check(PortLayout.pick(d,rack,bay,upper.x(),upper.y())==upper.port(),"visual upper port selects its own saved endpoint");
                check(PortLayout.pick(d,rack,bay,lower.x(),lower.y())==lower.port(),"visual lower port selects its own saved endpoint");
                if(odd<23)check(upper.x()>jack(d,j,"FastEthernet0/"+(odd+2)).x(),"port columns increase left to right in a front view");
            }
            check(jack(d,j,"FastEthernet0/23").x()>jack(d,j,"GigabitEthernet0/1").x(),"Gigabit uplinks are to the right of FastEthernet access ports");
            check(jack(d,j,"GigabitEthernet0/1").y()>jack(d,j,"GigabitEthernet0/2").y(),"Gigabit uplinks retain their vertical 1/2 pair");
            for(int turns=0;turns<4;turns++){
                var first=jack(d,j,"FastEthernet0/1");var third=jack(d,j,"FastEthernet0/3");
                var a=DeviceOrientation.rotate(first.x(),first.y(),first.z(),turns);var b=DeviceOrientation.rotate(third.x(),third.y(),third.z(),turns);
                double screenRight=switch(turns){case 0->-(b.x()-a.x());case 1->-(b.z()-a.z());case 2->b.x()-a.x();default->b.z()-a.z();};
                check(screenRight>0,"left-to-right order is correct for all four orientations");
            }
        }
        check(d.ports.equals(ids),"drawing never reindexes or replaces hardware/cable identities");
        String raw=SwitchConfiguration.render(d);var names=new ArrayList<String>();var matcher=Pattern.compile("(?m)^interface (.+)$").matcher(raw);while(matcher.find())names.add(matcher.group(1));
        var expected=new ArrayList<String>();for(int i=1;i<=24;i++)expected.add("FastEthernet0/"+i);expected.addAll(List.of("GigabitEthernet0/1","GigabitEthernet0/2","Vlan1"));
        check(names.equals(expected),"show run has one numerically sorted block per interface, FE then GE then SVI");
        check(block(raw,"FastEthernet0/1").equals("interface FastEthernet0/1"),"untouched Catalyst port omits default settings");
        check(block(raw,"Vlan1").equals("interface Vlan1\n no ip address\n shutdown"),"default Vlan1 matches the supplied IOS example");
        check(raw.contains("line con 0\n")&&raw.contains("line vty 0 4\n")&&raw.contains("line vty 5 15\n")&&raw.endsWith("end\n"),"IOS-style line sections and final end are present");
        String displayed=SwitchConfiguration.showRunning(d);check(displayed.startsWith("Building configuration...\n\nCurrent configuration : "+raw.getBytes(StandardCharsets.UTF_8).length+" bytes\n!\n"),"configuration byte count describes the actual UTF-8 body");
        check(displayed.indexOf("\nend\n")==displayed.lastIndexOf("\nend\n"),"there is no premature end or appended duplicate configuration");
        var n=new Network();n.devices.put(1L,d);var c=new Console();command(c,d,n,"enable");command(c,d,n,"conf t");command(c,d,n,"interface gi0/1");
        command(c,d,n,"switchport mode trunk");command(c,d,n,"switchport trunk allowed vlan 10");String one=block(c.execute(d,n,1,"do sh run"),"GigabitEthernet0/1");
        check(one.equals("interface GigabitEthernet0/1\n switchport mode trunk\n switchport trunk allowed vlan 10"),"allowed VLAN 10 does not dump unrelated access/native VLAN 1 defaults");
        var p=d.ports.get(d.findInterface("gi0/1",false));check(p.accessVlan==1&&p.trunkNative==1&&p.allowedVlans.equals("10"),"allowed list does not silently rewrite access or native VLAN");
        var peer=new Device("switch");peer.ports.getFirst().trunk=true;peer.ports.getFirst().allowedVlans="10";n.devices.put(2L,peer);d.hardware.powered=true;d.os().vlans.put(10,"USERS");peer.os().vlans.put(10,"USERS");CoreTest.wire(n,1,d.findInterface("gi0/1",false),2,0);
        check(n.linkUp(1,d.findInterface("gi0/1",false))&&n.switching.carries(1,d.findInterface("gi0/1",false),10),"positive control: powered wired trunk carries permitted VLAN 10");
        check(!n.switching.carries(1,d.findInterface("gi0/1",false),1),"native VLAN 1 is not implicitly added to allowed user VLANs");
        command(c,d,n,"switchport trunk native vlan 99");command(c,d,n,"switchport mode access");command(c,d,n,"switchport access vlan 10");
        check(!p.trunk&&p.allowedVlans.equals("10")&&p.trunkNative==99,"switching to access retains inactive explicitly configured trunk parameters");
        String configured=block(SwitchConfiguration.render(d),"GigabitEthernet0/1");check(configured.contains("native vlan 99")&&configured.contains("allowed vlan 10"),"non-default retained settings remain visible in running-config");
        command(c,d,n,"no switchport trunk native vlan");command(c,d,n,"no switchport trunk allowed vlan");
        check(!p.trunk&&p.switchMode.equals("access")&&p.trunkNative==1&&p.allowedVlans.equals("all")&&p.accessVlan==10,"explicit no commands reset trunk parameters without changing access mode or VLAN");
        check(block(SwitchConfiguration.render(d),"GigabitEthernet0/1").equals("interface GigabitEthernet0/1\n switchport mode access\n switchport access vlan 10"),"access VLAN 10 block has no default trunk lines");
        command(c,d,n,"exit");command(c,d,n,"default interface gi0/1");check(block(SwitchConfiguration.render(d),"GigabitEthernet0/1").equals("interface GigabitEthernet0/1"),"default interface restores a compact default Catalyst port");
        var old=new Device("switch");Hardware.ensure(old).powered=false;Hardware.changeProfile(old,"2960-24TT");old.ports.removeIf(x->x.virtual);var saved=ConfigStore.restore(ConfigStore.snapshot(old));ConfigStore.replace(d,saved);
        check(d.findInterface("Vlan1",false)>=0&&!d.ports.get(d.findInterface("Vlan1",false)).up,"loading an older card without SVI1 restores the default down interface");
        var restored=ConfigStore.restore(ConfigStore.snapshot(d));check(SwitchConfiguration.render(restored).equals(SwitchConfiguration.render(d)),"displayed configuration survives save/load without data loss");
        return checks;
    }
}
