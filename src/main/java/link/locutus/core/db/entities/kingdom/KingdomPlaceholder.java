package link.locutus.core.db.entities.kingdom;

import java.util.function.Function;

public interface KingdomPlaceholder<T> extends Function<DBKingdom,T> {
    String getName();

    Class<T> getType();
}
