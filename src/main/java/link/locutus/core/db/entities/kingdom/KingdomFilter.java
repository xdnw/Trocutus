package link.locutus.core.db.entities.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

import java.util.function.Predicate;

public interface KingdomFilter extends Predicate<DBKingdom> {
    String getFilter();

    default Predicate<DBKingdom> toCached(long expireAfter) {
        return this;
    }
}
