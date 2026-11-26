package com.ruslan.glitch.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Util;

import java.util.Random;

/**
 * Жуткое главное меню: тёмный налёт, редкие помехи, светящиеся глаза в темноте и шёпот.
 * Фон (панорама) и надписи-приколы заменены ресурсами мода.
 * Все "случайности" привязаны к окнам по ~4 секунды, поэтому кадры не дёргаются хаотично.
 */
public final class TitleFx {
    private static final String[] WHISPERS = {
            "i can see you", "it's me", "hi", "don't look away", "you left the door open", "found you", "wake up"
    };

    private TitleFx() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> render(ctx, s.width, s.height));
            }
        });
    }

    private static void render(DrawContext ctx, int w, int h) {
        long t = Util.getMeasuringTimeMs();
        long window = t / 4000;
        long inWindow = t % 4000;
        Random slot = new Random(t / 110);       // меняется ~9 раз в секунду
        Random ev = new Random(window * 7919L);  // одно и то же в течение 4 секунд

        // постоянный тёмный налёт
        ctx.fill(0, 0, w, h, 0x48000000);

        // помехи: в начале некоторых окон
        int glitchRoll = ev.nextInt(100);
        if (glitchRoll < 40 && inWindow < 550) {
            int[] colors = {0xFFFFFF, 0x00FFFF, 0xFF00FF, 0xFF0000, 0x000000};
            int n = 8 + slot.nextInt(10);
            for (int i = 0; i < n; i++) {
                int y = slot.nextInt(h);
                int bh = 1 + slot.nextInt(Math.max(2, h / 22));
                int x = slot.nextInt(Math.max(1, w / 2));
                int bw = w / 4 + slot.nextInt(Math.max(1, w / 2));
                int alpha = 50 + slot.nextInt(110);
                ctx.fill(x, y, Math.min(w, x + bw), Math.min(h, y + bh), (alpha << 24) | colors[slot.nextInt(colors.length)]);
            }
        }

        // глаза в темноте
        int eyeRoll = ev.nextInt(100);
        int ex = w / 10 + ev.nextInt(Math.max(1, w * 4 / 5));
        int ey = h / 6 + ev.nextInt(Math.max(1, h * 2 / 3));
        if (eyeRoll < 35 && inWindow > 1000 && inWindow < 2700) {
            int fade = (int) Math.min(255, Math.min(inWindow - 1000, 2700 - inWindow) * 255 / 350);
            int a = Math.max(0, fade) << 24;
            ctx.fill(ex, ey, ex + 7, ey + 3, a | 0x00FFFF);
            ctx.fill(ex + 14, ey, ex + 21, ey + 3, a | 0xFF00FF);
        }

        // шёпот мелким тусклым текстом
        int whisperRoll = ev.nextInt(100);
        String whisper = WHISPERS[ev.nextInt(WHISPERS.length)];
        int wx = 6 + ev.nextInt(Math.max(1, w / 2));
        int wy = h / 3 + ev.nextInt(Math.max(1, h / 2));
        if (whisperRoll < 25 && inWindow > 500 && inWindow < 2500) {
            ctx.drawText(MinecraftClient.getInstance().textRenderer, whisper, wx, wy, 0xFF3F5F5F, false);
        }

        // редкий "обрыв": экран мгновенно чернеет
        if (ev.nextInt(100) < 8 && inWindow > 3300 && inWindow < 3380) {
            ctx.fill(0, 0, w, h, 0xFF000000);
        }
    }
}
