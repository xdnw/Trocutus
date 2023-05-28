package link.locutus.util;

import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CounterGenerator {

    public static List<DBKingdom> generateCounters(GuildDB db, DBKingdom enemy, List<DBKingdom> attackersSorted) {
        return generateCounters(db, enemy, attackersSorted, true, true);
    }

    public static List<DBKingdom> generateCounters(GuildDB db, DBKingdom enemy, List<DBKingdom> attackersSorted, boolean filter, boolean filterWeak) {
        if (filter) {
            if (filterWeak) {
                attackersSorted.removeIf(f -> f.getAttackStrength() < enemy.getDefenseStrength());
            }
            attackersSorted.removeIf(f -> !f.isInSpyRange(enemy));
            attackersSorted.removeIf(f -> f.getActive_m() > 4880);
            attackersSorted.removeIf(f -> f.isVacation());
            attackersSorted.removeIf(f -> f.getPosition().ordinal() <= 1);
            attackersSorted.removeIf(f -> f.getRealm_id() != enemy.getRealm_id());
        }

        int enemyStr = enemy.getDefenseStrength();

        Map<DBKingdom, Double> factors = new HashMap<>();

        for (DBKingdom att : attackersSorted) {
            double activeFactor = att.isOnline() ? 1.2 : att.getActive_m() < 1440 ? 1 : (1 - (att.getActive_m() - 1440d) / 4880);
            factors.put(att, activeFactor * att.getAttackStrength());
        }

        attackersSorted.sort((o1, o2) -> Double.compare(factors.get(o2), factors.get(o1)));
        return attackersSorted;
    }
}
