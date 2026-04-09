package dev.utherhel.attributification.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized default mappings for easy modification and maintenance.
 * Separates platform-specific defaults and provides preset configurations.
 */
public class DefaultMappings {
    
    private DefaultMappings() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates default attribute mappings based on platform
     */
    public static List<AttributeMappingEntry> createDefaultMappings() {
        boolean isFabric = dev.utherhel.attributification.Attributifaction.isFabric();
        return isFabric ? getFabricDefaults() : getForgeDefaults();
    }
    
    /**
     * Default mappings for Fabric platform.
     * Fabric users typically prefer to configure their own mappings.
     */
    public static List<AttributeMappingEntry> getFabricDefaults() {
        return new ArrayList<>(); // Empty - let users configure
    }
    
    // ========== FORGE DEFAULTS ==========
    
    /**
     * Default mappings for Forge/NeoForge platform.
     * Comprehensive set of common mod attribute unifications.
     */
    public static List<AttributeMappingEntry> getForgeDefaults() {
        List<AttributeMappingEntry> defaults = new ArrayList<>();
        
        // Spell Power -> Iron's Spellbooks
        addSpellPowerMappings(defaults);
        
        // Spell Power Haste -> Iron's Spellbooks
        addSpellHasteMappings(defaults);
        
        // Spell Power Resistance -> Iron's Spellbooks
        addSpellResistanceMappings(defaults);
        
        // Spell Power Critical -> AttributesLib
        addSpellCriticalMappings(defaults);
        
        // Ranged Weapon -> AttributesLib
        addRangedWeaponMappings(defaults);
        
        // Eidolon -> Iron's Spellbooks
        addEidolonMappings(defaults);
        
        // More RPG Classes -> AttributesLib (fullReplacement)
        addMoreRPGClassesMappings(defaults);
        
        // Obscure API -> AttributesLib (fullReplacement)
        addObscureAPIMappings(defaults);
        
        // TurtleCore -> AttributesLib (fullReplacement)
        addTurtleCoreMappings(defaults);
        
        // GTBCS Spell Lib -> AttributesLib (fullReplacement)
        addGTBCSSpellLibMappings(defaults);
        
        // Goety -> Iron's Spellbooks
        addGoetyMappings(defaults);
        
        // Ars Nouveau -> Iron's Spellbooks
        addArsNouveauMappings(defaults);
        
        // Lodestone -> Iron's Spellbooks
        addLodestoneMappings(defaults);
        
        // Obscure API -> Iron's Spellbooks
        addObscureAPISpellMappings(defaults);
        
        // Wizards Reborn -> Iron's Spellbooks
        addWizardsRebornMappings(defaults);
        
        // Base value 0.0 → Base value 1.0 attributes
        addBaseValueConversionMappings(defaults);
        
        return defaults;
    }
    
    // ========== HELPER METHODS ==========
    
    private static void addSpellPowerMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("spell_power:fire", "irons_spellbooks:fire_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:frost", "irons_spellbooks:ice_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:lightning", "irons_spellbooks:lightning_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:arcane", "irons_spellbooks:ender_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:holy", "irons_spellbooks:holy_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:soul", "irons_spellbooks:eldritch_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:water", "traveloptics:aqua_spell_power, irons_spellbooks:nature_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:earth", "gtbcs_geomancy_plus:geo_spell_power, irons_spellbooks:nature_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:air", "irons_spellbooks:evocation_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:healing", "irons_spellbooks:holy_spell_power", 3.0, 20.0, false, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addSpellHasteMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("spell_power:haste", "irons_spellbooks:cast_time_reduction", 100.0, 100.0, false, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addSpellResistanceMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("spell_power:resistance.generic", "irons_spellbooks:spell_resist", 1.0, 0.1, false, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addSpellCriticalMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("spell_power:critical_damage", "attributeslib:crit_damage", 1.0, 100.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("spell_power:critical_chance", "attributeslib:crit_chance", 1.0, 100.0, false, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addRangedWeaponMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(visualMapping("ranged_weapon:haste", "attributeslib:draw_speed", 100.0, true));
        defaults.add(mapping("ranged_weapon:damage", "attributeslib:arrow_damage", 0.0, 100.0, true, "ADDITION_TO_MULTIPLY_TOTAL"));
    }
    
    private static void addEidolonMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(simpleMapping("eidolon:chanting_speed", "irons_spellbooks:cast_time_reduction"));
        defaults.add(simpleMapping("eidolon:magic_power", "irons_spellbooks:spell_power"));
    }
    
    private static void addMoreRPGClassesMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(fullReplacementMapping("more_rpg_classes:lifesteal_modifier", "attributeslib:life_steal", true));
    }
    
    private static void addObscureAPIMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(fullReplacementMapping("obscure_api:critical_hit", "attributeslib:crit_chance", true));
        defaults.add(fullReplacementMapping("obscure_api:critical_damage", "attributeslib:crit_damage", true));
        defaults.add(fullReplacementMapping("obscure_api:dodge", "attributeslib:dodge_chance", true));
        defaults.add(fullReplacementMapping("obscure_api:penetration", "attributeslib:armor_pierce", true));
        defaults.add(fullReplacementMapping("obscure_api:healing_power", "attributeslib:healing_received", true));
    }
    
    private static void addTurtleCoreMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(fullReplacementMapping("turtlecore:critical_damage", "attributeslib:crit_damage", true));
        defaults.add(fullReplacementMapping("turtlecore:critical_chance", "attributeslib:crit_chance", true));
        defaults.add(fullReplacementMapping("turtlecore:dodge_chance", "attributeslib:dodge_chance", true));
        defaults.add(fullReplacementMapping("turtlecore:life_steal", "attributeslib:life_steal", true));
        defaults.add(fullReplacementMapping("turtlecore:armor_penetration", "attributeslib:armor_pierce", true));
        defaults.add(fullReplacementMapping("turtlecore:ranged_damage_bonus", "attributeslib:arrow_damage", true));
        defaults.add(fullReplacementMapping("turtlecore:healing_effectiveness", "attributeslib:healing_received", true));
        defaults.add(simpleMapping("turtlecore:cooldown_reduction", "irons_spellbooks:cooldown_reduction"));
    }
    
