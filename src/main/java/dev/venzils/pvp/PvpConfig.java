package dev.venzils.pvp;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON config at .minecraft/config/venzilspvp.json */
public class PvpConfig {
    public boolean showSpotify = true;
    public boolean autoSprint = false;
    public boolean performanceTweaks = false;
    public int hudX = 6, hudY = 6, lineSpacing = 11;
    public int textColor = 0xFFFFFF;
    public boolean textShadow = true;
    public double reachFadeSeconds = 3.0;
    public double tntMaxRange = 48.0;
    public int cpsWindowMs = 1000;
    public int spotifyMaxChars = 40;
    public Map<String, Boolean> modules = new LinkedHashMap<>();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("venzilspvp.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static PvpConfig load() {
        PvpConfig c = null;
        try {
            if (Files.exists(FILE)) c = GSON.fromJson(Files.readString(FILE), PvpConfig.class);
        } catch (Exception e) { System.err.println("[VenzilsPVP] bad config, using defaults: " + e); }
        if (c == null) c = new PvpConfig();
        if (c.modules == null) c.modules = new LinkedHashMap<>();
        for (Modules.Feature f : Modules.ALL.values()) c.modules.putIfAbsent(f.id(), f.defaultOn());
        c.save();
        return c;
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
