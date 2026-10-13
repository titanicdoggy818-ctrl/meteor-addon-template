package com.example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

/** Draws a ring on the ground each time you jump. */
public class JumpCircles extends Module {
    private record Circle(double x, double y, double z, long start) {}

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder()
        .name("max-radius")
        .description("Radius of the ring.")
        .defaultValue(1.5).min(0.1).sliderMax(5)
        .build());

    private final Setting<Integer> duration = sg.add(new IntSetting.Builder()
        .name("duration")
        .description("How many ticks a ring lasts.")
        .defaultValue(20).min(1).sliderMax(100)
        .build());

    private final Setting<Integer> segments = sg.add(new IntSetting.Builder()
        .name("segments")
        .description("How round the ring is. Higher looks smoother.")
        .defaultValue(32).min(6).sliderMax(96)
        .build());

    private final Setting<Boolean> grow = sg.add(new BoolSetting.Builder()
        .name("grow")
        .description("Make the ring expand. Off keeps it at full size.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> fade = sg.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fade the ring out over its lifetime.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("ring-color")
        .description("Color of the ring.")
        .defaultValue(new SettingColor(130, 90, 255, 255))
        .build());

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCircles() {
        super(Categories.Render, "jump-circles", "Draws a ring on the ground when you jump.");
    }

    @Override
    public void onActivate() {
        circles.clear();
        wasOnGround = true;
    }

    @Override
    public void onDeactivate() {
        circles.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.1) {
            circles.add(new Circle(mc.player.getX(), mc.player.getY() + 0.02, mc.player.getZ(), System.currentTimeMillis()));
        }
        wasOnGround = onGround;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        long life = duration.get() * 50L;
        circles.removeIf(c -> now - c.start() > life);

        SettingColor base = color.get();
        int n = segments.get();

        for (Circle c : circles) {
            double t = (now - c.start()) / (double) life;
            double r = grow.get() ? maxRadius.get() * t : maxRadius.get();
            int a = fade.get() ? (int) (base.a * (1.0 - t)) : base.a;
            Color col = new Color(base.r, base.g, base.b, Math.max(0, Math.min(255, a)));

            for (int i = 0; i < n; i++) {
                double a1 = 2 * Math.PI * i / n;
                double a2 = 2 * Math.PI * (i + 1) / n;
                event.renderer.line(
                    c.x() + Math.cos(a1) * r, c.y(), c.z() + Math.sin(a1) * r,
                    c.x() + Math.cos(a2) * r, c.y(), c.z() + Math.sin(a2) * r,
                    col);
            }
        }
    }
}
