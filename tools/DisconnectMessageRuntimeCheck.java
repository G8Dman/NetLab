import dev.netlab.client.DisconnectMessages;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Real text components and report records; no screen, client, server or world. */
public final class DisconnectMessageRuntimeCheck {
    private static int checks;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        String expected = "Арабская ошибка, перезайти в лаунчер";
        var report = Optional.of(Path.of("reports", "disconnect.txt"));
        var link = Optional.of(URI.create("https://example.invalid/report"));
        String[] pages = {
            "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Transitional//EN'><html><head><title>Service unavailable</title></head><body>The request is blocked.</body></html>",
            "Failed to read value <HTML><HEAD><TITLE>SERVICE UNAVAILABLE</TITLE></HEAD></HTML>",
            "<body>The request is blocked.</body>",
            "<html><head><meta content='text/html; charset=utf-8' http-equiv='content-type' /><style type='text/css'>body {font-family: Arial;}</style><title>Service unavailable</title></head></html>"
        };
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.ROOT, Locale.forLanguageTag("ru"), Locale.forLanguageTag("tr")}) {
                Locale.setDefault(locale);
                for (String html : pages) {
                    var original = new DisconnectionDetails(Component.literal(html), report, link);
                    var result = DisconnectMessages.readable(original);
                    check(result.reason().getString().equals(expected), "readable HTML failure in " + locale);
                    check(result.report().equals(report) && result.bugReportLink().equals(link), "diagnostic links retained");
                    check(original.reason().getString().equals(html), "input component untouched");
                    check(DisconnectMessages.readable(result) == result, "replacement is idempotent");
                }
            }
        } finally { Locale.setDefault(previous); }
        for (String reason : new String[] {"Превышено время ожидания", "Connection refused", "Invalid session", "Incompatible mods", "You are banned", "Service unavailable", "The request is blocked", "<html><title>Different failure</title></html>", ""}) {
            var original = new DisconnectionDetails(Component.literal(reason));
            check(DisconnectMessages.readable(original) == original, "ordinary failure preserved: " + reason);
        }
        var split = Component.literal("<html><title>Service ").append(Component.literal("unavailable</title></html>"));
        check(DisconnectMessages.readable(new DisconnectionDetails(split)).reason().getString().equals(expected), "component siblings supported");
        check(DisconnectMessages.readable(new DisconnectionDetails(Component.literal(pages[0]))).report().isEmpty(), "no report invented");
        var config = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of("src/main/resources/netlab.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("client").asList().stream().anyMatch(v -> v.getAsString().equals("DisconnectedScreenMixin")), "hook registered on client only");
        check(config.getAsJsonArray("mixins").asList().stream().noneMatch(v -> v.getAsString().equals("DisconnectedScreenMixin")), "server never loads GUI hook");
        // Inspect target bytes without constructing or initializing the game screen.
        var target = new org.objectweb.asm.tree.ClassNode();
        try (var stream = DisconnectMessageRuntimeCheck.class.getResourceAsStream("/net/minecraft/client/gui/screens/DisconnectedScreen.class")) {
            new org.objectweb.asm.ClassReader(stream).accept(target, 0);
        }
        check(target.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals("(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/DisconnectionDetails;Lnet/minecraft/network/chat/Component;)V")), "real 1.21.1 constructor matches injection target");
        check(target.fields.stream().anyMatch(f -> f.name.equals("details") && f.desc.equals("Lnet/minecraft/network/DisconnectionDetails;")), "real shadow field exists");
        System.out.println(checks + " disconnect-message checks passed; no game, screen, server or world launched.");
    }
}
