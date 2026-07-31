package dev.utherhel.attributification.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
enum EditorMode {
    EDIT,
    OVERRIDE;

    boolean isOverride() {
        return this == OVERRIDE;
    }
}
