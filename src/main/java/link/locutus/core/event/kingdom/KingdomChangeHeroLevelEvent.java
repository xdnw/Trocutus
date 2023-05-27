package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomChangeHeroLevelEvent extends KingdomEvent {
    public KingdomChangeHeroLevelEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }
}
