package com.andrei1058.bedwars.mechanics;

import com.andrei1058.bedwars.api.language.Language;

/**
 * Language keys for the Hypixel-style mechanics. Kept out of the per-language classes: the English
 * defaults are written to every language file that lacks them, so translators can override them there.
 */
public final class MechanicsMessages {

    public static final String TRAP_BED_DESTROYED = "upgrades-trap-bed-destroyed";
    public static final String DEPOSIT_TEAM_CHEST = "interact-deposit-team-chest";
    public static final String DEPOSIT_ENDER_CHEST = "interact-deposit-ender-chest";
    public static final String DEPOSIT_FULL = "interact-deposit-full";

    private MechanicsMessages() {
    }

    public static void saveDefaults() {
        Language.saveIfNotExists(TRAP_BED_DESTROYED, "&cYou cannot buy traps once your bed is destroyed!");
        Language.saveIfNotExists(DEPOSIT_TEAM_CHEST, "&7Deposited &a{amount} {item} &7into the &aTeam Chest&7.");
        Language.saveIfNotExists(DEPOSIT_ENDER_CHEST, "&7Deposited &a{amount} {item} &7into your &dEnder Chest&7.");
        Language.saveIfNotExists(DEPOSIT_FULL, "&cThat chest is full!");
    }
}
