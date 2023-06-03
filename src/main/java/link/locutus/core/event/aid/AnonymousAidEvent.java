package link.locutus.core.event.aid;

import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.DBAid;
import link.locutus.core.event.Event;

public class AnonymousAidEvent extends Event {

    private final DBKingdom sender;
    private final MilitaryUnit unit;
    private final long amount;

    public AnonymousAidEvent(DBKingdom sender, MilitaryUnit unit, long amount) {
        this.sender = sender;
        this.unit = unit;
        this.amount = amount;
    }

    public MilitaryUnit getUnit() {
        return unit;
    }

    public long getAmount() {
        return amount;
    }

    public DBKingdom getSender() {
        return sender;
    }
}
