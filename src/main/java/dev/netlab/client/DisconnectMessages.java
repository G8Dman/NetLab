package dev.netlab.client;

import java.util.Locale;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;

/** Presentation only: never changes authentication, connection policy or reports. */
public final class DisconnectMessages {
    private DisconnectMessages() {}

    public static DisconnectionDetails readable(DisconnectionDetails details) {
        String text = details.reason().getString().toLowerCase(Locale.ROOT);
        boolean html = text.contains("<!doctype html") || text.contains("<html")
                || text.contains("<head>") || text.contains("<body>");
        boolean unavailable = text.contains("service unavailable")
                || text.contains("the request is blocked");
        if (!html || !unavailable) return details;
        return new DisconnectionDetails(
                Component.literal("Арабская ошибка, перезайти в лаунчер"),
                details.report(), details.bugReportLink());
    }
}
