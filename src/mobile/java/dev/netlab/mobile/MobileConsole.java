package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.api.ConsoleExtensions.Context;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;
import java.util.*;
import java.util.concurrent.*;

/** NetLab commands, not a claim to emulate a vendor's LTE management OS. */
public final class MobileConsole implements ConsoleExtensions.Handler {
    public static final String HELP="NetLab Mobile\nОбщие IOS-подобные: enable; configure terminal; interface gi0/0; ip address IP MASK; no shutdown; ip default-gateway IP; memory write.\nmobile panel — панель оборудования / SIM\nshow mobile — состояние; show subscribers — абоненты EPC\nВ configure terminal:\nEPC: mobile password ПАРОЛЬ; mobile operator PLMN НАЗВАНИЕ; mobile pool ШЛЮЗ/24; mobile apn ИМЯ\nBBU / станок: mobile core IP\nСтанок: mobile pair ПАРОЛЬ; mobile issue [ИМЯ] (также в enable)\nEPC: mobile revoke UUID\nRRU: mobile radio on|off; mobile band 3|20; mobile channel N; mobile azimuth auto|0..359; mobile tilt 0..20; mobile range 64..1024\nmobile keep-loaded on|off — ограниченная загрузка инфраструктуры.\nSIM: пустую карту вставь в панели станка, выпусти и забери активную. Рецептов пока нет.";
    private MobileRef ref(Context c){var t=c.target();var r=MobileRef.of(t.entity(),t.id()==t.entity().slotId(1)?1:0);if(r==null||!r.state().owns(c.player().getUUID()))throw new IllegalArgumentException("Устройство принадлежит другому игроку");return r;}
    @Override public String open(Context c){var r=ref(c);return "NetLab Mobile / учебная мобильная сеть\nhelp — команды; mobile panel — оборудование\n"+MemoryCards.status(r.device())+"\n"+c.target().cli().prompt(r.device());}
    private static CompletableFuture<String> done(String text){return CompletableFuture.completedFuture(text);}
    private static void kind(MobileRef r,String expected){if(!r.device().kind.equals(expected))throw new IllegalArgumentException("Команда для "+expected);}
    @Override public CompletableFuture<String> execute(Context c,String line){
        try{var r=ref(c);var s=r.state();var cli=c.target().cli();String text=line.strip();var a=text.split("\\s+");
            if(text.equals("help")||text.equals("?"))return done(HELP);
            if(text.equals("mobile panel")){MobilePackets.open(c.player(),r.host(),r.bay());return done("Панель открыта");}
            if(text.equals("show mobile"))return done("Оператор: "+s.operatorName+" / PLMN "+s.plmn+"\nIP: "+r.ip()+" / gateway "+r.device().gateway+"\nEPC: "+s.coreAddress+" / APN "+s.apn+"\nПул: "+s.subscriberGateway+"/24\nRadio: "+s.radioEnabled+" band "+s.band+" channel "+s.channel+" azimuth "+s.azimuth+" tilt "+s.tilt+" range "+s.range+"\n"+MemoryCards.status(r.device()));
            if(text.equals("show subscribers")){kind(r,MobileHardware.CORE);var out=new StringBuilder("UUID | имя | активна | IP\n");for(var sub:s.subscribers.entries.values())out.append(sub.id).append(" | ").append(sub.label).append(" | ").append(sub.active).append(" | ").append(sub.ip).append('\n');return done(out.toString());}
            if(!text.startsWith("mobile ")){
                var resolved=CommandGrammar.resolve(cli,r.device(),line);
                if(resolved.value().startsWith("ip address ")){if(cli.selected!=0||!r.device().ports.getFirst().medium.equals("copper"))throw new IllegalArgumentException("IP настраивается только на Ethernet; радиотракт и пул — mobile-командами");var words=resolved.value().split("\\s+");long inverse=(~Device.mask(words[3]))&0xffffffffL;if((inverse&(inverse+1))!=0)throw new IllegalArgumentException("Маска должна быть непрерывной");for(var e:c.network().devices.entrySet())for(int p=0;p<e.getValue().ports.size();p++)if(!(e.getKey()==r.id()&&p==0)&&e.getValue().ports.get(p).ip.equals(words[2]))throw new IllegalArgumentException("IP уже занят");}
                String out=cli.execute(r.device(),c.network(),r.id(),line);r.host().changed();return done(out);
            }
            if(cli.mode<1)throw new IllegalArgumentException("Сначала enable");
            if(text.equals("mobile issue")){kind(r,MobileHardware.STATION);return MobileProvisioner.issue(r,"").handle((v,e)->e==null?v:"% "+MobileService.reason(e));}
            if(a.length<3)throw new IllegalArgumentException("help — синтаксис команд");
            if(a[1].equals("pair")){kind(r,MobileHardware.STATION);return MobileProvisioner.pair(r,text.substring("mobile pair ".length())).handle((v,e)->e==null?v:"% "+MobileService.reason(e));}
            if(a[1].equals("issue")){kind(r,MobileHardware.STATION);return MobileProvisioner.issue(r,text.substring("mobile issue ".length())).handle((v,e)->e==null?v:"% "+MobileService.reason(e));}
            if(cli.mode!=2)throw new IllegalArgumentException("Сначала configure terminal");
            switch(a[1]){
                case "password"->{kind(r,MobileHardware.CORE);s.adminVerifier=MobileAuth.credential(text.substring("mobile password ".length()));}
                case "operator"->{kind(r,MobileHardware.CORE);if(!a[2].matches("[0-9]{5,6}")||a.length<4)throw new IllegalArgumentException("mobile operator 00101 Имя оператора");if(!s.subscribers.entries.isEmpty())throw new IllegalArgumentException("Смена PLMN после выпуска SIM требует нового ядра");s.plmn=a[2];s.operatorName=DeviceEntity.cleanName(text.substring(text.indexOf(a[2])+a[2].length()).strip());}
                case "core"->{Device.ip(a[2]);if(!Set.of(MobileHardware.BBU,MobileHardware.STATION).contains(r.device().kind))throw new IllegalArgumentException("Нужен BBU или SIM-станок");s.coreAddress=a[2];}
                case "pool"->{kind(r,MobileHardware.CORE);Device.ip(a[2]);long host=Device.ip(a[2])&255;if(host==0||host==255||!s.subscribers.entries.isEmpty())throw new IllegalArgumentException("Свободный /24, шлюз .1..254; пул меняется до выпуска SIM");s.subscriberGateway=a[2];r.device().ports.get(MobileHardware.cellular(r.device())).ip=a[2];}
                case "apn"->{kind(r,MobileHardware.CORE);if(!a[2].matches("[a-z0-9.-]{1,48}"))throw new IllegalArgumentException("APN: a-z, 0-9, точка, дефис");s.apn=a[2];}
                case "revoke"->{kind(r,MobileHardware.CORE);var sub=s.subscribers.entries.get(a[2]);if(sub==null)throw new IllegalArgumentException("SIM не найдена");sub.active=false;}
                case "radio"->{kind(r,MobileHardware.RRU);s.radioEnabled=on(a[2]);}
                case "band"->{kind(r,MobileHardware.RRU);int band=Integer.parseInt(a[2]);if(band!=3&&band!=20)throw new IllegalArgumentException("band 3|20");s.band=band;}
                case "channel"->{kind(r,MobileHardware.RRU);s.channel=Addressing.number(a[2],0,65535);}
                case "azimuth"->{kind(r,MobileHardware.RRU);s.azimuth=a[2].equals("auto")?-1:Addressing.number(a[2],0,359);}
                case "tilt"->{kind(r,MobileHardware.RRU);s.tilt=Addressing.number(a[2],0,20);}
                case "range"->{kind(r,MobileHardware.RRU);s.range=Addressing.number(a[2],64,1024);}
                case "keep-loaded"->s.keepLoaded=on(a[2]);
                default->throw new IllegalArgumentException("Неизвестная mobile-команда; help");
            }r.changed(s);MobileChunks.register(r.level(),r.host().getBlockPos());return done("OK; "+MemoryCards.status(r.device()));
        }catch(IllegalArgumentException e){return done("% "+e.getMessage());}
    }
    private static boolean on(String value){if(!Set.of("on","off").contains(value))throw new IllegalArgumentException("on|off");return value.equals("on");}
    @Override public CommandGrammar.Completion complete(String prefix){var choices=List.of("enable","configure terminal","interface GigabitEthernet0/0","ip address ","ip default-gateway ","ip route ","no shutdown","exit","end","show mobile","show subscribers","show ip interface brief","mobile panel","mobile core ","mobile password ","mobile operator ","mobile pool ","mobile apn ","mobile pair ","mobile issue ","mobile revoke ","mobile radio ","mobile band ","mobile channel ","mobile azimuth ","mobile tilt ","mobile range ","mobile keep-loaded ");var matching=choices.stream().filter(v->v.startsWith(prefix)).toList();return new CommandGrammar.Completion(matching.size()==1?matching.getFirst():prefix,matching);}
}
