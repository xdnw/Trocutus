package link.locutus.core.db.guild;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import link.locutus.Trocutus;
import link.locutus.core.db.DBMain;
import link.locutus.core.db.entities.AllianceList;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.settings.Settings;
import link.locutus.util.scheduler.ThrowingConsumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

public class GuildDB extends DBMain {
    public static GuildDB get(Guild guild) {
        return Trocutus.imp().getGuildDB(guild);
    }

    public static GuildDB get(long id) {
        return Trocutus.imp().getGuildDB(id);
    }

    private final Guild guild;

    private volatile IAutoRoleTask autoRoleTask;
    private GuildHandler handler;

    private Map<String, String> info;
    private final Map<GuildSetting, Object> infoParsed = new HashMap<>();
    private final Object nullInstance = new Object();

    private Map<String, Set<Long>> coalitions = null;

    private volatile boolean cachedRoleAliases = false;
    private final Map<Roles, Map<Long, Long>> roleToAccountToDiscord;


    public GuildDB(Guild guild) throws SQLException {
        super("guilds/" + guild.getId());
        this.guild = guild;
        this.roleToAccountToDiscord  = new ConcurrentHashMap<>();
    }

    @Override
    protected void createTables() {
        {
            String create = "CREATE TABLE IF NOT EXISTS `ROLES2` (`role` BIGINT NOT NULL, `alias` BIGINT NOT NULL, `alliance` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `COALITIONS` (`alliance_id` BIGINT NOT NULL, `coalition` VARCHAR NOT NULL, PRIMARY KEY(alliance_id, coalition))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `INFO` (`key` VARCHAR NOT NULL PRIMARY KEY, `value` VARCHAR NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
    }

    public Guild getGuild() {
        return guild;
    }

    public long getIdLong() {
        return guild.getIdLong();
    }

    public String getName() {
        return guild.getName();
    }

    private EventBus eventBus = null;

    public void postEvent(Object event) {
        EventBus bus = getEventBus();
        if (bus != null) bus.post(event);
    }

    public EventBus getEventBus() {
        if (this.eventBus == null) {
            if (hasAlliance()) {
                synchronized (this) {
                    if (this.eventBus == null) {
                        this.eventBus = new AsyncEventBus(getGuild().toString(), Runnable::run);
                        eventBus.register(getHandler());
                    }
                }
            }
        }
        return eventBus;
    }

    public boolean hasAlliance() {
        return getOrNull(GuildKey.ALLIANCE_ID) != null;
    }

    public <T> T getOrThrow(GuildSetting<T> key) {
        return getOrThrow(key, true);
    }

    public <T> T getOrThrow(GuildSetting<T> key, boolean allowDelegate) {
        T value = getOrNull(key, allowDelegate);
        if (value == null) {
            throw new UnsupportedOperationException("No " + key.name() + " registered. Use " + key.getCommandMention());
        }
        return value;
    }

    public <T> T getOrNull(GuildSetting<T> key, boolean allowDelegate) {
        Object parsed;
        synchronized (infoParsed) {
            parsed = infoParsed.getOrDefault(key, nullInstance);
        }
        if (parsed != nullInstance) return (T) parsed;

        boolean isDelegate = false;
        String value = getInfoRaw(key, false);
        if (value == null) {
            isDelegate = true;
            if (allowDelegate) {
                value = getInfoRaw(key, true);
            }
        }
        if (value == null) return null;
        try {
            parsed =  (T) key.parse(this, value);
            if (!isDelegate) {
                synchronized (infoParsed) {
                    infoParsed.put(key, parsed);
                }
            }
            return (T) parsed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getOrNull(GuildSetting<T> key) {
        return getOrNull(key, true);
    }

    public String getInfoRaw(GuildSetting key, boolean allowDelegate) {
        if (key == GuildKey.ALLIANCE_ID) {
            String result = getInfo(key.name(), false);
            if (result != null || !allowDelegate) return result;
        }
        return getInfo(key.name(), allowDelegate);
    }

    public <T> void setInfo(GuildSetting<T> key, T value) {
        checkNotNull(key);
        checkNotNull(value);
        value = key.validate(this, value);
        String toSave = key.toString(value);
        synchronized (infoParsed) {
            setInfo(key.name(), toSave);
            infoParsed.put(key, value);
        }
    }

    public Map<String, String> getInfoMap() {
        initInfo();
        return info;
    }

    public String getCopyPasta(String key, boolean allowDelegate) {
        return getInfo("copypasta." + key, allowDelegate);
    }
    public void setInfo(SheetKeys key, String value) {
        setInfo(key.name(), value);
    }
    public String getOrThrow(SheetKeys key) {
        String value = getInfo(key, true);
        if (value == null) {
            throw new UnsupportedOperationException("No `" + key.name() + "` has been set.");
        }
        return value;
    }
    public String getInfo(SheetKeys key, boolean allowDelegate) {
        return getInfo(key.name(), allowDelegate);
    }
    private String getInfo(String key, boolean allowDelegate) {
        if (info == null) {
            initInfo();
        }
        String value = info.get(key.toLowerCase());
        if (value == null && allowDelegate) {
            Map.Entry<Integer, Long> delegate = getOrNull(GuildKey.DELEGATE_SERVER, false);
            if (delegate != null && delegate.getValue() != getIdLong()) {
                GuildDB delegateDb = GuildDB.get(delegate.getValue());
                if (delegateDb != null) {
                    value = delegateDb.getInfo(key, false);
                }
            }
        }
        return value;
    }

    public void deleteInfo(GuildSetting key) {
        synchronized (infoParsed) {
            deleteInfo(key.name());
            infoParsed.remove(key);
        }
    }

    public void deleteCopyPasta(String key) {
        deleteInfo("copypasta." + key);
    }

    private void deleteInfo(String key) {
        info.remove(key.toLowerCase());
        update("DELETE FROM `INFO` where `key` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, key.toLowerCase());
        });
    }

    public void setCopyPasta(String key, String value) {
        setInfo("copypasta." + key, value);
    }

    private void setInfo(String key, String value) {
        checkNotNull(key);
        checkNotNull(value);
        initInfo();
        synchronized (this) {
            update("INSERT OR REPLACE INTO `INFO`(`key`, `value`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setString(1, key.toLowerCase());
                stmt.setString(2, value);
            });
            info.put(key.toLowerCase(), value);
        }
    }

    private synchronized void initInfo() {
        if (info == null) {
            ConcurrentHashMap<String, String> tmp = new ConcurrentHashMap<>();
            try (PreparedStatement stmt = prepareQuery("select * FROM INFO")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("key");
                        String value = rs.getString("value");
                        tmp.put(key, value);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            this.info = tmp;
        }
    }

    public synchronized void setHandler(GuildHandler handler) {
        if (eventBus == null) {
            this.eventBus = new AsyncEventBus(getGuild().toString(), Runnable::run);
        } else if (this.handler != null) {
            eventBus.unregister(this.handler);
        }
        this.handler = handler;
        this.eventBus.register(this.handler);
    }

    public synchronized GuildHandler getHandler() {
        if (handler == null) {
            this.handler = new GuildHandler(this);
        }
        return handler;
    }

    public Set<DBAlliance> getAlliances() {
        Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(this);
        if (aaIds == null || aaIds.isEmpty()) return Collections.emptySet();
        Set<DBAlliance> alliances = new HashSet<>();
        for (Integer id : aaIds) {
            DBAlliance alliance = DBAlliance.get(id);
            if (alliance == null) continue;
            alliances.add(alliance);
        }
        return alliances;
    }

    public boolean isWhitelisted() {
        if (getIdLong() == Settings.INSTANCE.ROOT_SERVER) return true;
        if (hasCoalitionPermsOnRoot(Coalition.WHITELISTED)) return true;

        // other stuff?
        return false;
    }

    public boolean hasCoalitionPermsOnRoot(Coalition coalition) {
        return hasCoalitionPermsOnRoot(coalition.name().toLowerCase());
    }

    public boolean hasCoalitionPermsOnRoot(String coalition) {
        return hasCoalitionPermsOnRoot(coalition, true);
    }
    public boolean hasCoalitionPermsOnRoot(String coalition, boolean allowDelegate) {
        Guild rootServer = Trocutus.imp().getServer();
        GuildDB rootDB = Trocutus.imp().getGuildDB(rootServer);
        if (this == rootDB) return true;
        Set<Long> coalMembers = rootDB.getCoalitionRaw(coalition);
        if (coalMembers.contains(getIdLong())) {
            return true;
        }
        for (DBAlliance alliance : getAlliances()) {
            if (coalMembers.contains(alliance.getId())) {
                return true;
            }
        }
        if (allowDelegate) {
            GuildDB delegate = getDelegateServer();
            if (delegate != null) {
                return delegate.hasCoalitionPermsOnRoot(coalition);
            }
        }
        return false;
    }

    public GuildDB getDelegateServer() {
        Map.Entry<Integer, Long> delegate = getOrNull(GuildKey.DELEGATE_SERVER, false);
        if (delegate != null && delegate.getValue() != getIdLong()) {
            return Trocutus.imp().getGuildDB(delegate.getValue());
        }
        return null;
    }

    public boolean isDelegateServer() {
        return getDelegateServer() != null;
    }

    private Map<String, Set<Integer>> coalitionToAlliances(Map<String, Set<Long>> input) {
        Map<String, Set<Integer>> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : input.entrySet()) {
            Set<Integer> aaIds = new LinkedHashSet<>();
            for (Long id : entry.getValue()) {
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Trocutus.imp().getGuildDB(id);
                    if (db != null) {
                        aaIds.addAll(db.getAllianceIds());
                    }
                } else {
                    aaIds.add(id.intValue());
                }
            }
            result.put(entry.getKey(), aaIds);
        }
        return result;
    }

