package dev.netlab.core;

import java.util.*;

/** Shared normalized geometry for world sockets, cable ends and server-side hit selection. */
public final class PortLayout {
    /** Addons describe their own enclosure face; selection and drawing consume the same sockets. */
    public record Profile(String title,double face,double titleY,double powerX,double powerY,List<Jack> jacks,Map<Integer,String> labels){}
    public static final Map<String,java.util.function.Function<Device,Profile>> PROFILES=new java.util.concurrent.ConcurrentHashMap<>();
    public static Profile profile(Device device){var factory=PROFILES.get(device.kind);return factory==null?null:factory.apply(device);}
    public record Point(double x,double y,double z){}
    /** A rack's selection box is in front of its recessed sockets; compensate for angled clicks. */
    public static Point project(boolean rack,double eyeX,double eyeY,double eyeZ,double hitX,double hitY,double hitZ){
        return project(rack?.11:.10,eyeX,eyeY,eyeZ,hitX,hitY,hitZ);
    }
    public static Point project(double z,double eyeX,double eyeY,double eyeZ,double hitX,double hitY,double hitZ){
        double dz=hitZ-eyeZ;if(Math.abs(dz)<1e-9)return new Point(hitX,hitY,hitZ);
        double t=(z-eyeZ)/dz;if(t<=0)return new Point(hitX,hitY,hitZ);
        return new Point(eyeX+(hitX-eyeX)*t,eyeY+(hitY-eyeY)*t,z);
    }
    public record Jack(int port,double x,double y,double z,double width,double height,boolean console,String medium){
        public boolean contains(double px,double py){return Math.abs(px-x)<=width*.62&&Math.abs(py-y)<=height*.62;}
    }
    public static boolean supported(Device d){return d!=null&&DeviceKinds.ports(d.kind);}
    public static List<Jack> jacks(Device d,boolean rack,int bay){
        if(!supported(d))return List.of();var custom=profile(d);
        if(custom!=null){if(!rack)return custom.jacks();return custom.jacks().stream().map(j->new Jack(bay*128+j.port,.06+j.x*.88,(bay==0?.12:.62)+j.y,.02+j.z*.9,j.width*.88,j.height,j.console,j.medium)).toList();}
        var data=new ArrayList<Integer>();var consoles=new ArrayList<Integer>();
        for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);if(p.hidden||p.virtual||"wireless".equals(p.medium)||"cellular".equals(p.medium))continue;(p.console?consoles:data).add(i);}
        if(d.kind.equals("pc")){
            boolean laptop=d.hardware!=null&&d.hardware.profile.equals("Laptop-PT");var out=new ArrayList<Jack>();
            for(int i=0;i<data.size();i++)out.add(new Jack(data.get(i),(laptop?.23:.43)+i*(laptop?.18:-.18),laptop?.035:.087,laptop?.058:.10,laptop?.085:.09,laptop?.042:.075,false,CableKind.medium(d,data.get(i))));
            for(int i=0;i<consoles.size();i++)out.add(new Jack(consoles.get(i),laptop?.72:.60,laptop?.0375:.087,laptop?.028:.10,.11,laptop?.05:.075,true,"console"));
            return List.copyOf(out);
        }
        if(d.kind.equals("endpoint")){
            var out=new ArrayList<Jack>();
            for(int i=0;i<data.size();i++)out.add(new Jack(data.get(i),.34,.20,.115,.125,.11,false,CableKind.medium(d,data.get(i))));
            for(int i=0;i<consoles.size();i++)out.add(new Jack(consoles.get(i),.66,.20,.115,.125,.11,true,"console"));
            if(!rack)return List.copyOf(out);
            return out.stream().map(j->new Jack(bay*128+j.port,.06+j.x*.88,(bay==0?.12:.62)+j.y,.02+j.z*.9,j.width*.88,j.height,j.console,j.medium)).toList();
        }
        int columns=Math.min(13,Math.max(1,data.size())),rows=Math.max(1,(data.size()+columns-1)/columns);
        double area=consoles.isEmpty()?.88:.77,step=area/columns;
        var out=new ArrayList<Jack>();
        for(int i=0;i<data.size();i++){
            double x=.06+(i%columns+.5)*step,y=.035+(rows-i/columns-.5)*.18/rows;
            out.add(new Jack(data.get(i),x,y,.10,Math.min(.11,step*.78),Math.min(.085,.18/rows*.68),false,CableKind.medium(d,data.get(i))));
        }
        for(int i=0;i<consoles.size();i++)out.add(new Jack(consoles.get(i),.918,.125,.10,.075,.082,true,"console"));
        if(!rack)return List.copyOf(out);
        return out.stream().map(j->new Jack(bay*128+j.port,.06+j.x*.88,(bay==0?.12:.62)+j.y,.02+j.z*.9,j.width*.88,j.height,j.console,j.medium)).toList();
    }
    public static int pick(Device d,boolean rack,int bay,double x,double y){
        for(var jack:jacks(d,rack,bay))if(jack.contains(x,y))return jack.port;return -1;
    }
    public static int pickRay(Device d,boolean rack,int bay,double eyeX,double eyeY,double eyeZ,double hitX,double hitY,double hitZ){
        int chosen=-1;double closest=Double.POSITIVE_INFINITY,dz=hitZ-eyeZ;if(Math.abs(dz)<1e-9)return -1;
        for(var jack:jacks(d,rack,bay)){
            double t=(jack.z-eyeZ)/dz;if(t<=0||t>=closest)continue;var at=project(jack.z,eyeX,eyeY,eyeZ,hitX,hitY,hitZ);
            if(jack.contains(at.x,at.y)){chosen=jack.port;closest=t;}
        }return chosen;
    }
}
