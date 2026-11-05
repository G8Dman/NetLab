package dev.netlab.automation;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Generic, sided FluidHandler access. No modification of stacks returned by getFluidInTank. */
public final class MachineFluids {
    public static final int MAX_AMOUNT=16000,MAX_TANKS=32;
    public static IFluidHandler handler(AutomationNode node){var level=(ServerLevel)node.getLevel();var pos=MachineAccess.target(node);return level.hasChunkAt(pos)?level.getCapability(Capabilities.FluidHandler.BLOCK,pos,node.target.getOpposite()):null;}
    public static JsonArray snapshot(IFluidHandler handler,boolean extract){
        var out=new JsonArray();for(int i=0;i<Math.min(MAX_TANKS,handler.getTanks());i++){
            var fluid=handler.getFluidInTank(i);var row=new JsonObject();row.addProperty("tank",i);row.addProperty("fluid",BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());row.addProperty("name",fluid.isEmpty()?"Пустой бак":dev.netlab.DeviceEntity.cleanName(fluid.getHoverName().getString()));row.addProperty("amount",fluid.getAmount());row.addProperty("capacity",handler.getTankCapacity(i));row.addProperty("componentsHash",fluid.getComponents().hashCode());
            var preview=!extract||fluid.isEmpty()?FluidStack.EMPTY:handler.drain(fluid.copyWithAmount(Math.min(MAX_AMOUNT,fluid.getAmount())),SIMULATE);
            row.addProperty("drainable",FluidStack.isSameFluidSameComponents(fluid,preview)?Math.clamp(preview.getAmount(),0,Math.min(MAX_AMOUNT,fluid.getAmount())):0);out.add(row);
        }return out;
    }
    public static FluidStack preview(IFluidHandler handler,String id,int amount,Integer componentsHash){
        if(amount<1||amount>MAX_AMOUNT)throw new IllegalArgumentException("Объём: 1..16000 mB");if(handler==null)return FluidStack.EMPTY;
        for(int i=0;i<Math.min(MAX_TANKS,handler.getTanks());i++){
            var fluid=handler.getFluidInTank(i);if(fluid.isEmpty()||!BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString().equals(id)||componentsHash!=null&&fluid.getComponents().hashCode()!=componentsHash)continue;
            var requested=fluid.copyWithAmount(amount);var result=handler.drain(requested.copy(),SIMULATE);
            if(!result.isEmpty()&&FluidStack.isSameFluidSameComponents(result,requested)&&result.getAmount()<=amount)return result.copy();
        }return FluidStack.EMPTY;
    }
    public static FluidStack drain(IFluidHandler handler,FluidStack expected){
        var actual=handler.drain(expected.copy(),EXECUTE);
        if(!actual.isEmpty()&&(!FluidStack.isSameFluidSameComponents(expected,actual)||actual.getAmount()>expected.getAmount()))throw new IllegalStateException("FluidHandler нарушил контракт слива");return actual.copy();
    }
    /** Returns an independent remainder, preserving every fluid component. */
    public static FluidStack fill(IFluidHandler handler,FluidStack fluid){
        if(handler==null||fluid.isEmpty())return fluid.copy();int possible=handler.fill(fluid.copy(),SIMULATE);
        if(possible<0||possible>fluid.getAmount())throw new IllegalStateException("FluidHandler нарушил контракт заполнения");
        if(possible==0)return fluid.copy();int accepted=handler.fill(fluid.copyWithAmount(possible),EXECUTE);
        if(accepted<0||accepted>possible)throw new IllegalStateException("FluidHandler нарушил контракт заполнения");return fluid.copyWithAmount(fluid.getAmount()-accepted);
    }
}
