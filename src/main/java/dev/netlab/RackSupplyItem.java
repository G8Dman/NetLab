package dev.netlab;
import net.minecraft.world.*;import net.minecraft.world.item.*;import net.minecraft.world.item.context.UseOnContext;import net.minecraft.network.chat.Component;
public final class RackSupplyItem extends Item {
    public RackSupplyItem(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext c){var p=c.getPlayer();var l=c.getLevel();if(p==null||!(l.getBlockEntity(c.getClickedPos()) instanceof DeviceEntity h)||!((DeviceBlock)h.getBlockState().getBlock()).rack)return InteractionResult.PASS;if(!l.isClientSide&&p.mayBuild()){if(h.rackSupply)p.displayClientMessage(Component.literal("Блок питания уже установлен. Разъёмы находятся сзади внизу шкафа."),true);else{h.rackSupply=true;h.rackSupplyId=java.util.UUID.randomUUID().toString();h.changed();if(!p.isCreative())c.getItemInHand().shrink(1);p.displayClientMessage(Component.literal("Блок питания установлен отдельно от двух ячеек. Подключи задний IN к энергошкафу, OUT — к устройствам."),true);}}return InteractionResult.sidedSuccess(l.isClientSide);}
}
