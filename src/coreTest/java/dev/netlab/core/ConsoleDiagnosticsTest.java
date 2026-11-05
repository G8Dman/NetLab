package dev.netlab.core;

/** Reproduces the reported saved-world setup: USB RS232 -> powered-off 3650 with only an SFP. */
public final class ConsoleDiagnosticsTest {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    public static int run(){
        var laptop=new Device("pc");Hardware.initializeHost(laptop,true);laptop.hardware.powered=false;
        Hardware.install(laptop,"usb0","USB-RS232");laptop.hardware.powered=true;
        var sw=new Device("switch");Hardware.ensure(sw).powered=false;Hardware.changeProfile(sw,"3650-24PS");Hardware.install(sw,"sfp0","GLC-LH-SMD");
        int console=-1;for(int i=0;i<sw.ports.size();i++)if(sw.ports.get(i).console)console=i;
        check(ConsoleLink.compatible(laptop,1,sw,console),"reported RS232/Console pair is wired correctly");
        String error=ConsoleLink.problem(laptop,1,sw,console,"console",true);
        check(error.contains("выключено")&&error.contains("3650-24PS"),"reports powered-off chassis rather than missing cable");
        check(error.contains("AC-POWER-SUPPLY")&&error.contains("power0"),"reports missing supply and the slot to install it");
        check(!Hardware.powerProblem(sw).isBlank(),"SFP does not satisfy power supply requirement");
        Hardware.install(sw,"power0","AC-POWER-SUPPLY");check(Hardware.powerProblem(sw).isBlank(),"AC supply satisfies chassis requirement");
        error=ConsoleLink.problem(laptop,1,sw,console,"console",true);
        check(error.contains("выключено")&&!error.contains("Установи AC-POWER-SUPPLY"),"installed supply still requires the power button");
        sw.hardware.powered=true;check(ConsoleLink.problem(laptop,1,sw,console,"console",true).isBlank(),"powered device accepts the same existing console cable");
        check(!ConsoleLink.problem(laptop,1,sw,console,"console",false).isBlank(),"diagnostics do not bypass reciprocal cable validation");
        check(!ConsoleLink.problem(laptop,1,sw,console,"copper",true).isBlank(),"diagnostics do not permit Ethernet cable on Console");
        int ethernet=sw.findInterface("GigabitEthernet1/0/1",false);
        check(ethernet>=0&&!ConsoleLink.problem(laptop,1,sw,ethernet,"console",true).isBlank(),"existing Ethernet destination cannot grant CLI access");
        laptop.hardware.powered=false;check(ConsoleLink.problem(laptop,1,sw,console,"console",true).contains("ПК/ноутбук выключен"),"host power remains enforced");
        Hardware.remove(laptop,"usb0");laptop.hardware.powered=true;
        check(ConsoleLink.problem(laptop,1,sw,console,"console",true).contains("USB-RS232"),"removed adapter has a specific diagnostic");
        return checks;
    }
}
