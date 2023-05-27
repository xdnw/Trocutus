package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeHeroEvent extends KingdomEvent {
    public KingdomChangeHeroEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
