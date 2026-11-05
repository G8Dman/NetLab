package dev.netlab.automation.client;

import dev.netlab.automation.*;
import dev.netlab.client.CableRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=Automation.ID,value=Dist.CLIENT)
public final class AutomationClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(Automation.ENTITY.get(),CableRenderer::new);if(dev.netlab.client.DesktopApps.APPS.stream().noneMatch(a->a.module().equals("CISCO-AUTOMATION")))dev.netlab.client.DesktopApps.APPS.add(new dev.netlab.client.DesktopApps.App("CISCO-AUTOMATION","Управление мультиинтерфейсами",(pos,parent)->Minecraft.getInstance().setScreen(new ManagementScreen(pos,parent))));}
    public static void management(ManagementPackets.State message){var mc=Minecraft.getInstance();if(mc.screen instanceof ManagementScreen screen&&screen.pc.equals(message.pc()))screen.update(message.json());}
    public static void receive(AutomationPackets.State message){var mc=Minecraft.getInstance();
        if(mc.screen instanceof AutomationScreen screen&&screen.pos.equals(message.pos()))screen.update(message.json());
        else if(message.open())mc.setScreen(new AutomationScreen(message.pos(),message.json()));
    }
}
