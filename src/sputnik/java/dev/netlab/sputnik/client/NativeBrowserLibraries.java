package dev.netlab.sputnik.client;

import java.util.Locale;
import java.util.function.Consumer;

/** Loads the current JDK's AWT dependency before Rinku starts native Chromium. */
public final class NativeBrowserLibraries {
    private static final NativeBrowserLibraries CURRENT = new NativeBrowserLibraries(
            System.getProperty("os.name", ""), System::loadLibrary);
    private final String os;
    private final Consumer<String> loader;
    private boolean prepared;

    NativeBrowserLibraries(String os, Consumer<String> loader) {
        this.os = os.toLowerCase(Locale.ROOT);
        this.loader = loader;
    }

    public static void prepare() {
        CURRENT.load();
    }

    synchronized void load() {
        if (prepared) return;
        if (os.startsWith("windows") || os.contains("linux")) {
            // libjcef.so needs libjawt.so by SONAME. Loading the current JDK's
            // copy first also works when Prism sets its own java.library.path.
            // Do this before Rinku calls CefApp.startup(), not after it fails.
            // Java chooses jawt.dll on Windows and libjawt.so on Linux.
            loader.accept("jawt");
        }
        prepared = true;
    }
}
