package link.locutus.core.db.entities.kingdom;

import java.lang.reflect.Type;
import java.util.function.Function;

public class KingdomAttributeDouble extends KingdomAttribute<Double> {
    public KingdomAttributeDouble(String id, String desc, Function<DBKingdom, Double> parent) {
        super(id, desc, Double.TYPE, parent);
    }

    @Override
    public Type getType() {
        return Double.class;
    }

    @Override
    public Double apply(DBKingdom nation) {
        return super.apply(nation);
    }
}
