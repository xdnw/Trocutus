package link.locutus.core.api;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.settings.Settings;
import link.locutus.util.FileUtil;
import link.locutus.util.PagePriority;
import link.locutus.util.TrounceUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.CookieManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

public class Auth {

    public static void main(String[] args) throws IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
//        Auth auth = new Auth(664156861033086987L, "test", "blah");
        Auth auth = new Auth(664156861033086987L, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        auth.login(true);
    }
    private final String password;
    private final String username;
    private final long userId;
    private CookieManager msCookieManager = new CookieManager();

    private boolean loggedIn = false;

    public Auth(long userId, String username, String password) {
        this.userId = userId;
        // set fields
        this.username = username;
        this.password = password;
    }

    public long getUserId() {
        return userId;
    }

    public DBKingdom getKingdom(int realmId) {
        for (DBKingdom kingdom : Trocutus.imp().getDB().getKingdomFromUser(getUserId())) {
            if (kingdom.getRealm_id() == realmId) return kingdom;
        }
        return null;
    }

    public synchronized CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments, boolean post) throws IOException {
        synchronized (this)
        {
            login(false);
            return FileUtil.readStringFromURL(priority, urlStr, arguments, post, msCookieManager, i -> {});
            // todo check if logged out
        }
    }

    public void login(boolean force) throws IOException {
        if (!force && loggedIn) return;

        synchronized (this)
        {
            // load a random page to get the csrf1
            String csrf1 = FileUtil.get(FileUtil.readStringFromURL(PagePriority.LOGIN.ordinal(), "https://trounced.net/sanctum/csrf-cookie", (byte[]) null, FileUtil.RequestType.GET, this.getCookieManager(), i -> {}));

            Map<String, String> userPass = new HashMap<>();
            userPass.put("email", this.getUsername());
            userPass.put("password", this.getPassword());
            userPass.put("remember", "on");
            String url = "https://trounced.net/login";

            String loginResult = FileUtil.get(FileUtil.readStringFromURL(PagePriority.LOGIN.ordinal(), url, userPass, this.getCookieManager()));
            if (loginResult.contains("<meta http-equiv=\"refresh\" content=\"0;url='https://trounced.net/dashboard'\" />")) {
                loggedIn = true;
                return;
            } else {
                Document dom = Jsoup.parse(loginResult);
                String userTrunc = getUsername();
                if (userTrunc.contains("@")) {
                    userTrunc = getUsername().charAt(0) + "..." + getUsername().split("@")[1];
                }
                boolean containsLogin = loginResult.contains("<meta http-equiv=\"refresh\" content=\"0;url='https://trounced.net/login'\" />");
                throw new IllegalArgumentException("Error: Failed to login `" + userTrunc + "`\n" + TrounceUtil.getAlert(dom) + "\nRedirect: " + containsLogin);
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String logout() throws IOException {
        String logout = FileUtil.readStringFromURL("https://trounced.net/logout");
        Document dom = Jsoup.parse(logout);
        clearCookies();
        return TrounceUtil.getAlert(dom);
    }

    public void clearCookies() {
        msCookieManager.getCookieStore().removeAll();
    }

    public CookieManager getCookieManager() {
        return msCookieManager;
    }

    public String sendMail(int sender_id, DBKingdom kingdom, String subject, String message) throws IOException {
        String url = "https://trounced.net/mail";
        Map<String, String> data = new HashMap<>();
        data.put("kingdom_id", String.valueOf(sender_id));
        data.put("destination", "kingdom");
        data.put("destination_id", String.valueOf(kingdom.getId()));
        data.put("subject", subject);
        data.put("body", message);
        String result = FileUtil.get(FileUtil.readStringFromURL(PagePriority.MAIL.ordinal(), url, data, this.getCookieManager()));
        Document dom = Jsoup.parse(result);
        return TrounceUtil.getAlert(dom);
    }
}
