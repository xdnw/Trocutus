package link.locutus.core.db;

import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import link.locutus.Trocutus;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.TrouncedApi;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.api.pojo.Api;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.core.event.alliance.AllianceCreateEvent;
import link.locutus.core.event.alliance.AllianceDeleteEvent;
import link.locutus.core.event.alliance.AllianceUpdateEvent;
import link.locutus.core.event.Event;
import link.locutus.core.event.attack.AttackCreateEvent;
import link.locutus.core.event.kingdom.KingdomCreateEvent;
import link.locutus.core.event.kingdom.KingdomDeleteEvent;
import link.locutus.core.event.kingdom.KingdomUpdateEvent;
import link.locutus.core.event.realm.RealmCreateEvent;
import link.locutus.core.event.realm.RealmDeleteEvent;
import link.locutus.core.event.realm.RealmUpdateEvent;
import link.locutus.core.event.spy.SpyCreateEvent;
import link.locutus.core.event.treaty.TreatyCancelEvent;
import link.locutus.core.event.treaty.TreatyCreateEvent;
import link.locutus.core.event.treaty.TreatyUpdateEvent;
import link.locutus.core.settings.Settings;
import link.locutus.util.scheduler.ThrowingBiConsumer;
import link.locutus.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TrouncedDB extends DBMain {
    private Map<Integer, Long> kingdomToUser = new ConcurrentHashMap<>();

    Map<Integer, DBKingdom> kingdoms = new ConcurrentHashMap<>();
    Map<Integer, DBAlliance> alliances = new ConcurrentHashMap<>();
    Map<Integer, DBRealm> realms = new ConcurrentHashMap<>();
    Map<Integer, Map<Integer, Map<Integer, DBTreaty>>> treatiesByAlliance = new ConcurrentHashMap<>();

    private Map<Integer, DBAttack> allAttacks = new Int2ObjectOpenHashMap<>();

    private Map<Integer, DBSpy> allSpies = new Int2ObjectOpenHashMap<>();

    private Set<Integer> unknownInteractionIds = new IntArraySet();

    public TrouncedDB() throws SQLException {
        super("trounced");
        init();
    }

    public void init() {
        // load users
        loadUsers();
        // load kingdoms
        loadKingdoms();
        // load alliances
        loadAlliances();
        // load realms
        loadRealms();
        // load treaties
        loadTreaties();

        loadAttacks();

        loadSpies();

        loadUnkownIds();
    }

    public void deleteApiKey(String key) {
        getDb().delete("API_KEYS", QueryCondition.equals("api_key", key));
    }

    public Auth getAuth(long userId) {
        if (userId == Settings.INSTANCE.ADMIN_USER_ID) {
            return Trocutus.imp().rootAuth();
        }
        return null;
    }

    public String getApiKeyFromKingdom(int id) {
        if (id == Settings.INSTANCE.NATION_ID) {
            return Settings.INSTANCE.API_KEY_PRIMARY;
        }
        if (!kingdoms.containsKey(id)) {
            return null;
        }
        try (ResultSet rs = getDb().selectBuilder("API_KEYS")
                .select("api_key")
                .where(QueryCondition.equals("kingdom_id", id))
                .executeRaw()) {
            if (rs.next()) {
                return rs.getString("api_key");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public void loadAttacks() {
        SelectBuilder builder = getDb().selectBuilder("ATTACKS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int attackerAa = rs.getInt("attacker_aa");
                int attackerId = rs.getInt("attacker_id");
                int attackerSoldiers = rs.getInt("attacker_soldiers");
                int attackerCavalry = rs.getInt("attacker_cavalry");
                int attackerArchers = rs.getInt("attacker_archers");
                int attackerElites = rs.getInt("attacker_elites");
                boolean victory = rs.getInt("victory") > 0;
                int goldLoot = rs.getInt("gold_loot");
                int acreLoot = rs.getInt("acre_loot");
                int defenderAcreLoss = rs.getInt("defender_acre_loss");
                int atkHeroExp = rs.getInt("atk_hero_exp");
                int defHeroExp = rs.getInt("def_hero_exp");
                int defenderAa = rs.getInt("defender_aa");
                int defenderId = rs.getInt("defender_id");
                int defenderSoldiers = rs.getInt("defender_soldiers");
                int defenderCavalry = rs.getInt("defender_cavalry");
                int defenderArchers = rs.getInt("defender_archers");
                int defenderElites = rs.getInt("defender_elites");
                long date = rs.getLong("date");

                DBAttack attack = new DBAttack(id, attackerAa, attackerId, attackerSoldiers, attackerCavalry, attackerArchers, attackerElites, victory, goldLoot, acreLoot, defenderAcreLoss, atkHeroExp, defHeroExp, defenderAa, defenderId, defenderSoldiers, defenderCavalry, defenderArchers, defenderElites, date);
                allAttacks.put(id, attack);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadSpies() {
        SelectBuilder builder = getDb().selectBuilder("SPY_OPS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int attackerAa = rs.getInt("attacker_aa");
                int attackerId = rs.getInt("attacker_id");
                int protectedGold = rs.getInt("protected_gold");
                int goldLoot = rs.getInt("gold_loot");
                int attack = rs.getInt("attack");
                int defense = rs.getInt("defense");
                int soldiers = rs.getInt("soldiers");
                int cavalry = rs.getInt("cavalry");
                int archers = rs.getInt("archers");
                int elites = rs.getInt("elites");
                int defenderAa = rs.getInt("defender_aa");
                int defenderId = rs.getInt("defender_id");
                long date = rs.getLong("date");

                DBSpy spy = new DBSpy(id, attackerId, attackerAa, protectedGold, goldLoot, attack, defense, soldiers, cavalry, archers, elites, defenderId, defenderAa, date);
                allSpies.put(id, spy);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Spies " + allSpies.size());
    }

    public void loadUnkownIds() {
        SelectBuilder builder = getDb().selectBuilder("INTERACTIONS")
                .select("id");

        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                unknownInteractionIds.add(id);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadUsers() {
        SelectBuilder builder = getDb().selectBuilder("USERS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                long discordId = rs.getLong("discord_id");
                int kingdomId = rs.getInt("kingdom_id");
                kingdomToUser.put(kingdomId, discordId);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadKingdoms() {
         SelectBuilder builder = getDb().selectBuilder("KINGDOMS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int realmId = rs.getInt("realm_id");
                int allianceId = rs.getInt("alliance_id");
                Rank position = Rank.values[rs.getInt("permission")];
                String name = rs.getString("name");
                String slug = rs.getString("slug");
                int totalLand = rs.getInt("total_land");
                int alertLevel = rs.getInt("alert_level");
                int resourceLevel = rs.getInt("resource_level");
                int spellAlert = rs.getInt("spell_alert");
                long lastActive = rs.getLong("last_active");
                long vacationStart = rs.getLong("vacation_start");
                HeroType hero = HeroType.values[rs.getInt("hero")];
                int heroLevel = rs.getInt("hero_level");
                long last_fetched = rs.getInt("last_fetched");

                DBKingdom kingdom = new DBKingdom(id, realmId, allianceId, position, name, slug, totalLand, alertLevel, resourceLevel, spellAlert, lastActive, vacationStart, hero, heroLevel, last_fetched);
                kingdoms.put(id, kingdom);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadAlliances() {
        SelectBuilder builder = getDb().selectBuilder("ALLIANCES")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int realmId = rs.getInt("realm_id");
                int allianceId = rs.getInt("alliance_id");
                String name = rs.getString("name");
                String tag = rs.getString("tag");
                String description = rs.getString("description");
                long last_fetched = rs.getInt("last_fetched");
                int level = rs.getInt("level");
                int experience = rs.getInt("experience");
                int level_total = rs.getInt("level_total");

                DBAlliance alliance = new DBAlliance(allianceId, realmId, name, tag, description, level, experience, level_total, last_fetched);
                alliances.put(allianceId, alliance);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadRealms() {
        SelectBuilder builder = getDb().selectBuilder("REALMS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int realmId = rs.getInt("realm_id");
                String name = rs.getString("name");

                DBRealm realm = new DBRealm(realmId, name);
                realms.put(realmId, realm);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadTreaties() {
        SelectBuilder builder = getDb().selectBuilder("TREATIES")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                int realmId = rs.getInt("realm_id");
                TreatyType type = TreatyType.values[rs.getInt("type")];
                int from = rs.getInt("from_id");
                int to = rs.getInt("to_id");

                DBTreaty treaty = new DBTreaty(type, from, to, realmId);
                // add to from
                treatiesByAlliance.computeIfAbsent(realmId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(from, k -> new ConcurrentHashMap<>())
                        .put(to, treaty);

                // add to to
                treatiesByAlliance.computeIfAbsent(realmId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(to, k -> new ConcurrentHashMap<>())
                        .put(from, treaty);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        TablePreset.create("KINGDOMS")
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("realm_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("alliance_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("permission", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .putColumn("slug", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .putColumn("total_land", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("alert_level", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("resource_level", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("spell_alert", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("last_active", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("vacation_start", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("hero", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("hero_level", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("last_fetched", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());

        // alliances
        TablePreset.create("ALLIANCES")
                .putColumn("alliance_id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("realm_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .putColumn("tag", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .putColumn("description", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .putColumn("last_fetched", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("level", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("experience", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("level_total", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());

        // realms
        // int id
        // string name
        TablePreset.create("REALMS")
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(64)))
                .create(getDb());

        // treaty
        String treatyCreate = TablePreset.create("TREATIES")
                .putColumn("from_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("to_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("realm_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("type", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .buildQuery(getDb().getType());
        treatyCreate = treatyCreate.replace(");", ", PRIMARY KEY(from_id, to_id));");
        getDb().executeUpdate(treatyCreate);

        // attacks
        TablePreset.create("ATTACKS")
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("attacker_aa", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_soldiers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_cavalry", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_archers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_elites", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("victory", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("gold_loot", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("acre_loot", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_acre_loss", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("atk_hero_exp", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("def_hero_exp", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_aa", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_soldiers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_cavalry", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_archers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_elites", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());


        // spy ops
        TablePreset.create("SPY_OPS")
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("attacker_aa", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attacker_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("protected_gold", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("gold_loot", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("attack", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defense", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("soldiers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("cavalry", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("archers", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("elites", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_aa", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("defender_id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());

        // unknown interactions
        TablePreset.create("INTERACTIONS")
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("data", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(999)))
                .create(getDb());

        // latest interaction kingdom
        TablePreset.create("LATEST_INTERACTION_AA")
                .putColumn("alliance_id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());

        // latest interaction alliance
        TablePreset.create("LATEST_INTERACTION_KINGDOM")
                .putColumn("kingdom_id", ColumnType.BIGINT.struct().setNullAllowed(false).setPrimary(true).configure(f -> f.apply(null)))
                .putColumn("id", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());
    }

    public void setLatestInteractionAlliance(int alliance, int id) {
        // prepared statement
        PreparedStatement stmt = getDb().prepareStatement("INSERT OR REPLACE INTO LATEST_INTERACTION_AA (alliance_id, id) VALUES (?, ?)");
        try {
            stmt.setInt(1, alliance);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setLatestInteractionKingdom(int kingdom, int id) {
        // prepared statement
        PreparedStatement stmt = getDb().prepareStatement("INSERT OR REPLACE INTO LATEST_INTERACTION_KINGDOM (kingdom_id, id) VALUES (?, ?)");
        try {
            stmt.setInt(1, kingdom);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer getLatestInteractionKingdom(int id) {
        // prepared statement
        PreparedStatement stmt = getDb().prepareStatement("SELECT id FROM LATEST_INTERACTION_KINGDOM WHERE kingdom_id = ?");
        try {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer getLatestInteractionAlliance(int id) {
        // prepared statement
        PreparedStatement stmt = getDb().prepareStatement("SELECT id FROM LATEST_INTERACTION_AA WHERE alliance_id = ?");
        try {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private final ThrowingBiConsumer<DBAttack, PreparedStatement> setAttack = new ThrowingBiConsumer<DBAttack, PreparedStatement>() {
        @Override
        public void acceptThrows(DBAttack attack, PreparedStatement stmt) throws Exception {
            stmt.setInt(1, attack.id);
            stmt.setInt(2, attack.attacker_aa);
            stmt.setInt(3, attack.attacker_id);
            stmt.setInt(4, attack.attacker_soldiers);
            stmt.setInt(5, attack.attacker_cavalry);
            stmt.setInt(6, attack.attacker_archers);
            stmt.setInt(7, attack.attacker_elites);
            stmt.setBoolean(8, attack.victory);
            stmt.setInt(9, attack.goldLoot);
            stmt.setInt(10, attack.acreLoot);
            stmt.setInt(11, attack.defenderAcreLoss);
            stmt.setInt(12, attack.atkHeroExp);
            stmt.setInt(13, attack.defHeroExp);
            stmt.setInt(14, attack.defender_aa);
            stmt.setInt(15, attack.defender_id);
            stmt.setInt(16, attack.defender_soldiers);
            stmt.setInt(17, attack.defender_cavalry);
            stmt.setInt(18, attack.defender_archers);
            stmt.setInt(19, attack.defender_elites);
            stmt.setLong(20, attack.date);
        }
    };

    public int[] saveAttacks(Set<DBAttack> toSave) {
        if (toSave.isEmpty()) return new int[0];
        Set<Event> events = new HashSet<>();
        List<DBAttack> toSaveFinal = new ArrayList<>();
        for (DBAttack attack : toSave) {
            DBAttack existing = allAttacks.put(attack.id, attack);
            if (existing != null) continue;

            toSaveFinal.add(attack);
        }
        int[] result;
        String query = "INSERT OR IGNORE INTO ATTACKS (id, " +
                "attacker_aa, " +
                "attacker_id, " +
                "attacker_soldiers, " +
                "attacker_cavalry, " +
                "attacker_archers, " +
                "attacker_elites, " +
                "victory, " +
                "gold_loot, " +
                "acre_loot, " +
                "defender_acre_loss, " +
                "atk_hero_exp, " +
                "def_hero_exp, " +
                "defender_aa, " +
                "defender_id, " +
                "defender_soldiers, " +
                "defender_cavalry, " +
                "defender_archers, " +
                "defender_elites, " +
                "date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (toSaveFinal.size() == 1) {
            DBAttack attack = toSaveFinal.iterator().next();
            result = new int[]{update(query, stmt -> setAttack.accept(attack, stmt))};
        } else {
            result = executeBatch(toSaveFinal, query, setAttack);
        }
        for (int i = 0; i < result.length; i++) {
            DBAttack attack = toSaveFinal.get(i);
            boolean modified = result[i] > 0;
            if (modified) {
                events.add(new AttackCreateEvent(attack));
            }

        }
        if (!events.isEmpty()) {
            Trocutus.imp().runEvents(events);
        }
        return result;
    }

    private final ThrowingBiConsumer<DBSpy, PreparedStatement> setSpy = new ThrowingBiConsumer<DBSpy, PreparedStatement>() {
        @Override
        public void acceptThrows(DBSpy attack, PreparedStatement stmt) throws Exception {
            stmt.setInt(1, attack.id);
            stmt.setInt(2, attack.attacker_id);
            stmt.setInt(3, attack.attacker_aa);
            stmt.setInt(4, attack.protectedGold);
            stmt.setInt(5, attack.gold);
            stmt.setInt(6, attack.attack);
            stmt.setInt(7, attack.defense);
            stmt.setInt(8, attack.soldiers);
            stmt.setInt(9, attack.cavalry);
            stmt.setInt(10, attack.archers);
            stmt.setInt(11, attack.elites);
            stmt.setInt(12, attack.defender_id);
            stmt.setInt(13, attack.defender_aa);
            stmt.setLong(14, attack.date);
        }
    };

    public int[] saveSpyOps(Set<DBSpy> toSave) {
        if (toSave.isEmpty()) return new int[0];
        Set<Event> events = new HashSet<>();
        List<DBSpy> toSaveFinal = new ArrayList<>();
        for (DBSpy spy : toSave) {
            DBSpy existing = allSpies.put(spy.id, spy);
            if (existing != null) continue;

            toSaveFinal.add(spy);
        }
        int[] result;
        String query = "INSERT OR IGNORE INTO SPY_OPS (id, " +
                "attacker_id, " +
                "attacker_aa, " +
                "protected_gold, " +
                "gold_loot, " +
                "attack, " +
                "defense, " +
                "soldiers, " +
                "cavalry, " +
                "archers, " +
                "elites, " +
                "defender_id, " +
                "defender_aa, " +
                "date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (toSaveFinal.size() == 1) {
            DBSpy spy = toSaveFinal.iterator().next();
            result = new int[]{update(query, stmt -> setSpy.accept(spy, stmt))};
        } else {
            result = executeBatch(toSaveFinal, query, setSpy);
        }
        for (int i = 0; i < result.length; i++) {
            DBSpy spy = toSaveFinal.get(i);
            boolean modified = result[i] > 0;
            if (modified) {
                events.add(new SpyCreateEvent(spy));
            }

        }
        if (!events.isEmpty()) {
            Trocutus.imp().runEvents(events);
        }
        return result;
    }

    // setUnknown
        private final ThrowingBiConsumer<Map.Entry<Integer, String>, PreparedStatement> setUnknown = new ThrowingBiConsumer<Map.Entry<Integer, String>, PreparedStatement>() {
        @Override
        public void acceptThrows(Map.Entry<Integer, String> entry, PreparedStatement stmt) throws Exception {
            stmt.setInt(1, entry.getKey());
            stmt.setString(2, entry.getValue());
        }
    };

    public int[] saveUnknownInteractions(Map<Integer, String> values) {
        if (values.isEmpty()) return new int[0];
        Set<Event> events = new HashSet<>();
        Map<Integer, String> toSave = new HashMap<>();
        for (Map.Entry<Integer, String> entry : values.entrySet()) {
            if (unknownInteractionIds.contains(entry.getKey())) continue;
            toSave.put(entry.getKey(), entry.getValue());
        }
        int[] result;
        String query = "INSERT OR IGNORE INTO INTERACTIONS (id, data) VALUES(?, ?)";
        if (toSave.size() == 1) {
            Map.Entry<Integer, String> entry = toSave.entrySet().iterator().next();
            result = new int[]{update(query, stmt -> setUnknown.accept(entry, stmt))};
        } else {
            result = executeBatch(toSave.entrySet(), query, setUnknown);
        }
        for (Map.Entry<Integer, String> entry : toSave.entrySet()) {

        }
        return result;
    }

    public Integer getKingdomFromApiKey(String key) {
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
            try {
                Set<Api.Kingdom> data = new TrouncedApi(ApiKeyPool.builder().addKeyUnsafe(key).build()).fetchData();
                if (data.isEmpty()) {
                    System.out.println("Invalid api key " + key);
                    return null;
                }
                Api.Kingdom keyStats = data.iterator().next();
                String name = keyStats.name;

                Set<DBKingdom> matching = getKingdomsMatching(f -> f.getName().equalsIgnoreCase(name) && f.getSlug().equalsIgnoreCase(keyStats.slug) && f.getAlliance_id() == keyStats.alliance.id);
                if (matching.size() > 1) throw new IllegalArgumentException("Multiple kingdoms with name " + name);
                if (matching.size() == 1) {
                    DBKingdom kingdom = matching.iterator().next();
                    addApiKey(kingdom.getId(), key);
                    return kingdom.getId();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void addApiKey(int kingdom_id, String key) {
            try (PreparedStatement stmt = prepareQuery("INSERT OR IGNORE INTO API_KEYS (kingdom_id, api_key) VALUES (?, ?)")) {
            stmt.setInt(1, kingdom_id);
            stmt.setString(2, key);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Long getUserIdFromKingdomId(int kingdom) {
        return kingdomToUser.get(kingdom);
    }

    public Set<DBKingdom> getKingdomFromUser(long owner) {
        Set<DBKingdom> results = new LinkedHashSet<>();
        for (Map.Entry<Integer, Long> entry : kingdomToUser.entrySet()) {
            if (entry.getValue() == owner) {
                DBKingdom kingdom = kingdoms.get(entry.getKey());
                if (kingdom != null) {
                    results.add(kingdom);
                }
            }
        }
        return results;
    }

    public DBAlliance getAlliance(int aaId) {
        return alliances.get(aaId);
    }

    public Set<DBKingdom> getKingdomsByAlliance(int allianceId) {
        Set<DBKingdom> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, DBKingdom> entry : kingdoms.entrySet()) {
            if (entry.getValue().getAlliance_id() == allianceId) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    private final ThrowingBiConsumer<DBAlliance, PreparedStatement> setAlliance = new ThrowingBiConsumer<DBAlliance, PreparedStatement>() {
        @Override
        public void acceptThrows(DBAlliance alliance, PreparedStatement stmt) throws Exception {
            stmt.setLong(1, alliance.getId());
            stmt.setLong(2, alliance.getRealm_id());
            stmt.setString(3, alliance.getName());
            stmt.setString(4, alliance.getTag());
            stmt.setString(5, alliance.getDescription());
            stmt.setLong(6, alliance.getLastFetched());
            stmt.setLong(7, alliance.getLevel());
            stmt.setLong(8, alliance.getExperience());
            stmt.setLong(9, alliance.getLevelTotal());
        }
    };

    public int[] saveAlliances(int realm_id, Set<Integer> allIds, Map<Integer, DBAlliance> original, List<DBAlliance> toSave, boolean deleteMissing) {
        if (toSave.isEmpty()) return new int[0];
        Set<Event> events = new HashSet<>();
        if (deleteMissing) {
            Set<Integer> toDelete = new HashSet<>();
            Set<Integer> toUpdate = toSave.stream().map(DBAlliance::getId).collect(Collectors.toSet());
            for (Map.Entry<Integer, DBAlliance> entry : alliances.entrySet()) {
                DBAlliance alliance = entry.getValue();
                if (alliance.getRealm_id() != realm_id) continue;
                if (!toUpdate.contains(entry.getKey()) && !allIds.contains(entry.getKey())) {
                    events.add(new AllianceDeleteEvent(alliance));
                    toDelete.add(entry.getKey());
                }
            }
            if (!toDelete.isEmpty()) {
                for (int id : toDelete) {
                    alliances.remove(id);
                }
                String query = "DELETE FROM ALLIANCES WHERE alliance_id = ?";
                executeBatch(toDelete, query, (ThrowingBiConsumer<Integer, PreparedStatement>) (id, stmt) -> stmt.setInt(1, id));
            }

        }
        for (DBAlliance alliance : toSave) {
            alliances.put(alliance.getId(), alliance);
            DBAlliance originalAlliance = original.get(alliance.getId());
            if (originalAlliance == null) {
                events.add(new AllianceCreateEvent(alliance));
            } else {
                events.add(new AllianceUpdateEvent(originalAlliance, alliance));
            }
        }

        int[] result;
        String query = "INSERT OR REPLACE INTO ALLIANCES (alliance_id, realm_id, name, tag, description, last_fetched, level, experience, level_total) VALUES(?,?,?,?,?,?,?,?,?)";
        if (toSave.size() == 1) {
            DBAlliance alliance = toSave.iterator().next();
            result = new int[]{update(query, stmt -> setAlliance.accept(alliance, stmt))};
        } else {
            result = executeBatch(toSave, query, setAlliance);
        }

        if (!events.isEmpty()) {
            Trocutus.imp().runEvents(events);
        }
        return result;
    }

    public void deleteTreaties(Set<DBTreaty> treaties) {
        if (treaties.isEmpty()) return;
        String query = "DELETE FROM TREATIES WHERE from_id = ? AND to_id = ?";
        executeBatch(treaties, query, (ThrowingBiConsumer<DBTreaty, PreparedStatement>) (treaty, stmt) -> {
            stmt.setLong(1, treaty.getFrom_id());
            stmt.setLong(2, treaty.getTo_id());
        });
    }

    public void saveTreaties(Set<DBTreaty> treaties) {
        if (treaties.isEmpty()) return;
        String query = "INSERT OR REPLACE INTO TREATIES (from_id, to_id, type, realm_id) VALUES(?,?,?,?)";
        executeBatch(treaties, query, (ThrowingBiConsumer<DBTreaty, PreparedStatement>) (treaty, stmt) -> {
            stmt.setLong(1, treaty.getFrom_id());
            stmt.setLong(2, treaty.getTo_id());
            stmt.setLong(3, treaty.getType().ordinal());
            stmt.setLong(4, treaty.getRealm_id());
        });
    }

    public void updateTreaties(int realmId, Map<Integer, Map<Integer, DBTreaty>> newTreaties) {
        Map<Integer, Map<Integer, DBTreaty>> oldTreaties = this.treatiesByAlliance.getOrDefault(realmId, new HashMap<>());
        this.treatiesByAlliance.put(realmId, newTreaties);

        Set<DBTreaty> treatyCancelled = new HashSet<>();
        Set<DBTreaty> treatyCreated = new HashSet<>();
        Map<DBTreaty, DBTreaty> treatyUpdated = new HashMap<>();

        for (Map.Entry<Integer, Map<Integer, DBTreaty>> entry : oldTreaties.entrySet()) {
            Map<Integer, DBTreaty> newByAA = newTreaties.getOrDefault(entry.getKey(), new HashMap<>());
            for (Map.Entry<Integer, DBTreaty> toTreatyEntry : entry.getValue().entrySet()) {
                int toAA = toTreatyEntry.getKey();
                DBTreaty before = toTreatyEntry.getValue();
                DBTreaty now = newByAA.get(toAA);
                if (now == null) {
                    treatyCancelled.add(before);
                } else if (!before.equals(now)) {
                    treatyUpdated.put(before, now);
                }
            }
        }

        for (Map.Entry<Integer, Map<Integer, DBTreaty>> entry : newTreaties.entrySet()) {
            Map<Integer, DBTreaty> oldByAA = oldTreaties.getOrDefault(entry.getKey(), new HashMap<>());
            for (Map.Entry<Integer, DBTreaty> toTreatyEntry : entry.getValue().entrySet()) {
                int toAA = toTreatyEntry.getKey();
                DBTreaty now = toTreatyEntry.getValue();
                DBTreaty before = oldByAA.get(toAA);
                if (before == null) {
                    treatyCreated.add(now);
                }
            }
        }

        deleteTreaties(treatyCancelled);
        Set<DBTreaty> toSave = new HashSet<>();
        toSave.addAll(treatyCreated);
        toSave.addAll(treatyUpdated.values());
        saveTreaties(toSave);

        for (DBTreaty treaty : treatyCancelled) {
            Trocutus.imp().runEvent(new TreatyCancelEvent(treaty));
        }
        for (DBTreaty treaty : treatyCreated) {
            Trocutus.imp().runEvent(new TreatyCreateEvent(treaty));
        }
        for (Map.Entry<DBTreaty, DBTreaty> entry : treatyUpdated.entrySet()) {
            Trocutus.imp().runEvent(new TreatyUpdateEvent(entry.getKey(), entry.getValue()));
        }
    }

    public Set<DBAlliance> getAlliances() {
        return new HashSet<>(alliances.values());
    }

    public DBKingdom getKingdom(int id) {
        return kingdoms.get(id);
    }

    private final ThrowingBiConsumer<DBKingdom, PreparedStatement> setKingdom = new ThrowingBiConsumer<DBKingdom, PreparedStatement>() {
        @Override
        public void acceptThrows(DBKingdom kingdom, PreparedStatement stmt) throws Exception {
            stmt.setInt(1, kingdom.getId());
            stmt.setInt(2, kingdom.getRealm_id());
            stmt.setInt(3, kingdom.getAlliance_id());
            stmt.setInt(4, kingdom.getPosition().ordinal());
            stmt.setString(5, kingdom.getName());
            stmt.setString(6, kingdom.getSlug());
            stmt.setInt(7, kingdom.getTotal_land());
            stmt.setInt(8, kingdom.getAlert_level());
            stmt.setInt(9, kingdom.getResource_level().ordinal());
            stmt.setInt(10, kingdom.getSpell_alert());
            stmt.setLong(11, kingdom.getLast_active());
            stmt.setLong(12, kingdom.getVacation_start());
            stmt.setInt(13, kingdom.getHero().ordinal());
            stmt.setInt(14, kingdom.getHero_level());
            stmt.setLong(15, kingdom.getLast_fetched());
        }
    };
    public int[] saveKingdoms(List<DBKingdom> toSave) {
        int[] result;
        String query = "INSERT OR REPLACE INTO KINGDOMS (id, realm_id, alliance_id, permission, name, slug, total_land, alert_level, resource_level, spell_alert, last_active, vacation_start, hero, hero_level, last_fetched) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (toSave.size() == 1) {
            DBKingdom kingdom = toSave.iterator().next();
            result = new int[]{update(query, stmt -> setKingdom.accept(kingdom, stmt))};
        } else {
            result = executeBatch(toSave, query, setKingdom);
        }
        return result;
    }
    public int[] saveKingdoms(Map<Integer, DBKingdom> original, List<DBKingdom> toSave, boolean deleteMissing) {
        if (toSave.isEmpty()) return new int[0];
        Set<Event> events = new HashSet<>();
        if (deleteMissing) {
            Set<Integer> toDelete = new HashSet<>();
            Set<Integer> toUpdate = toSave.stream().map(DBKingdom::getId).collect(Collectors.toSet());
            for (Map.Entry<Integer, DBKingdom> entry : kingdoms.entrySet()) {
                if (!toUpdate.contains(entry.getKey())) {
                    events.add(new KingdomDeleteEvent(entry.getValue()));
                    toDelete.add(entry.getKey());
                }
            }
            if (!toDelete.isEmpty()) {
                for (int id : toDelete) {
                    alliances.remove(id);
                }
                String query = "DELETE FROM KINGDOMS WHERE id = ?";
                executeBatch(toDelete, query, (ThrowingBiConsumer<Integer, PreparedStatement>) (id, stmt) -> stmt.setInt(1, id));
            }
        }
        for (DBKingdom kingdom : toSave) {
            kingdoms.put(kingdom.getId(), kingdom);
            DBKingdom originalKingdom = original.get(kingdom.getId());
            if (originalKingdom == null) {
                events.add(new KingdomCreateEvent(kingdom));
            } else {
                events.add(new KingdomUpdateEvent(originalKingdom, kingdom));
            }
        }

        int[] result = saveKingdoms(toSave);

        if (!events.isEmpty()) {
            Trocutus.imp().runEvents(events);
        }
        return result;
    }

    public DBRealm getRealm(int realmId) {
        return realms.get(realmId);
    }

    public Set<DBKingdom> getKingdomsMatching(Predicate<DBKingdom> predicate) {
        return kingdoms.values().stream().filter(predicate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getAllianceMatching(Predicate<DBAlliance> predicate) {
        return alliances.values().stream().filter(predicate).collect(Collectors.toSet());
    }

    public void deleteKingdoms(Set<DBKingdom> kingdoms) {
        if (kingdoms.isEmpty()) return;
        kingdoms.removeAll(kingdoms);

        // delete from db
        String query = "DELETE FROM KINGDOMS WHERE id = ?";
        executeBatch(kingdoms, query, (ThrowingBiConsumer<DBKingdom, PreparedStatement>) (kingdom, stmt) -> stmt.setInt(1, kingdom.getId()));

        Set<KingdomDeleteEvent> events = kingdoms.stream().map(f -> new KingdomDeleteEvent(f)).collect(Collectors.toSet());
        Trocutus.imp().runEvents(events);
    }

    public void saveRealm(DBRealm realm) {
        DBRealm existing = realms.get(realm.getId());
        Event event;
        if (existing == null) {
            realms.put(realm.getId(), realm);
            event = new RealmCreateEvent(realm);
        } else if (!existing.getName().equalsIgnoreCase(realm.getName())) {
            realms.put(realm.getId(), realm);
            event = new RealmUpdateEvent(existing, realm);
        } else {
            return;
        }
    }

    public Map<Integer, DBRealm> getRealms() {
        return Collections.unmodifiableMap(realms);
    }

    public void deleteRealm(DBRealm realm) {
        realms.remove(realm.getId());
        String query = "DELETE FROM REALMS WHERE id = ?";
        update(query, (ThrowingConsumer<PreparedStatement>) (stmt) -> stmt.setInt(1, realm.getId()));
        Trocutus.imp().runEvent(new RealmDeleteEvent(realm));
    }

    public void saveUser(long userId, DBKingdom kingdom) {
        Long existing = kingdomToUser.put(kingdom.getId(), userId);
        if (existing != null && existing.longValue() == userId) return;
        // save
        String query = "INSERT OR REPLACE INTO USERS (discord_id, kingdom_id) VALUES (?, ?)";
        update(query, (ThrowingConsumer<PreparedStatement>) (stmt) -> {
            stmt.setLong(1, userId);
            stmt.setInt(2, kingdom.getId());
        });
    }

    public Set<DBRealm> getRealmMatching(Predicate<DBRealm> realmPredicate) {
        return realms.values().stream().filter(realmPredicate).collect(Collectors.toSet());
    }

    public DBSpy getLatestSpy(int id) {
        DBSpy latest = null;
        for (DBSpy spy : allSpies.values()) {
            if (spy.defender_id == id && (latest == null || spy.id > latest.id)) {
                latest = spy;
            }
        }
        return latest;
    }

    public DBAttack getLatestDefensive(int id) {
        DBAttack latest = null;
        for (DBAttack spy : allAttacks.values()) {
            if (spy.defender_id == id && (latest == null || spy.id > latest.id)) {
                latest = spy;
            }
        }
        return latest;
    }

    public DBAttack getLatestOffensive(int id) {
        DBAttack latest = null;
        for (DBAttack spy : allAttacks.values()) {
            if (spy.attacker_id == id && (latest == null || spy.id > latest.id)) {
                latest = spy;
            }
        }
        return latest;
    }

    public Map<Integer, DBTreaty> getTreaties(int allianceId) {
        DBAlliance aa = DBAlliance.get(allianceId);
        if (aa == null) return Collections.emptyMap();
        return getTreaties(aa.getRealm_id(), allianceId);
    }

    public Map<Integer, DBTreaty> getTreaties(int realm, int allianceId) {
        return treatiesByAlliance.getOrDefault(realm, Collections.emptyMap()).getOrDefault(allianceId, Collections.emptyMap());
    }
}
