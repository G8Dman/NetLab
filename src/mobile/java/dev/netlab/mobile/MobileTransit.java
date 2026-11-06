package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.api.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

/** One logical L3 bearer, with real queues and witnesses for every physical stage. */
public final class MobileTransit {
    public static BooleanSupplier witness(ServerLevel l,Network.Traffic t){
        var ue=MobileService.leases(l).values().stream().filter(x->x.endpointId()==t.from().device()||x.endpointId()==t.to().device()).findFirst().orElse(null);
        if(!t.protocol().startsWith("LTE-AIR/")&&(ue==null||t.from().device()!=ue.core.id()&&t.to().device()!=ue.core.id()))return null;
        if(ue==null)return ()->false;var cell=ue.cell;return ()->MobileService.valid(l,ue)&&ue.cell==cell;
    }
    private static Network.Endpoint end(MobileRef ref,int port){return new Network.Endpoint(ref.id(),port);}
    private static void encrypted(Network n,byte[] envelope){n.wirePayloads.put(n.traffic.getLast(),new Network.CapturePayload(envelope,true));}
    private static long hop(ServerLevel l,Network n,Network.Endpoint from,Network.Endpoint to,long ready,int bytes,double rate,double length,String protocol,Network.Endpoint queue,double latency){
        double speed=n.speedPolicy.speed(from,to,1,protocol);var slot=n.clock.send(queue,ready,bytes,rate,length,latency,speed);
        var t=new Network.Traffic(from,to,protocol,1,false,slot.start(),slot.finish(),bytes);n.traffic.add(t);n.deliveryGuards.add(Simulation.witness(l,n,t));return slot.finish();
    }
    public static long send(ServerLevel l,Network n,Network.Wire wire,Network.Endpoint from,long ready,int bytes,String protocol){
        var lease=MobileService.leases(l).values().stream().filter(x->x.endpointId()==wire.a().device()||x.endpointId()==wire.b().device()).findFirst().orElse(null);
        if(lease==null||!MobileService.valid(l,lease)){n.deliveryGuards.add(()->false);return ready;}
        boolean uplink=from.device()==lease.endpointId();var cell=lease.cell;long time=ready;
        var ue=new Network.Endpoint(lease.endpointId(),lease.cellularPort());var antenna=end(cell.antenna(),0);var rruRF=end(cell.rru(),1);var rruFH=end(cell.rru(),0);var bbu=end(cell.bbu(),cell.bbuPort());
        double capacity=uplink?1:5;String air="LTE-AIR/"+(uplink?"UL":"DL")+"/"+protocol;
        // One sector queue in each direction; concurrent users share capacity rather than receiving independent links.
        var queue=new Network.Endpoint(cell.antenna().id(),uplink?1000:1001);
        byte[] clear=n.currentPayload(),payload=clear;int radioBytes=bytes+36;
        if(uplink){
            byte[] envelope=lease.upTx.seal(clear);payload=lease.upRx.open(envelope);if(!Arrays.equals(payload,clear))n.deliveryGuards.add(()->false);
            time=hop(l,n,ue,antenna,time,radioBytes,capacity*lease.signal.quality(),lease.signal.distance(),air,queue,8);
            encrypted(n,envelope);
            time=hop(l,n,antenna,rruRF,time,radioBytes,1000,2,"RF-IQ",antenna,0.01);
            time=hop(l,n,rruFH,bbu,time,radioBytes,1000,cell.rru().host().getBlockPos().distSqr(cell.bbu().host().getBlockPos())==0?1:Math.sqrt(cell.rru().host().getBlockPos().distSqr(cell.bbu().host().getBlockPos())),"FRONTHAUL-IQ",rruFH,0.1);
        }
        var a=uplink?cell.bbu():lease.core;var b=uplink?lease.core:cell.bbu();
        var under=WirelessWorld.network(l,false);under.cursor=Math.max(under.cursor,time);
        var plan=under.routePath(a.id(),b.ip());
        if(!plan.success()){n.deliveryGuards.add(()->false);return time;}
        var store=VpnStore.get(l);var tunnel=store.tunnel(under,plan.path());
        if(tunnel==null&&store.hasPolicy(under,plan.path())){n.deliveryGuards.add(()->false);return time;}
        var transform=tunnel==null?null:new RouterTunnel(tunnel);if(transform!=null)under.routerPayload=transform;
        var delivery=under.transmit(a.id(),b.ip(),"udp",2152,payload);
        n.traffic.addAll(under.traffic);n.wirePayloads.putAll(under.wirePayloads);n.changed.addAll(under.changed);n.deliveryGuards.add(Simulation.guard(l,under));
        if(!delivery.result().success()||delivery.destination()!=b.id()||transform!=null&&!transform.completed())n.deliveryGuards.add(()->false);
        time=under.cursor;
        if(!uplink){
            byte[] envelope=lease.downTx.seal(delivery.payload());if(!Arrays.equals(lease.downRx.open(envelope),clear))n.deliveryGuards.add(()->false);
            time=hop(l,n,bbu,rruFH,time,radioBytes,1000,Math.sqrt(cell.rru().host().getBlockPos().distSqr(cell.bbu().host().getBlockPos())),"FRONTHAUL-IQ",bbu,0.1);
            time=hop(l,n,rruRF,antenna,time,radioBytes,1000,2,"RF-IQ",rruRF,0.01);
            time=hop(l,n,antenna,ue,time,radioBytes,capacity*lease.signal.quality(),lease.signal.distance(),air,queue,8);
            encrypted(n,envelope);
        }
        n.deliveryGuards.add(()->MobileService.valid(l,lease)&&MobileRadio.live(cell));return time;
    }
}
