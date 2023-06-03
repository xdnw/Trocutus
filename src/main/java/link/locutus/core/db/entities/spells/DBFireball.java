package link.locutus.core.db.entities.spells;

import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.api.pojo.pages.FireballInteraction;
import link.locutus.core.db.entities.AbstractAttackOrSpell;

import java.util.Map;

public class DBFireball extends AbstractAttackOrSpell {
    public final int cavalry;
    public final int archers;
    public final int soldiers;
    public final int elites;

    public DBFireball(int id, int attackerId, int attackerAa, int soldiers, int cavalry, int archers, int elites, int defenderId, int defenderAa, long date) {
        super(AttackOrSpellType.FIREBALL, id, attackerId, attackerAa, defenderId, defenderAa, date);
        this.soldiers = soldiers;
        this.cavalry = cavalry;
        this.archers = archers;
        this.elites = elites;
    }

    public DBFireball(FireballInteraction.Fireball pojo) {
        super(AttackOrSpellType.FIREBALL, pojo.id, pojo.attacker.id, pojo.attacker_alliance == null ? 0 : pojo.attacker_alliance.id, pojo.defender.id, pojo.defender_alliance == null ? 0 : pojo.defender_alliance.id, pojo.created_at.getTime());
        this.soldiers = pojo.data.casualties.soldiers;
        this.cavalry = pojo.data.casualties.cavalry;
        this.archers = pojo.data.casualties.archers;
        this.elites = pojo.data.casualties.elites;
    }


    private final static String soldierChar = "\uD83D\uDC82\u200D\uFE0F";
    private final static String archerChar = "\uD83C\uDFF9";
    private final static String cavalryChar = "\uD83C\uDFC7";
    private final static String eliteChar = "\uD83E\uDDD9\u200D\uFE0F";


    public String getUnitMarkdown() {
        StringBuilder body = new StringBuilder();
        body.append(String.format("%6s", soldiers)).append(" " + soldierChar).append(" | ")
                .append(String.format("%5s", cavalry)).append(" " + archerChar).append(" | ")
                .append(String.format("%5s", archers)).append(" " + cavalryChar).append(" | ")
                .append(String.format("%4s", elites)).append(" " + eliteChar);
        return body.toString();
    }

    @Override
    public Map<MilitaryUnit, Long> getCost(boolean isAttacker) {
        Map<MilitaryUnit, Long> result = super.getCost(isAttacker);
        if (isAttacker) return result;
        result.put(MilitaryUnit.SOLDIERS, (long) soldiers);
        result.put(MilitaryUnit.CAVALRY, (long) cavalry);
        result.put(MilitaryUnit.ARCHER, (long) archers);
        result.put(MilitaryUnit.ELITE, (long) elites);
        return result;
    }
}
