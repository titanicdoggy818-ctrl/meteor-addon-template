package com.example.addon.modules;

import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.List;

/** Finds player-made light sources such as torches and lanterns. Pick any blocks you like. */
public class LightFinder extends SusBlockFinder {
    private final Setting<List<Block>> lightBlocks = sgScan.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Light-emitting blocks to look for.")
        .defaultValue(
            Blocks.TORCH, Blocks.WALL_TORCH,
            Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
            Blocks.REDSTONE_LAMP
        )
        .build()
    );

    public LightFinder() {
        super("light-finder", "Finds torches, lanterns and other light sources.", 1, new SettingColor(255, 220, 90, 255), false);
    }

    @Override
    protected boolean matches(BlockState state) {
        return lightBlocks.get().contains(state.getBlock());
    }
}
