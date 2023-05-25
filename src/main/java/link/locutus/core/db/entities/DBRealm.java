package link.locutus.core.db.entities;

import link.locutus.Trocutus;

public class DBRealm {
    private final int id;
    private String name;

    public DBRealm(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static DBRealm getOrCreate(int realmId) {
        DBRealm realm = Trocutus.imp().getDB().getRealm(realmId);
        if (realm == null) {
            realm = new DBRealm(realmId, null);
        }
        return realm;
    }

    public String getName() {
        if (name == null || name.isEmpty()) {
            return id + "";
        }
        return name;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DBRealm && ((DBRealm) obj).getId() == getId();
    }

    @Override
    public int hashCode() {
        return getId();
    }
}
