package link.locutus.core.db.entities;

import link.locutus.Trocutus;
import link.locutus.util.MathMan;

import java.util.Set;

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

    public static DBRealm parse(String input) {
        if (MathMan.isInteger(input)) {
            DBRealm aa = Trocutus.imp().getDB().getRealm(Integer.parseInt(input));
            if (aa != null) return aa;
        }
        Set<DBRealm> matching = Trocutus.imp().getDB().getRealmMatching(f -> f.getName().equalsIgnoreCase(input));
        return matching.isEmpty() ? null : matching.iterator().next();
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
