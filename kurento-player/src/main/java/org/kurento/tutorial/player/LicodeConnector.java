package org.kurento.tutorial.player;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class LicodeConnector {
    public static final ConnectionPool GLOBAL_CONNECTION_POOL = new ConnectionPool(Integer.MAX_VALUE, 5, TimeUnit.MINUTES);

    Socket socket;

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
        String url = "https://staging-licode.fam.software-mansion.com/createToken/";
        JSONObject json = new JSONObject()
                .put("username", username)
                .put("role", "presenter")
                .put("type", "erizo")
                .put("alwaysUseLicode", true);
        if (room != null) {
            json.put("room", room);
        }
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
            final JSONObject response = new JSONObject(result);
            final String token = response.getString("token");
            return new String(Base64.getDecoder().decode(token.getBytes()), "UTF-8");
        } catch (UnsupportedEncodingException | JSONException e) {
            System.err.println("Failed to decode token: " + e.getMessage());
        }
        return null;
    }

    private void connectToSocket(final JSONObject token) throws Exception {
        System.err.printf("[%s]: token %s\n", username, token);

        String wsUri = "https://" + token.getString("host");

        final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionPool(GLOBAL_CONNECTION_POOL)
                .build();

        IO.Options opts = new IO.Options();
        opts.secure = true;
        opts.transports = new String[]{"websocket"};
        opts.forceNew = true;
        opts.webSocketFactory = okHttpClient;
        this.socket = IO.socket(wsUri, opts);

        this.socket.on(Socket.EVENT_ERROR, args -> {
            System.err.printf("[%s]: an Error occured\n", username);
            if (args.length > 0 && args[0] instanceof Throwable) {
                ((Throwable) args[0]).printStackTrace();
            } else {
                System.err.println(Arrays.toString(args));
            }
        });
        this.socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            System.err.printf("[%s]: Connection error\n", username);
            if (args.length > 0 && args[0] instanceof Throwable) {
                ((Throwable) args[0]).printStackTrace();
            } else {
                System.err.println(Arrays.toString(args));
            }
        });
        this.socket.on(Socket.EVENT_CONNECT_TIMEOUT, args -> {
            System.err.printf("[%s]: Connection timed out\n", username);
            if (args.length > 0 && args[0] instanceof Throwable) {
                ((Throwable) args[0]).printStackTrace();
            } else {
                System.err.println(Arrays.toString(args));
            }
        });
        this.socket.on(Socket.EVENT_DISCONNECT,
                args -> System.err.printf("[%s]: Connection terminated: %s\n", username, Arrays.toString(args)));
        this.socket.on(Socket.EVENT_CONNECT, args -> {
            System.err.printf("[%s]: Connection established\n", username);

            socket.emit("token", token, new SimpleAck() {
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
            });
        });
        this.socket.on("signaling_message_erizo", args -> {
            try {
                JSONObject msg = (JSONObject) args[0];
                processSignalingMessageErizo(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        this.socket.on("onAddStream", args -> {
            if (subscribeCallback != null) {
                JSONObject stream = (JSONObject) args[0];
                subscribeCallback.onShouldSubscribe(stream);
            }
        });
        this.socket.on("onRemoveStream", args -> {
            JSONObject msg = (JSONObject) args[0];
            Long id = msg.getLong("id");
            StreamCallback streamCallback = streamCallbacks.get(id);
            streamCallback.onRemoveStream();
        });
        socket.connect();
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
        socket.emit("subscribe", new Object[] {obj, null}, (Object... args) -> {
            streamCallbacks.put(streamId, streamCallback);
        });
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

        socket.emit("publish",new Object[] { obj, null }, (Object... args) -> {
            Long streamId = (Long) args[0];
            streamCallbacks.put(streamId, streamCallback);
        });
    }

    public void sendSdpOffer(Long streamId, String sdp) {
        JSONObject offer = prepareSdpOffer(streamId, sdp);
        socket.emit("signaling_message", new Object[] { offer, null }, (Object... msgArgs) -> {});
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
        socket.emit("signaling_message", new Object[] {msg, null}, (Object... msgArgs) -> {});
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

    abstract class SimpleAck implements Ack {
        @Override
        public void call(Object... objects) {
            try {
                if(!(objects[1] instanceof JSONObject)) {
                    log.severe(String.format("Expected JSON object, got: %s", Objects.toString(objects[1])));
                } else {
                    handle((String) objects[0], (JSONObject) objects[1]);
                }
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        }

        abstract void handle(String respType, JSONObject msg) throws JSONException;
    }
}
