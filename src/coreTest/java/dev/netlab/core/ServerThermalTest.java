package dev.netlab.core;

import dev.netlab.automation.core.*;

/** Real thermal model and hardware slots without any game runtime. */
public final class ServerThermalTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static void rejects(Runnable operation,String why){boolean rejected=false;try{operation.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,why);}
    private static Device server(String... modules){var d=new Device("server");var h=Hardware.ensure(d);h.profile="Automation Server";h.powered=false;for(String m:modules)Hardware.install(d,m.equals(ServerHardware.ACCEL)?"accel0":m.equals(ServerHardware.BUFFER)?"buffer0":"cooling0",m);h.powered=true;return d;}
    public static int run(){
        checks=0;ServerHardware.register();var bare=server();var basic=server(ServerHardware.FAN);var enhanced=server(ServerHardware.ACCEL,ServerHardware.BUFFER,ServerHardware.FAN);var pro=server(ServerHardware.ACCEL,ServerHardware.BUFFER,ServerHardware.PRO);
        check(Hardware.slots(bare).size()==3&&Hardware.slots(bare).get("cooling0").equals("SERVER-COOLING"),"server has separate CPU, buffer and single cooler slot");
        check(ServerHardware.itemLimit(bare)==64&&ServerHardware.itemLimit(pro)==256,"buffer controls actual logical batch limit");
        check(PowerModel.demand(bare)==12&&PowerModel.demand(basic)==14&&PowerModel.demand(pro)==30,"upgrades and cooling consume additional FE");
        check(pro.ports.size()==bare.ports.size(),"cooling and application cards create no phantom network ports");
        rejects(()->Hardware.install(bare,"accel0",ServerHardware.ACCEL),"hot-plugging is forbidden");
        Hardware.ensure(bare).powered=false;
        rejects(()->Hardware.install(bare,"accel0",ServerHardware.BUFFER),"buffer does not fit CPU slot");
        Hardware.ensure(basic).powered=false;rejects(()->Hardware.install(basic,"cooling0",ServerHardware.PRO),"two coolers cannot occupy a single slot");Hardware.ensure(basic).powered=true;
        var router=new Device("router");Hardware.ensure(router).powered=false;rejects(()->Hardware.install(router,"cooling0",ServerHardware.FAN),"server cooler does not fit a router");
        Hardware.ensure(bare).powered=true;var heat=new ServerHeat();for(int i=0;i<120&&!heat.protectedStop;i++)heat.advance(bare,1);check(heat.protectedStop&&heat.temperature>=90,"uncooled running server trips thermal protection");
        heat=new ServerHeat();for(int i=0;i<600;i++){heat.work(basic,.8);heat.advance(basic,1);}check(!heat.protectedStop&&heat.temperature<60,"basic cooler supports an active basic server");
        heat=new ServerHeat();for(int i=0;i<600&&!heat.protectedStop;i++)heat.advance(enhanced,1);check(heat.protectedStop,"basic cooler cannot support both upgrades continuously");
        heat=new ServerHeat();for(int i=0;i<600;i++){heat.work(pro,4);heat.advance(pro,1);check(!heat.protectedStop,"pro cooler sustains upgraded representative load "+i);}check(heat.temperature<90,"upgraded cooling reaches a safe equilibrium");
        heat.temperature=89;heat.work(pro,2);check(heat.protectedStop,"work can trigger thermal protection immediately");double stopped=heat.temperature;heat.work(pro,32);check(heat.temperature==stopped,"protected server cannot perform more work");
        heat.temperature=70;heat.normalize();check(heat.protectedStop,"protection remains latched above resume temperature");heat.temperature=60;heat.normalize();check(!heat.protectedStop,"resume threshold releases protection");
        heat.temperature=90;heat.normalize();Hardware.ensure(pro).powered=false;for(int i=0;i<20;i++)heat.advance(pro,1);check(!heat.protectedStop&&heat.temperature<60,"powered-off server cools and recovers without losing hardware");check(PowerModel.demand(pro)==0,"powered-off upgraded server draws no active FE");Hardware.ensure(pro).powered=true;
        var fast=new ServerHeat();check(fast.processingNanos(basic)==4*fast.processingNanos(pro)&&fast.offerTicks(basic)==4*fast.offerTicks(pro),"accelerator reduces real CPU and offer delays fourfold");fast.temperature=70;check(fast.processingNanos(pro)==100_000_000L&&fast.offerTicks(pro)==200,"hot server throttles both delays twofold");
        var noFeed=server(ServerHardware.ACCEL);Hardware.ensure(noFeed).energyRequired=true;heat=new ServerHeat();heat.temperature=65;heat.advance(noFeed,1);check(heat.temperature<65,"missing FE cannot generate active heat");
        heat.temperature=Double.NaN;heat.normalize();check(heat.temperature==25,"invalid saved temperatures normalize safely");
        Hardware.ensure(pro).powered=false;Hardware.remove(pro,"buffer0");check(ServerHardware.itemLimit(pro)==64,"removing buffer immediately restores batch limit");
        for(int nativeLimit:new int[]{1,16,64})for(int total:new int[]{1,16,63,64,65,128,255,256}){int left=total,escrow=0,tickets=0;while(left>0){int take=Math.min(nativeLimit,ServerHardware.part(left));check(take>0&&take<=64&&take<=nativeLimit,"escrow obeys native stack limit");left-=take;escrow+=take;tickets++;}check(escrow==total&&tickets<=total,"batch splitting conserves count");}
        for(String id:ServerHardware.MODULES)check(!ModuleFabrication.MODULES.contains(id),"server upgrade cannot use cheap network module fabrication: "+id);
        var command=dev.netlab.automation.core.AutomationCli.parse("show server",1,"server");check(command.name().equals("show server"),"server thermal diagnostics are a real CLI command");rejects(()->dev.netlab.automation.core.AutomationCli.parse("show server",1,"endpoint"),"thermal diagnostics are server-only");
        var stock=new AutomationProgram.Stock("a","b","minecraft:coal",-1,-1,1024,256);check(stock.batch()==256,"automation rules retain upgraded batch sizes");
        return checks;
    }
}
