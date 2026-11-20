package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Power sockets have their own namespace and can be mounted on a device's side. */
public final class PowerGeometry {
    public static final int ENERGY_OUT=300,PSU_IN=310,PSU_OUT=320,BOARD_IN=512;
    public record Jack(int port,double x,double y,double z,double size,boolean output){}
    public static List<Jack> jacks(DeviceEntity h){var list=new ArrayList<Jack>();boolean rack=((DeviceBlock)h.getBlockState().getBlock()).rack;
        if(h.device.kind.equals("energy"))for(int i=0;i<4;i++)list.add(new Jack(ENERGY_OUT+i,.2+i*.2,.5,1.003,.12,true));
        if(rack&&h.rackSupply){list.add(new Jack(PSU_IN,.11,.095,.96,.06,false));for(int i=0;i<8;i++)list.add(new Jack(PSU_OUT+i,.29+i*.085,.095,.96,.055,true));}
        if(LaptopBattery.laptop(h.device))list.add(new Jack(BOARD_IN,.20,.045,.88,.075,false));
        for(int bay=0;bay<(rack?2:1);bay++){
            var d=bay==0?h.device:h.upperDevice;if(!PowerModel.board(d))continue;
            var custom=rack?null:((DeviceBlock)h.getBlockState().getBlock()).powerInputJack(h.getBlockState());
            if(custom!=null){list.add(custom);continue;}
            double x=d.kind.equals("endpoint")?.50:.82,y=d.kind.equals("endpoint")?.20:.125,z=.895;
            if(rack){x=.06+x*.88;y+=(bay==0?.12:.62);z=.825;}
            list.add(new Jack(BOARD_IN+bay,x,y,z,.10,false));
        }
        return List.copyOf(list);
    }
    public static Jack jack(DeviceEntity h,int port){return jacks(h).stream().filter(j->j.port==port).findFirst().orElse(null);}
    private static DeviceBlock.SocketMount mount(DeviceEntity h){
        var state=h.getBlockState();
        return DeviceBlock.socketMount(state,true,((DeviceBlock)state.getBlock()).rack);
    }
    public static net.minecraft.core.Direction facing(DeviceEntity h){
        var state=h.getBlockState();
        return DeviceBlock.socketFacing(state,true,((DeviceBlock)state.getBlock()).rack);
    }
    public static Vec3 point(DeviceEntity h,int port){
        var j=jack(h,port);if(j==null)return new Vec3(.5,.5,1);
        var local=mount(h).toDevice(new Vec3(j.x,j.y,j.z));
        var p=DeviceOrientation.rotate(local.x,local.y,local.z,DeviceBlock.turns(h.getBlockState()));
        return new Vec3(p.x(),p.y(),p.z());
    }
    public static List<Vec3> route(DeviceEntity a,int ap,DeviceEntity b,int bp){
        Vec3 start=point(a,ap).add(Vec3.atLowerCornerOf(a.getBlockPos())),end=point(b,bp).add(Vec3.atLowerCornerOf(b.getBlockPos()));
        Vec3 na=Vec3.atLowerCornerOf(facing(a).getNormal()),nb=Vec3.atLowerCornerOf(facing(b).getNormal());var exitA=start.add(na.scale(.4));var exitB=end.add(nb.scale(.4));
        if(na.dot(nb)>.99){double plane=Math.max(exitA.dot(na),exitB.dot(na));exitA=exitA.add(na.scale(plane-exitA.dot(na)));exitB=exitB.add(nb.scale(plane-exitB.dot(nb)));}
        return List.of(start,exitA,exitB,end);
    }
    public static List<WallCablePath.Point> fixedRoute(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        Vec3 start=point(a,ap).add(Vec3.atLowerCornerOf(a.getBlockPos())),end=point(b,bp).add(Vec3.atLowerCornerOf(b.getBlockPos()));
        var na=Vec3.atLowerCornerOf(facing(a).getNormal());
        var nb=Vec3.atLowerCornerOf(facing(b).getNormal());
        return WallCablePath.between(fixedPoint(start),fixedPoint(start.add(na.scale(.12))),anchors,fixedPoint(end.add(nb.scale(.12))),fixedPoint(end));
    }
    public static WallCablePath.Point fixedPoint(Vec3 p){return new WallCablePath.Point(p.x,p.y,p.z);}
    public static int clicked(DeviceEntity h,BlockHitResult hit,Player player){return clicked(h,hit,player.getEyePosition());}
    public static int clicked(DeviceEntity h,BlockHitResult hit,Vec3 eyePosition){
        if(hit.getDirection()!=facing(h))return -1;
        var eye=local(h,eyePosition);var at=local(h,hit.getLocation());
        double dz=at.z-eye.z;if(Math.abs(dz)<1e-9)return -1;
        for(var j:jacks(h)){
            double t=(j.z-eye.z)/dz;if(t<=0)continue;
            double x=eye.x+(at.x-eye.x)*t,y=eye.y+(at.y-eye.y)*t;
            if(Math.abs(x-j.x)<=j.size*.65&&Math.abs(y-j.y)<=j.size*.65)return j.port;
        }
        return -1;
    }
    private static Vec3 local(DeviceEntity host,Vec3 world){
        var at=world.subtract(Vec3.atLowerCornerOf(host.getBlockPos()));
        var p=DeviceOrientation.inverse(at.x,at.y,at.z,DeviceBlock.turns(host.getBlockState()));
        return mount(host).toPanel(new Vec3(p.x(),p.y(),p.z()));
    }
}
