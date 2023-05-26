package link.locutus.core.db.entities.war;

import link.locutus.core.api.pojo.pages.AttackInteraction;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;

public final class DBAttack {
    public final int id;
    public final int attacker_aa;
    public final int attacker_id;
    public final int attacker_soldiers;
    public final int attacker_cavalry;
    public final int attacker_archers;
    public final int attacker_elites;
    public final boolean victory;
    public final int goldLoot;
    public final int acreLoot;
    public final int defenderAcreLoss;
    public final int atkHeroExp;
    public final int defHeroExp;
    public final int defender_aa;
    public final int defender_id;
    public final int defender_soldiers;
    public final int defender_cavalry;
    public final int defender_archers;
    public final int defender_elites;
    public final long date;

    public DBAttack(int id, int attackerAa, int attackerId, int attackerSoldiers, int attackerCavalry, int attackerArchers, int attackerElites, boolean victory, int goldLoot, int acreLoot, int defenderAcreLoss, int atkHeroExp, int defHeroExp, int defenderAa, int defenderId, int defenderSoldiers, int defenderCavalry, int defenderArchers, int defenderElites, long date) {
        this.id = id;
        attacker_aa = attackerAa;
        attacker_id = attackerId;
        attacker_soldiers = attackerSoldiers;
        attacker_cavalry = attackerCavalry;
        attacker_archers = attackerArchers;
        attacker_elites = attackerElites;
        this.victory = victory;
        this.goldLoot = goldLoot;
        this.acreLoot = acreLoot;
        this.defenderAcreLoss = defenderAcreLoss;
        this.atkHeroExp = atkHeroExp;
        this.defHeroExp = defHeroExp;
        defender_aa = defenderAa;
        defender_id = defenderId;
        defender_soldiers = defenderSoldiers;
        defender_cavalry = defenderCavalry;
        defender_archers = defenderArchers;
        defender_elites = defenderElites;
        this.date = date;
    }

    public DBAttack(AttackInteraction.Attack pojo) {
        this.id = pojo.id;
        this.attacker_aa = pojo.attacker_alliance == null ? 0 : pojo.attacker_alliance.id;
        this.attacker_id = pojo.attacker.id;
        this.attacker_soldiers = pojo.data.attackerCasualties.soldiers;
        this.attacker_cavalry = pojo.data.attackerCasualties.cavalry;
        this.attacker_archers = pojo.data.attackerCasualties.archers;
        this.attacker_elites = pojo.data.attackerCasualties.elites;
        this.victory = pojo.data.victory;
        this.goldLoot = pojo.data.goldLoot;
        this.acreLoot = pojo.data.acreLoot;
        this.defenderAcreLoss = pojo.data.defenderAcreLoss;
        this.atkHeroExp = pojo.data.atkHeroExp;
        this.defHeroExp = pojo.data.defHeroExp;
        this.defender_aa = pojo.defender_alliance == null ? 0 : pojo.defender_alliance.id;
        this.defender_id = pojo.defender.id;
        this.defender_soldiers = pojo.data.defenderCasualties.soldiers;
        this.defender_cavalry = pojo.data.defenderCasualties.cavalry;
        this.defender_archers = pojo.data.defenderCasualties.archers;
        this.defender_elites = pojo.data.defenderCasualties.elites;
        this.date = pojo.created_at.getTime();
    }

    public String getAttackerAllianceName() {
        DBAlliance alliance = DBAlliance.get(attacker_aa);
        return alliance == null ? "AA:" + attacker_aa : alliance.getName();
    }

    public String getDefenderAllianceName() {
        DBAlliance alliance = DBAlliance.get(defender_aa);
        return alliance == null ? "AA:" + defender_aa : alliance.getName();
    }

    public String getAttackerKingdomName() {
        DBKingdom alliance = DBKingdom.get(attacker_id);
        return alliance == null ? "kingdom:" + attacker_id : alliance.getName();
    }

    public String getDefenderKingdomName() {
        DBKingdom alliance = DBKingdom.get(defender_id);
        return alliance == null ? "kingdom:" + defender_id : alliance.getName();
    }
}
