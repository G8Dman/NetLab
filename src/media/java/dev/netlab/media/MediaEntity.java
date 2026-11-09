package dev.netlab.media;
import dev.netlab.*;
import dev.netlab.media.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class MediaEntity extends DeviceEntity {
    public MediaEntity(BlockPos pos,BlockState state){super(Media.ENTITY.get(),pos,state);MediaHardware.initialize(device);MediaState.initial(device).save(device);}
}
