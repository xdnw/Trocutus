package link.locutus.core.event;

import link.locutus.core.db.guild.GuildDB;

public abstract class GuildScopeEvent extends Event {

    public final void post(GuildDB db) {
        if (db != null) db.postEvent(this);
    }

    protected abstract void postToGuilds();

    @Override
    public void post() {
        super.post();
        postToGuilds();
    }
}
