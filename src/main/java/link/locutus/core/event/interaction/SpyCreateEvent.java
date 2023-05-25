package link.locutus.core.event.interaction;

import link.locutus.core.db.entities.DBAttack;
import link.locutus.core.db.entities.DBSpy;
import link.locutus.core.event.Event;

public class SpyCreateEvent extends Event {
    private final DBSpy spy;

    public SpyCreateEvent(DBSpy spy) {
        this.spy = spy;
    }

    public DBSpy getSpy() {
        return spy;
    }
}
