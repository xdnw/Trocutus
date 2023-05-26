package link.locutus.core.api;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.settings.Settings;
import link.locutus.util.FileUtil;
import link.locutus.util.TrounceUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Auth {
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

    public synchronized String readStringFromURL(String urlStr, Map<String, String> arguments, boolean post) throws IOException {
        synchronized (this)
        {
            login(false);
            String result = FileUtil.readStringFromURL(urlStr, arguments, post, msCookieManager, i -> {});
            if (result.contains("<!--Logged Out-->")) {
                logout();
                msCookieManager = new CookieManager();
                login(true);
                result = FileUtil.readStringFromURL(urlStr, arguments, post, msCookieManager, i -> {});
                if (result.contains("<!--Logged Out-->")) {
                    throw new IllegalArgumentException("Failed to login to Trounced");
                }
            }
            if (result.toLowerCase().contains("authenticate your request")) {
                new Exception().printStackTrace();
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return result;
        }
    }

    public void login(boolean force) throws IOException {
        if (!force && loggedIn) return;

        synchronized (this)
        {
            // load a random page to get the csrf1
            String csrf1 = FileUtil.readStringFromURL("https://trounced.net/sanctum/csrf-cookie", (byte[]) null, FileUtil.RequestType.GET, this.getCookieManager(), i -> {});

            Map<String, String> userPass = new HashMap<>();
            userPass.put("email", this.getUsername());
            userPass.put("password", this.getPassword());
            userPass.put("remember", "on");
            String url = "https://trounced.net/login";

            String loginResult = FileUtil.readStringFromURL(url, userPass, this.getCookieManager());
            if (!loginResult.contains("Redirecting to")) {
                Document dom = Jsoup.parse(loginResult);
                String userTrunc = getUsername();
                if (userTrunc.contains("@")) {
                    userTrunc = getUsername().charAt(0) + "..." + getUsername().split("@")[1];
                }
                throw new IllegalArgumentException("Error: Failed to login `" + userTrunc + "`\n" + TrounceUtil.getAlert(dom));
            }
            loggedIn = true;
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
        String result = FileUtil.readStringFromURL(url, data, this.getCookieManager());
        Document dom = Jsoup.parse(result);
        return TrounceUtil.getAlert(dom);
    }
}
