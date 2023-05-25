package link.locutus.core.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fizzed.rocker.runtime.PrimitiveCollections;
import link.locutus.core.api.pojo.Api;
import link.locutus.core.db.TrouncedDB;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class TrouncedApi {
    private final String url;
    private final ApiKeyPool pool;
    private final ObjectMapper mapper;

    public TrouncedApi(ApiKeyPool pool) {
        this( "https://trounced.net/api/kingdom", pool);
    }
    public TrouncedApi(String url, ApiKeyPool pool) {
        this.url = url;
        this.pool = pool;
        // jackson object mapper
        this.mapper = new ObjectMapper();
    }

    public Set<Api.Kingdom> fetchData() throws JsonProcessingException {
        Set<Api.Kingdom> kingdoms = new LinkedHashSet<>();

        for (ApiKeyPool.ApiKey key : pool.getKeys()) {
            Api.Kingdom kingdom = fetchData(key);
            if (kingdom != null) {
                kingdoms.add(kingdom);
            }
        }
        return kingdoms;
    }

    private Api.Kingdom fetchData(ApiKeyPool.ApiKey key) throws JsonProcessingException {
        String urlFull = url + "?token=" + key;

        // read url using jackson
        Api.Kingdom kingdom = null;
        return mapper.readValue(urlFull, Api.Kingdom.class);
    }

}
