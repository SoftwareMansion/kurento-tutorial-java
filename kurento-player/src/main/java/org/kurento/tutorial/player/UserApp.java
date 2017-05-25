package org.kurento.tutorial.player;

import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.room.client.KurentoRoomClient;
import org.kurento.room.client.internal.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserApp {

    private ConcurrentHashMap<String, Watcher> watcherPipelines = new ConcurrentHashMap<>();

    private void run(final KurentoClient kurento, final String roomName, final String userName) throws IOException {
        String kurentoRoomUrl = System.getProperty("kurentoRoom.url", KurentoConstants.KURENTO_ROOM_URL);
        final KurentoRoomClient client = new KurentoRoomClient(kurentoRoomUrl);
        Map<String, List<String>> peers = client.joinRoom(roomName, userName, null);


        final Streamer streamer = new Streamer(kurento, client, userName);
        for (Map.Entry<String, List<String>> entry : peers.entrySet()) {
            if (!entry.getKey().equals(userName)) {
                Watcher watcher = new Watcher(kurento, client, entry.getKey(), entry.getKey() + "_" + entry.getValue().get(0));
                watcherPipelines.put(entry.getKey(), watcher);
            }
        }

        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
        ses.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Notification notification = client.getServerNotification();
                    if (notification == null) return;
                    switch (notification.getMethod()) {
                        case ICECANDIDATE_METHOD:
                            IceCandidateInfo iceCandidateInfo = (IceCandidateInfo) notification;
                            String endpointName = iceCandidateInfo.getEndpointName();
                            IceCandidate iceCandidate = iceCandidateInfo.getIceCandidate();
                            if (userName.equals(endpointName)) {
                                streamer.getWebRtcEndpoint().addIceCandidate(iceCandidate);
                            }
                            Watcher watcher = watcherPipelines.get(endpointName);
                            if (watcher != null) {
                                watcher.getWebRtcEndpoint().addIceCandidate(iceCandidate);
                            }
                            break;
                        case PARTICIPANTPUBLISHED_METHOD:
                            ParticipantPublishedInfo participantPublishedInfo = (ParticipantPublishedInfo) notification;
                            String participantId = participantPublishedInfo.getId();
                            String camera = participantPublishedInfo.getStreams().get(0);
                            Watcher newWatcher = new Watcher(kurento, client, participantId, participantId + "_" + camera);
                            try {
                                newWatcher.createPipeline();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            watcherPipelines.put(participantId, newWatcher);
                            break;
                        case PARTICIPANTUNPUBLISHED_METHOD:
                            ParticipantUnpublishedInfo participantUnpublishedInfo = (ParticipantUnpublishedInfo) notification;
                            String participantUnpublishedId = participantUnpublishedInfo.getName();
                            Watcher currentWatcher = watcherPipelines.get(participantUnpublishedId);
                            if (currentWatcher != null) {
                                currentWatcher.stop();
                                watcherPipelines.remove(participantUnpublishedId);
                            }
                            break;
                        case PARTICIPANTLEFT_METHOD:
                            ParticipantLeftInfo participantLeftInfo = (ParticipantLeftInfo) notification;
                            String participantLeftId = participantLeftInfo.getName();
                            Watcher leftWatcher = watcherPipelines.get(participantLeftId);
                            if (leftWatcher != null) {
                                leftWatcher.stop();
                                watcherPipelines.remove(participantLeftId);
                            }
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 5, 1, TimeUnit.SECONDS);

        streamer.createPipeline();
        for (Map.Entry<String, Watcher> watcherPipeline : watcherPipelines.entrySet()) {
            try {
                watcherPipeline.getValue().createPipeline();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int usersCount = Integer.parseInt(args[1]);
        for (int i = 0; i < usersCount; i++) {
            KurentoClient kurentoClient = KurentoClient.create();
            UserApp userApp = new UserApp();
            try {
                userApp.run(kurentoClient, args[0], "test" + i);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
