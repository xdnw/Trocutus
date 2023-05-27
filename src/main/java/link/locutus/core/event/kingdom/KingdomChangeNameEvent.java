package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeNameEvent extends KingdomEvent {
    public KingdomChangeNameEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
