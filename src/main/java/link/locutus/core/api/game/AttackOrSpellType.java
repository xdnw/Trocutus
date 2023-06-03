package link.locutus.core.api.game;

import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public enum AttackOrSpellType {
    ATTACK(200, 0, 0,true, false),
    SPY(0, 200, 10,false, false) {
        @Override
        public Map<MilitaryUnit, Long> getDefaultCost() {
            Map<MilitaryUnit, Long> result = super.getDefaultCost();
            result.put(MilitaryUnit.EXP, -10L);
            return result;
        }
    },
    FIREBALL(0, 2000, 100,true, true),

    AID(0, 0, 0, false, false),

    ;

    private final int bp;
    private final int mana;
    private final boolean attackAlert;
    private final boolean spellAlert;
    private final int exp;

    AttackOrSpellType(int bp, int mana, int exp, boolean attackAlert, boolean spellAlert) {
        this.bp = bp;
        this.mana = mana;
        this.attackAlert = attackAlert;
        this.spellAlert = spellAlert;
        this.exp = exp;
    }

    public Map<MilitaryUnit, Long> getDefaultCost() {
        Map<MilitaryUnit, Long> cost = new HashMap<>();
        if (getBattlePoints() != 0) cost.put(MilitaryUnit.BATTLE_POINTS, (long) getBattlePoints());
        if (getMana() != 0) cost.put(MilitaryUnit.MANA, (long) getMana());
        if (getExp() != 0) cost.put(MilitaryUnit.EXP, (long) -getExp());

        return cost;
    }

    public int getExp() {
        return exp;
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
