package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;

public class TreatyUpdateEvent extends TreatyEvent {
    public TreatyUpdateEvent(DBTreaty from, DBTreaty to) {
        super(from, to);
    }
}
