package link.locutus.core.db.entities.kingdom;

import link.locutus.command.binding.Key;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.bindings.Placeholders;
import link.locutus.command.binding.validator.ValidatorStore;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.CommandUsageException;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.perm.PermissionHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class KingdomPlaceholders extends Placeholders<DBKingdom> {
    private final Map<String, KingdomAttribute> customMetrics = new HashMap<>();

    public KingdomPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBKingdom.class, new DBKingdom(), store, validators, permisser);
    }

    public List<KingdomAttribute> getMetrics(ValueStore store) {
        List<KingdomAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            try {
                Map.Entry<Type, Function<DBKingdom, Object>> typeFunction = getPlaceholderFunction(store, id);
                if (typeFunction == null) continue;

                KingdomAttribute metric = new KingdomAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getKey(), typeFunction.getValue());
                result.add(metric);
            } catch (IllegalStateException | CommandUsageException ignore) {
                continue;
            }
        }
        return result;
    }

    public KingdomAttributeDouble getMetricDouble(ValueStore<?> store, String id) {
        return getMetricDouble(store, id, false);
    }

    public KingdomAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBKingdom, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;
        return new KingdomAttribute<>(id, "", typeFunction.getKey(), typeFunction.getValue());
    }

    public Map.Entry<Type, Function<DBKingdom, Object>> getTypeFunction(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBKingdom, Object>> typeFunction;
        try {
            typeFunction = getPlaceholderFunction(store, id);
        } catch (CommandUsageException ignore) {
            return null;
        } catch (Exception ignore2) {
            if (!ignorePerms) throw ignore2;
            return null;
        }
        return typeFunction;
    }

    public KingdomAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(getCmd(id));
        if (cmd == null) return null;
        Map.Entry<Type, Function<DBKingdom, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;

        Function<DBKingdom, Object> genericFunc = typeFunction.getValue();
        Function<DBKingdom, Double> func;
        Type type = typeFunction.getKey();
        if (type == int.class || type == Integer.class) {
            func = nation -> ((Integer) genericFunc.apply(nation)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = nation -> (Double) genericFunc.apply(nation);
        } else if (type == short.class || type == Short.class) {
            func = nation -> ((Short) genericFunc.apply(nation)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = nation -> ((Byte) genericFunc.apply(nation)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = nation -> ((Long) genericFunc.apply(nation)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = nation -> ((Boolean) genericFunc.apply(nation)) ? 1d : 0d;
        } else {
            return null;
        }
        return new KingdomAttributeDouble(cmd.getPrimaryCommandId(), cmd.simpleDesc(), func);
    }

    public List<KingdomAttributeDouble> getMetricsDouble(ValueStore store) {
        List<KingdomAttributeDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            KingdomAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, KingdomAttribute> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            KingdomAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    public String format(ValueStore<?> store, String arg) {
        User author = store.getProvided(Key.of(User.class, Me.class));
        DBKingdom me = store.getProvided(Key.of(DBKingdom.class, Me.class));

        if (author != null && arg.contains("%user%")) {
            arg = arg.replace("%user%", author.getAsMention());
        }

        return format(arg, 0, new Function<String, String>() {
            @Override
            public String apply(String placeholder) {
                KingdomAttribute result = KingdomPlaceholders.this.getMetric(store, placeholder, false);
                if (result != null) {
                    Object obj = result.apply(me);
                    if (obj != null) {
                        return obj.toString();
                    }
                }
                return placeholder;
            }
        });
    }
}