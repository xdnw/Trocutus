package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.DBKingdom;

public class KingdomDeleteEvent extends KingdomEvent {
    public KingdomDeleteEvent(DBKingdom Kingdom) {
        super(Kingdom, null);
    }
}