    public Set<Integer> getAllianceIds() {
        Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(this);
        if (aaIds == null) return Collections.emptySet();
        return aaIds;
    }

    public Map<String, Set<Long>> getCoalitionsRaw() {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) return faServer.getCoalitionsRaw();
        loadCoalitions();
        return Collections.unmodifiableMap(coalitions);
    }

    public void loadCoalitions() {
        if (coalitions == null) {
            synchronized (this) {
                if (coalitions == null) {
                    coalitions = new ConcurrentHashMap<>();
                    try (PreparedStatement stmt = prepareQuery("select * FROM COALITIONS")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                long allianceId = rs.getLong("alliance_id");
                                String coalition = rs.getString("coalition").toLowerCase();
                                Set<Long> set = coalitions.computeIfAbsent(coalition, k -> new LinkedHashSet<>());
                                set.add(allianceId);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public Map<String, Set<Integer>> getCoalitions() {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null) return faServer.getCoalitions();
        loadCoalitions();
        return coalitionToAlliances(coalitions);
    }

    public Set<Long> getCoalitionRaw(Coalition coalition) {
        return getCoalitionRaw(coalition.name().toLowerCase());
    }


    public Set<Long> getCoalitionRaw(String coalition) {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) return faServer.getCoalitionRaw(coalition);
        synchronized (this) {
            loadCoalitions();
            Set<Long> raw = coalitions.getOrDefault(coalition, Collections.emptySet());
            return Collections.unmodifiableSet(raw);
        }
    }
    public Set<Integer> getCoalition(Coalition coalition) {
        return getCoalition(coalition.name().toLowerCase());
    }

    public Set<Integer> getCoalition(String coalition) {
        coalition = coalition.toLowerCase();
        Set<Integer> result = getCoalitions().get(coalition);
        if (result == null && Coalition.getOrNull(coalition) == null) {
            Coalition namedCoal = Coalition.getOrNull(coalition);
            if (namedCoal == null) {
                GuildDB locutusStats = Trocutus.imp().getRootCoalitionServer();
                if (locutusStats != null) {
                    result = locutusStats.getCoalitions().get(coalition);
                }
            }
        }
        if (result == null) result = Collections.emptySet();
        return new LinkedHashSet<>(result);
    }

    public void removeCoalition(String coalition) {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.removeCoalition(coalition);
            return;
        }
        synchronized (this) {
            if (coalitions != null) {
                coalitions.remove(coalition.toLowerCase());
            }
        }
        update("DELETE FROM `COALITIONS` WHERE LOWER(coalition) = LOWER(?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, coalition);
        });
    }

    public void addRole(Roles locutusRole, Role discordRole, long allianceId) {
        addRole(locutusRole, discordRole.getIdLong(), allianceId);
    }

    public void addRole(Roles locutusRole, long discordRole, long allianceId) {
        deleteRole(locutusRole, allianceId, false);
        roleToAccountToDiscord.computeIfAbsent(locutusRole, f -> new ConcurrentHashMap<>()).put(allianceId, discordRole);
        update("INSERT OR REPLACE INTO `ROLES2`(`role`, `alias`, `alliance`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, locutusRole.getId());
            stmt.setLong(2, discordRole);
            stmt.setLong(3, allianceId);
        });
    }
    public void deleteRole(Roles role, long alliance) {
        deleteRole(role, alliance, true);
    }
    public void deleteRole(Roles role, long alliance, boolean updateCache) {
        if (updateCache) {
            Map<Long, Long> existing = roleToAccountToDiscord.get(role);
            if (existing != null) {
                existing.remove(alliance);
            }
        }
        update("DELETE FROM `ROLES2` WHERE `role` = ? AND `alliance` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, role.getId());
            stmt.setLong(2, alliance);
        });
    }

    public void deleteRole(Roles role) {
        roleToAccountToDiscord.remove(role);
        update("DELETE FROM `ROLES2` WHERE `role` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, role.getId());
        });
    }

    public  Map<Roles, Map<Long, Long>> getMappingRaw() {
        return Collections.unmodifiableMap(roleToAccountToDiscord);
    }

    public Map<Long, Role> getAccountMapping(Roles role) {
        loadRoles();
        Map<Long, Long> existing = roleToAccountToDiscord.get(role);
        if (existing == null || existing.isEmpty()) return Collections.emptyMap();

        Map<Long, Role> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : existing.entrySet()) {
            Role discordRole = guild.getRoleById(entry.getValue());
            if (discordRole != null) {
                result.put(entry.getKey(), discordRole);
            }
        }
        return result;
    }

    private void loadRoles() {
        if (roleToAccountToDiscord.isEmpty() && !cachedRoleAliases) {
            synchronized (roleToAccountToDiscord) {
                if (cachedRoleAliases) return;
                cachedRoleAliases = true;
                try (PreparedStatement stmt = prepareQuery("select * FROM ROLES2")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                Roles role = Roles.getRoleById(rs.getInt("role"));
                                long alias = rs.getLong("alias");
                                long alliance = rs.getLong("alliance");
                                roleToAccountToDiscord.computeIfAbsent(role, f -> new ConcurrentHashMap<>()).put(alliance, alias);
                            } catch (IllegalArgumentException ignore) {
                            }
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<Long, Role> getRoleMap(Roles role) {
        loadRoles();
        Map<Long, Long> roleIds = roleToAccountToDiscord.get(role);
        if (roleIds == null) return Collections.emptyMap();
        Map<Long, Role> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : roleIds.entrySet()) {
            Role discordRole = guild.getRoleById(entry.getValue());
            if (discordRole != null) {
                result.put(entry.getKey(), discordRole);
            }
        }
        return result;
    }

    public Role getRole(Roles role, Long allianceOrNull) {
        loadRoles();
        Map<Long, Long> roleIds = roleToAccountToDiscord.get(role);
        if (roleIds == null) return null;
        Long mapping = null;
        if (allianceOrNull != null) {
            mapping = roleIds.get(allianceOrNull);
        }
        if (mapping == null) {
            mapping = roleIds.get(0L);
        }
        if (mapping != null) {
            return guild.getRoleById(mapping);
        }
        return null;
    }

    public boolean isValidAlliance() {
        return !getAlliances().isEmpty();
    }

    public AllianceList getAllianceList() {
        return new AllianceList(getAlliances());
    }
}
