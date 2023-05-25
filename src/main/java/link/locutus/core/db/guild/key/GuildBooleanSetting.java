package link.locutus.core.db.guild.key;


import link.locutus.command.binding.Key;

public abstract class GuildBooleanSetting extends GuildSetting<Boolean> {
    public GuildBooleanSetting(GuildSettingCategory category) {
        super(category, Key.of(Boolean.class));
    }

    @Override
    public final String toString(Boolean value) {
        return value.toString();
    }
}
