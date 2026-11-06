package dev.netlab.mobile.client;

import dev.netlab.mobile.*;
import dev.netlab.mobile.core.MobileHardware;
import dev.netlab.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=Mobile.ID,value=Dist.CLIENT)
public final class MobileClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(Mobile.ENTITY.get(),CableRenderer::new);CableTextures.register("rf",ResourceLocation.fromNamespaceAndPath(Mobile.ID,"textures/cable/rf.png"));if(DesktopApps.APPS.stream().noneMatch(a->a.module().equals(MobileHardware.MODEM)))DesktopApps.APPS.add(new DesktopApps.App(MobileHardware.MODEM,"Мобильная сеть",(pos,parent)->{Minecraft.getInstance().setScreen(new MobileScreen(pos,0,"","{}",parent));PacketDistributor.sendToServer(new MobilePackets.Action(pos,0,"","refresh",""));}));}
    public static void receive(MobilePackets.State m){var mc=Minecraft.getInstance();if(!m.phone().isEmpty()){if(mc.screen instanceof PhoneScreen screen&&screen.phone.equals(m.phone()))screen.update(m.json());else if(m.open())mc.setScreen(new PhoneScreen(m.phone(),m.json(),mc.screen));return;}if(mc.screen instanceof MobileScreen screen&&screen.pos.equals(m.pos())&&screen.bay==m.bay()&&screen.phone.equals(m.phone()))screen.update(m.json());else if(m.open())mc.setScreen(new MobileScreen(m.pos(),m.bay(),m.phone(),m.json(),mc.screen));}
}
