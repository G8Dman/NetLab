package dev.netlab.core;

/** The battery is physical laptop state and survives both placement and power loss. */
public final class LaptopBattery {
    public static final String KEY="netlab.laptopBattery",CHARGING="netlab.laptopCharging";
    public static final int CAPACITY=48_000,CONSUMPTION=2,CHARGE_RATE=40;
    public static boolean laptop(Device d){return d!=null&&d.kind.equals("pc")&&d.hardware!=null&&d.hardware.profile.equals("Laptop-PT");}
    public static int stored(Device d){if(!laptop(d))return 0;try{return Math.clamp(Integer.parseInt(d.extensions.getOrDefault(KEY,""+CAPACITY)),0,CAPACITY);}catch(NumberFormatException e){return 0;}}
    public static void prepare(Device d){if(!laptop(d))return;d.extensions.put(KEY,""+stored(d));Hardware.ensure(d).energyRequired=true;Hardware.ensure(d).energySupplied=stored(d)>0;}
    public static int receive(Device d,int amount,int budget,boolean simulate){if(!laptop(d)||amount<=0||budget<=0)return 0;int value=Math.min(amount,Math.min(CAPACITY-stored(d),Math.min(CHARGE_RATE,budget)));if(!simulate&&value>0){d.extensions.put(KEY,""+(stored(d)+value));prepare(d);}return value;}
    public static boolean tick(Device d,boolean charging){if(!laptop(d))return false;int before=stored(d);if(Hardware.ensure(d).powered)d.extensions.put(KEY,""+Math.max(0,before-CONSUMPTION));d.extensions.put(CHARGING,""+charging);prepare(d);return before!=stored(d);}
    public static String status(Device d){return "Аккумулятор: "+(stored(d)*100/CAPACITY)+"% · "+stored(d)+" / "+CAPACITY+" FE"+(Boolean.parseBoolean(d.extensions.get(CHARGING))?" · зарядка":"");}
}
