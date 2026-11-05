package dev.netlab.core;

import java.util.*;

/** Fixed surface waypoints. Kept independent of Minecraft for route validation. */
public final class WallCablePath {
    public static final int MAX_ANCHORS=32;
    public static final double CLEARANCE=.025;
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
        public double distance(Point p){return Math.hypot(Math.hypot(x-p.x,y-p.y),z-p.z);}
    }
    public static Point surface(double x,double y,double z,int nx,int ny,int nz){
        return new Point(x+nx*CLEARANCE,y+ny*CLEARANCE,z+nz*CLEARANCE);
    }
    /** Join two perpendicular surface planes at their intersection, keeping both legs on a plane. */
    public static Point bend(Point previous,int previousAxis,Point next,int nextAxis){
        if(previousAxis==nextAxis)return null;
        double[] xyz={previous.x,previous.y,previous.z},target={next.x,next.y,next.z};xyz[nextAxis]=target[nextAxis];
        return new Point(xyz[0],xyz[1],xyz[2]);
    }
    public static List<Point> between(Point start,Point exit,List<Point> anchors,Point approach,Point end){
        var path=new ArrayList<Point>();path.add(start);path.add(exit);path.addAll(anchors);path.add(approach);path.add(end);
        return List.copyOf(path);
    }
    public static double length(List<Point> path){
        double length=0;for(int i=1;i<path.size();i++)length+=path.get(i-1).distance(path.get(i));return length;
    }
    /** Arc-length interpolation shared by packet markers and geometric checks. */
    public static Point sample(List<Point> path,double progress){
        if(path.isEmpty())throw new IllegalArgumentException("Empty cable path");
        double remaining=length(path)*Math.clamp(progress,0,1);
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);double distance=a.distance(b);if(distance<1e-9)continue;
            if(remaining<=distance){double t=remaining/distance;return new Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);}remaining-=distance;
        }return path.getLast();
    }
    public record Hit(Point point,double distance){}
    /** Nearest wire segment within a finite interaction ray; routes may leave the endpoint bounding box. */
    public static Hit pick(List<Point> path,Point eye,Point end,double radius){
        double ux=end.x-eye.x,uy=end.y-eye.y,uz=end.z-eye.z,uu=ux*ux+uy*uy+uz*uz;if(uu<1e-12)return null;
        Hit best=null;
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);
            if(Math.max(a.x,b.x)+radius<Math.min(eye.x,end.x)||Math.min(a.x,b.x)-radius>Math.max(eye.x,end.x)
                ||Math.max(a.y,b.y)+radius<Math.min(eye.y,end.y)||Math.min(a.y,b.y)-radius>Math.max(eye.y,end.y)
                ||Math.max(a.z,b.z)+radius<Math.min(eye.z,end.z)||Math.min(a.z,b.z)-radius>Math.max(eye.z,end.z))continue;
            double vx=b.x-a.x,vy=b.y-a.y,vz=b.z-a.z,wx=eye.x-a.x,wy=eye.y-a.y,wz=eye.z-a.z;
            double vv=vx*vx+vy*vy+vz*vz;if(vv<1e-12)continue;
            double uv=ux*vx+uy*vy+uz*vz,uw=ux*wx+uy*wy+uz*wz,vw=vx*wx+vy*wy+vz*wz,den=uu*vv-uv*uv;
            double s=den<1e-12?0:Math.clamp((uv*vw-vv*uw)/den,0,1),t=(uv*s+vw)/vv;
            if(t<0){t=0;s=Math.clamp(-uw/uu,0,1);}else if(t>1){t=1;s=Math.clamp((uv-uw)/uu,0,1);}
            var point=new Point(a.x+vx*t,a.y+vy*t,a.z+vz*t);double separation=point.distance(new Point(eye.x+ux*s,eye.y+uy*s,eye.z+uz*s));
            double distance=s*Math.sqrt(uu);if(separation<=radius&&(best==null||distance<best.distance))best=new Hit(point,distance);
        }return best;
    }
    public static boolean valid(List<Point> path,double maximum){
        return path.size()>=2&&path.size()<=MAX_ANCHORS+4&&Double.isFinite(maximum)&&maximum>0
            &&path.stream().allMatch(Point::finite)&&length(path)<=maximum+1e-6;
    }
}
