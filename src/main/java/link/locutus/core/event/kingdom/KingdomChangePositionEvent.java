package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangePositionEvent extends KingdomEvent {
    public KingdomChangePositionEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
