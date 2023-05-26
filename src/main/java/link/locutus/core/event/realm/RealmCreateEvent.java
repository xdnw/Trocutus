package link.locutus.core.event.realm;

import link.locutus.core.db.entities.alliance.DBRealm;

public class RealmCreateEvent extends RealmEvent{
    public RealmCreateEvent(DBRealm realm) {
        super(null, realm);
    }
}
