package dev.netlab.core;

import java.util.*;

public final class PortableDevicesTest {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    public static int run(){
        checks=0;var laptop=new Device("pc");Hardware.initializeHost(laptop,true);LaptopBattery.prepare(laptop);
        laptop.ports.getFirst().ip="192.168.1.25";laptop.gateway="192.168.1.1";var wireless=Wireless.state(laptop);wireless.clientSsid="Classroom";
        check(LaptopBattery.stored(laptop)==LaptopBattery.CAPACITY&&Hardware.powered(laptop),"fresh laptop has a full physical battery");
        check(LaptopBattery.receive(laptop,1000,40,false)==0,"full laptop cannot accept free or overflowing energy");
        for(int tick=0;tick<24_000;tick++)LaptopBattery.tick(laptop,false);
        check(LaptopBattery.stored(laptop)==0&&!Hardware.powered(laptop),"twenty minutes at 20 TPS drains the laptop and disables networking");
        check(laptop.ports.getFirst().ip.equals("192.168.1.25")&&laptop.gateway.equals("192.168.1.1")&&wireless.clientSsid.equals("Classroom"),"battery depletion never resets IP, gateway or wireless configuration");
        check(LaptopBattery.receive(laptop,100,40,true)==40&&LaptopBattery.stored(laptop)==0,"FE simulation cannot charge the laptop");
        check(LaptopBattery.receive(laptop,100,17,false)==17&&LaptopBattery.stored(laptop)==17&&Hardware.powered(laptop),"charging obeys available FE and powers up the preserved configuration");
        check(LaptopBattery.receive(laptop,-1,40,false)==0&&LaptopBattery.receive(laptop,40,0,false)==0,"invalid or exhausted charging budgets create no energy");
        Hardware.ensure(laptop).powered=false;int before=LaptopBattery.stored(laptop);LaptopBattery.tick(laptop,false);check(LaptopBattery.stored(laptop)==before&&!Hardware.powered(laptop),"powered-off laptop retains battery and does not start itself");
        var budget=new PowerModel.Budget(13);check(budget.draw("rack",40)==13&&budget.energy==0&&budget.draw("rack",1)==0,"partial charging conserves the supplying cabinet balance");
        budget=new PowerModel.Budget(500);check(budget.supply("rack",250)&&budget.draw("rack",40)==6&&budget.energy==244,"battery and devices share the same PSU output limit");
        var desktop=new Device("pc");Hardware.initializeHost(desktop,false);LaptopBattery.prepare(desktop);check(!LaptopBattery.laptop(desktop)&&!Hardware.ensure(desktop).energyRequired,"desktop and virtual phones do not acquire a laptop battery");
        var memory=new PhoneTerminalMemory();var ids=new ArrayList<String>();for(int i=0;i<4;i++)ids.add(UUID.randomUUID().toString());
        var first=memory.open(ids.get(0));first.remember("show version",false);memory.open(ids.get(1));memory.open(ids.get(2));
        check(memory.open(ids.get(0))==first&&first.history.equals(List.of("show version")),"returning to a physical peer restores its history and refreshes recency");
        memory.open(ids.get(3));check(memory.devices.size()==3&&!memory.devices.containsKey(ids.get(1))&&memory.devices.containsKey(ids.get(0)),"fourth peer evicts the least recently opened of the last three devices");
        var replaced=memory.open(UUID.randomUUID().toString());check(replaced.history.isEmpty(),"replacement device at the same address cannot inherit another physical device history");
        first.remember("test-password",true);first.remember("sat key test-secret",Console.sensitive("sat key test-secret"));first.remember("sat subscriber "+ids.getFirst()+" test-secret",Console.sensitive("sat subscriber "+ids.getFirst()+" test-secret"));
        check(first.history.equals(List.of("show version")),"password prompts and satellite credentials are excluded from command recall");
        first.appendText("x".repeat(40_000));for(int i=0;i<80;i++)first.remember("show clock",false);PhoneTerminalMemory.trim(first);
        check(first.text.length()==PhoneTerminalMemory.TEXT_LIMIT&&first.history.size()==60,"three-device history has bounded transcript and command memory");
        var config=new HandsetConfiguration();config.install("terminal",new HandsetConfiguration.Installed("CISCO-POCKET-CONSOLE",null));check(config.has("terminal"),"local phone Console module needs no server binding or SIM");
        config.eject("terminal");check(!config.has("terminal"),"removing the terminal module revokes Console capability");
        return checks;
    }
}
