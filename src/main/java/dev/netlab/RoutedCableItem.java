package dev.netlab;

import dev.netlab.core.WallCablePath;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** One interaction and preview implementation for every physical cable. */
public abstract class RoutedCableItem extends Item {
    protected record Connection(boolean success,String message){}
    private record Pending(ResourceKey<Level> level,BlockPos pos,int port,String identity,List<WallCablePath.Point> anchors,long touched,int axis){}
    private final Map<UUID,Pending> pending=new HashMap<>();
    protected RoutedCableItem(Properties properties){super(properties);}
    protected abstract int pick(DeviceEntity host,BlockHitResult hit,Player player);
    protected abstract String identity(DeviceEntity host,int port);
    protected abstract boolean occupied(DeviceEntity host,int port);
    protected abstract void disconnect(DeviceEntity host,int port);
    protected abstract List<WallCablePath.Point> path(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors);
    protected abstract Connection connect(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors);
    protected abstract String portHint();
    protected abstract int color();
    public final void clear(){pending.clear();}
    public final void clear(UUID player){pending.remove(player);}
    private static void message(Player player,String text){player.displayClientMessage(Component.literal(text),true);}
    private DeviceEntity source(Level level,Pending first){
        return level.hasChunkAt(first.pos)&&level.getBlockEntity(first.pos) instanceof DeviceEntity h
            &&identity(h,first.port).equals(first.identity)&&!occupied(h,first.port)?h:null;
    }
    public final void preview(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%10!=0)return;
        int rgb=color();var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f),.7f);
        var iterator=pending.entrySet().iterator();while(iterator.hasNext()){
            var entry=iterator.next();var player=event.getServer().getPlayerList().getPlayer(entry.getKey());var first=entry.getValue();
            if(player==null||!player.level().dimension().equals(first.level)||player.level().getGameTime()<first.touched||player.level().getGameTime()-first.touched>12000){iterator.remove();continue;}
            var level=player.serverLevel();if(!level.hasChunkAt(first.pos))continue;
            var h=source(level,first);if(h==null){iterator.remove();continue;}
            if(player.getMainHandItem().getItem()!=this&&player.getOffhandItem().getItem()!=this)continue;
            var path=path(h,first.port,h,first.port,first.anchors);
            for(int i=1;i<path.size()-2;i++){
                var a=path.get(i-1);var b=path.get(i);int steps=Math.max(1,(int)Math.ceil(a.distance(b)/.5));
                for(int step=0;step<=steps;step++){double t=step/(double)steps;
                    level.sendParticles(player,dust,true,a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,1,0,0,0,0);
                }
            }
        }
    }
    @Override public final InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();if(player==null)return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(!player.mayBuild())return InteractionResult.CONSUME;
        long now=level.getGameTime();pending.values().removeIf(first->now<first.touched||now-first.touched>12000);
        var h=level.getBlockEntity(context.getClickedPos()) instanceof DeviceEntity d?d:null;
        var hit=new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),false);
        int port=h==null?-1:pick(h,hit,player);
        if(player.isShiftKeyDown()){
            clear(player.getUUID());if(port>=0)disconnect(h,port);
            message(player,port>=0?"Кабель отключён; выбор очищен.":"Прокладка отменена; выбор очищен.");return InteractionResult.CONSUME;
        }
        var first=pending.get(player.getUUID());
        if(first!=null&&!first.level.equals(level.dimension())){clear(player.getUUID());first=null;}
        if(port<0){
            if(h!=null){message(player,portHint());return InteractionResult.CONSUME;}
            if(first==null){message(player,portHint()+" Затем ПКМ по стенам — крепления.");return InteractionResult.CONSUME;}
            var start=source(level,first);if(start==null){clear(player.getUUID());message(player,"Начальный разъём выгружен, заменён или занят. Начни маршрут заново.");return InteractionResult.CONSUME;}
            if(level.getBlockState(context.getClickedPos()).getCollisionShape(level,context.getClickedPos()).isEmpty()){
                message(player,"Крепление нужно ставить на поверхность твёрдого блока.");return InteractionResult.CONSUME;
            }
            var at=context.getClickLocation();var normal=context.getClickedFace().getNormal();
            var next=WallCablePath.surface(at.x,at.y,at.z,normal.getX(),normal.getY(),normal.getZ());
            var anchors=new ArrayList<>(first.anchors);
            if(!anchors.isEmpty()&&anchors.getLast().distance(next)<.04){message(player,"Это крепление уже выбрано.");return InteractionResult.CONSUME;}
            int axis=context.getClickedFace().getAxis().ordinal();
            if(!anchors.isEmpty()&&first.axis>=0){
                var bend=WallCablePath.bend(anchors.getLast(),first.axis,next,axis);
                if(bend!=null&&bend.distance(anchors.getLast())>.001&&bend.distance(next)>.001)anchors.add(bend);
            }
            if(anchors.size()>=WallCablePath.MAX_ANCHORS){message(player,"Максимум 32 крепления с учётом углов. Заверши маршрут или отмени Shift+ПКМ.");return InteractionResult.CONSUME;}
            anchors.add(next);var path=path(start,first.port,start,first.port,anchors);
            if(!WallCablePath.valid(path.subList(0,path.size()-2),NetLab.MAX_LENGTH.get())){message(player,"Маршрут превышает максимальную длину кабеля.");return InteractionResult.CONSUME;}
            pending.put(player.getUUID(),new Pending(first.level,first.pos,first.port,first.identity,List.copyOf(anchors),now,axis));
            message(player,"Крепление "+anchors.size()+" / 32. Продолжи по стене или нажми конечный порт. Shift+ПКМ — отмена.");return InteractionResult.CONSUME;
        }
        if(first==null){
            if(occupied(h,port)){message(player,"Разъём занят. Shift+ПКМ кабелем — отключить.");return InteractionResult.CONSUME;}
            pending.put(player.getUUID(),new Pending(level.dimension(),h.getBlockPos(),port,identity(h,port),List.of(),now,-1));
            message(player,"Выбран порт. ПКМ по стенам — крепления; ПКМ по другому порту — подключение. Shift+ПКМ — отмена.");return InteractionResult.CONSUME;
        }
        var start=source(level,first);
        if(start==null){clear(player.getUUID());message(player,"Начальный разъём выгружен, заменён или занят. Начни маршрут заново.");return InteractionResult.CONSUME;}
        var result=connect(start,first.port,h,port,first.anchors);if(result.success)clear(player.getUUID());message(player,result.message);
        return InteractionResult.CONSUME;
    }
    @Override public final InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide){if(player.isShiftKeyDown()){clear(player.getUUID());message(player,"Прокладка отменена.");}else message(player,"ПКМ: порт → крепления на стенах → порт. Shift+ПКМ: отмена или отключение порта.");}
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal("ПКМ: порт → точки на стенах → порт"));lines.add(Component.literal("Shift+ПКМ: отменить / отключить порт"));lines.add(Component.literal("До 32 креплений; маршрут сохраняется в мире"));
    }
}
