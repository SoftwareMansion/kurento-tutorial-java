package org.kurento.tutorial.player;

import org.json.JSONObject;
import org.kurento.client.KurentoClient;
import java.util.logging.Logger;

public class UserApp {
    private LicodeConnector connector = null;
    static final Logger log = Logger.getLogger(UserApp.class.getName());

    private void run(final KurentoClient kurento, final String roomName, final String userName) throws Exception {
        SubscribeCallback subscribeCallback = (JSONObject stream) -> {
            try {
                Long streamId = stream.getLong("id");
                Watcher watcher = new Watcher(kurento, connector, streamId, userName);
                watcher.createPipeline();
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        };
        PublishCallback publishCallback = () -> {
            try {
                Streamer streamer = new Streamer(kurento, connector);
                streamer.createPipeline();
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        };
        connector = new LicodeConnector(userName, roomName, subscribeCallback, publishCallback);
        connector.connect();
    }

    public static void main(String[] args) throws Exception {
        String room = "2";
        int usersCount = 1;

        for (int i = 0; i < usersCount; i++) {
            KurentoClient kurentoClient = KurentoClient.create();
            UserApp userApp = new UserApp();
            try {
                userApp.run(kurentoClient, room, "test" + i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
