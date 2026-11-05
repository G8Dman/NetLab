package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Scheduled payload delivery and captures use the same link clock as CLI traffic. */
public final class ApplicationTransport {
    private static String ip(Device d){return d.ports.get(d.kind.equals("pc")?Wireless.clientPort(d):0).ip;}
    public record Endpoint(DeviceEntity host,int slot){public Device device(){return slot==0?host.device:host.upperDevice;}public long id(){return host.slotId(slot);}}
    public record Capture(ServerLevel level,long from,int fromPort,long to,int toPort,byte[] bytes,boolean encrypted){}
    public record Receipt(boolean success,String status,boolean encrypted,byte[] payload,List<Long> path){}
    public static final List<java.util.function.Consumer<Capture>> OBSERVERS=new CopyOnWriteArrayList<>();
    private static CompletableFuture<Receipt> fail(String message){return CompletableFuture.completedFuture(new Receipt(false,message,false,new byte[0],List.of()));}
    @Deprecated public static Receipt send(ServerLevel level,DeviceEntity source,DeviceEntity destination,byte[] payload,boolean requireVpn){return new Receipt(false,"Обнови дополнение: передача теперь использует sendAsync и очередь симуляции",false,new byte[0],List.of());}
    public static CompletableFuture<Receipt> sendAsync(ServerLevel level,DeviceEntity source,DeviceEntity destination,byte[] payload,boolean requireVpn){
        return sendAsync(level,new Endpoint(source,0),new Endpoint(destination,0),payload,requireVpn);
    }
    public static CompletableFuture<Receipt> sendAsync(ServerLevel level,Endpoint source,Endpoint destination,byte[] payload,boolean requireVpn){
        return sendAsync(level,source,destination,payload,requireVpn,TrafficMark.NONE);
    }
    public static CompletableFuture<Receipt> sendAsync(ServerLevel level,Endpoint source,Endpoint destination,byte[] payload,boolean requireVpn,TrafficMark mark){
        return sendAsync(level,source,destination,payload,requireVpn,mark,5420);
    }
    public static CompletableFuture<Receipt> sendAsync(ServerLevel level,Endpoint source,Endpoint destination,byte[] payload,boolean requireVpn,TrafficMark mark,int destinationPort){
        if(destinationPort<1||destinationPort>65535)return fail("Invalid destination port");
        var sourceDevice=source.device();var destinationDevice=destination.device();
        if(sourceDevice==null||destinationDevice==null)return fail("Empty device bay");
        String sourceIp=ip(sourceDevice);
        if(payload.length>60000)return fail("Payload exceeds 60 KiB");
        var n=source.host.network();long from=source.id(),to=destination.id();String ip=ip(destinationDevice);
        if(ip.isBlank())return fail("Destination has no IP");
        if(!n.devices.containsKey(from)||!n.devices.containsKey(to))return fail("Device outside loaded topology limit");
        boolean encrypted=false;
        try{
            var planned=n.routePath(from,ip);if(!planned.success())return fail(planned.text());
            var vpn=VpnStore.get(level);var route=planned.path();var tunnel=vpn.tunnel(n,route);
            if(tunnel==null&&vpn.hasPolicy(n,route))return fail("VPN peer configuration incomplete or keys differ");
            long routers=route.stream().filter(id->n.devices.get(id).kind.equals("router")).count();
            if(tunnel==null&&requireVpn&&routers>=2)return fail("VPN required: no authenticated router pair");
            encrypted=tunnel!=null;final boolean protectedDelivery=encrypted;
            Study.publish(n,level);
            var transform=encrypted?new RouterTunnel(tunnel):null;if(transform!=null)n.routerPayload=transform;
            var delivery=n.transmit(from,ip,"tcp",destinationPort,payload.clone());byte[] wire=encrypted?transform.envelope():payload.clone();var path=delivery.result().path();
            var guards=new ArrayList<BooleanSupplier>();
            for(var e:n.traffic){
                if(!(e.protocol().endsWith("TCP")||e.protocol().endsWith("UDP"))||e.dropped())continue;
                var actual=n.wirePayloads.get(e);boolean protectedHop=actual!=null?actual.encrypted():encrypted&&e.protocol().endsWith("UDP");
                var capture=new Capture(level,e.from().device(),e.from().port(),e.to().device(),e.to().port(),(actual!=null?actual.bytes():protectedHop?wire:payload).clone(),protectedHop);
                var valid=Simulation.witness(level,n,e);
                if(!e.protocol().startsWith("SPAN/")&&path.contains(e.from().device())&&path.contains(e.to().device()))guards.add(valid);
                Simulation.after(level,e.finish(),()->{if(valid.getAsBoolean())for(var observer:OBSERVERS)observer.accept(capture);return true;});
            }
            if(!delivery.result().success()||delivery.destination()!=to)return fail(delivery.result().text());
            guards.addAll(n.deliveryGuards);
            return Simulation.after(level,n.cursor,()->{
                if(source.host.isRemoved()||destination.host.isRemoved()||source.device()!=sourceDevice||destination.device()!=destinationDevice||!ip(sourceDevice).equals(sourceIp)||!ip(destinationDevice).equals(ip)||guards.stream().anyMatch(g->!g.getAsBoolean()))return new Receipt(false,"Cable, device or destination changed during transmission",protectedDelivery,new byte[0],path);
                if(protectedDelivery&&!transform.completed())return new Receipt(false,"VPN receiving router was not reached",true,new byte[0],path);
                byte[] received=delivery.payload();
                return new Receipt(true,"Delivered",protectedDelivery,received,List.copyOf(path));
            });
        }finally{WirelessWorld.changed(level,n);Study.publish(n,level,mark,encrypted);}
    }
}
