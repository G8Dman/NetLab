package dev.netlab.sputnik.client;

import java.util.Locale;

/** Prepares the JDK dependency omitted by Rinku 3.0.4's Linux startup path. */
public final class NativeBrowserLibraries {
    private static boolean prepared;

    private NativeBrowserLibraries() {}

    public static synchronized void prepare() {
        if (prepared) return;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            // libjcef.so needs libjawt.so by SONAME. Loading the current JDK's
            // copy first also works when Prism sets its own java.library.path.
            // Do this before Rinku calls CefApp.startup(), not after it fails.
            System.loadLibrary("jawt");
        }
        prepared = true;
    }
}
