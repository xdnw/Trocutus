package link.locutus.command.binding.bindings;

import link.locutus.command.binding.Key;
import link.locutus.command.binding.LocalValueStore;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.validator.ValidatorStore;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.CommandGroup;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.perm.PermissionHandler;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringEscapeUtils;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class Placeholders<T> {
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandGroup commands;

    private final T nullInstance;
    private final Type instanceType;

    public Placeholders(Type type, T nullInstance, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.nullInstance = nullInstance;
        this.instanceType = type;

        this.validators = validators;
        this.permisser = permisser;

        this.commands = CommandGroup.createRoot(store, validators);
        this.commands.registerCommands(nullInstance);
        this.commands.registerCommands(this);
    }

    public Set<String> getKeys() {
        return commands.primarySubCommandIds();
    }

    public List<ParametricCallable> getParametricCallables() {
        List<ParametricCallable> result = new ArrayList<>();
        for (CommandCallable value : this.commands.getSubcommands().values()) {
            if (value instanceof ParametricCallable) {
                result.add((ParametricCallable) value);
            }
        }
        return result;
    }

    public ParametricCallable get(String cmd) {
        return (ParametricCallable) commands.get(cmd);
    }

    public String getHtml(ValueStore store, String cmd, String parentId) {
        ParametricCallable callable = get(cmd);
        StringBuilder html = new StringBuilder(callable.toBasicHtml(store));
        html.append("<select name=\"filter-operator\" for=\"").append(parentId).append("\" class=\"form-control\">");
        for (Operation value : Operation.values()) {
            String selected = value == Operation.EQUAL ? "selected=\"selected\"" : "";
            html.append("<option value=\"").append(value.code).append("\" ").append(selected).append(">").append(StringEscapeUtils.escapeHtml4(value.code)).append("</option>");
        }
        html.append("</select>");
        Type type = callable.getReturnType();
        if (type == String.class) {
            html.append("<input name=\"filter-value\" for=\"").append(parentId).append("\" required type=\"text\" class=\"form-control\"/>");
        } else if (type == boolean.class || type == Boolean.class) {
            html.append("<select name=\"filter-value\" for=\"").append(parentId).append("\" required class=\"form-control\" /><option>true</option><option>false</option></select>");
        } else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
            html.append("<input name=\"filter-value\" for=\"").append(parentId).append("\" required type=\"number\" class=\"form-control\"/>");
        } else {
            throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean.");
        }
        html.append("<button id=\"addfilter.submit\" for=\"").append(parentId).append("\" class=\"btn btn-primary\" >Add Filter</button>");

        return html.toString();
    }

    public List<CommandCallable> getPlaceholderCallables() {
        return new ArrayList<>(getParametricCallables());
    }

    public List<CommandCallable> getFilterCallables() {
        List<ParametricCallable> result = getParametricCallables();
        result.removeIf(cmd -> {
            Type type = cmd.getReturnType();
            return type != String.class && type != boolean.class && type != Boolean.class && type != int.class && type != Integer.class && type != double.class && type != Double.class && type != long.class && type != Long.class;
        });
        return new ArrayList<>(result);
    }

    public Predicate<T> getFilter(ValueStore store, String input) {
        int argEnd = input.lastIndexOf(')');

        for (Operation op : Operation.values()) {
            int index = input.lastIndexOf(op.code);
            if (index > argEnd) {
                String part1 = input.substring(0, index);
                String part2 = input.substring(index + op.code.length());

                Map.Entry<Type, Function<T, Object>> placeholder = getPlaceholderFunction(store, part1);
                Function<T, Object> func = placeholder.getValue();
                Type type = placeholder.getKey();
                Predicate adapter;

                if (type == String.class) {
                    adapter = op.getStringPredicate(part2);
                } else if (type == boolean.class || type == Boolean.class) {
                    boolean val2 = PrimitiveBindings.Boolean(part2);
                    adapter = op.getBooleanPredicate(val2);
                } else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
                    double val2 = MathMan.parseDouble(part2);
                    adapter = op.getNumberPredicate(val2);
                } else {
                    throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean");
                }

                return nation -> adapter.test(func.apply(nation));
            }
        }
        return null;
    }

    public String getCmd(String input) {
        int argStart = input.indexOf('(');
        return argStart != -1 ? input.substring(0, argStart) : input;
    }

    public String format(String line, int recursion, Function<String, String> formatPlaceholder) {
        try {
            int q = 0;
            List<Integer> indicies = null;
            for (int i = 0; i < line.length(); i++) {
                char current = line.charAt(i);
                if (current == '{') {
                    if (indicies == null) indicies = new ArrayList<>();
                    indicies.add(i);
                    q++;
                } else if (current == '}' && indicies != null) {
                    if (q > 0) {
                        if (recursion < 513) {
                            q--;
                            int lastindx = indicies.size() - 1;
                            int start = indicies.get(lastindx);
                            String arg = line.substring(start, i + 1);

                            Object result;
                            try {
                                System.out.println("Format `" + arg + "`");
                                result = formatPlaceholder.apply(arg.substring(1, arg.length() - 1));
                            } catch (Exception e) {
                                e.printStackTrace();
                                result = null;
                            }
                            if (result != null) {
                                line = new StringBuffer(line).replace(start, i + 1, result + "").toString();
                            }
                            indicies.remove(lastindx);
                            i = start;
                        }
                    }
                }
            }
            return line;
        } catch (Exception e2) {
            e2.printStackTrace();
            return "";
        }
    }

    public Map.Entry<Type, Function<T, Object>> getPlaceholderFunction(ValueStore store, String input) {
        List<String> args;
        int argStart = input.indexOf('(');
        int argEnd = input.lastIndexOf(')');
        String cmd;
        if (argStart != -1 && argEnd != -1) {
            cmd = input.substring(0, argStart);
            String argsStr = input.substring(argStart + 1, argEnd);
            args = StringMan.split(argsStr, ',');
        } else if (input.indexOf(' ') != -1) {
            args = StringMan.split(input, ' ');
            cmd = args.get(0);
            args.remove(0);
        } else {
            args = Collections.emptyList();
            cmd = input;
        }

        ParametricCallable cmdObj = (ParametricCallable) commands.get(cmd);
        if (cmdObj == null) return null;

        LocalValueStore locals = new LocalValueStore<>(store);
        locals.addProvider(Key.of(instanceType, Me.class), nullInstance);
        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);

        Object[] arguments = cmdObj.parseArguments(stack);
        int replacement = -1;
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == nullInstance) replacement = i;
        }

        Function<T, Object> func;

        if (replacement != -1) {
            int valIndex = replacement;
            func = new Function<>() {
                private volatile boolean useCopy = false;

                @Override
                public Object apply(T val) {
                    // create copy if used by multiple threads
                    if (useCopy) {
                        Object[] copy = arguments.clone();
                        copy[valIndex] = val;
                        return cmdObj.call(val, store, copy);
                    } else {
                        try {
                            useCopy = true;
                            arguments[valIndex] = val;
                            return cmdObj.call(val, store, arguments);
                        } finally {
                            useCopy = false;
                        }
                    }
                }
            };
        } else {
            func = obj -> cmdObj.call(obj, store, arguments);
        }
        return new AbstractMap.SimpleEntry<>(cmdObj.getReturnType(), func);
    }
}