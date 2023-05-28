package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RankPermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.TrouncedApi;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.pojo.Api;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public class CredentialCommands {
    @Command(desc="Set your api and bot key for the bot\n" +
            "Your API key can be found on the account page: <https://politicsandwar.com/account/>\n" +
            "See: <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to get a bot key")
    public String addApiKey(@Me Map<DBRealm, DBKingdom> me, @Me IMessageIO io, @Me JSONObject command, String apiKey) throws IOException {
        try {
            IMessageBuilder msg = io.getMessage();
            if (msg != null) io.delete(msg.getId());
        } catch (Throwable ignore) {}
        ApiKeyPool.ApiKey key = new ApiKeyPool.ApiKey(-1, apiKey);
        Api.Kingdom stats = new TrouncedApi(ApiKeyPool.create(key)).fetchData(key);

        // find me kingdom matching stats
        DBKingdom first = me.values().stream().filter(k -> k.getName().equalsIgnoreCase(stats.name) && k.getAlliance_id() == stats.alliance.id).findFirst()
                .orElseThrow(() -> new RuntimeException("You are not registered to that kingdom"));

        Trocutus.imp().getDB().addApiKey(first.getId(), apiKey);

        return "Set api key for " + first.getUrl(first.getSlug());
    }

    @Command(desc="Login to allow the bot to run scripts through your account\n" +
            "(Avoid using this if possible)")
    @RankPermission(Rank.ADMIN)
    public static String login(@Me IMessageIO io,
                               @Me User author,
                               @Arg("Your username (i.e. email) for Politics And War")
                               String username,
                               String password) throws IOException {
        IMessageBuilder msg = io.getMessage();
        try {
            if (msg != null) io.delete(msg.getId());
        } catch (Throwable ignore) {};

        Auth auth = new Auth(author.getIdLong(), username, password);
        // test
        auth.login(true);

        Trocutus.imp().getDB().addUserPass(auth.getUserId(), username, password);

        return "Login successful.";
    }

    @Command(desc = "Remove your login details from the bot")
    public String logout(@Me Map<DBRealm, DBKingdom> me, @Me User author) {
        if (Trocutus.imp().getDB().getUserPass(author.getIdLong()) != null) {
            Trocutus.imp().getDB().logout(author.getIdLong());
            if (me != null) {
                Trocutus.imp().getDB().logout(author.getIdLong());
            }
            return "Logged out";
        }
        return "You are not logged in";
    }
}
