package link.locutus.core.api;

import link.locutus.core.db.entities.DBKingdom;

import java.io.IOException;

public class Auth {
    private final String password;
    private final String username;

    public Auth(String username, String password) {
        // set fields
        this.username = username;
        this.password = password;
    }

    public ApiKeyPool.ApiKey fetchApiKey() {
        return null;
    }

    public DBKingdom getKingdom(int realmId) {
        return null;
    }

    public String readStringFromURL(String url) throws IOException {
        return null;
    }
}
