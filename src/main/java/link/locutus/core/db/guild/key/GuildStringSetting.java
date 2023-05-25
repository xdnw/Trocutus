package link.locutus.core.db.guild.key;

import link.locutus.command.binding.Key;
import link.locutus.core.db.guild.GuildDB;

public abstract class GuildStringSetting extends GuildSetting<String> {
    public GuildStringSetting(GuildSettingCategory category) {
        super(category, Key.of(String.class));
    }

    @Override
    public String parse(GuildDB db, String input) {
        return super.parse(db, input);
    }

    @Override
    public final String toString(String value) {
        return value.replace("\\n", "\n");
    }
}
