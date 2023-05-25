package link.locutus.command.binding.bindings;

import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Range;

public class PrimitiveValidators extends BindingHelper {
    @Binding(types = {Integer.class})
    @Range
    public static int intRange(int input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }

    @Binding(types = {Double.class})
    @Range
    public static double doubleRange(double input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }

    @Binding(types = {Long.class})
    @Range
    public static long longRange(long input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }
}