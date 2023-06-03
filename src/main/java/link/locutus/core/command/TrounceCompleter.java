package link.locutus.core.command;

import com.google.gson.reflect.TypeToken;
import link.locutus.Trocutus;
import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.FunctionConsumerParser;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.AllianceDepositLimit;
import link.locutus.command.binding.annotation.Autocomplete;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.impl.discord.binding.DiscordBindings;
import link.locutus.command.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomAttribute;
import link.locutus.core.db.entities.kingdom.KingdomAttributeDouble;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.core.db.entities.kingdom.KingdomOrAllianceOrGuild;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholder;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholders;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.interview.IACheckup;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TrounceCompleter extends BindingHelper {
    {
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Member.class).getType(), Autocomplete.class);
            addBinding(store -> store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));

                List<Member> options = guild.getMembers();
                return StringMan.autocompleteComma(input.toString(), options, s -> DiscordBindings.member(guild, null, s), Member::getEffectiveName, IMentionable::getAsMention, OptionData.MAX_CHOICES);
            })));
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Role.class).getType(), Autocomplete.class);
            addBinding(store -> store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));

                List<Role> options = guild.getRoles();
                return StringMan.autocompleteComma(input.toString(), options, s -> DiscordBindings.role(guild, s), Role::getName, IMentionable::getAsMention, OptionData.MAX_CHOICES);
            })));
        }
    }

    @Autocomplete
    @Binding(types = {Category.class})
    public List<String> category(@Me Member member, @Me Guild guild, String input) {
        List<Category> categories = new ArrayList<>(guild.getCategories());
        categories.removeIf(f -> !f.getMembers().contains(member));
        if (!input.isEmpty()) {
            categories = StringMan.getClosest(input, categories, Category::getName, OptionData.MAX_CHOICES, true);
        }

        return categories.stream().map(Category::getAsMention).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types = {Guild.class})
    public List<Map.Entry<String, String>> Guild(@Me User user, String input) {
        List<Guild> options = user.getMutualGuilds();
        options = StringMan.getClosest(input, options, Guild::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> new AbstractMap.SimpleEntry<>(f.getName(), f.getIdLong() + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types = {Roles.class})
    public List<String> Roles(String input) {
        return StringMan.completeEnum(input, Roles.class);
    }

    @Autocomplete
    @Binding(types = {Permission.class})
    public List<String> Permission(String input) {
        return StringMan.completeEnum(input, Permission.class);
    }

    @Autocomplete
    @Binding(types = {OnlineStatus.class})
    public List<String> onlineStatus(String input) {
        return StringMan.completeEnum(input, OnlineStatus.class);
    }

    @Autocomplete
    @Binding(types = {Role.class})
    public List<Map.Entry<String, String>> role(@Me Guild guild, String input) {
        List<Role> options = guild.getRoles();
        List<Role> closest = StringMan.getClosest(input, options, true);
        return StringMan.autocompletePairs(closest, Role::getName, IMentionable::getAsMention);
    }


    @Autocomplete
    @Binding(types={DBKingdom.class})
    public List<String> DBKingdom(String input) {
        if (input.isEmpty()) return null;

        List<DBKingdom> options = new ArrayList<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true));
        options = StringMan.getClosest(input, options, DBKingdom::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(DBKingdom::getName).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBRealm.class})
    public List<String> DBRealm(String input) {
        if (input.isEmpty()) return null;

        List<DBRealm> options = new ArrayList<>(Trocutus.imp().getDB().getRealms().values());
        options = StringMan.getClosest(input, options, DBRealm::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(DBRealm::getName).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBAlliance.class})
    public List<Map.Entry<String, String>> DBAlliance(String input) {
        if (input.isEmpty()) return null;

        List<DBAlliance> options = new ArrayList<>(Trocutus.imp().getDB().getAlliances());
        options = StringMan.getClosest(input, options, DBAlliance::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(f -> Map.entry(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={KingdomOrAlliance.class})
    public List<Map.Entry<String, String>> KingdomOrAlliance(String input) {
        if (input.isEmpty()) return null;

        List<KingdomOrAlliance> options = new ArrayList<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true));
        options.addAll(Trocutus.imp().getDB().getAlliances());

        options = StringMan.getClosest(input, options, KingdomOrAlliance::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(f -> Map.entry(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={KingdomOrAllianceOrGuild.class})
    public List<Map.Entry<String, String>> KingdomOrAllianceOrGuild(String input, @Me User user) {
        if (input.isEmpty()) return null;

        List<KingdomOrAllianceOrGuild> options = new ArrayList<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true));
        options.addAll(Trocutus.imp().getDB().getAlliances());
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Trocutus.imp().getGuildDB(guild);
                if (db != null) {
                    options.add(db);
                }
            }
        }
        options = StringMan.getClosest(input, options, KingdomOrAllianceOrGuild::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> Map.entry((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={AllianceMetric.class})
    public List<String> AllianceMetric(String input) {
        return StringMan.completeEnum(input, AllianceMetric.class);
    }

    @Autocomplete
    @Binding(types={KingdomPlaceholder.class})
    public List<String> KingdomPlaceholder(String input) {
        KingdomPlaceholders placeholders = Trocutus.imp().getCommandManager().getKingdomPlaceholders();
        List<String> options = new ArrayList<>(placeholders.getKeys());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={GuildSetting.class})
    public List<String> setting(String input) {
        List<String> options = Arrays.asList(GuildKey.values()).stream().map(f -> f.name()).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={KingdomAttributeDouble.class})
    public List<String> KingdomPlaceholder(ArgumentStack stack, String input) {
        KingdomPlaceholders placeholders = Trocutus.imp().getCommandManager().getKingdomPlaceholders();
        List<String> options = placeholders.getMetricsDouble(stack.getStore())
                .stream().map(KingdomAttribute::getName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={DiscordCommands.ClearRolesEnum.class})
    public List<String> ClearRolesEnum(String input) {
        return StringMan.completeEnum(input, DiscordCommands.ClearRolesEnum.class);
    }

    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, DBAlliance.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                List<DBAlliance> options = new ArrayList<>(Trocutus.imp().getDB().getAlliances());
                String inputStr = input.toString();
                return StringMan.autocompleteComma(inputStr, options, f -> DBAlliance.parse(f), DBAlliance::getName, f -> f.getId() + "", OptionData.MAX_CHOICES);
            }));
        });
    }

    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, Roles.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                return StringMan.autocompleteCommaEnum(Roles.class, input.toString(), OptionData.MAX_CHOICES);
            }));
        });
    }
    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, IACheckup.AuditType.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                return StringMan.autocompleteCommaEnum(IACheckup.AuditType.class, input.toString(), OptionData.MAX_CHOICES);
            }));
        });
    }
    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, AttackOrSpellType.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                return StringMan.autocompleteCommaEnum(AttackOrSpellType.class, input.toString(), OptionData.MAX_CHOICES);
            }));
        });
    }
    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, HeroType.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                return StringMan.autocompleteCommaEnum(HeroType.class, input.toString(), OptionData.MAX_CHOICES);
            }));
        });
    }
    {
        Key key = Key.of(TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                List<String> options = Arrays.asList(MilitaryUnit.values).stream().map(Enum::name).collect(Collectors.toList());
                return StringMan.completeMap(options, null, input.toString());
            }));
        });
    }
    {
        Type type = TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType();
        Consumer<ValueStore<?>> binding = store -> {
            Key key = Key.of(type, Autocomplete.class);
            FunctionConsumerParser parser = new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                List<String> options = Arrays.asList(MilitaryUnit.values()).stream().map(Enum::name).collect(Collectors.toList());
                return StringMan.completeMap(options, null, input.toString());
            });
            store.addParser(key, parser);
        };
        addBinding(binding);
    }

    @Autocomplete
    @Binding(types={CommandCallable.class})
    public List<String> command(String input) {
        List<ParametricCallable> options = new ArrayList<>(Trocutus.imp().getCommandManager().getCommands().getParametricCallables(f -> true));
        List<String> optionsStr = options.stream().map(f -> f.getFullPath()).toList();
        return StringMan.getClosest(input, optionsStr, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={Coalition.class})
    public List<String> Coalition(String input) {
        return StringMan.completeEnum(input, Coalition.class);
    }

    @Autocomplete
    @GuildCoalition
    @Binding(types={String.class})
    public List<String> GuildCoalition(@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>();
        for (Coalition coalition : Coalition.values()) {
            options.add(coalition.name());
        }
        for (String coalition : db.getCoalitions().keySet()) {
            if (Coalition.getOrNull(coalition) != null) continue;
            options.add(coalition);
        }
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }
}
