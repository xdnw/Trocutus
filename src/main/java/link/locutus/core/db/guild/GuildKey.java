package link.locutus.core.db.guild;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.Auth;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.guild.entities.AutoNickOption;
import link.locutus.core.db.guild.entities.AutoRoleOption;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildBooleanSetting;
import link.locutus.core.db.guild.key.GuildChannelSetting;
import link.locutus.core.db.guild.key.GuildEnumSetSetting;
import link.locutus.core.db.guild.key.GuildEnumSetting;
import link.locutus.core.db.guild.key.GuildIntegerSetting;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.db.guild.key.GuildSettingCategory;
import link.locutus.util.FileUtil;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GuildKey {
    public static final GuildSetting<Set<Integer>> ALLIANCE_ID = new GuildSetting<Set<Integer>>(GuildSettingCategory.DEFAULT, Set.class, Integer.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String registerAlliance(@Me GuildDB db, @Me User user, Set<DBAlliance> alliances) {
            Set<Integer> existing = ALLIANCE_ID.getOrNull(db, false);
            existing = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
            Set<Integer> toAdd = alliances.stream().map(DBAlliance::getId).collect(Collectors.toSet());
            for (DBAlliance alliance : alliances) {
                if (existing.contains(alliance.getId())) {
                    throw new IllegalArgumentException("Alliance " + alliance.getName() + " is already registered (registered: " + StringMan.join(existing, ",") + ")");
                }
            }
            toAdd = ALLIANCE_ID.allowedAndValidate(db, user, toAdd);
            existing.addAll(toAdd);
            return ALLIANCE_ID.set(db, toAdd);
        }
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String unregisterAlliance(@Me GuildDB db, @Me User user, Set<DBAlliance> alliances) {
            Set<Integer> existing = ALLIANCE_ID.getOrNull(db, false);
            existing = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
            for (DBAlliance alliance : alliances) {
                if (!existing.contains(alliance.getId())) {
                    throw new IllegalArgumentException("Alliance " + alliance.getName() + " is not registered (registered: " + StringMan.join(existing, ",") + ")");
                }
                existing.remove(alliance.getId());
            }
            if (existing.isEmpty()) {
                return ALLIANCE_ID.delete(db, user);
            }
            return ALLIANCE_ID.set(db, existing);
        }

        @Override
        public Set<Integer> validate(GuildDB db, Set<Integer> aaIds) {
            if (DELEGATE_SERVER.has(db, false))
                throw new IllegalArgumentException("Cannot set alliance id of delegate server (please unset DELEGATE_SERVER first)");

            if (aaIds.isEmpty()) {
                throw new IllegalArgumentException("No alliance provided");
            }

            for (Integer aaId : aaIds) {
                if (aaId == 0) {
                    throw new IllegalArgumentException("None alliance (id=0) cannot be registered: " + aaIds);
                }
                DBAlliance alliance = DBAlliance.get(aaId);
                if (alliance == null) {
                    throw new IllegalArgumentException("Alliance " + aaId + " does not exist");
                }
                GuildDB otherDb = alliance.getGuildDB();
                Member owner = db.getGuild().getOwner();
                DBKingdom ownerNation = DBKingdom.getFromUser(owner.getUser(), alliance.getRealm());
                if (ownerNation == null || ownerNation.getAlliance_id() != aaId || ownerNation.getPosition().ordinal() < Rank.FOUNDER.ordinal()) {
                    Set<String> inviteCodes = new HashSet<>();
                    boolean isValid = Roles.ADMIN.hasOnRoot(owner.getUser());
                    try {
                        try {
                            List<Invite> invites = RateLimitUtil.complete(db.getGuild().retrieveInvites());
                            for (Invite invite : invites) {
                                String inviteCode = invite.getCode();
                                inviteCodes.add(inviteCode);
                            }
                        } catch (InsufficientPermissionException ignore) {
                        }

                        if (!inviteCodes.isEmpty() && alliance.getDiscord_link() != null && !alliance.getDiscord_link().isEmpty()) {
                            for (String code : inviteCodes) {
                                if (alliance.getDiscord_link().contains(code)) {
                                    isValid = true;
                                    break;
                                }
                            }
                        }

                        if (!isValid) {
                            Auth auth = Trocutus.imp().rootAuth();
                            DBKingdom kingdom = auth.getKingdom(alliance.getRealm_id());
                            String url = alliance.getUrl(kingdom.getName());

                            String content = auth.readStringFromURL(url, Collections.emptyMap(), false);

                            String idStr = db.getGuild().getId();

                            if (!content.contains(idStr)) {
                                for (String inviteCode : inviteCodes) {
                                    if (content.contains(inviteCode)) {
                                        isValid = true;
                                        break;
                                    }
                                }
                            } else {
                                isValid = true;
                            }
                        }

                        if (!isValid) {
                            String msg = "1. Go to: <" + alliance.getUrl("__your_name__") + ">\n" +
                                    "2. Scroll down to where it says Alliance Description:\n" +
                                    "3. Put your guild id `" + db.getIdLong() + "` somewhere in the text\n" +
                                    "4. Click save\n" +
                                    "5. Run the command " + getCommandObj(aaIds) + " again\n" +
                                    "(note: you can remove the id after setup)";
                            throw new IllegalArgumentException(msg);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (otherDb != null && otherDb != db) {
                    otherDb.deleteInfo(ALLIANCE_ID);

                    String msg = "Only 1 root server per Alliance is permitted. The ALLIANCE in the other guild: " + otherDb.getGuild() + " has been removed.\n" +
                            "To have multiple servers, set the ALLIANCE on your primary server, and then set " + DELEGATE_SERVER.getCommandMention() + " on your other servers\n" +
                            "The `<guild-id>` for this server is `" + db.getIdLong() + "` and the id for the other server is `" + otherDb.getIdLong() + "`.\n\n" +
                            "Run this command again to confirm and set the ALLIANCE";
                    throw new IllegalArgumentException(msg);
                }
            }
            return aaIds;
        }

        @Override
        public String help() {
            return "Your alliance name";
        }

        @Override
        public String toString(Set<Integer> value) {
            return StringMan.join(value, ",");
        }

        @Override
        public String toReadableString(Set<Integer> value) {
            Set<String> names = new LinkedHashSet<>();
            for (Integer id : value) {
                DBAlliance aa = DBAlliance.get(id);
                if (aa != null) {
                    names.add(aa.getName());
                } else {
                    names.add("null/" + id);
                }
            }
            return StringMan.join(names, ",");
        }
    };

    public static GuildSetting<MessageChannel> DEFENSE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DEFENSE_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return DEFENSE_WAR_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts for defensive wars";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());

    public static GuildSetting<MessageChannel> OFFENSIVE_SPY_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String OFFENSIVE_SPY_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return OFFENSIVE_SPY_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts for offensive spy ops";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());

    public static GuildSetting<MessageChannel> DEFENSIVE_SPY_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String OFFENSIVE_SPY_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return DEFENSIVE_SPY_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts for offensive spy ops";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());

//    public static GuildSetting<Boolean> SHOW_ALLY_DEFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
//        @Command(descMethod = "help")
//        @RolePermission(Roles.ADMIN)
//        public String SHOW_ALLY_DEFENSIVE_WARS(@Me GuildDB db, @Me User user, boolean enabled) {
//            return SHOW_ALLY_DEFENSIVE_WARS.setAndValidate(db, user, enabled);
//        }
//        @Override
//        public String help() {
//            return "Whether to show offensive war alerts for allies (true/false)";
//        }
//    }.setupRequirements(f -> f.requires(DEFENSE_WAR_CHANNEL).requiresCoalition(Coalition.ALLIES));

    public static GuildSetting<MessageChannel> OFFENSIVE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String OFFENSIVE_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return OFFENSIVE_WAR_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for offensive wars";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());
    public static GuildSetting<Boolean> SHOW_ALLY_OFFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String SHOW_ALLY_OFFENSIVE_WARS(@Me GuildDB db, @Me User user, boolean enabled) {
            return SHOW_ALLY_OFFENSIVE_WARS.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to show offensive war alerts for allies (true/false)";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(OFFENSIVE_WAR_CHANNEL).requiresCoalition(Coalition.ALLIES));
    public static GuildSetting<Boolean> HIDE_APPLICANT_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String HIDE_APPLICANT_WARS(@Me GuildDB db, @Me User user, boolean value) {
            return HIDE_APPLICANT_WARS.setAndValidate(db, user, value);
        }
        @Override
        public String help() {
            return "Whether to hide war alerts for applicants";
        }
    }.setupRequirements(f -> f.requires(OFFENSIVE_WAR_CHANNEL));

    public static GuildSetting<AutoNickOption> AUTONICK = new GuildEnumSetting<AutoNickOption>(GuildSettingCategory.ROLE, AutoNickOption.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTONICK(@Me GuildDB db, @Me User user, AutoNickOption mode) {
            return AUTONICK.setAndValidate(db, user, mode);
        }
        @Override
        public String help() {
            return "Options: " + StringMan.getString(AutoNickOption.values()) + "\n" + "";
//                    "See also: " + CM.role.clearNicks.cmd.toSlashMention();
        }
    };
    public static GuildSetting<AutoRoleOption> AUTOROLE = new GuildEnumSetting<AutoRoleOption>(GuildSettingCategory.ROLE, AutoRoleOption.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE(@Me GuildDB db, @Me User user, AutoRoleOption mode) {
            return AUTOROLE.setAndValidate(db, user, mode);
        }

        @Override
        public String help() {
            return "Options: " + StringMan.getString(AutoRoleOption.values()) + "\n" +
                    "See also:\n" +
//                    "- " + CM.coalition.create.cmd.create(null, Coalition.MASKED_ALLIANCES.name()) + "\n" +
//                    "- " + CM.role.clearAllianceRoles.cmd.toSlashMention() + "\n" +
                    "- " + AUTOROLE_ALLIANCE_RANK.getCommandMention() + "\n" +
                    "- " + AUTOROLE_TOP_X.getCommandMention();
        }
    };

    public static GuildSetting<Map.Entry<Integer, Long>> DELEGATE_SERVER = new GuildSetting<Map.Entry<Integer, Long>>(GuildSettingCategory.DEFAULT, Map.class, Integer.class, Long.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DELEGATE_SERVER(@Me GuildDB db, @Me User user, Guild guild) {
            return DELEGATE_SERVER.setAndValidate(db, user, Map.entry(0, guild.getIdLong()));
        }

        @Override
        public Map.Entry<Integer, Long> validate(GuildDB db, Map.Entry<Integer, Long> ids) {
            if (db.getOrNull(ALLIANCE_ID) != null) {
                throw new IllegalArgumentException("You cannot delegate a server with an alliance id set");
            }
            Guild guild = Trocutus.imp().getDiscordApi().getGuildById(ids.getValue());
            if (guild == null)
                throw new IllegalArgumentException("Invalid guild: `" + ids.getValue() + "` (are you sure this bot is in that server?)");
            GuildDB otherDb = GuildDB.get(guild);
            if (guild.getIdLong() == db.getIdLong())
                throw new IllegalArgumentException("You cannot set the delegate as this guild");
            if (DELEGATE_SERVER.has(otherDb, false)) {
                throw new IllegalArgumentException("Circular reference. The server you have set already delegates its DELEGATE_SERVER");
            }
            return ids;
        }

        @Override
        public boolean hasPermission(GuildDB db, User author, Map.Entry<Integer, Long> entry) {
            if (!super.hasPermission(db, author, entry)) return false;
            if (entry == null) return true;
            GuildDB otherDB = GuildDB.get(entry.getValue());
            if (otherDB == null) {
                throw new IllegalArgumentException("Invalid guild: `" + entry.getValue() + "` (are you sure this bot is in that server?)");
            }
            if (!Roles.ADMIN.has(author, otherDB.getGuild()))
                throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
            return true;
        }

        @Override
        public Map.Entry<Integer, Long> parse(GuildDB db, String input) {
            String[] split2 = input.trim().split("[:|=]", 2);
            Map.Entry<Integer, Long> entry;
            if (split2.length == 2) {
                return Map.entry(Integer.parseInt(split2[0]), Long.parseLong(split2[1]));
            } else {
                return Map.entry(0, Long.parseLong(input));
            }
        }

        @Override
        public String toString(Map.Entry<Integer, Long> value) {
            Map.Entry<Integer, Long> pair = value;
            if (pair.getKey() == 0) return String.valueOf(pair.getValue());
            return pair.getKey() + ":" + pair.getValue();
        }

        @Override
        public String help() {
            return "The guild to delegate unset settings to";
        }
    };
    public static GuildSetting<GuildDB> FA_SERVER = new GuildSetting<GuildDB>(GuildSettingCategory.FOREIGN_AFFAIRS, GuildDB.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String FA_SERVER(@Me GuildDB db, @Me User user, Guild guild) {
            GuildDB otherDb = GuildDB.get(guild);
            return FA_SERVER.setAndValidate(db, user, otherDb);
        }

        @Override
        public GuildDB validate(GuildDB db, GuildDB otherDb) {
            if (otherDb.getIdLong() == db.getGuild().getIdLong())
                throw new IllegalArgumentException("Use " + "CM.settings.delete.cmd.create(FA_SERVER.name())" + " to unset the FA_SERVER");
            if (FA_SERVER.has(otherDb, false))
                throw new IllegalArgumentException("Circular reference. The server you have set already defers its FA_SERVER");
            return otherDb;
        }

        @Override
        public boolean hasPermission(GuildDB db, User author, GuildDB otherDB) {
            if (!super.hasPermission(db, author, otherDB)) return false;
            if (otherDB != null && !Roles.ADMIN.has(author, otherDB.getGuild()))
                throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
            return true;
        }

        @Override
        public String toString(GuildDB value) {
            return value.getIdLong() + "";
        }

        @Override
        public String toReadableString(GuildDB value) {
            return value.getName();
        }

        @Override
        public String help() {
            return "The guild to defer coalitions to";
        }
    };

    public static GuildSetting<Rank> AUTOROLE_ALLIANCE_RANK = new GuildEnumSetting<Rank>(GuildSettingCategory.ROLE, Rank.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLIANCE_RANK(@Me GuildDB db, @Me User user, Rank allianceRank) {
            return AUTOROLE_ALLIANCE_RANK.setAndValidate(db, user, allianceRank);
        }
        @Override
        public String help() {
            return "The ingame rank required to get an alliance role. (default: member) Options: " + StringMan.getString(Rank.values());
        }
    }.setupRequirements(f -> f.requires(AUTOROLE));
    public static GuildSetting<Integer> AUTOROLE_TOP_X = new GuildIntegerSetting(GuildSettingCategory.ROLE) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_TOP_X(@Me GuildDB db, @Me User user, Integer topScoreRank) {
            return AUTOROLE_TOP_X.setAndValidate(db, user, topScoreRank);
        }
        @Override
        public String help() {
            return "The number of top alliances to provide roles for, defaults to `0`";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE));
    public static GuildSetting<Integer> DO_NOT_RAID_TOP_X = new GuildIntegerSetting(GuildSettingCategory.FOREIGN_AFFAIRS) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DO_NOT_RAID_TOP_X(@Me GuildDB db, @Me User user, Integer topAllianceScore) {
            return DO_NOT_RAID_TOP_X.setAndValidate(db, user, topAllianceScore);
        }
        @Override
        public String help() {
            return "The number of top alliances to include in the Do Not Raid (DNR) list\n" +
                    "Members are not permitted to declare on members of these alliances or their direct allies\n" +
                    "Results in the DNR will be excluded from commands, and will alert Foreign Affairs if violated\n" +
                    "Defaults to `0`";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<Boolean> AUTOROLE_ALLY_GOV = new GuildBooleanSetting(GuildSettingCategory.ROLE) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLY_GOV(@Me GuildDB db, @Me User user, boolean enabled) {
            return AUTOROLE_ALLY_GOV.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to give gov/member roles to allies (this is intended for coalition servers), `true` or `false`";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE).requiresCoalition(Coalition.ALLIES).requiresNot(ALLIANCE_ID));
    public static GuildSetting<Set<Roles>> AUTOROLE_ALLY_ROLES = new GuildEnumSetSetting<Roles>(GuildSettingCategory.ROLE, Roles.class) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLY_ROLES(@Me GuildDB db, @Me User user, Set<Roles> roles) {
            return AUTOROLE_ALLY_ROLES.setAndValidate(db, user, roles);
        }
        @Override
        public String toString(Set<Roles> value) {
            return StringMan.join(value.stream().map(f -> f.name()).collect(Collectors.toList()), ",");
        }

        @Override
        public String help() {
            return "List of roles to autorole from ally servers\n" +
                    "(this is intended for coalition servers to give gov roles to allies)";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE_ALLY_GOV).requiresCoalition(Coalition.ALLIES).requiresNot(ALLIANCE_ID));

    public static GuildSetting<MessageChannel> MEMBER_LEAVE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.AUDIT) {
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_LEAVE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return MEMBER_LEAVE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a member leaves";
        }
    }.setupRequirements(f -> f.requireValidAlliance());

    private static final Map<String, GuildSetting> BY_NAME = new HashMap<>();

    static {
        // add by field names
        for (Field field : GuildKey.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!GuildSetting.class.isAssignableFrom(field.getType())) continue;
            try {
                GuildSetting setting = (GuildSetting) field.get(null);
                BY_NAME.put(field.getName(), setting);
                setting.setName(field.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static GuildSetting[] values() {
        return BY_NAME.values().toArray(new GuildSetting[0]);
    }

    public static GuildSetting valueOf(String name) {
        GuildSetting result = BY_NAME.get(name);
        if (result == null) {
            throw new IllegalArgumentException("No such setting: " + name + ". Options:\n- " + StringMan.join(BY_NAME.keySet(), "\n- "));
        }
        return result;
    }
}
