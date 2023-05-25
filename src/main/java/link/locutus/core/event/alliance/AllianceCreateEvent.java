package link.locutus.core.event.alliance;

import link.locutus.core.db.entities.DBAlliance;

public class AllianceCreateEvent extends AllianceEvent {

    public AllianceCreateEvent(DBAlliance aa) {
        super(null, aa);
    }
}
