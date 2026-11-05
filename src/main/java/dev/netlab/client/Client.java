package dev.netlab.client;

import dev.netlab.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.*;

@Mod(value=NetLab.ID,dist=Dist.CLIENT)
@EventBusSubscriber(modid=NetLab.ID,value=Dist.CLIENT)
public final class Client {
    public static void terminal(TerminalPackets.Data data){
        var mc=Minecraft.getInstance();
        if(data.open()){var screen=new ConsoleScreen(data.pos(),"");mc.setScreen(screen);screen.terminal(data);}
        else if(mc.screen instanceof ConsoleScreen screen&&screen.pos.equals(data.pos())&&screen.token().equals(data.token()))screen.terminal(data);
    }
    public static void openGuide(){Minecraft.getInstance().setScreen(new GuideBookScreen());}
    public Client(ModContainer c){c.registerExtensionPoint(IConfigScreenFactory.class,ConfigurationScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(NetLab.DEVICE.get(),CableRenderer::new);}
    @SubscribeEvent public static void menus(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(NetLab.MODULE_ASSEMBLER_MENU.get(),ModuleAssemblerScreen::new);}
    @SubscribeEvent public static void gui(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event){
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NetLab.ID,"diagnostics"),DiagnosticsOverlay::render);
    }
    public static void complete(Packets.Completed msg){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof ConsoleScreen screen&&screen.pos.equals(msg.pos()))screen.complete(msg);
    }
    public static void panel(Packets.PanelData msg){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof FrontPanelScreen screen&&screen.pos.equals(msg.pos())&&screen.bay==msg.bay())screen.update(msg.json());
        else if(msg.open())mc.setScreen(new FrontPanelScreen(msg.pos(),msg.bay(),msg.json(),mc.screen));
    }
    public static void wifi(Packets.WifiData msg){
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof WirelessScreen screen&&screen.pos.equals(msg.pos())&&screen.bay==msg.bay())screen.update(msg.json());
        else if(msg.open())mc.setScreen(new WirelessScreen(msg.pos(),msg.bay(),msg.json(),mc.screen));
    }
    public static void receive(Packets.Output msg) {
        Minecraft mc=Minecraft.getInstance();
        if(msg.text().equals("__netlab_study")){mc.setScreen(new StudyScreen());return;}
        if(msg.open()) {
            if(mc.level != null && mc.level.getBlockEntity(msg.pos()) instanceof DeviceEntity d && d.device.kind.equals("pc"))
                mc.setScreen(new PCDesktopScreen(msg.pos(),msg.text(),d.device));
            else mc.setScreen(new ConsoleScreen(msg.pos(),msg.text()));
        }
        else if(mc.screen instanceof ConsoleScreen screen && !screen.isTerminal()&&screen.pos.equals(msg.pos())) screen.append(msg.text());
    }
}
