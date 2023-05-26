package link.locutus.core.event.treaty;

import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.core.event.Event;

public class TreatyEvent extends Event {
    private final DBTreaty from;
    private final DBTreaty to;

    public TreatyEvent(DBTreaty from, DBTreaty to) {
        this.from = from;
        this.to = to;
    }

    public DBTreaty getFrom() {
        return from;
    }

    public DBTreaty getTo() {
        return to;
    }
}
