package link.locutus.core.db.entities.kingdom;

import java.lang.reflect.Type;
import java.util.function.Function;

public class SimpleKingdomPlaceholder<T> implements KingdomPlaceholder<T> {
    private final String name;
    private final Type type;
    private final Function<DBKingdom, T> func;

    public SimpleKingdomPlaceholder(String name, Type type, Function<DBKingdom, T> func) {
        this.name = name;
        this.type = type;
        this.func = func;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getType() {
        return (Class<T>) type;
    }

    @Override
    public T apply(DBKingdom nation) {
        return (T) func;
    }
}

