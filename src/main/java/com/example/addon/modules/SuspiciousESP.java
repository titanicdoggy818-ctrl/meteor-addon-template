package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/** Sus Chunk Finder set to box individual blocks instead of whole chunks. */
public class SuspiciousESP extends SusChunkFinder {
    public SuspiciousESP() {
        super(AddonTemplate.CATEGORY, "suspicious-esp", "Boxes individual suspicious blocks.",
            1, 1, false, true, false, new SettingColor(255, 80, 200, 255));
    }
}
