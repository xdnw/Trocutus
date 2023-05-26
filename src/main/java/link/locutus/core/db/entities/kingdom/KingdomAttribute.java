package link.locutus.core.db.entities.kingdom;


import link.locutus.command.binding.Attribute;

import java.lang.reflect.Type;
import java.util.function.Function;

public class KingdomAttribute<T> implements Attribute<DBKingdom, T> {
    private final Function<DBKingdom, T> parent;
    private final Type type;
    private final String name;
    private final String desc;

    public KingdomAttribute(String name, String desc, Type type, Function<DBKingdom, T> parent) {
        this.type = type;
        this.parent = parent;
        this.name = name;
        this.desc = desc;
    }


    public String getName() {
        return this.name;
    }

    public Type getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public T apply(DBKingdom nation) {
        return parent.apply(nation);
    }
}