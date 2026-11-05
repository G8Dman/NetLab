package dev.netlab.sputnik.client;

import java.util.ArrayList;
import java.util.List;

/** Checks platform selection without loading native code or starting Minecraft. */
public final class NativeBrowserLibrariesTest {
    public static int run() {
        int checks = 0;
        for (String name : List.of("Windows 10", "Windows 11", "WINDOWS 11", "Linux", "GNU/Linux")) {
            var loaded = new ArrayList<String>();
            var bootstrap = new NativeBrowserLibraries(name, loaded::add);
            bootstrap.load();
            if (!loaded.equals(List.of("jawt"))) throw new AssertionError("AWT preload missing for " + name);
            checks++;
            bootstrap.load();
            if (loaded.size() != 1) throw new AssertionError("Native dependency loaded twice for " + name);
            checks++;
        }
        for (String name : List.of("Mac OS X", "Darwin", "FreeBSD", "")) {
            var loaded = new ArrayList<String>();
            new NativeBrowserLibraries(name, loaded::add).load();
            if (!loaded.isEmpty()) throw new AssertionError("Unrelated OS matched Windows: " + name);
            checks++;
        }
        int[] attempts = {0};
        var retry = new NativeBrowserLibraries("Linux", library -> {
            if (++attempts[0] == 1) throw new UnsatisfiedLinkError("fixture");
        });
        try {
            retry.load();
            throw new AssertionError("Native loading failure was hidden");
        } catch (UnsatisfiedLinkError expected) { checks++; }
        retry.load();
        retry.load();
        if (attempts[0] != 2) throw new AssertionError("Failed load marked as successful");
        return checks + 1;
    }
}
