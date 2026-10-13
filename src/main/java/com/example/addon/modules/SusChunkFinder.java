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
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags chunks that hold a suspicious number of grown or player-placed blocks
 * (kelp, cave vines, vines, amethyst, bamboo, bee nests, sideways deepslate).
 * PrimeChunkFinder and SuspiciousESP are the same scanner with different defaults.
 */
public class SusChunkFinder extends Module {
    private static final int TYPES = 7;

    private final SettingGroup sgScan = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanDistance;
    private final Setting<Integer> sensitivity;
    private final Setting<Integer> minTypes;
    private final Setting<Integer> scanInterval;
    private final Setting<Integer> minY;
    private final Setting<Integer> maxY;
    private final Setting<Boolean> chatNotify;

    private final Setting<Boolean> kelp;
    private final Setting<Boolean> caveVines;
    private final Setting<Integer> minAge;
    private final Setting<Boolean> vines;
    private final Setting<Boolean> amethyst;
    private final Setting<Boolean> amethystBuds;
    private final Setting<Boolean> bamboo;
    private final Setting<Boolean> beeNest;
    private final Setting<Boolean> rotatedDeepslate;

    private final Setting<Boolean> highlightChunks;
    private final Setting<Boolean> highlightBlocks;
    private final Setting<Integer> maxBlocks;
    private final Setting<Integer> alpha;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> boxColor;

    private List<BlockPos> blocks = new ArrayList<>();
    private List<int[]> chunkHits = new ArrayList<>(); // {chunkX, chunkZ, count, averageY}
    private int lastChunkCount;
    private int timer;

    public SusChunkFinder() {
        this(AddonTemplate.CATEGORY, "sus-chunk-finder", "Flags chunks with grown or player-placed blocks.",
            5, 1, true, true, false, new SettingColor(255, 80, 80, 255));
    }

    protected SusChunkFinder(Category category, String name, String description, int defaultSensitivity,
                             int defaultMinTypes, boolean defaultHighlightChunks, boolean defaultHighlightBlocks,
                             boolean allTypesOn, SettingColor defaultColor) {
        super(category, name, description);

        scanDistance = sgScan.add(new IntSetting.Builder()
            .name("scan-distance")
            .description("How many chunks around you to scan.")
            .defaultValue(8).min(1).sliderMax(32).build());

        sensitivity = sgScan.add(new IntSetting.Builder()
            .name("sensitivity")
            .description("How many matching blocks a chunk needs before it is flagged. Lower flags more chunks.")
            .defaultValue(defaultSensitivity).min(1).sliderMax(50).build());

        minTypes = sgScan.add(new IntSetting.Builder()
            .name("min-types")
            .description("How many different block types a chunk needs. Only counts types you have turned on.")
            .defaultValue(defaultMinTypes).min(1).max(TYPES).sliderRange(1, TYPES).build());

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

        kelp = sgBlocks.add(new BoolSetting.Builder()
            .name("kelp").description("Count kelp at or above the minimum age.")
            .defaultValue(true).build());

        caveVines = sgBlocks.add(new BoolSetting.Builder()
            .name("cave-vines").description("Count cave vines at or above the minimum age.")
            .defaultValue(allTypesOn).build());

        minAge = sgBlocks.add(new IntSetting.Builder()
            .name("min-age").description("Minimum age for kelp and cave vines. 25 is fully grown.")
            .defaultValue(25).min(0).max(25).sliderRange(0, 25).build());

        vines = sgBlocks.add(new BoolSetting.Builder()
            .name("vines").description("Count vines.")
            .defaultValue(allTypesOn).build());

        amethyst = sgBlocks.add(new BoolSetting.Builder()
            .name("amethyst").description("Count fully grown amethyst clusters.")
            .defaultValue(true).build());

        amethystBuds = sgBlocks.add(new BoolSetting.Builder()
            .name("amethyst-buds").description("Also count small, medium and large amethyst buds.")
            .defaultValue(false).build());

        bamboo = sgBlocks.add(new BoolSetting.Builder()
            .name("bamboo").description("Count bamboo.")
            .defaultValue(allTypesOn).build());

        beeNest = sgBlocks.add(new BoolSetting.Builder()
            .name("bee-nest").description("Count bee nests.")
            .defaultValue(allTypesOn).build());

        rotatedDeepslate = sgBlocks.add(new BoolSetting.Builder()
            .name("rotated-deepslate").description("Count deepslate that is not upright.")
            .defaultValue(allTypesOn).build());

        highlightChunks = sgRender.add(new BoolSetting.Builder()
            .name("highlight-chunks").description("Draw a box over each flagged chunk.")
            .defaultValue(defaultHighlightChunks).build());

        highlightBlocks = sgRender.add(new BoolSetting.Builder()
            .name("highlight-blocks").description("Draw a box around each matching block in flagged chunks.")
            .defaultValue(defaultHighlightBlocks).build());

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

    /** Index of the block type this state counts as, or -1 if it does not count. */
    private int typeOf(BlockState state) {
        if (kelp.get() && state.isOf(Blocks.KELP)) {
            return state.get(KelpBlock.AGE) >= minAge.get() ? 0 : -1;
        }
        if (caveVines.get() && state.isOf(Blocks.CAVE_VINES)) {
            return state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) >= minAge.get() ? 1 : -1;
        }
        if (vines.get() && state.isOf(Blocks.VINE)) return 2;
        if (amethyst.get() && state.isOf(Blocks.AMETHYST_CLUSTER)) return 3;
        if (amethystBuds.get() && (state.isOf(Blocks.LARGE_AMETHYST_BUD)
            || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
            || state.isOf(Blocks.SMALL_AMETHYST_BUD))) return 3;
        if (bamboo.get() && state.isOf(Blocks.BAMBOO)) return 4;
        if (beeNest.get() && state.isOf(Blocks.BEE_NEST)) return 5;
        if (rotatedDeepslate.get() && state.isOf(Blocks.DEEPSLATE)) {
            return state.get(PillarBlock.AXIS) != Direction.Axis.Y ? 6 : -1;
        }
        return -1;
    }

    private boolean counts(BlockState state) {
        return typeOf(state) >= 0;
    }

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
        int neededTypes = minTypes.get();
        int yMin = Math.min(minY.get(), maxY.get());
        int yMax = Math.max(minY.get(), maxY.get());

        for (int cx = playerChunkX - range; cx <= playerChunkX + range; cx++) {
            for (int cz = playerChunkZ - range; cz <= playerChunkZ + range; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                List<BlockPos> found = new ArrayList<>();
                int[] typeCounts = new int[TYPES];
                long ySum = 0;

                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty()) continue;

                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    if (baseY + 15 < yMin || baseY > yMax) continue;
                    if (!section.hasAny(this::counts)) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            if (worldY < yMin || worldY > yMax) continue;

                            for (int z = 0; z < 16; z++) {
                                int type = typeOf(section.getBlockState(x, y, z));
                                if (type >= 0) {
                                    typeCounts[type]++;
                                    found.add(new BlockPos((cx << 4) + x, worldY, (cz << 4) + z));
                                    ySum += worldY;
                                }
                            }
                        }
                    }
                }

                int distinct = 0;
                for (int count : typeCounts) {
                    if (count > 0) distinct++;
                }

                if (found.size() >= needed && distinct >= neededTypes) {
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
