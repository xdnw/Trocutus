package link.locutus.core.db.guild.role;

import link.locutus.core.db.entities.kingdom.DBKingdom;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DelegateAutoRoleTask implements IAutoRoleTask {
    private final IAutoRoleTask task;

    @Override
    public void autoRoleCities(Member member, Supplier<DBKingdom> nationSup, Consumer<String> output, Consumer<Future> tasks) {
        task.autoRoleCities(member, nationSup, output, tasks);
    }

    @Override
    public void autoRoleAll(Consumer<String> output) {
        task.autoRoleAll(output);
    }

    @Override
    public void autoRole(Member member, Consumer<String> output) {
        task.autoRole(member, output);
    }

    @Override
    public void syncDB() {
        task.syncDB();
    }

    public DelegateAutoRoleTask(IAutoRoleTask task) {
        this.task = task;
    }
}
