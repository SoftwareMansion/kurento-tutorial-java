package org.kurento.tutorial.player;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public class LicodeConnector {
    SocketIO socket;

    static final Logger log = Logger.getLogger(LicodeConnector.class.getName());
    private final SubscribeCallback subscribeCallback;
    private final PublishCallback publishCallback;
    private final ConcurrentHashMap<Long, StreamCallback> streamCallbacks;
    private String username;
    private String room;

    public LicodeConnector(String username, String room, SubscribeCallback subscribeCallback, PublishCallback publishCallback) {
        this.username = username;
        this.room = room;
        this.subscribeCallback = subscribeCallback;
        this.publishCallback = publishCallback;
        streamCallbacks = new ConcurrentHashMap<>();
    }

    public void connect() throws Exception {
        JSONObject token = getToken();
        connectToSocket(token);
    }

    private JSONObject getToken() throws Exception {
        String url = "https://licode.swmansion.eu/createToken";
        JSONObject json = new JSONObject()
                .put("username", username)
                .put("role", "presenter")
                .put("room", room)
                .put("type", "erizo");
        String result = sendPost(url, json);
        String decoded = decodeToken(result);
        return new JSONObject(decoded);
    }

    private String sendPost(String url, JSONObject json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");

        String jsonString = json.toString();
        OutputStream os = conn.getOutputStream();
        os.write(jsonString.getBytes("UTF-8"));
        os.close();

        InputStream in = new BufferedInputStream(conn.getInputStream());
        String result = org.apache.commons.io.IOUtils.toString(in, "UTF-8");

        in.close();
        conn.disconnect();

        return result;
    }

    private String decodeToken(String result) {
        try {
            return new String(Base64.getDecoder().decode(result.getBytes()), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Failed to decode token: " + e.getMessage());
        }
        return null;
    }

    private void connectToSocket(final JSONObject token) throws Exception {
        System.out.println(token);
        String wsUri = "https://" + token.getString("host");

        SocketIO.setDefaultSSLSocketFactory(SSLContext.getDefault());
        this.socket = new SocketIO(wsUri);
        this.socket.connect(new IOCallback() {
            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
                try {
                    System.out.println("Server said:" + json.toString(2));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
                System.out.println("Server said: " + data);
            }

            @Override
            public void onError(SocketIOException socketIOException) {
                System.out.println("an Error occured");
                socketIOException.printStackTrace();
            }

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established");

                socket.emit("token", new SimpleAck() {
                    @Override
                    void handle(String respType, JSONObject msg) throws JSONException {
                        if (respType.equals("error")) {
                            log.severe("Error token response");
                            return;
                        }

                        for (JSONObject stream : toArray(msg.getJSONArray("streams"))) {
                            if (subscribeCallback != null) {
                                subscribeCallback.onShouldSubscribe(stream);
                            }
                        }

                        if (publishCallback != null) {
                            publishCallback.onShouldPublish();
                        }
                    }
                }, token);
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {
                try {
                    System.out.println("Server triggered event '" + event + "'");

                    if (event.equals("signaling_message_erizo")) {
                        JSONObject msg = (JSONObject) args[0];
                        processSignalingMessageErizo(msg);
                    } else {
                        log.warning("Unsupported event " + event);
                    }
                } catch (Exception e) {
                    log.severe("Error");
                    e.printStackTrace();
                }
            }
        });
    }

    private void processSignalingMessageErizo(JSONObject msg) throws Exception {
        Long streamId;
        if (msg.has("peerId")) {
            streamId = msg.getLong("peerId");
        } else {
            streamId = msg.getLong("streamId");
        }
        log.info("Got signaling message for stream " + streamId.toString());

        JSONObject mess = msg.getJSONObject("mess");
        String type = mess.getString("type");
        StreamCallback streamCallback = streamCallbacks.get(streamId);
        if (type.equals("answer")) {
            streamCallback.onSdpAnswer(mess);
        } else if (type.equals("started")) {
            streamCallback.onStreamStarted(streamId);
        } else if (type.equals("ready")) {
            streamCallback.onStreamReady();
        } else {
            log.warning("unhandled signaling message: " + msg.toString(2));
        }
    }

    public void subscribe(Long streamId, StreamCallback streamCallback) throws JSONException {
        log.info("Subscribing stream " + streamId.toString() + "...");
        JSONObject obj = new JSONObject();
        obj.put("browser", "chrome-stable");
        obj.put("streamId", streamId);
        obj.put("slideShowMode", false);
        JSONObject metadata = new JSONObject();
        metadata.put("type", "subscriber");
        obj.put("metadata", metadata);
        socket.emit("subscribe", (Object... args) -> {
            streamCallbacks.put(streamId, streamCallback);
        }, obj, null);
    }

    public void publish(StreamCallback streamCallback) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("audio", true);
        obj.put("video", true);
        obj.put("data", true);
        obj.put("screen", "");
        obj.put("state", "erizo");
        obj.put("minVideoBW", 0);
        obj.put(
                "metadata",
                new JSONObject()
                        .put("type", "publisher")
        );

        socket.emit("publish", (Object... args) -> {
            Long streamId = (Long) args[0];
            streamCallbacks.put(streamId, streamCallback);
        }, obj, null);
    }

    public void sendSdpOffer(Long streamId, String sdp) {
        JSONObject offer = prepareSdpOffer(streamId, sdp);
        socket.emit("signaling_message", (Object... msgArgs) -> {}, offer, null);
    }

    public JSONObject prepareSdpOffer(Long streamId, String sdp) {
        try {
            JSONObject offer = new JSONObject();
            offer.put("streamId", streamId);
            offer.put(
                    "msg",
                    new JSONObject()
                            .put("type", "offer")
                            .put("sdp", sdp)
            );
            return offer;
        } catch (Exception e) {
            log.severe("Error");
            e.printStackTrace();
        }
        return null;
    }

    public void sendIceCandidate(Long streamId, String candidate, String sdpMid, Integer sdpMLineIndex) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("streamId", streamId);

        JSONObject innerMsg = new JSONObject();
        innerMsg.put("type", "candidate");

        JSONObject candidateObj = new JSONObject();
        candidateObj.put("candidate", "a=" + candidate);
        candidateObj.put("sdpMid", sdpMid);
        candidateObj.put("sdpMLineIndex", sdpMLineIndex);

        innerMsg.put("candidate", candidateObj);
        msg.put("msg", innerMsg);
        socket.emit("signaling_message", (Object... msgArgs) -> {}, msg, null);
    }

    public static JSONObject[] toArray(JSONArray arr) {
        try {
            JSONObject[] result = new JSONObject[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                result[i] = arr.getJSONObject(i);
            }
            return result;
        } catch (Exception e) {
            log.severe("Error");
            e.printStackTrace();
        }
        return null;
    }

    abstract class SimpleAck implements IOAcknowledge {
        @Override
        public void ack(Object... objects) {
            try {
                handle((String) objects[0], (JSONObject) objects[1]);
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        }

        abstract void handle(String respType, JSONObject msg) throws JSONException;
    }
}
