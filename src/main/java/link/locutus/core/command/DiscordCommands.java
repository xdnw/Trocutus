package link.locutus.core.command;

import com.google.gson.JsonObject;
import link.locutus.Trocutus;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Range;
import link.locutus.command.binding.annotation.RegisteredRole;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.binding.annotation.Timediff;
import link.locutus.command.binding.annotation.Timestamp;
import link.locutus.command.command.CommandBehavior;
import link.locutus.command.command.CommandResult;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.command.impl.discord.permission.HasApi;
import link.locutus.command.impl.discord.permission.IsAlliance;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholders;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.SheetKeys;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.entities.UnmaskedReason;
import link.locutus.core.db.guild.interview.IACategory;
import link.locutus.core.db.guild.role.IAutoRoleTask;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.FileUtil;
import link.locutus.util.MarkupUtil;
import link.locutus.util.MathMan;
import link.locutus.util.Messages;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.spreadsheet.SpreadSheet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IMemberContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.json.JSONObject;

import java.awt.Color;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DiscordCommands {
    @Command(desc = "Add or remove a role from a set of members on discord")
    @RolePermission(Roles.ADMIN)
    public String mask(@Me Member me, @Me GuildDB db, Set<Member> members, Role role, boolean value, @Arg("If the role should be added or removed from all other members\n" +
            "If `value` is true, the role will be removed, else added") @Switch("r") boolean toggleMaskFromOthers) {
        List<Role> myRoles = me.getRoles();
        List<String> response = new ArrayList<>();
        for (Member member : members) {
            User user = member.getUser();
            List<Role> roles = member.getRoles();
            if (value && roles.contains(role)) {
                response.add(user.getName() + " already has the role: `" + role + "`");
                continue;
            } else if (!value && !roles.contains(role)) {
                response.add(user.getName() + ": does not have the role: `" + role + "`");
                continue;
            }
            if (value) {
                RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                response.add(user.getName() + ": Added role to member");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member");
            }
        }
        if (toggleMaskFromOthers) {
            for (Member member : db.getGuild().getMembers()) {
                if (members.contains(member)) continue;
                List<Role> memberRoles = member.getRoles();
                if (value) {
                    if (memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        response.add(member.getUser().getName() + ": Removed role from member");
                    }
                } else {
                    if (!memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                        response.add(member.getUser().getName() + ": Added role to member");
                    }
                }
            }
        }
        return StringMan.join(response, "\n").trim();
    }

    @Command(desc = "Remove a discord role the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public String unregisterRole(@Me User user, @Me Guild guild, @Me GuildDB db, Roles locutusRole, @Arg("Only remove a role mapping for this alliance") @Default DBAlliance alliance) {
        return aliasRole(user, guild, db, locutusRole, null, alliance, true);
    }

    @Command(desc = "Set the discord roles the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    @IsAlliance
    public static String aliasRole(@Me User author, @Me Guild guild, @Me GuildDB db, @Default Roles locutusRole, @Default() Role discordRole, @Arg("If the role mapping is only for a specific alliance (WIP)") @Default() DBAlliance alliance, @Arg("Remove the existing mapping instead of setting it") @Switch("r") boolean removeRole) {
        if (alliance != null && !db.isAllianceId(alliance.getId())) {
            return "Alliance: " + alliance.getId() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }
        StringBuilder response = new StringBuilder();
        boolean showGlobalMappingInfo = false;

        if (locutusRole == null) {
            if (discordRole != null) {
                List<String> rolesListStr = new ArrayList<>();
                Map<Roles, Map<Long, Long>> allMapping = db.getMappingRaw();
                if (removeRole) {
                    // remove all roles registered to it
                    for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                        for (Map.Entry<Long, Long> discEntry : locEntry.getValue().entrySet()) {
                            long aaId =discEntry.getKey();
                            if (alliance != null && aaId != alliance.getId()) continue;
                            if (discEntry.getValue() == discordRole.getIdLong()) {
                                rolesListStr.add("Removed " + locEntry.getKey().name() + " from " + discordRole.getName() + " (AA:" + aaId + ")");
                                db.deleteRole(locEntry.getKey(), aaId);
                            }
                        }
                    }
                    if (rolesListStr.isEmpty()) {
                        return "No aliases found for " + discordRole.getName();
                    }
                    response.append("Removed aliases for " + discordRole.getName() + ":\n- ");
                    response.append(StringMan.join(rolesListStr, "\n- "));
                    response.append("\n\nUse " + CM.role.alias.set.cmd.toSlashMention() + " to view current role aliases");
                    return response.toString();
                }

                for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                    Map<Long, Long> aaToRoleMap = locEntry.getValue();
                    showGlobalMappingInfo |= aaToRoleMap.size() > 1 && aaToRoleMap.containsKey(0L);
                    for (Map.Entry<Long, Long> discEntry : aaToRoleMap.entrySet()) {
                        if (discEntry.getValue() == discordRole.getIdLong()) {
                            Roles role = locEntry.getKey();
                            long aaId = discEntry.getKey();
                            if (aaId == 0) {
                                rolesListStr.add("*:" + role.name());
                            } else {
                                DBAlliance aa = DBAlliance.get((int) aaId);
                                String name = aa == null ? "AA:" + aaId : aa.getName() + "/" + aaId;
                                rolesListStr.add(name + ":" + role.name());
                            }
                        }
                    }
                }
                if (rolesListStr.isEmpty()) {
                    return "No aliases found for " + discordRole.getName();
                }
                response.append("Aliases for " + discordRole.getName() + ":\n- ");
                response.append(StringMan.join(rolesListStr, "\n- "));
                if (showGlobalMappingInfo) response.append("\n`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
                return response.toString();
            }

            List<String> registeredRoles = new ArrayList<>();
            List<String> unregisteredRoles = new ArrayList<>();
            for (Roles role : Roles.values) {
                Map<Long, Role> mapping = db.getAccountMapping(role);
                if (mapping != null && !mapping.isEmpty()) {
                    registeredRoles.add(role + ":\n" + mappingToString(mapping));
                    continue;
                }
                if (role.getKey() != null && db.getOrNull(role.getKey()) == null) continue;
                unregisteredRoles.add(role + ":\n" + mappingToString(mapping));
            }

            if (!registeredRoles.isEmpty()) {
                response.append("**Registered Roles**:\n" + StringMan.join(registeredRoles, "\n") + "\n");
            }
            if (!unregisteredRoles.isEmpty()) {
                response.append("**Unregistered Roles**:\n" + StringMan.join(unregisteredRoles, "\n") + "\n");
            }
            response.append("Provide a value for `locutusRole` for specific role information.\n" +
                    "Provide a value for `discordRole` to register a role.\n");

            return response.toString();
        }

        if (discordRole == null) {
            if (removeRole) {
                Role alias = db.getRole(locutusRole, alliance != null ? (long) alliance.getId() : null);
                if (alias == null) {
                    String allianceStr = alliance != null ? alliance.getName() + "/" + alliance.getId() : "*";
                    return "No role alias found for " + allianceStr + ":" + locutusRole.name();
                }
                if (alliance != null) {
                    db.deleteRole(locutusRole, alliance.getId());
                } else {
                    db.deleteRole(locutusRole);
                }
                response.append("Removed role alias for " + locutusRole.name() + ":\n");
            }
            Map<Long, Role> mapping = db.getAccountMapping(locutusRole);
            response.append("**" + locutusRole.name() + "**:\n");
            response.append("`" + locutusRole.getDesc() + "`\n");
            if (mapping.isEmpty()) {
                response.append("No value set.");
            } else {
                response.append("```\n" + mappingToString(mapping) + "```\n");
            }
            response.append("Provide a value for `discordRole` to register a role.\n");
            if (mapping.size() > 1 && mapping.containsKey(0L)) {
                response.append("`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
            }
            return response.toString().trim();
        }

        if (removeRole) {
            throw new IllegalArgumentException("Cannot remove role alias with this command. Use " + CM.role.alias.remove.cmd.create(locutusRole.name(), null).toSlashCommand() + "");
        }


        int aaId = alliance == null ? 0 : alliance.getId();
        String allianceStr = alliance == null ? "*" : alliance.getName() + "/" + aaId;
        db.addRole(locutusRole, discordRole, aaId);
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + " for alliance " + allianceStr + "\n" +
                "To unregister, use " + CM.role.alias.remove.cmd.create(locutusRole.name(), null).toSlashCommand() + "";
    }

    private static String mappingToString(Map<Long, Role> mapping) {
        List<String> response = new ArrayList<>();
        for (Map.Entry<Long, Role> entry : mapping.entrySet()) {
            Role role = entry.getValue();
            long aaId = entry.getKey();
            if (aaId == 0) {
                response.add("*:" + role.getName());
            } else {
                response.add(aaId + ": " + role.getName());
            }
        }
        if (response.isEmpty()) return "";
        return "- " + StringMan.join(response, "\n- ");
    }

    @Command(desc = "Purge a category's channels older than the time specified")
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min=60) @Timestamp long cutoff) {
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.getLatestMessageIdLong() > 0) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(message).toEpochSecond() * 1000L;
                    if (created > cutoff) {
                        continue;
                    }
                } catch (Throwable ignore) {}
            }
            RateLimitUtil.queue(GuildMessageChannel.delete());
            deleted++;
            continue;
        }
        return "Deleted " + deleted + " channels";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredGuilds(boolean checkMessages) {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Trocutus.imp().getGuildDatabases().values()) {
            Guild guild = db.getGuild();
            Member owner = db.getGuild().getOwner();
            Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(owner.getUser());

            Set<Integer> aaIds = db.getAllianceIds();

            long maxActive = kingdoms.isEmpty() ? 0 : kingdoms.values().stream().mapToLong(DBKingdom::getActive_m).max().getAsLong();
            if ( kingdoms.isEmpty() || maxActive > 30000) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": owner (nation:" + kingdoms.values().stream().map(DBKingdom::getName).collect(Collectors.toSet()) + ") is inactive " +
                        TimeUtil.secToTime(TimeUnit.MINUTES, maxActive) + "\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (!aaIds.isEmpty() && !db.isValidAlliance()) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": alliance is invalid (" + aaIds +  ")\n");
                continue;
            }

            if (aaIds.isEmpty() && kingdoms.isEmpty() && checkMessages) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                boolean error = false;
                long last = 0;

                outer:
                for (GuildMessageChannel channel : guild.getTextChannels()) {
                    if (channel.getLatestMessageIdLong() <= 0) continue;
                    try {
                        long latestSnowflake = channel.getLatestMessageIdLong();
                        long latestMs = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestSnowflake).toEpochSecond() * 1000L;
                        if (latestMs > cutoff) {
                            List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(5));
                            for (Message message : messages) {
                                if (message.getAuthor().isSystem() || message.getAuthor().isBot() || guild.getMember(message.getAuthor()) == null) {
                                    continue;
                                }
                                last = Math.max(last, message.getTimeCreated().toEpochSecond() * 1000L);
                                if (last > cutoff) {
                                    break outer;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        error = true;
                    }
                }
                if (last < cutoff) {
                    response.append(guild + ": has no recent messages\n");
                    continue;
                }
            }
        }
        return response.toString();
    }

    @Command(desc = "Modify the permissions for a list of nations in a channel.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public static String channelPermissions(@Me Member author, @Me Guild guild, TextChannel channel, Set<DBKingdom> nations, Permission permission,
                                            @Arg("Negate the permission") @Switch("n") boolean negate,
                                            @Arg("Remove the permission from all other users")
                                            @Switch("r") boolean removeOthers,
                                            @Arg("Log the changes to user permissions that are made")
                                            @Switch("l") boolean listChanges,
                                            @Switch("p") boolean pingAddedUsers) throws ExecutionException, InterruptedException {
        if (!author.hasPermission(channel, Permission.MANAGE_PERMISSIONS))
            throw new IllegalArgumentException("You do not have " + Permission.MANAGE_PERMISSIONS + " in " + channel.getAsMention());

        Set<Member> members = new HashSet<>();
        for (DBKingdom nation : nations) {
            User user = nation.getUser();
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    members.add(member);
                }
            }
        }

        List<String> changes = new ArrayList<>();

        Set<Member> toRemove = new HashSet<>();

        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
            Member member = override.getMember();
            if (member == null || member.getUser().isBot()) continue;

            boolean allowed = (override.getAllowedRaw() & permission.getRawValue()) > 0;
            boolean denied = (override.getDeniedRaw() & permission.getRawValue()) > 0;
            boolean contains = members.contains(member);
            boolean isSet = negate ? denied : allowed;
            if (contains && isSet) {
                members.remove(member);
            } else if (!contains && isSet && removeOthers) {
                toRemove.add(member);
            }
        }
        Function<Member, String> nameFuc = Member::getEffectiveName;
        if (pingAddedUsers) {
            listChanges = true;
            nameFuc = IMentionable::getAsMention;
        }

        List<Future<?>> tasks = new ArrayList<>();
        for (Member member : members) {
            PermissionOverrideAction override = channel.upsertPermissionOverride(member);
            PermissionOverrideAction action;
            if (negate) {
                action = override.deny(permission);
            } else {
                action = override.grant(permission);
            }
            tasks.add(RateLimitUtil.queue(action));

            changes.add("Set " + permission + "=" + !negate + " for " + nameFuc.apply(member));
        }

        for (Member member : toRemove) {
            tasks.add(RateLimitUtil.queue(channel.upsertPermissionOverride(member).clear(permission)));
            changes.add("Clear " + permission + " for " + nameFuc.apply(member));
        }

        for (Future<?> task : tasks) {
            task.get();
        }

        StringBuilder response = new StringBuilder("Done.");
        if (listChanges && !changes.isEmpty()) {
            response.append("\n- ").append(StringMan.join(changes, "\n- "));
        }
        return response.toString();
    }

    @Command(desc = "Have the bot say the provided message, with placeholders replaced.")
    public String say(KingdomPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me Guild guild, @Me IMessageIO channel, @Me User author, @Me Map<DBRealm, DBKingdom> me, @TextArea String msg) {
        msg = DiscordUtil.trimContent(msg);
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");
        msg = msg + "\n\n- " + author.getAsMention();

        msg = placeholders.format(store, msg);
        return msg;
    }

    @Command(desc = "Import all emojis from another guild", aliases = {"importEmoji", "importEmojis"})
    @RolePermission(Roles.ADMIN)
    public String importEmojis(@Me IMessageIO channel, Guild guild) throws ExecutionException, InterruptedException {
        if (!Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
            throw new IllegalStateException("Please enable DISCORD.CACHE.EMOTE in " + Settings.INSTANCE.getDefaultFile());
        }
        if (!Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
            throw new IllegalStateException("Please enable DISCORD.INTENTS.EMOJI in " + Settings.INSTANCE.getDefaultFile());
        }
        List<RichCustomEmoji> emotes = guild.getEmojis();

        List<Future<?>> tasks = new ArrayList<>();
        for (RichCustomEmoji emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }

            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(url);

            channel.send("Creating emote: " + emote.getName() + " | " + url);

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                tasks.add(RateLimitUtil.queue(guild.createEmoji(emote.getName(), icon)));
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        return "Done!";
    }

    @Command(desc = """
            Generate a card which runs a command when users react to it.
            Put commands inside "quotes".
            Prefix a command with a #channel e.g. `"#channel {prefix}embedcommand"` to have the command output go there

            Prefix the command with:`~{prefix}command` to remove the user's reaction upon use and keep the card
            `_{prefix}command` to remove ALL reactions upon use and keep the card
            `.{prefix}command` to keep the card upon use

            Example:
            `{prefix}embed 'Some Title' 'My First Embed' '~{prefix}fun say Hello {nation}' '{prefix}fun say "Goodbye {nation}"'`""",
            aliases = {"card", "embed"})
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String card(@Me IMessageIO channel, String title, String body, @TextArea List<String> commands) {
        try {
            String emoji = "\ufe0f\u20e3";

            if (commands.size() > 10) {
                return "Too many commands (max: 10, provided: " + commands.size() + ")\n" +
                        "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space.";
            }

            System.out.println("Commands: " + commands);
            IMessageBuilder msg = channel.create().embed(title, body);
            for (int i = 0; i < commands.size(); i++) {
                String cmd = commands.get(i);
                String codePoint = i + emoji;

                msg = msg.commandButton(cmd, codePoint);
            }
            msg.send();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Command(desc = "Create a channel with name in a specified category and ping the specified roles upon creation.")
    public String channel(KingdomPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me JSONObject command, @Me User author, @Me Guild guild, @Me IMessageIO output, @Me Map<DBRealm, DBKingdom> nation,
                          String channelName, Category category, @Default String copypasta,
                          @Switch("i") boolean addInternalAffairsRole,
                          @Switch("m") boolean addMilcom,
                          @Switch("f") boolean addForeignAffairs,
                          @Switch("e") boolean addEcon,
                          @Switch("p") boolean pingRoles,
                          @Switch("a") boolean pingAuthor

    ) throws ExecutionException, InterruptedException {
        channelName = placeholders.format(store, channelName);

        Member member = guild.getMember(author);

        List<IPermissionHolder> holders = new ArrayList<>();
        holders.add(member);
        assert member != null;
        holders.addAll(member.getRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        IMessageBuilder msg = output.getMessage();
        boolean hasOverride = msg != null && msg.getAuthor().getIdLong() == Settings.INSTANCE.APPLICATION_ID;
        for (IPermissionHolder holder : holders) {
            PermissionOverride overrides = category.getPermissionOverride(holder);
            if (overrides == null) continue;
            if (overrides.getAllowed().contains(Permission.MANAGE_CHANNEL)) {
                hasOverride = true;
                break;
            }
        }

        if (!hasOverride) {
            return "No permission to create channel in: " + category.getName();
        }

        Set<Roles> roles = new HashSet<>();
        if (addInternalAffairsRole) roles.add(Roles.INTERNAL_AFFAIRS);
        if (addMilcom) roles.add(Roles.MILCOM);
        if (addForeignAffairs) roles.add(Roles.FOREIGN_AFFAIRS);
        if (addEcon) roles.add(Roles.ECON);
        if (roles.isEmpty()) roles.add(Roles.INTERNAL_AFFAIRS);

        GuildMessageChannel createdChannel = null;
        List<TextChannel> channels = category.getTextChannels();
        for (TextChannel channel : channels) {
            if (channel.getName().equalsIgnoreCase(channelName)) {
                createdChannel = updateChannel(channel, member, roles);
                break;
            }
        }
        if (createdChannel == null) {
            createdChannel = updateChannel(RateLimitUtil.complete(category.createTextChannel(channelName)), member, roles);
            DiscordChannelIO io = new DiscordChannelIO(createdChannel);
            IMessageBuilder toSend = null;
            if (copypasta != null && !copypasta.isEmpty()) {
                String copyPasta = db.getCopyPasta(copypasta, true);
                if (copyPasta != null) {
                    if (toSend == null) toSend = io.create();
                    toSend.append(copyPasta);
                }
            }
            if (pingRoles) {
                for (Roles dept : roles) {
                    Role role = dept.toRole(guild);
                    if (role != null) {
                        if (toSend == null) toSend = io.create();
                        toSend.append("\n" + role.getAsMention());
                    }
                }
            }
            if (pingAuthor) {
                if (toSend == null) toSend = io.create();
                toSend.append("\n" + author.getAsMention());
            }
            if (toSend != null) toSend.send();
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(holder).grant(Permission.VIEW_CHANNEL));

        for (Roles dept : depts) {
            Role role = dept.toRole(channel.getGuild());
            if (role != null) {
                RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));
            }
        }
        return channel;
    }

    @Command(desc = "Get info about a bot embed")
    @RolePermission(value = Roles.ADMIN)
    public String embedInfo(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embed found.";

        MessageEmbed embed = embeds.get(0);
        String title = embed.getTitle();
        String desc = embed.getDescription();
        Map<String, String> reactions = DiscordUtil.getReactions(embed);
        Map<String, String> commands = new HashMap<>();

        if (reactions == null || reactions.isEmpty()) {
            return "No embed commands found.";
        }

        List<Button> buttons = message.getButtons();
        for (Button button : buttons) {
            String id = button.getId();
            if (id == null) continue;
            System.out.println("ID " + id);
            if (id.isBlank()) {
                commands.put(button.getLabel(), "");
            } else if (MathMan.isInteger(id)) {
                String cmd = reactions.get(id);
                if (cmd != null) {
                    commands.put(button.getLabel(), cmd);
                } else {
                    commands.put(button.getLabel(), id);
                }
            } else {
                commands.put(button.getLabel(), id);
            }
        }
        if (buttons.isEmpty()) {
            commands.putAll(reactions);
        }

        String cmd = CM.embed.create.cmd.create(title, desc, StringMan.join(commands.values(), ",")).toSlashCommand();
        return "```" + cmd + "```";
    }

    @Command(desc = "Update a bot embed")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String updateEmbed(@Me Guild guild, @Me User user, @Me IMessageIO io, @Switch("r") @RegisteredRole Roles requiredRole, @Switch("c") Color color, @Switch("t") String title, @Switch("d") String desc) {
        IMessageBuilder message = io.getMessage();

        if (message == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID)
            return "This command can only be run when bound to a Trocutus embed.";
        if (requiredRole != null) {
            if (!requiredRole.has(user, guild)) {
                return null;
            }
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);

        if (color != null) {
            builder.setColor(color);
        }

        if (title != null) {
            builder.setTitle(parse(title.replace(("{title}"), Objects.requireNonNull(embed.getTitle())), embed, message));
        }

        if (desc != null) {
            builder.setDescription(parse(desc.replace(("{description}"), Objects.requireNonNull(embed.getDescription())), embed, message));
        }

        message.clearEmbeds();
        message.embed(builder.build());
        message.send();

        return null;
    }

    private String parse(String arg, MessageEmbed embed, IMessageBuilder message) {
        long timestamp = message.getTimeCreated();
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

    @Command(desc = "Unregister a nation to a discord user")
    public String unregister(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Default("%user%") DBKingdom nation, @Switch("f") boolean force) {
        User nationUser = nation.getUser();
        if (nationUser == null) return "That nation is not registered.";
        if (force && !Roles.ADMIN.hasOnRoot(user)) return "You do not have permission to force un-register.";
        if (!user.equals(nationUser) && !force) {
            String title = "Unregister another user.";
            String body = nation.getKingdomUrlMarkup(nation.getSlug(), true) + " | " + nationUser.getAsMention() + " | " + nationUser.getName();
            io.create().confirmation(title, body, command).send();
            return null;
        }
        Trocutus.imp().getDB().unregister(user.getIdLong(), nation);
        return "Unregistered " + nationUser.getAsMention() + " from " + nation.getUrl(nation.getSlug());
    }

    @Command(desc = "Lists the shared servers where a user has a role.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String hasRole(User user, Roles role) {
        StringBuilder response = new StringBuilder();
        for (Guild other : user.getMutualGuilds()) {
            Role discRole = role.toRole(other);
            if (Objects.requireNonNull(other.getMember(user)).getRoles().contains(discRole)) {
                response.append(user.getName()).append(" has ").append(role.name()).append(" | @").append(discRole.getName()).append(" on ").append(other).append("\n");
            }
        }
        return response.toString();
    }

    @Command(desc = "Move a discord channel up 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelUp(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() - 1));
        return null;
    }

    @Command(desc = "Delete a discord channel")
    @RolePermission(value = Roles.ADMIN)
    public String deleteChannel(@Me Guild guild, @Me User user, @Me Member member, MessageChannel channel) {
        GuildMessageChannel text = (GuildMessageChannel) channel;
        String[] split = text.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(user, guild)) && text.canTalk(member)) {
            RateLimitUtil.queue(text.delete());
            return null;
        } else {
            return "You do not have permission to close that channel.";
        }

    }

    @Command(desc = "Move a discord channel down 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelDown(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() + 1));
        return null;
    }

    @Command(desc = "Set the category for a discord channel")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelCategory(@Me Guild guild, @Me Member member, @Me TextChannel channel, Category category) {
        if (channel.getParentCategory() != null && channel.getParentCategory().getIdLong() == category.getIdLong()) {
            return "Channel is already in category: " + category;
        }
        String[] split = channel.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(member)) && channel.canTalk(member)) {
            RateLimitUtil.queue(channel.getManager().setParent(category));
            return null;
        } else {
            return "You do not have permission to move that channel.";
        }
    }

    @Command(desc = "Add a discord role to all users in a server")
    @RolePermission(Roles.ADMIN)
    public String addRoleToAllMembers(@Me Guild guild, Role role) {
        int amt = 0;
        for (Member member : guild.getMembers()) {
            if (!member.getRoles().contains(role)) {
                RateLimitUtil.queue(guild.addRoleToMember(member, role));
                amt ++;
            }
        }
        return "Added " + amt + " roles to members (note: it may take a few minutes to update)";
    }

    @Command(desc = "View a list of reactions on a message sent by or mentioning the bot")
    @RolePermission(Roles.ADMIN)
    public String msgInfo(@Me IMessageIO channel, Message message, @Arg("List the ids of users who reacted")@Switch("i") boolean useIds) {
        StringBuilder response = new StringBuilder();

        List<MessageReaction> reactions = message.getReactions();
        Map<User, List<String>> reactionsByUser = new LinkedHashMap<>();
        for (MessageReaction reaction : reactions) {
            String emoji = reaction.getEmoji().asUnicode().getAsCodepoints();
            List<User> users = RateLimitUtil.complete(reaction.retrieveUsers());
            for (User user : users) {
                reactionsByUser.computeIfAbsent(user, f -> new ArrayList<>()).add(emoji);
            }
        }

        String title = "Message " + message.getIdLong();
        response.append("```" + DiscordUtil.trimContent(message.getContentRaw()).replaceAll("`", "\\`") + "```\n\n");

        if (!reactionsByUser.isEmpty()) {
            for (Map.Entry<User, List<String>> entry : reactionsByUser.entrySet()) {
                if (useIds) {
                    response.append(entry.getKey().getIdLong() + "\t" + StringMan.join(entry.getValue(), ","));
                } else {
                    response.append(entry.getKey().getAsMention() + "\t" + StringMan.join(entry.getValue(), ","));
                }
                response.append("\n");
            }
        }

        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command(desc = "Close inactive channels in a category")
    @RolePermission(value = {
            Roles.INTERNAL_AFFAIRS_STAFF,
            Roles.INTERNAL_AFFAIRS,
            Roles.ECON_STAFF,
            Roles.ECON,
            Roles.MILCOM,
            Roles.MILCOM_NO_PINGS,
            Roles.FOREIGN_AFFAIRS,
            Roles.FOREIGN_AFFAIRS_STAFF,
    }, any = true)
    public String closeInactiveChannels(@Me GuildDB db, @Me IMessageIO outputChannel, Category category, @Arg("Close channels older than age") @Timediff long age, @Switch("f") boolean force) {
        long cutoff = System.currentTimeMillis() - age;

        IMessageBuilder msg = outputChannel.create();
        for (GuildMessageChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (!MathMan.isInteger(split[split.length - 1])) continue;

            if (channel.getLatestMessageIdLong() > 0) {
                long lastId = channel.getLatestMessageIdLong();
                long lastMessageTime = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(lastId).toEpochSecond() * 1000L;
                if (lastMessageTime > cutoff) continue;
            }

            String append = close(db, channel, force);
            msg = msg.append(append);
        }
        msg.append("Done!").send();
        return null;
    }

    @Command(desc = "List the self roles that you can assign yourself via the bot")
    public String listAssignableRoles(@Me GuildDB db, @Me Member member) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildKey.ASSIGNABLE_ROLES);
        if (assignable == null || assignable.isEmpty()) {
            return "No roles found. See `" +  CM.role.assignable.create.cmd.toSlashMention() + "`";
        }
        assignable = new HashMap<>(assignable);
        if (!Roles.ADMIN.has(member)) {
            Set<Role> myRoles = new HashSet<>(member.getRoles());
            assignable.entrySet().removeIf(f -> !myRoles.contains(f.getValue()));
        }

        if (assignable.isEmpty()) return "You do not have permission to assign any roles";


        StringBuilder response = new StringBuilder();
        for (Map.Entry<Role, Set<Role>> entry : assignable.entrySet()) {
            Role role = entry.getKey();
            response.append("\n" + role.getName() + ":\n- "
                    + StringMan.join(entry.getValue().stream().map(Role::getName).collect(Collectors.toList()), "\n- "));
        }

        return response.toString().trim();
    }

    @Command(desc = "Allow a role to add/remove roles from users")
    @RolePermission(Roles.ADMIN)
    public String addAssignableRole(@Me GuildDB db, @Arg("Require this role in order to use the specified self roles") Role requireRole, Set<Role> assignableRoles) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildKey.ASSIGNABLE_ROLES);
        if (assignable == null) assignable = new HashMap<>();

        assignable.computeIfAbsent(requireRole, f -> new HashSet<>()).addAll(assignableRoles);

        StringBuilder result = new StringBuilder();
        result.append(GuildKey.ASSIGNABLE_ROLES.set(db, assignable)).append("\n");

        result.append(StringMan.getString(requireRole) + " can now add/remove " + StringMan.getString(assignableRoles) + " via " + CM.role.add.cmd.toSlashMention() + " / " + CM.role.assignable.remove.cmd.toSlashMention() + "\n" +
                "- To see a list of current mappings, use " + CM.settings.info.cmd.create(GuildKey.ASSIGNABLE_ROLES.name(), null, null) + "");
        return result.toString();
    }

    @Command(desc = "Remove a role from adding/removing specified roles\n" +
            "(having manage roles perm on discord overrides this)")
    @RolePermission(Roles.ADMIN)
    public String removeAssignableRole(@Me GuildDB db, Role requireRole, Set<Role> assignableRoles) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildKey.ASSIGNABLE_ROLES);
        if (assignable == null) assignable = new HashMap<>();

        if (!assignable.containsKey(requireRole)) {
            return requireRole + " does not have any roles it can assign";
        }

        StringBuilder response = new StringBuilder();
        Set<Role> current = assignable.get(requireRole);

        for (Role role : assignableRoles) {
            if (current.contains(role)) {
                current.remove(role);
                response.append("\n" + requireRole + " can no longer assign " + role);
            } else {
                response.append("\nUnable to remove " + role + " (no mapping found)");
            }
        }

        response.append("\n" + GuildKey.ASSIGNABLE_ROLES.set(db, assignable));

        return response.toString() + "\n" +
                "- To see a list of current mappings, use " + GuildKey.ASSIGNABLE_ROLES.getCommandMention() + "";
    }

    @Command(desc = "Add discord role to a user\n" +
            "See: `{prefix}self list`")
    public String addRole(@Me GuildDB db, @Me Member author, Member member, Role addRole) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildKey.ASSIGNABLE_ROLES);
        if (assignable == null) return "`!KeyStore ASSIGNABLE_ROLES` is not set`";
        boolean canAssign = Roles.ADMIN.has(author);
        if (!canAssign) {
            for (Role role : author.getRoles()) {
                if (assignable.getOrDefault(role, Collections.emptySet()).contains(addRole)) {
                    canAssign = true;
                    break;
                }
            }
        }
        if (!canAssign) {
            return "No permission to assign " + addRole + " (see: `listAssignableRoles` | ADMIN: see `" +  CM.role.assignable.create.cmd.toSlashMention() + "`)";
        }
        if (member.getRoles().contains(addRole)) {
            return member + " already has " + addRole;
        }
        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, addRole));
        return "Added " + addRole + " to " + member;
    }

    @Command(desc = "Remove a role to a user\n" +
            "See: `{prefix}self list`")
    @RolePermission(value = {
            Roles.INTERNAL_AFFAIRS_STAFF,
            Roles.INTERNAL_AFFAIRS,
            Roles.ECON_STAFF,
            Roles.ECON,
            Roles.MILCOM,
            Roles.MILCOM_NO_PINGS,
            Roles.FOREIGN_AFFAIRS,
            Roles.FOREIGN_AFFAIRS_STAFF,
    }, any = true)
    public String removeRole(@Me GuildDB db, @Me Member author, Member member, Role addRole) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildKey.ASSIGNABLE_ROLES);
        if (assignable == null) return "`!KeyStore ASSIGNABLE_ROLES` is not set`";
        boolean canAssign = Roles.ADMIN.has(author);
        if (!canAssign) {
            for (Role role : author.getRoles()) {
                if (assignable.getOrDefault(role, Collections.emptySet()).contains(addRole)) {
                    canAssign = true;
                    break;
                }
            }
        }
        if (!canAssign) {
            return "No permission to assign " + addRole + " (see: `listAssignableRoles` | ADMIN: see `" +  CM.role.alias.set.cmd.toSlashMention() + "`)";
        }
        if (!member.getRoles().contains(addRole)) {
            return member + " does not have " + addRole;
        }
        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, addRole));
        return "Removed " + addRole + " to " + member;
    }

    @Command(desc = "Bulk send the result of a bot command to a list of nations")
    @RolePermission(value=Roles.ADMIN)
    public String mailCommandOutput(KingdomPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me Guild guild, @Me User author, @Me IMessageIO channel,
                                    @Arg("Kingdoms to mail command results to") Set<DBKingdom> nations,
                                    String subject,
                                    @Arg("The locutus command to run") String command,
                                    @Arg("Message to send along with the command result")
                                    @TextArea String body, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.MAIL_RESPONSES_SHEET);
        }

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "mail-id",
                "subject",
                "body"
        ));

        sheet.setHeader(header);
        Map<DBKingdom, Map.Entry<CommandResult, String>> errors = new LinkedHashMap<>();

        if (nations.size() > 300 && !Roles.ADMIN.hasOnRoot(author)) {
            return "Max allowed: 300 nations.";
        }

        if (nations.isEmpty()) return "No nations specified";

        Future<IMessageBuilder> msgFuture = channel.send("Please wait...");
        long start = System.currentTimeMillis();

        int success = 0;
        for (DBKingdom nation : nations) {
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                try {
                    msgFuture.get().clear().append("Running for: " + nation.getName() + "...").send();
                } catch (InterruptedException | ExecutionException e) {
                    // ignore
                }
            }
            User nationUser = nation.getUser();
            String subjectFormat = placeholders.format(store, subject);
            String bodyFormat = placeholders.format(store, body);

            Map.Entry<CommandResult, String> response = nation.runCommandInternally(guild, nationUser, command);
            CommandResult respType = response.getKey();
            String cmdMsg = response.getValue();

            if (respType == CommandResult.SUCCESS) {
                if (!bodyFormat.isEmpty()) {
                    bodyFormat = MarkupUtil.markdownToHTML(bodyFormat) + "<br>";
                }
                bodyFormat += cmdMsg;

                header.set(0, MarkupUtil.sheetUrl(nation.getName(), nation.getUrl(null)));
                header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getUrl(null)));
                header.set(2, "");
                header.set(3, subjectFormat);
                header.set(4, bodyFormat);

                sheet.addRow(header);
                success++;
            } else{
                if (cmdMsg == null) {
                    respType = CommandResult.NO_RESPONSE;
                } else if (cmdMsg.isEmpty()) {
                    respType = CommandResult.EMPTY_RESPONSE;
                }
                errors.put(nation, new AbstractMap.SimpleEntry<>(respType, cmdMsg));
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);

        List<String> errorMsgs = new ArrayList<>();
        if (!errors.isEmpty()) {
            for (Map.Entry<DBKingdom, Map.Entry<CommandResult, String>> entry : errors.entrySet()) {
                DBKingdom nation = entry.getKey();
                Map.Entry<CommandResult, String> error = entry.getValue();
                errorMsgs.add(nation.getName() + " -> " + error.getKey() + ": " + error.getValue());
            }
        }

        String title = "Send " + success + " messages";
        StringBuilder embed = new StringBuilder();
        IMessageBuilder msg = channel.create();
        sheet.attach(msg, embed, false, 0);
        embed.append("\nPress `confirm` to confirm");
