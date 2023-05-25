package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.SheetKeys;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.db.guild.key.GuildSettingCategory;
import link.locutus.util.MarkupUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingCommands {
    private Map<SheetKeys, String> getSheets(GuildDB db) {
        Map<SheetKeys, String> map = new LinkedHashMap<>();
        for (SheetKeys key : SheetKeys.values()) {
            String value = db.getInfo(key, false);
            if (value != null) {
                String baseUrl = "https://tinyurl.com/nnfajjp/";
                String fullUrl = baseUrl + value;
                String formatted = MarkupUtil.markdownUrl(key.name(), fullUrl);
                map.put(key, formatted);
            }
        }
        return map;
    }

    private static Map<GuildSettingCategory, Map<GuildSetting, Object>> getKeys(GuildDB db, boolean listAll) {
        Map<GuildSettingCategory, Map<GuildSetting, Object>> map = new LinkedHashMap<>();
        for (GuildSetting key : GuildKey.values()) {
            if (!key.allowed(db) && !listAll) continue;
            map.computeIfAbsent(key.getCategory(), f -> new LinkedHashMap<>()).put(key, db.getOrNull(key, false));
        }
        return map;
    }

    @Command(desc = "Configure alliance or guild settings")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String delete(@Me GuildDB db, @Me User author, @Default GuildSetting key) {
        if (!key.hasPermission(db, author, null)) {
            return "You do not have permission to delete the key `" + key.name() + "`";
        }
        String valueRaw = key.getRaw(db, false);
        if (valueRaw == null) {
            return "The key `" + key.name() + "` has not been set on this guild";
        }
        StringBuilder response = new StringBuilder();
        Object value = key.getOrNull(db, false);
        if (value != null) {
            response.append("Previous value:\n```\n" + key.toReadableString(value) + "\n```\n");
        } else {
            response.append("Previous value (invalid):\n```\n" + valueRaw + "\n```\n");
        }
        response.append(key.delete(db, author));
        return response.toString();
    }

    @Command(desc = "Configure any alliance or guild settings")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public static String info(@Me IMessageIO io, @Me Guild guild, @Me User author,
                              @Arg("The setting to change or view")
                              @Default GuildSetting key,
                              @Arg("The value to set the setting to")
                              @Default @TextArea String value,
                              @Switch("a") boolean listAll) throws Exception {
        GuildDB db = Trocutus.imp().getGuildDB(guild);
        if (db == null) return "Command must run in a guild.";
        if (value == null) {
            if (key != null) {
                StringBuilder response = new StringBuilder();
                response.append("# **__").append(key.name()).append("__**\n");
                response.append("> " + key.help().replaceAll("\n", "\n> ") + "\n\n");
                response.append("**category**: `" + key.getCategory() + "`\n");
                response.append("**type**: `" + key.getType().toSimpleString() + "`\n");
                response.append("**commands**: ").append(key.getCommandMention()).append("\n");

                try {
                    key.allowed(db, true);
                } catch (IllegalArgumentException e) {
                    String[] msg = e.getMessage().split("\n");
                    response.append("**missing requirements**:\n- " + StringMan.join(msg, "\n- ") + "\n");
                }
                response.append("\n");

                String valueStr = key.getRaw(db, false);

                if (valueStr != null) {
                    Object valueObj = key.getOrNull(db, false);
                    if (valueObj == null) {
                        response.append("**current value**: `" + valueStr + "`\n\n");
                        response.append("`A value is set but it is invalid`\n");
                    } else {
                        response.append("**current value**: `" + key.toReadableString(valueObj) + "`\n\n");
                    }
                    response.append("`note: to delete, use: " + "CM.settings.delete.cmd.create(key.name()).toSlashCommand(false)" + "`\n");
                } else {
                    response.append("`no value is set`\n");
                }
                return response.toString();
            } else {
                StringBuilder response = new StringBuilder();
                Map<GuildSettingCategory, Map<GuildSetting, Object>> keys = getKeys(db, listAll);
                for (Map.Entry<GuildSettingCategory, Map<GuildSetting, Object>> entry : keys.entrySet()) {
                    GuildSettingCategory category = entry.getKey();

                    response.append("# **__").append(category.name()).append(":__**\n");

                    Map<GuildSetting, Object> catKeys = entry.getValue();
                    for (Map.Entry<GuildSetting, Object> keyObjectEntry : catKeys.entrySet()) {
                        GuildSetting currKey = keyObjectEntry.getKey();
                        if (!currKey.hasPermission(db, author, null)) continue;

                        String hide = "";
                        if (!currKey.allowed(db, false)) {
                            hide = "~~";
                        }
                        response.append("- ").append(hide + "`" + currKey.name() + "`" + hide);
//                        response.append(" (" + currKey.getCommandMention() + ")");

                        Object setValue = keyObjectEntry.getValue();
                        if (setValue != null) {
                            String setValueStr = currKey.toReadableString(setValue);
                            if (setValueStr.length() > 25) {
                                setValueStr = setValueStr.substring(0, 24) + "\u2026";
                            }
                            response.append("=").append(setValueStr);
                        }
                        response.append("\n");
                    }
                }

                response.append("\n");
                if (!listAll) {
//                    response.append("To list all setting: " + CM.settings.info.cmd.create(null, null, "true") + "\n");
                }
//                response.append("For info/usage: " + CM.settings.info.cmd.create("YOUR_KEY_HERE", null, null).toSlashCommand(false) + "\n");
//                response.append("To delete: " + CM.settings.delete.cmd.toSlashMention() + "\n");
//                response.append("Find a setting: " + CM.help.find_setting.cmd.toSlashMention());

                return response.toString();
            }
        }
        if (!key.hasPermission(db, author, null)) return "No permission for modify that key.";
        if (!key.allowed(db, true)) return "This guild does not have permission to set this key.";

        Object valueObj;
        if (value.equalsIgnoreCase("null")) {
            valueObj = null;
        } else {
            valueObj = key.parse(db, value);
            valueObj = key.validate(db, valueObj);

            if (valueObj == null) {
                return "Invalid value for key `" + key.name() + "`";
            }
            if (!key.hasPermission(db, author, valueObj)) return "No permission to set `" + key.name() + "` to `" + key.toReadableString(valueObj) + "`";
        }
        if (valueObj == null) {
            if (!key.has(db, false)) {
                return "Key `" + key.name() + "` is already unset.";
            }
            return key.delete(db, author);
        } else {
            return key.set(db, valueObj);
        }
    }

    @Command(desc = "Configure alliance or guild settings")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String sheets(@Me GuildDB db, @Me IMessageIO io, @Me Guild guild, @Me User author) throws Exception {
        Map<SheetKeys, String> sheets = getSheets(db);
        if (sheets.isEmpty()) {
            return "No sheets are configured (sheets are created when you use a sheet command)";
        }
        StringBuilder response = new StringBuilder();
        response.append("**Sheets**:\n");
        for (Map.Entry<SheetKeys, String> entry : sheets.entrySet()) {
            response.append(entry.getKey() + ": <" + entry.getValue()).append(">\n");
        }
        return response.toString();
    }
}
