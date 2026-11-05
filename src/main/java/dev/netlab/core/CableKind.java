package dev.netlab.core;

import java.util.*;

public final class CableKind {
    public static final List<String> TYPES=List.of("copper","crossover","console","serial_dce","serial_dte","fiber","phone","async");
    public static final Map<String,String> EXTRA_MEDIA=new java.util.concurrent.ConcurrentHashMap<>(),EXTRA_NAMES=new java.util.concurrent.ConcurrentHashMap<>();
    public static String medium(Device d,int port){var p=d.ports.get(port);if(p.console)return "console";if(d.portName(port).startsWith("Serial"))return "serial";return p.medium==null?"copper":p.medium;}
    public static boolean compatible(String type,Device a,int ap,Device b,int bp){
        if(ap<0||bp<0||ap>=a.ports.size()||bp>=b.ports.size()||a.ports.get(ap).hidden||b.ports.get(bp).hidden)return false;
        String x=medium(a,ap),y=medium(b,bp);
        return switch(type){
            case "wifi"->x.equals("wireless")&&y.equals("wireless");
            case "auto"->x.equals(y)&&!x.equals("sfp")&&!x.equals("wireless");
            case "copper","crossover"->x.equals("copper")&&y.equals("copper");
            case "fiber"->x.equals("fiber")&&y.equals("fiber")||x.equals("fronthaul")&&y.equals("fronthaul");
            case "serial_dce","serial_dte"->x.equals("serial")&&y.equals("serial");
            case "phone"->x.equals("phone")&&y.equals("phone");
            case "console"->ConsoleLink.compatible(a,ap,b,bp);
            case "async"->x.equals("async")&&y.equals("console")||y.equals("async")&&x.equals("console");
            default->EXTRA_MEDIA.containsKey(type)&&x.equals(EXTRA_MEDIA.get(type))&&y.equals(EXTRA_MEDIA.get(type));
        };
    }
    public static int color(String type){return switch(type){case "console","async"->0x66ddff;case "fiber"->0xffaa22;case "serial_dce","serial_dte"->0xdd5555;case "phone"->0xaaaaaa;case "crossover"->0x222222;default->0x237de6;};}
    public static String storedType(String type,long source,int sourcePort,long target,int targetPort){
        boolean reversed=source>target||source==target&&sourcePort>targetPort;
        return type.startsWith("serial")&&reversed?(type.equals("serial_dte")?"serial_dce":"serial_dte"):type;
    }
}
