package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.DBKingdom;

public class KingdomUpdateEvent extends KingdomEvent {
    public KingdomUpdateEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
