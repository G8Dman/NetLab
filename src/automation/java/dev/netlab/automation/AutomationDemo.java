package dev.netlab.automation;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.automation.core.AutomationDemoPlan;
import dev.netlab.automation.core.AutomationDemoPlan.Node;
import dev.netlab.automation.core.DemoBookPages;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** An opt-in local LAN; construction starts only after the whole empty footprint is checked. */
public final class AutomationDemo {
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("demo").requires(s->s.hasPermission(2)).then(Commands.literal("automation").executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();var l=player.serverLevel();var start=player.blockPosition().offset(-5,0,4);
            for(int x=0;x<AutomationDemoPlan.WIDTH;x++)for(int z=0;z<AutomationDemoPlan.DEPTH;z++){
                var floor=start.offset(x,-1,z);
                if(!available(l,floor)||!l.isEmptyBlock(floor)&&!l.getBlockState(floor).isFaceSturdy(l,floor,Direction.UP)){ctx.getSource().sendFailure(Component.literal("Нужно свободное загруженное место 11×7×3 блока к югу (+Z), с ровной опорой или воздухом снизу. Существующие блоки не заменяются."));return 0;}
                for(int y=0;y<AutomationDemoPlan.HEIGHT;y++){var pos=start.offset(x,y,z);if(!available(l,pos)||!l.isEmptyBlock(pos)){ctx.getSource().sendFailure(Component.literal("Стенд не построен: освободи область 11×7×3 к югу (+Z). Мешает блок "+pos.toShortString()+". Ничего не заменено."));return 0;}}
            }
            var occupied=new ArrayList<String>();for(var host:DeviceEntity.loaded(l))for(var d:Arrays.asList(host.device,host.upperDevice))if(d!=null)for(var port:d.ports)if(!port.ip.isBlank())occupied.add(port.ip);
            AutomationDemoPlan.Address a;try{a=AutomationDemoPlan.choose(occupied);}catch(IllegalArgumentException e){ctx.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
            var book=book(a,start);
            for(int x=0;x<AutomationDemoPlan.WIDTH;x++)for(int z=0;z<AutomationDemoPlan.DEPTH;z++){var floor=start.offset(x,-1,z);if(l.isEmptyBlock(floor))l.setBlockAndUpdate(floor,Blocks.SMOOTH_STONE.defaultBlockState());}
            var sourceChest=start.offset(1,0,1);var destinationChest=start.offset(9,0,1);
            l.setBlockAndUpdate(sourceChest,Blocks.CHEST.defaultBlockState());l.setBlockAndUpdate(destinationChest,Blocks.CHEST.defaultBlockState());
            ((ChestBlockEntity)l.getBlockEntity(sourceChest)).setItem(0,new ItemStack(Items.IRON_INGOT,AutomationDemoPlan.SOURCE_COUNT));
            l.getBlockEntity(sourceChest).setChanged();
            var hosts=new EnumMap<Node,DeviceEntity>(Node.class);
            var rack=place(l,start.offset(5,0,1),NetLab.RACK.get());rack.device=AutomationDemoPlan.device(Node.SERVER,a);rack.upperDevice=AutomationDemoPlan.device(Node.SWITCH,a);Hardware.ensure(rack.upperDevice);
            hosts.put(Node.SERVER,rack);hosts.put(Node.SWITCH,rack);
            hosts.put(Node.SOURCE,place(l,sourceChest.above(),Automation.INTERFACE.get()));
            hosts.put(Node.DESTINATION,place(l,destinationChest.above(),Automation.INTERFACE.get()));
            hosts.put(Node.SERVER_TERMINAL,place(l,start.offset(5,0,4),NetLab.LAPTOP.get()));
            hosts.put(Node.SOURCE_TERMINAL,place(l,start.offset(1,0,4),NetLab.LAPTOP.get()));
            hosts.put(Node.DESTINATION_TERMINAL,place(l,start.offset(9,0,4),NetLab.LAPTOP.get()));
            for(var node:Node.values()){
                var host=hosts.get(node);if(node!=Node.SERVER&&node!=Node.SWITCH)host.device=AutomationDemoPlan.device(node,a);
                var app=AutomationNode.of(host,slot(node));if(app!=null){app.owner=player.getUUID();if(node==Node.SOURCE||node==Node.DESTINATION){app.manager=a.server();app.target=Direction.DOWN;}app.changed();}
            }
            for(var w:AutomationDemoPlan.LINKS){var left=hosts.get(w.a());var right=hosts.get(w.b());int ap=128*slot(w.a())+w.ap(),bp=128*slot(w.b())+w.bp();String name=w.a()+" → "+w.b();int color=CableKind.color(w.cable());
                left.links.put(ap,new DeviceEntity.Peer(right.getBlockPos().asLong(),bp,color,name,w.cable()));right.links.put(bp,new DeviceEntity.Peer(left.getBlockPos().asLong(),ap,color,name,w.cable()));left.connectionChanged(ap);right.connectionChanged(bp);
            }
            PowerSystem.demo(l,rack,start.offset(3,0,4),start.offset(2,0,4),List.of(Map.entry(rack,0),Map.entry(rack,1),Map.entry(hosts.get(Node.SOURCE),0),Map.entry(hosts.get(Node.DESTINATION),0)));
            for(var host:new HashSet<>(hosts.values())){dev.netlab.MemoryCards.demo(host.device);if(host.upperDevice!=null)dev.netlab.MemoryCards.demo(host.upperDevice);host.changed();}
            sign(l,start.offset(1,0,5),"SOURCE","Настройка MI",a.source(),"Terminal ноутбука");
            sign(l,start.offset(5,0,5),"SERVER","inspect / transfer",a.server(),"Terminal ноутбука");
            sign(l,start.offset(9,0,5),"DESTINATION","Настройка MI",a.destination(),"Terminal ноутбука");
            l.setBlockAndUpdate(start.offset(3,2,1),Blocks.SEA_LANTERN.defaultBlockState());l.setBlockAndUpdate(start.offset(7,2,1),Blocks.SEA_LANTERN.defaultBlockState());
            if(!player.addItem(book))player.drop(book,false);
            ctx.getSource().sendSuccess(()->Component.literal("Стенд автоматики: "+start.toShortString()+". В источнике 64 железа. Все адреса уже настроены, шлюз не нужен. Открой центральный ноутбук SERVER → Terminal и выполняй команды из выданной книги. Автоперенос пока выключен."),false);
            return 1;
        }))));
    }
    private static boolean available(ServerLevel l,BlockPos p){return !l.isOutsideBuildHeight(p)&&l.getWorldBorder().isWithinBounds(p)&&l.hasChunkAt(p);}
    private static int slot(Node n){return n==Node.SWITCH?1:0;}
    private static DeviceEntity place(ServerLevel l,BlockPos p,DeviceBlock b){l.setBlockAndUpdate(p,b.defaultBlockState().setValue(DeviceBlock.FACING,Direction.NORTH));return (DeviceEntity)l.getBlockEntity(p);}
    private static void sign(ServerLevel l,BlockPos p,String... lines){l.setBlockAndUpdate(p,Blocks.OAK_SIGN.defaultBlockState());var s=(SignBlockEntity)l.getBlockEntity(p);var text=new SignText();for(int i=0;i<Math.min(4,lines.length);i++)text=text.setMessage(i,Component.literal(lines[i]));text=text.setHasGlowingText(true);s.setText(text,true);s.setText(text,false);s.setWaxed(true);s.setChanged();l.sendBlockUpdated(p,s.getBlockState(),s.getBlockState(),3);}
    public static List<Filterable<Component>> pages(AutomationDemoPlan.Address a,BlockPos start){
        var pages=new ArrayList<Filterable<Component>>();
        page(pages,"ДЕМО АВТОМАТИКИ\n\nСтенд: "+start.toShortString()+"\n\nВсе устройства настроены и запитаны. Слева источник: 64 железных слитка. Справа получатель: пустой сундук.\n\nНе вводи transfer в чат Minecraft!");
        page(pages,"ГДЕ ВВОДИТЬ\n\nЦентральный ноутбук с табличкой SERVER. ПКМ пустой рукой → Desktop → Terminal.\n\nОн уже подключён кабелем Console к серверу в нижней ячейке шкафа.");
        page(pages,"ПРИЛОЖЕНИЕ ПК\n\nНа центральном ноутбуке установлен CISCO-AUTOMATION и подключён Ethernet. В Terminal сервера: enable → configure terminal → management password СВОЙ_ПАРОЛЬ → end → memory write.\n\nЗакрой Terminal, открой «Управление мультиинтерфейсами», введи IP "+a.server()+" и свой пароль (8–64 символа). Карты памяти устройств уже установлены.");
        page(pages,"ШЛЮЗ\n\nВ этом стенде он НЕ НУЖЕН: все устройства в одной /24 и VLAN 1 одного коммутатора.\n\nШлюз нужен при связи с другой подсетью. Тогда адрес шлюза — интерфейс настоящего роутера, а не сервер.");
        commands(pages,"1. На СЕРВЕРЕ\n\nВход в привилегированный EXEC. Затем запрос состояния источника.\nНажми команду для копирования:\n\n","enable","inspect "+a.source());
        commands(pages,"2. На СЕРВЕРЕ\n\nПеренести 16 железных слитков слева направо:\n\n","transfer "+a.source()+" "+a.destination()+" minecraft:iron_ingot 16");
        commands(pages,"3. На СЕРВЕРЕ\n\nПроверь журнал:\n\n","show jobs");
        page(pages,"РЕЗУЛЬТАТ\n\nПодожди около 20 секунд после доставки OFFER плюс время сетевых запросов.\n\nВ левом сундуке останется 48 железа, в правом появится 16. Во время ожидания груз хранится в журнале, а не теряется.");
        commands(pages,"4. На СЕРВЕРЕ\n\nПроверь содержимое получателя:\n\n","inspect "+a.destination(),"show endpoints");
        commands(pages,"АВТОПРАВИЛО\n\nНа сервере: поддерживать справа запас 32, партия 8.\n\n","configure terminal","automation rule "+a.source()+" "+a.destination()+" minecraft:iron_ingot 32 8","automation run","end");
        commands(pages,"ОСТАНОВКА\n\nНа сервере:\n\n","configure terminal","automation stop","end","show rules");
        page(pages,"МУЛЬТИИНТЕРФЕЙСЫ\n\nБоковые ноутбуки SOURCE и DESTINATION подключены к своим интерфейсам. Они нужны для настройки IP, target и server-address.\n\ninspect и transfer вводятся только на центральном сервере.");
        page(pages,"АДРЕСА\n\nСервер: "+a.server()+"\nИсточник: "+a.source()+"\nПолучатель: "+a.destination()+"\nМаска всех:\n255.255.255.0\n\nШлюз пустой. VPN здесь не нужен: это одна локальная сеть.");
        return List.copyOf(pages);
    }
    public static ItemStack book(AutomationDemoPlan.Address a,BlockPos start){var book=new ItemStack(Items.WRITTEN_BOOK);book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("Автоматика: первый перенос"),"NetLab Automation",0,pages(a,start),true));return book;}
    private static void page(List<Filterable<Component>> pages,String body){append(pages,List.of(new DemoBookPages.Span(body,"")));}
    private static void commands(List<Filterable<Component>> pages,String body,String... commands){var spans=new ArrayList<DemoBookPages.Span>();spans.add(new DemoBookPages.Span(body,""));for(String command:commands)spans.add(new DemoBookPages.Span(command,command));append(pages,spans);}
    private static void append(List<Filterable<Component>> pages,List<DemoBookPages.Span> spans){
        for(var page:DemoBookPages.paginate(spans)){var text=Component.empty();for(var line:page){var part=Component.literal(line.text());if(!line.copy().isBlank())part.withStyle(s->s.withColor(net.minecraft.ChatFormatting.DARK_GREEN).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,line.copy())).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Скопировать команду целиком"))));text.append(part);}pages.add(Filterable.passThrough(text));}
    }
}
