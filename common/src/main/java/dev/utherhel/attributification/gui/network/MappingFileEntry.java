package dev.utherhel.attributification.gui.network;

/**
 * Represents a single mapping file entry in the GUI file list.
 *
 * @param fileName   the file name (e.g. "mymappings.json")
 * @param editable   whether this file can be edited (world datapack) or is read-only
 */
public record MappingFileEntry(String fileName, boolean editable) {
}
