package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;

public class TreatyCreateEvent extends TreatyEvent {
    public TreatyCreateEvent(DBTreaty treaty) {
        super(null, treaty);
    }
}
