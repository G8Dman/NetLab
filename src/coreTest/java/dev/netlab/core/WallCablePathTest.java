package dev.netlab.core;

import java.util.*;

public final class WallCablePathTest {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static WallCablePath.Point p(double x,double y,double z){return new WallCablePath.Point(x,y,z);}
    public static int run(){
        checks=0;
        // A wall run around the outside of a corner, with rear-port exit stubs.
        WallCablePath.Point start=p(1,.5,1),exit=p(1,.5,1.12),approach=p(4,.5,1.12),end=p(4,.5,1);
        var anchors=List.of(p(1,2,1.12),p(1,2,4),p(4,2,4),p(4,2,1.12));
        var path=WallCablePath.between(start,exit,anchors,approach,end);
        check(WallCablePath.valid(path,64),"a multi-wall run with vertical sections is accepted");
        check(WallCablePath.length(path)>start.distance(end)*3,"length follows the walls rather than the distance between sockets");
        check(!WallCablePath.valid(path,4),"a detour cannot evade the configured cable length limit");
        var backwards=WallCablePath.between(end,approach,anchors.reversed(),exit,start);
        check(backwards.equals(path.reversed()),"either endpoint renders the same route in reverse order");
        check(Math.abs(WallCablePath.length(path)-WallCablePath.length(backwards))<1e-9,"render owner ordering does not change routed length");
        var north=p(1,2,-.025);var west=p(-.025,3,1);
        var corner=WallCablePath.bend(north,2,west,0);
        check(corner.equals(p(-.025,2,-.025)),"a turn follows the outer wall corner instead of cutting through the block");
        check(corner.z()==north.z()&&corner.x()==west.x(),"each side of the bend stays on its own surface plane");
        check(WallCablePath.bend(north,2,p(2,2,-.025),2)==null,"a straight wall run needs no artificial elbow");
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            int nx=axis==0?sign:0,ny=axis==1?sign:0,nz=axis==2?sign:0;
            var surface=WallCablePath.surface(8,4,2,nx,ny,nz);
            check(Math.abs(surface.distance(p(8,4,2))-WallCablePath.CLEARANCE)<1e-9,"wall/floor/ceiling anchors keep the wire outside the surface");
            check((surface.x()-8)*nx+(surface.y()-4)*ny+(surface.z()-2)*nz>0,"surface clearance always points outward");
        }
        var maximum=new ArrayList<WallCablePath.Point>();for(int i=0;i<32;i++)maximum.add(p(1+i*.05,1,2));
        check(WallCablePath.valid(WallCablePath.between(start,exit,maximum,approach,end),64),"32 anchors fit the route budget");
        maximum.add(p(3,1,2));
        check(!WallCablePath.valid(WallCablePath.between(start,exit,maximum,approach,end),64),"excessive waypoint counts are rejected");
        check(!WallCablePath.valid(List.of(start,p(Double.NaN,1,1)),64),"corrupt NaN coordinates are rejected");
        check(!WallCablePath.valid(List.of(start,p(1,Double.POSITIVE_INFINITY,1)),64),"corrupt infinite coordinates are rejected");
        check(!WallCablePath.valid(List.of(start,end),Double.POSITIVE_INFINITY),"unbounded length settings are rejected");
        var touching=WallCablePath.between(start,start,List.of(),end,end);
        check(WallCablePath.valid(touching,3),"coincident connector stubs do not create an invalid route");
        var elbow=List.of(p(0,0,0),p(0,0,10),p(10,0,10));
        check(WallCablePath.sample(elbow,.25).equals(p(0,0,5)),"packet follows the first wall rather than cutting diagonally");
        check(WallCablePath.sample(elbow,.5).equals(p(0,0,10)),"packet reaches the wall corner halfway along the route");
        check(WallCablePath.sample(elbow,.75).equals(p(5,0,10)),"packet continues along the second wall");
        for(int i=0;i<=40;i++){
            var forward=WallCablePath.sample(elbow,i/40.0);var reverse=WallCablePath.sample(elbow.reversed(),1-i/40.0);
            check(forward.distance(reverse)<1e-8,"forward and reverse packets occupy the same physical cable");
        }
        check(WallCablePath.sample(List.of(start,start),.5).equals(start),"coincident route points cannot divide by zero");
        var hit=WallCablePath.pick(elbow,p(5,0,7),p(5,0,11),.12);
        check(hit!=null&&hit.point().distance(p(5,0,10))<1e-8&&Math.abs(hit.distance()-3)<1e-8,"a wall detour outside the endpoint box can be selected");
        check(WallCablePath.pick(elbow,p(5,1,5),p(5,-1,5),.12)==null,"the old imaginary chord is not selectable");
        check(WallCablePath.pick(elbow,p(5,0,7),p(5,0,9.5),.12)==null,"a wall blocking the ray prevents selecting a cable behind it");
        var backHit=WallCablePath.pick(elbow.reversed(),p(5,0,7),p(5,0,11),.12);
        check(backHit!=null&&backHit.point().equals(hit.point()),"selection is independent of endpoint order");
        check(WallCablePath.pick(List.of(start,start),p(1,1,1),p(2,1,1),.12)==null,"zero length spans are skipped when selecting");
        var endpoint=new Network.Endpoint(1,0);
        long straight=new PacketClock().send(endpoint,0,64,100,start.distance(end),0,1).finish();
        long routed=new PacketClock().send(endpoint,0,64,100,WallCablePath.length(path),0,1).finish();
        check(routed>straight,"packet propagation uses full wall route length");
        check(CableKind.storedType("serial_dce",1,0,2,0).equals(CableKind.storedType("serial_dte",2,0,1,0)),"clicking the opposite Serial end preserves DCE ownership");
        check(CableKind.storedType("serial_dce",1,128,1,0).equals("serial_dte"),"Serial cables between rack bays preserve canonical clock direction");
        for(String type:CableKind.TYPES)if(!type.startsWith("serial"))check(CableKind.storedType(type,2,128,1,0).equals(type),"non-Serial cable type is independent of route and click order");
        return checks;
    }
}
