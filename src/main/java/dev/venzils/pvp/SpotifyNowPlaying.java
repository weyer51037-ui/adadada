package dev.venzils.pvp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.*;

/** Polls the local Spotify desktop app (no login/API key). Windows: window title. macOS: AppleScript. Linux: playerctl. */
public class SpotifyNowPlaying {
    private static volatile String current = "";
    private static ScheduledExecutorService ex;

    public static String get() { return current; }

    public static void start() {
        if (ex != null) return;
        ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "venzils-spotify"); t.setDaemon(true); return t; });
        ex.scheduleWithFixedDelay(() -> {
            try { current = poll(); } catch (Throwable t) { current = ""; }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private static String poll() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        String[] cmd;
        if (os.contains("win")) cmd = new String[]{"powershell", "-NoProfile", "-Command",
            "(Get-Process Spotify -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowTitle} | Select-Object -First 1).MainWindowTitle"};
        else if (os.contains("mac")) cmd = new String[]{"osascript", "-e",
            "if application \"Spotify\" is running then tell application \"Spotify\" to if player state is playing then return (artist of current track) & \" - \" & (name of current track)"};
        else cmd = new String[]{"playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}"};
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { out = r.readLine(); }
        p.waitFor(3, TimeUnit.SECONDS);
        if (out == null) return "";
        out = out.trim();
        // Windows shows "Spotify Free"/"Spotify Premium" when paused or idle.
        if (out.isEmpty() || out.startsWith("Spotify") || out.equals("Advertisement")) return "";
        return out;
    }
}
