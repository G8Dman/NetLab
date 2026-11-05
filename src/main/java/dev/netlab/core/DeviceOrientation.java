package dev.netlab.core;

/** Clockwise quarter turns from north, around the block centre. No Minecraft dependency. */
public final class DeviceOrientation {
    public record Point(double x,double y,double z){}
    public static Point rotate(double x,double y,double z,int turns){
        return switch(Math.floorMod(turns,4)){
            case 1->new Point(1-z,y,x);
            case 2->new Point(1-x,y,1-z);
            case 3->new Point(z,y,1-x);
            default->new Point(x,y,z);
        };
    }
    public static Point inverse(double x,double y,double z,int turns){return rotate(x,y,z,-turns);}
}
