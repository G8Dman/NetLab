package dev.netlab.core;

public final class PowerAndPortableTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;check(PowerModel.receive(999990,1000,10000)==10,"FE input cannot exceed storage capacity");
        check(PowerModel.receive(0,100000,10000)==10000,"FE input obeys tick budget");
        check(PowerModel.receive(0,-100,10000)==0&&PowerModel.receive(0,100,0)==0,"negative input and exhausted budget cannot create FE");
        var budget=new PowerModel.Budget(20);check(budget.supply("rack-a",8)&&budget.supply("rack-b",12)&&budget.energy==0,"two PSUs draw from one conserved cabinet balance");
        check(!budget.supply("rack-a",1)&&budget.energy==0,"empty cabinet cannot power another board");
        budget=new PowerModel.Budget(1000);check(budget.supply("rack-a",256)&&!budget.supply("rack-a",1)&&budget.supply("rack-b",1)&&budget.energy==743,"PSU rate is limited independently without free energy");
        check(!budget.supply("rack-a",-1)&&budget.energy==743,"negative load cannot charge a cabinet");
        var standby=new PowerModel.Budget(1);check(standby.supply("rack",0)&&standby.energy==1,"manually disabled board senses feed but consumes no active power");
        check(!new PowerModel.Budget(0).supply("rack",0),"empty feed is not indicated as energized");
        var router=new Device("router");var h=Hardware.ensure(router);h.energyRequired=true;
        check(!Hardware.powered(router)&&Hardware.powerProblem(router).contains("FE"),"FE requirement prevents boot without feed");
        h.energySupplied=true;check(Hardware.powered(router),"board boots with delivered energy");h.powered=false;check(!Hardware.powered(router),"manual Power off remains effective with feed");
        h.powered=true;var cli=new Console();var lease=new PortableLease(router,100,cli);check(lease.valid(router,true,16,101),"portable session accepts nearby held terminal on running board");
        check(!lease.valid(router,false,16,101)&&!lease.valid(router,true,65,101),"portable session rejects missing item and excess range");
        check(!lease.valid(router,true,16,6100),"idle portable session expires");lease.touch(6000);check(lease.valid(router,true,16,6100),"activity renews portable authorization");
        h.energySupplied=false;check(!lease.valid(router,true,16,6100),"power failure invalidates portable access");h.energySupplied=true;h.epoch++;check(!lease.valid(router,true,16,6100),"reboot cannot reuse portable authentication");
        cli.history.add("show running-config");lease.close();check(cli.history.isEmpty()&&!lease.valid(router,true,16,6100),"closing clears command history and permanently ends lease");
        var next=new PortableLease(router,6100,new Console());check(!next.token.equals(lease.token)&&next.cli.history.isEmpty()&&next.cli.mode==0,"reopening has fresh token, mode and empty history");
        check(!next.valid(new Device("router"),true,16,6101),"replaced board cannot inherit a portable session");
        var setup=new Console();var setupNet=new Network();setupNet.devices.put(2L,router);var configuring=new PortableLease(router,6100,setup);
        for(String command:java.util.List.of("enable","configure terminal","hostname PERSISTENT","interface gig0/0","ip address 192.168.1.1 255.255.255.0","no shutdown","end"))setup.execute(router,setupNet,2,command);
        configuring.close();var reopened=new PortableLease(router,6101,new Console());
        check(router.hostname.equals("PERSISTENT")&&router.ports.getFirst().ip.equals("192.168.1.1")&&router.ports.getFirst().up,"closing portable authorization never resets configured router state");
        check(reopened.cli.history.isEmpty()&&reopened.cli.mode==0&&setup.history.isEmpty(),"device settings survive while both portable command histories are empty");
        var n=new Network();var pc=new Device("pc");pc.ports.getFirst().ip="192.168.1.2";router.ports.getFirst().ip="192.168.1.1";router.ports.getFirst().up=true;n.devices.put(1L,pc);n.devices.put(2L,router);n.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),4));
        check(n.ping(1,"192.168.1.1").success(),"powered router receives actual model traffic");h.energySupplied=false;check(!n.ping(1,"192.168.1.1").success()&&!n.linkUp(2,0),"removing FE stops link and packet delivery");
        check(PowerModel.board(new Device("endpoint"))&&PowerModel.board(new Device("server"))&&!PowerModel.board(pc),"addon boards require power; hosts retain their existing battery model");
        check(new Device("energy").ports.isEmpty()&&new Device("energy_source").ports.isEmpty(),"FE cabinets have no phantom Ethernet or Console");
        return checks;
    }
}
