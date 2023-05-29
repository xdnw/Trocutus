package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.Timestamp;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomList;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class KingdomCommands {
    @Command(aliases = {"dnr", "caniraid"}, desc = "Check if declaring war on a nation is allowed by the guild's Do Not Raid (DNR) settings")
    public String dnr(@Me GuildDB db, DBKingdom nation) {
        Integer dnrTopX = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
        Set<Integer> canRaid = db.getCoalition(Coalition.CAN_RAID);
        Set<Integer> canRaidInactive = db.getCoalition(Coalition.CAN_RAID_INACTIVE);

        String title;
        Boolean dnr = db.getCanRaid().apply(nation);
        if (!dnr) {
            title = ("do NOT raid " + nation.getName());
        }  else if (nation.getPosition().ordinal() > 1 && nation.getActive_m() < 10000) {
            title = ("You CAN raid " + nation.getName() + " (however they are an active member of an alliance)");
        } else if (nation.getPosition().ordinal() > 1) {
            title =  "You CAN raid " + nation.getName() + " (however they are a member of an alliance)";
        } else if (nation.getAlliance_id() != 0) {
            title =  "You CAN raid " + nation.getName();
        } else {
            title =  "You CAN raid " + nation.getName();
        }
        StringBuilder response = new StringBuilder();
        response.append("**" + title + "**");
        if (dnrTopX != null) {
            response.append("\n\n> Do Not Raid Guidelines:");
            response.append("\n> - Avoid members of the top " + dnrTopX + " alliances and members of direct allies (check ingame treaties)");

            Set<String> enemiesStr = enemies.stream().map(f -> TrounceUtil.getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidStr = canRaid.stream().map(f -> TrounceUtil.getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> canRaidInactiveStr = canRaidInactive.stream().map(f -> TrounceUtil.getAllianceName(f)).filter(Objects::nonNull).collect(Collectors.toSet());

            if (!enemiesStr.isEmpty()) {
                response.append("\n> - Enemies: " + StringMan.getString(enemiesStr));
            }
            if (!canRaidStr.isEmpty()) {
                response.append("\n> - You CAN raid: " + StringMan.getString(canRaidStr));
            }
            if (!canRaidInactiveStr.isEmpty()) {
                response.append("\n> - You CAN raid inactives (1w) of: " + StringMan.getString(canRaidInactiveStr));
            }
        }
        return response.toString();
    }

    @Command(desc = "List the alliance rank changes of a nation or alliance members")
    public String leftAA(@Me IMessageIO io, @Me Guild guild, @Me User author, @Me Map<DBRealm, DBKingdom> me,
                         KingdomOrAlliance nationOrAlliance,
                         @Arg("Date to start from")
                         @Default @Timestamp Long time,
                         @Arg("Only include these nations")
                         @Default KingdomList filter,
                         @Arg("Ignore inactive nations (7 days)")
                         @Switch("a") boolean ignoreInactives,
                         @Arg("Ignore nations in vacation mode")
                         @Switch("v") boolean ignoreVM,
                         @Arg("Ignore nations currently a member of an alliance")
                         @Switch("m") boolean ignoreMembers,
                         @Arg("Attach a list of all nation ids found")
                         @Switch("i") boolean listIds) throws Exception {
        if (time == null) time = 0L;
        StringBuilder response = new StringBuilder();
        Map<Integer, Map.Entry<Long, Rank>> removes;
        List<Map.Entry<Map.Entry<DBKingdom, Integer>, Map.Entry<Long, Rank>>> toPrint = new ArrayList<>();

        boolean showCurrentAA = false;
        if (nationOrAlliance.isKingdom()) {
            DBKingdom nation = nationOrAlliance.asKingdom();
            removes = nation.getAllianceHistory();
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                DBKingdom tmp = nation;
                if (tmp == null) {
                    tmp = new DBKingdom();
                    tmp.setId(nation.getId());
                    tmp.setAlliance_id(entry.getKey());
                    tmp.setName(nation.getId() + "");
                }
                AbstractMap.SimpleEntry<DBKingdom, Integer> key = new AbstractMap.SimpleEntry<>(tmp, entry.getKey());
                Map.Entry<Long, Rank> value = entry.getValue();
                toPrint.add(new AbstractMap.SimpleEntry<>(key, value));
            }

        } else {
            showCurrentAA = true;
            DBAlliance alliance = nationOrAlliance.asAlliance();
            removes = alliance.getRemoves();


            if (removes.isEmpty()) return "No history found";

            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                if (entry.getValue().getKey() < time) continue;

                DBKingdom nation = Trocutus.imp().getDB().getKingdom(entry.getKey());
                if (nation != null && (filter == null || filter.getKingdoms().contains(nation))) {

                    if (ignoreInactives && nation.getActive_m() > 10000) continue;
                    if (ignoreVM && nation.isVacation()) continue;
                    if (ignoreMembers && nation.getPosition().ordinal() > 1) continue;

                    AbstractMap.SimpleEntry<DBKingdom, Integer> key = new AbstractMap.SimpleEntry<>(nation, alliance.getId());
                    toPrint.add(new AbstractMap.SimpleEntry<>(key, entry.getValue()));
                }
            }
        }

        Set<Integer> ids = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<Map.Entry<DBKingdom, Integer>, Map.Entry<Long, Rank>> entry : toPrint) {
            long diff = now - entry.getValue().getKey();
            Rank rank = entry.getValue().getValue();
            String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

            Map.Entry<DBKingdom, Integer> nationAA = entry.getKey();
            DBKingdom nation = nationAA.getKey();
            ids.add(nation.getId());

            response.append(timeStr + " ago: " + nationAA.getKey().getName() + " left " + TrounceUtil.getAllianceName(nationAA.getValue()) + " | " + rank.name());
            if (showCurrentAA && nation.getId() != 0) {
                response.append(" and joined " + nation.getAllianceName());
            }
            response.append("\n");
        }

        if (response.length() == 0) return "No history found in the specified timeframe";
        IMessageBuilder msg = io.create();
        if (listIds) {
            msg.file("ids.txt",  StringMan.join(ids, ","));
        }
        msg.append(response.toString()).send();
        return null;
    }
}
