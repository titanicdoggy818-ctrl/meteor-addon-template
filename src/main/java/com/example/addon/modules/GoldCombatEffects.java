package com.example.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Random;

public class GoldCombatEffects extends Module {
    private static final int MAX_PARTICLES = 180;

    private static class Spark {
        double x, y, z;
        double px, py, pz;
        double vx, vy, vz;
        int age;
        final int life;
        final double phase;

        Spark(double x, double y, double z, int life, Random random) {
            this.x = this.px = x;
            this.y = this.py = y;
            this.z = this.pz = z;
            this.life = life;
            this.age = 0;

            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.025 + random.nextDouble() * 0.11;

            vx = Math.cos(angle) * speed;
            vz = Math.sin(angle) * speed;
            vy = 0.015 + random.nextDouble() * 0.12;

            phase = random.nextDouble() * Math.PI * 2;
        }
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> particleCount = sg.add(
        new IntSetting.Builder()
            .name("particles")
            .description("Number of sparks per attack.")
            .defaultValue(24).min(1).sliderMax(60)
            .build()
    );

    private final Setting<Double> spread = sg.add(
        new DoubleSetting.Builder()
            .name("spread")
            .description("How far sparks spread around the target.")
            .defaultValue(0.8).min(0.1).sliderMax(2.5)
            .build()
    );

    private final Setting<Double> energyNoise = sg.add(
        new DoubleSetting.Builder()
            .name("energy-noise")
            .description("Strength of animated spark wobble.")
            .defaultValue(0.08).min(0).sliderMax(0.25)
            .build()
    );

    private final Setting<Integer> lifetime = sg.add(
        new IntSetting.Builder()
            .name("lifetime")
            .description("Maximum spark lifetime in ticks.")
            .defaultValue(18).min(3).sliderMax(40)
            .build()
    );

    private final Deque<Spark> sparks = new ArrayDeque<>();
    private final Random random = new Random();

    public GoldCombatEffects() {
        super(Categories.Render, "gold-combat-effects",
            "Adds animated golden energy sparks when you attack entities.");
    }

    @Override
    public void onDeactivate() {
        sparks.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        Entity target = event.entity;
        Box box = target.getBoundingBox();
        double cx = target.getX();
        double cy = box.minY + (box.maxY - box.minY) * 0.55;
        double cz = target.getZ();

        int amount = particleCount.get();
        double spreadVal = spread.get();
        int lifeCap = lifetime.get();

        while (sparks.size() + amount > MAX_PARTICLES && !sparks.isEmpty()) {
            sparks.pollFirst();
        }

        for (int i = 0; i < amount; i++) {
            double x = cx + (random.nextDouble() * 2 - 1) * spreadVal;
            double y = cy + (random.nextDouble() * 2 - 1) * spreadVal;
            double z = cz + (random.nextDouble() * 2 - 1) * spreadVal;

            int life = 8 + random.nextInt(13);
            if (life > lifeCap) life = lifeCap;

            sparks.addLast(new Spark(x, y, z, life, random));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (sparks.isEmpty()) return;

        double noise = energyNoise.get();
        Iterator<Spark> iterator = sparks.iterator();

        while (iterator.hasNext()) {
            Spark spark = iterator.next();

            spark.px = spark.x;
            spark.py = spark.y;
            spark.pz = spark.z;

            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.z += spark.vz;

            spark.vx += Math.sin(spark.phase + spark.age * 0.7) * noise * 0.08;
            spark.vz += Math.cos(spark.phase + spark.age * 0.6) * noise * 0.08;

            spark.vx *= 0.91;
            spark.vy = spark.vy * 0.91 - 0.003;
            spark.vz *= 0.91;

            spark.age++;

            if (spark.age >= spark.life) iterator.remove();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (sparks.isEmpty()) return;

        double time = System.nanoTime() / 1_000_000_000.0;

        for (Spark spark : sparks) {
            double progress = spark.age / (double) spark.life;
            int alpha = (int) (255 * (1.0 - progress));
            if (alpha < 0) alpha = 0;
            else if (alpha > 255) alpha = 255;

            Color trail = new Color(255, 166, 25, alpha);
            Color core = new Color(255, 239, 155, alpha);

            event.renderer.line(
                spark.px, spark.py, spark.pz,
                spark.x, spark.y, spark.z,
                trail
            );

            if (Math.sin(time * 22 + spark.phase) > -0.15) {
                double size = 0.025 + (1.0 - progress) * 0.025;

                event.renderer.line(
                    spark.x - size, spark.y, spark.z,
                    spark.x + size, spark.y, spark.z,
                    core
                );

                event.renderer.line(
                    spark.x, spark.y - size, spark.z,
                    spark.x, spark.y + size, spark.z,
                    core
                );
            }
        }
    }
}
