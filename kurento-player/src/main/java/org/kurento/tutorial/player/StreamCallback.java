package org.kurento.tutorial.player;

import org.json.JSONObject;

public interface StreamCallback {
    void onSdpAnswer(JSONObject msg);
    void onStreamStarted(Long sid);
    void onStreamReady();
    void onRemoveStream();
}
