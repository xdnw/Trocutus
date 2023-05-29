package link.locutus.core.db.guild;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import link.locutus.Trocutus;
import link.locutus.core.api.Auth;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.db.DBMain;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.guild.Announcement;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.core.db.entities.kingdom.KingdomMeta;
import link.locutus.core.db.entities.kingdom.KingdomOrAllianceOrGuild;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.entities.UnmaskedReason;
import link.locutus.core.db.guild.interview.IACategory;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.db.guild.role.AutoRoleTask;
import link.locutus.core.db.guild.role.IAutoRoleTask;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.builder.RankBuilder;
import link.locutus.util.scheduler.ThrowingConsumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class GuildDB extends DBMain implements KingdomOrAllianceOrGuild {
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
    private IACategory iaCat;


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
        {
            String query = "CREATE TABLE IF NOT EXISTS `ANNOUNCEMENTS2` (`ann_id` INTEGER PRIMARY KEY AUTOINCREMENT, `sender` BIGINT NOT NULL, `active` BOOLEAN NOT NULL, `title` VARCHAR NOT NULL, `content` VARCHAR NOT NULL, `replacements` VARCHAR NOT NULL, `filter` VARCHAR NOT NULL, `date` BIGINT NOT NULL)";
            executeStmt(query);

            String query2 = "CREATE TABLE IF NOT EXISTS `ANNOUNCEMENTS_PLAYER2` (`receiver` INT NOT NULL, `ann_id` INT NOT NULL, `active` BOOLEAN NOT NULL, `diff` BLOB NOT NULL, PRIMARY KEY(receiver, ann_id), FOREIGN KEY(ann_id) REFERENCES ANNOUNCEMENTS2(ann_id))";
            executeStmt(query2);
        }
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

    @Override
    public String getUrl(String slug) {
        List<Invite> invites = RateLimitUtil.complete(guild.retrieveInvites());
        for (Invite invite : invites) {
            if (invite.getMaxUses() == 0) {
                return invite.getUrl();
            }
        }
        return null;
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


    public Function<DBKingdom, Boolean> getCanRaid() {
        Integer topX = getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
        if (topX == null) topX = 0;
        return getCanRaid(topX, true);
    }

    public Function<DBKingdom, Boolean> getCanRaid(int topX, boolean checkTreaties) {
        Set<Integer> dnr = new HashSet<>(getCoalition(Coalition.ALLIES));
        dnr.addAll(getCoalition("dnr"));
        dnr.addAll(getAllianceIds());
        Set<Integer> dnr_active = new HashSet<>(getCoalition("dnr_active"));
        Set<Integer> dnr_member = new HashSet<>(getCoalition("dnr_member"));
        Set<Integer> can_raid = new HashSet<>(getCoalition(Coalition.CAN_RAID));
        can_raid.addAll(getCoalition(Coalition.ENEMIES));
        Set<Integer> can_raid_inactive = new HashSet<>(getCoalition("can_raid_inactive"));
        Map<Integer, Long> dnr_timediff_member = new HashMap<>();
        Map<Integer, Long> dnr_timediff_app = new HashMap<>();

        if (checkTreaties) {
            for (int allianceId : getAllianceIds()) {
                Map<Integer, DBTreaty> treaties = Trocutus.imp().getDB().getTreaties(allianceId);
                for (Map.Entry<Integer, DBTreaty> entry : treaties.entrySet()) {
                    if (entry.getValue().getType() == TreatyType.WAR || entry.getValue().getType() == TreatyType.LAP) continue;
                    dnr.add(entry.getKey());
                }
            }
        }

        if (topX > 0) {
            DBAlliance t = null;
            Map<Integer, Double> aas = new RankBuilder<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true)).group(DBKingdom::getAlliance_id).sumValues(f -> (double) f.getScore()).sort().get();
            for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
                if (entry.getKey() == 0) continue;
                if (topX-- <= 0) break;
                int allianceId = entry.getKey();
                Map<Integer, DBTreaty> treaties = Trocutus.imp().getDB().getTreaties(allianceId);
                for (Map.Entry<Integer, DBTreaty> aaTreatyEntry : treaties.entrySet()) {
                    switch (aaTreatyEntry.getValue().getType()) {
                        case MDP:
                            dnr_member.add(aaTreatyEntry.getKey());
                    }
                }
                dnr_member.add(allianceId);
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : getCoalitions().entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.startsWith("dnr_")) continue;

            String[] split = name.split("_");
            if (split.length < 2 || split[split.length - 1].isEmpty()) continue;
            String timeStr = split[split.length - 1];
            if (!Character.isDigit(timeStr.charAt(0))) continue;

            long time = TimeUtil.timeToSec(timeStr) * 1000L;
            for (Integer aaId : entry.getValue()) {
                if (split.length == 3 && split[1].equalsIgnoreCase("member")) {
                    dnr_timediff_member.put(aaId, time);
                } else if (true || split[1].equalsIgnoreCase("applicant")) {
                    dnr_timediff_app.put(aaId, time);
                }

            }
        }
        Set<Integer> enemies = getCoalition("enemies");
        enemies.addAll(getCoalition(Coalition.CAN_RAID));

        Function<DBKingdom, Boolean> canRaid = new Function<DBKingdom, Boolean>() {
            @Override
            public Boolean apply(DBKingdom enemy) {
                if (enemy.getAlliance_id() == 0) return true;
                if (can_raid.contains(enemy.getAlliance_id())) return true;
                if (can_raid_inactive.contains(enemy.getAlliance_id()) && enemy.getActive_m() > 10000) return true;
                if (enemies.contains(enemy.getAlliance_id())) return true;
                if (dnr.contains(enemy.getAlliance_id())) return false;
                if (enemy.getActive_m() < 10000 && dnr_active.contains(enemy.getAlliance_id())) return false;
                if ((enemy.getActive_m() < 10000 || enemy.getPosition().ordinal() > Rank.APPLICANT.ordinal()) && dnr_member.contains(enemy.getAlliance_id())) return false;

                long requiredInactive = -1;

                {
                    Long timeDiff = dnr_timediff_app.get(enemy.getAlliance_id());
                    if (timeDiff != null) {
                        requiredInactive = enemy.getPosition().ordinal() > Rank.APPLICANT.ordinal() ? Long.MAX_VALUE : timeDiff;
                    }
                }
                if (enemy.getPosition().ordinal() > Rank.APPLICANT.ordinal()) {
                    Long timeDiff = dnr_timediff_member.get(enemy.getAlliance_id());
                    if (timeDiff != null) {
                        requiredInactive = timeDiff;
                    }
                }

                long msInactive = enemy.getActive_m() * 60 * 1000L;

                return (msInactive > requiredInactive);
            }
        };

        return canRaid;
    }

    public List<GuildSetting> listInaccessibleChannelKeys() {
        List<GuildSetting> inaccessible = new ArrayList<>();
        for (GuildSetting key : GuildKey.values()) {
            String valueStr = getInfoRaw(key, false);
            if (valueStr == null) continue;
            Object value = key.parse(this, valueStr);
            if (value == null) {
                inaccessible.add(key);
                continue;
            }
            if (value instanceof GuildMessageChannel) {
                GuildMessageChannel channel = (GuildMessageChannel) value;
                if (!channel.canTalk()) {
                    inaccessible.add(key);
                }
            }
        }
        return inaccessible;
    }
    public void unsetInaccessibleChannels() {
        for (GuildSetting key : listInaccessibleChannelKeys()) {
            deleteInfo(key);
        }
    }


    public int addAnnouncement(User sender, String title, String content, String replacements, String filter) {
        String query = "INSERT INTO `ANNOUNCEMENTS2`(`sender`, `active`, `title`, `content`, `replacements`, `filter`, `date`) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, sender.getIdLong());
            stmt.setBoolean(2, true);
            stmt.setString(3, title);
            stmt.setString(4, content);
            stmt.setString(5, replacements);
            stmt.setString(6, filter);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return id;
                }
            }
            throw new IllegalArgumentException("Error creating announcement");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addPlayerAnnouncement(DBKingdom receiver, int annId, byte[] diff) {
        String query = "INSERT INTO ANNOUNCEMENTS_PLAYER2(`receiver`, `ann_id`, `active`, `diff`) VALUES(?, ?, ?, ?)";
        update(query , (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, receiver.getId());
            stmt.setInt(2, annId);
            stmt.setBoolean(3, true);
            stmt.setBytes(4, diff);
        });
    }

    public List<Announcement> getAnnouncements() {
        List<Announcement> result = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM ANNOUNCEMENTS2 ORDER BY date desc")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new Announcement(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Announcement getAnnouncement(int ann_id) {
        List<Announcement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS2 WHERE ann_id = ? ORDER BY date desc",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, ann_id),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        result.add(new Announcement(rs));
                    }
                });
        return result.isEmpty() ? null : result.get(0);
    }

    public Map<Integer, Announcement> getAnnouncementsByIds(Set<Integer> ids) {
        Map<Integer, Announcement> result = new LinkedHashMap<>();
        query("select * FROM ANNOUNCEMENTS2 WHERE ann_id in " + StringMan.getString(ids) + " ORDER BY date desc",
                f -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        Announcement ann = new Announcement(rs);
                        result.put(ann.id, ann);
                    }
                });
        return result;
    }

    public void setAnnouncementActive(int ann_id, boolean value) {
        update("UPDATE ANNOUNCEMENTS2 SET active = ? WHERE ann_id = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setBoolean(1, value);
                stmt.setInt(2, ann_id);
            }
        });
    }

    public void setAnnouncementActive(int ann_id, int nation_id, boolean active) {
        update("UPDATE ANNOUNCEMENTS_PLAYER2 SET active = ? WHERE ann_id = ? AND receiver = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setBoolean(1, active);
                stmt.setInt(2, ann_id);
                stmt.setInt(3, nation_id);
            }
        });
    }

    public Map<Announcement, List<Announcement.PlayerAnnouncement>> getAllPlayerAnnouncements(boolean allowArchived) {
        List<Announcement> announcements = getAnnouncements();
        Map<Integer, Announcement> announcementsById = new LinkedHashMap<>();
        for (Announcement announcement : announcements) {
            if (!announcement.active && !allowArchived) continue;
            announcementsById.put(announcement.id, announcement);
        }

        Map<Announcement, List<Announcement.PlayerAnnouncement>> result = new LinkedHashMap<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 ORDER BY ann_id desc",
                f -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        int annId = rs.getInt("ann_id");
                        Announcement announcement = announcementsById.get(annId);
                        if (announcement == null) continue;
                        Announcement.PlayerAnnouncement plrAnn = new Announcement.PlayerAnnouncement(this, announcement, rs);
                        if (!allowArchived && !plrAnn.active) continue;
                        result.computeIfAbsent(announcement, f -> new ArrayList<>()).add(plrAnn);
                    }
                });
        return result;
    }

    public List<Announcement.PlayerAnnouncement> getPlayerAnnouncementsByAnnId(int ann_id) {
        Announcement announcement = getAnnouncement(ann_id);
        if (announcement == null) return null;
        List<Announcement.PlayerAnnouncement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 WHERE ann_id = ? ORDER BY ann_id desc",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, ann_id),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        result.add(new Announcement.PlayerAnnouncement(this, announcement, rs));
                    }
                });
        return result;
    }

    public List<Announcement.PlayerAnnouncement> getPlayerAnnouncementsByKingdom(int nationId, boolean requireActive) {
        List<Announcement.PlayerAnnouncement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 WHERE receiver = ?" + (requireActive ? " AND `active` = true" : "") + " ORDER BY ann_id desc",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, nationId),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        result.add(new Announcement.PlayerAnnouncement(this, rs));
                    }
                });
        Set<Integer> annIds = result.stream().map(f -> f.ann_id).collect(Collectors.toSet());
        Map<Integer, Announcement> announcements = getAnnouncementsByIds(annIds);
        Iterator<Announcement.PlayerAnnouncement> iter = result.iterator();
        while (iter.hasNext()) {
            Announcement.PlayerAnnouncement plrAnn = iter.next();
            Announcement announcement = announcements.get(plrAnn.ann_id);
            if (announcement != null && (announcement.active || !requireActive)) {
                plrAnn.setParent(announcement);
            } else if (requireActive) {
                iter.remove();
            }
        }
        return result;
    }

    public Auth getMailAuth() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isAllianceId(int id) {
        return getAllianceIds().contains(id);
    }

    public void addCoalition(long allianceId, Coalition coalition) {
        addCoalition(allianceId, coalition.name().toLowerCase());
    }

    public void addCoalition(long allianceId, String coalition) {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.addCoalition(allianceId, coalition);
            return;
        }
        {
            loadCoalitions();
            if (coalitions == null) coalitions = new ConcurrentHashMap<>();
            coalitions.computeIfAbsent(coalition.toLowerCase(), f -> new LinkedHashSet<>()).add((long) allianceId);

            update("INSERT OR IGNORE INTO `COALITIONS`(`alliance_id`, `coalition`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, allianceId);
                stmt.setString(2, coalition.toLowerCase());
            });
        }
    }

    public void removeCoalition(long allianceId, Coalition coalition) {
        removeCoalition(allianceId, coalition.name().toLowerCase());
    }

    public void removeCoalition(long allianceId, String coalition) {
        GuildDB faServer = getOrNull(GuildKey.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.removeCoalition(allianceId, coalition);
            return;
        }
        {
            Set<Long> set = coalitions.getOrDefault(coalition, Collections.emptySet());
            if (set != null) set.remove(allianceId);
            update("DELETE FROM `COALITIONS` WHERE `alliance_id` = ? AND LOWER(coalition) = LOWER(?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, allianceId);
                stmt.setString(2, coalition.toLowerCase());
            });
        }
    }

    public IAutoRoleTask getAutoRoleTask() {
        if (this.autoRoleTask == null) {
            synchronized (this) {
                if (this.autoRoleTask == null) {
                    this.autoRoleTask = new AutoRoleTask(getGuild(), this);
                }
            }
        }
        return autoRoleTask;
    }

    public Set<Integer> getAllies(boolean fetchTreaties) {
        Set<Integer> allies = getCoalition(Coalition.ALLIES);
        Set<Integer> aaIds = getAllianceIds();
        if (!aaIds.isEmpty()) {
            allies.addAll(aaIds);
            if (fetchTreaties) {
                for (DBAlliance alliance : getAlliances()) {
                    for (Map.Entry<Integer, DBTreaty> entry : alliance.getTreaties().entrySet()) {
                        switch (entry.getValue().getType()) {
                            case MDP:
                                allies.add(entry.getKey());
                        }
                    }
                }
            }
        }
        return allies;
    }

    public synchronized IACategory getExistingIACategory() {
        return iaCat;
    }

    public synchronized IACategory getIACategory() {
        return getIACategory(false, true, false);
    }
    public synchronized IACategory getIACategory(boolean create, boolean allowDelegate, boolean throwError) {
        GuildDB delegate = allowDelegate ? getDelegateServer() : null;
        if (delegate != null && delegate.iaCat != null) {
            return delegate.iaCat;
        }
        if (this.iaCat == null) {
            boolean hasInterview = false;
            for (Category category : guild.getCategories()) {
                if (category.getName().toLowerCase().startsWith("interview")) {
                    hasInterview = true;
                }
            }

            if (hasInterview) {
                this.iaCat = new IACategory(this);
                this.iaCat.load();
            }
        }
        if (iaCat == null && delegate != null) {
            iaCat = delegate.getIACategory(false, false, throwError);
        }
        if (iaCat == null && create) {
            Category category = RateLimitUtil.complete(guild.createCategory("interview"));
            this.iaCat = new IACategory(this);
            this.iaCat.load();
        }
        if (iaCat == null && throwError) {
            throw new IllegalStateException("No `interview` category found");
        }
        return this.iaCat;
    }

    public void setMeta(long userId, KingdomMeta key, byte[] value) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.setMeta(userId, key, value);
            return;
        }
        checkNotNull(key);
        checkNotNull(value);
        update("INSERT OR REPLACE INTO `NATION_META`(`id`, `key`, `meta`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, userId);
            stmt.setInt(2, key.ordinal());
            stmt.setBytes(3, value);
        });
    }

    public Map<DBKingdom, byte[]> getKingdomMetaMap(KingdomMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getKingdomMetaMap(key);
        }
        Map<DBKingdom, byte[]> result = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where key = ?")) {
            stmt.setInt(1, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long nationId = rs.getInt("id");
                    if (nationId < Integer.MAX_VALUE) {
                        DBKingdom nation = Trocutus.imp().getDB().getKingdom((int) nationId);
                        byte[] data = rs.getBytes("meta");

                        result.put(nation, data);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public ByteBuffer getKingdomMeta(int nationId, KingdomMeta key) {
        return getMeta((long) nationId, key);
    }

    public Map<Long, ByteBuffer> getAllMeta(KingdomMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getAllMeta(key);
        }
        Map<Long, ByteBuffer> results = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where AND key = ?")) {
            stmt.setInt(1, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    ByteBuffer buf = ByteBuffer.wrap(rs.getBytes("meta"));
                    results.put(id, buf);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public ByteBuffer getMeta(long userId, KingdomMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getMeta(userId, key);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where id = ? AND key = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return ByteBuffer.wrap(rs.getBytes("meta"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteMeta(long userId, KingdomMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.deleteMeta(userId, key);
            return;
        }
        update("DELETE FROM NATION_META where id = ? AND key = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, userId);
                stmt.setInt(2, key.ordinal());
            }
        });
    }

    public Map<String, String> getCopyPastas(@Nullable Member memberOrNull) {
        Map<String, String> options = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getInfoMap().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("copypasta.")) continue;
            if (memberOrNull != null && !getMissingCopypastaPerms(key, memberOrNull).isEmpty()) continue;
            options.put(key.substring("copypasta.".length()), entry.getValue());
        }
        return options;
    }

    /**
     * Returns the missing copypasta permissions for the given member.
     * @param key
     * @param member
     * @return
     */
    public Set<String> getMissingCopypastaPerms(String key, Member member) {
        String[] split = key.split("\\.");
        Set<String> noRoles = new HashSet<>();
        if (split.length > 1) {
            for (int i = 0; i < split.length - 1; i++) {
                String roleName = split[i];
                if (roleName.equalsIgnoreCase("copypasta")) continue;

                Roles role = Roles.parse(roleName);
                Role discRole = DiscordUtil.getRole(member.getGuild(), roleName);
                if ((role != null && role.has(member)) || (discRole != null && member.getRoles().contains(discRole))) {
                    return Collections.emptySet();
                }
                if (role != null) noRoles.add(role.toString());
                if (discRole != null) noRoles.add(discRole.getName());
            }
        }
        return noRoles;
    }

    public Map<Member, UnmaskedReason> getMaskedNonMembers() {
        if (!hasAlliance()) return Collections.emptyMap();

        List<Role> roles = new ArrayList<>();
        roles.add(Roles.MEMBER.toRole(this));
        roles.removeIf(Objects::isNull);

        Map<Member, UnmaskedReason> result = new HashMap<>();

        Set<Integer> allowedAAs = new HashSet<>(getAllianceIds());
        for (Role role : roles) {
            List<Member> members = guild.getMembersWithRoles(role);
            for (Member member : members) {
                Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(member.getUser());
                if (kingdoms.isEmpty()) {
                    result.put(member, UnmaskedReason.NOT_REGISTERED);
                    continue;
                }
                Set<Integer> kingdomAAIds = kingdoms.values().stream().map(DBKingdom::getAlliance_id).collect(Collectors.toSet());
                int minActive = Integer.MAX_VALUE;
                Rank highestRank = Rank.NONE;
                for (DBKingdom kingdom : kingdoms.values()) {
                    if (allowedAAs.contains(kingdom.getAlliance_id())) {
                        minActive = Math.min(minActive, kingdom.getActive_m());
                        if (kingdom.getPosition().ordinal() > highestRank.ordinal()) {
                            highestRank = kingdom.getPosition();
                        }
                    }
                }
                if (minActive == Integer.MAX_VALUE) result.put(member, UnmaskedReason.NOT_IN_ALLIANCE);
                else if (highestRank.ordinal() <= 1) result.put(member, UnmaskedReason.APPLICANT);
                else if (minActive > 20000) result.put(member, UnmaskedReason.INACTIVE);
            }
        }
        return result;
    }

    public Set<String> findCoalitions(int aaId) {
        Set<String> coalitions = new LinkedHashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : getCoalitions().entrySet()) {
            if (entry.getValue().contains(aaId)) {
                coalitions.add(entry.getKey());
            }
        }
        return coalitions;
    }
}
