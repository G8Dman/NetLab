package dev.netlab.client;

import dev.netlab.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

public final class DiagnosticsOverlay {
    public static boolean wearing(){var p=Minecraft.getInstance().player;return p!=null&&p.getItemBySlot(EquipmentSlot.HEAD).is(NetLab.GOGGLES.get());}
    public static String endpoint(DeviceEntity d,int port){var target=d.portDevice(port);return (target==null?d.device.hostname:target.hostname)+" : "+d.portLabel(port)+" ("+d.getBlockPos().toShortString()+")";}
    public static void render(GuiGraphics g,DeltaTracker delta){
        var mc=Minecraft.getInstance();if(!wearing()||mc.screen!=null||mc.options.hideGui)return;
        List<String> lines=new ArrayList<>();int color=0x74d8ef;
        var hit=CableGeometry.pick(mc.player);
        if(hit!=null){
            var d=hit.device();var peer=d.links.get(hit.port());color=peer.color();
            lines.add("NETLAB / "+(peer.name().isBlank()?"Кабель":peer.name()));
            lines.add("A: "+endpoint(d,hit.port()));
            if(mc.level.getBlockEntity(BlockPos.of(peer.pos())) instanceof DeviceEntity other)lines.add("B: "+endpoint(other,peer.port()));
            if(StudyClient.visible){
                double now=StudyClient.time();var active=StudyClient.flights().stream().filter(f->{var e=f.event();return now>=f.start()&&now<=f.start()+f.duration()&&(e.from()==d.getBlockPos().asLong()&&e.fromPort()==hit.port()&&e.to()==peer.pos()&&e.toPort()==peer.port()||e.to()==d.getBlockPos().asLong()&&e.toPort()==hit.port()&&e.from()==peer.pos()&&e.fromPort()==peer.port());}).sorted(Comparator.comparingInt(f->"ITEM_DATA".equals(f.event().phase())?0:1)).findFirst();
                if(active.isPresent()){
                    var flight=active.get();var e=flight.event();String phase=e.phase()==null?"":e.phase();
                    lines.add("Пакет: "+e.protocol()+(phase.isBlank()?"":" / "+switch(phase){case "ITEM_DATA"->"предметы";case "FLUID_DATA"->"жидкость";case "VPN"->"VPN, содержимое скрыто";case "OFFER"->"предложение передачи";default->phase;}));
                    if("FLUID_DATA".equals(phase)&&e.item()!=null){var id=net.minecraft.resources.ResourceLocation.tryParse(e.item());if(id!=null&&net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id))lines.add(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id),e.count()).getHoverName().getString()+" / "+e.count()+" mB");}
                    if("ITEM_DATA".equals(phase)&&e.item()!=null){var id=net.minecraft.resources.ResourceLocation.tryParse(e.item());if(id!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))lines.add(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).getHoverName().getString()+" × "+e.count());}
                    lines.add(String.format(Locale.ROOT,"До конца участка: %.3f с",Math.max(0,flight.start()+flight.duration()-now)));
                }
            }
            lines.add("ПКМ: краситель или подписанная бирка");
        }else if(mc.hitResult instanceof BlockHitResult block&&mc.level.getBlockEntity(block.getBlockPos()) instanceof DeviceEntity d){
            int selected=CableGeometry.clickedPort(d,block,mc.player);
            int offset=selected>=0?selected/128*128:((DeviceBlock)d.getBlockState().getBlock()).rack&&block.getLocation().y-block.getBlockPos().getY()>=.5?128:0;
            var device=d.portDevice(offset);
            if(device==null||device.kind.equals("rack")){lines.add("NETLAB / пустая ячейка шкафа");}
            else{
                lines.add("NETLAB / "+device.hostname);
                String extra=FrontPanel.extraStatus(d,offset/128);if(!extra.isBlank())lines.add(extra);
                if(selected>=0&&dev.netlab.core.PortLayout.supported(device)){
                    var port=device.ports.get(selected%128);var peer=d.links.get(selected);
                    lines.add("Разъём: "+d.portLabel(selected));
                    lines.add(port.console?"Console / 9600 8N1":dev.netlab.core.CableKind.medium(device,selected%128)+" / "+port.maxMbps+" Mbps");
                    lines.add(peer==null?"Свободен":peer.name().isBlank()?"Кабель подключён":peer.name());
                    if(peer!=null&&mc.level.getBlockEntity(BlockPos.of(peer.pos())) instanceof DeviceEntity other)lines.add("К: "+endpoint(other,peer.port()));
                    if(!port.up)lines.add("[shutdown]");
                }else{
                    int shown=0,total=0;
                    for(int i=0;i<device.ports.size();i++){
                        var port=device.ports.get(i);if(port.hidden||port.virtual)continue;total++;if(shown++>=8)continue;
                        if(port.medium.equals("cellular")){lines.add(d.portLabel(offset+i)+" — "+(device.kind.equals("pc")?"сотовый модем":"логический шлюз")+" (без кабеля)");continue;}
                        if(port.medium.equals("wireless")){
                            var wifi=dev.netlab.core.Wireless.state(device);boolean ap=dev.netlab.core.Wireless.accessPoint(device);
                            lines.add(d.portLabel(offset+i)+" — Wi-Fi "+(ap?wifi.ssid:wifi.clientSsid)+" / "+(ap?(wifi.enabled&&dev.netlab.core.Hardware.powered(device)?"радио включено":"радио выключено"):wifi.associated==Long.MIN_VALUE?"нет связи":"подключено"));continue;
                        }
                        var peer=d.links.get(offset+i);lines.add(d.portLabel(offset+i)+" — "+(peer==null?"свободен":peer.name().isBlank()?"подключён":peer.name())+(port.up?"":" [shutdown]"));
                    }
                    if(total>8)lines.add("Ещё портов: "+(total-8)+". Наведи прицел на разъём.");
                }
            }
        }else{lines.add("NETLAB / диагностические очки");lines.add("Наведи прицел на кабель или устройство");}
        int w=Math.max(80,Math.min(420,g.guiWidth()-24));
        var wrapped=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:lines)wrapped.addAll(mc.font.split(Component.literal(line),w-16));
        int h=wrapped.size()*(mc.font.lineHeight+2)+16;g.fill(8,8,w+8,h+8,0xe609121a);g.fill(8,8,10,h+8,0xff000000|color);
        int y=16;for(var line:wrapped){g.drawString(mc.font,line,16,y,0xffddf4f8,false);y+=mc.font.lineHeight+2;}
    }
}
