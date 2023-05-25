package link.locutus.core.event.alliance;

import link.locutus.core.db.entities.DBAlliance;

public class AllianceDeleteEvent extends AllianceEvent {
    public AllianceDeleteEvent(DBAlliance alliance) {
        super(alliance, null);
    }
}
