package link.locutus.core.db;

import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import link.locutus.Trocutus;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.TrouncedApi;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.settings.Settings;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrouncedDB extends DBMain {
    private Map<Long, Integer> userToKingdom = new ConcurrentHashMap<>();
    private Map<Integer, Long> kingdomToUser = new ConcurrentHashMap<>();

    public TrouncedDB() throws SQLException {
        super("trounced");
        init();
    }

    public void init() {
        // Do any initialization here

        // load users
        loadUsers();
        // load kingdoms
        loadKingdoms();
        // load alliances
        loadAlliances();
        // load realms
        loadRealms();
    }

    public void loadUsers() {
        SelectBuilder builder = getDb().selectBuilder("USERS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                long discordId = rs.getLong("discord_id");
                int kingdomId = rs.getInt("kingdom_id");
                userToKingdom.put(discordId, kingdomId);
                kingdomToUser.put(kingdomId, discordId);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadKingdoms() {

    }

    public void loadAlliances() {

    }

    public void loadRealms() {

    }

    @Override
    protected void createTables() {
        // api keys
        // executeStmt("CREATE TABLE IF NOT EXISTS `API_KEYS2`(`nation_id` INT NOT NULL PRIMARY KEY, `api_key` BIGINT, `bot_key` BIGINT)");
        TablePreset.create("API_KEYS")
                .putColumn("kingdom_id", ColumnType.BIGINT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("api_key", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .create(getDb())
        ;

        // users
        // executeStmt("CREATE TABLE IF NOT EXISTS `USERS` (`nation_id` INT NOT NULL, `discord_id` BIGINT NOT NULL, `discord_name` VARCHAR, PRIMARY KEY(discord_id))");
        TablePreset.create("USERS")
                .putColumn("kingdom_id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("discord_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb())
        ;
        // kingdoms

        // alliances

        // realms
    }

    public int getKingdomFromApiKey(String key) {
        return getKingdomFromApiKey(key, true);
    }
    public Integer getKingdomFromApiKey(String key, boolean allowFetch) {
        if (Settings.INSTANCE.API_KEY_PRIMARY.equalsIgnoreCase(key) && Settings.INSTANCE.NATION_ID > 0) {
            return Settings.INSTANCE.NATION_ID;
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS WHERE api_key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("kingdom_id");
                    if (id > 0) return id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (allowFetch) {
//            ApiKeyDetails keyStats = new TrouncedApi.TrouncedApiBuilder().addApiKeys(key).build().getStats();
//            if (keyStats != null && keyStats.getNation() != null && keyStats.getNation().getId() != null) {
//                if (keyStats.getNation().getId() > 0) {
//                    int natId = keyStats.getNation().getId();
//                    addApiKey(natId, keyStats.getKey());
//                    return natId;
//                } else {
//                    System.out.println("Invalid nation id " + keyStats);
//                }
//            }
        }
        return null;
    }

    public Long getUserIdFromKingdomId(int kingdom) {
        return kingdomToUser.get(kingdom);
    }

    public Integer getKingdomIdFromUserId(long userId) {
        return userToKingdom.get(userId);
    }

    public DBKingdom getKingdomFromUser(long owner) {
        return null;
    }

    public DBAlliance getAlliance(int aaId) {
        return null;
    }

    public Set<DBKingdom> getKingdomsByAlliance(int realmId, int allianceId) {
        return null;
    }

    public void deleteApiKey(String key) {

    }

    public Auth getAuth(long userId) {
        if (userId == Settings.INSTANCE.ADMIN_USER_ID) {
            return Trocutus.imp().rootAuth();
        }
        return null;
    }
}
