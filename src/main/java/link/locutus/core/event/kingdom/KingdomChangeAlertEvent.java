package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeAlertEvent extends KingdomEvent {
    public KingdomChangeAlertEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
