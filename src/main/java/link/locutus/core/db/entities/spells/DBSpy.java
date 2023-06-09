package link.locutus.core.db.entities.spells;

import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.api.pojo.pages.SpyInteraction;
import link.locutus.core.db.entities.AbstractAttackOrSpell;
import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DBSpy extends AbstractAttackOrSpell {
    public final int protectedGold;
    public final int gold;
    public final int attack;
    public final int defense;
    public final int soldiers;
    public final int cavalry;
    public final int archers;
    public final int elites;

    public DBSpy(int id, int attackerId, int attackerAa, int protectedGold, int gold, int attack, int defense, int soldiers, int cavalry, int archers, int elites, int defenderId, int defenderAa, long date) {
        super(AttackOrSpellType.SPY, id, attackerId, attackerAa, defenderId, defenderAa, date);
        this.protectedGold = protectedGold;
        this.gold = gold;
        this.attack = attack;
        this.defense = defense;
        this.soldiers = soldiers;
        this.cavalry = cavalry;
        this.archers = archers;
        this.elites = elites;
    }

    public DBSpy(SpyInteraction.Spy pojo) {
        super(AttackOrSpellType.SPY, pojo.id, pojo.attacker.id, pojo.attacker_alliance == null ? 0 : pojo.attacker_alliance.id, pojo.defender.id, pojo.defender_alliance == null ? 0 : pojo.defender_alliance.id, pojo.created_at.getTime());
        this.protectedGold = pojo.data.protectedGold;
        this.gold = pojo.data.gold;
        this.attack = pojo.data.attack;
        this.defense = pojo.data.defense;
        this.soldiers = pojo.data.soldiers;
        this.cavalry = pojo.data.cavalry;
        this.archers = pojo.data.archers;
        this.elites = pojo.data.elites;
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
}
