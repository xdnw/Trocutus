package link.locutus.core.db.entities;

import link.locutus.Trocutus;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.settings.Settings;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class DBAlliance {
    private final int realm_id;
    private final int alliance_id;
    private String name;

    public DBAlliance(int id, int realmId, String name) {
        this.alliance_id = id;
        this.realm_id = realmId;
        this.name = name;
    }

    public static DBAlliance getOrCreate(int aaId, int realm_id) {
        DBAlliance alliance = Trocutus.imp().getDB().getAlliance(aaId);
        if (alliance == null) {
            alliance = new DBAlliance(aaId, realm_id, realm_id + "/" + aaId);
        }
        return alliance;
    }

    public static DBAlliance get(Integer aaId) {
        return null;
    }

    public DBRealm getRealm() {
        return DBRealm.getOrCreate(realm_id);
    }

    public String getName() {
        return name;
    }

    public String getDiscord_link() {
        return null;
    }

    public GuildDB getGuildDB() {
        return Trocutus.imp().getGuildDBByAA(alliance_id);
    }

    public String getUrl(String kingdomName) {
        return "https://trounced.net/kingdom/" + kingdomName + "/alliance/" + getName();
    }

    public int getId() {
        return alliance_id;
    }

    public String getQualifiedName() {
        return "aa:" + getRealm().getName() + "/" + getName();
    }
    public Set<DBKingdom> getKingdoms() {
        return Trocutus.imp().getDB().getKingdomsByAlliance(realm_id, alliance_id);
    }
    public Set<DBKingdom> getKingdoms(Predicate<DBKingdom> filter) {
        Set<DBKingdom> results = new LinkedHashSet<>();
        for (DBKingdom kingdom : getKingdoms()) {
            if (filter.test(kingdom)) {
                results.add(kingdom);
            }
        }
        return results;
    }

    public int getRealm_id() {
        return realm_id;
    }
}
