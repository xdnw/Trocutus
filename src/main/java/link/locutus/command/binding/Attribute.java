package link.locutus.command.binding;

import link.locutus.util.MathMan;
import link.locutus.util.StringMan;

import java.lang.reflect.Type;
import java.util.function.Function;

public interface Attribute<T, R> extends Function<T, R> {
    R apply(T nation);

    default String toString(R value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return MathMan.format((Number) value);
        }
        return StringMan.getString(value);
    }

    String getName();

    Type getType();

    String getDesc();
}
