package dev.netlab.media.core;

import dev.netlab.core.DeviceOrientation;

/** The visible image and the hit area use the same north-facing screen coordinates. */
public final class TelevisionSurface {
    public static final float LEFT_X=.095f, RIGHT_X=.905f, BOTTOM=.33f, TOP=.785625f, FRONT=.060f;
    public static final double WALL_OFFSET=11/16.0, SIDE_DEPTH=3/16.0;
    public record Pixel(double u,double v) {}

    public static Pixel pixel(double x,double y,double z,int turns){
        var p=DeviceOrientation.inverse(x,y,z,turns);
        if(p.x()<LEFT_X||p.x()>RIGHT_X||p.y()<BOTTOM||p.y()>TOP)return null;
        // Looking at a north-facing screen, world east is on the viewer's left.
        return new Pixel((RIGHT_X-p.x())/(RIGHT_X-LEFT_X),(TOP-p.y())/(TOP-BOTTOM));
    }

    private TelevisionSurface(){}
}
