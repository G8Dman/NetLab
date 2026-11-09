package dev.netlab.media.client;
import dev.netlab.media.*;
import dev.netlab.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid=Media.ID,value=Dist.CLIENT)
public final class MediaClient {
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->dev.netlab.sputnik.SputnikPackets.EXTRA_REPLIES.add(MediaVideoSession::receive));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(Media.ENTITY.get(),MediaRenderer::new);DesktopApps.APPS.add(new DesktopApps.App("","IPTV / АТС",(pos,parent)->{var mc=Minecraft.getInstance();mc.setScreen(new MediaScreen(pos,0,"{\"kind\":\"pc\",\"name\":\"NetLab Media\"}",parent));PacketDistributor.sendToServer(new MediaPackets.Action(pos,0,"app",""));}));DesktopApps.APPS.add(new DesktopApps.App("CISCO-POCKET-SIP","IP-телефония",(pos,parent)->{Minecraft.getInstance().setScreen(new MediaScreen(pos,0,"{\"kind\":\"pc\",\"name\":\"Cisco Pocket\"}",parent));PacketDistributor.sendToServer(new MediaPackets.Action(pos,0,"app",""));}));}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level==null||mc.getConnection()==null){MediaVideoSession.clear();return;}for(var session:java.util.List.copyOf(MediaVideoSession.SESSIONS.values()))session.tick();}
    public static void state(MediaPackets.State q){var mc=Minecraft.getInstance();if(mc.screen instanceof MediaScreen s&&s.pos.equals(q.pos())&&s.bay==q.bay())s.update(q.json());else if(q.open())mc.setScreen(new MediaScreen(q.pos(),q.bay(),q.json(),mc.screen));}
    public static void scene(MediaPackets.Scene q){try{MediaVideoSession.scene(com.google.gson.JsonParser.parseString(q.json()).getAsJsonArray());}catch(RuntimeException error){com.mojang.logging.LogUtils.getLogger().warn("Media scene",error);}}
}
