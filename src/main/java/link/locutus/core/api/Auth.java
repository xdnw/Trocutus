package link.locutus.core.api;

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
    private CookieManager msCookieManager = new CookieManager();

    private boolean loggedIn = false;

    public Auth(String username, String password) {
        // set fields
        this.username = username;
        this.password = password;
    }

    public void test() throws IOException {
        login(false);

        String data = loadSelf();
        System.out.println(data);
    }

    public String loadSelf() throws IOException {
        String url = "https://trounced.net/dashboard";
        String html = readStringFromURL(url, Collections.emptyMap(), false);
        Document doc = Jsoup.parse(html);
        String data = doc.getElementById("app").attr("data-page");
        // html unescape
        data = URLDecoder.decode(data);
        return data;
    }

    public DBKingdom getKingdom(int realmId) {

    }

    public String readStringFromURL(String urlStr, Map<String, String> arguments, boolean post) throws IOException {
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
}
