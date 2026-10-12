package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights fully grown kelp (age 25) and fully grown amethyst clusters
 * in loaded chunks around the player.
 */
public class GrowthDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRange = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("How many chunks around you to scan.")
        .defaultValue(8)
        .min(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between scans.")
        .defaultValue(40)
        .min(5)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> detectKelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp")
        .description("Detect fully grown kelp.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectAmethyst = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst")
        .description("Detect fully grown amethyst clusters.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> kelpSide = sgRender.add(new ColorSetting.Builder()
        .name("kelp-side-color")
        .defaultValue(new SettingColor(40, 200, 80, 40))
        .build()
    );

    private final Setting<SettingColor> kelpLine = sgRender.add(new ColorSetting.Builder()
        .name("kelp-line-color")
        .defaultValue(new SettingColor(40, 200, 80, 255))
        .build()
    );

    private final Setting<SettingColor> amethystSide = sgRender.add(new ColorSetting.Builder()
        .name("amethyst-side-color")
        .defaultValue(new SettingColor(170, 80, 255, 40))
        .build()
    );

    private final Setting<SettingColor> amethystLine = sgRender.add(new ColorSetting.Builder()
        .name("amethyst-line-color")
        .defaultValue(new SettingColor(170, 80, 255, 255))
        .build()
    );

    private List<BlockPos> kelp = new ArrayList<>();
    private List<BlockPos> amethyst = new ArrayList<>();
    private int timer;

    public GrowthDetector() {
        super(AddonTemplate.CATEGORY, "growth-detector", "Finds fully grown kelp and amethyst clusters.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        kelp = new ArrayList<>();
        amethyst = new ArrayList<>();
    }

    @Override
    public void onDeactivate() {
        kelp = new ArrayList<>();
        amethyst = new ArrayList<>();
    }

    @Override
    public String getInfoString() {
        return kelp.size() + "/" + amethyst.size();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = scanInterval.get();

        List<BlockPos> newKelp = new ArrayList<>();
        List<BlockPos> newAmethyst = new ArrayList<>();

        int playerChunkX = mc.player.getBlockPos().getX() >> 4;
        int playerChunkZ = mc.player.getBlockPos().getZ() >> 4;
        int range = chunkRange.get();

        boolean wantKelp = detectKelp.get();
        boolean wantAmethyst = detectAmethyst.get();

        for (int cx = playerChunkX - range; cx <= playerChunkX + range; cx++) {
            for (int cz = playerChunkZ - range; cz <= playerChunkZ + range; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty()) continue;

                    boolean hasTarget = section.hasAny(state ->
                        (wantKelp && state.isOf(Blocks.KELP)) ||
                        (wantAmethyst && state.isOf(Blocks.AMETHYST_CLUSTER))
                    );
                    if (!hasTarget) continue;

                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;

                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = section.getBlockState(x, y, z);

                                if (wantKelp && state.isOf(Blocks.KELP)) {
                                    if (state.get(KelpBlock.AGE) >= 25) {
                                        newKelp.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                                    }
                                } else if (wantAmethyst && state.isOf(Blocks.AMETHYST_CLUSTER)) {
                                    newAmethyst.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                                }
                            }
                        }
                    }
                }
            }
        }

        kelp = newKelp;
        amethyst = newAmethyst;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (BlockPos pos : kelp) {
            event.renderer.box(pos, kelpSide.get(), kelpLine.get(), shapeMode.get(), 0);
        }
        for (BlockPos pos : amethyst) {
            event.renderer.box(pos, amethystSide.get(), amethystLine.get(), shapeMode.get(), 0);
        }
    }
}
