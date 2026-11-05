package dev.netlab.sputnik;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Authenticated virtual radio bearer backed by actual coax/RF connections and a shared GEO queue. */
public final class SatelliteLinks {
    public record Pair(SputnikRef modem,SputnikRef terminal,SputnikRef hub,SputnikRef odu,SputnikRef dish,double quality,String fingerprint){}
    private record Cache(long tick,List<Pair> pairs){}
    private static final Map<ServerLevel,Cache> CACHE=new WeakHashMap<>();
    public static SputnikRef peer(SputnikRef r,int port,String medium){
        var p=r.host().links.get(r.bay()*128+port);if(p==null)return null;var l=r.level();var q=BlockPos.of(p.pos());if(!l.hasChunkAt(q)||!(l.getBlockEntity(q) instanceof DeviceEntity h))return null;var b=h.links.get(p.port());var target=SputnikRef.of(h,p.port()/128);
        if(target==null||b==null||b.pos()!=r.host().getBlockPos().asLong()||b.port()!=r.bay()*128+port||!r.live()||!target.live()||p.port()%128>=target.device().ports.size())return null;
        var a=r.device().ports.get(port);var remote=target.device().ports.get(p.port()%128);return a.up&&remote.up&&a.medium.equals(medium)&&remote.medium.equals(medium)&&CableKind.compatible(p.cable(),r.device(),port,target.device(),p.port()%128)?target:null;
    }
    private static double length(SputnikRef r,int port){var p=r.host().links.get(r.bay()*128+port);var other=SputnikRef.of((DeviceEntity)r.level().getBlockEntity(BlockPos.of(p.pos())),p.port()/128);return WallCablePath.length(CableGeometry.route(r.host(),r.bay()*128+port,other.host(),p.port(),p.anchors()));}
    private static SatelliteModel.Reception reception(SputnikRef dish,double cable){var s=dish.state();var satellite=SatelliteModel.find(s.satellite);double az=Math.toRadians(s.azimuth),el=Math.toRadians(s.elevation);var direction=new Vec3(-Math.sin(az)*Math.cos(el),Math.sin(el),Math.cos(az)*Math.cos(el));var origin=dish.host().getBlockPos().getCenter().add(0,1,0);boolean blocked=!dish.level().dimensionType().hasSkyLight();
        for(int i=4;i<192&&!blocked;i++){var p=BlockPos.containing(origin.add(direction.scale(i*.5)));if(p.getY()>=dish.level().getMaxBuildHeight())break;if(dish.level().hasChunkAt(p)&&!dish.level().getBlockState(p).getCollisionShape(dish.level(),p).isEmpty())blocked=true;}
        return SatelliteModel.signal(satellite,s.azimuth,s.elevation,cable,dish.device().kind.equals(SputnikHardware.DISH)?4:1,blocked,dish.level().isThundering());}
    public static String fingerprint(SputnikRef... refs){var text=new StringBuilder();for(var r:refs)text.append(r.id()).append('/').append(r.device().identity).append('/').append(Hardware.ensure(r.device()).epoch).append('/').append(r.device().extensions.getOrDefault(SputnikState.KEY,"")).append('/').append(r.host().links.hashCode()).append(';');return text.toString();}
    public static List<Pair> pairs(ServerLevel l){var c=CACHE.get(l);if(c!=null&&c.tick==l.getGameTime())return c.pairs;var out=new ArrayList<Pair>();var refs=SputnikRef.loaded(l);
        for(var modem:refs)if(modem.device().kind.equals(SputnikHardware.MODEM)&&modem.live()&&modem.state().enabled)try{
            var terminal=peer(modem,2,"coax_tx");var receive=peer(modem,3,"coax_rx");if(terminal==null||receive==null||terminal.id()!=receive.id()||!terminal.device().kind.equals(SputnikHardware.VSAT)||Math.max(length(modem,2),length(modem,3))>64)continue;
            var ms=modem.state();var mr=reception(terminal,Math.max(length(modem,2),length(modem,3)));if(mr.quality()==0||!terminal.state().satellite.equals(ms.satellite))continue;
            for(var hub:refs)if(SputnikHardware.hubReady(hub.device())&&hub.state().enabled){var hs=hub.state();if(!hs.network.equals(ms.network)||!hs.satellite.equals(ms.satellite)||!hs.polarization.equals(ms.polarization)||!SatelliteModel.carriers(hs.txMhz,hs.rxMhz,ms.txMhz,ms.rxMhz)||!hs.subscribers.containsKey(modem.device().identity)||!ConfigStore.verify(ms.key,hs.subscribers.get(modem.device().identity)))continue;
                var odu=peer(hub,2,"coax_tx");var rx=peer(hub,3,"coax_rx");if(odu==null||rx==null||odu.id()!=rx.id()||!odu.device().kind.equals(SputnikHardware.ODU)||!odu.state().enabled||Math.max(length(hub,2),length(hub,3))>64)continue;
                var dish=peer(odu,2,"sat_rf_tx");var dr=peer(odu,3,"sat_rf_rx");if(dish==null||dr==null||dish.id()!=dr.id()||!dish.device().kind.equals(SputnikHardware.DISH)||Math.max(length(odu,2),length(odu,3))>8||!dish.state().satellite.equals(hs.satellite))continue;
                var hr=reception(dish,Math.max(length(hub,2),length(hub,3)));if(hr.quality()>0)out.add(new Pair(modem,terminal,hub,odu,dish,Math.min(hr.quality(),mr.quality()),fingerprint(modem,terminal,hub,odu,dish)));
            }
        }catch(IllegalArgumentException ignored){}
        CACHE.put(l,new Cache(l.getGameTime(),List.copyOf(out)));return out;
    }
    public static boolean valid(Pair p){return p.modem.live()&&p.terminal.live()&&p.hub.live()&&p.odu.live()&&p.dish.live()&&p.fingerprint.equals(fingerprint(p.modem,p.terminal,p.hub,p.odu,p.dish))&&pairs(p.modem.level()).stream().anyMatch(q->q.modem.id()==p.modem.id()&&q.hub.id()==p.hub.id()&&q.quality>0);}
    public static String status(SputnikRef r){if(!r.live())return "Нет питания";if(r.device().kind.equals(SputnikHardware.SPEAKER))return r.state().enabled?"Колонка включена / "+(int)(r.state().volume*100)+"%":"Колонка выключена";if(r.device().kind.equals(SputnikHardware.DISH)||r.device().kind.equals(SputnikHardware.VSAT)){var s=reception(r,0);return s.state()+String.format(Locale.ROOT," / запас %.1f dB",s.marginDb());}if(r.device().kind.equals(SputnikHardware.MODEM))return pairs(r.level()).stream().filter(p->p.modem.id()==r.id()).findFirst().map(p->"LOCK / "+p.hub.state().network+String.format(Locale.ROOT," / %.0f%%",p.quality*100)).orElse("OFFLINE: проверь TX/RX, наведение и регистрацию");return r.device().kind.equals(SputnikHardware.HUB)?(SputnikHardware.hubReady(r.device())?"HUB готов / абонентов "+r.state().subscribers.size():"Нужны CONTROL, TX, RX"):r.device().kind.equals(SputnikHardware.GATEWAY)?(r.state().internet?"Интернет-шлюз включён":"sat internet on"):"";}
    public static void topology(Level level,Network n){for(var d:n.devices.values())SputnikHardware.audio(d);if(!(level instanceof ServerLevel l))return;for(var p:pairs(l))if(valid(p)&&n.devices.containsKey(p.modem.id())&&n.devices.containsKey(p.hub.id()))n.wires.add(new Network.Wire(new Network.Endpoint(p.modem.id(),4),new Network.Endpoint(p.hub.id(),4),0,"satellite"));n.linkTransits.put("satellite",(net,w,from,ready,bytes,protocol)->transit(l,net,w,from,ready,bytes,protocol));}
    private static Network.Endpoint ep(SputnikRef r,int p){return new Network.Endpoint(r.id(),p);}
    private static long hop(ServerLevel l,Network n,Pair pair,Network.Endpoint a,Network.Endpoint b,long now,int bytes,double rate,double delay,Network.Endpoint queue,String phase,String protocol){String kind="SAT/"+pair.modem.id()+"/"+phase+"/"+protocol;double speed=n.speedPolicy.speed(a,b,1,kind);var slot=n.clock.send(queue,now,bytes,rate,0,delay,speed);var t=new Network.Traffic(a,b,kind,1,false,slot.start(),slot.finish(),bytes);n.traffic.add(t);n.deliveryGuards.add(()->valid(pair));return slot.finish();}
    private static long transit(ServerLevel l,Network n,Network.Wire w,Network.Endpoint from,long ready,int bytes,String protocol){var pair=pairs(l).stream().filter(p->p.modem.id()==w.a().device()&&p.hub.id()==w.b().device()).findFirst().orElse(null);if(pair==null){n.deliveryGuards.add(()->false);return ready;}boolean up=from.device()==pair.modem.id();long time=ready;var m=pair.modem;var t=pair.terminal;var h=pair.hub;var o=pair.odu;var d=pair.dish;double rate=(up?Math.min(m.state().upload,h.state().upload):Math.min(m.state().download,h.state().download))*pair.quality;
        if(up){time=hop(l,n,pair,ep(m,2),ep(t,0),time,bytes,1000,.05,ep(m,2),"COAX-TX",protocol);time=hop(l,n,pair,ep(t,0),ep(d,1),time,bytes,rate,SatelliteModel.ONE_WAY_MS,new Network.Endpoint(h.id(),1000),"AIR-UL",protocol);time=hop(l,n,pair,ep(d,1),ep(o,3),time,bytes,1000,.05,ep(d,1),"LNB",protocol);time=hop(l,n,pair,ep(o,1),ep(h,3),time,bytes,1000,.05,ep(o,1),"COAX-RX",protocol);}
        else{time=hop(l,n,pair,ep(h,2),ep(o,0),time,bytes,1000,.05,ep(h,2),"COAX-TX",protocol);time=hop(l,n,pair,ep(o,2),ep(d,0),time,bytes,1000,.05,ep(o,2),"BUC",protocol);time=hop(l,n,pair,ep(d,0),ep(t,1),time,bytes,rate,SatelliteModel.ONE_WAY_MS,new Network.Endpoint(h.id(),1001),"AIR-DL",protocol);time=hop(l,n,pair,ep(t,1),ep(m,3),time,bytes,1000,.05,ep(t,1),"COAX-RX",protocol);}return time;
    }
    public static BooleanSupplier witness(ServerLevel l,Network.Traffic t){if(!t.protocol().startsWith("SAT/"))return null;try{long id=Long.parseLong(t.protocol().split("/",4)[1]);var p=pairs(l).stream().filter(q->q.modem.id()==id).findFirst().orElse(null);return p==null?()->false:()->valid(p);}catch(RuntimeException e){return ()->false;}}
    public static void clear(){CACHE.clear();}
}
