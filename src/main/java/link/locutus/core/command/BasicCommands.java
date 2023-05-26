package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.binding.annotation.Timediff;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.pojo.pages.AllianceMembers;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.entities.DBAttack;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.entities.DBRealm;
import link.locutus.core.db.entities.DBSpy;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MarkupUtil;
import link.locutus.util.MathMan;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.AbstractMap;
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
import java.util.stream.Collectors;

public class BasicCommands {
    private final Map<UUID, Integer> registerIds = new ConcurrentHashMap<>();

    @Command
    public String register(@Me User user, DBKingdom kingdom, @Default UUID key) throws IOException {
        User existingUser = kingdom.getUser();
        if (existingUser != null && !existingUser.equals(user)) {
            throw new IllegalArgumentException("Your kingdom is already registered by: " + existingUser.getAsMention());
        }
        if (key == null) {
            UUID uuid = UUID.randomUUID();
            String title = "Copy this command to register your kingdom";
            String message = "/register kingdom:" + kingdom.getId() + " key:" + uuid.toString();
            registerIds.put(uuid, kingdom.getId());

            Trocutus.imp().rootAuth().sendMail(Settings.INSTANCE.NATION_ID, kingdom, title, message);

            String url = "https://trounced.net/kingdom/" + kingdom.getSlug() + "/mail/inbox";
            return "A registration mail was sent to your kingdom in-game: <" + url + ">";
        } else {
            Integer kingdomId = registerIds.remove(key);
            if (kingdomId == null) {
                throw new IllegalArgumentException("Invalid key: " + key + " (try again)");
            }
            if (kingdomId != kingdom.getId()) {
                throw new IllegalArgumentException("Your kingdom: " + kingdom.getName() + " is not the kingdom you are trying to register (" + kingdomId + " != " + kingdom.getId() + ")");
            }
            Trocutus.imp().getDB().saveUser(user.getIdLong(), kingdom);
            return "Registered your kingdom: " + kingdom.getName() + " " + user.getAsMention();
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
            String url = kingdom.getUrl(myName);
            result.append("<" + url + ">\n");

            User user = kingdom.getUser();
            if (user != null) {
                result.append("user: " + user.getAsMention() + "\n");
            }
            result.append("id: `" + kingdom.getId() + "`\n");
            result.append("realm: `" + realm.getName() + "`\n");
            result.append("alliance: `" + kingdom.getAllianceName() + "`\n");
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

            io.create().embed(kingdom.getName(), result.toString()).send();
        } else {
            Set<String> realms = kingdoms.stream().map(k -> k.getRealm().getName()).collect(Collectors.toSet());
            Set<String> alliances = kingdoms.stream().map(k -> k.getAllianceName()).collect(Collectors.toSet());
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
            result.append("alliances: `" + String.join("`, `", alliances) + "`\n");
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
    public String raid(DBRealm realm, @Me Map<DBRealm, DBKingdom> me, @Switch("g") boolean sortGold, @Switch("l") boolean sortLand) throws IOException {
        if (sortGold && sortLand) {
            sortGold = false;
            sortLand = false;
        }
        DBKingdom myKingdom = me.get(realm);
        if (myKingdom == null) return "You are not in a kingdom in this realm";

        DBKingdom root = Trocutus.imp().rootAuth().getKingdom(realm.getId());
        if (root == null) return "Could not find root kingdom";
        if (root.getAlliance_id() != myKingdom.getAlliance_id()) return "You are not in the same alliance as " + root.getName() + " | " + root.getAllianceName();

        AllianceMembers.AllianceMember memberInfo = Trocutus.imp().getScraper().getAllianceMemberStrength(myKingdom.getRealm_id(), myKingdom.getId());
        if (memberInfo == null) return "Could not find member info for " + myKingdom.getName();

        // -50% -> 50%
        double minScore = memberInfo.land.total * 0.5;
        double maxScore = memberInfo.land.total * 1.5;
        Set<DBKingdom> inRange = realm.getKindoms(f -> f.getTotal_land() >= minScore && f.getTotal_land() <= maxScore);
        Map<DBKingdom, DBSpy> spyops = new HashMap<>();
        List<Map.Entry<DBKingdom, Long>> values = new ArrayList<>();

        for (DBKingdom kingdom : inRange) {
            DBSpy spy = kingdom.getLatestSpyReport();
            if (spy == null) continue;
            if (spy.defense > memberInfo.stats.attack) continue;
            double goldLoot =  (long) (spy.gold * 0.2);
            double defenderRatio = kingdom.getTotal_land() / (double) memberInfo.land.total;
            double landLoot = 0.1 * kingdom.getTotal_land() * (Math.pow(defenderRatio, 2.2));

            double value;
            if (sortGold) {
                value = goldLoot;
            } else if(sortLand) {
                value = landLoot;
            } else {
                value = goldLoot + landLoot * 500;
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
        for (Map.Entry<DBKingdom, Long> entry : values) {
            DBKingdom kingdom = entry.getKey();
            DBSpy spy = kingdom.getLatestSpyReport();
            double value = entry.getValue();
            response.append("__**" + kingdom.getName() + " | " + kingdom.getAllianceName() + ":**__ " + kingdom.getTotal_land() + " ns\n");
            response.append("<" + kingdom.getUrl(myKingdom.getSlug()) + "> ");
            response.append("Worth: `$" + MathMan.format((long) value) + "` (" + types + ")\n");
            response.append("Spied: " + DiscordUtil.timestamp(spy.date, "R") + " | ");
            response.append("Active: " + DiscordUtil.timestamp(kingdom.getLast_active(), "R") + "\n");
            response.append("Attack Str: `" + spy.attack + "` | Defense Str: `" + spy.defense + "`\n");
            response.append("Resource: `" + kingdom.getResource_level() + "` | Alert: `" + kingdom.getAlert_level() + "`\n\n");
        }

        response.append("\n\n`note: Please use '/spyop' to find suitable kingdoms to spy`");
        response.append("\n`note: you loot 20% of your opponents unprotected gold");
        response.append("\n`note: you need 1 more attack than enemy defense to win`");
        return response.toString();
    }

    @Command
    public String spyop(DBRealm realm, @Me Map<DBRealm, DBKingdom> me, int score, @Switch("n") @Default("5") int numResults, @Switch("a") boolean noAlliance, @Switch("i") @Timediff Long inactive, @Switch("l") boolean skipSpiedAfterLogin) {
        DBKingdom myKingdom = me.get(realm);
        String myName = myKingdom == null ? "__your_name__" : myKingdom.getSlug();
        // -50% -> 50%
        double minScore = score * 0.5;
        double maxScore = score * 1.5;
        Set<DBKingdom> inRange = realm.getKindoms(f -> f.getTotal_land() >= minScore && f.getTotal_land() <= maxScore);

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
                value = Integer.MAX_VALUE + kingdom.getResource_level().ordinal();
            } else {
                spies.put(kingdom.getId(), spy);
                DBAttack def = kingdom.getLatestDefensive();
                long max = lastLogin;
                if (def != null) max = Math.max(max, def.date);
                value = max - spy.date;
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

        response.append("Top " + numResults + " results for " + score + " score:\n");
        for (Map.Entry<DBKingdom, Long> result : results) {
            DBKingdom kingdom = result.getKey();

            DBSpy spy = spies.get(kingdom.getId());
            response.append("__**" + kingdom.getName() + " | " + kingdom.getAllianceName() + ":**__ " + kingdom.getTotal_land() + " ns\n");
            response.append("<" + kingdom.getUrl(myName) + "> ");
            if (spy != null) {
                response.append("Spied: " + DiscordUtil.timestamp(spy.date, "R") + " | ");
            }
            response.append("Active: " + DiscordUtil.timestamp(kingdom.getLast_active(), "R") + "\n");
            if (spy != null) {
                response.append("$" + spy.gold + " (total) | $" + spy.protectedGold + " (protected)\n");
                response.append("Attack Str: " + spy.attack + " | Defense Str: " + spy.defense + "\n");
                response.append("```" + spy.getUnitMarkdown() + "``` ");
            }
            response.append("Resource: `" + kingdom.getResource_level() + "` | Alert: `" + kingdom.getAlert_level() + "`\n\n");
        }

        return response.toString();
    }
}
