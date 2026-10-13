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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared scanning and rendering for simple block finders.
 * A chunk is flagged when it holds at least "sensitivity" blocks that match.
 */
public abstract class SusBlockFinder extends Module {
    protected final SettingGroup sgScan = settings.getDefaultGroup();
    protected final SettingGroup sgRender = settings.createGroup("Render");

    protected final Setting<Integer> scanDistance;
    protected final Setting<Integer> sensitivity;
    protected final Setting<Integer> scanInterval;
    protected final Setting<Integer> minY;
    protected final Setting<Integer> maxY;
    protected final Setting<Boolean> chatNotify;

    protected final Setting<Boolean> highlightChunks;
    protected final Setting<Boolean> highlightBlocks;
    protected final Setting<Integer> maxBlocks;
    protected final Setting<Integer> alpha;
    protected final Setting<ShapeMode> shapeMode;
    protected final Setting<SettingColor> boxColor;

    private List<BlockPos> blocks = new ArrayList<>();
    private List<int[]> chunkHits = new ArrayList<>(); // {chunkX, chunkZ, count, averageY}
    private int lastChunkCount;
    private int timer;

    protected SusBlockFinder(String name, String description, int defaultSensitivity, SettingColor defaultColor) {
        this(name, description, defaultSensitivity, defaultColor, true);
    }

    protected SusBlockFinder(String name, String description, int defaultSensitivity, SettingColor defaultColor, boolean defaultHighlightChunks) {
        super(AddonTemplate.CATEGORY, name, description);

        scanDistance = sgScan.add(new IntSetting.Builder()
            .name("scan-distance")
            .description("How many chunks around you to scan.")
            .defaultValue(8).min(1).sliderMax(32).build());

        sensitivity = sgScan.add(new IntSetting.Builder()
            .name("sensitivity")
            .description("How many matching blocks a chunk needs before it is flagged. Lower flags more chunks.")
            .defaultValue(defaultSensitivity).min(1).sliderMax(50).build());

        scanInterval = sgScan.add(new IntSetting.Builder()
            .name("scan-interval")
            .description("Ticks between scans. Higher is lighter on your game.")
            .defaultValue(40).min(5).sliderMax(200).build());

        minY = sgScan.add(new IntSetting.Builder()
            .name("min-y").description("Lowest Y level to scan.")
            .defaultValue(-64).sliderRange(-64, 320).build());

        maxY = sgScan.add(new IntSetting.Builder()
            .name("max-y").description("Highest Y level to scan.")
            .defaultValue(320).sliderRange(-64, 320).build());

        chatNotify = sgScan.add(new BoolSetting.Builder()
            .name("chat-notify")
            .description("Send a chat message when a scan flags more chunks than the last one.")
            .defaultValue(false).build());

        highlightChunks = sgRender.add(new BoolSetting.Builder()
            .name("highlight-chunks").description("Draw a box over each flagged chunk.")
            .defaultValue(defaultHighlightChunks).build());

        highlightBlocks = sgRender.add(new BoolSetting.Builder()
            .name("highlight-blocks").description("Draw a box around each matching block in flagged chunks.")
            .defaultValue(true).build());

        maxBlocks = sgRender.add(new IntSetting.Builder()
            .name("max-blocks").description("Most block boxes to draw.")
            .defaultValue(500).min(1).sliderMax(5000).build());

        alpha = sgRender.add(new IntSetting.Builder()
            .name("alpha").description("Fill opacity of the boxes.")
            .defaultValue(73).min(0).max(255).sliderRange(0, 255).build());

        shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the boxes are rendered.")
            .defaultValue(ShapeMode.Both).build());

        boxColor = sgRender.add(new ColorSetting.Builder()
            .name("color").description("Color of the boxes.")
            .defaultValue(defaultColor).build());
    }

    /** True for a block state this finder should count. Also used to skip chunk sections quickly. */
    protected abstract boolean matches(BlockState state);

    @Override
    public void onActivate() {
        timer = 0;
        lastChunkCount = 0;
        blocks = new ArrayList<>();
        chunkHits = new ArrayList<>();
    }

    @Override
    public void onDeactivate() {
        blocks = new ArrayList<>();
        chunkHits = new ArrayList<>();
    }

    @Override
    public String getInfoString() {
        return chunkHits.size() + " chunks";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = scanInterval.get();

        List<BlockPos> newBlocks = new ArrayList<>();
        List<int[]> newHits = new ArrayList<>();

        BlockPos playerPos = mc.player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int range = scanDistance.get();
        int needed = sensitivity.get();
        int yMin = Math.min(minY.get(), maxY.get());
        int yMax = Math.max(minY.get(), maxY.get());

        for (int cx = playerChunkX - range; cx <= playerChunkX + range; cx++) {
            for (int cz = playerChunkZ - range; cz <= playerChunkZ + range; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                List<BlockPos> found = new ArrayList<>();
                long ySum = 0;

                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty()) continue;

                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    if (baseY + 15 < yMin || baseY > yMax) continue;
                    if (!section.hasAny(this::matches)) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            if (worldY < yMin || worldY > yMax) continue;

                            for (int z = 0; z < 16; z++) {
                                if (matches(section.getBlockState(x, y, z))) {
                                    found.add(new BlockPos((cx << 4) + x, worldY, (cz << 4) + z));
                                    ySum += worldY;
                                }
                            }
                        }
                    }
                }

                if (found.size() >= needed) {
                    newHits.add(new int[]{cx, cz, found.size(), (int) (ySum / found.size())});
                    newBlocks.addAll(found);
                }
            }
        }

        blocks = newBlocks;
        chunkHits = newHits;

        if (chatNotify.get() && newHits.size() > lastChunkCount) {
            info("Flagged %d chunks.", newHits.size());
        }
        lastChunkCount = newHits.size();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        SettingColor base = boxColor.get();
        Color side = new Color(base.r, base.g, base.b, alpha.get());

        if (highlightChunks.get()) {
            for (int[] hit : chunkHits) {
                double x1 = hit[0] << 4;
                double z1 = hit[1] << 4;
                event.renderer.box(x1, hit[3], z1, x1 + 16, hit[3] + 1, z1 + 16,
                    side, base, shapeMode.get(), 0);
            }
        }

        if (highlightBlocks.get()) {
            int limit = Math.min(blocks.size(), maxBlocks.get());
            for (int i = 0; i < limit; i++) {
                event.renderer.box(blocks.get(i), side, base, shapeMode.get(), 0);
            }
        }
    }
}
