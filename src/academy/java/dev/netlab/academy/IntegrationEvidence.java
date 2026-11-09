package dev.netlab.academy;
import dev.netlab.api.LearningEvents;
import net.minecraft.server.level.*;
import java.util.*;
/** Optional APIs are resolved only from the addon operation which has already reached its hook. */
public final class IntegrationEvidence {
    public record Manual(long amount,String inventory){}
    private static String inventory(ServerPlayer p){var text=new StringBuilder(p.containerMenu.getCarried().toString());for(var item:p.getInventory().items)text.append('|').append(item);return text.toString();}
    private static long amount(Object menu,Object key)throws ReflectiveOperationException{
        Class<?> type=Class.forName("appeng.menu.me.common.MEStorageMenu");var field=type.getDeclaredField("storage");field.setAccessible(true);Object storage=field.get(menu);var api=Class.forName("appeng.api.storage.MEStorage");Object counter=api.getMethod("getAvailableStacks").invoke(storage);return (Long)Class.forName("appeng.api.stacks.KeyCounter").getMethod("get",Class.forName("appeng.api.stacks.AEKey")).invoke(counter,key);
    }
    public static Object manualStart(Object menu,ServerPlayer p,Object key){if(!Academy.policy().ae2()||key==null)return null;try{return new Manual(amount(menu,key),inventory(p));}catch(ReflectiveOperationException e){return null;}}
    public static void manualEnd(Object menu,ServerPlayer p,Object key,Object before){if(!(before instanceof Manual sample))return;try{if(sample.amount!=amount(menu,key)&&!sample.inventory.equals(inventory(p)))LearningEvents.emit(p,"ae2.manual",null,0);}catch(ReflectiveOperationException ignored){}}
    public static void kitchen(Object building){try{
        String type=building.getClass().getSimpleName();if(!Set.of("BuildingCook","BuildingKitchen").contains(type))return;
        String pack=String.valueOf(building.getClass().getMethod("getStructurePack").invoke(building));if(!Set.of("Вкусно и точка","netlab_fastfood","netlab/netlab_fastfood").contains(pack))return;
        int level=(Integer)building.getClass().getMethod("getBuildingLevel").invoke(building);if(level<1)return;
        var colony=building.getClass().getMethod("getColony").invoke(building);Object world=Class.forName("com.minecolonies.api.colony.IColony").getMethod("getWorld").invoke(colony);if(!(world instanceof ServerLevel server))return;
        var permissions=Class.forName("com.minecolonies.api.colony.IColony").getMethod("getPermissions").invoke(colony);var owner=(UUID)Class.forName("com.minecolonies.api.colony.permissions.IPermissions").getMethod("getOwner").invoke(permissions);LearningEvents.owner(server,owner,"minecolonies.kitchen.completed",null,0);
    }catch(ReflectiveOperationException ignored){}}
    private IntegrationEvidence(){}
}
