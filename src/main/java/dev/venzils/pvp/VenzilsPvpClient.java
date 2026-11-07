package dev.venzils.pvp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;

public class VenzilsPvpClient implements ClientModInitializer {
    public static PvpConfig CFG;

    @Override public void onInitializeClient() {
        CFG = PvpConfig.load();
        if (CFG.showSpotify) SpotifyNowPlaying.start();

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> { if (CFG.performanceTweaks) Modules.applyPerformanceTweaks(mc); });
        ClientTickEvents.END_CLIENT_TICK.register(Modules::tick);

        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide()) Modules.onAttack(player, target);
            return InteractionResult.PASS;
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("venzilspvp", "hud"), this::renderHud);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("vpe")
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    for (Modules.Feature f : Modules.ALL.values())
                        ctx.getSource().sendFeedback(Component.literal((CFG.modules.get(f.id()) ? "[ON]  " : "[off] ") + f.id() + " - " + f.desc()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("toggle").then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (!Modules.ALL.containsKey(id)) { ctx.getSource().sendFeedback(Component.literal("Unknown feature: " + id + " (try /vpe list)")); return 0; }
                    boolean now = !CFG.modules.getOrDefault(id, false);
                    CFG.modules.put(id, now); CFG.save();
                    ctx.getSource().sendFeedback(Component.literal(id + " -> " + (now ? "ON" : "off")));
                    return 1;
                })))
                .then(ClientCommandManager.literal("sprint").executes(ctx -> {
                    CFG.autoSprint = !CFG.autoSprint; CFG.save();
                    ctx.getSource().sendFeedback(Component.literal("Auto-sprint " + (CFG.autoSprint ? "ON" : "off")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("perf").executes(ctx -> {
                    Modules.applyPerformanceTweaks(Minecraft.getInstance());
                    ctx.getSource().sendFeedback(Component.literal("Performance tweaks applied"));
                    return 1;
                }))));
    }

    private void renderHud(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        List<String> lines = new ArrayList<>();
        for (Modules.Feature f : Modules.ALL.values()) {
            if (!CFG.modules.getOrDefault(f.id(), false)) continue;
            try {
                String s = f.fn().apply(mc);
                if (s != null) for (String part : s.split("\n")) lines.add(part);
            } catch (Throwable ignored) {}
        }
        int y = CFG.hudY;
        for (String s : lines) {
            g.text(mc.font, s, CFG.hudX, y, 0xFF000000 | CFG.textColor, CFG.textShadow);
            y += CFG.lineSpacing;
        }
    }
}
