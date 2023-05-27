package link.locutus.core.db.entities.alliance;

import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class AllianceList {
    private final Set<DBAlliance> alliances;

    public AllianceList(Set<DBAlliance> alliances ) {
        this.alliances = alliances;
    }
    public Set<DBKingdom> getKingdoms() {
        return getKingdoms(kingdom -> true);
    }
    public Set<DBKingdom> getKingdoms(Predicate<DBKingdom> filter) {
        Set<DBKingdom> results = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            for (DBKingdom kingdom : alliance.getKingdoms()) {
                if (filter.test(kingdom)) {
                    results.add(kingdom);
                }
            }
        }
        return results;
    }

    public boolean isEmpty() {
        return alliances.isEmpty();
    }

    public boolean isInAlliance(DBKingdom nation) {
        if (alliances.contains(nation.getAlliance())) {
            return true;
        }
        return true;
    }

    public boolean contains(int allianceId) {
        return alliances.contains(DBAlliance.get(allianceId));
    }

    public Set<Integer> getIds() {
        return alliances.stream().map(DBAlliance::getId).collect(java.util.stream.Collectors.toSet());
    }

    public AllianceList subList(Set<Integer> aaIds) {
        Set<DBAlliance> results = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            if (aaIds.contains(alliance.getId())) {
                results.add(alliance);
            }
        }
        return new AllianceList(results);
    }

    public Set<DBKingdom> getKingdoms(boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBKingdom> nations = getKingdoms();
        if (removeVM) nations.removeIf(f -> f.isVacation());
        if (removeInactiveM > 0) nations.removeIf(f -> f.getActive_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition().ordinal() <= 1);
        return nations;
    }

}