//        CM.mail.sheet cmd = CM.mail.sheet.cmd.create(sheet.getURL(), null);
//
//        msg.confirmation(title, embed.toString(), cmd).send(); <--- todo that too

        if (errorMsgs.isEmpty()) return null;
        return "Errors\n- " + StringMan.join(errorMsgs, "\n- ");
    }

    @Command(desc = "Bulk send in-game mail from a google sheet\n" +
            "Columns: nation, subject, body")
    @HasApi
    @RolePermission(Roles.ADMIN)
    public String mailSheet(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO io, @Me User author, SpreadSheet sheet, @Switch("f") boolean confirm) {
        List<List<Object>> data = sheet.loadValues();

        List<Object> nationNames = sheet.findColumn(0, "nation", "id");
        List<Object> subjects = sheet.findColumn("subject");
        List<Object> bodies = sheet.findColumn("message", "body", "content");
        if (nationNames == null || nationNames.size() <= 1) return "No column found: `nation`";
        if (subjects == null) return "No column found: `subjects`";
        if (bodies == null) return "No column found: `message`";

        Set<Integer> alliances = new HashSet<>();
        int inactive = 0;
        int vm = 0;
        int noAA = 0;
        int applicants = 0;

        Map<DBKingdom, Map.Entry<String, String>> messageMap = new LinkedHashMap<>();

        for (int i = 1; i < nationNames.size(); i++) {
            Object nationNameObj = nationNames.get(i);
            if (nationNameObj == null) continue;
            String nationNameStr = nationNameObj.toString();
            if (nationNameStr.isEmpty()) continue;

            DBKingdom nation = DBKingdom.parse(nationNameStr);
            if (nation == null) return "Invalid nation: `" + nationNameStr + "`";

            Object subjectObj = subjects.get(i);
            Object messageObj = bodies.get(i);
            if (subjectObj == null) {
                return "No subject found for: `" + nation.getName() + "`";
            }
            if (messageObj == null) {
                return "No body found for: `" + nation.getName() + "`";
            }
            Map.Entry<String, String> msgEntry = new AbstractMap.SimpleEntry<>(
                    subjectObj.toString(), messageObj.toString());

            messageMap.put(nation, msgEntry);

            // metrics
            alliances.add(nation.getAlliance_id());
            if (nation.getActive_m() > 7200) inactive++;
            if (nation.isVacation() == true) vm++;
            if (nation.getAlliance_id() == 0) noAA++;
            if (nation.getPosition().ordinal() <= 1) applicants++;
        }

        if (messageMap.size() > 1000 && !Roles.ADMIN.hasOnRoot(author)) {
            return "Max allowed: 1000 messages.";
        }

        if (!confirm) {
            String title = "Send " + messageMap.size() + " to nations in " + alliances.size() + " alliances";
            StringBuilder body = new StringBuilder("Messages:");
            IMessageBuilder msg = io.create();
            sheet.attach(msg, body, false, 0);

            if (inactive > 0) body.append("Inactive Receivers: " + inactive + "\n");
            if (vm > 0) body.append("vm Receivers: " + vm + "\n");
            if (noAA > 0) body.append("No Alliance Receivers: " + noAA + "\n");
            if (applicants > 0) body.append("applicant receivers: " + applicants + "\n");

            body.append("\nPress to confirm");
            msg.confirmation(title, body.toString(), command, "confirm").send();
            return null;
        }

        Auth auth = db.getMailAuth();
        if (auth == null) throw new IllegalArgumentException("No guild auth set, please use " + CM.credentials.login.cmd.toSlashMention() + "");

        io.send("Sending to " + messageMap.size() + " nations in " + alliances.size() + " alliances. Please wait.");
        List<String> response = new ArrayList<>();
        int errors = 0;
        for (Map.Entry<DBKingdom, Map.Entry<String, String>> entry : messageMap.entrySet()) {
            DBKingdom nation = entry.getKey();
            Map.Entry<String, String> msgEntry = entry.getValue();
            String subject = msgEntry.getKey();
            String body = msgEntry.getValue();

            String result;
            try {
                DBKingdom sender = auth.getKingdom(nation.getRealm_id());
                if (sender == null) throw new IllegalArgumentException("No sender found for realm " + nation.getRealm_id());
                result = auth.sendMail(sender.getId(), nation, subject, body);
            } catch (IOException e) {
                errors++;
                if (errors > 50) {
                    throw new IllegalArgumentException(">50 Errors. Aborting at `" + nation.getName() + "`");
                }
                e.printStackTrace();
                result = "IOException: " + e.getMessage();
            } catch (Throwable e) {
                result = e.getMessage();
            }
            response.add(nation.getUrl(null) + " -> " + result);
        }
        return "Done!\n- " + StringMan.join(response, "\n- ");
    }

    private String channelMemberInfo(Set<Integer> aaIds, Role memberRole, Member member) {
        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(member.getUser());

        StringBuilder response = new StringBuilder(member.getUser().getName() + "#" + member.getUser().getDiscriminator());
        response.append(" | `" + member.getAsMention() + "`");
        if (kingdoms.isEmpty()) {
            response.append(" | Unregistered");
            return response.toString();
        } else {
            for (Map.Entry<DBRealm, DBKingdom> entry : kingdoms.entrySet()) {
                DBKingdom nation = entry.getValue();
                response.append("(" + nation.getName());
                if (!aaIds.isEmpty() && !aaIds.contains(nation.getId())) {
                    response.append(" | AA:" + nation.getAllianceName());
                }
                if (nation.getPosition().ordinal() <= 1) {
                    response.append(" | applicant");
                }
                if (nation.isVacation() == true) {
                    response.append(" | vm=" + nation.isVacation());
                }
                if (nation.getActive_m() > 10000) {
                    response.append(" | inactive=" + TimeUtil.minutesToTime(nation.getActive_m()));
                }
                response.append(")");
            }
        }
        if (aaIds.isEmpty() && !member.getRoles().contains(memberRole)) {
            response.append(" | No Member Role");
        }
        return response.toString();
    }

    @Command(desc = "List members who can see a discord channel")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ADMIN}, any = true)
    public String channelMembers(@Me GuildDB db, MessageChannel channel) {
        Set<Integer> aaIds = db.getAllianceIds();
        Role memberRole = Roles.MEMBER.toRole(db.getGuild());

        List<Member> members = (channel instanceof IMemberContainer) ? ((IMemberContainer) channel).getMembers() : Collections.emptyList();
        members.removeIf(f -> f.getUser().isBot() || f.getUser().isSystem());

        List<String> results = new ArrayList<>();
        for (Member member : members) {
            results.add(channelMemberInfo(aaIds, memberRole, member));
        }
        if (results.isEmpty()) return "No users found";
        return StringMan.join(results, "\n");
    }

    @Command(desc = "List all guild channels and what members have access to each")
    @RolePermission(value = {Roles.ADMIN}, any = true)
    public String allChannelMembers(@Me GuildDB db) {
        Set<Integer> aaIds = db.getAllianceIds();
        Role memberRole = Roles.MEMBER.toRole(db.getGuild());

        StringBuilder result = new StringBuilder();

        for (Category category : db.getGuild().getCategories()) {
            result.append("**" + category.getName() + "**\n");
            for (TextChannel GuildMessageChannel : category.getTextChannels()) {
                result.append(GuildMessageChannel.getAsMention() + "\n");

                for (Member member : GuildMessageChannel.getMembers()) {
                    result.append(channelMemberInfo(aaIds, memberRole, member) + "\n");
                }
            }
        }
        return result.toString();
    }

    @Command(desc = "List discord channels a member has access to")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ADMIN}, any = true)
    public String memberChannels(@Me Guild guild, Member member) {
        List<String> channels = guild.getTextChannels().stream().filter(f -> f.getMembers().contains(member)).map(f -> f.getAsMention()).collect(Collectors.toList());
        User user = member.getUser();
        return user.getName() + "#" + user.getDiscriminator() + " has access to:\n" +
                StringMan.join(channels, "\n");
    }

    @Command(desc = "Move an interview channel from the `interview-archive` category")
    @RolePermission(Roles.MEMBER)
    public String open(@Me GuildDB db, @Me User author, @Me Guild guild, @Me IMessageIO channel, @Default Category category) {
        if (!(channel instanceof TextChannel)) {
            return "Not a text channel";
        }
        String closeChar = "\uD83D\uDEAB";
        TextChannel tc = (TextChannel) channel;
        Category channelCategory = tc.getParentCategory();

        if (channelCategory != null && channelCategory.getName().toLowerCase().contains("archive") && category == null) {
            throw new IllegalArgumentException("Please provide a category to move this channel to");
        }
        if (category != null && channelCategory != null && !channelCategory.getName().toLowerCase().contains("archive") && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
            throw new IllegalArgumentException("You do not have permission to move this channel: INTERNAL_AFFAIRS_STAFF");
        }

        if (tc.getName().contains(closeChar)) {
            String newName = tc.getName().replace(closeChar, "");
            RateLimitUtil.queue(tc.getManager().setName(newName));
        }
        if (category != null) {
            RateLimitUtil.queue(tc.getManager().setParent(category));
        }
        return "Reopened channel";
    }

    @Command(desc = "Close a war room, interview or embassy discord channel")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ECON, Roles.ECON_STAFF}, any=true)
    public String close(@Me GuildDB db, @Me GuildMessageChannel channel, @Switch("f") boolean forceDelete) {
        if (!(channel instanceof TextChannel)) {
            return "Not a text channel";
        }
        String closeChar = "\uD83D\uDEAB";
        long expireTime = TimeUnit.HOURS.toMillis(24);

        boolean canClose = true;

        TextChannel tc = (TextChannel) channel;

        IACategory iaCat = db.getIACategory();
        if (iaCat != null && iaCat.isInCategory(tc)) {
            canClose = true;
        } else if (tc.getParentCategory() != null && tc.getParentCategory().getName().toLowerCase().contains("warcat")) {
            canClose = true;
        } else {
            String[] split = channel.getName().split("-");
            String id = split[split.length - 1];
            if (split.length >= 2 && MathMan.isInteger(id)) {
                canClose = true;
            }
        }
        if (canClose) {
            Category parent = tc.getParentCategory();
            if (channel.getName().contains(closeChar)) {
                RateLimitUtil.queue(((GuildMessageChannel) channel).delete());
                return null;
            } else if (parent != null && (parent.getName().toLowerCase().startsWith("treasury") || parent.getName().toLowerCase().startsWith("grant"))) {
                int i = 0;
                for (GuildMessageChannel otherChannel : db.getGuild().getTextChannels()) {
                    if (otherChannel.getName().contains(closeChar) && otherChannel.getLatestMessageIdLong() > 0) {
                        String[] split = otherChannel.getName().split("-");
                        if (split.length == 2 && MathMan.isInteger(split[1])) {
                            long id = otherChannel.getLatestMessageIdLong();
                            OffsetDateTime created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(id);
                            long diff = System.currentTimeMillis() - created.toEpochSecond() * 1000L;
                            if (diff > expireTime && ++i < 5) {
                                RateLimitUtil.queue(otherChannel.delete());
                            }
                        }
                    }
                }
                RateLimitUtil.queue(((GuildMessageChannel) channel).getManager().setName(closeChar + channel.getName()));
                return "Marked channel as closed. Auto deletion in >24h. Use " + CM.channel.open.cmd.toSlashMention() + " to reopen. Use " + CM.channel.close.cmd.toSlashMention() + " again to force close";
            }

            Category archiveCategory = db.getOrNull(GuildKey.ARCHIVE_CATEGORY);
            if (archiveCategory != null) {
                if (true || archiveCategory.equals(tc.getParentCategory()) || forceDelete) {
                    RateLimitUtil.queue(tc.delete());
                }
                else {
                    long cutoff = System.currentTimeMillis() - expireTime;
                    Trocutus.imp().getExecutor().submit(new Runnable() {
                        @Override
                        public void run() {
                            for (GuildMessageChannel toDelete : archiveCategory.getTextChannels()) {
                                try {
                                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(toDelete.getLatestMessageIdLong()).toEpochSecond() * 1000L;
                                    if (created < cutoff) {
                                        RateLimitUtil.queue(toDelete.delete());
                                    }
                                } catch (IllegalStateException ignore) {
                                    ignore.printStackTrace();
                                }
                            }
                        }
                    });
                    RateLimitUtil.queue(tc.getManager().setParent(archiveCategory));
                    for (PermissionOverride perm : tc.getMemberPermissionOverrides()) {
                        RateLimitUtil.queue(tc.upsertPermissionOverride(perm.getMember()).setAllowed(Permission.VIEW_CHANNEL).setDenied(Permission.MESSAGE_SEND));
                    }
                    return "This channel is archived and marked for deletion after 2 days. Do not reply here";
                }
            }
            RateLimitUtil.queue(((GuildMessageChannel)channel).delete());
            return null;
        } else {
            return "You do not have permission to close this channel";
        }
    }

    @Command(desc = "Clear the bot managed roles on discord")
    @RolePermission(Roles.ADMIN)
    public String clearAllianceRoles(@Me GuildDB db, @Me Guild guild,
                                     @Arg("What role types do you want to remove")
                                     ClearRolesEnum type) throws ExecutionException, InterruptedException {
        List<Future<?>> tasks = new ArrayList<>();
        try {
            switch (type) {
                case UNUSED: {
                    Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                    for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                        if (guild.getMembersWithRoles(entry.getValue()).isEmpty()) {
                            tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
                        }
                    }
                    return "Cleared unused Alliance roles!";
                }
                case ALLIANCE: {
                    Set<Integer> aaIds = db.getAllianceIds();

                    Role memberRole = Roles.MEMBER.toRole(guild);

                    StringBuilder response = new StringBuilder();
                    for (Member member : guild.getMembers()) {
                        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(member.getUser());
                        Set<Integer> userAAIds = kingdoms.values().stream().map(DBKingdom::getAlliance_id).collect(Collectors.toSet());
                        List<Role> roles = member.getRoles();
                        if (roles.contains(memberRole)) {
                            // if aaIds contains any from userAAIds
                            boolean containsAny = false;
                            for (Integer userAAId : userAAIds) {
                                if (aaIds.contains(userAAId)) {
                                    containsAny = true;
                                    break;
                                }
                            }
                            if (!containsAny) {
                                response.append("\nRemove member from " + member.getEffectiveName());
                                tasks.add(RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, memberRole)));
                            }
                        }
                    }
                    response.append("\nDone!");
                    return response.toString();
                }
                case UNREGISTERED: {
                    Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                    for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                        tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
                    }
                    return "Cleared all AA roles!";
                }
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }
        } finally {
            for (Future<?> task : tasks) {
                task.get();
            }
        }
    }

    private Map<Long, String> previous = new HashMap<>();
    private long previousNicksGuild = 0;

    public enum ClearRolesEnum {
        UNUSED,
        ALLIANCE,
        UNREGISTERED
    }

    @Command(desc = "Clear all nicknames on discord")
    @RolePermission(Roles.ADMIN)
    public synchronized String clearNicks(@Me Guild guild,
                                          @Arg("Undo the last recent use of this command")
                                          @Default boolean undo) throws ExecutionException, InterruptedException {
        if (previousNicksGuild != guild.getIdLong()) {
            previousNicksGuild = guild.getIdLong();
            previous.clear();
        }
        int failed = 0;
        String msg = null;
        List<Future<?>> tasks = new ArrayList<>();
        for (Member member : guild.getMembers()) {
            if (member.getNickname() != null) {
                try {
                    String nick;
                    if (!undo) {
                        previous.put(member.getIdLong(), member.getNickname());
                        nick = null;
                    } else {
                        nick = previous.get(member.getIdLong());
//                        if (args.get(0).equalsIgnoreCase("*")) {
//                            nick = previous.get(member.getIdLong());
//                        } else {
//                            previous.put(member.getIdLong(), member.getNickname());
//                            nick = DiscordUtil.trimContent(event.getMessage().getContentRaw());
//                            nick = nick.substring(nick.indexOf(' ') + 1);
//                        }
                    }
                    tasks.add(RateLimitUtil.queue(member.modifyNickname(nick)));
                } catch (Throwable e) {
                    msg = e.getMessage();
                    failed++;
                }
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        if (failed != 0) {
            return "Failed to clear " + failed + " nicknames for reason: " + msg;
        }
        return "Cleared all nicknames (that I have permission to clear)!";
    }

    @Command(desc = "Save or paste a stored message")
    @RolePermission(Roles.MEMBER)
    public String copyPasta(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild, @Me Member member, @Me User author, @Me Map<DBRealm, DBKingdom> me,
                            @Arg("What to name the saved message")
                            @Default String key,
                            @Default @TextArea String message,
                            @Arg("Require roles to paste the message")
                            @Default Set<Role> requiredRolesAny,
                            KingdomPlaceholders placeholders, ValueStore store) throws Exception {
        if (key == null) {

            Map<String, String> copyPastas = db.getCopyPastas(member);
            Set<String> options = copyPastas.keySet().stream().map(f -> f.split("\\.")[0]).collect(Collectors.toSet());

            if (options.size() <= 25) {
                // buttons
                IMessageBuilder msg = io.create().append("Options:");
                for (String option : options) {
                    msg.commandButton(CommandBehavior.DELETE_MESSAGE, CM.copyPasta.cmd.create(option, null, null), option);
                }
                msg.send();
                return null;
            }

            // link modals
            return "Options:\n- " + StringMan.join(options, "\n- ");

            // return options
        }
        if (requiredRolesAny != null && !requiredRolesAny.isEmpty()) {
            if (message == null) {
                throw new IllegalArgumentException("requiredRoles can only be used with a message");
            }
            key = requiredRolesAny.stream().map(Role::getId).collect(Collectors.joining(".")) + key;
        }

        if (message == null) {
            Set<String> noRoles = db.getMissingCopypastaPerms(key, member);
            if (!noRoles.isEmpty()) {
                throw new IllegalArgumentException("You do not have the required roles to use this command: `" + StringMan.join(noRoles, ",") + "`");
            }
        } else if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) {
            return "Missing role: " + Roles.INTERNAL_AFFAIRS;
        }

        String value = db.getCopyPasta(key, true);

        Set<String> missingRoles = null;
        if (value == null) {
            Map<String, String> map = db.getCopyPastas(member);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String otherKey = entry.getKey();
                String[] split = otherKey.split("\\.");
                if (!split[split.length - 1].equalsIgnoreCase(key)) continue;

                Set<String> localMissing = db.getMissingCopypastaPerms(otherKey, guild.getMember(author));

                if (!localMissing.isEmpty()) {
                    missingRoles = localMissing;
                    continue;
                }

                value = entry.getValue();
                missingRoles = null;
            }
        } else {
            missingRoles = db.getMissingCopypastaPerms(key, guild.getMember(author));
        }
        if (missingRoles != null && !missingRoles.isEmpty()) {
            throw new IllegalArgumentException("You do not have the required roles to use this command: `" + StringMan.join(missingRoles, ",") + "`");
        }
        if (value == null) return "No message set for `" + key + "`. Plase use " + CM.copyPasta.cmd.toSlashMention() + "";

        value = placeholders.format(store, value);

        return value;
    }

    @Command(desc = "list channels")
    @RolePermission(Roles.ADMIN)
    public String channelCount(@Me IMessageIO channel, @Me Guild guild) {
        StringBuilder channelList = new StringBuilder();

        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            channelList.append(category.getName() + "\n");

            for (GuildChannel catChannel : category.getChannels()) {
                String prefix = "+ ";
                if (catChannel instanceof VoiceChannel) {
                    prefix = "\uD83D\uDD0A ";
                }
                channelList.append(prefix + catChannel.getName() + "\n");
            }

            channelList.append("\n");
        }

        channel.create().file(guild.getChannels().size() + "/500 channels", channelList.toString()).send();
        return null;
    }

    @Command(desc = "Auto rank users on discord")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String autoroleall(@Me User author, @Me GuildDB db, @Me IMessageIO channel) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        StringBuilder response = new StringBuilder();

        Function<Long, Boolean> func = new Function<Long, Boolean>() {
            @Override
            public Boolean apply(Long i) {
                task.autoRoleAll(new Consumer<String>() {
                    private boolean messaged = false;
                    @Override
                    public void accept(String s) {
                        if (messaged) return;
                        messaged = true;
                        channel.send(s);
                    }
                });
                return true;
            }
        };
        channel.send("Please wait...");
        func.apply(0L);

        if (db.hasAlliance()) {
            for (Map.Entry<Member, UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
                User user = entry.getKey().getUser();
                response.append("`" + user.getName() + "#" + user.getDiscriminator() + "`" + "`<@" + user.getIdLong() + ">`");
                response.append("- ").append(entry.getValue());
                response.append("\n");
            }
        }

        if (db.getOrNull(GuildKey.AUTOROLE) == null) {
            response.append("\n- AutoRole disabled. To enable it use: " + GuildKey.AUTOROLE.getCommandMention() + "");
        }
        else response.append("\n- AutoRole Mode: ").append(db.getOrNull(GuildKey.AUTOROLE) + "");
        if (db.getOrNull(GuildKey.AUTONICK) == null) {
            response.append("\n- AutoNick disabled. To enable it use: " + GuildKey.AUTONICK.getCommandMention() + "");
        }
        else response.append("\n- AutoNick Mode: ").append(db.getOrNull(GuildKey.AUTONICK) + "");
        if (Roles.REGISTERED.toRole(db) == null) response.append("\n- Please set a registered role: " + CM.role.alias.set.cmd.create(Roles.REGISTERED.name(), null, null, null).toSlashCommand() + "");
        return response.toString();
    }

    @Command(desc = "Auto rank users on discord")
    public String autorole(@Me GuildDB db, @Me IMessageIO channel, Member member) {
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        Consumer<String> out = channel::send;
        task.autoRole(member, out);

        StringBuilder response = new StringBuilder("Done!");

        if (db.getOrNull(GuildKey.AUTOROLE) == null) {
            response.append("\n- AutoRole disabled. To enable it use: " + GuildKey.AUTOROLE.getCommandMention() + "");
        }
        else response.append("\n- AutoRole Mode: ").append((Object) db.getOrNull(GuildKey.AUTOROLE));
        if (db.getOrNull(GuildKey.AUTONICK) == null) {
            response.append("\n- AutoNick disabled. To enable it use: " + GuildKey.AUTONICK.getCommandMention() + "");
        }
        else response.append("\n- AutoNick Mode: ").append((Object) db.getOrNull(GuildKey.AUTONICK));
        if (Roles.REGISTERED.toRole(db) == null) response.append("\n- Please set a registered role: " + CM.role.alias.set.cmd.create(Roles.REGISTERED.name(), null, null, null).toSlashCommand() + "");
        return response.toString();
    }
}
