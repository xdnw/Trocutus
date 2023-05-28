package link.locutus.core.api.game;

import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public enum AttackOrSpellType {
    ATTACK(200, 0, true, false),
    SPY(0, 200, false, false),
    FIREBALL(0, 2000, true, true),

    // todo

    ;

    private final int bp;
    private final int mana;
    private final boolean attackAlert;
    private final boolean spellAlert;

    AttackOrSpellType(int bp, int mana, boolean attackAlert, boolean spellAlert) {
        this.bp = bp;
        this.mana = mana;
        this.attackAlert = attackAlert;
        this.spellAlert = spellAlert;
    }

    public Map<MilitaryUnit, Long> getDefaultCost() {
        Map<MilitaryUnit, Long> cost = new HashMap<>();
        if (getBattlePoints() != 0) cost.put(MilitaryUnit.BATTLE_POINTS, (long) getBattlePoints());
        if (getMana() != 0) cost.put(MilitaryUnit.MANA, (long) getMana());
        return cost;
    }

    public int getBattlePoints() {
        return bp;
    }

    public int getMana() {
        return mana;
    }

    public double getDefaultDamage(DBKingdom attacker, DBKingdom defender, int myStrength, int defStrength, boolean unitDamage, boolean landDamage, boolean net) {
        return 0;
    }

    public boolean isAttackAlert() {
        return this.attackAlert;
    }

    public boolean isSpellAlert() {
        return this.spellAlert;
    }
}
