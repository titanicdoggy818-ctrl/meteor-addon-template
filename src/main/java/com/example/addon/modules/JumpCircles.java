package com.example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JumpCircles extends Module {
    private record Circle(double x, double y, double z, long start, double seed) {}

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder()
        .name("max-radius").description("Maximum ring radius.")
        .defaultValue(1.5).min(0.1).sliderMax(5).build());

    private final Setting<Integer> duration = sg.add(new IntSetting.Builder()
        .name("duration").description("Ring lifetime in ticks.")
        .defaultValue(20).min(1).sliderMax(100).build());

    private final Setting<Integer> segments = sg.add(new IntSetting.Builder()
        .name("segments").description("Ring smoothness.")
        .defaultValue(64).min(12).sliderMax(128).build());

    private final Setting<Boolean> grow = sg.add(new BoolSetting.Builder()
        .name("grow").description("Expand rings over time.")
        .defaultValue(true).build());

    private final Setting<Boolean> fade = sg.add(new BoolSetting.Builder()
        .name("fade").description("Fade rings out.")
        .defaultValue(true).build());

    private final Setting<Boolean> energy = sg.add(new BoolSetting.Builder()
        .name("energy").description("Add animated gold energy arcs.")
        .defaultValue(true).build());

    private final Setting<Double> noise = sg.add(new DoubleSetting.Builder()
        .name("energy-noise").description("Strength of animated ring distortion.")
        .defaultValue(0.12).min(0).sliderMax(0.4).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("ring-color").description("Main energy color.")
        .defaultValue(new SettingColor(255, 185, 45, 255)).build());

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCircles() {
        super(Categories.Render, "jump-circles",
            "Draws animated gold energy rings when you jump.");
    }

    @Override
    public void onActivate() {
        circles.clear();
        wasOnGround = mc.player == null || mc.player.isOnGround();
    }

    @Override
    public void onDeactivate() {
        circles.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean grounded = mc.player.isOnGround();

        if (wasOnGround && !grounded && mc.player.getVelocity().y > 0.1) {
            long now = System.nanoTime();
            double seed = (now % 1_000_000_000L) * 1e-9;
            circles.add(new Circle(
                mc.player.getX(), mc.player.getY() + 0.025,
                mc.player.getZ(), now, seed
            ));
        }

        wasOnGround = grounded;

        long life = duration.get() * 50_000_000L;
        long now = System.nanoTime();
        Iterator<Circle> it = circles.iterator();
        while (it.hasNext()) {
            if (now - it.next().start() > life) it.remove();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (circles.isEmpty()) return;

        long now = System.nanoTime();
        long life = duration.get() * 50_000_000L;

        SettingColor base = color.get();
        int count = segments.get();
        boolean doGrow = grow.get();
        boolean doFade = fade.get();
        boolean doEnergy = energy.get();
        double noiseVal = noise.get();
        double maxR = maxRadius.get();
        double time = now / 1_000_000_000.0;

        for (Circle c : circles) {
            double t = Math.min(1, (now - c.start()) / (double) life);
            double radius = doGrow ? maxR * t : maxR;
            double alpha = doFade ? 1 - t : 1;

            for (int layer = 0; layer < 3; layer++) {
                double layerRadius = radius * (1.0 - layer * 0.12);
                double layerAlpha = alpha * (1.0 - layer * 0.23);
                double y = c.y() + layer * 0.003;

                int layerR = layer == 0 ? 0 : 15;
                int layerG = layer == 0 ? 0 : 25;
                int layerB = layer == 0 ? 0 : 35;

                for (int i = 0; i < count; i++) {
                    double a1 = Math.PI * 2 * i / count;
                    double a2 = Math.PI * 2 * (i + 1) / count;

                    double wobble1 = doEnergy
                        ? Math.sin(a1 * 7 + time * 8 + c.seed()) * noiseVal
                        : 0;
                    double wobble2 = doEnergy
                        ? Math.sin(a2 * 7 + time * 8 + c.seed()) * noiseVal
                        : 0;

                    double r1 = layerRadius * (1 + wobble1);
                    double r2 = layerRadius * (1 + wobble2);

                    int flicker = doEnergy
                        ? (int) (18 * (0.5 + 0.5 * Math.sin(time * 12 + i * 1.7)))
                        : 0;

                    int a = clamp((int) (base.a * layerAlpha) + flicker);

                    Color col = new Color(
                        clamp(base.r + layerR),
                        clamp(base.g + layerG),
                        clamp(base.b + layerB),
                        a
                    );

                    event.renderer.line(
                        c.x() + Math.cos(a1) * r1, y,
                        c.z() + Math.sin(a1) * r1,
                        c.x() + Math.cos(a2) * r2, y,
                        c.z() + Math.sin(a2) * r2, col
                    );

                    if (doEnergy) {
                        double head = (time * 1.8 + c.seed()) % (Math.PI * 2);
                        double distance = Math.abs(Math.atan2(
                            Math.sin(a1 - head), Math.cos(a1 - head)
                        ));

                        if (distance < 0.22) {
                            Color spark = new Color(255, 235, 155, a);
                            event.renderer.line(
                                c.x() + Math.cos(a1) * r1, y + 0.006,
                                c.z() + Math.sin(a1) * r1,
                                c.x() + Math.cos(a2) * r2, y + 0.006,
                                c.z() + Math.sin(a2) * r2, spark
                            );
                        }
                    }
                }
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
