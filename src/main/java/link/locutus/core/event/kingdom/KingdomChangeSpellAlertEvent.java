package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeSpellAlertEvent extends KingdomEvent {
    public KingdomChangeSpellAlertEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
