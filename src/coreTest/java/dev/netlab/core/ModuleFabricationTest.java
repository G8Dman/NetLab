package dev.netlab.core;

public final class ModuleFabricationTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;check(ModuleFabrication.MODULES.equals(Hardware.BUILTIN_MODULES),"base modules are fabricated; addon upgrade recipes cannot be bypassed");
        for(String id:ModuleFabrication.MODULES){var cost=ModuleFabrication.cost(id);
            check(cost.boards()>0&&cost.copper()>0&&cost.redstone()>0,id+" cannot be fabricated without all three ingredients");
            check(cost.boards()<=64&&cost.copper()<=64&&cost.redstone()<=64,id+" fits ordinary ingredient stacks");
            check(cost.available(cost.boards(),cost.copper(),cost.redstone()),id+" accepts exact quantities");
            check(!cost.available(cost.boards()-1,cost.copper(),cost.redstone()),id+" requires all boards");
            check(!cost.available(cost.boards(),cost.copper()-1,cost.redstone()),id+" requires all copper");
            check(!cost.available(cost.boards(),cost.copper(),cost.redstone()-1),id+" requires all redstone");
        }
        check(ModuleFabrication.cost("NM-ESW-161").boards()>ModuleFabrication.cost("WIC-1T").boards(),"large switching cards require more base boards");
        check(ModuleFabrication.cost("USB-RS232").available(64,64,64),"host console adapter remains obtainable");
        try{ModuleFabrication.cost("INVALID");throw new AssertionError("Unknown module accepted");}catch(IllegalArgumentException expected){checks++;}
        return checks;
    }
}
