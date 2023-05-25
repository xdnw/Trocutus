package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.DBKingdom;

public class KingdomCreateEvent extends KingdomEvent {

    public KingdomCreateEvent(DBKingdom aa) {
        super(null, aa);
    }
}
