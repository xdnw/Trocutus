package link.locutus.core.command.binding;

import link.locutus.Trocutus;
import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.AllowDeleted;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.ParameterData;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.impl.discord.binding.PermissionBinding;
import link.locutus.command.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.command.perm.PermissionHandler;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.command.CM;
import link.locutus.core.command.CommandManager;
import link.locutus.core.command.DiscordCommands;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.KingdomAttribute;
import link.locutus.core.db.entities.kingdom.KingdomAttributeDouble;
import link.locutus.core.db.entities.kingdom.KingdomFilter;
import link.locutus.core.db.entities.kingdom.KingdomFilterString;
import link.locutus.core.db.entities.kingdom.KingdomList;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.core.db.entities.kingdom.KingdomOrAllianceOrGuild;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholder;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholders;
import link.locutus.core.db.entities.kingdom.SimpleKingdomList;
import link.locutus.core.db.entities.kingdom.SimpleKingdomPlaceholder;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildHandler;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.AutoNickOption;
import link.locutus.core.db.guild.entities.AutoRoleOption;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.EnemyAlertChannelMode;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.interview.IACheckup;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import link.locutus.util.TrounceUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TrouncedBindings extends BindingHelper {

    @Binding(examples = {"locutus"}, value = "A kingdom name or id")
    public static DBKingdom kingdom(String input) {
        DBKingdom result = DBKingdom.parse(input);
        if (result == null) {
            throw new IllegalArgumentException("Invalid kingdom: `" + input + "`");
        }
        return result;
    }

    @Binding(examples = {"paradise"}, value = "An alliance name or id")
    public static DBAlliance alliance(String input) {
        DBAlliance result = DBAlliance.parse(input);
        if (result == null) {
            throw new IllegalArgumentException("Invalid alliance: `" + input + "`");
        }
        return result;
    }

    @Binding(examples = {"standard"}, value = "An realm name or id")
    public static DBRealm realm(String input) {
        DBRealm result = DBRealm.parse(input);
        if (result == null) {
            throw new IllegalArgumentException("Invalid realm: `" + input + "`");
        }
        return result;
    }

    @Binding(value = "A comma separated list of kingdoms")
    public static Set<DBKingdom> kingdoms(@Me Guild guild, String input) {
        return DBKingdom.parseList(guild, input, false);
    }

    @Binding(value = "A comma separated list of alliances")
    public Set<DBAlliance> alliances(@Me Guild guild, String input) {
        return DBAlliance.parseList(guild, input);
    }

    @Binding(value = "Bot guild settings")
    public GuildSetting key(String input) {
        input = input.replaceAll("_", " ").toLowerCase();
        GuildSetting[] constants = GuildKey.values();
        for (GuildSetting constant : constants) {
            String name = constant.name().replaceAll("_", " ").toLowerCase();
            if (name.equals(input)) return constant;
        }
        List<String> options = Arrays.asList(constants).stream().map(GuildSetting::name).collect(Collectors.toList());
        throw new IllegalArgumentException("Invalid category: `" + input + "`. Options: " + StringMan.getString(options));
    }

    @Binding(value = "Mode for automatically giving discord roles")
    public AutoRoleOption roleOption(String input) {
        return emum(AutoRoleOption.class, input);
    }

    @Binding(value = "Mode for automatically giving discord nicknames")
    public AutoNickOption nickOption(String input) {
        return emum(AutoNickOption.class, input);
    }

    @Binding(value = "Hero class")
    public HeroType HeroType(String input) {
        return emum(HeroType.class, input);
    }

    @Binding(value = "Rank class")
    public Rank Rank(String input) {
        return emum(Rank.class, input);
    }

    @Binding(value = "Roles class")
    public Roles Roles(String input) {
        return emum(Roles.class, input);
    }


    @Binding(value = "Coalition class")
    public Coalition Coalition(String input) {
        return emum(Coalition.class, input);
    }

    @Binding(value = "TreatyType class")
    public TreatyType TreatyType(String input) {
        return emum(TreatyType.class, input);
    }

    @Me
    @Binding
    public Map<DBRealm, DBKingdom> me() {
        throw new UnsupportedOperationException("No registered kingdom found in command locals. Please use: " + CM.register.cmd.toSlashMention());
    }

    @Me
    @Binding
    public GuildDB meGuildDB() {
        throw new UnsupportedOperationException("No GuildDB binding provided in command locals");
    }

    @Binding(value = "A nation or alliance name, url or id. Prefix with `AA:` or `nation:` to avoid ambiguity if there exists both by the same name or id", examples = {"Borg", "https://politicsandwar.com/alliance/id=1234", "aa:1234"})
    public static KingdomOrAlliance nationOrAlliance(String input) {
        String lower = input.toLowerCase();
        if (lower.startsWith("aa:")) {
            return alliance(input.split(":", 2)[1]);
        }
        if (lower.contains("/alliance/")) {
            return alliance(input);
        }
        DBKingdom nation = DBKingdom.parse(input);
        if (nation == null) {
            return alliance(input);
        }
        return nation;
    }

    @Binding
    public KingdomPlaceholders placeholders() {
        return Trocutus.imp().getCommandManager().getKingdomPlaceholders();
    }

    @Binding(examples = "{nation}", value = "See: <https://github.com/xdnw/locutus/wiki/Kingdom-Filters>")
    public KingdomPlaceholder placeholder(ValueStore store, PermissionHandler permisser, String input) {
        CommandManager v2 = Trocutus.imp().getCommandManager();
        KingdomPlaceholders placeholders = v2.getKingdomPlaceholders();
        ParametricCallable ph = placeholders.get(input);
        ph.validatePermissions(store, permisser);
        Map.Entry<Type, Function<DBKingdom, Object>> entry = placeholders.getPlaceholderFunction(store, input);
        return new SimpleKingdomPlaceholder(ph.getPrimaryCommandId(), entry.getKey(), entry.getValue());
    }

    @Binding(examples = {"Borg", "alliance/id=7452", "647252780817448972"}, value = "A nation or alliance name, url or id, or a guild id")
    public static KingdomOrAllianceOrGuild nationOrAllianceOrGuild(String input) {
        try {
            return nationOrAlliance(input);
        } catch (IllegalArgumentException ignore) {
            if (input.startsWith("guild:")) {
                input = input.substring(6);
                if (!MathMan.isInteger(input)) {
                    for (GuildDB db : Trocutus.imp().getGuildDatabases().values()) {
                        if (db.getName().equalsIgnoreCase(input) || db.getGuild().getName().equalsIgnoreCase(input)) {
                            return db;
                        }
                    }
                }
            }
            if (MathMan.isInteger(input)) {
                long id = Long.parseLong(input);
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Trocutus.imp().getGuildDB(id);
                    if (db == null) {
                        throw new IllegalArgumentException("Not connected to guild: " + id);
                    }
                    return db;
                }
            }
            for (GuildDB value : Trocutus.imp().getGuildDatabases().values()) {
                if (value.getName().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            throw ignore;
        }
    }

    @Binding(value = "audit types")
    public IACheckup.AuditType auditType(String input) {
        return emum(IACheckup.AuditType.class, input);
    }

    @Binding(value = "MilitaryUnit types")
    public MilitaryUnit MilitaryUnit(String input) {
        return emum(MilitaryUnit.class, input);
    }

    @Binding(value = "AttackOrSpellType types")
    public AttackOrSpellType AttackOrSpellType(String input) {
        return emum(AttackOrSpellType.class, input);
    }

    @Binding(value = "A comma separated list of audit types")
    public Set<IACheckup.AuditType> auditTypes(String input) {
        return emumSet(IACheckup.AuditType.class, input);
    }

    @Binding(value = "A comma separated list of MilitaryUnit types")
    public Set<MilitaryUnit> MilitaryUnits(String input) {
        return emumSet(MilitaryUnit.class, input);
    }

    @Binding(value = "A comma separated list of AttackOrSpellType types")
    public Set<AttackOrSpellType> AttackOrSpellTypes(String input) {
        return emumSet(AttackOrSpellType.class, input);
    }

    @Binding(value = "A comma separated list of alliance metrics")
    public Set<AllianceMetric> metrics(String input) {
        Set<AllianceMetric> metrics = new HashSet<>();
        for (String type : input.split(",")) {
            AllianceMetric arg = StringMan.parseUpper(AllianceMetric.class, type);
            metrics.add(arg);
        }
        return metrics;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1", value = "A comma separated list of nations, alliances and filters")
    public KingdomList nationList(ParameterData data, @Default @Me Guild guild, String input) {
        return new SimpleKingdomList(kingdoms(guild, input)).setFilter(input);
    }

    @Binding(examples = "#position>1,#cities<=5", value = "A comma separated list of filters (can include nations and alliances)")
    public KingdomFilter nationFilter(@Default @Me Guild guild, String input) {
        kingdoms(guild, input + "||*");
        return new KingdomFilterString(input, guild);
    }

    @Binding(examples = "score,soldiers", value = "A comma separated list of numeric nation attributes")
    public Set<KingdomAttributeDouble> nationMetricDoubles(ValueStore store, String input) {
        Set<KingdomAttributeDouble> metrics = new LinkedHashSet<>();
        for (String arg : StringMan.split(input, ',')) {
            metrics.add(nationMetricDouble(store, arg));
        }
        return metrics;
    }

    @Binding(examples = "warpolicy,color", value = "A comma separated list of nation attributes")
    public Set<KingdomAttribute> nationMetrics(ValueStore store, String input) {
        Set<KingdomAttribute> metrics = new LinkedHashSet<>();
        for (String arg : StringMan.split(input, ',')) {
            metrics.add(nationMetric(store, arg));
        }
        return metrics;
    }

    @Binding(examples = "borg,AA:Cataclysm", value = "A comma separated list of nations and alliances")
    public Set<KingdomOrAlliance> nationOrAlliance(ParameterData data, @Default @Me Guild guild, String input) {
        Set<KingdomOrAlliance> result = new LinkedHashSet<>();

        for (String group : input.split("\\|+")) {
            List<String> remainder = new ArrayList<>();
            if (!group.contains("#")) {
                List<String> args = StringMan.split(group, ',');

                GuildDB db = Trocutus.imp().getGuildDB(guild);
                for (String arg : args) {
                    try {
                        DBAlliance aa = alliance(arg);
                        if ((data != null && data.getAnnotation(AllowDeleted.class) != null)) {
                            result.add(aa);
                            continue;
                        }
                    } catch (IllegalArgumentException ignore) {
                        ignore.printStackTrace();
                    }
                    if (db != null) {
                        if (arg.charAt(0) == '~') arg = arg.substring(1);
                        Set<Integer> coalitions = db.getCoalition(arg);
                        if (!coalitions.isEmpty()) {
                            for (int aaId : coalitions) {
                                DBAlliance aa = DBAlliance.get(aaId);
                                if (aa != null) {
                                    result.add(aa);
                                }
                            }
                            continue;
                        }
                    }
                    remainder.add(arg);
                }
            } else {
                remainder.add(group);
            }
            result.addAll(kingdoms(guild, StringMan.join(remainder, ",")));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972", value = "A comma separated list of nations, alliances and guild ids")
    public Set<KingdomOrAllianceOrGuild> nationOrAllianceOrGuild(ParameterData data, @Default @Me Guild guild, String input) {
        return (Set) nationOrAllianceOrGuild(guild, input);
    }

    public static Set<KingdomOrAllianceOrGuild> nationOrAllianceOrGuild(@Default @Me Guild guild, String input) {
        List<String> args = StringMan.split(input, ',');
        Set<KingdomOrAllianceOrGuild> result = new LinkedHashSet<>();
        List<String> remainder = new ArrayList<>();
        outer:
        for (String arg : args) {
            if (arg.startsWith("guild:")) {
                arg = arg.substring(6);
                if (!MathMan.isInteger(arg)) {
                    for (GuildDB db : Trocutus.imp().getGuildDatabases().values()) {
                        if (db.getName().equalsIgnoreCase(arg) || db.getGuild().getName().equalsIgnoreCase(arg)) {
                            result.add(db);
                            continue outer;
                        }
                    }
                    throw new IllegalArgumentException("Unknown guild: " + arg);
                }
            }
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Trocutus.imp().getGuildDB(id);
                    if (db == null) throw new IllegalArgumentException("Unknown guild: " + id);
                    result.add(db);
                    continue;
                }
            }

            try {
                DBAlliance aa = alliance(arg);
                if (aa != null) {
                    result.add(aa);
                    continue;
                }
            } catch (IllegalArgumentException ignore) {}
            GuildDB db = guild == null ? null : Trocutus.imp().getGuildDB(guild);
            if (db != null) {
                if (arg.charAt(0) == '~') arg = arg.substring(1);
                Set<Integer> coalitions = db.getCoalition(arg);
                if (!coalitions.isEmpty()) {
                    for (int aaId : coalitions) {
                        DBAlliance aa = DBAlliance.get(aaId);
                        if (aa != null) result.add(aa);
                    }
                    continue;
                }
            }
            remainder.add(arg);
        }
        result.addAll(kingdoms(guild, StringMan.join(remainder, ",")));
        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    @Binding(examples = {"{soldiers=12,tanks=56}"}, value = "A comma separated list of units and their amounts")
    public Map<MilitaryUnit, Long> units(String input) {
        Map<MilitaryUnit, Long> map = TrounceUtil.parseUnits(input);
        if (map == null) throw new IllegalArgumentException("Invalid units: " + input + ". Valid types: " + StringMan.getString(MilitaryUnit.values()) + ". In the form: `{SOLDIERS=1234,TANKS=5678}`");
        return map;
    }

    @Binding
    @Me
    public GuildHandler handler(@Me GuildDB db) {
        return db.getHandler();
    }

    @Binding
    public ExecutorService executor() {
        return Trocutus.imp().getExecutor();
    }

    @Binding(value = "Types of users to clear roles of")
    public DiscordCommands.ClearRolesEnum clearRolesEnum(String input) {
        return emum(DiscordCommands.ClearRolesEnum.class, input);
    }

    @Binding(examples = {"@role", "672238503119028224", "roleName"}, value = "A discord role name, mention or id")
    public Roles role(String role) {
        return emum(Roles.class, role);
    }

    @Binding(value = "One of the default Locutus coalition names")
    public Coalition coalition(String input) {
        return emum(Coalition.class, input);
    }

    @Binding(value = "A name for a default or custom Locutus coalition")
    @GuildCoalition
    public String guildCoalition(@Me GuildDB db, String input) {
        input = input.toLowerCase();
        Set<String> coalitions = new HashSet<>(db.getCoalitions().keySet());
        for (Coalition value : Coalition.values()) coalitions.add(value.name().toLowerCase());
        if (!coalitions.contains(input)) throw new IllegalArgumentException(
                "No coalition found matching: `" + input +
                        "`. Options: " + StringMan.getString(coalitions) + "\n" +
                        "Create it via " + CM.coalition.create.cmd.toSlashMention()
        );
        return input;
    }

    @Binding
    public AllianceList allianceList(ParameterData param, @Me GuildDB db, @Default @Me User user) {
        AllianceList list = db.getAllianceList();
        if (list == null) {
            throw new IllegalArgumentException("This guild has no registered alliance. See " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.ALLIANCE_ID.name());
        }
        RolePermission perms = param.getAnnotation(RolePermission.class);
        if (perms != null) {
            if (user != null) {
                Set<Integer> allowedIds = new HashSet<>();
                for (int aaId : list.getIds()) {
                    try {
                        PermissionBinding.checkRole(db.getGuild(), perms, user, aaId);
                        allowedIds.add(aaId);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
                if (allowedIds.isEmpty()) {
                    throw new IllegalArgumentException("You are lacking role permissions for the alliance ids: " + StringMan.getString(list.getIds()));
                }
                return list.subList(allowedIds);
            }
            throw new IllegalArgumentException("Not registered");
        } else {
            throw new IllegalArgumentException("TODO: disable this error once i verify it works (see console for debug info)");
        }
    }

    @Binding(value = "A locutus metric for alliances")
    public AllianceMetric AllianceMetric(String input) {
        return StringMan.parseUpper(AllianceMetric.class, input);
    }

    @Binding(value = "A numeric nation attribute. See: <https://github.com/xdnw/locutus/wiki/Kingdom-Filters>", examples = {"score", "ships", "land"})
    public KingdomAttributeDouble nationMetricDouble(ValueStore store, String input) {
        KingdomPlaceholders placeholders = Trocutus.imp().getCommandManager().getKingdomPlaceholders();
        KingdomAttributeDouble metric = placeholders.getMetricDouble(store, input);
        if (metric == null) {
            String optionsStr = StringMan.getString(placeholders.getMetricsDouble(store).stream().map(KingdomAttribute::getName).collect(Collectors.toList()));
            throw new IllegalArgumentException("Invalid metric: `" + input + "`. Options: " + optionsStr);
        }
        return metric;
    }

    @Binding(value = "A nation attribute. See: <https://github.com/xdnw/locutus/wiki/Kingdom-Filters>", examples = {"color", "war_policy", "continent"})
    public KingdomAttribute nationMetric(ValueStore store, String input) {
        KingdomPlaceholders placeholders = Trocutus.imp().getCommandManager().getKingdomPlaceholders();
        KingdomAttribute metric = placeholders.getMetric(store, input, false);
        if (metric == null) {
            String optionsStr = StringMan.getString(placeholders.getMetrics(store).stream().map(KingdomAttribute::getName).collect(Collectors.toList()));
            throw new IllegalArgumentException("Invalid metric: `" + input + "`. Options: " + optionsStr);
        }
        return metric;
    }

    @Binding(value = "An alert mode for the ENEMY_ALERT_CHANNEL when enemies leave alert levels")
    public EnemyAlertChannelMode EnemyAlertChannelMode(String input) {
        return emum(EnemyAlertChannelMode.class, input);
    }
}
