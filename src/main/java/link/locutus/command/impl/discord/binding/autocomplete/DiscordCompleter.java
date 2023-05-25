package link.locutus.command.impl.discord.binding.autocomplete;

import com.google.gson.reflect.TypeToken;
import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.FunctionConsumerParser;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Autocomplete;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.impl.discord.binding.DiscordBindings;
import link.locutus.core.db.guild.entities.Roles;
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
import java.util.stream.Collectors;

public class DiscordCompleter extends BindingHelper {
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
}
