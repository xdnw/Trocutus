package link.locutus.core.db.guild.key;

import link.locutus.command.binding.BindingHelper;
import link.locutus.core.db.guild.GuildDB;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class GuildEnumSetSetting<T extends Enum> extends GuildSetting<Set<T>> {
    private final Class<T> t;

    public GuildEnumSetSetting(GuildSettingCategory category, Class<T> t) {
        super(category, t);
        this.t = t;
    }

    @Override
    public String toString(Set<T> value) {
        return value.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<T> parse(GuildDB db, String input) {
        try {
            return BindingHelper.emumSet(t, input);
        } catch (IllegalArgumentException ignore) {}
        return super.parse(db, input);
    }
}
