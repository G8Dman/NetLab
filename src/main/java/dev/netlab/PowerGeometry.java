package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Rear power sockets have their own namespace and cannot be mistaken for network ports. */
public final class PowerGeometry {
    public static final int ENERGY_OUT=300,PSU_IN=310,PSU_OUT=320,BOARD_IN=512;
    public record Jack(int port,double x,double y,double z,double size,boolean output){}
    public static List<Jack> jacks(DeviceEntity h){var list=new ArrayList<Jack>();boolean rack=((DeviceBlock)h.getBlockState().getBlock()).rack;
        if(h.device.kind.equals("energy"))for(int i=0;i<4;i++)list.add(new Jack(ENERGY_OUT+i,.2+i*.2,.5,1.003,.12,true));
        if(rack&&h.rackSupply){list.add(new Jack(PSU_IN,.11,.095,.96,.06,false));for(int i=0;i<8;i++)list.add(new Jack(PSU_OUT+i,.29+i*.085,.095,.96,.055,true));}
        if(LaptopBattery.laptop(h.device))list.add(new Jack(BOARD_IN,.20,.045,.88,.075,false));
        for(int bay=0;bay<(rack?2:1);bay++){var d=bay==0?h.device:h.upperDevice;if(!PowerModel.board(d))continue;double x=d.kind.equals("endpoint")?.50:.82,y=d.kind.equals("endpoint")?.20:.125,z=.895;if(rack){x=.06+x*.88;y+=(bay==0?.12:.62);z=.825;}list.add(new Jack(BOARD_IN+bay,x,y,z,.10,false));}
        return List.copyOf(list);
    }
    public static Jack jack(DeviceEntity h,int port){return jacks(h).stream().filter(j->j.port==port).findFirst().orElse(null);}
    public static Vec3 point(DeviceEntity h,int port){var j=jack(h,port);if(j==null)return new Vec3(.5,.5,1);var p=DeviceOrientation.rotate(j.x,j.y,j.z,DeviceBlock.turns(h.getBlockState()));return new Vec3(p.x(),p.y(),p.z());}
    public static List<Vec3> route(DeviceEntity a,int ap,DeviceEntity b,int bp){
        Vec3 start=point(a,ap).add(Vec3.atLowerCornerOf(a.getBlockPos())),end=point(b,bp).add(Vec3.atLowerCornerOf(b.getBlockPos()));
        Vec3 na=Vec3.atLowerCornerOf(a.getBlockState().getValue(DeviceBlock.FACING).getOpposite().getNormal()),nb=Vec3.atLowerCornerOf(b.getBlockState().getValue(DeviceBlock.FACING).getOpposite().getNormal());var exitA=start.add(na.scale(.4));var exitB=end.add(nb.scale(.4));
        if(na.dot(nb)>.99){double plane=Math.max(exitA.dot(na),exitB.dot(na));exitA=exitA.add(na.scale(plane-exitA.dot(na)));exitB=exitB.add(nb.scale(plane-exitB.dot(nb)));}
        return List.of(start,exitA,exitB,end);
    }
    public static List<WallCablePath.Point> fixedRoute(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        Vec3 start=point(a,ap).add(Vec3.atLowerCornerOf(a.getBlockPos())),end=point(b,bp).add(Vec3.atLowerCornerOf(b.getBlockPos()));
        var na=Vec3.atLowerCornerOf(a.getBlockState().getValue(DeviceBlock.FACING).getOpposite().getNormal());
        var nb=Vec3.atLowerCornerOf(b.getBlockState().getValue(DeviceBlock.FACING).getOpposite().getNormal());
        return WallCablePath.between(fixedPoint(start),fixedPoint(start.add(na.scale(.12))),anchors,fixedPoint(end.add(nb.scale(.12))),fixedPoint(end));
    }
    public static WallCablePath.Point fixedPoint(Vec3 p){return new WallCablePath.Point(p.x,p.y,p.z);}
    public static int clicked(DeviceEntity h,BlockHitResult hit,Player player){if(hit.getDirection()!=h.getBlockState().getValue(DeviceBlock.FACING).getOpposite())return -1;var base=Vec3.atLowerCornerOf(h.getBlockPos());var e=player.getEyePosition().subtract(base);var a=hit.getLocation().subtract(base);int turns=DeviceBlock.turns(h.getBlockState());var eye=DeviceOrientation.inverse(e.x,e.y,e.z,turns);var at=DeviceOrientation.inverse(a.x,a.y,a.z,turns);double dz=at.z()-eye.z();if(Math.abs(dz)<1e-9)return -1;
        for(var j:jacks(h)){double t=(j.z-eye.z())/dz;if(t<=0)continue;double x=eye.x()+(at.x()-eye.x())*t,y=eye.y()+(at.y()-eye.y())*t;if(Math.abs(x-j.x)<=j.size*.65&&Math.abs(y-j.y)<=j.size*.65)return j.port;}return -1;
    }
}
