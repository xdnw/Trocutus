package link.locutus.command.binding.validator;

import link.locutus.command.binding.ValueStore;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.ParameterData;

public interface Validator<T> {
    T validate(CommandCallable callable, ParameterData param, ValueStore store, T value);
}
