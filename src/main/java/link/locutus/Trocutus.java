package link.locutus;

import com.google.common.eventbus.AsyncEventBus;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.TrouncedApi;
import link.locutus.core.api.ScrapeKingdomUpdater;
import link.locutus.core.api.pojo.Api;
import link.locutus.core.command.CommandManager;
import link.locutus.core.command.TrouncedSlash;
import link.locutus.core.db.TrouncedDB;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.event.Event;
import link.locutus.core.event.MainListener;
import link.locutus.core.settings.Settings;
import link.locutus.util.GuildShardManager;
import link.locutus.util.RateLimitUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

public class Trocutus extends ListenerAdapter {
    private final ExecutorService executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private final TrouncedApi apiPool;
    private final TrouncedApi rootApi;
    private Auth rootAuth;
    private ScrapeKingdomUpdater scraper;
    private TrouncedSlash slashCommands;
    private final Map<Long, GuildDB> guildDatabases = new ConcurrentHashMap<>();
    private Guild server;

    public static void main(String[] args) throws SQLException, InterruptedException, IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        Trocutus instance = new Trocutus();
        instance.start();
        Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());
    }

    private static Trocutus INSTANCE;
    private final CommandManager commandManager;
    private final Logger logger;
    private GuildShardManager manager;
    private final AsyncEventBus eventBus;
    private final TrouncedDB rootDb;
    public Trocutus() throws SQLException, IOException {
        if (INSTANCE != null) throw new IllegalStateException("Already running.");
        INSTANCE = this;

        if (Settings.INSTANCE.ROOT_SERVER <= 0) throw new IllegalStateException("Please set ROOT_SERVER in " + Settings.INSTANCE.getDefaultFile());
        String cmdPrefix = Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX;
        if (cmdPrefix.length() != 1) throw new IllegalStateException("COMMAND_PREFIX must be 1 character in " + Settings.INSTANCE.getDefaultFile());
        if (cmdPrefix.matches("[._~]")) {
            throw new IllegalStateException("COMMAND_PREFIX cannot be `.` or `_` or `~` in " + Settings.INSTANCE.getDefaultFile());
        }

        this.logger = Logger.getLogger("LOCUTUS");
        this.eventBus = new AsyncEventBus("locutus", Runnable::run);
        this.executor = Executors.newCachedThreadPool();
        this.scheduler = new ScheduledThreadPoolExecutor(256);
        this.rootDb = new TrouncedDB();
        this.manager = new GuildShardManager();

        this.commandManager = new CommandManager(this);
        this.commandManager.registerCommands();
        if (Settings.INSTANCE.BOT_TOKEN.isEmpty()) {
            throw new IllegalStateException("Please set BOT_TOKEN in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            throw new IllegalStateException("Please set API_KEY_PRIMARY in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.USERNAME.isEmpty() || Settings.INSTANCE.PASSWORD.isEmpty()) {
            throw new IllegalStateException("Please set USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
        }

        List<String> pool = new ArrayList<>();
        pool.addAll(Settings.INSTANCE.API_KEY_POOL);
        if (pool.isEmpty()) {
            pool.add(Settings.INSTANCE.API_KEY_PRIMARY);
        }

        if (Settings.INSTANCE.ADMIN_USER_ID <= 0) {
            throw new IllegalStateException("Please set ADMIN_USER_ID in " + Settings.INSTANCE.getDefaultFile());
        }

        this.rootAuth = new Auth(Settings.INSTANCE.ADMIN_USER_ID, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        this.scraper = new ScrapeKingdomUpdater(this.rootAuth, this.rootDb);
        for (DBKingdom kingdom : this.scraper.updateSelf()) {
            if (Settings.INSTANCE.ADMIN_USER_ID > 0) {
                rootDb.saveUser(Settings.INSTANCE.ADMIN_USER_ID, kingdom);
            }
        }

        Settings.INSTANCE.NATION_ID = -1;
        Integer nationNameFromKey = rootDb.getKingdomFromApiKey(Settings.INSTANCE.API_KEY_PRIMARY);
        if (nationNameFromKey == null) {
            throw new IllegalStateException("Could not get NATION_ID from key. Please ensure a valid API_KEY is set in " + Settings.INSTANCE.getDefaultFile());
        }
        Settings.INSTANCE.NATION_ID = nationNameFromKey;

        this.apiPool = new TrouncedApi(ApiKeyPool.builder().addKeys(pool).build());
        this.rootApi = new TrouncedApi(ApiKeyPool.builder().addKeyUnsafe(Settings.INSTANCE.API_KEY_PRIMARY).build());

        if (Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS) {
            this.registerEvents();
        }
    }

    public static Trocutus imp() {
        return INSTANCE;
    }

    public void registerEvents() {
        eventBus.register(new MainListener());
    }

    public void start() throws InterruptedException {
        if (Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT) {
            JDABuilder builder = JDABuilder.createLight(Settings.INSTANCE.BOT_TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
                this.slashCommands = new TrouncedSlash(this);
                builder.addEventListeners(slashCommands);
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
                builder.addEventListeners(commandManager);
            }
            builder
                    .setBulkDeleteSplittingEnabled(true)
                    .setCompression(Compression.ZLIB)
                    .setMemberCachePolicy(MemberCachePolicy.ALL);
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MEMBERS) {
                builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_PRESENCES) {
                builder.enableIntents(GatewayIntent.GUILD_PRESENCES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGES) {
                builder.enableIntents(GatewayIntent.GUILD_MESSAGES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGE_REACTIONS) {
                builder.enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.DIRECT_MESSAGES) {
                builder.enableIntents(GatewayIntent.DIRECT_MESSAGES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
                builder.enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.MEMBER_OVERRIDES) {
                builder.enableCache(CacheFlag.MEMBER_OVERRIDES);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.ONLINE_STATUS) {
                builder.enableCache(CacheFlag.ONLINE_STATUS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
                builder.enableCache(CacheFlag.EMOJI);
            }
//                Set<Long> whitelist = Settings.INSTANCE.Discord.Guilds.GET().WHITELIST();
//                long[] whitelistArr = new long[whitelist.size()];
//            int numShards = 10;
//            for (int i = 0; i < numShards; i++) {
//                long guildId = whitelistArr[i];
//                JDA instance = builder.useSharding(i, numShards).build();
//                manager.put(instance);
//            }
//            rootInstance = builder.useSharding(whitelistArr.length, whitelistArr.length + 1).build();

            JDA jda = builder.build();
            manager.put(jda);
            manager.awaitReady();

            long appId = jda.getSelfUser().getApplicationIdLong();
            if (appId > 0) {
                Settings.INSTANCE.APPLICATION_ID = appId;
            }

            if (this.slashCommands != null) {
                this.slashCommands.setupCommands();
            }

            this.server = manager.getGuildById(Settings.INSTANCE.ROOT_SERVER);

            if (Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP) {
                try {
                    initGuildDB();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
                initRepeatingTasks();
            }

            for (long guildId : Settings.INSTANCE.MODERATION.BANNED_GUILDS) {
                Guild guild = manager.getGuildById(guildId);
                if (guild != null) {
                    RateLimitUtil.queue(guild.leave());
                }
            }

            if (!Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.isEmpty()) {
                for (GuildDB value : getGuildDatabases().values()) {
                    Guild guild = value.getGuild();
                    if (!guild.isLoaded()) continue;
                    long owner = guild.getOwnerIdLong();
                    Set<DBKingdom> kingdoms = rootDb.getKingdomFromUser(owner);
                    for (DBKingdom kingdom : kingdoms) {
                        if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(kingdom.getAlliance_id())) {
                            RateLimitUtil.queue(guild.leave());
                        }
                    }
                }
            }
        }
    }

    private void initRepeatingTasks() {

    }

    public Map<Long, GuildDB> getGuildDatabases() {
        return initGuildDB();
    }

    public Map<Long, GuildDB> initGuildDB() {
        synchronized (guildDatabases) {
            if (guildDatabases.isEmpty()) {
                for (Guild guild : manager.getGuilds()) {
                    GuildDB db = getGuildDB(guild.getIdLong(), false);
                }
            }
            return guildDatabases;
        }
    }

    public GuildDB getGuildDB(Guild guild) {
        return getGuildDB(guild.getIdLong());
    }

    public GuildDB getGuildDB(long guildId) {
        return getGuildDB(guildId, true);
    }
    public GuildDB getGuildDB(long guildId, boolean cache) {
        if (cache) initGuildDB();
        GuildDB db = guildDatabases.get(guildId);
        if (db != null) {
            return db;
        }
        Guild guild = manager.getGuildById(guildId);
        if (guild == null) return null;
        synchronized (guildDatabases) {
            db = guildDatabases.get(guildId);
            if (db != null) return db;

            try {
                db = new GuildDB(guild);
                guildDatabases.put(guildId, db);
                return db;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }

    public GuildShardManager getDiscordApi() {
        return manager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }


    public Guild getServer() {
        return server;
    }

    public GuildDB getRootCoalitionServer() {
        GuildDB locutusStats = getGuildDB(Settings.INSTANCE.ROOT_COALITION_SERVER);
        return locutusStats;
    }

    public GuildDB getGuildDBByAA(int allianceId) {
        if (allianceId == 0) return null;
        for (Map.Entry<Long, GuildDB> entry : initGuildDB().entrySet()) {
            GuildDB db = entry.getValue();
            Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(db, false);
            if (aaIds != null && aaIds.contains(allianceId)) {
                return db;
            }
        }
        return null;
    }

    public TrouncedDB getDB() {
        return rootDb;
    }

    public Auth rootAuth() {
        if (rootAuth == null) {
            throw new IllegalStateException("user/pass not provided in config.yml");
        }
        return rootAuth;
    }

    public void runEvent(Event event) {
        eventBus.post(event);
    }

    public <T extends Event> void runEvents(Set<T> events) {
        for (Event event : events) {
            eventBus.post(event);
        }
    }
}
