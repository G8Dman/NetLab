package dev.netlab.automation.core;

import dev.netlab.core.*;
import java.util.*;

/** The application command grammar is independent of block entities and GUI packets. */
public final class AutomationCli {
    public record Command(String name,List<String> args){}
    public static final Map<String,String> HELP=new LinkedHashMap<>();
    static {
        HELP.put("management password","PASSWORD — пароль приложения, 8..64 символа (config, server)");
        HELP.put("receiver","public|private — основной публичный приёмный интерфейс (config, endpoint)");
        HELP.put("server-address","IP — адрес управляющего сервера (config, endpoint)");
        HELP.put("target","down|up|north|south|west|east — сторона подключённого блока (config, endpoint)");
        HELP.put("permission","extract|insert|control on|off — разрешения мультиинтерфейса (config)");
        HELP.put("vpn","required|optional — требовать роутерный VPN для межсетевой доставки (config)");
        HELP.put("automation rule","SOURCE DESTINATION ITEM MINIMUM BATCH — добавить правило (config, server)");
        HELP.put("automation clear","— удалить правила и остановить сервер (config)");
        HELP.put("automation run","— запустить правила (config)");HELP.put("automation stop","— остановить правила (config)");
        HELP.put("inspect","IP — запросить состояние мультиинтерфейса (EXEC #, server)");
        HELP.put("transfer","SOURCE DESTINATION ITEM COUNT — передать предметы (EXEC #, server)");
        HELP.put("transfer-fluid","SOURCE DESTINATION FLUID MB — передать 1..16000 mB жидкости (EXEC #, server)");
        HELP.put("machine","IP ACTION — команда блоку (EXEC #, server)");
        HELP.put("claim","TICKET DESTINATION — погасить билет (EXEC #, server)");HELP.put("return","TICKET — вернуть груз (EXEC #, server)");
        for(String name:List.of("automation","rules","jobs","endpoints","machine","server"))HELP.put("show "+name,"— состояние "+name+" (EXEC)");
    }
    public static Command parse(String line,int mode,String kind){
        String clean=line.strip();for(String name:HELP.keySet())if(clean.equals(name)||clean.startsWith(name+" ")){
            var args=clean.length()==name.length()?List.<String>of():List.of(clean.substring(name.length()+1).strip().split("\\s+"));
            int count=switch(name){case "management password","receiver","server-address","target","vpn","inspect","return"->1;case "permission","claim"->2;case "automation rule"->5;case "transfer","transfer-fluid"->4;case "machine"->-1;default->0;};
            if(count>=0&&args.size()!=count||count<0&&args.size()<2)throw new IllegalArgumentException(name+" "+HELP.get(name));
            if(name.startsWith("show ")){if(Set.of("show rules","show jobs","show endpoints","show server").contains(name)&&!kind.equals("server"))throw new IllegalArgumentException("Нужен сервер управления");return new Command(name,args);}
            boolean config=name.startsWith("automation ")||Set.of("management password","receiver","server-address","target","permission","vpn").contains(name);
            if(config&&mode!=2||!config&&mode!=1)throw new IllegalArgumentException(config?"configure terminal required":"enable required");
            if(Set.of("receiver","server-address","target","permission").contains(name)&&!kind.equals("endpoint"))throw new IllegalArgumentException("Нужен мультиинтерфейс");
            if(!config&&!kind.equals("server")||(name.startsWith("automation ")||name.equals("vpn")||name.equals("management password"))&&!kind.equals("server"))throw new IllegalArgumentException("Нужен сервер управления");
            return new Command(name,args);
        }return null;
    }
    public static String help(){var out=new StringBuilder();HELP.forEach((k,v)->out.append(k).append(' ').append(v).append('\n'));return out.toString();}
}
