package link.locutus.core.event.alliance;

import link.locutus.core.db.entities.alliance.DBAlliance;

public class AllianceUpdateEvent extends AllianceEvent{
    public AllianceUpdateEvent(DBAlliance from, DBAlliance to) {
        super(from, to);
    }
}
