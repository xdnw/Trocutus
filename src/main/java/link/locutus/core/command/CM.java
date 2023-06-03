package link.locutus.core.command;

import link.locutus.command.command.AutoRegister;
import link.locutus.command.command.CommandRef;

public class CM {
    public static class settings_interview{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ARCHIVE_CATEGORY", field="ARCHIVE_CATEGORY")
        public static class ARCHIVE_CATEGORY extends CommandRef {
            public static final ARCHIVE_CATEGORY cmd = new ARCHIVE_CATEGORY();
            public ARCHIVE_CATEGORY create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="INTERVIEW_PENDING_ALERTS", field="INTERVIEW_PENDING_ALERTS")
        public static class INTERVIEW_PENDING_ALERTS extends CommandRef {
            public static final INTERVIEW_PENDING_ALERTS cmd = new INTERVIEW_PENDING_ALERTS();
            public INTERVIEW_PENDING_ALERTS create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="INTERVIEW_INFO_SPAM", field="INTERVIEW_INFO_SPAM")
        public static class INTERVIEW_INFO_SPAM extends CommandRef {
            public static final INTERVIEW_INFO_SPAM cmd = new INTERVIEW_INFO_SPAM();
            public INTERVIEW_INFO_SPAM create(String channel) {
                return createArgs("channel", channel);
            }
        }
    }
    public static class alliance{
        @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="alliance")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String allianceInfo) {
                return createArgs("allianceInfo", allianceInfo);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="treaties")
        public static class treaties extends CommandRef {
            public static final treaties cmd = new treaties();
            public treaties create(String alliances) {
                return createArgs("alliances", alliances);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.KingdomCommands.class,method="leftAA")
        public static class departures extends CommandRef {
            public static final departures cmd = new departures();
            public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds) {
                return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds);
            }
        }
    }
    public static class help{
        @AutoRegister(clazz=link.locutus.core.command.HelpCommands.class,method="command")
        public static class command extends CommandRef {
            public static final command cmd = new command();
            public command create(String command) {
                return createArgs("command", command);
            }
        }
    }
    public static class alert{
        public static class opt_out{
            @AutoRegister(clazz=link.locutus.core.command.PlayerSettingCommands.class,method="enemyAlertOptOut")
            public static class enemy_alert extends CommandRef {
                public static final enemy_alert cmd = new enemy_alert();
                public enemy_alert create() {
                    return createArgs();
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.PlayerSettingCommands.class,method="loginNotifier")
        public static class login extends CommandRef {
            public static final login cmd = new login();
            public login create(String target, String doNotRequireWar) {
                return createArgs("target", target, "doNotRequireWar", doNotRequireWar);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="register")
    public static class register extends CommandRef {
        public static final register cmd = new register();
        public register create(String kingdom, String key) {
            return createArgs("kingdom", kingdom, "key", key);
        }
    }
    public static class embed{
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="card")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String title, String body, String commands) {
                return createArgs("title", title, "body", body, "commands", commands);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="updateEmbed")
        public static class update extends CommandRef {
            public static final update cmd = new update();
            public update create(String requiredRole, String color, String title, String desc) {
                return createArgs("requiredRole", requiredRole, "color", color, "title", title, "desc", desc);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="embedInfo")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String message) {
                return createArgs("message", message);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="msgInfo")
        public static class reactions extends CommandRef {
            public static final reactions cmd = new reactions();
            public reactions create(String message, String useIds) {
                return createArgs("message", message, "useIds", useIds);
            }
        }
    }
    public static class sheet{
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="IntelOpSheet")
        public static class intel extends CommandRef {
            public static final intel cmd = new intel();
            public intel create(String time, String attackers, String dnrTopX, String ignoreWithLootHistory, String ignoreDNR, String sheet) {
                return createArgs("time", time, "attackers", attackers, "dnrTopX", dnrTopX, "ignoreWithLootHistory", ignoreWithLootHistory, "ignoreDNR", ignoreDNR, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="allianceKingdomsSheet")
        public static class allianceKingdoms extends CommandRef {
            public static final allianceKingdoms cmd = new allianceKingdoms();
            public allianceKingdoms create(String nations, String columns, String sheet, String useTotal, String includeInactives, String includeApplicants) {
                return createArgs("nations", nations, "columns", columns, "sheet", sheet, "useTotal", useTotal, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="ActivitySheet")
        public static class activity extends CommandRef {
            public static final activity cmd = new activity();
            public activity create(String nations, String trackTime, String sheet) {
                return createArgs("nations", nations, "trackTime", trackTime, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="mailTargets")
        public static class mail_targets extends CommandRef {
            public static final mail_targets cmd = new mail_targets();
            public mail_targets create(String spySheet, String allowedKingdoms, String header, String apiKey, String hideDefaultBlurb, String force, String dm) {
                return createArgs("spySheet", spySheet, "allowedKingdoms", allowedKingdoms, "header", header, "apiKey", apiKey, "hideDefaultBlurb", hideDefaultBlurb, "force", force, "dm", dm);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="KingdomSheet")
        public static class kingdoms extends CommandRef {
            public static final kingdoms cmd = new kingdoms();
            public kingdoms create(String nations, String columns, String sheet) {
                return createArgs("nations", nations, "columns", columns, "sheet", sheet);
            }
        }
        public static class blitz{
            @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="validateSpyBlitzSheet")
            public static class validate extends CommandRef {
                public static final validate cmd = new validate();
                public validate create(String sheet, String allowUpdeclares, String allowWarAlerted, String allowSpellAlerted, String maxAttacks, String maxSpellCasts, String filter) {
                    return createArgs("sheet", sheet, "allowUpdeclares", allowUpdeclares, "allowWarAlerted", allowWarAlerted, "allowSpellAlerted", allowSpellAlerted, "maxAttacks", maxAttacks, "maxSpellCasts", maxSpellCasts, "filter", filter);
                }
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="score")
    public static class score extends CommandRef {
        public static final score cmd = new score();
        public score create(String score) {
            return createArgs("score", score);
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="raid")
    public static class raid extends CommandRef {
        public static final raid cmd = new raid();
        public raid create(String realm, String myKingdom, String numResults, String sortGold, String sortLand, String ignoreLosses, String ignoreDoNotRaid, String excludeWarUnitEstimate, String includeStronger) {
            return createArgs("realm", realm, "myKingdom", myKingdom, "numResults", numResults, "sortGold", sortGold, "sortLand", sortLand, "ignoreLosses", ignoreLosses, "ignoreDoNotRaid", ignoreDoNotRaid, "excludeWarUnitEstimate", excludeWarUnitEstimate, "includeStronger", includeStronger);
        }
    }
    public static class credentials{
        @AutoRegister(clazz=link.locutus.core.command.CredentialCommands.class,method="login")
        public static class login extends CommandRef {
            public static final login cmd = new login();
            public login create(String username, String password) {
                return createArgs("username", username, "password", password);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="unregister")
        public static class unregister extends CommandRef {
            public static final unregister cmd = new unregister();
            public unregister create(String nation, String force) {
                return createArgs("nation", nation, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.CredentialCommands.class,method="logout")
        public static class logout extends CommandRef {
            public static final logout cmd = new logout();
            public logout create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.CredentialCommands.class,method="addApiKey")
        public static class addApiKey extends CommandRef {
            public static final addApiKey cmd = new addApiKey();
            public addApiKey create(String apiKey) {
                return createArgs("apiKey", apiKey);
            }
        }
    }
    public static class settings{
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="info")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String key, String value, String listAll) {
                return createArgs("key", key, "value", value, "listAll", listAll);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="delete")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String key) {
                return createArgs("key", key);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="sheets")
        public static class sheets extends CommandRef {
            public static final sheets cmd = new sheets();
            public sheets create() {
                return createArgs();
            }
        }
    }
    public static class setting{
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="sheets")
        public static class sheets extends CommandRef {
            public static final sheets cmd = new sheets();
            public sheets create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="info")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String key, String value, String listAll) {
                return createArgs("key", key, "value", value, "listAll", listAll);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SettingCommands.class,method="delete")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String key) {
                return createArgs("key", key);
            }
        }
    }
    public static class admin{
        @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="stop")
        public static class stop extends CommandRef {
            public static final stop cmd = new stop();
            public stop create(String save) {
                return createArgs("save", save);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="listExpiredGuilds")
        public static class listExpiredGuilds extends CommandRef {
            public static final listExpiredGuilds cmd = new listExpiredGuilds();
            public listExpiredGuilds create(String checkMessages) {
                return createArgs("checkMessages", checkMessages);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="importEmojis")
        public static class importEmojis extends CommandRef {
            public static final importEmojis cmd = new importEmojis();
            public importEmojis create(String guild) {
                return createArgs("guild", guild);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="deleteAllInaccessibleChannels")
        public static class unsetUnreachableChannels extends CommandRef {
            public static final unsetUnreachableChannels cmd = new unsetUnreachableChannels();
            public unsetUnreachableChannels create(String force) {
                return createArgs("force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="listGuildOwners")
        public static class listGuildOwners extends CommandRef {
            public static final listGuildOwners cmd = new listGuildOwners();
            public listGuildOwners create() {
                return createArgs();
            }
        }
        public static class sync{
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncAllKingdoms")
            public static class kingdoms extends CommandRef {
                public static final kingdoms cmd = new kingdoms();
                public kingdoms create(String fetchNew, String fetchChanged, String fetchDeleted, String fetchMissingSlow) {
                    return createArgs("fetchNew", fetchNew, "fetchChanged", fetchChanged, "fetchDeleted", fetchDeleted, "fetchMissingSlow", fetchMissingSlow);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncMetrics")
            public static class metrics extends CommandRef {
                public static final metrics cmd = new metrics();
                public metrics create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncAllianceKingdoms")
            public static class kingdoms_in_aa extends CommandRef {
                public static final kingdoms_in_aa cmd = new kingdoms_in_aa();
                public kingdoms_in_aa create(String updateMissing) {
                    return createArgs("updateMissing", updateMissing);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncGlobalInteractions")
            public static class all_interactions extends CommandRef {
                public static final all_interactions cmd = new all_interactions();
                public all_interactions create(String realm) {
                    return createArgs("realm", realm);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncFey")
            public static class fey extends CommandRef {
                public static final fey cmd = new fey();
                public fey create(String realm, String landStart, String landEnd, String multiplier) {
                    return createArgs("realm", realm, "landStart", landStart, "landEnd", landEnd, "multiplier", multiplier);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncAllianceKingdoms")
            public static class alliance_kingdoms extends CommandRef {
                public static final alliance_kingdoms cmd = new alliance_kingdoms();
                public alliance_kingdoms create(String updateMissing) {
                    return createArgs("updateMissing", updateMissing);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="sync")
            public static class kingdom extends CommandRef {
                public static final kingdom cmd = new kingdom();
                public kingdom create(String realm, String name) {
                    return createArgs("realm", realm, "name", name);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncInteractions")
            public static class interactions extends CommandRef {
                public static final interactions cmd = new interactions();
                public interactions create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncAlliances")
            public static class alliances extends CommandRef {
                public static final alliances cmd = new alliances();
                public alliances create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="syncSupport")
            public static class support extends CommandRef {
                public static final support cmd = new support();
                public support create(String realm, String unit) {
                    return createArgs("realm", realm, "unit", unit);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="leaveServer")
        public static class leaveServer extends CommandRef {
            public static final leaveServer cmd = new leaveServer();
            public leaveServer create(String guildId) {
                return createArgs("guildId", guildId);
            }
        }
    }
    public static class settings_war_alerts{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="OFFENSIVE_WAR_CHANNEL", field="OFFENSIVE_WAR_CHANNEL")
        public static class OFFENSIVE_WAR_CHANNEL extends CommandRef {
            public static final OFFENSIVE_WAR_CHANNEL cmd = new OFFENSIVE_WAR_CHANNEL();
            public OFFENSIVE_WAR_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="OFFENSIVE_SPY_CHANNEL", field="OFFENSIVE_SPY_CHANNEL")
        public static class OFFENSIVE_SPY_CHANNEL extends CommandRef {
            public static final OFFENSIVE_SPY_CHANNEL cmd = new OFFENSIVE_SPY_CHANNEL();
            public OFFENSIVE_SPY_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="SHOW_ALLY_OFFENSIVE_WARS", field="SHOW_ALLY_OFFENSIVE_WARS")
        public static class SHOW_ALLY_OFFENSIVE_WARS extends CommandRef {
            public static final SHOW_ALLY_OFFENSIVE_WARS cmd = new SHOW_ALLY_OFFENSIVE_WARS();
            public SHOW_ALLY_OFFENSIVE_WARS create(String enabled) {
                return createArgs("enabled", enabled);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ENEMY_SUPPORT_CHANNEL", field="ENEMY_SUPPORT_CHANNEL")
        public static class ENEMY_SUPPORT_CHANNEL extends CommandRef {
            public static final ENEMY_SUPPORT_CHANNEL cmd = new ENEMY_SUPPORT_CHANNEL();
            public ENEMY_SUPPORT_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="HIDE_APPLICANT_WARS", field="HIDE_APPLICANT_WARS")
        public static class HIDE_APPLICANT_WARS extends CommandRef {
            public static final HIDE_APPLICANT_WARS cmd = new HIDE_APPLICANT_WARS();
            public HIDE_APPLICANT_WARS create(String value) {
                return createArgs("value", value);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="DEFENSE_WAR_CHANNEL", field="DEFENSE_WAR_CHANNEL")
        public static class DEFENSE_WAR_CHANNEL extends CommandRef {
            public static final DEFENSE_WAR_CHANNEL cmd = new DEFENSE_WAR_CHANNEL();
            public DEFENSE_WAR_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
    }
    public static class unit{
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="unitCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String units, String costFactor) {
                return createArgs("units", units, "costFactor", costFactor);
            }
        }
    }
    public static class announce{
        @AutoRegister(clazz=link.locutus.core.command.AnnounceCommands.class,method="archiveAnnouncement")
        public static class archive extends CommandRef {
            public static final archive cmd = new archive();
            public archive create(String announcementId, String archive) {
                return createArgs("announcementId", announcementId, "archive", archive);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.AnnounceCommands.class,method="announce")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String sendTo, String subject, String announcement, String replacements, String requiredVariation, String requiredDepth, String seed, String sendMail, String sendDM, String force) {
                return createArgs("sendTo", sendTo, "subject", subject, "announcement", announcement, "replacements", replacements, "requiredVariation", requiredVariation, "requiredDepth", requiredDepth, "seed", seed, "sendMail", sendMail, "sendDM", sendDM, "force", force);
            }
        }
    }
    public static class channel{
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="closeInactiveChannels")
        public static class closeInactive extends CommandRef {
            public static final closeInactive cmd = new closeInactive();
            public closeInactive create(String category, String age, String force) {
                return createArgs("category", category, "age", age, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelPermissions")
        public static class permissions extends CommandRef {
            public static final permissions cmd = new permissions();
            public permissions create(String channel, String nations, String permission, String negate, String removeOthers, String listChanges, String pingAddedUsers) {
                return createArgs("channel", channel, "nations", nations, "permission", permission, "negate", negate, "removeOthers", removeOthers, "listChanges", listChanges, "pingAddedUsers", pingAddedUsers);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelUp")
        public static class up extends CommandRef {
            public static final up cmd = new up();
            public up create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelCategory")
        public static class category extends CommandRef {
            public static final category cmd = new category();
            public category create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelDown")
        public static class channelDown extends CommandRef {
            public static final channelDown cmd = new channelDown();
            public channelDown create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="debugPurgeChannels")
        public static class purge extends CommandRef {
            public static final purge cmd = new purge();
            public purge create(String category, String cutoff) {
                return createArgs("category", category, "cutoff", cutoff);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channel")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String channelName, String category, String copypasta, String addInternalAffairsRole, String addMilcom, String addForeignAffairs, String addEcon, String pingRoles, String pingAuthor) {
                return createArgs("channelName", channelName, "category", category, "copypasta", copypasta, "addInternalAffairsRole", addInternalAffairsRole, "addMilcom", addMilcom, "addForeignAffairs", addForeignAffairs, "addEcon", addEcon, "pingRoles", pingRoles, "pingAuthor", pingAuthor);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="allChannelMembers")
        public static class all extends CommandRef {
            public static final all cmd = new all();
            public all create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="memberChannels")
        public static class can_access extends CommandRef {
            public static final can_access cmd = new can_access();
            public can_access create(String member) {
                return createArgs("member", member);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="close")
        public static class close extends CommandRef {
            public static final close cmd = new close();
            public close create(String forceDelete) {
                return createArgs("forceDelete", forceDelete);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="open")
        public static class open extends CommandRef {
            public static final open cmd = new open();
            public open create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="deleteChannel")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelMembers")
        public static class members extends CommandRef {
            public static final members cmd = new members();
            public members create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="channelCount")
        public static class count extends CommandRef {
            public static final count cmd = new count();
            public count create() {
                return createArgs();
            }
        }
    }
    public static class land{
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="landLoot")
        public static class loot extends CommandRef {
            public static final loot cmd = new loot();
            public loot create(String attackerAcres, String defenderAcres) {
                return createArgs("attackerAcres", attackerAcres, "defenderAcres", defenderAcres);
            }
        }
    }
    public static class mail{
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="mailCommandOutput")
        public static class command extends CommandRef {
            public static final command cmd = new command();
            public command create(String nations, String subject, String command, String body, String sheet) {
                return createArgs("nations", nations, "subject", subject, "command", command, "body", body, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="mail")
        public static class send extends CommandRef {
            public static final send cmd = new send();
            public send create(String kingdoms, String subject, String message, String confirm) {
                return createArgs("kingdoms", kingdoms, "subject", subject, "message", message, "confirm", confirm);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="mailSheet")
        public static class sheet extends CommandRef {
            public static final sheet cmd = new sheet();
            public sheet create(String sheet, String confirm) {
                return createArgs("sheet", sheet, "confirm", confirm);
            }
        }
    }
    public static class role{
        public static class assignable{
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="listAssignableRoles")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="removeRole")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String member, String addRole) {
                    return createArgs("member", member, "addRole", addRole);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="addAssignableRole")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String requireRole, String assignableRoles) {
                    return createArgs("requireRole", requireRole, "assignableRoles", assignableRoles);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="removeAssignableRole")
            public static class unregister extends CommandRef {
                public static final unregister cmd = new unregister();
                public unregister create(String requireRole, String assignableRoles) {
                    return createArgs("requireRole", requireRole, "assignableRoles", assignableRoles);
                }
            }
        }
        public static class alias{
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="aliasRole")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String locutusRole, String discordRole, String alliance, String removeRole) {
                    return createArgs("locutusRole", locutusRole, "discordRole", discordRole, "alliance", alliance, "removeRole", removeRole);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="unregisterRole")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String locutusRole, String alliance) {
                    return createArgs("locutusRole", locutusRole, "alliance", alliance);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="mask")
        public static class mask extends CommandRef {
            public static final mask cmd = new mask();
            public mask create(String members, String role, String value, String toggleMaskFromOthers) {
                return createArgs("members", members, "role", role, "value", value, "toggleMaskFromOthers", toggleMaskFromOthers);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="addRoleToAllMembers")
        public static class addToAllMembers extends CommandRef {
            public static final addToAllMembers cmd = new addToAllMembers();
            public addToAllMembers create(String role) {
                return createArgs("role", role);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="hasRole")
        public static class has extends CommandRef {
            public static final has cmd = new has();
            public has create(String user, String role) {
                return createArgs("user", user, "role", role);
            }
        }
        public static class auto{
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="clearAllianceRoles")
            public static class clearAllianceRoles extends CommandRef {
                public static final clearAllianceRoles cmd = new clearAllianceRoles();
                public clearAllianceRoles create(String type) {
                    return createArgs("type", type);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="autoroleall")
            public static class all extends CommandRef {
                public static final all cmd = new all();
                public all create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="clearNicks")
            public static class clearNicks extends CommandRef {
                public static final clearNicks cmd = new clearNicks();
                public clearNicks create(String undo) {
                    return createArgs("undo", undo);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="autorole")
            public static class user extends CommandRef {
                public static final user cmd = new user();
                public user create(String member) {
                    return createArgs("member", member);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="addRole")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String member, String addRole) {
                return createArgs("member", member, "addRole", addRole);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.FunCommands.class,method="trorg")
    public static class trorg extends CommandRef {
        public static final trorg cmd = new trorg();
        public trorg create(String msg) {
            return createArgs("msg", msg);
        }
    }
    public static class war{
        public static class find{
            @AutoRegister(clazz=link.locutus.core.command.WarCommands.class,method="war")
            public static class enemy extends CommandRef {
                public static final enemy cmd = new enemy();
                public enemy create(String maxAttackAlert, String maxSpellAlert, String enemies, String myKingdom, String numResults, String includeStronger, String excludeWarUnitEstimate) {
                    return createArgs("maxAttackAlert", maxAttackAlert, "maxSpellAlert", maxSpellAlert, "enemies", enemies, "myKingdom", myKingdom, "numResults", numResults, "includeStronger", includeStronger, "excludeWarUnitEstimate", excludeWarUnitEstimate);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="raid")
            public static class raid extends CommandRef {
                public static final raid cmd = new raid();
                public raid create(String realm, String myKingdom, String numResults, String sortGold, String sortLand, String ignoreLosses, String ignoreDoNotRaid, String excludeWarUnitEstimate, String includeStronger) {
                    return createArgs("realm", realm, "myKingdom", myKingdom, "numResults", numResults, "sortGold", sortGold, "sortLand", sortLand, "ignoreLosses", ignoreLosses, "ignoreDoNotRaid", ignoreDoNotRaid, "excludeWarUnitEstimate", excludeWarUnitEstimate, "includeStronger", includeStronger);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="spyop")
            public static class intel extends CommandRef {
                public static final intel cmd = new intel();
                public intel create(String realm, String numResults, String noAlliance, String inactive, String skipSpiedAfterLogin, String ignoreDoNotRaid, String excludeWarUnitEstimate) {
                    return createArgs("realm", realm, "numResults", numResults, "noAlliance", noAlliance, "inactive", inactive, "skipSpiedAfterLogin", skipSpiedAfterLogin, "ignoreDoNotRaid", ignoreDoNotRaid, "excludeWarUnitEstimate", excludeWarUnitEstimate);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="counter")
        public static class counter extends CommandRef {
            public static final counter cmd = new counter();
            public counter create(String target, String counterWith, String allowWeak, String onlyActive, String requireDiscord, String ping, String allowSameAlliance) {
                return createArgs("target", target, "counterWith", counterWith, "allowWeak", allowWeak, "onlyActive", onlyActive, "requireDiscord", requireDiscord, "ping", ping, "allowSameAlliance", allowSameAlliance);
            }
        }
    }
    public static class stat{
        public static class alliance{
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="allianceRankingTime")
            public static class ranking_time extends CommandRef {
                public static final ranking_time cmd = new ranking_time();
                public ranking_time create(String alliances, String metric, String timeStart, String timeEnd, String reverseOrder, String uploadFile) {
                    return createArgs("alliances", alliances, "metric", metric, "timeStart", timeStart, "timeEnd", timeEnd, "reverseOrder", reverseOrder, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="allianceRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
                public ranking create(String alliances, String metric, String reverseOrder, String uploadFile) {
                    return createArgs("alliances", alliances, "metric", metric, "reverseOrder", reverseOrder, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="allianceMetricsByTurn")
            public static class metrics_time extends CommandRef {
                public static final metrics_time cmd = new metrics_time();
                public metrics_time create(String metric, String coalition, String time) {
                    return createArgs("metric", metric, "coalition", coalition, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="allianceMetricsCompareByTurn")
            public static class metrics_compare_time extends CommandRef {
                public static final metrics_compare_time cmd = new metrics_compare_time();
                public metrics_compare_time create(String metric, String alliances, String time) {
                    return createArgs("metric", metric, "alliances", alliances, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="allianceMetricsAB")
            public static class compare_metrics extends CommandRef {
                public static final compare_metrics cmd = new compare_metrics();
                public compare_metrics create(String metric, String coalition1, String coalition2, String time) {
                    return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "time", time);
                }
            }
        }
        public static class war{
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="warsCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String coalition1, String coalition2, String timeStart, String timeEnd, String listWarIds, String showWarTypes, String allowedWarTypes) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd, "listWarIds", listWarIds, "showWarTypes", showWarTypes, "allowedWarTypes", allowedWarTypes);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="warAttacksByDay")
            public static class attacks_time extends CommandRef {
                public static final attacks_time cmd = new attacks_time();
                public attacks_time create(String inclueOffensives, String includeDefensives, String nations, String cutoff) {
                    return createArgs("inclueOffensives", inclueOffensives, "includeDefensives", includeDefensives, "nations", nations, "cutoff", cutoff);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="warRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
                public ranking create(String time, String attackers, String defenders, String onlyOffensives, String onlyDefensives, String normalizePerMember, String ignore2dInactives, String rankByKingdom, String allowedTypes) {
                    return createArgs("time", time, "attackers", attackers, "defenders", defenders, "onlyOffensives", onlyOffensives, "onlyDefensives", onlyDefensives, "normalizePerMember", normalizePerMember, "ignore2dInactives", ignore2dInactives, "rankByKingdom", rankByKingdom, "allowedTypes", allowedTypes);
                }
            }
        }
        public static class kingdom{
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="nationRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
                public ranking create(String nations, String attribute, String groupByAlliance, String reverseOrder, String total) {
                    return createArgs("nations", nations, "attribute", attribute, "groupByAlliance", groupByAlliance, "reverseOrder", reverseOrder, "total", total);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="attributeScoreGraph")
            public static class attribute_score extends CommandRef {
                public static final attribute_score cmd = new attribute_score();
                public attribute_score create(String metric, String coalition1, String coalition2, String includeInactives, String includeApplicants, String total) {
                    return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.StatCommands.class,method="scoreTierGraph")
            public static class score_tier extends CommandRef {
                public static final score_tier cmd = new score_tier();
                public score_tier create(String coalition1, String coalition2, String includeInactives, String includeApplicants) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
                }
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="copyPasta")
    public static class copyPasta extends CommandRef {
        public static final copyPasta cmd = new copyPasta();
        public copyPasta create(String key, String message, String requiredRolesAny) {
            return createArgs("key", key, "message", message, "requiredRolesAny", requiredRolesAny);
        }
    }
    public static class user{
        @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="dm")
        public static class dm extends CommandRef {
            public static final dm cmd = new dm();
            public dm create(String nation, String message) {
                return createArgs("nation", nation, "message", message);
            }
        }
    }
    public static class aid{
        public static class path{
            @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="findAidPath")
            public static class optimal extends CommandRef {
                public static final optimal cmd = new optimal();
                public optimal create(String sender, String receiver, String intermediaries) {
                    return createArgs("sender", sender, "receiver", receiver, "intermediaries", intermediaries);
                }
            }
        }
    }
    public static class settings_foreign_affairs{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="TREATY_ALERTS", field="TREATY_ALERTS")
        public static class TREATY_ALERTS extends CommandRef {
            public static final TREATY_ALERTS cmd = new TREATY_ALERTS();
            public TREATY_ALERTS create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="EMBASSY_CATEGORY", field="EMBASSY_CATEGORY")
        public static class EMBASSY_CATEGORY extends CommandRef {
            public static final EMBASSY_CATEGORY cmd = new EMBASSY_CATEGORY();
            public EMBASSY_CATEGORY create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="FA_SERVER", field="FA_SERVER")
        public static class FA_SERVER extends CommandRef {
            public static final FA_SERVER cmd = new FA_SERVER();
            public FA_SERVER create(String guild) {
                return createArgs("guild", guild);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="DO_NOT_RAID_TOP_X", field="DO_NOT_RAID_TOP_X")
        public static class DO_NOT_RAID_TOP_X extends CommandRef {
            public static final DO_NOT_RAID_TOP_X cmd = new DO_NOT_RAID_TOP_X();
            public DO_NOT_RAID_TOP_X create(String topAllianceScore) {
                return createArgs("topAllianceScore", topAllianceScore);
            }
        }
    }
    public static class settings_game_alerts{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="DELETION_ALERT_CHANNEL", field="DELETION_ALERT_CHANNEL")
        public static class DELETION_ALERT_CHANNEL extends CommandRef {
            public static final DELETION_ALERT_CHANNEL cmd = new DELETION_ALERT_CHANNEL();
            public DELETION_ALERT_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AA_ADMIN_LEAVE_ALERTS", field="AA_ADMIN_LEAVE_ALERTS")
        public static class AA_ADMIN_LEAVE_ALERTS extends CommandRef {
            public static final AA_ADMIN_LEAVE_ALERTS cmd = new AA_ADMIN_LEAVE_ALERTS();
            public AA_ADMIN_LEAVE_ALERTS create(String channel) {
                return createArgs("channel", channel);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.DiscordCommands.class,method="say")
    public static class say extends CommandRef {
        public static final say cmd = new say();
        public say create(String msg) {
            return createArgs("msg", msg);
        }
    }
    public static class settings_awareness_alert{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ENEMY_ALERT_FILTER", field="ENEMY_ALERT_FILTER")
        public static class ENEMY_ALERT_FILTER extends CommandRef {
            public static final ENEMY_ALERT_FILTER cmd = new ENEMY_ALERT_FILTER();
            public ENEMY_ALERT_FILTER create(String filter) {
                return createArgs("filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL_MODE", field="ENEMY_ALERT_CHANNEL_MODE")
        public static class ENEMY_ALERT_CHANNEL_MODE extends CommandRef {
            public static final ENEMY_ALERT_CHANNEL_MODE cmd = new ENEMY_ALERT_CHANNEL_MODE();
            public ENEMY_ALERT_CHANNEL_MODE create(String mode) {
                return createArgs("mode", mode);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL", field="ENEMY_ALERT_CHANNEL")
        public static class ENEMY_ALERT_CHANNEL extends CommandRef {
            public static final ENEMY_ALERT_CHANNEL cmd = new ENEMY_ALERT_CHANNEL();
            public ENEMY_ALERT_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
    }
    public static class fey{
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="fey_top")
        public static class top extends CommandRef {
            public static final top cmd = new top();
            public top create(String attacker, String my_score, String updateFey) {
                return createArgs("attacker", attacker, "my_score", my_score, "updateFey", updateFey);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="fey_land")
        public static class land extends CommandRef {
            public static final land cmd = new land();
            public land create(String attack) {
                return createArgs("attack", attack);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="fey_optimal")
        public static class optimal extends CommandRef {
            public static final optimal cmd = new optimal();
            public optimal create(String kingdom, String my_score, String my_attack, String updateFey) {
                return createArgs("kingdom", kingdom, "my_score", my_score, "my_attack", my_attack, "updateFey", updateFey);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.TrounceUtilCommands.class,method="fey_strength")
        public static class strength extends CommandRef {
            public static final strength cmd = new strength();
            public strength create(String land) {
                return createArgs("land", land);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="me")
    public static class me extends CommandRef {
        public static final me cmd = new me();
        public me create() {
            return createArgs();
        }
    }
    public static class settings_audit{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="MEMBER_LEAVE_ALERT_CHANNEL", field="MEMBER_LEAVE_ALERT_CHANNEL")
        public static class MEMBER_LEAVE_ALERT_CHANNEL extends CommandRef {
            public static final MEMBER_LEAVE_ALERT_CHANNEL cmd = new MEMBER_LEAVE_ALERT_CHANNEL();
            public MEMBER_LEAVE_ALERT_CHANNEL create(String channel) {
                return createArgs("channel", channel);
            }
        }
    }
    public static class coalition{
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="removeCoalition")
        public static class remove extends CommandRef {
            public static final remove cmd = new remove();
            public remove create(String alliances, String coalitionName) {
                return createArgs("alliances", alliances, "coalitionName", coalitionName);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="deleteCoalition")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String coalitionName) {
                return createArgs("coalitionName", coalitionName);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="addCoalition")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String alliances, String coalitionName) {
                return createArgs("alliances", alliances, "coalitionName", coalitionName);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="listCoalition")
        public static class list extends CommandRef {
            public static final list cmd = new list();
            public list create(String filter, String listIds, String ignoreDeleted) {
                return createArgs("filter", filter, "listIds", listIds, "ignoreDeleted", ignoreDeleted);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="createCoalition")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String alliances, String coalitionName) {
                return createArgs("alliances", alliances, "coalitionName", coalitionName);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="who")
    public static class who extends CommandRef {
        public static final who cmd = new who();
        public who create(String kingdoms) {
            return createArgs("kingdoms", kingdoms);
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.FunCommands.class,method="joke")
    public static class joke extends CommandRef {
        public static final joke cmd = new joke();
        public joke create() {
            return createArgs();
        }
    }
    public static class settings_role{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="addAssignableRole", field="ASSIGNABLE_ROLES")
        public static class addAssignableRole extends CommandRef {
            public static final addAssignableRole cmd = new addAssignableRole();
            public addAssignableRole create(String role, String roles) {
                return createArgs("role", role, "roles", roles);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTOROLE_ALLIANCE_RANK", field="AUTOROLE_ALLIANCE_RANK")
        public static class AUTOROLE_ALLIANCE_RANK extends CommandRef {
            public static final AUTOROLE_ALLIANCE_RANK cmd = new AUTOROLE_ALLIANCE_RANK();
            public AUTOROLE_ALLIANCE_RANK create(String allianceRank) {
                return createArgs("allianceRank", allianceRank);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTOROLE", field="AUTOROLE")
        public static class AUTOROLE extends CommandRef {
            public static final AUTOROLE cmd = new AUTOROLE();
            public AUTOROLE create(String mode) {
                return createArgs("mode", mode);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTONICK", field="AUTONICK")
        public static class AUTONICK extends CommandRef {
            public static final AUTONICK cmd = new AUTONICK();
            public AUTONICK create(String mode) {
                return createArgs("mode", mode);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTOROLE_ALLY_ROLES", field="AUTOROLE_ALLY_ROLES")
        public static class AUTOROLE_ALLY_ROLES extends CommandRef {
            public static final AUTOROLE_ALLY_ROLES cmd = new AUTOROLE_ALLY_ROLES();
            public AUTOROLE_ALLY_ROLES create(String roles) {
                return createArgs("roles", roles);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTOROLE_TOP_X", field="AUTOROLE_TOP_X")
        public static class AUTOROLE_TOP_X extends CommandRef {
            public static final AUTOROLE_TOP_X cmd = new AUTOROLE_TOP_X();
            public AUTOROLE_TOP_X create(String topScoreRank) {
                return createArgs("topScoreRank", topScoreRank);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="AUTOROLE_ALLY_GOV", field="AUTOROLE_ALLY_GOV")
        public static class AUTOROLE_ALLY_GOV extends CommandRef {
            public static final AUTOROLE_ALLY_GOV cmd = new AUTOROLE_ALLY_GOV();
            public AUTOROLE_ALLY_GOV create(String enabled) {
                return createArgs("enabled", enabled);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="ASSIGNABLE_ROLES", field="ASSIGNABLE_ROLES")
        public static class ASSIGNABLE_ROLES extends CommandRef {
            public static final ASSIGNABLE_ROLES cmd = new ASSIGNABLE_ROLES();
            public ASSIGNABLE_ROLES create(String value) {
                return createArgs("value", value);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.FACommands.class,method="embassy")
    public static class embassy extends CommandRef {
        public static final embassy cmd = new embassy();
        public embassy create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="spyop")
    public static class spyop extends CommandRef {
        public static final spyop cmd = new spyop();
        public spyop create(String realm, String numResults, String noAlliance, String inactive, String skipSpiedAfterLogin, String ignoreDoNotRaid, String excludeWarUnitEstimate) {
            return createArgs("realm", realm, "numResults", numResults, "noAlliance", noAlliance, "inactive", inactive, "skipSpiedAfterLogin", skipSpiedAfterLogin, "ignoreDoNotRaid", ignoreDoNotRaid, "excludeWarUnitEstimate", excludeWarUnitEstimate);
        }
    }
    public static class audit{
        @AutoRegister(clazz=link.locutus.core.command.IACommands.class,method="checkCities")
        public static class run extends CommandRef {
            public static final run cmd = new run();
            public run create(String nationList, String audits, String pingUser, String mailResults, String postInInterviewChannels, String skipUpdate) {
                return createArgs("nationList", nationList, "audits", audits, "pingUser", pingUser, "mailResults", mailResults, "postInInterviewChannels", postInInterviewChannels, "skipUpdate", skipUpdate);
            }
        }
    }
    public static class interview{
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="sortInterviews")
        public static class sort extends CommandRef {
            public static final sort cmd = new sort();
            public sort create(String sortCategorized) {
                return createArgs("sortCategorized", sortCategorized);
            }
        }
        public static class mentee{
            @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="mentee")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String mentee, String force) {
                    return createArgs("mentee", mentee, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="unassignMentee")
            public static class unassign extends CommandRef {
                public static final unassign cmd = new unassign();
                public unassign create(String mentee) {
                    return createArgs("mentee", mentee);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="myMentees")
            public static class list_mine extends CommandRef {
                public static final list_mine cmd = new list_mine();
                public list_mine create(String mentees, String timediff) {
                    return createArgs("mentees", mentees, "timediff", timediff);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="interview")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String kingdom, String user) {
                return createArgs("kingdom", kingdom, "user", user);
            }
        }
        public static class mentor{
            @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="listMentors")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String mentees, String timediff, String includeAudit, String ignoreUnallocatedMembers, String listIdleMentors) {
                    return createArgs("mentees", mentees, "timediff", timediff, "includeAudit", includeAudit, "ignoreUnallocatedMembers", ignoreUnallocatedMembers, "listIdleMentors", listIdleMentors);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="mentor")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String mentor, String mentee, String force) {
                    return createArgs("mentor", mentor, "mentee", mentee, "force", force);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="syncInterviews")
        public static class sync extends CommandRef {
            public static final sync cmd = new sync();
            public sync create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="iaCat")
        public static class set_category extends CommandRef {
            public static final set_category cmd = new set_category();
            public set_category create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="iachannels")
        public static class list extends CommandRef {
            public static final list cmd = new list();
            public list create(String filter, String time) {
                return createArgs("filter", filter, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.InterviewCommands.class,method="interviewMessage")
        public static class send_message extends CommandRef {
            public static final send_message cmd = new send_message();
            public send_message create(String nations, String message, String pingMentee) {
                return createArgs("nations", nations, "message", message, "pingMentee", pingMentee);
            }
        }
    }
    public static class announcement{
        @AutoRegister(clazz=link.locutus.core.command.PlayerSettingCommands.class,method="readAnnouncement")
        public static class read extends CommandRef {
            public static final read cmd = new read();
            public read create(String realm, String ann_id, String markRead) {
                return createArgs("realm", realm, "ann_id", ann_id, "markRead", markRead);
            }
        }
    }
    public static class settings_default{
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="unregisterAlliance", field="ALLIANCE_ID")
        public static class unregisterAlliance extends CommandRef {
            public static final unregisterAlliance cmd = new unregisterAlliance();
            public unregisterAlliance create(String alliances) {
                return createArgs("alliances", alliances);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="registerAlliance", field="ALLIANCE_ID")
        public static class registerAlliance extends CommandRef {
            public static final registerAlliance cmd = new registerAlliance();
            public registerAlliance create(String alliances) {
                return createArgs("alliances", alliances);
            }
        }
        @AutoRegister(clazz=link.locutus.core.db.guild.GuildKey.class,method="DELEGATE_SERVER", field="DELEGATE_SERVER")
        public static class DELEGATE_SERVER extends CommandRef {
            public static final DELEGATE_SERVER cmd = new DELEGATE_SERVER();
            public DELEGATE_SERVER create(String guild) {
                return createArgs("guild", guild);
            }
        }
    }
    public static class kingdom{
        @AutoRegister(clazz=link.locutus.core.command.KingdomCommands.class,method="leftAA")
        public static class departures extends CommandRef {
            public static final departures cmd = new departures();
            public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds) {
                return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="register")
        public static class register extends CommandRef {
            public static final register cmd = new register();
            public register create(String kingdom, String key) {
                return createArgs("kingdom", kingdom, "key", key);
            }
        }
        public static class bp_mana{
            @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="setBpMana")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String realm, String bp, String mana) {
                    return createArgs("realm", realm, "bp", bp, "mana", mana);
                }
            }
            @AutoRegister(clazz=link.locutus.core.command.SheetCommands.class,method="listBpMana")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String kingdoms) {
                    return createArgs("kingdoms", kingdoms);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.KingdomCommands.class,method="dnr")
        public static class can_i_raid extends CommandRef {
            public static final can_i_raid cmd = new can_i_raid();
            public can_i_raid create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="who")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String kingdoms) {
                return createArgs("kingdoms", kingdoms);
            }
        }
        public static class set{
            @AutoRegister(clazz=link.locutus.core.command.AdminCommands.class,method="setUnits")
            public static class units extends CommandRef {
                public static final units cmd = new units();
                public units create(String nation, String units, String attack, String defense) {
                    return createArgs("nation", nation, "units", units, "attack", attack, "defense", defense);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.core.command.BasicCommands.class,method="me")
        public static class me extends CommandRef {
            public static final me cmd = new me();
            public me create() {
                return createArgs();
            }
        }
    }

}
