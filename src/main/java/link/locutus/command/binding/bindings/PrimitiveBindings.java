package link.locutus.command.binding.bindings;

import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.*;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.command.ParameterData;
import link.locutus.util.MathMan;
import link.locutus.util.ScriptUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;

import java.awt.Color;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class PrimitiveBindings extends BindingHelper {

    /**
     * Gets a type from a {@link Binding}.
     *
     * @param argument the context
     * @return the requested type
     * @throws IllegalArgumentException on error
     */
    @Binding(examples = {"true", "false"}, types = {boolean.class})
    public static Boolean Boolean(String argument) {
        return switch (argument.toLowerCase(Locale.ROOT)) {
            case "" -> null;
            case "true", "yes", "on", "y", "1", "t" -> true;
            case "false", "no", "off", "f", "n", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean " + argument);
        };
    }

    @Binding(examples = {"1,2,5"})
    public static Set<Integer> integerSet(String argument) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String arg : StringMan.split(argument, ',')) {
            ids.add(Integer(arg));
        }
        return ids;
    }

    @Binding(examples = "hello", value = "A single line of text")
    public static String String(String input, @Default ParameterData param) {
        Filter annotation = param == null ? null : param.getAnnotation(Filter.class);
        if (annotation != null && !input.matches(annotation.value())) {
            throw new IllegalArgumentException("Input: `" + input + "` does not match: `" + annotation.value() + "`");
        }
        ArgChoice choice = param == null ? null : param.getAnnotation(ArgChoice.class);
        if (choice != null) {
            String[] choices = choice.value();
            for (String s : choices) {
                if (choice.caseSensitive() ? s.equals(input) : s.equalsIgnoreCase(input)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Input: `" + input + "` is not a valid choice. Valid choices are: " + StringMan.join(choices, ", "));
        }
        return input;
    }

    /**
     * Try to parse numeric input as either a number or a mathematical expression.
     *
     * @param input input
     * @return a number
     * @throws IllegalArgumentException thrown on parse error
     */
    @Binding(types = {double.class}, examples = {"3.0", "3*4.5-6/2", "50.3m"}, value = "A decimal number")
    public static Double Double(String input) {
        return Number(input).doubleValue();
    }

    @Binding(types = {int.class}, examples = {"3", "3*4-6/2", "50.3m"}, value = "A whole number")
    public static Integer Integer(String input) {
        return Number(input).intValue();
    }

    @Binding(examples = {"#420420"}, value = "A color name or hex code")
    public static Color color(String input) {
        if (input.charAt(0) == '#') return Color.decode(input);
        try {
            return (Color) Color.class.getField(input.toUpperCase()).get(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new IllegalArgumentException("Invalid color: `" + input + "`");
        }
    }

    @Binding(types = {long.class}, examples = {"3", "3*4-6/2", "50.3k"}, value = "A whole number")
    public static Long Long(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignore) {
        }
        return Number(input).longValue();
    }

    @Binding(examples = {"3.0", "3.2*4-6/2", "50.3k"}, value = "A decimal number")
    public static Number Number(String input) {
        try {
            Double parsed = MathMan.parseDouble(input);
            if (parsed != null) return parsed;
        } catch (NumberFormatException ignored) {
        }
        try {
            Object result = ScriptUtil.getEngine().eval(input);
            if (result instanceof Boolean) return ((Boolean) result) ? 1 : 0;
            return (Number) result;
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "Expected '%s' to be a number or valid math expression (error: %s)", input, e.getMessage()));
        }
    }

    @Binding
    public ArgumentStack stack() {
        throw new IllegalStateException("No ArgumentStack set in command locals.");
    }

    @Binding(examples = {"a b c"}, value = "Multiple words or text separated by spaces")
    public List<String> all(@TextArea String string) {
        List<String> result = new ArrayList<>(StringMan.split(string, ' '));
        // remove starting and ending quote if exist
        for (int i = 0; i < result.size(); i++) {
            String s = result.get(i);
            if (s.length() <= 2) continue;
             if (StringMan.isQuote(s.charAt(0)) && StringMan.isQuote(s.charAt(s.length() - 1))) {
                result.set(i, s.substring(1, s.length() - 1));
            }
        }
        return result;
    }

    @Binding(examples = {"8-4-4-4-12"}, value = "Universally Unique IDentifier")
    public UUID uuid(String argument) {
        return UUID.fromString(argument);
    }

    @Timediff
    @Binding(types = {long.class}, examples = {"5d", "1w10h3m25s", "timestamp:1682013943000"}, value = "A time difference or unix timestamp which will resolve as a difference relative to the current date")
    public static Long timediff(String argument) {
        return TimeUtil.timeToSec(argument) * 1000;
    }

    @Timestamp
    @Binding(types={long.class}, examples = {"5d", "1w10h3m25s", "dd/MM/yyyy", "timestamp:1682013943000"}, value = "A unix timestamp, a DMY date or a time difference that will resolve to a timestamp from the current date")
    public static Long timestamp(String argument) throws ParseException {
        if (argument.equalsIgnoreCase("%epoch%")) {
            return 0L;
        }
        if (argument.contains("/")) {
            long time = 0;
            long date = 0;
            String[] split = argument.split("/");
            if (split.length == 3) {
                if (split[2].length() == 2) {
                    return TimeUtil.parseDate(TimeUtil.DD_MM_YY, argument, false);
                } else {
                    return TimeUtil.parseDate(TimeUtil.DD_MM_YYYY, argument, false);
                }
            } else {
                throw new IllegalArgumentException("Invalid time format: " + argument);
            }
        }
        if (argument.length() == 10 && argument.charAt(4) == '-' && argument.charAt(7) == '-') {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date parsed = format.parse(argument);
            return parsed.getTime();
        }
        return System.currentTimeMillis() - TimeUtil.timeToSec(argument) * 1000;
    }
}
