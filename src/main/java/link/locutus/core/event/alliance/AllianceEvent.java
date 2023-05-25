package link.locutus.core.event.alliance;

import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.event.Event;

public class AllianceEvent extends Event {
    private final DBAlliance from;
    private final DBAlliance to;

    public AllianceEvent(DBAlliance from, DBAlliance to) {
        this.from = from;
        this.to = to;
    }

    public DBAlliance getFrom() {
        return from;
    }

    public DBAlliance getTo() {
        return to;
    }
}
