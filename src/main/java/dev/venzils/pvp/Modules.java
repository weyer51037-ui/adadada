package dev.venzils.pvp;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Every HUD feature lives here. To add one: call reg(id, description, onByDefault, mc -> "text") below. */
public final class Modules {
    public record Feature(String id, String desc, boolean defaultOn, Function<Minecraft, String> fn) {}
    public static final Map<String, Feature> ALL = new LinkedHashMap<>();

    // ---- shared state ----
    static final ArrayDeque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    static boolean prevL, prevR;
    static double lastReach = -1; static long lastReachAt;
    static double tntSeconds = -1;
    static int hits, combo, prevHurt;
    static Entity lastTarget;
    static Vec3 prevPos; static double speed;

    private static void reg(String id, String desc, boolean on, Function<Minecraft, String> fn) {
        ALL.put(id, new Feature(id, desc, on, fn));
    }

    private static int count(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }
    private static String key(KeyMapping k, String label) { return k.isDown() ? "[" + label + "]" : " " + label + " "; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    static {
        reg("fps", "Frames per second", true, mc -> "FPS: " + mc.getFps());
        reg("cps", "Left-click CPS", true, mc -> "CPS: " + left.size());
        reg("cps_right", "Right-click CPS", false, mc -> "RCPS: " + right.size());
        reg("reach", "Reach of your last hit", true, mc ->
            (lastReach >= 0 && System.currentTimeMillis() - lastReachAt < VenzilsPvpClient.CFG.reachFadeSeconds * 1000)
                ? String.format("Reach: %.2f", lastReach) : null);
        reg("tnt", "Nearest primed TNT fuse", true, mc -> tntSeconds >= 0 ? String.format("TNT: %.1fs", tntSeconds) : null);
        reg("spotify", "Spotify now playing", true, mc -> {
            String t = SpotifyNowPlaying.get();
            if (t.isEmpty()) return null;
            int max = VenzilsPvpClient.CFG.spotifyMaxChars;
            return "♪ " + (t.length() > max ? t.substring(0, max - 1) + "…" : t);
        });
        reg("ping", "Server ping", true, mc -> {
            if (mc.getConnection() == null) return null;
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? null : "Ping: " + info.getLatency() + "ms";
        });
        reg("coords", "XYZ coordinates", true, mc ->
            String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        reg("chunk", "Chunk coordinates", false, mc ->
            "Chunk: " + (mc.player.blockPosition().getX() >> 4) + ", " + (mc.player.blockPosition().getZ() >> 4));
        reg("direction", "Facing direction", false, mc -> "Facing: " + mc.player.getDirection().getName());
        reg("yawpitch", "Yaw and pitch", false, mc ->
            String.format("Yaw/Pitch: %.1f / %.1f", net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()), mc.player.getXRot()));
        reg("speed", "Horizontal speed in blocks/sec", false, mc -> String.format("Speed: %.2f b/s", speed));
        reg("health", "Health (+absorption)", false, mc ->
            String.format("HP: %.1f", mc.player.getHealth()) +
            (mc.player.getAbsorptionAmount() > 0 ? String.format(" (+%.1f)", mc.player.getAbsorptionAmount()) : ""));
        reg("hunger", "Hunger and saturation", false, mc ->
            "Food: " + mc.player.getFoodData().getFoodLevel() + " Sat: " + String.format("%.1f", mc.player.getFoodData().getSaturationLevel()));
        reg("armor", "Armor durability %", false, mc -> {
            int cur = 0, max = 0;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = mc.player.getItemBySlot(s);
                if (st.getMaxDamage() > 0) { max += st.getMaxDamage(); cur += st.getMaxDamage() - st.getDamageValue(); }
            }
            return max == 0 ? null : "Armor: " + (cur * 100 / max) + "%";
        });
        reg("held_durability", "Durability of held item", false, mc -> {
            ItemStack s = mc.player.getMainHandItem();
            return s.getMaxDamage() > 0 ? "Item: " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage() : null;
        });
        reg("held_count", "Stack size of held item", false, mc -> {
            ItemStack s = mc.player.getMainHandItem();
            return s.isEmpty() ? null : "Held: x" + s.getCount();
        });
        reg("effects", "Active potion effects", false, mc -> {
            StringBuilder sb = new StringBuilder();
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.getEffect().getRegisteredName().replace("minecraft:", ""))
                  .append(' ').append(e.getAmplifier() + 1).append(" - ").append(e.getDuration() / 20).append('s');
            }
            return sb.length() == 0 ? null : sb.toString();
        });
        reg("combo", "Hits in a row without being hit", false, mc -> "Combo: " + combo);
        reg("hits", "Total hits landed", false, mc -> "Hits: " + hits);
        reg("target", "Last target's name and health", false, mc -> {
            if (lastTarget == null || !lastTarget.isAlive()) return null;
            String n = lastTarget.getName().getString();
            return lastTarget instanceof LivingEntity le ? String.format("Target: %s %.1f", n, le.getHealth()) : "Target: " + n;
        });
        reg("cooldown", "Attack cooldown %", false, mc ->
            "Cooldown: " + Math.round(mc.player.getAttackStrengthScale(0f) * 100) + "%");
        reg("hurttime", "Hurt time ticks", false, mc -> "Hurt: " + mc.player.hurtTime);
        reg("memory", "Memory usage", false, mc -> {
            Runtime r = Runtime.getRuntime();
            return "Mem: " + ((r.totalMemory() - r.freeMemory()) >> 20) + "/" + (r.maxMemory() >> 20) + "MB";
        });
        reg("clock", "Real-world clock", false, mc -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        reg("server", "Server address", false, mc -> mc.getCurrentServer() == null ? null : mc.getCurrentServer().ip);
        reg("light", "Light level", false, mc -> "Light: " + mc.level.getMaxLocalRawBrightness(mc.player.blockPosition()));
        reg("state", "Sprint/sneak state", false, mc ->
            mc.player.isShiftKeyDown() ? "Sneaking" : mc.player.isSprinting() ? "Sprinting" : null);
        reg("xp", "XP level", false, mc -> "XP: " + mc.player.experienceLevel);
        reg("fall", "Fall distance", false, mc ->
            mc.player.fallDistance > 0.5 ? String.format("Fall: %.1f", (double) mc.player.fallDistance) : null);
        reg("players", "Nearby players and nearest distance", false, mc -> {
            int n = 0; double best = 1e9;
            for (Player o : mc.level.players()) {
                if (o == mc.player) continue;
                double d = o.distanceTo(mc.player);
                if (d <= 64) { n++; best = Math.min(best, d); }
            }
            return n == 0 ? null : String.format("Players: %d (nearest %.1fm)", n, best);
        });
        reg("fov", "Field of view", false, mc -> "FOV: " + mc.options.fov().get());
        reg("name", "Your username", false, mc -> mc.player.getName().getString());
        reg("keystrokes", "WASD / jump / sneak display", false, mc ->
            key(mc.options.keyUp, "W") + key(mc.options.keyLeft, "A") + key(mc.options.keyDown, "S") + key(mc.options.keyRight, "D")
            + key(mc.options.keyJump, "_") + key(mc.options.keyShift, "v"));
        reg("totems", "Totems in inventory", false, mc -> "Totems: " + count(mc.player, Items.TOTEM_OF_UNDYING));
        reg("gapples", "Golden apples", false, mc -> "Gapples: " + count(mc.player, Items.GOLDEN_APPLE));
        reg("pearls", "Ender pearls", false, mc -> "Pearls: " + count(mc.player, Items.ENDER_PEARL));
        reg("arrows", "Arrows", false, mc -> "Arrows: " + count(mc.player, Items.ARROW));
        reg("crystals", "End crystals", false, mc -> "Crystals: " + count(mc.player, Items.END_CRYSTAL));
        reg("obsidian", "Obsidian", false, mc -> "Obsidian: " + count(mc.player, Items.OBSIDIAN));
    }

    // ---- per-tick updates ----
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        PvpConfig cfg = VenzilsPvpClient.CFG;
        long now = System.currentTimeMillis();
        boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
        if (l && !prevL) left.add(now);
        if (r && !prevR) right.add(now);
        prevL = l; prevR = r;
        while (!left.isEmpty() && now - left.peek() > cfg.cpsWindowMs) left.poll();
        while (!right.isEmpty() && now - right.peek() > cfg.cpsWindowMs) right.poll();

        // nearest primed TNT
        double best = Double.MAX_VALUE; int fuse = -1;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PrimedTnt t) {
                double d = t.distanceTo(mc.player);
                if (d < best && d <= cfg.tntMaxRange) { best = d; fuse = t.getFuse(); }
            }
        }
        tntSeconds = fuse < 0 ? -1 : fuse / 20.0;

        // speed
        Vec3 pos = mc.player.position();
        if (prevPos != null) speed = Math.hypot(pos.x - prevPos.x, pos.z - prevPos.z) * 20.0;
        prevPos = pos;

        // combo resets when you get hurt
        int ht = mc.player.hurtTime;
        if (ht > prevHurt) combo = 0;
        prevHurt = ht;

        // auto-sprint
        if (cfg.autoSprint && mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.isUsingItem())
            mc.options.keySprint.setDown(true);
    }

    public static void onAttack(Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        var b = target.getBoundingBox();
        Vec3 p = new Vec3(clamp(eye.x, b.minX, b.maxX), clamp(eye.y, b.minY, b.maxY), clamp(eye.z, b.minZ, b.maxZ));
        lastReach = eye.distanceTo(p); lastReachAt = System.currentTimeMillis();
        hits++; combo++; lastTarget = target;
    }

    public static void applyPerformanceTweaks(Minecraft mc) {
        try {
            mc.options.entityShadows().set(false);
            mc.options.biomeBlendRadius().set(0);
            mc.options.enableVsync().set(false);
        } catch (Throwable t) { System.err.println("[VenzilsPVP] perf tweak failed: " + t); }
    }
}
