package link.locutus.core.api;

import link.locutus.Trocutus;
import link.locutus.core.db.guild.GuildKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApiKeyPool {
    private final List<ApiKey> apiKeyPool;
    private int nextIndex;

    public ApiKeyPool(Collection<ApiKey> keys) {
        this.apiKeyPool = new ArrayList<>(keys);
        this.nextIndex = 0;
        if (apiKeyPool.size() == 0) {
            throw new IllegalStateException("No API Key provided, Make sure apiKeyPool array is not empty.");
        }
    }

    public static SimpleBuilder builder() {
        return new SimpleBuilder();
    }

    public static ApiKeyPool create(int nationId, String key) {
        return builder().addKey(nationId, key).build();
    }

    public static ApiKeyPool create(ApiKey key) {
        return builder().addKey(key).build();
    }

    public List<ApiKey> getKeys() {
        return apiKeyPool;
    }

    public synchronized ApiKey getNextApiKey() {
        if (this.nextIndex >= this.apiKeyPool.size()) {
            this.nextIndex = 0;
        }
        if (this.apiKeyPool.isEmpty())
            throw new IllegalArgumentException("No API key found." + "`)");
        ApiKey key = this.apiKeyPool.get(this.nextIndex++);
        key.use();
        return key;
    }

    public synchronized void removeKey(ApiKey key) {
        key.setValid(false);
        if (apiKeyPool.size() == 1) throw new IllegalArgumentException("Invalid API key.");
        this.apiKeyPool.removeIf(f -> f.equals(key));
    }

    public int size() {
        return apiKeyPool.size();
    }

    public static class ApiKey {
        private final String key;
        private int nationId;
        private boolean valid;
        private int usage;

        public ApiKey(int nationId, String key) {
            this.nationId = nationId;
            this.key = key;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public void deleteApiKey() {
            setValid(false);
            Trocutus.imp().getDB().deleteApiKey(key);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ApiKey)) return false;
            return ((ApiKey) obj).key.equalsIgnoreCase(key);
        }

        public ApiKey use() {
            usage++;
            return this;
        }

        public int getUsage() {
            return usage;
        }

        public String getKey() {
            return key;
        }


        public int getKingdomId() {
            if (nationId == -1) {
                nationId = Trocutus.imp().getDB().getKingdomFromApiKey(key);
            }
            return nationId;
        }
    }

    public static class SimpleBuilder {
        private final Map<String, ApiKey> keys = new LinkedHashMap<>();


        public boolean isEmpty() {
            return keys.isEmpty();
        }


        @Deprecated
        public SimpleBuilder addKeyUnsafe(String key) {
            return addKey(-1, key);
        }

        @Deprecated
        public SimpleBuilder addKeysUnsafe(String... keys) {
            for (String key : keys) addKeyUnsafe(key);
            return this;
        }

        public SimpleBuilder addKeys(List<String> keys) {
            for (String key : keys) addKeyUnsafe(key);
            return this;
        }

        public SimpleBuilder addKey(int nationId, String apiKey) {
            ApiKey key = new ApiKey(nationId, apiKey);
            apiKey = apiKey.toLowerCase(Locale.ROOT);
            ApiKey existing = this.keys.get(apiKey);
            if (existing != null) return this;

            this.keys.put(apiKey, key);
            return this;
        }

        public ApiKeyPool build() {
            if (keys.isEmpty()) throw new IllegalArgumentException("No api keys were provided.");
            return new ApiKeyPool(keys.values());
        }

        public SimpleBuilder addKey(ApiKey key) {
            return addKey(key.nationId, key.key);
        }
    }
}
