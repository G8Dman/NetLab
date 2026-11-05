package dev.netlab.client;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import dev.netlab.core.*;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.*;

/** Add-ons supply assets/<namespace>/netlab_guide/*.json, no reverse Java dependency. */
public final class GuideBookContents {
    public record Article(String id,String title,String body){}
    public record Section(String id,String title,int order,List<Article> articles){}
    public static List<Section> load(ResourceManager resources){
        var sections=new LinkedHashMap<String,Section>();
        resources.listResources("netlab_guide",id->id.getPath().endsWith(".json")).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            try(var reader=entry.getValue().openAsReader()){
                var json=JsonParser.parseReader(reader).getAsJsonObject();String id=json.get("id").getAsString();var articles=new ArrayList<Article>();
                for(var element:json.getAsJsonArray("articles")){var article=element.getAsJsonObject();articles.add(new Article(id+"/"+article.get("id").getAsString(),article.get("title").getAsString(),article.get("body").getAsString()));}
                if(articles.isEmpty())throw new IllegalArgumentException("Empty guide section");
                sections.put(id,new Section(id,json.get("title").getAsString(),json.get("order").getAsInt(),List.copyOf(articles)));
            }catch(Exception e){LogUtils.getLogger().warn("Cannot load NetLab guide section {}",entry.getKey(),e);}
        });
        var blocks=sections.get("netlab:blocks");if(blocks!=null){var articles=new ArrayList<>(blocks.articles());var body=new StringBuilder("Совместимость определяется форматом слота и профилем устройства. Устанавливай при выключенном питании.\n\n");
            for(var m:Hardware.CATALOG.values())body.append(m.id()).append("\nФормат: ").append(m.format()).append("; среда: ").append(m.medium()).append("; портов: ").append(m.ports()).append(m.mbps()>0?"; Мбит/с: "+m.mbps():"").append(m.wicSlots()>0?"; вложенных WIC: "+m.wicSlots():"").append("\n\n");
            articles.add(new Article("netlab:blocks/module-reference","Справочник модулей",body.toString()));sections.put(blocks.id(),new Section(blocks.id(),blocks.title(),blocks.order(),List.copyOf(articles)));
        }
        var commands=sections.get("netlab:commands");if(commands!=null){var articles=new ArrayList<>(commands.articles());
            addReference(articles,"router","Маршрутизатор");addReference(articles,"switch","Коммутатор");addReference(articles,"pc","ПК и ноутбук");
            articles.add(new Article("netlab:commands/cisco-audit","Документация Cisco и аудит команд",
                "NetLab — независимая учебная модель, а не IOS. Справочник автоматически охватывает все команды текущего дерева CLI, включая do и no, и объясняет реализованное поведение. Проверка синтаксиса и выполнения без исключения не доказывает полную совместимость с Cisco.\n\n"
                +"Функциональные тесты проверяют отдельные сетевые сценарии, изменение состояния и отказы. Реальные IOS, оборудование и игровой интерфейс этими тестами не запускались.\n\n"
                +"Ограничения: STP/CDP/маршрутизация сходятся аналитически; нет полного обмена BPDU/LSA/DUAL, аппаратных коллизий и настоящих SSH-сеансов. Внешний веб-доступ добавляет отдельный NetLab Sputnik. FTP, SNMP и таблица TCP-сессий ПК пока отсутствуют.\n\n"
                +"Официальные источники для сопоставления синтаксиса и назначения:\n"
                +"Packet Tracer, ПК и Terminal:\nhttps://tutorials.ptnetacad.net/help/default/config_PCs.htm\n\n"
                +"Cisco IOS, статическая маршрутизация и passive-interface:\nhttps://www.cisco.com/c/en/us/td/docs/ios/iproute_pi/command/reference/iri_book/iri_pi1.html\n\n"
                +"Cisco IOS LAN Switching, MAC и SPAN:\nhttps://www.cisco.com/c/en/us/td/docs/ios/lanswitch/command/reference/lsw_book/lsw_m1.html\n\n"
                +"Cisco IOS LAN Switching, VLAN/STP:\nhttps://www.cisco.com/c/en/us/td/docs/ios-xml/ios/lanswitch/command/lsw-cr-book/lsw-s2.html\n\n"
                +"Cisco IOS DHCP:\nhttps://www.cisco.com/c/en/us/td/docs/ios-xml/ios/ipaddr_dhcp/configuration/12-4/dhcp-12-4-book/config-dhcp-server.html\n\n"
                +"Cisco IOS EIGRP:\nhttps://www.cisco.com/c/en/us/td/docs/ios-xml/ios/iproute_eigrp/command/ire-cr-book/ire-a1.html\n\n"
                +"В разных моделях и версиях Cisco доступность, значения по умолчанию и параметры отличаются. Источники служат ориентиром для поддержанного подмножества; это не сертификация полного соответствия."));
            sections.put(commands.id(),new Section(commands.id(),commands.title(),commands.order(),List.copyOf(articles)));
        }
        return sections.values().stream().sorted(Comparator.comparingInt(Section::order).thenComparing(Section::id)).toList();
    }
    private static void addReference(List<Article> articles,String kind,String label){
        var d=new Device(kind);var cli=new dev.netlab.core.Console();cli.selected=0;
        int[] modes=kind.equals("pc")?new int[]{0}:kind.equals("switch")?new int[]{0,1,2,3,4,8}:new int[]{0,1,2,3,4,5,6,7};
        String[] names={"EXEC (>)","Привилегированный EXEC (#)","Глобальная настройка (config)","Интерфейс (config-if)","Линия (config-line)","Маршрутизация (config-router)","DHCP-пул (dhcp-config)","Именованный ACL (config-std-nacl)","VLAN (config-vlan)"};
        for(int mode:modes){cli.mode=mode;if(mode==5)continue;
            addCommands(articles,d,cli,kind+"-"+mode,label+": "+(kind.equals("pc")?"Command Prompt":names[mode]));
        }
        if(kind.equals("router"))for(String protocol:List.of("rip","ospf","eigrp")){
            cli.mode=5;cli.context=protocol;d.os().routing.put(protocol,new RouterState.Routing(protocol,"1"));
            addCommands(articles,d,cli,kind+"-"+protocol,"Маршрутизатор: "+protocol.toUpperCase(Locale.ROOT));
        }
    }
    private static void addCommands(List<Article> articles,Device device,dev.netlab.core.Console cli,String id,String title){
        var body=new StringBuilder("У каждой команды ниже есть отдельная статья: назначение, синтаксис, пример и проверка результата. Используй поиск по имени команды.\n\n");
        var seen=new HashSet<String>();
        for(var spec:CommandGrammar.reference(cli,device).stream().sorted(Comparator.comparing(CommandGrammar.Spec::words)).toList()){
            if(!seen.add(spec.words()))continue;
            String syntax=spec.words()+(spec.usage().isBlank()?"":" "+spec.usage());body.append(syntax).append('\n');
            var entry=CommandManual.entry(spec.words());if(entry==null)throw new IllegalStateException("Missing command explanation: "+syntax);
            String args=CommandManual.arguments(spec,cli,device),example=spec.words()+(args.isBlank()?"":" "+args);
            String text="Устройство и режим: "+title+"\n\nНазначение:\n"+entry.purpose()+"\n\nСинтаксис:\n"+syntax+"\n\nПример:\n"+example+"\n\nПроверка результата:\n"+CommandManual.verification(spec.words())+"\n\nОграничения NetLab:\n"+(entry.limits().isBlank()?"Реализован указанный учебный вариант команды. Полный набор параметров и побочных эффектов Cisco IOS не гарантируется.":entry.limits())+"\n\nПараметры IP, VLAN, имена портов и ACL замени своими. Пример требует подходящей топологии и предыдущей настройки. Официальные источники и границы проверки — статья «Документация Cisco и аудит команд».";
            articles.add(new Article("netlab:commands/cmd-"+id+"-"+spec.words().replace(' ','_'),spec.words()+" — "+title,text));
        }
        articles.add(new Article("netlab:commands/reference-"+id,title,body.toString()));
    }
}
