package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public final class SputnikConsole implements ConsoleExtensions.Handler {
    public static final String HELP="show sputnik | show subscribers | sat panel\nConfig: sat network NAME; sat satellite GEO-1|GEO-2|GEO-3; sat azimuth 0..359; sat elevation 5..85; sat polarization H|V; sat frequencies TX_MHZ RX_MHZ\nHUB: sat subscriber MODEM_UUID KEY; sat revoke MODEM_UUID; sat rates DOWN_MBIT UP_MBIT\nModem: sat key KEY; sat address IP MASK; sat rates DOWN_MBIT UP_MBIT\nGateway: sat internet on|off; sat hub HUB_LAN_IP\nAll: sat enabled on|off; sat keep-loaded on|off. write memory сохраняет на карте. Обычные IPv4, route, shutdown — команды NetLab.";
    private static CompletableFuture<String> done(String s){return CompletableFuture.completedFuture(s);}
    @Override public String open(ConsoleExtensions.Context c){return "NetLab Sputnik / VSAT Console\nhelp — команды; show sputnik — состояние\n"+c.target().cli().prompt(c.target().device());}
    @Override public CompletableFuture<String> execute(ConsoleExtensions.Context c,String line){var r=new SputnikRef(c.target().entity(),c.target().id()==c.target().entity().slotId(1)?1:0,c.target().device());if(!r.state().owns(c.player().getUUID()))return done("% Устройство принадлежит другому игроку");try{String text=line.strip();var s=r.state();if(s.owner.isEmpty()){s.owner=c.player().getUUID().toString();r.changed(s);}var cli=c.target().cli();if(text.equals("help")||text.equals("?"))return done(HELP);if(text.equals("show sputnik"))return done("ID: "+r.device().identity+"\n"+SatelliteLinks.status(r)+"\nСеть "+s.network+" / "+s.satellite+" / "+s.polarization+" / TX "+s.txMhz+" RX "+s.rxMhz+" MHz\n"+s.download+" / "+s.upload+" Мбит/с\n"+MemoryCards.status(r.device()));if(text.equals("show subscribers")){kind(r,SputnikHardware.HUB);return done(String.join("\n",s.subscribers.keySet()));}if(text.equals("sat panel")){SputnikPackets.panel(c.player(),r,true,"");return done("Панель открыта");}
        if(!text.startsWith("sat ")){String result=cli.execute(r.device(),c.network(),r.id(),text);r.host().changed();return done(result);}if(cli.mode<2)throw new IllegalArgumentException("Сначала enable → configure terminal");var a=text.split("\\s+");if(a.length<3)throw new IllegalArgumentException(HELP);if(!Set.of("frequencies","rates","subscriber","address").contains(a[1])&&a.length!=3)throw new IllegalArgumentException("Лишние аргументы; help");
        switch(a[1]){
            case "network"->{if(!a[2].matches("[A-Za-z0-9_.-]{1,32}"))throw new IllegalArgumentException("Имя до 32 символов");s.network=a[2];}
            case "satellite"->{SatelliteModel.find(a[2]);s.satellite=a[2].toUpperCase(Locale.ROOT);}
            case "azimuth"->s.azimuth=Addressing.number(a[2],0,359);
            case "elevation"->s.elevation=Addressing.number(a[2],5,85);
            case "polarization"->{if(!Set.of("H","V").contains(a[2].toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("H|V");s.polarization=a[2].toUpperCase(Locale.ROOT);}
            case "frequencies"->{if(a.length!=4)throw new IllegalArgumentException("sat frequencies TX RX");int tx=Addressing.number(a[2],13750,14500),rx=Addressing.number(a[3],10700,12750);s.txMhz=tx;s.rxMhz=rx;}
            case "rates"->{if(a.length!=4)throw new IllegalArgumentException("sat rates DOWN UP");s.download=Addressing.number(a[2],1,100);s.upload=Addressing.number(a[3],1,20);}
            case "subscriber"->{kind(r,SputnikHardware.HUB);if(a.length!=4||a[3].length()<8||a[3].length()>64)throw new IllegalArgumentException("sat subscriber UUID KEY (8..64)");String id=UUID.fromString(a[2]).toString();if(s.subscribers.size()>=32&&!s.subscribers.containsKey(id))throw new IllegalArgumentException("До 32 модемов на HUB");s.subscribers.put(id,ConfigStore.secret(a[3]));}
            case "revoke"->{kind(r,SputnikHardware.HUB);s.subscribers.remove(UUID.fromString(a[2]).toString());}
            case "key"->{kind(r,SputnikHardware.MODEM);if(a[2].length()<8||a[2].length()>64)throw new IllegalArgumentException("Ключ: 8..64 символа");s.key=a[2];}
            case "address"->{kind(r,SputnikHardware.MODEM);if(a.length!=4)throw new IllegalArgumentException("sat address IP MASK");Device.ip(a[2]);long mask=Device.mask(a[3]),inverse=(~mask)&0xffffffffL;if((inverse&(inverse+1))!=0)throw new IllegalArgumentException("Непрерывная маска");r.device().ports.get(4).ip=a[2];r.device().ports.get(4).mask=a[3];}
            case "internet"->{kind(r,SputnikHardware.GATEWAY);s.internet=on(a[2]);}
            case "hub"->{kind(r,SputnikHardware.GATEWAY);Device.ip(a[2]);s.hubAddress=a[2];}
            case "enabled"->s.enabled=on(a[2]);
            case "keep-loaded"->s.keepLoaded=on(a[2]);
            default->throw new IllegalArgumentException(HELP);
        }r.changed(s);SputnikChunks.register(r.level(),r.host().getBlockPos());SatelliteLinks.clear();return done("OK / "+MemoryCards.status(r.device()));
    }catch(IllegalArgumentException e){return done("% "+e.getMessage());}}
    private static boolean on(String v){if(!Set.of("on","off").contains(v))throw new IllegalArgumentException("on|off");return v.equals("on");}
    private static void kind(SputnikRef r,String k){if(!r.device().kind.equals(k))throw new IllegalArgumentException("Команда для "+k);}
    @Override public CommandGrammar.Completion complete(String p){var all=List.of("show sputnik","show subscribers","sat panel","sat network ","sat satellite ","sat azimuth ","sat elevation ","sat frequencies ","sat polarization ","sat subscriber ","sat revoke ","sat key ","sat address ","sat rates ","sat internet ","sat hub ","sat enabled ","sat keep-loaded ");var choices=all.stream().filter(v->v.startsWith(p)).toList();return new CommandGrammar.Completion(choices.size()==1?choices.getFirst():p,choices);}
}
