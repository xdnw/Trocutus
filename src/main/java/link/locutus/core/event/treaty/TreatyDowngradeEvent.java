package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;

public class TreatyDowngradeEvent extends TreatyEvent {
    public TreatyDowngradeEvent(DBTreaty from, DBTreaty to) {
        super(from, to);
    }
}
