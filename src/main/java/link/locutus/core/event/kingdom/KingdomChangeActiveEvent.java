package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeActiveEvent extends KingdomEvent {
    public KingdomChangeActiveEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
