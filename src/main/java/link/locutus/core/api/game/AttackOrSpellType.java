package link.locutus.core.api.game;

import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.EnumMap;
import java.util.Map;

public enum AttackOrSpellType {
    ATTACK {
        @Override
        public Map<MilitaryUnit, Long> getDefaultCost() {
            Map<MilitaryUnit, Long> result = new EnumMap<>(MilitaryUnit.class);
            result.put(MilitaryUnit.BATTLE_POINTS, 200L);
            return result;
        }

        @Override
        public double getDefaultDamage(DBKingdom attacker, DBKingdom defender, int myStrength, int defStrength, boolean unitDamage, boolean landDamage, boolean net) {

        }
    },
    SPY {
        @Override
        public Map<MilitaryUnit, Long> getDefaultCost() {
            Map<MilitaryUnit, Long> result = new EnumMap<>(MilitaryUnit.class);
            result.put(MilitaryUnit.MANA, 200L);
            result.put(MilitaryUnit.EXP, -10L);
            return result;
        }
    },

    // todo

    ;

    public abstract Map<MilitaryUnit, Long> getDefaultCost();

    public double getDefaultDamage(DBKingdom attacker, DBKingdom defender, int myStrength, int defStrength, boolean unitDamage, boolean landDamage, boolean net) {
        return 0;
    }
}
