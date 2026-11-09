package dev.netlab.media.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.netlab.*;
import dev.netlab.client.CableRenderer;
import dev.netlab.media.core.MediaHardware;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
public final class MediaRenderer implements BlockEntityRenderer<DeviceEntity> {
    private final CableRenderer cables;
    public MediaRenderer(BlockEntityRendererProvider.Context context){cables=new CableRenderer(context);}
    @Override public void render(DeviceEntity h,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){cables.render(h,tick,pose,buffers,light,overlay);if(!h.device.kind.equals(MediaHardware.TV))return;var session=MediaVideoSession.SESSIONS.get(h.getBlockPos().asLong());if(session==null||session.browser==null)return;var frame=session.browser.frameHandle();if(frame==null)return;
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-90*DeviceBlock.turns(h.getBlockState())));pose.translate(-.5,0,-.5);var out=buffers.getBuffer(RenderType.entityTranslucent(frame.texture()));var m=pose.last();float x=.06f,y=.28f,w=.88f,height=.495f,z=.060f;
        out.addVertex(m.pose(),x,y,z).setColor(255,255,255,255).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(m,0,0,-1);
        out.addVertex(m.pose(),x,y+height,z).setColor(255,255,255,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(m,0,0,-1);
        out.addVertex(m.pose(),x+w,y+height,z).setColor(255,255,255,255).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(m,0,0,-1);
        out.addVertex(m.pose(),x+w,y,z).setColor(255,255,255,255).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(m,0,0,-1);pose.popPose();}
}
