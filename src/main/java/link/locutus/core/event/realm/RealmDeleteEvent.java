package link.locutus.core.event.realm;

import link.locutus.core.db.entities.DBRealm;

public class RealmDeleteEvent extends RealmEvent{
    public RealmDeleteEvent(DBRealm realm) {
        super(realm, null);
    }
}
