package link.locutus.core.db.guild.key;

import link.locutus.command.binding.Key;
import link.locutus.util.MathMan;

public abstract class GuildLongSetting extends GuildSetting<Long> {
    public GuildLongSetting(GuildSettingCategory category) {
        this(category, null);
    }
    public GuildLongSetting(GuildSettingCategory category, Class annotation) {
        super(category, annotation != null ? Key.of(Long.class, annotation) : Key.of(Long.class));
    }

    @Override
    public String toReadableString(Long value) {
        return MathMan.format(value);
    }

    @Override
    public final String toString(Long value) {
        return value.toString();
    }
}
