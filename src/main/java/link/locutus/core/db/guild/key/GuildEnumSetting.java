package link.locutus.core.db.guild.key;

import link.locutus.command.binding.BindingHelper;
import link.locutus.core.db.guild.GuildDB;

public abstract class GuildEnumSetting<T extends Enum> extends GuildSetting<T> {
    private final Class<T> t;

    public GuildEnumSetting(GuildSettingCategory category, Class<T> t) {
        super(category, t);
        this.t = t;
    }

    @Override
    public String toString(T value) {
        return value.name();
    }

    @Override
    public T parse(GuildDB db, String input) {
        try {
            return BindingHelper.emum(t, input);
        } catch (IllegalArgumentException ignore) {}
        return super.parse(db, input);
    }
}
