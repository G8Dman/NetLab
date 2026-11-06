package dev.netlab.client;

import dev.netlab.Packets;
import dev.netlab.core.Device;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Desktop entry point shared by PCs and laptops. */
public final class PCDesktopScreen extends Screen {
    private final BlockPos pos;
    private final String banner;
    private Device device;
    private EditBox address, mask, gateway;
    private String status = "";
    public PCDesktopScreen(BlockPos pos, String banner, Device device) {
        super(Component.literal("NetLab PC / Laptop Desktop"));
        this.pos=pos; this.banner=banner; this.device=device;
    }
    private EditBox field(int y,String value) {
        EditBox box=new EditBox(font,width/2-40,y,190,20,Component.literal("Network setting"));
        box.setMaxLength(64); box.setValue(value); addRenderableWidget(box); return box;
    }
    @Override protected void init() {
        if(minecraft.level!=null&&minecraft.level.getBlockEntity(pos) instanceof dev.netlab.DeviceEntity e)device=e.device;
        int y=Math.max(42,(height-210)/2);int index=dev.netlab.core.Wireless.clientPort(device);
        address=field(y,device.ports.get(index).ip);
        mask=field(y+26,device.ports.get(index).mask);
        gateway=field(y+52,device.gateway);
        addRenderableWidget(Button.builder(Component.literal("Apply IPv4"),b->{
            try {
                Device.ip(address.getValue()); Device.mask(mask.getValue());
                if(!gateway.getValue().isBlank())Device.ip(gateway.getValue());
                send("ipconfig "+address.getValue()+" "+mask.getValue()+(gateway.getValue().isBlank()?"":" "+gateway.getValue()));
            } catch(IllegalArgumentException e){status=e.getMessage();}
        }).bounds(width/2-150,y+80,140,20).build());
        addRenderableWidget(Button.builder(Component.literal("DHCP"),b->send("ipconfig /renew")).bounds(width/2+10,y+80,140,20).build());
        addRenderableWidget(Button.builder(Component.literal("Command Prompt"),b->minecraft.setScreen(new ConsoleScreen(pos,banner)))
            .bounds(width/2-150,y+106,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Terminal (RS232, 9600 8N1)"),b->send("__terminal"))
            .bounds(width/2+5,y+106,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("PC Wireless / Wi-Fi"),b->PacketDistributor.sendToServer(new Packets.WifiRequest(pos,0,"open","")))
            .bounds(width/2-150,y+132,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Физическая панель / модули"),b->PacketDistributor.sendToServer(new Packets.PanelRequest(pos,0,"open","","")))
            .bounds(width/2-150,y+158,300,20).build());
        var apps=DesktopApps.APPS.stream().filter(app->app.module().isBlank()||dev.netlab.core.Hardware.ensure(device).modules.containsValue(app.module())).toList();
        for(int i=0;i<apps.size();i++){var app=apps.get(i);int count=apps.size(),gap=4,appWidth=(300-gap*(count-1))/count;addRenderableWidget(Button.builder(Component.literal(app.title()),b->app.open().accept(pos,this)).bounds(width/2-150+i*(appWidth+gap),y+184,appWidth,20).build());}
    }
    private void send(String command) {
        minecraft.setScreen(new ConsoleScreen(pos,banner));
        PacketDistributor.sendToServer(new Packets.Command(pos,command));
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt) {}
    @Override public void render(GuiGraphics g,int x,int y,float dt) {
        if(minecraft.level!=null&&minecraft.level.getBlockEntity(pos) instanceof dev.netlab.DeviceEntity host)device=host.device;
        g.fill(0,0,width,height,0xff102332);
        g.drawCenteredString(font,title,width/2,15,0xffffff);
        g.drawCenteredString(font,"Адаптер: "+device.portName(dev.netlab.core.Wireless.clientPort(device)),width/2,29,0x99ddee);
        int top=Math.max(42,(height-210)/2);
        g.drawString(font,"IPv4 address",width/2-150,top+6,0xffffff);
        g.drawString(font,"Subnet mask",width/2-150,top+32,0xffffff);
        g.drawString(font,"Gateway",width/2-150,top+58,0xffffff);
        g.drawCenteredString(font,status,width/2,top+208,0xffaaaa);
        if(dev.netlab.core.LaptopBattery.laptop(device))g.drawCenteredString(font,dev.netlab.core.LaptopBattery.status(device),width/2,height-12,0x88e7a7);
        super.render(g,x,y,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
