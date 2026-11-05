import dev.netlab.sputnik.client.NativeBrowserLibraries;
import java.nio.file.Path;

/** Native linking only: no Minecraft, CEF startup, browser, server, Level or GameTest. */
public final class SputnikNativeRuntimeCheck {
    public static void main(String[] args) {
        if (args.length < 1) throw new IllegalArgumentException("Pass the installed JCEF native directory");
        if (args.length == 1 || !args[1].equals("without-preload")) {
            NativeBrowserLibraries.prepare();
            NativeBrowserLibraries.prepare(); // repeated setup must remain safe
        }
        Path nativeDir = Path.of(args[0]).toAbsolutePath();
        System.load(nativeDir.resolve("libcef.so").toString());
        System.load(nativeDir.resolve("libjcef.so").toString());
        System.out.println("JCEF native linking passed; Minecraft and Chromium were not started.");
    }
}
