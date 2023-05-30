package link.locutus.core.db.guild.key;

import com.google.gson.reflect.TypeToken;
import link.locutus.Trocutus;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.command.CM;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.LocalValueStore;
import link.locutus.command.binding.Parser;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.ParameterData;
import link.locutus.command.command.ParametricCallable;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public abstract class GuildSetting<T> {
    private final Set<GuildSetting> requires = new LinkedHashSet<>();
    private final Set<Coalition> requiresCoalition = new LinkedHashSet<>();

    private final Set<BiPredicate<GuildDB, Boolean>> requiresFunction = new LinkedHashSet<>();
    private final Map<Roles, Boolean> requiresRole = new LinkedHashMap<>();

    private final Key type;
    private final GuildSettingCategory category;
    private String name;

    private Queue<Consumer<GuildSetting<T>>> setupRequirements = new ConcurrentLinkedQueue<>();

    public GuildSetting(GuildSettingCategory category, Type a, Type... subArgs) {
        this(category, TypeToken.getParameterized(a, subArgs).getType());
    }

    public GuildSetting(GuildSettingCategory category, Type t) {
        this(category, Key.of(t));
    }
    public GuildSetting(GuildSettingCategory category, Class t) {
        this(category, Key.of(t));
    }
    public GuildSetting(GuildSettingCategory category, Key type) {
        this.category = category;
        this.type = type;
        if (type == null) {
            try {
                if (getClass().getMethod("myMethod", GuildDB.class, String.class).getDeclaringClass() != getClass()) {
                    throw new IllegalStateException("Type is null for " + getClass().getSimpleName() + " and no parse method found");
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Key getType() {
        return type;
    }

    public ValueStore getStore() {
        return Trocutus.imp().getCommandManager().getStore();
    }


    public abstract String help();

    public abstract String toString(T value);

    public GuildSetting<T> setName(String name) {
        this.name = name;
        return this;
    }

    public GuildSetting<T> requires(GuildSetting other) {
        requires.add(other);
        return this;
    }

    public GuildSetting<T> requiresCoalition(Coalition coalition) {
        requiresCoalition.add(coalition);
        return this;
    }

    public T validate(GuildDB db, T value) {
        return value;
    }

    public String toReadableString(T value) {
        return toString(value);
    }

    public String getCommandFromArg(Object value) {
        return getCommandObjRaw(value);
    }

    private Set<ParametricCallable> getCallables() {
        Set<ParametricCallable> callables = Trocutus.imp().getCommandManager().getCommands().getParametricCallables(f -> f.getObject() == this);
        callables.removeIf(f -> {
            String id = f.getPrimaryCommandId().toLowerCase();
            return id.startsWith("remove") || id.startsWith("delete") || id.startsWith("unregister");
        });
        if (callables.isEmpty()) {
            new RuntimeException("No configuration command found" + name()).printStackTrace();
        }
        return callables;
    }

    public String getCommandObj(T value) {
        return getCommandObjRaw(value);
    }

    private String getCommandObjRaw(Object value) {
        Set<ParametricCallable> callables = getCallables();
        if (value != null) {
            for (ParametricCallable callable : callables) {
                for (ParameterData parameter : callable.getUserParameters()) {
                    Type type = parameter.getBinding().getKey().getType();
                    if ((type instanceof Class clazz && clazz.isAssignableFrom(value.getClass())) || callables.size() == 1) {
                        String valueStr = null;
                        if (value instanceof String) {
                            valueStr = (String) value;
                        } else {
                            try {
                                valueStr = toReadableString((T) value);
                            } catch (Throwable e) {
                                valueStr = value + "";
                            }
                        }
                        Map<String, String> args = Collections.singletonMap(parameter.getName(), valueStr);
                        return callable.getSlashCommand(args);
                    }
                }
            }
        }
        return getCommandMention();
    }

    public String getCommandMention() {
        return getCommandMention(getCallables());
    }

    private String getCommandMention(Set<ParametricCallable> callables) {
        List<String> result = new ArrayList<>();
        for (ParametricCallable callable : callables) {
            result.add(callable.getSlashMention());
        }
        return StringMan.join(result, ", ");
    }

    public String set(GuildDB db, T value) {
        String readableStr = toReadableString(value);
        db.setInfo(this, value);
        return "Set `" + name() + "` to `" + readableStr + "`\n" + "" +
                "Delete with " + CM.settings.delete.cmd.create(name);
    }

    public T parse(GuildDB db, String input) {
        if (type == null) throw new IllegalStateException("Type is null for " + getClass().getSimpleName());
        LocalValueStore locals = new LocalValueStore<>(getStore());
        locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        locals.addProvider(Key.of(Guild.class, Me.class), db.getGuild());
        Parser parser = locals.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("No parser found for " + type);
        }
        return (T) parser.apply(locals, input);
    }

    public String toString() {
        return name() + "\n> " + help() + "\n";
    }

    public String name() {
        if (name == null) {
            throw new IllegalStateException("Name is null for " + getClass().getSimpleName());
        }
        return name;
    }

    public boolean hasPermission(GuildDB db, User author, T value) {
        return Roles.ADMIN.has(author, db.getGuild());
    }


    public T getOrNull(GuildDB db) {
        return getOrNull(db, true);
    }

    public T getOrNull(GuildDB db, boolean allowDelegate) {
        return db.getOrNull(this, allowDelegate);
    }

    public T get(GuildDB db) {
        return get(db, true);
    }

    public T get(GuildDB db, boolean allowDelegate) {
        return db.getOrThrow(this, allowDelegate);
    }

    public boolean allowed(GuildDB db) {
        return allowed(db, false);
    }

    public boolean allowed(GuildDB db, boolean throwException) {
        while (!setupRequirements.isEmpty()) {
            Consumer<GuildSetting<T>> poll = setupRequirements.poll();
            if (poll != null) poll.accept(this);
        }
        List<String> errors = new ArrayList<>();
        for (GuildSetting require : requires) {
            if (require.getOrNull(db, false) == null) {
                if (throwException) {
                    errors.add("Missing required setting `" + require.name() + "` (see: " + require.getCommandObj((String) null) + ")");
                } else {
                    return false;
                }
            }
        }
        for (Coalition coalition : requiresCoalition) {
            if (db.getCoalition(coalition).isEmpty()) {
                if (throwException) {
                    errors.add("Missing required coalition " + coalition.name() + " (see: " + CM.coalition.create.cmd.create(null, coalition.name()).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }

        for (Map.Entry<Roles, Boolean> entry : requiresRole.entrySet()) {
            Roles role = entry.getKey();
            boolean allowAARole = entry.getValue();
            Map<Long, Role> roleMap = db.getRoleMap(role);
            if (roleMap.isEmpty() || (!allowAARole && !roleMap.containsKey(0L))) {
                if (throwException) {
                    errors.add("Missing required role " + role.name() + " (see: " + CM.role.alias.set.cmd.create(role.name(), null, null, null).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }

        for (BiPredicate<GuildDB, Boolean> predicate : requiresFunction) {
            try {
                if (!predicate.test(db, throwException)) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                if (throwException) {
                    errors.add(e.getMessage());
                } else {
                    return false;
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }

        return true;
    }

    public GuildSetting<T> setupRequirements(Consumer<GuildSetting<T>> consumer) {
        setupRequirements.add(consumer);
        return this;
    }

    public GuildSetting<T> requiresRole(Roles role, boolean allowAllianceRole) {
        checkNotNull(role);
        this.requiresRole.put(role, allowAllianceRole);
        return this;
    }

    public GuildSetting<T> requiresWhitelisted() {
        this.requiresFunction.add(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                if (!db.isWhitelisted()) {
                    throw new IllegalArgumentException("This guild is not whitelisted by the bot developer (this feature may not be ready for public use yet)");
                }
                return true;
            }
        });
        return this;
    }

    public GuildSetting<T> nonPublic() {
        this.requiresFunction.add(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                throw new IllegalArgumentException("Please use the public channels for this (this is to reduce unnecessary discord calls)");
            }
        });
        return this;
    }

    public static MessageChannel validateChannel(GuildDB db, MessageChannel channel) {
        MessageChannel original = channel;
        channel = DiscordUtil.getChannel(db.getGuild(), channel.getId());
        if (channel == null) {
            throw new IllegalArgumentException("Channel " + original + " not found (are you sure it is in this server?)");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is not a text channel");
        }
        if (!channel.canTalk()) {
            throw new IllegalArgumentException("Bot does not have permission to talk in " + channel.getAsMention());
        }
        return channel;
    }

    public static Category validateCategory(GuildDB db, Category category) {
        Category original = category;
        category = DiscordUtil.getCategory(db.getGuild(), category.getId());
        if (category == null) {
            throw new IllegalArgumentException("Category " + original + " not found (are you sure it is in this server?)");
        }
        if (category.getType() != ChannelType.CATEGORY) {
            throw new IllegalArgumentException("Channel " + category.getAsMention() + " is not a category");
        }
        return category;
    }

    public GuildSetting<T> requireValidAlliance() {
        requiresFunction.add((db, throwError) -> {
            if (!db.isValidAlliance()) {
                throw new IllegalArgumentException("No valid alliance is setup (see: " + GuildKey.ALLIANCE_ID.getCommandMention() + ")");
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requiresNot(GuildSetting setting) {
        requiresFunction.add((db, throwError) -> {
            if (setting.getOrNull(db) != null) {
                throw new IllegalArgumentException("Cannot be used with " + setting.name() + " set. Unset via " + CM.settings.info.cmd.toSlashMention());
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requireFunction(Consumer<GuildDB> predicate) {
        this.requiresFunction.add((guildDB, throwError) -> {
            try {
                predicate.accept(guildDB);
            } catch (IllegalArgumentException e) {
                if (throwError) throw e;
                return false;
            }
            return true;
        });
        return this;
    }

    private void checkRegisteredOwnerOrActiveAlliance(GuildDB db) {
        if (db.isValidAlliance()) {
            if (!db.getAllianceList().getKingdoms(f -> f.getPosition().ordinal() >= Rank.ADMIN.ordinal() && f.getActive_m() < 43200).isEmpty()) {
                return;
            }
        }
        GuildDB delegate = db.getDelegateServer();
        if (delegate != null) {
            if (delegate.isValidAlliance()) {
                if (!delegate.getAllianceList().getKingdoms(f -> f.getPosition().ordinal() >= Rank.ADMIN.ordinal() && f.getActive_m() < 43200).isEmpty()) {
                    return;
                }
            }
        }
        GuildDB faServer = GuildKey.FA_SERVER.getOrNull(db, false);
        if (faServer != null) {
            if (faServer.isValidAlliance()) {
                if (!faServer.getAllianceList().getKingdoms(f -> f.getPosition().ordinal() >= Rank.ADMIN.ordinal() && f.getActive_m() < 43200).isEmpty()) {
                    return;
                }
            }
        }

        Member owner = db.getGuild().getOwner();
        if (owner != null && owner.getIdLong() != Settings.INSTANCE.ADMIN_USER_ID) {
            Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(owner.getUser());
            if (kingdoms == null || kingdoms.isEmpty()) {
                throw new IllegalArgumentException("The owner of this server (" + owner.getEffectiveName() + ") is not registered with the bot (see: " + CM.register.cmd.toSlashMention() + ")");
            }
            long mostRecent = kingdoms.values().stream().map(DBKingdom::getLast_active).max(Long::compareTo).orElse(0L);
            // get max

            if (mostRecent < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)) {
                List<String> kingdomNames = kingdoms.values().stream().map(DBKingdom::getQualifiedName).collect(Collectors.toList());
                throw new IllegalArgumentException("The owner of this server (user: " + owner.getEffectiveName() + " | nation: " + StringMan.getString(kingdomNames) + ") has not been active in the last 30 days");
            }
        }
    }


    public GuildSetting<T> requireActiveGuild() {
        requireFunction(db -> {
            checkRegisteredOwnerOrActiveAlliance(db);
        });
        return this;
    }

    public boolean has(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate) != null;
    }

    public String getRaw(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate);
    }

    public GuildSettingCategory getCategory() {
        return category;
    }

    public String delete(GuildDB db, User user) {
        db.deleteInfo(this);
        return "Deleted `" + name() + "`";
    }

    public T allowedAndValidate(GuildDB db, User user, T value) {
        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(user);
        if ((kingdoms == null || kingdoms.isEmpty()) && user.getIdLong() != Settings.INSTANCE.ADMIN_USER_ID) {
            throw new IllegalArgumentException("You are not registered with the bot (see: " + CM.register.cmd.toSlashMention() + ")");
        }
        if (!allowed(db, true)) {
            throw new IllegalArgumentException("This setting is not allowed in this server (you may be missing some prerequisite settings)");
        }
        if (!hasPermission(db, user, value)) {
            throw new IllegalArgumentException("You do not have permission to set " + name() + " to `" + toReadableString(value) + "`");
        }
        return validate(db, value);
    }

    public String setAndValidate(GuildDB db, User user, T value) {
        value = allowedAndValidate(db, user, value);
        return set(db, value);
    }

    public GuildSetting<T> requiresAllies() {
        this.requireFunction(db -> {
            if (!db.isValidAlliance()) {
                boolean hasValidAllies = false;
                for (Integer aaId : db.getCoalition(Coalition.ALLIES)) {
                    if (DBAlliance.get(aaId) != null) {
                        hasValidAllies = true;
                        break;
                    }
                }
                if (!hasValidAllies) {
                    if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
                        throw new IllegalArgumentException("No valid alliance or `" + Coalition.ALLIES + "` coalition exists. See: " + GuildKey.ALLIANCE_ID.getCommandMention() + " or " + CM.coalition.create.cmd.toSlashMention());
                    }
                }
            }
        });
        return this;
    }
}
