package dev.netlab.compat;

import dev.netlab.DeviceBlock;
import dev.netlab.DeviceEntity;
import dev.netlab.api.RackDevices;
import net.minecraft.server.level.ServerPlayer;

/** Carry On may move the whole physical rack, never endpoints of live cables. */
public final class RackCarry {
    private RackCarry() {}
    public static boolean rack(DeviceEntity host) {
        return host.getBlockState().getBlock() instanceof DeviceBlock block && block.rack;
    }
    public static boolean connected(DeviceEntity host) {
        return !host.links.isEmpty() || !host.powerLinks.isEmpty();
    }
    public static String refusal(ServerPlayer player, DeviceEntity host) {
        if (!rack(host)) return null;
        if (connected(host)) return "Сначала отключи от шкафа все сетевые, консольные и силовые кабели.";
        for (int bay=0; bay<2; bay++) {
            var device=bay==0?host.device:host.upperDevice;
            if (device!=null && !device.kind.equals("rack") && !RackDevices.allowed(player,host,bay))
                return "Нет доступа к одному из устройств шкафа.";
        }
        return null;
    }
}
