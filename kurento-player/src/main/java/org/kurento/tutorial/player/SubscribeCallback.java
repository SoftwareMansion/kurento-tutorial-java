package org.kurento.tutorial.player;

import org.json.JSONObject;

public interface SubscribeCallback {
    void onShouldSubscribe(JSONObject stream);
}
