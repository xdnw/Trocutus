package link.locutus.command.binding;

import link.locutus.command.binding.validator.ValidatorStore;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.perm.PermissionHandler;

import java.util.ArrayList;
import java.util.Arrays;

public interface Parser<T> {
    T apply(ArgumentStack arg);

    T apply(ValueStore store, Object t);

    boolean isConsumer(ValueStore store);

    Key getKey();

    String getDescription();

    default T apply(ValueStore store, ValidatorStore validators, PermissionHandler permisser, String... args) {
        ArgumentStack stack = new ArgumentStack(new ArrayList<>(Arrays.asList(args)), store, validators, permisser);
        return apply(stack);
    }
}
