package link.locutus.core.db.entities;

import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAttackOrSpell implements AttackOrSpell {
    public final int id;
    public final int attacker_id;
    public final int attacker_aa;

    public final int defender_id;
    public final int defender_aa;
    public final long date;
    private final AttackOrSpellType type;

    protected AbstractAttackOrSpell(AttackOrSpellType type, int id, int attackerId, int attackerAa, int defenderId, int defenderAa, long date) {
        this.type = type;
        this.id = id;
        attacker_id = attackerId;
        attacker_aa = attackerAa;
        defender_id = defenderId;
        defender_aa = defenderAa;
        this.date = date;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getAttacker_id() {
        return attacker_id;
    }

    @Override
    public int getDefender_id() {
        return defender_id;
    }

    @Override
    public int getAttacker_aa() {
        return attacker_aa;
    }

    @Override
    public int getDefender_aa() {
        return defender_aa;
    }

    public long getDate() {
        return date;
    }

    @Override
    public AttackOrSpellType getType() {
        return type;
    }


    public DBKingdom getAttacker() {
        return DBKingdom.get(attacker_id);
    }

    public DBKingdom getDefender() {
        return DBKingdom.get(defender_id);
    }


    public DBAlliance getAttackerAlliance() {
        return DBAlliance.get(attacker_aa);
    }

    public DBAlliance getDefenderAlliance() {
        return DBAlliance.get(defender_aa);
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

    @Override
    public Map<MilitaryUnit, Long> getCost(boolean isAttacker) {
        if (!isAttacker) return new HashMap<>();
        Map<MilitaryUnit, Long> result = new HashMap<>();
        result.putAll(getType().getDefaultCost());
        return result;
    }
}
