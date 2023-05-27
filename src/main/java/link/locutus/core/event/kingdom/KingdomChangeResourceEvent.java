package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeResourceEvent extends KingdomEvent {
    public KingdomChangeResourceEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
