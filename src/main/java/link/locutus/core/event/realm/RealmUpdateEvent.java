package link.locutus.core.event.realm;

import link.locutus.core.db.entities.alliance.DBRealm;

public class RealmUpdateEvent extends RealmEvent{
    public RealmUpdateEvent(DBRealm from, DBRealm to) {
        super(from, to);
    }
}
