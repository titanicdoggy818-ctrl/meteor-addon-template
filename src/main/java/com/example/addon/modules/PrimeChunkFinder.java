package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/** Sus Chunk Finder with stricter defaults: every block type on, and a chunk needs three different types. */
public class PrimeChunkFinder extends SusChunkFinder {
    public PrimeChunkFinder() {
        super(AddonTemplate.DONUT, "prime-chunk-finder", "Flags chunks that match several different block types.",
            10, 3, true, false, true, new SettingColor(255, 170, 0, 255));
    }
}
