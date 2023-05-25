package link.locutus.core.event.interaction;

import link.locutus.core.db.entities.DBAttack;
import link.locutus.core.event.Event;

public class AttackCreateEvent extends Event {
    private final DBAttack attack;

    public AttackCreateEvent(DBAttack attack) {
        this.attack = attack;
    }

    public DBAttack getAttack() {
        return attack;
    }
}
