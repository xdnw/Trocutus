package link.locutus.command.impl.discord.binding;

import link.locutus.Trocutus;
import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.impl.discord.permission.CoalitionPermission;
import link.locutus.command.impl.discord.permission.HasKey;
import link.locutus.command.impl.discord.permission.IsAlliance;
import link.locutus.command.impl.discord.permission.IsGuild;
import link.locutus.command.impl.discord.permission.NotGuild;
import link.locutus.command.impl.discord.permission.RankPermission;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.command.impl.discord.permission.WhitelistPermission;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.settings.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class PermissionBinding extends BindingHelper {

    @Binding(value = "Must be used in a guild registered to a valid in-game alliance")
    @IsAlliance
    public boolean checkAlliance(@Me GuildDB db, IsAlliance perm) {
        if (db.getAlliances().isEmpty()) throw new IllegalArgumentException(db.getGuild() + " is not a valid alliance. See: " + GuildKey.ALLIANCE_ID.getCommandMention() + "");
        return true;
    }
    @Binding(value = "Must be run in a guild matching the provided ids")
    @IsGuild
    public boolean checkGuild(@Me Guild guild, IsGuild perm) {
        if (Arrays.stream(perm.value()).noneMatch(f -> f == guild.getIdLong())) {
            throw new IllegalCallerException("Guild does not have permission");
        }
        return true;
    }

    @Binding(value = "Cannot be run in guilds matching the provided ids")
    @NotGuild
    public boolean checkNotGuild(@Me Guild guild, NotGuild perm) {
        if (Arrays.stream(perm.value()).noneMatch(f -> f == guild.getIdLong())) {
            throw new IllegalCallerException("Guild has permission denied");
        }
        return true;
    }

    @Binding(value = "Must be run in a guild that has configured the provided settings")
    @HasKey
    public boolean checkKey(@Me GuildDB db, @Me User author, HasKey perm) {
        if (perm.value() == null || perm.value().length == 0) {
            throw new IllegalArgumentException("No key provided");
        }
        for (String keyName : perm.value()) {
            GuildSetting key = GuildKey.valueOf(keyName.toUpperCase());
            Object value = key.getOrNull(db);
            if (value == null) {
                throw new IllegalArgumentException("Key " + key.name() + " is not set in " + db.getGuild());
            }
            if (perm.checkPermission() && !key.hasPermission(db, author, value)) {
                throw new IllegalCallerException("Key " + key.name() + " does not have permission in " + db.getGuild());
            }
        }
        return true;
    }

    @Binding("Must be run in a guild whitelisted by the bot developer")
    @WhitelistPermission
    public boolean checkWhitelistPermission(@Me GuildDB db, @Me User user, WhitelistPermission perm) {
        if (!db.isWhitelisted()) {
            throw new IllegalCallerException("Guild is not whitelisted");
        }
        if (!Roles.MEMBER.has(user, db.getGuild())) {
            throw new IllegalCallerException("You do not have " + Roles.MEMBER + " " + user.getAsMention() + " see: " + "CM.role.setAlias.cmd.toSlashMention()");
        }
        return true;
    }

    @Binding("Must be run in a guild added to a coalition by the bot developer")
    @CoalitionPermission(Coalition.ALLIES)
    public boolean checkWhitelistPermission(@Me GuildDB db, CoalitionPermission perm) {
        if (db.getIdLong() == Settings.INSTANCE.ROOT_SERVER) return true;
        Coalition requiredCoalition = perm.value();
        Guild root = Trocutus.imp().getServer();
        GuildDB rootDb = Trocutus.imp().getGuildDB(root);
        Set<Long> coalitionMembers = rootDb.getCoalitionRaw(requiredCoalition);
        if (coalitionMembers.contains(db.getIdLong())) return true;
        Set<Integer> aaIds = db.getAllianceIds();
        for (int aaId : aaIds) {
            if (coalitionMembers.contains((long) aaId)) return true;
        }
        return false;
    }

    @Binding("Must be registered to a nation with an in-game rank equal to or above the provided rank\n" +
            "Default: MEMBER")
    @RankPermission
    public boolean checkRank(@Me Guild guild, RankPermission perm, @Me Map<Integer, DBKingdom> me) {
        for (Map.Entry<Integer, DBKingdom> entry : me.entrySet()) {
            if (entry.getValue().getPosition().ordinal() >= perm.value().ordinal()) {
                return true;
            }
        }
        throw new IllegalCallerException("Your ingame alliance positions is below " + perm.value());
    }

    @Binding("Must have the provided Locutus roles on discord\n" +
            "If `any` is set then you only need one of the roles instead of all\n" +
            "If `root` is set you need the role on the Locutus server\n" +
            "If `guild` is set you need the role in the guild matching that id\n" +
            "If `alliance` is set you need the role in the guild for that alliance")
    @RolePermission
    public static boolean checkRole(@Me Guild guild, RolePermission perm, @Me User user) {
        return checkRole(guild, perm, user, null);
    }

    public static boolean checkRole(@Me Guild guild, RolePermission perm, @Me User user, Integer allianceId) {
        if (perm.root()) {
            guild = Trocutus.imp().getServer();
        } else if (perm.guild() > 0) {
            guild = Trocutus.imp().getDiscordApi().getGuildById(perm.guild());
            if (guild == null) throw new IllegalCallerException("Guild " + perm.guild() + " does not exist" + " " + user.getAsMention() + " (are you sure Locutus is invited?)");
        }
        boolean hasAny = false;
        for (Roles requiredRole : perm.value()) {
            if (allianceId != null && !requiredRole.has(user, guild, allianceId) ||
                    (!requiredRole.has(user, guild) && (!perm.alliance() || requiredRole.getAllowedAccounts(user, guild).isEmpty()))) {
                if (perm.any()) continue;
                throw new IllegalCallerException("You do not have " + requiredRole.name() + " on " + guild + " " + user.getAsMention() + " see: " + "CM.role.setAlias.cmd.toSlashMention()");
            } else {
                hasAny = true;
            }
        }
        return hasAny;
    }
}