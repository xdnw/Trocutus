package link.locutus.core.db.entities;

import java.util.LinkedHashSet;
import java.util.Map;
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
}
