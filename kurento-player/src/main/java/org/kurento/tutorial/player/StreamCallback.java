package org.kurento.tutorial.player;

import org.json.JSONObject;

public interface StreamCallback {
    void onSdpOffer(JSONObject msg);
    void onSdpAnswer(JSONObject msg);
    void onIceCandidate(JSONObject msg);
}
