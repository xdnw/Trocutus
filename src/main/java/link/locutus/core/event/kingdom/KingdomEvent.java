package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.event.Event;

public class KingdomEvent extends Event {
    private final DBKingdom from;
    private final DBKingdom to;

    public KingdomEvent(DBKingdom from, DBKingdom to) {
        this.from = from;
        this.to = to;
    }

    public DBKingdom getFrom() {
        return from;
    }

    public DBKingdom getTo() {
        return to;
    }
}
