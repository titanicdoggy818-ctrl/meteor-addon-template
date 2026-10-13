
package com.example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;a
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
import java.util.Iterator;
import java.util.List;

/**
 * Draws an expanding ring at the player's feet when they jump.
 */
public class JumpCircles extends Module {
    private record Circle(double x, double y, double z, long startNanos) {}

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder()
        .name("max-radius")
        .description("Maximum radius of the ring.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderMax(5.0)
        .build());

    private final Setting<Integer> duration = sg.add(new IntSetting.Builder()
        .name("duration")
        .description("How many ticks a ring lasts.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build());

    private final Setting<Integer> segments = sg.add(new IntSetting.Builder()
        .name("segments")
        .description("Higher values make the ring smoother.")
        .defaultValue(32)
        .min(6)
        .sliderMax(96)
        .build());

    private final Setting<Boolean> grow = sg.add(new BoolSetting.Builder()
        .name("grow")
        .description("Expand the ring from zero to its maximum radius.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> fade = sg.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fade the ring out over its lifetime.")
        .defaultValue(true)
        .build());

    private final Setting<Double> heightOffset = sg.add(new DoubleSetting.Builder()
        .name("height-offset")
        .description("Vertical offset above the player's feet.")
        .defaultValue(0.02)
        .min(0.0)
        .sliderMax(0.5)
        .build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("ring-color")
        .description("Color of the ring.")
        .defaultValue(new SettingColor(130, 90, 255, 255))
        .build());

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround;

    public JumpCircles() {
        super(
            Categories.Render,
            "jump-circles",
            "Draws an expanding ring at your feet when you jump."
        );
    }

    @Override
    public void onActivate() {
        circles.clear();
        wasOnGround = mc.player != null && mc.player.isOnGround();
    }

    @Override
    public void onDeactivate() {
        circles.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            wasOnGround = false;
            return;
        }

        boolean onGround = mc.player.isOnGround();

        // Detect the transition from grounded to rising.
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.0) {
            circles.add(new Circle(
                mc.player.getX(),
                mc.player.getY() + heightOffset.get(),
                mc.player.getZ(),
                System.nanoTime()
            ));
        }

        wasOnGround = onGround;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        long now = System.nanoTime();
        long lifeNanos = duration.get() * 50_000_000L;

        Iterator<Circle> iterator = circles.iterator();

        while (iterator.hasNext()) {
            Circle circle = iterator.next();
            long elapsed = now - circle.startNanos();

            if (elapsed >= lifeNanos) {
                iterator.remove();
                continue;
            }

            double t = Math.max(0.0, Math.min(1.0,
                elapsed / (double) lifeNanos));

            double radius = grow.get()
                ? maxRadius.get() * t
                : maxRadius.get();

            SettingColor base = color.get();

            int alpha = fade.get()
                ? (int) (base.a * (1.0 - t))
                : base.a;

            Color renderColor = new Color(
                base.r,
                base.g,
                base.b,
                Math.max(0, Math.min(255, alpha))
            );

            int count = segments.get();

            for (int i = 0; i < count; i++) {
                double angle1 = 2.0 * Math.PI * i / count;
                double angle2 = 2.0 * Math.PI * (i + 1) / count;

                event.renderer.line(
                    circle.x() + Math.cos(angle1) * radius,
                    circle.y(),
                    circle.z() + Math.sin(angle1) * radius,
                    circle.x() + Math.cos(angle2) * radius,
                    circle.y(),
                    circle.z() + Math.sin(angle2) * radius,
                    renderColor
                );
            }
        }
    }
}
