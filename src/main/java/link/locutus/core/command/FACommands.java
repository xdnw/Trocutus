package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomOrAllianceOrGuild;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.AutoRoleOption;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FACommands {
    @Command(desc = "List the bot coalitions")
    @RolePermission(Roles.MEMBER)
    public String listCoalition(@Me User user, @Me GuildDB db, @Arg("Only list alliances or guilds containing this filter") @Default String filter, @Arg("List the alliance and guild ids instead of names") @Switch("i") boolean listIds, @Arg("Ignore deleted alliances") @Switch("d") boolean ignoreDeleted) {
        Map<String, Set<Long>> coalitions = db.getCoalitionsRaw();
        List<String> coalitionNames = new ArrayList<>(coalitions.keySet());
        Collections.sort(coalitionNames);
        if (filter != null) filter = filter.toLowerCase(Locale.ROOT);

        StringBuilder response = new StringBuilder();
        for (String coalition : coalitionNames) {
            if (coalition.equalsIgnoreCase("offshore") && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild())) {
                continue;
            }
            Set<Long> alliances = coalitions.get(coalition);
            List<String> names = new ArrayList<>();
            for (long allianceOrGuildId : alliances) {
                String name;
                if (allianceOrGuildId > Integer.MAX_VALUE) {
                    GuildDB guildDb = Trocutus.imp().getGuildDB(allianceOrGuildId);
                    if (guildDb == null) {
                        if (ignoreDeleted) continue;
                        name = "guild:" + allianceOrGuildId;
                    } else {
                        name = guildDb.getGuild().toString();
                    }
                } else {
                    DBAlliance alliance = DBAlliance.get((int) allianceOrGuildId);
                    name = alliance == null ? "AA:" + allianceOrGuildId : alliance.getName();
                }
                if (listIds) {
                    names.add(allianceOrGuildId + "");
                } else {
                    names.add(name);
                }
            }
            if (filter != null) {
                if (!coalition.toLowerCase(Locale.ROOT).contains(filter)) {
                    String finalFilter = filter;
                    names.removeIf(f -> !f.toLowerCase().contains(finalFilter));
                    if (names.isEmpty()) continue;
                }
            }

            String listBold = StringMan.join(names, ",");
            if (filter != null) {
                // replace ignore case regex
                listBold = listBold.replaceAll("(?i)" + filter, "__" + filter + "__");
            }
            response.append('\n').append("**" + coalition + "**: " + listBold);
        }

        if (response.length() == 0) return "No coalitions found";
        return response.toString().trim();
    }

    @Command(desc = "Create a new coalition with the provided alliances")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String createCoalition(@Me User user, @Me GuildDB db, Set<KingdomOrAllianceOrGuild> alliances, String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (KingdomOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isKingdom()) {
                DBAlliance alliance = DBAlliance.parse(aaOrGuild.getName());
                if (alliance == null) {
                    alliance = DBAlliance.parse(aaOrGuild.asKingdom().getName());
                }
                if (alliance == null) {
                    throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                }
                aaOrGuild = alliance;
            }
            db.addCoalition(aaOrGuild.getIdLong(), coalitionName);
            response.append("Added " + aaOrGuild.getName() + " to " + coalitionName).append("\n");
        }
        return response.toString();
    }

    @Command(desc = "Add alliances to an existing coalition\n" +
            "Note: Use `{prefix}coalition create` to use a nonstandard coalition")
    @RolePermission(Roles.MEMBER)
    public String addCoalition(@Me User user, @Me GuildDB db, Set<KingdomOrAllianceOrGuild> alliances, @GuildCoalition String coalitionName) {
        return createCoalition(user, db, alliances, coalitionName);
    }

    @Command(desc = "Delete an entire coalition")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String deleteCoalition(@Me User user, @Me GuildDB db, @GuildCoalition String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        db.removeCoalition(coalitionName);
        return "Deleted coalition " + coalitionName;
    }

    @Command(desc = "Remove alliances to a coalition\n" +
            "Note: Use `{prefix}coalition delete` to delete an entire coalition")
    @RolePermission(Roles.MEMBER)
    public String removeCoalition(@Me User user, @Me GuildDB db, Set<KingdomOrAllianceOrGuild> alliances, @GuildCoalition String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (KingdomOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isKingdom()) {
                DBAlliance alliance = DBAlliance.parse(aaOrGuild.getName());
                if (alliance == null) throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                aaOrGuild = alliance;
            }
            db.removeCoalition(aaOrGuild.getIdLong(), coalitionName);
            response.append("Removed " + aaOrGuild.getName() + " from " + coalitionName).append("\n");
        }
        return response.toString();
    }

    // Write a long description
    @Command(desc = "List the treaties of the provided alliances\n" +
            "Note: If you have the FORIEGN_AFFAIRS role you can view the pending treaties of your own alliance from its guild")
    public String treaties(@Me IMessageIO channel, @Me User user, Set<DBAlliance> alliances) {
        StringBuilder response = new StringBuilder();
        Set<DBTreaty> allTreaties = new LinkedHashSet<>();

        if (alliances.size() == 1) {
            DBAlliance alliance = alliances.iterator().next();
            allTreaties.addAll(alliance.getTreaties().values());
        } else {
            for (DBAlliance alliance : alliances) {
                Map<Integer, DBTreaty> treaties = alliance.getTreaties();
                allTreaties.addAll(treaties.values());
            }
        }

        if (allTreaties.isEmpty()) return "No treaties";

        long turn = TimeUtil.getTurn();
        for (DBTreaty treaty : allTreaties) {
            DBAlliance from = treaty.getFrom();
            DBAlliance to = treaty.getTo();
            String fromStr = from == null ? "" + treaty.getFrom_id() : from.getName();
            String toStr = to == null ? "" + treaty.getTo_id() : to.getName();
            TreatyType type = treaty.getType();

            response.append(fromStr + " | " + type + " -> " + toStr);
            response.append("\n");
        }

        String title = allTreaties.size() + " treaties";
        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command(desc = "Create an embassy channel in the embassy category")
    public String embassy(@Me GuildDB db, @Me Guild guild, @Me User user, @Me Map<DBRealm, DBKingdom> me, @Arg("The nation to create an embassy for") DBKingdom nation) {
        if (!me.values().contains(nation) && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You do not have FOREIGN_AFFAIRS";
        Category category = db.getOrThrow(GuildKey.EMBASSY_CATEGORY);
        if (category == null) {
            return "Embassies are disabled. To set it up, use " + GuildKey.EMBASSY_CATEGORY.getCommandMention() + "";
        }
        if (nation.getPosition().ordinal() < Rank.ADMIN.ordinal() && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You are not an " + Rank.ADMIN.name();
        User nationUser = nation.getUser();
        if (nationUser == null) return "Kingdom " + nation.getUrl(null) + " is not registered";
        Member member = guild.getMember(nationUser);
        if (member == null) return "User " + user.getName() + " is not in this guild";

        Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
        Role role = aaRoles.get(nation.getAlliance_id());
        if (role == null) {
            db.addCoalition(nation.getAlliance_id(), Coalition.MASKED_ALLIANCES);
            AutoRoleOption autoRoleValue = db.getOrNull(GuildKey.AUTOROLE);
            if (autoRoleValue == null || autoRoleValue == AutoRoleOption.FALSE) {
                return "AutoRole is disabled. See " + GuildKey.AUTOROLE.getCommandMention() + "";
            }
            db.getAutoRoleTask().syncDB();
            db.getAutoRoleTask().autoRole(member, f -> {});
            aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            role = aaRoles.get(nation.getAlliance_id());
            if (role == null) {
                return "No alliance role found. Please try " + CM.role.auto.all.cmd.create().toSlashCommand() + "";
            }
        }

        for (TextChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1]) && Integer.parseInt(split[split.length - 1]) == nation.getAlliance_id()) {
                updateEmbassyPerms(channel, role, user, true);
                return "Embassy: <#" + channel.getId() + ">";
            }
        }
        if (nation.getPosition().ordinal() < Rank.ADMIN.ordinal()) {
            return "You must be an ADMIN to create an embassy";
        }

        String embassyName = nation.getAllianceName() + "-" + nation.getAlliance_id();

        TextChannel channel = RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category));
        updateEmbassyPerms(channel, role, nationUser, true);

        return "Embassy: <#" + channel.getId() + ">";
    }

    public static void updateEmbassyPerms(TextChannel channel, Role role, User user, boolean mention) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));

        Role ambassador = Roles.FOREIGN_AFFAIRS.toRole(channel.getGuild());
        if (ambassador != null) {
            RateLimitUtil.complete(channel.upsertPermissionOverride(ambassador).grant(Permission.VIEW_CHANNEL));
        }

        if (mention) {
            RateLimitUtil.queue(channel.sendMessage(user.getAsMention()));
        }
    }
}
