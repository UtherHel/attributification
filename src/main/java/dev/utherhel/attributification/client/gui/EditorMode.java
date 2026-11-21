package dev.utherhel.attributification.client.gui;

//? if >=1.21 {
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}

/**
 * How {@link MappingEditorScreen} treats the file it has open.
 * <p>
 * Both modes allow editing and saving — the difference is where the result
 * lands from the player's point of view. Saving always writes to the world
 * datapack; for a built-in file that write becomes an override of the shipped
 * one rather than a change to it, which is what {@link #OVERRIDE} communicates.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
enum EditorMode {
    /** A file in the world datapack — saving updates it in place. */
    EDIT,

    /**
     * A read-only file from a mod or another datapack. Saving copies it into
     * the world datapack under the same id, shadowing the original.
     */
    OVERRIDE;

    boolean isOverride() {
        return this == OVERRIDE;
    }
}
//?} else {
/*

enum EditorMode {
    EDIT,
    OVERRIDE;

    boolean isOverride() {
        return this == OVERRIDE;
    }
}
*///?}
