package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.media.core.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MediaConsole implements ConsoleExtensions.Handler {
    public static final String HELP="NetLab Media commands (службы NetLab, не Cisco IOS):\nshow media | show iptv | show sip | media panel\nConfig: media admin PASSWORD; media enabled on|off\nHeadend: iptv channel N NAME; iptv add N SECONDS URL; iptv remove N INDEX; iptv move N FROM TO; iptv start N; iptv stop N; iptv loop N on|off; iptv grant N UUID; iptv revoke N UUID; iptv gateway IP\nSTB: iptv server IP; iptv tune N\nPBX: sip user NUMBER PASSWORD; sip delete NUMBER\nPhone: sip server IP; sip account NUMBER PASSWORD\nIPv4/hostname/shutdown/route: обычные команды NetLab. end → write memory.";
    @Override public String open(ConsoleExtensions.Context c){return "NetLab Media / Console\nhelp — команды; media panel — графическое меню\n"+c.target().cli().prompt(c.target().device());}
    @Override public CompletableFuture<String> execute(ConsoleExtensions.Context c,String line){var target=c.target();var r=new MediaRef(target.entity(),target.id()==target.entity().slotId(1)?1:0,target.device());String result;
        try{if(!r.state().owns(c.player().getUUID()))throw new IllegalArgumentException("Устройство принадлежит другому игроку");String text=line.strip();if(text.equals("help")||text.equals("?"))result=HELP;else if(text.equals("media panel")){MediaService.open(c.player(),r);result="Панель открыта";}else if(Set.of("show media","show iptv","show sip").contains(text))result=MediaService.describe(r);else if(!text.startsWith("media ")&&!text.startsWith("iptv ")&&!text.startsWith("sip ")){result=target.cli().execute(r.device(),c.network(),r.id(),text);r.host().changed();}else{if(target.cli().mode<2)throw new IllegalArgumentException("Сначала enable → configure terminal");configure(r,text,c.player().getUUID(),r.level().getGameTime());result="OK / "+MemoryCards.status(r.device());}}catch(IllegalArgumentException error){result="% "+error.getMessage();}return CompletableFuture.completedFuture(result);
    }
    public static void configure(MediaRef r,String text,UUID actor,long now){var s=r.state();if(s.owner.isEmpty())s.owner=actor.toString();String[] a=text.split("\\s+",5);if(a.length<3)throw new IllegalArgumentException(HELP);String kind=r.device().kind;
        if(a[0].equals("iptv")&&Set.of("channel","add","remove","move","start","stop","loop","grant","revoke").contains(a[1])&&kind.equals(MediaHardware.HEADEND)){int num=Addressing.number(a[2],1,9999);var existing=s.channels.get(num);if(existing!=null&&!s.owns(actor)&&!existing.owner.equals(actor.toString())&&!existing.editors.contains(actor.toString()))throw new IllegalArgumentException("Канал занят другим игроком");}

        switch(a[0]+" "+a[1]){
            case "media admin"->{service(kind);arity(a,3);MediaState.password(a[2]);s.admin=ConfigStore.secret(a[2]);MediaService.revoke(r.device().identity);}
            case "media enabled"->{arity(a,3);s.enabled=on(a[2]);}
            case "iptv gateway"->{require(kind,MediaHardware.HEADEND);arity(a,3);Device.ip(a[2]);s.gateway=a[2];}
            case "iptv server"->{require(kind,MediaHardware.STB);arity(a,3);Device.ip(a[2]);s.server=a[2];}
            case "iptv tune"->{require(kind,MediaHardware.STB);arity(a,3);s.channel=Addressing.number(a[2],1,9999);}
            case "iptv channel"->{require(kind,MediaHardware.HEADEND);int number=Addressing.number(a[2],1,9999);String name=text.split("\\s+",4).length==4?text.split("\\s+",4)[3]:"";name=MediaPlaylist.clean(name,64);if(name.isBlank())throw new IllegalArgumentException("iptv channel N NAME");if(!s.channels.containsKey(number)&&s.channels.size()>=channelLimit(r.device()))throw new IllegalArgumentException("Нет свободных каналов; плата NL-MEDIA-VIDEO увеличивает лимит с 4 до 16");var ch=s.channels.computeIfAbsent(number,k->new MediaState.Channel());ch.name=name;if(ch.owner.isEmpty())ch.owner=actor.toString();}
            case "iptv grant"->{require(kind,MediaHardware.HEADEND);arity(a,4);channel(s,a[2]).editors.add(UUID.fromString(a[3]).toString());}
            case "iptv revoke"->{require(kind,MediaHardware.HEADEND);arity(a,4);channel(s,a[2]).editors.remove(UUID.fromString(a[3]).toString());}
            case "iptv add"->{require(kind,MediaHardware.HEADEND);arity(a,5);var ch=channel(s,a[2]);if(ch.playlist.size()>=64)throw new IllegalArgumentException("До 64 видео в канале");ch.playlist.add(new MediaPlaylist.Entry(a[4],"Видео "+(ch.playlist.size()+1),Addressing.number(a[3],1,86400)));}
            case "iptv move"->{require(kind,MediaHardware.HEADEND);arity(a,5);var ch=channel(s,a[2]);int from=Addressing.number(a[3],1,ch.playlist.size())-1,to=Addressing.number(a[4],1,ch.playlist.size())-1;var entry=ch.playlist.remove(from);ch.playlist.add(to,entry);ch.started=now;}
            case "iptv remove"->{require(kind,MediaHardware.HEADEND);arity(a,4);var ch=channel(s,a[2]);ch.playlist.remove(Addressing.number(a[3],1,ch.playlist.size())-1);ch.started=now;}
            case "iptv start"->{require(kind,MediaHardware.HEADEND);arity(a,3);var ch=channel(s,a[2]);if(ch.playlist.isEmpty())throw new IllegalArgumentException("Сначала добавь видео");ch.running=true;ch.started=now;}
            case "iptv stop"->{require(kind,MediaHardware.HEADEND);arity(a,3);channel(s,a[2]).running=false;}
            case "iptv loop"->{require(kind,MediaHardware.HEADEND);arity(a,4);channel(s,a[2]).loop=on(a[3]);}
            case "sip user"->{require(kind,MediaHardware.PBX);arity(a,4);MediaState.number(a[2]);MediaState.password(a[3]);if(!s.subscribers.containsKey(a[2])&&s.subscribers.size()>=64)throw new IllegalArgumentException("До 64 абонентов");s.subscribers.put(a[2],ConfigStore.secret(a[3]));MediaService.revokeNumber(r.device().identity,a[2]);}
            case "sip delete"->{require(kind,MediaHardware.PBX);arity(a,3);s.subscribers.remove(a[2]);MediaService.revokeNumber(r.device().identity,a[2]);}
            case "sip server"->{require(kind,MediaHardware.PHONE,"pc");arity(a,3);Device.ip(a[2]);s.server=a[2];}
            case "sip account"->{require(kind,MediaHardware.PHONE,"pc");arity(a,4);MediaState.number(a[2]);MediaState.password(a[3]);s.number=a[2];s.password=a[3];}
            default->throw new IllegalArgumentException(HELP);
        }r.changed(s);MediaLearning.configure(r,text,actor);
    }
    public static int channelLimit(Device d){return Hardware.ensure(d).modules.containsValue(MediaHardware.VIDEO)?16:4;}
    private static MediaState.Channel channel(MediaState s,String text){var c=s.channels.get(Addressing.number(text,1,9999));if(c==null)throw new IllegalArgumentException("Канал не создан");return c;}
    private static void service(String kind){require(kind,MediaHardware.HEADEND,MediaHardware.PBX);}
    private static void require(String kind,String... allowed){if(!List.of(allowed).contains(kind))throw new IllegalArgumentException("Команда для "+String.join(" / ",allowed));}
    private static boolean on(String v){if(!Set.of("on","off").contains(v))throw new IllegalArgumentException("on|off");return v.equals("on");}
    private static void arity(String[] a,int count){if(a.length!=count)throw new IllegalArgumentException("Неверные аргументы; help");}
    @Override public CommandGrammar.Completion complete(String p){var all=List.of("show media","show iptv","show sip","media panel","media admin ","media enabled ","iptv channel ","iptv add ","iptv remove ","iptv move ","iptv start ","iptv stop ","iptv loop ","iptv gateway ","iptv grant ","iptv revoke ","iptv server ","iptv tune ","sip user ","sip delete ","sip server ","sip account ");var choices=all.stream().filter(s->s.startsWith(p)).toList();return new CommandGrammar.Completion(choices.size()==1?choices.getFirst():p,choices);}
}
