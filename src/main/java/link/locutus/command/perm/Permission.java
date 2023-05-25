package link.locutus.command.perm;

import link.locutus.command.binding.ValueStore;

public interface Permission {
    boolean test(ValueStore store);
}
