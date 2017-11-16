package org.kurento.tutorial.player;

import org.kurento.client.KurentoClient;

import java.util.HashMap;
import java.util.Map;

public class KurentoClientManager {
    private final Map<String, KurentoClient> _cache = new HashMap<>();

    public KurentoClient getKurentoClient(String websocketUrl) {
        if (!_cache.containsKey(websocketUrl)) {
            final KurentoClient client = KurentoClient.create(websocketUrl);
            _cache.put(websocketUrl, client);
            return client;
        }

        return _cache.get(websocketUrl);
    }
}
