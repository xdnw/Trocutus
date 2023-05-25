package link.locutus.command.binding;

import link.locutus.command.command.ArgumentStack;

public class ProviderParser<T> implements Parser<T> {
    private final Key<T> key;
    private final T value;

    public ProviderParser(Key<T> key, T value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public T apply(ArgumentStack arg) {
        return value;
    }

    @Override
    public T apply(ValueStore store, Object t) {
        return value;
    }

    @Override
    public boolean isConsumer(ValueStore store) {
        return false;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return null;
    }
}
