package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;

public class TreatyCancelEvent extends TreatyEvent {
    public TreatyCancelEvent(DBTreaty treaty) {
        super(treaty, null);
    }
}
