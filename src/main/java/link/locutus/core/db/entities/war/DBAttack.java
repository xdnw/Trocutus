package link.locutus.core.db.entities.war;

import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.api.pojo.pages.AttackInteraction;
import link.locutus.core.db.entities.AbstractAttackOrSpell;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DBAttack extends AbstractAttackOrSpell {
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
    public final int defender_soldiers;
    public final int defender_cavalry;
    public final int defender_archers;
    public final int defender_elites;

    public DBAttack(int id, int attackerAa, int attackerId, int attackerSoldiers, int attackerCavalry, int attackerArchers, int attackerElites, boolean victory, int goldLoot, int acreLoot, int defenderAcreLoss, int atkHeroExp, int defHeroExp, int defenderAa, int defenderId, int defenderSoldiers, int defenderCavalry, int defenderArchers, int defenderElites, long date) {
        super(AttackOrSpellType.ATTACK, id, attackerId, attackerAa, defenderId, defenderAa, date);
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
        defender_soldiers = defenderSoldiers;
        defender_cavalry = defenderCavalry;
        defender_archers = defenderArchers;
        defender_elites = defenderElites;
    }

    public DBAttack(AttackInteraction.Attack pojo) {
        super(AttackOrSpellType.ATTACK, pojo.id, pojo.attacker.id, pojo.attacker_alliance == null ? 0 : pojo.attacker_alliance.id, pojo.defender.id, pojo.defender_alliance == null ? 0 : pojo.defender_alliance.id, pojo.created_at.getTime());
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
        this.defender_soldiers = pojo.data.defenderCasualties.soldiers;
        this.defender_cavalry = pojo.data.defenderCasualties.cavalry;
        this.defender_archers = pojo.data.defenderCasualties.archers;
        this.defender_elites = pojo.data.defenderCasualties.elites;
    }

    @Override
    public Map<MilitaryUnit, Long> getCost(boolean isAttacker) {
        Map<MilitaryUnit, Long> result = new LinkedHashMap<>();
        if (isAttacker) {
            if (attacker_soldiers != 0) result.put(MilitaryUnit.SOLDIERS, (long) attacker_soldiers);
            if (attacker_cavalry != 0) result.put(MilitaryUnit.CAVALRY, (long) attacker_cavalry);
            if (attacker_archers != 0) result.put(MilitaryUnit.ARCHER, (long) attacker_archers);
            if (attacker_elites != 0) result.put(MilitaryUnit.ELITE, (long) attacker_elites);
            if (goldLoot != 0) result.put(MilitaryUnit.GOLD, (long) -goldLoot);
            if (acreLoot != 0) result.put(MilitaryUnit.LAND, (long) -acreLoot);
            if (atkHeroExp != 0) result.put(MilitaryUnit.EXP, (long) -atkHeroExp);
            result.put(MilitaryUnit.BATTLE_POINTS, 200L);
        } else {
            if (defender_soldiers != 0) result.put(MilitaryUnit.SOLDIERS, (long) defender_soldiers);
            if (defender_cavalry != 0) result.put(MilitaryUnit.CAVALRY, (long) defender_cavalry);
            if (defender_archers != 0) result.put(MilitaryUnit.ARCHER, (long) defender_archers);
            if (defender_elites != 0) result.put(MilitaryUnit.ELITE, (long) defender_elites);
            if (goldLoot != 0) result.put(MilitaryUnit.GOLD, (long) goldLoot);
            if (defenderAcreLoss != 0) result.put(MilitaryUnit.LAND, (long) defenderAcreLoss);
            if (defHeroExp != 0) result.put(MilitaryUnit.EXP, (long) -defHeroExp);
        }
        return result;
    }
}
