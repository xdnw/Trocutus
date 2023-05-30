package link.locutus.core.command;

import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.util.DiscordUtil;
import link.locutus.util.TrounceUtil;

import javax.swing.SwingContainer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WarCommands {
    @Command
    public String war(@Me GuildDB db, @Me Map<DBRealm, DBKingdom> me, int maxAttackAlert, int maxSpellAlert, @Default Set<DBKingdom> enemies, @Switch("n") @Default("15") int numResults, @Switch("s") boolean includeStronger, @Switch("w") boolean excludeWarUnitEstimate) {
        if (enemies == null) {
            enemies = new HashSet<>();
            Set<Integer> enemyIds = db.getCoalition(Coalition.ENEMIES);
            for (int id : enemyIds) {
                DBAlliance aa = DBAlliance.get(id);
                if (aa != null) {
                    enemies.addAll(aa.getKingdoms());
                }
            }
        }
        Set<Integer> realm = enemies.stream().map(DBKingdom::getRealm_id).collect(Collectors.toSet());
        if (realm.size() > 1) {
            return "You can only check one realm at a time (not: " + realm + ")";
        }
        if (enemies.isEmpty()) {
            return "No enemies found.";
        }
        int realmId = realm.iterator().next();
        DBRealm realmObj = DBRealm.getOrCreate(realmId);
        DBKingdom myKingdom = me.get(realmObj);
        if (myKingdom == null) {
            return "You are not in this realm. Use: " + CM.register.cmd.toSlashMention();
        }
        int outScoreRemoved = 0;
        {
            int size = enemies.size();
            enemies.removeIf(k -> k.getScore() < myKingdom.getAttackMinRange() || k.getScore() > myKingdom.getAttackMaxRange());
            outScoreRemoved = size - enemies.size();
        }
        int attackAlertRemoved = 0;
        if (maxAttackAlert < 5) {
            int size = enemies.size();
            enemies.removeIf(k -> k.getAlert_level() > maxAttackAlert);
            attackAlertRemoved = size - enemies.size();
        }
        int spellRemoved = 0;
        if (maxSpellAlert < 5) {
            int size = enemies.size();
            enemies.removeIf(k -> k.getSpell_alert() > maxSpellAlert);
            spellRemoved = size - enemies.size();
        }
        StringBuilder errors = new StringBuilder();
        Map<DBKingdom, DBSpy> spyReport = new HashMap<>();
        List<Map.Entry<DBKingdom, Integer>> enemyStrength = new ArrayList<>();
        for (DBKingdom enemy : enemies) {
            DBSpy spy = enemy.getLatestSpyReport();
            if (spy == null) {
                errors.append("No spy report for " + enemy.getUrl(myKingdom.getSlug()) + "\n");
                continue;
            }
            spyReport.put(enemy, spy);
            enemyStrength.add(Map.entry(enemy, spy.defense));
        }
        int strongerRemoved = 0;
        int myAttStr = myKingdom.getAttackStrength();
        if (!includeStronger) {
            int size = enemyStrength.size();
            enemyStrength.removeIf(k -> k.getValue() > myAttStr);
            strongerRemoved = size - enemyStrength.size();
        }
        if (enemyStrength.isEmpty()) {
            if (outScoreRemoved > 0) errors.append(outScoreRemoved + " enemies removed because of score range.\n");
            if (attackAlertRemoved > 0) errors.append(attackAlertRemoved + " enemies removed because of attack alert.\n");
            if (spellRemoved > 0) errors.append(spellRemoved + " enemies removed because of spell alert.\n");
            if (strongerRemoved > 0) errors.append(strongerRemoved + " enemies removed because of defense strength. (spy them again)\n");
            errors.append("No enemies found. Maybe try `/damage`");
        }

        StringBuilder response = new StringBuilder("## __" + myKingdom.getName() + " | attack: " + myAttStr + "__\n");
        numResults = Math.min(numResults, enemyStrength.size());
        for (int i = 0; i < numResults; i++) {
            Map.Entry<DBKingdom, Integer> result = enemyStrength.get(i);
            DBKingdom kingdom = result.getKey();
            response.append(kingdom.getInfoRowMarkdown(myKingdom.getSlug(), !excludeWarUnitEstimate));
            response.append("\n\n");
        }
        if (errors.length() > 0) {
            response.append("\n\nErrors:\n" + errors);
        }

        return response.toString();




    }
}
