package link.locutus.core.db.entities.spells;

import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.AbstractAttackOrSpell;

public class DBAid extends AbstractAttackOrSpell {
    private final MilitaryUnit unit;
    private final long amount;

    protected DBAid(MilitaryUnit unit, long amount, int id, int attackerId, int attackerAa, int defenderId, int defenderAa, long date) {
        super(AttackOrSpellType.AID, id, attackerId, attackerAa, defenderId, defenderAa, date);
        this.unit = unit;
        this.amount = amount;
    }

    public MilitaryUnit getUnit() {
        return unit;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isAnonymous() {
        return getDefender_id() == 0;
    }
}
