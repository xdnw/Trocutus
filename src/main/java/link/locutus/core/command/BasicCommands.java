package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.binding.annotation.Timediff;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.Auth;
import link.locutus.core.api.pojo.pages.AllianceMembers;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MarkupUtil;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import link.locutus.util.TrounceUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BasicCommands {
    private final Map<UUID, Integer> registerIds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> registerUsers = new ConcurrentHashMap<>();

    @Command
    public String about() {
        return """
                Bot created and managed by the Interwebs Sourcery division of the Borg Collective. For setup instructions visit <https://github.com/xdnw/Trocutus> and follow the summoning ritual instructions.
                """;
    }

    @Command
    public String register(@Me User user, DBKingdom kingdom, @Default UUID key) throws IOException {
        User existingUser = kingdom.getUser();
        if (existingUser != null && existingUser.equals(user)) {
            throw new IllegalArgumentException("Your kingdom is already registered by: " + existingUser.getName());
        }
        if (key == null) {
            UUID uuid = UUID.randomUUID();
            String title = "Copy this command to register your kingdom";
            String message = "/register kingdom:" + kingdom.getId() + " key:" + uuid.toString();
            registerIds.put(uuid, kingdom.getId());
            registerUsers.put(uuid, user.getIdLong());

            Trocutus.imp().rootAuth().sendMail(Settings.INSTANCE.NATION_ID, kingdom, title, message);

            String url = "https://trounced.net/kingdom/" + kingdom.getSlug() + "/mail/inbox";
            return "A registration mail was sent to your kingdom in-game: <" + url + ">";
        } else {
            Integer kingdomId = registerIds.remove(key);
            Long userId = registerUsers.remove(key);
            if (kingdomId == null) {
                throw new IllegalArgumentException("Invalid key: " + key + " (try again)");
            }
            if (kingdomId != kingdom.getId()) {
                throw new IllegalArgumentException("Your kingdom: " + kingdom.getName() + " is not the kingdom you are trying to register (" + kingdomId + " != " + kingdom.getId() + ")");
            }
            if (userId == null) {
                throw new IllegalArgumentException("Invalid key: " + key + " (try again) invalid userId");
            }
            if (userId != user.getIdLong()) {
                throw new IllegalArgumentException("Your user id (" + user.getIdLong() + ") does not match who ran the register command: " + userId + " (try again)");
            }
            Trocutus.imp().getDB().saveUser(user.getIdLong(), kingdom);

            StringBuilder response = new StringBuilder("Registered your kingdom: " + kingdom.getName() + " " + user.getAsMention());
            for (Guild mutualGuild : user.getMutualGuilds()) {
                GuildDB db = Trocutus.imp().getGuildDB(mutualGuild);
                if (db == null) continue;
                Member member = mutualGuild.getMember(user);
                if (member == null) continue;
                try {
                    db.getAutoRoleTask().autoRole(member, f -> response.append("\n" + mutualGuild.getName() + ": " + f));
                } catch (Throwable e) {

                }
            }
            return response.toString();
        }
    }

    @Command
    public String me(@Me Map<DBRealm, DBKingdom> me, @Me IMessageIO io) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<DBRealm, DBKingdom> entry : me.entrySet()) {
            DBKingdom kingdom = entry.getValue();
            DBRealm realm = entry.getKey();
            result.append("# **" + realm.getName() + "/" + realm.getId() + "**\n");

            result.append(kingdom.getName() + " | " + kingdom.getAllianceName() + "\n");
            result.append("position: `" + kingdom.getPosition() + "`\n");
            result.append("total land: `" + kingdom.getTotal_land() + "`\n");
            result.append("alert level: `" + kingdom.getAlert_level() + "`\n");
            result.append("resource level: `" + kingdom.getResource_level() + "`\n");
            result.append("spell alert: `" + kingdom.getSpell_alert() + "`\n");
            result.append("last active: " + DiscordUtil.timestamp(kingdom.getLast_active(), "R") + "\n");
            result.append("vacation start: " + DiscordUtil.timestamp(kingdom.getVacation_start(), "d") + "\n");
            result.append("hero: `" + kingdom.getHero() + "`\n");
            result.append("hero level: `" + kingdom.getHero_level() + "`\n");
            result.append("last fetched: " + DiscordUtil.timestamp(kingdom.getLast_fetched(), "R") + "\n");
            result.append("-------\n\n");
        }

        io.create().embed("Your Kingdoms (" + me.size() + ")", result.toString()).send();
        return null;
    }

    @Command
    public String alliance(DBAlliance allianceInfo, @Me IMessageIO io) {
        StringBuilder result = new StringBuilder();

        String discord = allianceInfo.getDiscord_link();
        if (discord != null) {
            result.append("discord: <" + discord + ">\n");
            result.append("-------\n\n");
        }
        result.append("id: `" + allianceInfo.getId() + "`\n");
        result.append("realm: `" + allianceInfo.getRealm().getName() + "`\n");
        result.append("name: `" + allianceInfo.getName() + "`\n");
        result.append("tag: `" + allianceInfo.getTag() + "`\n");
        result.append("level: `" + allianceInfo.getLevel() + "`\n");
        result.append("experience: `" + allianceInfo.getExperience() + "`\n");
        result.append("level total: `" + allianceInfo.getLevelTotal() + "`\n");
        Set<DBKingdom> kingdoms = allianceInfo.getKingdoms();
        int numKingdoms = kingdoms.size();
        int totalLand = kingdoms.stream().mapToInt(DBKingdom::getTotal_land).sum();
        double avg_land = totalLand / (double) numKingdoms;
        result.append("kingdoms: `" + numKingdoms + "`\n");
        result.append("total land: `" + totalLand + "`\n");
        result.append("avg land: `" + MathMan.format(avg_land) + "`\n");

        if (allianceInfo.getDescription().trim().isEmpty()) {
            result.append("`no description`\n");
        } else {
            result.append("description:\n```\n" + allianceInfo.getDescription().trim() + "\n```\n");
        }

        result.append("\nlast fetched: " + DiscordUtil.timestamp(allianceInfo.getLastFetched(), "R") + "\n");

        io.create().embed(allianceInfo.getName(), result.toString()).send();
        return null;
    }

    @Command
    public String who(@Me Map<DBRealm, DBKingdom> me, Set<DBKingdom> kingdoms, @Me IMessageIO io) {
        if (kingdoms.isEmpty()) return "No kingdoms found";

        StringBuilder result = new StringBuilder();

        if (kingdoms.size() == 1) {
            DBKingdom kingdom = kingdoms.iterator().next();
            DBRealm realm = kingdom.getRealm();

            DBKingdom myKingdom = me.get(realm);
            String myName = myKingdom != null ? myKingdom.getSlug() : "__your_name__";
            result.append(kingdom.toMarkdown(myName, true));

            result.append("\n");
            boolean canView = me.values().stream().map(DBKingdom::getAlliance_id).anyMatch(f -> f == kingdom.getAlliance_id());
            result.append(kingdom.getInfoRowMarkdown(myName, true, canView, false, false));

            io.create().embed(kingdom.getName(), result.toString()).send();
        } else {
            Set<String> realms = kingdoms.stream().map(k -> k.getRealm().getName()).collect(Collectors.toSet());
            Map<String, Integer> countByAlliance = kingdoms.stream().collect(Collectors.groupingBy(DBKingdom::getAllianceName, Collectors.summingInt(k -> 1)));
            long total_land = kingdoms.stream().mapToLong(k -> k.getTotal_land()).sum();
            double average_land = kingdoms.stream().mapToLong(k -> k.getTotal_land()).average().orElse(0);
            double average_alert = kingdoms.stream().mapToDouble(k -> k.getAlert_level()).average().orElse(0);
            double average_resource = kingdoms.stream().mapToDouble(k -> k.getResource_level().ordinal()).average().orElse(0);
            double average_spell = kingdoms.stream().mapToDouble(k -> k.getSpell_alert()).average().orElse(0);
            long total_hero_levels = kingdoms.stream().mapToLong(k -> k.getHero_level()).sum();
            double average_hero_level = kingdoms.stream().mapToDouble(k -> k.getHero_level()).average().orElse(0);
            int numVacation = (int) kingdoms.stream().filter(k -> k.getVacation_start() > 0).count();
            int num_active_1h = (int) kingdoms.stream().filter(k -> k.getLast_active() > System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)).count();
            int num_active_6h = (int) kingdoms.stream().filter(k -> k.getLast_active() > System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)).count();
            int num_active_1d = (int) kingdoms.stream().filter(k -> k.getLast_active() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)).count();
            int num_active_2d = (int) kingdoms.stream().filter(k -> k.getLast_active() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)).count();
            int num_active_7d = (int) kingdoms.stream().filter(k -> k.getLast_active() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)).count();

            result.append("num nations: " + kingdoms.size() + "\n");
            result.append("realms: `" + String.join("`, `", realms) + "`\n");
            result.append("alliances: `" + StringMan.getString(countByAlliance) + "`\n");
            result.append("total land: `" + total_land + "`\n");
            result.append("average land: `" + MathMan.format(average_land) + "`\n");
            result.append("average alert: `" + MathMan.format(average_alert) + "`\n");
            result.append("average resource: `" + MathMan.format(average_resource) + "`\n");
            result.append("average spell: `" + MathMan.format(average_spell) + "`\n");
            result.append("total hero levels: `" + total_hero_levels + "`\n");
            result.append("average hero level: `" + MathMan.format(average_hero_level) + "`\n");
            result.append("num vacation: `" + numVacation + "`\n");
            result.append("num active 1h: `" + num_active_1h + "`\n");
            result.append("num active 6h: `" + num_active_6h + "`\n");
            result.append("num active 1d: `" + num_active_1d + "`\n");
            result.append("num active 2d: `" + num_active_2d + "`\n");
            result.append("num active 7d: `" + num_active_7d + "`\n");

            io.create().embed(kingdoms.size() + " kingdoms", result.toString()).send();
        }
        return null;
    }

    @Command(desc = "Send in-game mail to a list of nations")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String mail(@Me User author, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO channel, Set<DBKingdom> kingdoms, String subject, @TextArea String message, @Switch("f") boolean confirm) throws IOException {
        Auth auth = Trocutus.imp().rootAuth();

        message = MarkupUtil.transformURLIntoLinks(message);

        if (!confirm) {
            String title = "Send " + kingdoms.size() + " messages";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBKingdom kingdom : kingdoms) alliances.add(kingdom.getAlliance_id());
            String embedTitle = title + " to ";
            if (kingdoms.size() == 1) {
                DBKingdom kingdom = kingdoms.iterator().next();
                embedTitle += kingdoms.size() == 1 ? kingdom.getName() + " | " + kingdom.getAllianceName() : "nations";
            } else {
                embedTitle += " nations";
            }
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");
            body.append("body: ```" + message + "```");

            channel.create().confirmation(embedTitle, body.toString(), command, "confirm").send();
            return null;
        }

        if (!Roles.ADMIN.hasOnRoot(author)) {
            message += "\n\n<i>This message was sent by: " + author.getName() + "</i>";
        }

        CompletableFuture<IMessageBuilder> msg = null;
        if (kingdoms.size() > 1) {
            msg = channel.send("Sending to " + kingdoms.size() + " (please wait)");
        }
        List<String> full = new ArrayList<>();
        for (DBKingdom kingdom : kingdoms) {
            try {
//                String subjectF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, subject);
//                String messageF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, message);
                String result = auth.sendMail(Settings.INSTANCE.NATION_ID, kingdom, subject, message);
                full.add(result);
            } catch (Throwable e) {
                e.printStackTrace();
                full.add("Error sending mail to " + kingdom.getName() + " (" + kingdom.getId() + "): " + e.getMessage());
            }
        }

        return "Done sending mail:\n- " + String.join("\n- ", full);
    }

    @Command
    public String raid(@Me GuildDB db, DBRealm realm, @Me Map<DBRealm, DBKingdom> me, @Switch("att") DBKingdom myKingdom, @Switch("n") @Default("15") int numResults,  @Switch("g") boolean sortGold, @Switch("l") boolean sortLand, @Switch("i") boolean ignoreLosses, @Switch("dnr") boolean ignoreDoNotRaid, @Switch("w") boolean excludeWarUnitEstimate, @Switch("s") boolean includeStronger) throws IOException {
        if (sortGold && sortLand) {
            sortGold = false;
            sortLand = false;
        }
        if (myKingdom == null) myKingdom = me.get(realm);
        if (myKingdom == null) return "You are not in a kingdom in this realm";

        AllianceMembers.AllianceMember memberInfo = Trocutus.imp().getScraper().getAllianceMemberStrength(myKingdom.getRealm_id(), myKingdom.getId());
        if (memberInfo == null) return "Could not find member info for " + myKingdom.getName() + ". Member info requires authentication from a alliance admin: " + CM.credentials.login.cmd.toSlashMention();

        // -50% -> 50%
        int minScore = TrounceUtil.getMinScoreRange(memberInfo.land.total, true);
        int maxScore = TrounceUtil.getMaxScoreRange(memberInfo.land.total, true);
        Set<DBKingdom> inRange = realm.getKindoms(f -> f.getTotal_land() >= minScore && f.getTotal_land() <= maxScore);

        Function<DBKingdom, Boolean> canRaid;
        if (ignoreDoNotRaid) canRaid = f -> true;
        else canRaid = db.getCanRaid();
        inRange.removeIf(f -> !canRaid.apply(f));

        Map<DBKingdom, DBSpy> spyops = new HashMap<>();
        List<Map.Entry<DBKingdom, Long>> values = new ArrayList<>();

        for (DBKingdom kingdom : inRange) {
            DBSpy spy = kingdom.getLatestSpyReport();
            if (spy == null) continue;
            if (spy.defense > memberInfo.stats.attack && !includeStronger) continue;
            double goldLoot =  (long) (spy.gold * 0.2);
            double landLoot = TrounceUtil.landLoot(memberInfo.land.total, kingdom.getTotal_land(), false);

            double losses = ignoreLosses ? 0 : spy.defense * 3;

            double value;
            if (sortGold) {
                value = goldLoot - losses;
            } else if(sortLand) {
                value = landLoot - losses;
            } else {
                value = goldLoot + TrounceUtil.getLandValue(myKingdom.getTotal_land(), (int) landLoot) - losses;
            }
            values.add(Map.entry(kingdom, (long) value));
        }

        // sort
        values.sort((o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

        String types;
        if (sortGold) types = "gold";
        else if (sortLand) types = "land";
        else types = "gold + land";

        StringBuilder response = new StringBuilder();

        response.append("## **Targets for " + myKingdom.getName() + "**: (" + myKingdom.getTotal_land() + " land | " + memberInfo.stats.attack + " attack)\n");
        numResults = Math.min(numResults, values.size());
        for (int i = 0; i < numResults; i++) {
            Map.Entry<DBKingdom, Long> entry = values.get(i);
            DBKingdom kingdom = entry.getKey();
            boolean canView = me.values().stream().map(DBKingdom::getAlliance_id).anyMatch(f -> f == kingdom.getAlliance_id());
            response.append(kingdom.getInfoRowMarkdown(myKingdom.getSlug(), !excludeWarUnitEstimate, canView));
            double value = entry.getValue();
            response.append("Worth: `$" + MathMan.format((long) value) + "` (" + types + ")\n\n");
        }

        double optimalBothFey = 0;
        double optimalBothValue = 0;

        double optimalGoldFey = 0;
        double optimalGoldValue = 0;

        double optimalLandFey = 0;
        double optimalLandValue = 0;

        for (int land = minScore; land <= maxScore; land++) {
            double dpa = TrounceUtil.getFeyDPA(land);
            double strength = dpa * land;
            if (strength > memberInfo.stats.attack) break; // can't beat it :(

            double losses = strength * 3;
            double loot = 88 * land * 0.2;
            double landLoot = TrounceUtil.landLoot(memberInfo.land.total, land, true);
            double landValue = TrounceUtil.getLandValue(myKingdom.getTotal_land(), (int) landLoot);
            double netBoth = loot + landValue - losses;
            double netGold = loot - losses;
            if (netBoth > optimalBothValue) {
                optimalBothValue = netBoth;
                optimalBothFey = land;
            }
            if (landValue > optimalLandValue) {
                optimalLandValue = landValue;
                optimalLandFey = land;
            }
            if (netGold > optimalGoldValue) {
                optimalGoldValue = netGold;
                optimalGoldFey = land;
            }
        }

        double fey = TrounceUtil.getFeyLand(memberInfo.stats.attack);

        response.append("\n`note: Your army is on par with fey of approx. ~" + MathMan.format(fey) + " land`");
        response.append("\n`note: Optimal fey targets`");
        // both
        response.append("\n` - land+gold: " + MathMan.format(optimalBothFey) + " land -> net $" + MathMan.format((long) optimalBothValue) + "`");
        response.append("\n` - land: " + MathMan.format(optimalLandFey) + " land -> net $" + MathMan.format((long) optimalLandValue) + "`");
        response.append("\n` - gold: " + MathMan.format(optimalGoldFey) + " land -> net $" + MathMan.format((long) optimalGoldValue) + "`");

        response.append("\n\n`note: Please use '/spyop' to find suitable kingdoms to spy`");
        response.append("\n`note: you loot 20% of your opponents unprotected gold`");
        response.append("\n`note: you need 1 more attack than enemy defense to win`");
        return response.toString();
    }

    @Command
    public String spyop(@Me GuildDB db, DBRealm realm, @Me Map<DBRealm, DBKingdom> me, @Switch("n") @Default("15") int numResults, @Switch("a") boolean noAlliance, @Switch("i") @Timediff Long inactive, @Switch("l") boolean skipSpiedAfterLogin, @Switch("dnr") boolean ignoreDoNotRaid, @Switch("w") boolean excludeWarUnitEstimate) throws IOException {
        DBKingdom myKingdom = me.get(realm);
        if (myKingdom == null) return "You are not in a kingdom in this realm";

        AllianceMembers.AllianceMember memberInfo = Trocutus.imp().getScraper().getAllianceMemberStrength(myKingdom.getRealm_id(), myKingdom.getId());
        if (memberInfo == null) return "Could not find member info for " + myKingdom.getName() + ". Member info requires authentication from a alliance admin: " + CM.credentials.login.cmd.toSlashMention();

        int minScore = (int) (myKingdom.getAttackMinRange());
        int maxScore = (int) (myKingdom.getAttackMaxRange());

        Set<DBKingdom> inRange = realm.getKindoms(f -> f.getTotal_land() >= minScore && f.getTotal_land() <= maxScore);
        int myStr = myKingdom.getAttackStrength();

        Function<DBKingdom, Boolean> canRaid;
        if (ignoreDoNotRaid) canRaid = f -> true;
        else canRaid = db.getCanRaid();
        inRange.removeIf(f -> !canRaid.apply(f));

        if (noAlliance) {
            inRange.removeIf(k -> k.getAlliance_id() != 0);
        }

        if (inactive != null) {
            inRange.removeIf(k -> k.getLast_active() > System.currentTimeMillis() - inactive);
        }

        List<Map.Entry<DBKingdom, Long>> results = new ArrayList<>();
        Map<Integer, DBSpy> spies = new HashMap<>();
        Map<Integer, DBAttack> loss = new HashMap<>();
        for (DBKingdom kingdom : inRange) {
            if (myKingdom != null && kingdom.getAlliance_id() == myKingdom.getAlliance_id()) continue;

            DBSpy spy = kingdom.getLatestSpyReport();
            long lastLogin = kingdom.getLast_active();
            if (skipSpiedAfterLogin && spy != null && spy.date > lastLogin) {
                continue;
            }
            long value;
            if (spy == null) {
                value = ((long) Integer.MAX_VALUE) + kingdom.getResource_level().ordinal();
            } else {
                spies.put(kingdom.getId(), spy);
                DBAttack def = kingdom.getLatestDefensive();
                long max = lastLogin;
                if (def != null) max = Math.max(max, def.date);
                value = max - spy.date;
                if (spy.defense > myStr) {
                    value /= 1000;
                }
            }
            results.add(Map.entry(kingdom, value));
        }
        if (results.isEmpty()) {
            return "No results";
        }
        // sort results by value
        results.sort(Comparator.comparingLong(Map.Entry::getValue));
        // reverse
        Collections.reverse(results);

        StringBuilder response = new StringBuilder();

        response.append("Top " + numResults + " results for " + myKingdom.getName() + " score:\n");
        numResults = Math.min(numResults, results.size());
        for (int i = 0; i < numResults; i++) {
            Map.Entry<DBKingdom, Long> result = results.get(i);
            DBKingdom kingdom = result.getKey();

            boolean canView = me.values().stream().map(DBKingdom::getAlliance_id).anyMatch(f -> f == kingdom.getAlliance_id());
            response.append(kingdom.getInfoRowMarkdown(myKingdom.getSlug(), !excludeWarUnitEstimate, canView));
            response.append("\n\n");
        }

        return response.toString();
    }
}