    private static void addGTBCSSpellLibMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(fullReplacementMapping("gtbcs_spell_lib:spell_crit_damage", "attributeslib:crit_damage", true));
        defaults.add(fullReplacementMapping("gtbcs_spell_lib:spell_crit_chance", "attributeslib:crit_chance", true));
    }
    
    private static void addGoetyMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(simpleMapping("goety:casting_speed", "irons_spellbooks:cast_time_reduction"));
    }
    
    private static void addArsNouveauMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("ars_nouveau:ars_nouveau.perk.spell_damage", "irons_spellbooks:spell_power", 1.0, 1.0, false, "DEFAULT"));
        defaults.add(mapping("ars_nouveau:ars_nouveau.perk.mana_regen", "irons_spellbooks:mana_regen", 1.0, 100.0, false, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("ars_nouveau:ars_nouveau.perk.max_mana", "irons_spellbooks:max_mana", 1.0, 100.0, false, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addLodestoneMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(simpleMapping("lodestone:magic_resistance", "irons_spellbooks:spell_resist"));
        defaults.add(simpleMapping("lodestone:magic_proficiency", "irons_spellbooks:spell_power"));
    }
    
    private static void addObscureAPISpellMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(simpleMapping("obscure_api:magic_resistance", "irons_spellbooks:spell_resist"));
    }
    
    private static void addWizardsRebornMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("wizards_reborn:magic_armor", "irons_spellbooks:spell_resist", 1.0, 20.0, true, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    private static void addBaseValueConversionMappings(List<AttributeMappingEntry> defaults) {
        defaults.add(mapping("turtlecore:magic_amp", "irons_spellbooks:spell_power", 1.0, 1.0, false, 0.0, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("gtbcs_spell_lib:spell_damage", "irons_spellbooks:spell_power", 1.0, 1.0, false, 0.0, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("iter_rpg:spellcasting_power", "irons_spellbooks:spell_power", 1.0, 1.0, false, 1.0, "ADDITION_TO_MULTIPLY_BASE"));
        defaults.add(mapping("wizards_reborn:magic_modifier", "irons_spellbooks:spell_power", 1.0, 1.0, false, 1.0, "ADDITION_TO_MULTIPLY_BASE"));
    }
    
    // ========== FACTORY METHODS ==========
    
    /**
     * Creates a simple 1:1 mapping
     */
    private static AttributeMappingEntry simpleMapping(String source, String target) {
        return new AttributeMappingEntry(source, target, 1.0, 0.0, false, false, false, 0.0, "DEFAULT");
    }
    
    /**
     * Creates a mapping with conversion
     */
    private static AttributeMappingEntry mapping(String source, String target, double equivalence, 
                                                 double conversionEquivalence, boolean unidirectional, 
                                                 String operationConversion) {
        return new AttributeMappingEntry(source, target, equivalence, conversionEquivalence, 
                                        unidirectional, false, false, 0.0, operationConversion);
    }
    
    /**
     * Creates a mapping with subtract value
     */
    private static AttributeMappingEntry mapping(String source, String target, double equivalence, 
                                                 double conversionEquivalence, boolean unidirectional, 
                                                 double subtractValue, String operationConversion) {
        return new AttributeMappingEntry(source, target, equivalence, conversionEquivalence, 
                                        unidirectional, false, false, subtractValue, operationConversion);
    }
    
    /**
     * Creates a visual-only mapping
     */
    private static AttributeMappingEntry visualMapping(String source, String target, double equivalence, 
                                                       boolean unidirectional) {
        return new AttributeMappingEntry(source, target, equivalence, 0.0, unidirectional, 
                                        true, false, 0.0, "DEFAULT");
    }
    
    /**
     * Creates a full replacement mapping
     */
    private static AttributeMappingEntry fullReplacementMapping(String source, String target, 
                                                               boolean unidirectional) {
        return new AttributeMappingEntry(source, target, 1.0, 0.0, unidirectional, 
                                        false, true, 0.0, "DEFAULT");
    }
}
