package link.locutus.command.binding.bindings.serializer;

import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.command.binding.annotation.Serializer;

public class PrimitiveSerializer extends BindingHelper {

    @Binding(types = {
            int.class,
            Integer.class,
    })
    @Serializer
    public String serializeInt(Object input) {
        return input.toString();
    }
}
