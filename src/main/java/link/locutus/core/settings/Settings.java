package link.locutus.core.settings;

import org.snakeyaml.custom_config.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Settings extends Config {
    @Ignore
    @Final
    public static final Settings INSTANCE = new Settings();


    @Ignore
    @Final
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.52";

    @Comment({"The discord token of the bot (required)",
            "Found on the bot section of the Discord Developer Portal"})
    public String BOT_TOKEN = "";

    @Comment({"The discord client secret of the bot (required)",
            "Found on the OAuth2 section of the Discord Developer Portal"})
    public String CLIENT_SECRET = "";

    @Comment({"The guild id of the management server for this bot (you should be owner here)",
            "See: https://support.discord.com/hc/en-us/articles/206346498"})
    public long ROOT_SERVER = 0;

    @Comment({"The guild id of the management server for this bot (you should be owner here)",
            "See: https://support.discord.com/hc/en-us/articles/206346498"})
    public long ROOT_COALITION_SERVER = 0;

    @Comment("Your P&W username (optional, but recommended)")
    public String USERNAME = "";
    @Comment("Your P&W password (optional, but recommended)")
    public String PASSWORD = "";

    @Comment("Your api key (generated if username/password is set)")
    public String API_KEY_PRIMARY = "";

    @Comment({"A list of api keys the bot can use for requests (optional)",
            "See: `/admin validateApiKeys`"})
    public List<String> API_KEY_POOL = Arrays.asList();

    @Comment({"The discord id of the bot (generated)",
            "Found in the General Information section of the Discord Developer Portal"})
    public long APPLICATION_ID = 0;

    @Comment("The discord user id of the admin user. (generated)")
    public long ADMIN_USER_ID = -1;

    @Comment("The nation name of the admin. (generated from login or api key)")
    public int NATION_ID = -1; // generated

    @Comment({"Your API key from <https://platform.openai.com/account/api-keys> (optional)"})
    public String OPENAI_API_KEY = "";

    ////////////////////////////////////////////////////

    @Create
    public ENABLED_COMPONENTS ENABLED_COMPONENTS;
    @Create
    public TASKS TASKS;
    @Create
    public DISCORD DISCORD;
    @Create
    public WEB WEB;
    @Create
    public MODERATION MODERATION;
    @Create
    public DATABASE DATABASE;

    //////////////////////////////////////////////

    public static class ENABLED_COMPONENTS {

        @Comment({"If the discord bot is enabled at all",
                "- Other components require the discord bot to be enabled"})
        public boolean DISCORD_BOT = true;
        @Comment("If message commands e.g. `!who` is enabled")
        public boolean MESSAGE_COMMANDS = true;
        @Comment("If slash `/` commands are enabled (WIP)")
        public boolean SLASH_COMMANDS = true;
        @Comment({"If the web interface is enabled",
                "- If enabled, also configure the web section below"
        })
        public boolean WEB = false;

        @Comment({"Should databases be initialized on startup",
                "false = they are initialized as needed (I havent done much optimization here, so thats probably shortly after startup anyway, lol)"})
        public boolean CREATE_DATABASES_ON_STARTUP = true;

        @Comment({"Should any repeating tasks be enabled",
                "- See the task section to disable/adjust individual tasks"})
        public boolean REPEATING_TASKS = true;

        @Comment("If P&W events should be enabled")
        public boolean EVENTS = true;
    }

    @Comment({
            "How often in seconds a task is run (set to 0 to disable)",
            "Note: Politics and war is rate limited. You may experience issues if you run tasks too frequently"
    })
    public static class TASKS {
        @Create
        public TURN_TASKS TURN_TASKS;

        public static class TURN_TASKS {
        }

        @Create
        public ConfigBlock<MAIL> MAIL;

        @Comment({"Fetch ingame mail of an authenticated nation and post it to a channel",
                "Set the values to 0 to disable",
                "Copy the default block for multiple users",
                "The tasks will fail if a user is not authenticated (user/pass)"})
        @BlockName("default")
        public static class MAIL extends ConfigBlock {
            public String NATION_NAME = "locutus";
            public int FETCH_INTERVAL_SECONDS = 62;
            public long CHANNEL_ID = -1;
        }
    }

    @Comment({"Configure the discord build configuration"})
    public static class DISCORD {
        @Create
        public INTENTS INTENTS;
        @Create
        public CACHE CACHE;

        @Create
        public COMMAND COMMAND;

        public static class INTENTS {
            public boolean GUILD_MEMBERS = true;
            public boolean GUILD_PRESENCES = true;
            public boolean GUILD_MESSAGES = true;
            public boolean GUILD_MESSAGE_REACTIONS = true;
            public boolean DIRECT_MESSAGES = true;
            public boolean EMOJI = false;
        }

        public static class CACHE {
            public boolean MEMBER_OVERRIDES = true;
            public boolean ONLINE_STATUS = true;
            public boolean EMOTE = false;
        }

        public static class COMMAND {
            @Comment("The prefix used for v2 commands (single character)")
            public String COMMAND_PREFIX = "/";
        }
    }

    public static class DATABASE {
            @Create
        public MYSQL MYSQL;
        @Create
        public SQLITE SQLITE;

        public static final class SQLITE {
            @Comment("Should SQLite be used?")
            public boolean USE = true;
            @Comment("The directory to store the database in")
            public String DIRECTORY = "database";
        }

        @Comment("TODO: MySQL support is not fully implemented. Request this to be finished if important")
        public static final class MYSQL {
            @Comment("Should MySQL be used?")
            public boolean USE = false;
            public String HOST = "localhost";
            public int PORT = 3306;
            public String USER = "root";
            public String PASSWORD = "password";
        }
    }

    @Comment({
            "Prevent users, nations, alliances from using the bot"
    })
    public static class MODERATION {
        /*
        DO NOT ADD HERE FOR PERSONAL REASONS. ONLY IF THEY ARE ABUSING THE BOT
        */
        public List<Integer> BANNED_NATIONS = List.of();
        public List<Long> BANNED_USERS = List.of();
        public List<Long> BANNED_GUILDS = new ArrayList<>();
        public List<Integer> BANNED_ALLIANCES = new ArrayList<>();
    }

    public static class WEB {
//        @Comment("The url/ip/hostname for the web interface")
//        public String REDIRECT = "https://locutus.link";
//        @Comment({"File location of the ssl certificate",
//                "- Trocutus expects a privkey.pem and a fullchain.pem in the directory",
//                "- You can get a free certificate from e.g. https://zerossl.com/ or https://letsencrypt.org/",
//                "- Set to empty string to not use an ssl certificate",
//        })
//        public String CERT_PATH = "C:/Certbot/live/locutus.link/";
//        @Comment({"The password or passphrase for the certificate",
//                "Leave blank if there is none"})
//        public String CERT_PASSWORD = "";
//        @Comment("Port used for HTTP. Set to 0 to disable")
//        public int PORT_HTTP = 80;
//        @Comment("Port used for secure HTTPS. Set to 0 to disable")
//        public int PORT_HTTPS = 443;
//        @Comment("If set to true, web content is not compressed/minified")
//        public boolean DEVELOPMENT = true;

        @Comment("The port google sheets uses to validate your credentials")
        public int GOOGLE_SHEET_VALIDATION_PORT = 8889;

        @Comment("The port google drive uses to validate your credentials")
        public int GOOGLE_DRIVE_VALIDATION_PORT = 8890;
    }

    private File defaultFile = new File("config" + File.separator + "config.yaml");

    public File getDefaultFile() {
        return defaultFile;
    }

    public void reload(File file) {
        this.defaultFile = file;
        load(file);
        save(file);
    }
}
