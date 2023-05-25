package link.locutus.core.db.guild.key;

import link.locutus.command.binding.Key;
import link.locutus.core.db.guild.GuildDB;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

public abstract class GuildCategorySetting extends GuildSetting<Category> {
    public GuildCategorySetting(GuildSettingCategory category) {
        super(category, Key.of(Category.class));
    }

    @Override
    public Category validate(GuildDB db, Category value) {
        return validateCategory(db, value);
    }

    @Override
    public final String toString(Category value) {
        return ((IMentionable) value).getAsMention();
    }
}
