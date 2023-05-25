package link.locutus.core.event.realm;

import link.locutus.core.db.entities.DBRealm;
import link.locutus.core.event.Event;

public class RealmEvent extends Event {
    private final DBRealm from;
    private final DBRealm to;

    public RealmEvent(DBRealm from, DBRealm to) {
        this.from = from;
        this.to = to;
    }

    public DBRealm getFrom() {
        return from;
    }

    public DBRealm getTo() {
        return to;
    }
}
