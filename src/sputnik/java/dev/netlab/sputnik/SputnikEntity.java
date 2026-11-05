package dev.netlab.sputnik;
import dev.netlab.*;
import dev.netlab.sputnik.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class SputnikEntity extends DeviceEntity {
    @Override public void onLoad(){super.onLoad();if(level instanceof net.minecraft.server.level.ServerLevel server){
        if(links.isEmpty()&&SputnikHardware.repairGeneric(device))changed();
        SputnikChunks.register(server,worldPosition);
    }}
    public SputnikEntity(BlockPos pos,BlockState state){super(Sputnik.ENTITY.get(),pos,state);SputnikHardware.initialize(device);SputnikState.initial(device).save(device);}
}
