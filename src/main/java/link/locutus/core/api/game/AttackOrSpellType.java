package link.locutus.core.api.game;

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
}
