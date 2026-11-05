import dev.netlab.sputnik.client.NativeBrowserLibraries;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Native linking only: no Minecraft, CEF startup, browser, server, Level or GameTest. */
public final class SputnikNativeRuntimeCheck {
    public static void main(String[] args) {
        if (args.length < 1) throw new IllegalArgumentException("Pass the installed JCEF native directory");
        if (args.length == 1 || !args[1].equals("without-preload")) {
            NativeBrowserLibraries.prepare();
            NativeBrowserLibraries.prepare(); // repeated setup must remain safe
        }
        Path nativeDir = Path.of(args[0]).toAbsolutePath();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> libraries;
        if (os.startsWith("windows")) {
            libraries = List.of("d3dcompiler_47.dll", "libGLESv2.dll", "libEGL.dll",
                    "chrome_elf.dll", "libcef.dll", "jcef.dll");
        } else if (os.contains("linux")) {
            libraries = List.of("libcef.so", "libjcef.so");
        } else throw new IllegalArgumentException("This fixture supports Windows and Linux");
        for (String library : libraries) System.load(nativeDir.resolve(library).toString());
        System.out.println("JCEF native linking passed; Minecraft and Chromium were not started.");
    }
}
