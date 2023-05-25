package link.locutus.command.binding.bindings.autocomplete;

import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.*;
import link.locutus.command.command.ParameterData;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PrimitiveCompleter extends BindingHelper {

    @Autoparse
    @Binding(types = {
            UUID.class,
    })
    public static void uuid(String input) {
    }

    @Autoparse
    @Binding(types = String.class)
    public static List<String> String(ParameterData param, String input) {
        ArgChoice choice = param.getAnnotation(ArgChoice.class);
        if (choice != null) {
            String[] choices = choice.value();
            return StringMan.getClosest(input, Arrays.asList(choices), OptionData.MAX_CHOICES, true);
        }
        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            if (!input.matches(filter.value())) {
                throw new IllegalArgumentException("Invalid match for " + filter.value());
            }
        }
        return null;
    }

    @Autocomplete
    @Binding(types = {Color.class})
    public static List<String> color(String input) {
        if (input.isEmpty()) return null;

        List<String> colors = new ArrayList<>();

        List<Map.Entry<String, Double>> distances = new ArrayList<>();

        for (Field field : Color.class.getDeclaredFields()) {
            if (field.getType() == Color.class) {
                String name = field.getName();
                if (!Character.isUpperCase(name.charAt(0))) continue;
                colors.add(name);
            }
        }

        return StringMan.getClosest(input, colors, OptionData.MAX_CHOICES, true);
    }
}
