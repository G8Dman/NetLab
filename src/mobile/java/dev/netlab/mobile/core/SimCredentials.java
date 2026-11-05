package dev.netlab.mobile.core;

import java.util.UUID;

/** Keys exist only in server-persistent item/device data. */
public final class SimCredentials {
    public String id=UUID.randomUUID().toString(),key=MobileAuth.secret(),issuer="",operator="",apn="netlab",label="SIM";
}
