package dev.netlab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import dev.netlab.core.WallCablePath;
import java.util.*;

/** One shape shared by drawing, selection and diagnostic labels. */
public final class CableGeometry {
    public static final int SEGMENTS=24;
    public static Vec3 port(DeviceEntity device,int port){
        var local=localPort(device,port);var p=dev.netlab.core.DeviceOrientation.rotate(local.x,local.y,local.z,DeviceBlock.turns(device.getBlockState()));
        return new Vec3(p.x(),p.y(),p.z());
    }
    private static Vec3 localPort(DeviceEntity device,int port){
        var targetDevice=device.portDevice(port);boolean rack=((DeviceBlock)device.getBlockState().getBlock()).rack;
        if(dev.netlab.core.PortLayout.supported(targetDevice))for(var jack:dev.netlab.core.PortLayout.jacks(targetDevice,rack,port/128))
            if(jack.port()==port)return new Vec3(jack.x(),jack.y(),jack.z());
        if(((DeviceBlock)device.getBlockState().getBlock()).rack){
            var target=device.portDevice(port); if(target==null)return new Vec3(.5,.5,.1);
            return new Vec3((target.physicalSlot(port%128)+.5)/Math.max(1,target.physicalPortCount()),port>=128?.75:.25,.1);
        }
        return new Vec3((device.device.physicalSlot(port)+.5)/device.device.physicalPortCount(),((DeviceBlock)device.getBlockState().getBlock()).rack?.45:.13,.1);
    }
    public static Vec3 worldPort(DeviceEntity device,int port){return port(device,port).add(Vec3.atLowerCornerOf(device.getBlockPos()));}
    public static List<WallCablePath.Point> route(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        var start=worldPort(a,ap);var end=worldPort(b,bp);
        var na=Vec3.atLowerCornerOf(a.getBlockState().getValue(DeviceBlock.FACING).getNormal());
        var nb=Vec3.atLowerCornerOf(b.getBlockState().getValue(DeviceBlock.FACING).getNormal());
        return WallCablePath.between(PowerGeometry.fixedPoint(start),PowerGeometry.fixedPoint(start.add(na.scale(.12))),anchors,PowerGeometry.fixedPoint(end.add(nb.scale(.12))),PowerGeometry.fixedPoint(end));
    }
    public static Vec3 vector(WallCablePath.Point point){return new Vec3(point.x(),point.y(),point.z());}
    public static Vec3 point(Vec3 a,Vec3 b,double t){
        // Low laptop sockets must not pull the wire below the supporting block's top surface.
        double lower=Math.min(a.y,b.y),clearance=Math.max(0,lower-Math.floor(lower)-.025);
        double sag=Math.min(Math.min(.7,a.distanceTo(b)*.04),clearance);
        return a.lerp(b,t).add(0,-4*sag*t*(1-t),0);
    }
    public record Hit(DeviceEntity device,int port,Vec3 point,double distance) {}
    public static Hit pick(Player player){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        var wall=player.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        if(eye.distanceToSqr(end)<1e-8)return null;
        Hit best=null;
        for(var d:DeviceEntity.loaded(player.level()))for(var entry:d.links.entrySet()){
            var peer=entry.getValue();if(d.getBlockPos().asLong()>peer.pos()||d.getBlockPos().asLong()==peer.pos()&&entry.getKey()>peer.port())continue;
            BlockPos target=BlockPos.of(peer.pos());
            if(!player.level().hasChunkAt(target)||!(player.level().getBlockEntity(target) instanceof DeviceEntity other))continue;
            var path=route(d,entry.getKey(),other,peer.port(),peer.anchors());
            var candidate=WallCablePath.pick(path,PowerGeometry.fixedPoint(eye),PowerGeometry.fixedPoint(end),.12);
            if(candidate!=null&&(best==null||candidate.distance()<best.distance()))best=new Hit(d,entry.getKey(),vector(candidate.point()),candidate.distance());
        }
        return best;
    }
    public static int clickedPort(DeviceEntity device,BlockHitResult hit,Player player){
        boolean rack=((DeviceBlock)device.getBlockState().getBlock()).rack;
        if(hit.getDirection()==device.getBlockState().getValue(DeviceBlock.FACING)){
            var eye=local(device,player.getEyePosition());var at=local(device,hit.getLocation());int bay=0;
            if(rack){var projected=dev.netlab.core.PortLayout.project(true,eye.x,eye.y,eye.z,at.x,at.y,at.z);bay=projected.y()>=.5?1:0;}
            var target=device.portDevice(bay*128);
            if(dev.netlab.core.PortLayout.supported(target))return dev.netlab.core.PortLayout.pickRay(target,rack,bay,eye.x,eye.y,eye.z,at.x,at.y,at.z);
        }
        return clickedPort(device,hit);
    }
    private static Vec3 local(DeviceEntity device,Vec3 world){
        var at=world.subtract(Vec3.atLowerCornerOf(device.getBlockPos()));var p=dev.netlab.core.DeviceOrientation.inverse(at.x,at.y,at.z,DeviceBlock.turns(device.getBlockState()));return new Vec3(p.x(),p.y(),p.z());
    }
    public static int clickedPort(DeviceEntity device,BlockHitResult hit){
        var at=local(device,hit.getLocation());int offset=((DeviceBlock)device.getBlockState().getBlock()).rack && at.y>=.5?128:0;
        var target=device.portDevice(offset);if(target==null||target.physicalPortCount()==0)return -1;
        if(dev.netlab.core.PortLayout.supported(target)){
            if(hit.getDirection()!=device.getBlockState().getValue(DeviceBlock.FACING))return -1;
            return dev.netlab.core.PortLayout.pick(target,((DeviceBlock)device.getBlockState().getBlock()).rack,offset/128,
                at.x,at.y);
        }
        return offset+target.physicalPortIndex(Math.clamp((int)(at.x*target.physicalPortCount()),0,target.physicalPortCount()-1));
    }
}
