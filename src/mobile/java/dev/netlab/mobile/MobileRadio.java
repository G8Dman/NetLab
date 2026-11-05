package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** RF and fronthaul are physical links, independent of Ethernet backhaul. */
public final class MobileRadio {
    public record Cell(MobileRef antenna,MobileRef rru,MobileRef bbu,int bbuPort,RadioModel.Site site){}
    public record Reception(Cell cell,RadioModel.Signal signal){}
    public static MobileRef peer(MobileRef from,int port,String medium){
        var p=from.host().links.get(from.bay()*128+port);if(p==null)return null;
        var l=from.level();var pos=BlockPos.of(p.pos());if(!l.hasChunkAt(pos)||!(l.getBlockEntity(pos) instanceof DeviceEntity host))return null;
        var back=host.links.get(p.port());var target=MobileRef.of(host,p.port()/128);
        if(target==null||back==null||back.pos()!=from.host().getBlockPos().asLong()||back.port()!=from.bay()*128+port)return null;
        var a=from.device().ports.get(port);var b=target.device().ports.get(p.port()%128);
        return !a.hidden&&!b.hidden&&a.up&&b.up&&Hardware.powered(from.device())&&Hardware.powered(target.device())&&a.medium.equals(medium)&&b.medium.equals(medium)&&CableKind.compatible(p.cable(),from.device(),port,target.device(),p.port()%128)?target:null;
    }
    public static List<Cell> cells(ServerLevel level){
        var cells=new ArrayList<Cell>();
        for(var r:MobileRef.loaded(level)){
            if(!r.device().kind.equals(MobileHardware.RRU)||!r.state().radioEnabled)continue;
            var a=peer(r,1,"rf");var second=peer(r,2,"rf");var b=peer(r,0,"fronthaul");
            if(a==null||second==null||a.id()!=second.id()||b==null||!a.device().kind.equals(MobileHardware.ANTENNA)||!MobileHardware.bbuReady(b.device()))continue;
            var p=a.host().getBlockPos();if(p.distSqr(r.host().getBlockPos())>64)continue;
            var s=r.state();double azimuth=s.azimuth<0?a.host().getBlockState().getValue(DeviceBlock.FACING).toYRot():s.azimuth;
            double height=height(level,p);var site=new RadioModel.Site(a.device().identity,p.getX()+.5,p.getY()+.5,p.getZ()+.5,s.band,s.channel,azimuth,s.tilt,height,s.range,s.power);
            int bp=r.host().links.get(0).port()%128;cells.add(new Cell(a,r,b,bp,site));
        }return cells;
    }
    private static double height(ServerLevel l,BlockPos p){
        var heights=new ArrayList<Integer>();for(int dx:new int[]{-12,0,12})for(int dz:new int[]{-12,0,12})if(dx!=0||dz!=0){var q=p.offset(dx,0,dz);if(l.hasChunkAt(q))heights.add(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,q.getX(),q.getZ()));}
        if(heights.isEmpty())return 0;Collections.sort(heights);return p.getY()-heights.get(heights.size()/2);
    }
    public static List<Reception> scan(ServerLevel l,Vec3 at){return scan(l,at,cells(l));}
    public static List<Reception> scan(ServerLevel l,Vec3 at,List<Cell> cells){
        var sites=cells.stream().map(Cell::site).toList();var out=new ArrayList<Reception>();
        for(var cell:cells){double loss=0;var origin=new Vec3(cell.site.x(),cell.site.y(),cell.site.z());for(int i=1;i<=16;i++){var p=BlockPos.containing(origin.lerp(at,i/17.0));if(l.hasChunkAt(p)&&!l.getBlockState(p).getCollisionShape(l,p).isEmpty())loss+=3;}
            out.add(new Reception(cell,RadioModel.signal(cell.site,sites,at.x,at.y,at.z,loss)));}
        out.sort(Comparator.comparingDouble((Reception r)->r.signal.rsrp()).reversed());return out;
    }
    public static boolean live(Cell c){if(!c.antenna.live()||!c.rru.live()||!c.bbu.live()||!MobileHardware.bbuReady(c.bbu.device())||!c.rru.state().radioEnabled)return false;var b=peer(c.rru,0,"fronthaul");var a=peer(c.rru,1,"rf");var second=peer(c.rru,2,"rf");return b!=null&&a!=null&&second!=null&&b.id()==c.bbu.id()&&a.id()==c.antenna.id()&&second.id()==c.antenna.id();}
}
