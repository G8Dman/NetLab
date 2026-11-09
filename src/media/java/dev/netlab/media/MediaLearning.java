package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.media.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import java.util.*;
/** Learning observations never take the place of SIP, IPTV, permissions or decoding. */
public final class MediaLearning {
    private record Route(boolean routed,boolean satellite){}
    private static final Map<String,Route> ROUTES=new HashMap<>();
    public static void route(MediaRef a,MediaRef b,dev.netlab.api.ApplicationTransport.Receipt receipt){var net=a.host().network();boolean routed=receipt.path().stream().map(net.devices::get).filter(Objects::nonNull).anyMatch(d->dev.netlab.core.DeviceKinds.router(d.kind));boolean different=!a.ip().isBlank()&&!b.ip().isBlank()&&!dev.netlab.core.Device.same(a.ip(),b.ip(),a.device().ports.getFirst().mask);boolean satellite=receipt.path().stream().map(net.devices::get).filter(Objects::nonNull).anyMatch(d->d.kind.startsWith("sputnik_"));if(ROUTES.size()>512)ROUTES.clear();ROUTES.put(a.device().identity+":"+b.device().identity,new Route(routed&&different,satellite));}
    private record Voice(UUID from,UUID to,MediaRef a,MediaRef b,boolean speaker){}
    private static final Set<String> FAILED=new HashSet<>();
    public static void failed(MediaRef a,MediaRef b,Throwable error){String text=String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);if(text.contains("route")||text.contains("acl")){if(FAILED.size()>512)FAILED.clear();FAILED.add(a.device().identity+":"+b.device().identity);}}
    private static final Map<UUID,Set<String>> PROVEN=new HashMap<>();
    private static final Map<UUID,Map<UUID,Integer>> FRAMES=new HashMap<>();
    private static final Map<UUID,Map<UUID,Voice>> VOICE=new HashMap<>();
    private record Screen(UUID player,long pos){}
    private record Frame(int channel,String server,double position,long tick){}
    private static final Map<Screen,Frame> FRAMES_SEEN=new HashMap<>();
    private record Audio(UUID source,BlockPos tv,long time){}
    private static final Map<UUID,List<Audio>> AUDIO=new HashMap<>();
    public static void hint(ServerPlayer p,LearningEvents.Hint h){
        if(h.kind().equals("media.audio")){if(MediaService.watching(p,h.pos())==null||!dev.netlab.sputnik.BrowserLearning.received(p,h.pos()))return;UUID source;try{source=UUID.fromString(h.value());}catch(IllegalArgumentException e){return;}var entries=AUDIO.computeIfAbsent(p.getUUID(),k->new ArrayList<>());entries.removeIf(a->a.source.equals(source)||p.serverLevel().getGameTime()-a.time>60);if(entries.size()<4)entries.add(new Audio(source,h.pos(),p.serverLevel().getGameTime()));return;}
        if(!h.kind().equals("audio.played"))return;for(var a:AUDIO.getOrDefault(p.getUUID(),List.of())){if(!a.source.toString().equals(h.value())||p.serverLevel().getGameTime()-a.time>60)continue;var w=MediaService.watching(p,a.tv);if(w==null||w.tv().state().speakers||p.distanceToSqr(h.pos().getCenter())>=32*32)continue;var outputs=MediaAudio.outputs(w.tv());if(outputs.stream().noneMatch(o->o.get("pos").getAsLong()==h.pos().asLong()))continue;LearningEvents.emit(p,"media.audio",w.tv().host(),w.tv().bay(),"speakers",outputs.size());}
    }
    public static void emit(MediaRef r,String kind,Object... data){try{LearningEvents.owner(r.level(),UUID.fromString(r.state().owner),kind,r.host(),r.bay(),data);}catch(IllegalArgumentException ignored){}}
    public static void configure(MediaRef r,String command,UUID actor){String[] words=command.split("\\s+");if(words.length<2)return;String key=words[0]+" "+words[1];String event=switch(key){case "iptv channel"->"media.channel.create";case "iptv add"->"media.playlist.add";case "iptv move"->"media.playlist.move";case "iptv start"->"media.channel.start";case "sip user"->"voice.subscriber";default->"";};if(!event.isEmpty())LearningEvents.owner(r.level(),actor,event,r.host(),r.bay(),"subscribers",r.state().subscribers.size());
        if(words[0].equals("iptv")&&words.length>=3){try{var c=r.state().channels.get(Integer.parseInt(words[2]));if(c!=null&&!c.owner.equals(actor.toString())&&c.editors.contains(actor.toString())){LearningEvents.owner(r.level(),UUID.fromString(c.owner),"media.editor",r.host(),r.bay());LearningEvents.owner(r.level(),actor,"media.editor",r.host(),r.bay());}}catch(IllegalArgumentException ignored){}}
    }
    public static void voice(UUID call,UUID from,ServerPlayer to,MediaRef a,MediaRef b,boolean speaker){
        var counts=FRAMES.computeIfAbsent(call,k->new HashMap<>());counts.merge(from,1,(x,y)->Math.min(50,x+y));VOICE.computeIfAbsent(call,k->new HashMap<>()).put(from,new Voice(from,to.getUUID(),a,b,speaker));
        if(counts.getOrDefault(from,0)<20||counts.getOrDefault(to.getUUID(),0)<20)return;
        for(var v:VOICE.get(call).values()){if(!PROVEN.computeIfAbsent(call,k->new HashSet<>()).add(v.from+":"+v.speaker))continue;var network=v.a.host().network();var path=network.routePath(v.a.id(),v.b.ip()).path();boolean routed=path.stream().map(network.devices::get).filter(Objects::nonNull).anyMatch(d->d.kind.equals("router")),sat=path.stream().map(network.devices::get).filter(Objects::nonNull).anyMatch(d->d.kind.startsWith("sputnik_"));int pockets=(v.a.host() instanceof PortableHosts.Host?1:0)+(v.b.host() instanceof PortableHosts.Host?1:0);
            var actual=ROUTES.getOrDefault(v.a.device().identity+":"+v.b.device().identity,new Route(false,false));LearningEvents.owner(a.level(),v.from,"voice.bidirectional",v.a.host(),v.a.bay(),"routed",actual.routed,"satellite",actual.satellite,"pockets",pockets,"speaker",v.speaker,"repaired",FAILED.contains(v.a.device().identity+":"+v.b.device().identity)||FAILED.contains(v.b.device().identity+":"+v.a.device().identity));
        }
    }
    public static void end(UUID call){FRAMES.remove(call);VOICE.remove(call);PROVEN.remove(call);}
    public static void frame(ServerPlayer p,BlockPos pos,String json){
        var w=MediaService.watching(p,pos);if(w==null||!dev.netlab.sputnik.BrowserLearning.received(p,pos))return;
        double seconds;try{seconds=Double.parseDouble(json);}catch(NumberFormatException e){return;}if(!Double.isFinite(seconds))return;
        var ch=w.server().state().channels.get(w.channel());var at=MediaPlaylist.at(ch.playlist,ch.started,p.serverLevel().getGameTime(),ch.loop);if(at==null||Math.abs(at.seconds()-seconds)>4)return;
        var key=new Screen(p.getUUID(),pos.asLong());long now=p.serverLevel().getGameTime();boolean synced=FRAMES_SEEN.entrySet().stream().anyMatch(e->e.getKey().pos!=key.pos&&e.getValue().channel==w.channel()&&e.getValue().server.equals(w.server().device().identity)&&now-e.getValue().tick<=60&&Math.abs(e.getValue().position-seconds)<4);
        var previous=FRAMES_SEEN.put(key,new Frame(w.channel(),w.server().device().identity,seconds,now));
        LearningEvents.emit(p,"media.frame",w.tv().host(),w.tv().bay(),"synchronized",synced,"lateJoin",previous==null&&synced&&seconds>5);
    }
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(e.getServer().getTickCount()%100!=0||LearningEvents.EVENTS.isEmpty())return;
        for(var level:e.getServer().getAllLevels())for(var r:MediaRef.loaded(level))if(r.live()){
            String kind=r.device().kind;boolean network=r.device().ports.stream().anyMatch(p->p.up&&!p.console&&!p.ip.isBlank());boolean card=MemoryCards.present(r.device())&&MemoryCards.card(r.device()).active;
            if(kind.equals(MediaHardware.HEADEND)&&card&&network)emit(r,"media.headend.ready");if(kind.equals(MediaHardware.HEADEND)&&card&&network&&!r.state().admin.isBlank()&&!r.device().os().startup.isBlank())emit(r,"media.admin.ready");
            if(kind.equals(MediaHardware.PBX)&&card&&network){emit(r,"voice.pbx.ready");if(Hardware.ensure(r.device()).modules.containsValue(MediaHardware.SESSIONS))emit(r,"voice.capacity");}
            if(kind.equals(MediaHardware.PHONE)&&network)emit(r,"voice.phone.ready");
            if(kind.equals(MediaHardware.TV)&&MediaService.peer(r,"HDMIIn","HDMIOut")!=null)emit(r,"media.hdmi");
        }
        FRAMES_SEEN.entrySet().removeIf(x->e.getServer().getPlayerList().getPlayer(x.getKey().player)==null);
        var active=MediaService.CALLS.all().stream().map(CallBook.Call::id).collect(java.util.stream.Collectors.toSet());FRAMES.keySet().retainAll(active);VOICE.keySet().retainAll(active);PROVEN.keySet().retainAll(active);
    }
    public static void clear(){FRAMES.clear();VOICE.clear();PROVEN.clear();FRAMES_SEEN.clear();AUDIO.clear();FAILED.clear();ROUTES.clear();}
    private MediaLearning(){}
}
