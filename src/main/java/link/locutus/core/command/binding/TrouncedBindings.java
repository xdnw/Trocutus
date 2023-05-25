package link.locutus.core.command.binding;

import link.locutus.Trocutus;
import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Me;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.db.TrouncedDB;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.entities.DBRealm;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.AutoNickOption;
import link.locutus.core.db.guild.entities.AutoRoleOption;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public Set<DBKingdom> kingdoms(@Me Guild guild, String input) {
        return DBKingdom.parseList(guild, input);
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
        throw new UnsupportedOperationException("No Map<DBRealm, DBKingdom> binding provided in command locals");
    }

    @Me
    @Binding
    public GuildDB meGuildDB() {
        throw new UnsupportedOperationException("No GuildDB binding provided in command locals");
    }
}
