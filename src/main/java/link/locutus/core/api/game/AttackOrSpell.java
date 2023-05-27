package link.locutus.core.api.game;

import java.util.Map;

public interface AttackOrSpell {
    int getId();

    int getAttacker_id();

    int getDefender_id();

    int getAttacker_aa();

    int getDefender_aa();

    Map<MilitaryUnit, Long> getCost(boolean isAttacker);

    AttackOrSpellType getType();
}
