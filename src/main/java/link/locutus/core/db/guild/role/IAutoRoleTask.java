package link.locutus.core.db.guild.role;

import link.locutus.core.db.entities.kingdom.DBKingdom;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IAutoRoleTask {
    void autoRoleAll(Consumer<String> output);

    void autoRole(Member member, Consumer<String> output);

    void syncDB();
}
