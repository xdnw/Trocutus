package link.locutus.core.db.entities.spells;

import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.db.entities.kingdom.DBKingdom;

public class SpellOp {
    private final AttackOrSpellType type;
    private final DBKingdom attacker;
    private final DBKingdom target;

    public SpellOp(AttackOrSpellType type, DBKingdom attacker, DBKingdom target) {
        this.type = type;
        this.attacker = attacker;
        this.target = target;
    }

    public AttackOrSpellType getType() {
        return type;
    }

    public DBKingdom getAttacker() {
        return attacker;
    }

    public DBKingdom getTarget() {
        return target;
    }
}
