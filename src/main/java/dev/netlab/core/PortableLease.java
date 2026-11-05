package dev.netlab.core;

import java.util.UUID;

/** Portable authorization exists only for this opening and cannot be resumed after close/reboot. */
public final class PortableLease {
    public final String token="p:"+UUID.randomUUID();
    public final Console cli;
    private final String identity;
    private final long epoch;
    private long touched;
    private boolean closed;
    public PortableLease(Device device,long now,Console cli){this.cli=cli;identity=device.identity;epoch=Hardware.ensure(device).epoch;touched=now;}
    public void touch(long now){touched=now;}
    public boolean valid(Device device,boolean held,double distanceSquared,long now){return !closed&&held&&distanceSquared<=64&&distanceSquared>=0&&now>=touched&&now-touched<6000&&device.identity.equals(identity)&&Hardware.powered(device)&&Hardware.ensure(device).epoch==epoch;}
    public void close(){closed=true;cli.history.clear();}
}
