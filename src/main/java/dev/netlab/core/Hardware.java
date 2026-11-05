package dev.netlab.core;

import java.io.Serializable;
import java.util.*;

/** Hardware inventory, with stable port identifiers and strict slot formats. */
public final class Hardware implements Serializable {
    private static final long serialVersionUID=1L;
    public boolean powered=true;
    public boolean energyRequired,energySupplied;
    public long epoch;
    public int hostConsoleVersion;
    public String profile="";
    public Map<String,String> modules=new LinkedHashMap<>();
    public record Module(String id,String format,String medium,int ports,int mbps,boolean switching,int wicSlots) {}
    public static final Map<String,Module> CATALOG=Collections.synchronizedMap(new LinkedHashMap<>());
    public static final List<String> BUILTIN_MODULES;
    public static final Set<String> CREATIVE_ONLY=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final Map<String,Map<String,String>> EXTRA_SLOTS=Collections.synchronizedMap(new LinkedHashMap<>());
    public static final Map<String,String> HOST_SLOTS=Collections.synchronizedMap(new LinkedHashMap<>());
    public static final List<java.util.function.BiConsumer<Device,String>> REMOVE_GUARDS=new java.util.concurrent.CopyOnWriteArrayList<>();
    static {
        add("WIC-1T","WIC","serial",1,0,false,0);add("WIC-2T","WIC","serial",2,0,false,0);
        add("HWIC-2T","HWIC","serial",2,0,false,0);add("NIM-2T","NIM","serial",2,0,false,0);
        add("NM-4A/S","NM","serial",4,0,false,0);add("NM-8A/S","NM","serial",8,0,false,0);add("HWIC-8A","HWIC","async",8,0,false,0);
        add("WIC-1ENET","WIC","copper",1,10,false,0);add("HWIC-1GE-SFP","HWIC","sfp",1,1000,false,0);
        add("HWIC-4ESW","HWIC","copper",4,100,true,0);add("NIM-ES2-4","NIM","copper",4,1000,true,0);add("NM-ESW-161","NM","copper",16,100,true,0);
        add("NM-1E","NM","copper",1,10,false,0);add("NM-4E","NM","copper",4,10,false,0);add("NM-1FE-TX","NM","copper",1,100,false,0);add("NM-1FE-FX","NM","fiber",1,100,false,0);
        for(String name:List.of("NM-1E2W","NM-2E2W","NM-1FE2W","NM-2FE2W","NM-2W"))add(name,"NM","copper",name.equals("NM-2W")?0:name.startsWith("NM-2")?2:1,name.contains("FE")?100:10,false,2);
        add("WIC-1AM","WIC","phone",1,0,false,0);add("WIC-2AM","WIC","phone",2,0,false,0);add("NM-8AM","NM","phone",8,0,false,0);add("HWIC-AP-AG-B","HWIC","wireless",1,54,false,0);
        for(String kind:List.of("ROUTER","SWITCH")){
            add("PT-"+kind+"-NM-1CE","PT-"+kind,"copper",1,10,kind.equals("SWITCH"),0);
            add("PT-"+kind+"-NM-1CFE","PT-"+kind,"copper",1,100,kind.equals("SWITCH"),0);
            add("PT-"+kind+"-NM-1CGE","PT-"+kind,"copper",1,1000,kind.equals("SWITCH"),0);
            add("PT-"+kind+"-NM-1FFE","PT-"+kind,"fiber",1,100,kind.equals("SWITCH"),0);
            add("PT-"+kind+"-NM-1FGE","PT-"+kind,"fiber",1,1000,kind.equals("SWITCH"),0);
        }
        add("PT-ROUTER-NM-1S","PT-ROUTER","serial",1,0,false,0);add("PT-ROUTER-NM-1SS","PT-ROUTER","serial",1,0,false,0);add("PT-ROUTER-NM-1AM","PT-ROUTER","phone",1,0,false,0);add("PT-ROUTER-NM-COVER","PT-ROUTER","cover",0,0,false,0);
        add("GLC-T","SFP","copper",1,1000,false,0);add("GLC-TE","SFP","copper",1,1000,false,0);add("GLC-LH-SMD","SFP","fiber",1,1000,false,0);add("GLC-GE-100FX","SFP","fiber",1,100,false,0);
        add("AC-POWER-SUPPLY","POWER","power",0,0,false,0);add("POWER-COVER-PLATE","POWER","cover",0,0,false,0);
        add("WPC300N","PCMCIA","wireless",1,300,false,0);add("WMP300N","PCI","wireless",1,300,false,0);
        add("USB-RS232","USB","console",1,0,false,0);
        add("CISCO-AUTOMATION","APP","application",0,0,false,0);
        BUILTIN_MODULES=List.copyOf(CATALOG.keySet());
    }
    private static void add(String id,String format,String medium,int ports,int speed,boolean sw,int children){CATALOG.put(id,new Module(id,format,medium,ports,speed,sw,children));}
    public static List<String> profiles(String kind){return kind.equals("router")?List.of("NetLab modular router","Router-PT","1841","2811","ISR4331"):List.of("Switch-PT","2960-24TT","3560-24PS","2950-24","3650-24PS");}
    public static Hardware ensure(Device d){if(d.hardware==null){d.hardware=new Hardware();d.hardware.profile=d.kind.equals("traffic")?"Traffic Controller":d.kind.equals("pc")?"Laptop-PT":d.kind.equals("ap")?"AccessPoint-PT":d.kind.equals("router")?"NetLab modular router":"Switch-PT";}return d.hardware;}
    public static boolean powered(Device d){return d.hardware==null||d.hardware.powered&&(!d.hardware.energyRequired||d.hardware.energySupplied);}
    public static String powerProblem(Device d){
        if(d.hardware!=null&&d.hardware.profile.equals("3650-24PS")&&!d.hardware.modules.containsValue("AC-POWER-SUPPLY"))
            return "Установи AC-POWER-SUPPLY в слот power0 или power1.";
        if(d.hardware!=null&&d.hardware.energyRequired&&!d.hardware.energySupplied)return "Нет питания FE. Подключи задний разъём к OUT блока питания шкафа; его IN — к энергошкафу.";
        return "";
    }
    public static void initializeHost(Device d,boolean laptop){
        var h=ensure(d);h.profile=laptop?"Laptop-PT":"PC-PT";h.hostConsoleVersion=1;
        if(laptop)for(var p:d.ports)if(p.console){p.hidden=true;p.up=false;p.hardwareSlot="usb0";p.medium="console";}
    }
    /** Older placed laptops retain their built-in RS232 as an already installed adapter. */
    public static boolean migrateHostConsole(Device d){
        if(!d.kind.equals("pc"))return false;var h=ensure(d);if(h.hostConsoleVersion>=1)return false;
        if(h.profile.equals("Laptop-PT"))for(var p:d.ports)if(p.console){
            p.hardwareSlot="usb0";p.medium="console";if(!p.hidden)h.modules.putIfAbsent("usb0","USB-RS232");
        }
        h.hostConsoleVersion=1;return true;
    }
    public static Map<String,String> slots(Device d){
        var h=ensure(d);var out=new LinkedHashMap<String,String>();
        switch(h.profile){
            case "Laptop-PT"->{out.put("radio0","PCMCIA");out.put("usb0","USB");}
            case "PC-PT"->out.put("radio0","PCI");
            case "NetLab modular router"->{out.put("hwic0","HWIC");out.put("hwic1","HWIC");out.put("nm0","NM");out.put("nim0","NIM");out.put("nim1","NIM");out.put("pt0","PT-ROUTER");}
            case "Router-PT"->{for(int i=0;i<6;i++)out.put("pt"+i,"PT-ROUTER");}
            case "1841"->{out.put("hwic0","HWIC");out.put("hwic1","HWIC");}
            case "2811"->{for(int i=0;i<4;i++)out.put("hwic"+i,"HWIC");out.put("nm0","NM");}
            case "ISR4331"->{out.put("nim0","NIM");out.put("nim1","NIM");}
            case "Switch-PT"->{for(int i=0;i<6;i++)out.put("pt"+i,"PT-SWITCH");}
            case "3650-24PS"->{for(int i=0;i<4;i++)out.put("sfp"+i,"SFP");out.put("power0","POWER");out.put("power1","POWER");}
            default->{}
        }
        if(d.kind.equals("pc"))out.put("app0","APP");
        if(d.kind.equals("pc"))out.putAll(HOST_SLOTS);out.putAll(EXTRA_SLOTS.getOrDefault(h.profile,Map.of()));
        for(var entry:new ArrayList<>(h.modules.entrySet())){var m=CATALOG.get(entry.getValue());if(m==null)continue;
            for(int i=0;i<m.wicSlots;i++)out.put(entry.getKey()+"/wic"+i,"WIC");if(m.medium.equals("sfp"))out.put(entry.getKey()+"/sfp0","SFP");}
        return out;
    }
    public static boolean compatible(String slot,Module m){return slot.equals(m.format)||slot.equals("HWIC")&&m.format.equals("WIC");}
    public static void install(Device d,String slot,String module){
        var h=ensure(d);if(h.powered)throw new IllegalArgumentException("Сначала выключи устройство");
        var m=CATALOG.get(module);String format=slots(d).get(slot);
        if(m==null||format==null||!compatible(format,m))throw new IllegalArgumentException("Модуль несовместим со слотом");
        if(h.modules.containsKey(slot))throw new IllegalArgumentException("Слот занят");
        if(d.ports.size()+m.ports>120)throw new IllegalArgumentException("Достигнут лимит аппаратных индексов");
        if(m.format.equals("SFP")&&slot.contains("/")){
            String parent=slot.substring(0,slot.lastIndexOf('/'));var p=d.ports.stream().filter(port->port.hardwareSlot.equals(parent)&&!port.hidden).findFirst().orElseThrow();p.medium=m.medium;p.maxMbps=m.mbps;p.bandwidth=m.mbps*1000;
        }else for(int i=0;i<m.ports;i++){
            String family=m.medium.equals("serial")?"Serial":m.medium.equals("async")?"Async":m.medium.equals("phone")?"Modem":m.medium.equals("wireless")?"Dot11Radio":m.medium.equals("fronthaul")?"Fronthaul":m.mbps==10?"Ethernet":m.mbps==100?"FastEthernet":"GigabitEthernet";
            int index=slotNumber(slot);String name=m.medium.equals("cellular")?"Cellular0":m.medium.equals("fronthaul")?"Fronthaul0/"+i:m.medium.equals("console")?"RS232":m.format.equals("PCMCIA")||m.format.equals("PCI")?"Wireless0":family+"0/"+index+"/"+i;
            int found=-1;for(int j=0;j<d.ports.size();j++)if(d.portName(j).equals(name)&&d.ports.get(j).hidden){found=j;break;}
            var p=new Device.Port(d.kind.equals("switch")||m.switching||m.medium.equals("wireless"));p.name=name;p.hardwareSlot=slot;p.medium=m.medium;p.maxMbps=m.mbps;p.bandwidth=m.mbps>0?m.mbps*1000:64;p.l2=m.switching||d.kind.equals("switch")||m.id.equals("HWIC-AP-AG-B");
            if(m.medium.equals("console")){p.console=true;p.up=true;}
            if(m.medium.equals("cellular")||m.medium.equals("fronthaul"))p.up=true;
            if(found<0)d.ports.add(p);else d.ports.set(found,p);
        }
        h.modules.put(slot,module);
    }
    public static List<Integer> portsIn(Device d,String slot){var out=new ArrayList<Integer>();for(int i=0;i<d.ports.size();i++)if(d.ports.get(i).hardwareSlot.equals(slot)||d.ports.get(i).hardwareSlot.startsWith(slot+"/"))out.add(i);return out;}
    public static String remove(Device d,String slot){
        var h=ensure(d);if(h.powered)throw new IllegalArgumentException("Сначала выключи устройство");
        for(var guard:REMOVE_GUARDS)guard.accept(d,slot);
        if(h.modules.keySet().stream().anyMatch(k->k.startsWith(slot+"/")))throw new IllegalArgumentException("Сначала извлеки вложенные карты/SFP");
        String module=h.modules.remove(slot);if(module==null)throw new IllegalArgumentException("Слот пуст");
        if(slot.endsWith("/sfp0")){String parent=slot.substring(0,slot.lastIndexOf('/'));for(var p:d.ports)if(p.hardwareSlot.equals(parent)){p.medium="sfp";p.maxMbps=1000;}}
        else for(int i:portsIn(d,slot)){var p=d.ports.get(i);p.hidden=true;p.up=false;p.ip="";}
        if(d.kind.equals("pc")&&d.preferredPort<d.ports.size()&&d.ports.get(d.preferredPort).hidden){
            d.preferredPort=0;if(d.wireless!=null){d.gateway=d.wireless.wiredGateway;d.os().dns=new ArrayList<>(d.wireless.wiredDns);}
        }
        return module;
    }
    private static int slotNumber(String slot){
        String[] parts=slot.split("/");String root=parts[0];int number=Integer.parseInt(root.replaceAll("[^0-9]",""));
        int base=root.startsWith("hwic")?1:root.startsWith("nm")?10:root.startsWith("nim")?20:root.startsWith("pt")?30:50;
        return parts.length==1?base+number:(base+number)*10+Integer.parseInt(parts[1].replaceAll("[^0-9]",""))+1;
    }
    public static void changeProfile(Device d,String profile){
        var h=ensure(d);if(h.powered||!h.modules.isEmpty())throw new IllegalArgumentException("Выключи устройство и извлеки все модули");if(!profiles(d.kind).contains(profile))throw new IllegalArgumentException("Неизвестное шасси");
        if(d.ports.size()>80)throw new IllegalArgumentException("Недостаточно аппаратных индексов для смены шасси");
        for(var p:d.ports)if(!p.virtual&&!p.console){p.hidden=true;p.up=false;}
        h.profile=profile;
        if(d.kind.equals("router")){
            int count=profile.equals("Router-PT")?0:profile.equals("ISR4331")?3:2;
            for(int i=0;i<count;i++)builtin(d,(profile.equals("1841")||profile.equals("2811")?"FastEthernet":"GigabitEthernet")+(profile.equals("ISR4331")?"0/0/":"0/")+i,profile.equals("1841")||profile.equals("2811")?100:1000);
        }else if(profile.equals("Switch-PT")){for(int i=0;i<4;i++)builtin(d,"GigabitEthernet0/"+i,1000);}
        else {
            for(int i=1;i<=24;i++)builtin(d,(profile.equals("3650-24PS")?"GigabitEthernet1/0/":"FastEthernet0/")+i,profile.equals("3650-24PS")?1000:100);
            if(profile.equals("2960-24TT")||profile.equals("3560-24PS"))for(int i=1;i<=2;i++)builtin(d,"GigabitEthernet0/"+i,1000);
        }
    }
    private static void builtin(Device d,String name,int speed){
        var p=new Device.Port(d.kind.equals("switch"));p.name=name;p.maxMbps=speed;p.bandwidth=speed*1000;p.l2=d.kind.equals("switch");
        for(int i=0;i<d.ports.size();i++)if(d.portName(i).equals(name)){d.ports.set(i,p);return;}d.ports.add(p);
    }
}
