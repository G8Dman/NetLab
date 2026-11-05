package dev.netlab.core;

import dev.netlab.automation.core.*;
import java.util.*;

public final class AutomationDemoTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;var a=AutomationDemoPlan.choose(List.of());check(a.server().equals("192.168.77.10"),"first demo selects documented subnet");
        var b=AutomationDemoPlan.choose(List.of(a.source()));check(!b.prefix().equals(a.prefix()),"repeated demo avoids occupied LAN prefix");
        var occupied=new ArrayList<String>();for(int i=2;i<=254;i++)occupied.add("192.168."+i+".1");
        try{AutomationDemoPlan.choose(occupied);throw new AssertionError("full address space was reused");}catch(IllegalArgumentException expected){checks++;}
        var n=AutomationDemoPlan.network(a);
        long server=AutomationDemoPlan.Node.SERVER.ordinal(),source=AutomationDemoPlan.Node.SOURCE.ordinal(),destination=AutomationDemoPlan.Node.DESTINATION.ordinal();
        check(n.devices.values().stream().allMatch(d->d.gateway.isEmpty()),"demo has no fabricated gateway");
        check(n.ping(server,a.source()).success()&&n.ping(server,a.destination()).success(),"server reaches both interfaces on same subnet without gateway");
        check(n.transmit(source,a.server(),"tcp",5420,"REGISTER_STATUS".getBytes()).result().success(),"endpoint registration reaches server without gateway");
        check(n.transmit(source,a.destination(),"tcp",5420,"OFFER".getBytes()).result().success(),"source offer reaches destination without gateway");
        check(n.transmit(destination,a.source(),"tcp",5420,"ACCEPT".getBytes()).result().success(),"destination acknowledgement reaches source");
        for(var w:AutomationDemoPlan.LINKS)if(w.cable().equals("console")){
            var laptop=n.devices.get((long)w.a().ordinal());var target=n.devices.get((long)w.b().ordinal());
            check(ConsoleLink.problem(laptop,w.ap(),target,w.bp(),w.cable(),true).isEmpty(),"demo laptop adapter and console wire are ready: "+w.b());
        }
        check(AutomationCli.parse("inspect "+a.source(),1,"server").name().equals("inspect"),"inspect is entered in server EXEC");
        try{AutomationCli.parse("inspect "+a.source(),1,"endpoint");throw new AssertionError("inspect accepted on endpoint");}catch(IllegalArgumentException expected){checks++;}
        var device=new Device("endpoint");
        for(var jack:PortLayout.jacks(device,false,0)){
            check(jack.x()-jack.width()/2>=2.0/16&&jack.x()+jack.width()/2<=14.0/16,"endpoint socket stays inside physical housing");
            check(jack.y()-jack.height()/2>=0&&jack.y()+jack.height()/2<=8.0/16,"endpoint socket stays within face height");
            check(jack.z()<1.9/16,"socket is in front of panel, not hidden inside it");
            check(PortLayout.pick(device,false,0,jack.x(),jack.y())==jack.port(),"visible socket can be selected");
            check(PortLayout.pickRay(device,false,0,jack.x(),jack.y(),-2,jack.x(),jack.y(),.125)==jack.port(),"cable hit ray and rendered socket agree");
        }
        String command="transfer "+a.source()+" "+a.destination()+" minecraft:iron_ingot 16";
        var pages=DemoBookPages.paginate(List.of(new DemoBookPages.Span("На СЕРВЕРЕ, Terminal.\n\n".repeat(15),""),new DemoBookPages.Span(command,command)));
        check(pages.size()>1&&pages.stream().allMatch(p->p.size()<=DemoBookPages.ROWS),"book text paginates within vanilla height");
        check(pages.stream().flatMap(List::stream).allMatch(line->line.text().stripTrailing().length()<=DemoBookPages.COLUMNS),"book lines fit conservative width");
        check(pages.stream().flatMap(List::stream).filter(line->!line.copy().isEmpty()).allMatch(line->line.copy().equals(command)),"any wrapped command fragment copies entire executable command");
        return checks;
    }
}
