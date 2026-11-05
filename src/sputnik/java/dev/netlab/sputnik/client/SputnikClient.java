package dev.netlab.sputnik.client;
import dev.netlab.sputnik.*;
import dev.netlab.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid=Sputnik.ID,value=Dist.CLIENT)
public final class SputnikClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(Sputnik.ENTITY.get(),CableRenderer::new);for(String c:Sputnik.CABLES.keySet())CableTextures.register(c,ResourceLocation.fromNamespaceAndPath(Sputnik.ID,"textures/cable/"+c+".png"));DesktopApps.APPS.add(new DesktopApps.App("","Браузер",(pos,parent)->{Minecraft.getInstance().setScreen(new BrowserScreen(pos,parent));PacketDistributor.sendToServer(new SputnikPackets.Action(pos,0,"browser",""));}));}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=Minecraft.getInstance();SpeakerAudio.tick();if(mc.getConnection()==null||mc.level==null){if(BrowserSession.current!=null){BrowserSession.current.close();BrowserSession.current=null;}return;}if(BrowserSession.current!=null)BrowserSession.current.tick();}
    public static void state(SputnikPackets.State q){var mc=Minecraft.getInstance();var state=com.google.gson.JsonParser.parseString(q.json()).getAsJsonObject();if(state.get("kind").getAsString().equals("pc")){if(BrowserSession.current!=null&&BrowserSession.current.pc.equals(q.pos()))BrowserSession.current.status=state.get("status").getAsString();return;}if(mc.screen instanceof SputnikScreen screen&&screen.pos.equals(q.pos())&&screen.bay==q.bay())screen.update(q.json());else if(q.open())mc.setScreen(new SputnikScreen(q.pos(),q.bay(),q.json(),mc.screen));}
}
