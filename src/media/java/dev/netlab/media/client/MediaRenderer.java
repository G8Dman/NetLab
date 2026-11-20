package dev.netlab.media.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.netlab.DeviceBlock;
import dev.netlab.DeviceEntity;
import dev.netlab.client.CableRenderer;
import dev.netlab.media.core.MediaHardware;
import dev.netlab.media.core.TelevisionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class MediaRenderer implements BlockEntityRenderer<DeviceEntity> {
    private final CableRenderer cables;
    public MediaRenderer(BlockEntityRendererProvider.Context context){cables=new CableRenderer(context);}

    @Override public void render(DeviceEntity host,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        cables.render(host,tick,pose,buffers,light,overlay);
        if(!host.device.kind.equals(MediaHardware.TV))return;
        var facing=host.getBlockState().getValue(DeviceBlock.FACING);
        var mc=Minecraft.getInstance();
        Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
        var delta=camera.subtract(Vec3.atCenterOf(host.getBlockPos()));
        if(facing.getStepX()*delta.x+facing.getStepZ()*delta.z<-.39)return;
        var display=MediaVideoSession.display(host.getBlockPos());
        var session=MediaVideoSession.SESSIONS.get(host.getBlockPos().asLong());
        pose.pushPose();
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-90*DeviceBlock.turns(host.getBlockState())));
        pose.translate(-.5,0,-.5);
        label(pose,buffers,"PWR",.86,.211,.058,.004f,display!=null&&display.on()?0xff75e897:0xffd9aca2);
        label(pose,buffers,"VOL",.14,.211,.058,.003f,0xffadc9cd);
        if(display!=null&&display.on()){
            String channel=display.channel()>0?"CH "+display.channel():"HDMI";
            label(pose,buffers,channel,.5,.211,.058,.003f,0xffadc9cd);
            var frame=session==null?null:session.frame();
            if(frame==null){
                String status=session==null?display.status():session.status;
                if(status.isBlank())status="Загрузка плеера";
                label(pose,buffers,mc.font.plainSubstrByWidth(status,135),.5,.55,.058,.006f,0xffaecbd1);
            }else{
                var out=buffers.getBuffer(RenderType.entityTranslucent(frame.texture()));
                var p=pose.last();
                vertex(out,p,TelevisionSurface.LEFT_X,TelevisionSurface.BOTTOM,1,1);
                vertex(out,p,TelevisionSurface.LEFT_X,TelevisionSurface.TOP,1,0);
                vertex(out,p,TelevisionSurface.RIGHT_X,TelevisionSurface.TOP,0,0);
                vertex(out,p,TelevisionSurface.RIGHT_X,TelevisionSurface.BOTTOM,0,1);
            }
        }
        pose.popPose();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,float x,float y,int u,int v){
        out.addVertex(pose.pose(),x,y,TelevisionSurface.FRONT).setColor(255,255,255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose,0,0,-1);
    }

    private static void label(PoseStack pose,MultiBufferSource buffers,String text,double x,double y,double z,float scale,int color){
        var font=Minecraft.getInstance().font;
        pose.pushPose();pose.translate(x,y,z);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(scale,-scale,scale);
        font.drawInBatch(text,-font.width(text)/2f,0,color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xf000f0);
        pose.popPose();
    }
}
