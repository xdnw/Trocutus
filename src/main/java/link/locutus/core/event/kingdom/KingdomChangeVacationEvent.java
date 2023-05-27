package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeVacationEvent extends KingdomEvent {
    public KingdomChangeVacationEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
