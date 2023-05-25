package link.locutus.core.db.entities;

import link.locutus.core.api.alliance.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DBKingdom {
    public static DBKingdom getFromUser(User user, DBRealm realm) {
        Map<DBRealm, DBKingdom> map = getFromUser(user);
        return map == null ? null : map.get(realm);
    }
    public static DBKingdom getFromUser(User user, int realm_id) {
        return getFromUser(user, DBRealm.getOrCreate(realm_id));
    }

    public static Map<DBRealm, DBKingdom> getFromUser(User user) {
        return null;
    }
    public static DBKingdom parse(String arg) {
        return null;
    }
    public static DBKingdom get(int id) {
        return null;
    }
    public static Set<DBKingdom> parseList(Guild guild, String arg) {
        return null;
    }

    private final int id;
    private final int realm_id;
    private int alliance_id;
    private Rank permission = Rank.NONE;
    private String name;

    public DBKingdom(int id, int alliance_id, int realm_id, String name) {
        this.id = id;
        this.alliance_id = alliance_id;
        this.realm_id = realm_id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getAlliance_id() {
        return alliance_id;
    }

    public int getRealm_id() {
        return realm_id;
    }

    public DBRealm getRealm() {
        return DBRealm.getOrCreate(realm_id);
    }

    public String getAllianceName() {
        return null;
    }

    public String getName() {
        return name;
    }

    public DBAlliance getAlliance() {
        return DBAlliance.getOrCreate(alliance_id, realm_id);
    }

    public Rank getPosition() {
        return permission;
    }

    public User getUser() {
        return null;
    }

    public long lastActive() {
        return 0;
    }

    public String getQualifiedName() {
        return "kingdom:" + getRealm().getName() + "/" + getName();
    }

    public long active_m() {
        return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastActive());
    }
}
