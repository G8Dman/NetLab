package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class MobileEntity extends DeviceEntity {
    public ItemStack blank=ItemStack.EMPTY,output=ItemStack.EMPTY;
    public SimCredentials pending;
    public String pendingCore="",phase="",last="Готово";
    public transient boolean issuing;
    public MobileEntity(BlockPos p,BlockState s){super(Mobile.ENTITY.get(),p,s);MobileHardware.initialize(device);device.hostname=switch(device.kind){case MobileHardware.BBU->"MobileBBU";case MobileHardware.CORE->"MobileCore";case MobileHardware.RRU->"MobileRRU";case MobileHardware.STATION->"SIMStation";default->"SectorAntenna";};new MobileState().save(device);}
    @Override public void onLoad(){super.onLoad();if(level instanceof net.minecraft.server.level.ServerLevel server){if(getBlockState().getBlock() instanceof SectorAntennaBlock antenna)antenna.ensureUpper(server,worldPosition,getBlockState());MobileChunks.register(server,worldPosition);}}
    public CompoundTag physical(HolderLookup.Provider regs){var tag=new CompoundTag();if(!blank.isEmpty())tag.put("blank",blank.save(regs));if(!output.isEmpty())tag.put("output",output.save(regs));if(pending!=null)StoredJson.put(tag,"pending",MobileState.JSON.toJson(pending));tag.putString("pendingCore",pendingCore);tag.putString("phase",phase);return tag;}
    public void physical(CompoundTag tag,HolderLookup.Provider regs){blank=ItemStack.parseOptional(regs,tag.getCompound("blank"));output=ItemStack.parseOptional(regs,tag.getCompound("output"));pending=tag.contains("pending")?MobileState.JSON.fromJson(StoredJson.get(tag,"pending"),SimCredentials.class):null;pendingCore=tag.getString("pendingCore");phase=tag.getString("phase");}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider regs){super.saveAdditional(tag,regs);tag.put("mobilePhysical",physical(regs));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider regs){super.loadAdditional(tag,regs);physical(tag.getCompound("mobilePhysical"),regs);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider regs){var tag=super.getUpdateTag(regs);tag.remove("mobilePhysical");return tag;}
    public ItemStack configuredItem(){var item=MemoryCards.deviceItem(getBlockState().getBlock(),device);var tag=MobileItems.privateTag(item);tag.put("mobilePhysical",physical(level.registryAccess()));MobileItems.seal(item,tag);return item;}
}
