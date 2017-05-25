package org.kurento.tutorial.player;

import org.kurento.client.*;
import org.kurento.room.client.KurentoRoomClient;
import org.kurento.room.client.internal.IceCandidateInfo;
import org.kurento.room.client.internal.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestStreamerRoom {

    private final Logger log = LoggerFactory.getLogger(TestStreamerRoom.class);

    private void run(final KurentoClient kurento, final String roomName, final String userName) throws IOException {
        String kurentoRoomUrl = System.getProperty("kurentoRoom.url", KurentoConstants.KURENTO_ROOM_URL);
        final KurentoRoomClient client = new KurentoRoomClient(kurentoRoomUrl);
        client.joinRoom(roomName, userName, null);
        MediaPipeline pipeline = kurento.createMediaPipeline();
        final WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint
                .Builder(pipeline)
                .build();

        final PlayerEndpoint playerEndpoint = new PlayerEndpoint
                .Builder(pipeline, "http://www.sample-videos.com/video/mp4/480/big_buck_bunny_480p_5mb.mp4")
                .build();
        playerEndpoint.connect(webRtcEndpoint);

        String sdpOffer = webRtcEndpoint.generateOffer();
        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                IceCandidate candidate = event.getCandidate();
                try {
                    client.onIceCandidate(
                            userName,
                            candidate.getCandidate(),
                            candidate.getSdpMid(),
                            candidate.getSdpMLineIndex());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        webRtcEndpoint.gatherCandidates();

        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
        ses.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Notification notification = client.getServerNotification();
                if (notification == null) return;
                switch (notification.getMethod()) {
                    case ICECANDIDATE_METHOD:
                        IceCandidateInfo info = (IceCandidateInfo) notification;
                        webRtcEndpoint.addIceCandidate(info.getIceCandidate());
                        break;
                }
            }
        }, 3, 3, TimeUnit.SECONDS);

        String sdpAnswer = client.publishVideo(sdpOffer, false);
        webRtcEndpoint.processAnswer(sdpAnswer);

        playerEndpoint.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
            @Override
            public void onEvent(EndOfStreamEvent event) {
                log.info("EndOfStreamEvent: {}", event.getTimestamp());
                playerEndpoint.play();
            }
        });

        playerEndpoint.play();
    }


    public static void main(String[] args) {
        try {
            new TestStreamerRoom().run(KurentoClient.create(), args[0], args[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
