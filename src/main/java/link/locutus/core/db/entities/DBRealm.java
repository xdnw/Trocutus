package link.locutus.core.db.entities;

public class DBRealm {
    private final int id;
    private String name;

    public DBRealm(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static DBRealm getOrCreate(int realmId) {
        return null;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }
}
