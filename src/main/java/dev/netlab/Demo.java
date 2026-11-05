package dev.netlab;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** Explicit opt-in example topology; refuses to replace existing blocks. */
public final class Demo {
    public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("study").executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new Packets.Output(player.blockPosition(),"__netlab_study",true));return 1;
        })));
        e.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("demo").requires(s->s.hasPermission(2)).executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();var level=player.serverLevel();BlockPos start=player.blockPosition().offset(0,0,3);
            var positions=new ArrayList<BlockPos>();for(int i=0;i<4;i++)positions.add(start.offset(i*3,0,0));
            var reserved=new ArrayList<>(positions);reserved.add(start.offset(6,0,3));reserved.add(start.offset(7,0,3));
            for(var pos:reserved)if(!level.hasChunkAt(pos)||!level.isEmptyBlock(pos)) {ctx.getSource().sendFailure(Component.literal("Need four empty loaded positions in front (+Z, spaced 3 blocks along +X)."));return 0;}
            var blocks=List.of(NetLab.PC.get(),NetLab.SWITCH.get(),NetLab.RACK_ROUTER.get(),NetLab.LAPTOP.get());
            for(int i=0;i<4;i++)level.setBlockAndUpdate(positions.get(i),blocks.get(i).defaultBlockState());
            DeviceEntity[] d=new DeviceEntity[4];for(int i=0;i<4;i++)d[i]=(DeviceEntity)level.getBlockEntity(positions.get(i));
            d[0].device.hostname="PC-A";d[1].device.hostname="SW1";d[2].device.hostname="R1";d[3].device.hostname="PC-B";
            configure(d[0],0,"192.168.10.2");d[0].device.gateway="192.168.10.1";
            configure(d[2],0,"192.168.10.1");configure(d[2],1,"192.168.20.1");
            configure(d[3],0,"192.168.20.2");d[3].device.gateway="192.168.20.1";
            link(d[0],0,d[1],0);link(d[1],1,d[2],0);link(d[2],1,d[3],0);
            PowerSystem.demo(level,d[2],start.offset(6,0,3),start.offset(7,0,3),List.of(Map.entry(d[2],0),Map.entry(d[1],0)));
            for(var device:d){MemoryCards.demo(device.device);device.changed();}
            ctx.getSource().sendSuccess(()->Component.literal("NetLab demo placed at "+start.toShortString()+". Right-click PC-A with an empty hand: ping 192.168.20.2. Use creative tab NetLab for more devices."),false);
            return Command.SINGLE_SUCCESS;
        })));
    }
    private static void configure(DeviceEntity d,int port,String ip){var p=d.device.ports.get(port);p.ip=ip;p.up=true;}
    private static void link(DeviceEntity a,int ap,DeviceEntity b,int bp){a.links.put(ap,new DeviceEntity.Peer(b.getBlockPos().asLong(),bp));b.links.put(bp,new DeviceEntity.Peer(a.getBlockPos().asLong(),ap));}
}
