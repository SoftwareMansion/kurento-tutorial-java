package org.kurento.tutorial.player;

import org.json.JSONObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.internal.client.ListenerSubscriptionImpl;
import org.kurento.room.client.KurentoRoomClient;
import org.kurento.room.client.internal.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class UserApp {

    private ConcurrentHashMap<String, Watcher> watcherPipelines = new ConcurrentHashMap<>();
    private LicodeConnector connector = null;
    static final Logger log = Logger.getLogger(UserApp.class.getName());

//    private void run(final KurentoClient kurento, final String roomName, final String userName) throws IOException {
//        String kurentoRoomUrl = System.getProperty("kurentoRoom.url", KurentoConstants.KURENTO_ROOM_URL);
//        final KurentoRoomClient client = new KurentoRoomClient(kurentoRoomUrl);
//        Map<String, List<String>> peers = client.joinRoom(roomName, userName, null);
//
//
//        final Streamer streamer = new Streamer(kurento, client, userName);
//        for (Map.Entry<String, List<String>> entry : peers.entrySet()) {
//            if (!entry.getKey().equals(userName)) {
//                String streamerName = entry.getKey();
//                String camera = entry.getValue().get(0);
//                Watcher watcher = new Watcher(kurento, client, streamerName, streamerName + "_" + camera);
//                watcherPipelines.put(entry.getKey(), watcher);
//            }
//        }
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        Notification notification = client.getServerNotification();
//                        if (notification == null) return;
//                        switch (notification.getMethod()) {
//                            case ICECANDIDATE_METHOD:
//                                IceCandidateInfo iceCandidateInfo = (IceCandidateInfo) notification;
//                                String endpointName = iceCandidateInfo.getEndpointName();
//                                IceCandidate iceCandidate = iceCandidateInfo.getIceCandidate();
//                                if (userName.equals(endpointName)) {
//                                    streamer.getWebRtcEndpoint().addIceCandidate(iceCandidate);
//                                    break;
//                                }
//                                Watcher watcher = watcherPipelines.get(endpointName);
//                                if (watcher != null) {
//                                    synchronized (watcher) {
//                                        watcher.getWebRtcEndpoint().addIceCandidate(iceCandidate);
//                                    }
//                                }
//                                break;
//                            case PARTICIPANTPUBLISHED_METHOD:
//                                ParticipantPublishedInfo participantPublishedInfo = (ParticipantPublishedInfo) notification;
//                                String participantId = participantPublishedInfo.getId();
//                                String camera = participantPublishedInfo.getStreams().get(0);
//                                Watcher newWatcher = new Watcher(kurento, client, participantId, participantId + "_" + camera);
//                                try {
//                                    newWatcher.createPipeline();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                                watcherPipelines.put(participantId, newWatcher);
//                                break;
//                            case PARTICIPANTUNPUBLISHED_METHOD:
//                                ParticipantUnpublishedInfo participantUnpublishedInfo = (ParticipantUnpublishedInfo) notification;
//                                String participantUnpublishedId = participantUnpublishedInfo.getName();
//                                removeWatcher(participantUnpublishedId);
//                                break;
//                            case PARTICIPANTLEFT_METHOD:
//                                ParticipantLeftInfo participantLeftInfo = (ParticipantLeftInfo) notification;
//                                String participantLeftId = participantLeftInfo.getName();
//                                removeWatcher(participantLeftId);
//                                break;
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }).start();
//
//        streamer.createPipeline();
//        for (Map.Entry<String, Watcher> watcherPipeline : watcherPipelines.entrySet()) {
//            try {
//                watcherPipeline.getValue().createPipeline();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

    private void removeWatcher(String watcherName) {
        Watcher watcher = watcherPipelines.get(watcherName);
        if (watcher != null) {
            watcher.stop();
            watcherPipelines.remove(watcherName);
        }
    }

    public void start(final KurentoClient kurento) throws Exception {
        SubscribeCallback subscribeCallback = (JSONObject stream) -> {
            try {
                Long streamId = stream.getLong("id");
                Watcher watcher = new Watcher(kurento, connector, streamId);
                watcher.createPipeline();
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        };
        connector = new LicodeConnector(subscribeCallback, null);
        connector.connect();
    }

    public static void main(String[] args) throws Exception {
        KurentoClient kurentoClient = KurentoClient.create();
        UserApp userApp = new UserApp();
        userApp.start(kurentoClient);


//        int usersCount = Integer.parseInt(args[1]);
//        for (int i = 0; i < usersCount; i++) {
//            KurentoClient kurentoClient = KurentoClient.create();
//            UserApp userApp = new UserApp();
//            try {
//                userApp.run(kurentoClient, args[0], "test" + i);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
    }
}
