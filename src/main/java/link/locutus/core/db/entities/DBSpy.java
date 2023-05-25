package link.locutus.core.db.entities;

import link.locutus.core.api.pojo.pages.SpyInteraction;

public class DBSpy {
    public final int id;
    public final int attacker_id;
    public final int attacker_aa;
    public final int protectedGold;
    public final int gold;
    public final int attack;
    public final int defense;
    public final int soldiers;
    public final int cavalry;
    public final int archers;
    public final int elites;
    public final int defender_id;
    public final int defender_aa;
    public final long date;

    public DBSpy(int id, int attackerId, int attackerAa, int protectedGold, int gold, int attack, int defense, int soldiers, int cavalry, int archers, int elites, int defenderId, int defenderAa, long date) {
        this.id = id;
        attacker_id = attackerId;
        attacker_aa = attackerAa;
        this.protectedGold = protectedGold;
        this.gold = gold;
        this.attack = attack;
        this.defense = defense;
        this.soldiers = soldiers;
        this.cavalry = cavalry;
        this.archers = archers;
        this.elites = elites;
        defender_id = defenderId;
        defender_aa = defenderAa;
        this.date = date;
    }

    public DBSpy(SpyInteraction.Spy pojo) {
        this.id = pojo.id;
        this.attacker_id = pojo.attacker.id;
        this.attacker_aa = pojo.attacker_alliance.id;
        this.protectedGold = pojo.data.protectedGold;
        this.gold = pojo.data.gold;
        this.attack = pojo.data.attack;
        this.defense = pojo.data.defense;
        this.soldiers = pojo.data.soldiers;
        this.cavalry = pojo.data.cavalry;
        this.archers = pojo.data.archers;
        this.elites = pojo.data.elites;
        this.defender_id = pojo.defender.id;
        this.defender_aa = pojo.defender_alliance.id;
        this.date = pojo.created_at.getTime();
    }
}
