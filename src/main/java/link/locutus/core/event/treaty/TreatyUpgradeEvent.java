package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;

public class TreatyUpgradeEvent extends TreatyEvent {
    public TreatyUpgradeEvent(DBTreaty from, DBTreaty to) {
        super(from, to);
    }
}
