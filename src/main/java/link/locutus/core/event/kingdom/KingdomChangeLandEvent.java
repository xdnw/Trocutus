package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeLandEvent extends KingdomEvent {
    public KingdomChangeLandEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
