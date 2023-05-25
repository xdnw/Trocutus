package link.locutus.core.db.guild.key;

import link.locutus.command.binding.Key;

public abstract class GuildIntegerSetting extends GuildSetting<Integer> {
    public GuildIntegerSetting(GuildSettingCategory category) {
        super(category, Key.of(Integer.class));
    }

    @Override
    public final String toString(Integer value) {
        return value.toString();
    }
}
