package dev.netlab.core;
import dev.netlab.automation.core.*;
import java.util.*;
public final class ManagementTest {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;var pc=new Device("pc");Hardware.initializeHost(pc,true);var hardware=Hardware.ensure(pc);hardware.powered=false;int ports=pc.ports.size();
        Hardware.install(pc,"app0","CISCO-AUTOMATION");hardware.powered=true;
        check(ManagementAccess.installed(pc)&&pc.ports.size()==ports,"application module enables management without adding fake network ports");
        check(Hardware.slots(pc).containsKey("usb0")&&Hardware.slots(pc).containsKey("radio0"),"application slot coexists with laptop Console and Wi-Fi adapters");
        hardware.powered=false;Hardware.remove(pc,"app0");hardware.powered=true;check(!ManagementAccess.installed(pc),"removing the module revokes application eligibility");
        var server=new Device("server");pc.ports.get(0).ip="10.8.0.2";server.ports.get(0).ip="10.8.0.3";server.ports.get(0).up=true;
        var n=new Network();n.devices.put(1L,pc);n.devices.put(2L,server);n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),3));
        check(ManagementAccess.local(n,1,"10.8.0.3"),"direct local server is eligible for login");
        n.wires.clear();var router=new Device("router");router.ports.get(0).ip="10.8.0.1";router.ports.get(1).ip="10.9.0.1";router.ports.get(0).up=true;router.ports.get(1).up=true;server.ports.get(0).ip="10.9.0.3";pc.gateway="10.8.0.1";server.gateway="10.9.0.1";n.devices.put(3L,router);n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(3,0),2));n.wires.add(new Network.Wire(new Network.Endpoint(3,1),new Network.Endpoint(2,0),2));
        check(!ManagementAccess.local(n,1,"10.9.0.3"),"a routed server cannot bypass the local-login restriction");
        var password=ServerPassword.create("test-password-123");check(ServerPassword.verify("test-password-123",password),"correct server password authenticates");check(!ServerPassword.verify("wrong-password",password)&&!ServerPassword.verify("test-password-123","broken"),"wrong password or malformed verifier rejected");check(!password.equals(ServerPassword.create("test-password-123")),"same passwords receive different salts");
        try{ServerPassword.create("short");throw new AssertionError("Short password allowed");}catch(IllegalArgumentException expected){checks++;}
        var chain=new AuditChain();chain.add("reserve|ticket-a|64 iron");String first=chain.head();chain.add("deliver|ticket-a|64 iron");check(chain.valid()&&!chain.head().equals(first),"audit records bind transaction transitions in order");var original=chain.entries.get(1);chain.entries.set(1,new AuditChain.Entry(original.previous(),"deliver|ticket-a|64 diamond",original.hash()));check(!chain.valid(),"editing an audit event is detected");
        chain=new AuditChain();for(int i=0;i<4100;i++)chain.add("transaction "+i);check(chain.entries.size()==4096&&chain.valid()&&!chain.anchor.equals("GENESIS"),"bounded audit preserves a verifiable retained prefix");
        var stock=new AutomationProgram.Stock("10.0.0.1","10.0.0.2","minecraft:coal",-1,1,16,8);check(stock.destinationSlot()==1&&stock.minimum()==16,"stock rule retains an explicit furnace fuel slot");
        try{new AutomationProgram.Stock("a","b","c",-1,256,1,1);throw new AssertionError("Invalid slot allowed");}catch(IllegalArgumentException expected){checks++;}
        var grid=new ArrayList<>(Collections.nCopies(9,"-"));grid.set(0,"minecraft:coal");grid.set(3,"minecraft:stick");var pattern=new AutomationProgram.Pattern("torches","a","b","a","minecraft:torch",64,grid);grid.set(0,"minecraft:diamond");check(pattern.grid().get(0).equals("minecraft:coal"),"recipe snapshot does not alias an editor's mutable grid");
        try{new AutomationProgram.Pattern("empty","a","b","a","c",1,Collections.nCopies(9,"-"));throw new AssertionError("Empty pattern accepted");}catch(IllegalArgumentException expected){checks++;}
        var history=dev.netlab.automation.core.ServerHistory.success(List.of("10.0.0.1","10.0.0.2"),"10.0.0.2");
        check(history.equals(List.of("10.0.0.2","10.0.0.1")),"successful address moves to the front without duplicate login-history entries");
        check(dev.netlab.automation.core.ServerHistory.clean(List.of("password", "10.0.0.1", "10.0.0.1", "10.0.0.2:secret")).equals(List.of("10.0.0.1")),"recent-server store excludes malformed addresses and credential strings");
        var many=new ArrayList<String>();for(int i=1;i<=30;i++)many.add("192.168.4."+i);
        check(dev.netlab.automation.core.ServerHistory.success(many,"10.0.0.1").size()==16&&many.size()==30,"address history is bounded and does not mutate its source");
        try{dev.netlab.automation.core.ServerHistory.success(history,"wrong");throw new AssertionError("Invalid login address accepted");}catch(IllegalArgumentException expected){checks++;}
        var any=new AutomationProgram.Stock("a","b",AutomationProgram.ANY_ITEM,5,2,1,16);
        var contents=List.of(new AutomationProgram.Available(0,"minecraft:diamond",64),new AutomationProgram.Available(5,"minecraft:iron_ingot",7));
        var dispatch=AutomationProgram.drain(any,contents).orElseThrow();
        check(dispatch.item().equals("minecraft:iron_ingot")&&dispatch.sourceSlot()==5&&dispatch.destinationSlot()==2&&dispatch.amount()==7,"any-item rule binds the requested real slot and preserves destination slot");
        check(AutomationProgram.drain(any,List.of(new AutomationProgram.Available(0,"minecraft:coal",64),new AutomationProgram.Available(5,"minecraft:air",0))).isEmpty(),"empty selected slot cannot drain a different occupied slot");
        var anywhere=new AutomationProgram.Stock("a","b",AutomationProgram.ANY_ITEM,-1,-1,4096,16);
        var mixed=List.of(new AutomationProgram.Available(0,"minecraft:coal",0),new AutomationProgram.Available(3,"example:processed_ore",48));
        dispatch=AutomationProgram.drain(anywhere,mixed).orElseThrow();check(dispatch.item().equals("example:processed_ore")&&dispatch.sourceSlot()==3&&dispatch.amount()==16,"all-slot drain skips blocked stacks and emits a concrete mod item in bounded batches");
        var nextItem=AutomationProgram.drain(anywhere,List.of(new AutomationProgram.Available(3,"minecraft:gold_ingot",2))).orElseThrow();
        check(nextItem.item().equals("minecraft:gold_ingot")&&nextItem.amount()==2,"next source snapshot can change item type without changing the rule");
        check(AutomationProgram.drain(anywhere,List.of(new AutomationProgram.Available(0,"minecraft:air",64),new AutomationProgram.Available(1,"*",64),new AutomationProgram.Available(999,"minecraft:diamond",64))).isEmpty(),"air, wildcard manifests and invalid source slots never become transfers");
        check(any.anyItem()&&!stock.anyItem(),"ordinary stock rules remain separate from any-item draining");
        try{AutomationProgram.drain(stock,contents);throw new AssertionError("Ordinary stock treated as drain");}catch(IllegalArgumentException expected){checks++;}
        return checks;
    }
}
