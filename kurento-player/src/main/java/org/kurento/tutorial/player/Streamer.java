package org.kurento.tutorial.player;

import org.kurento.client.*;
import org.kurento.room.client.KurentoRoomClient;

import java.io.IOException;

public class Streamer {

    private final KurentoClient kurento;
    private final KurentoRoomClient roomClient;
    private final String userName;

    private PlayerEndpoint playerEndpoint;
    private WebRtcEndpoint webRtcEndpoint;

    public Streamer(KurentoClient kurento, KurentoRoomClient roomClient, String userName) {
        this.kurento = kurento;
        this.roomClient = roomClient;
        this.userName = userName;
    }

    void createPipeline() throws IOException {
        MediaPipeline pipeline = kurento.createMediaPipeline();
        webRtcEndpoint = new WebRtcEndpoint
                .Builder(pipeline)
                .build();

        playerEndpoint = new PlayerEndpoint
                .Builder(pipeline, "file:///home/ubuntu/FAM/sintel_no_audio.webm")
                .build();
        playerEndpoint.connect(webRtcEndpoint);

        String sdpOffer = webRtcEndpoint.generateOffer();
        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                IceCandidate candidate = event.getCandidate();
                try {
                    roomClient.onIceCandidate(
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
        String sdpAnswer = roomClient.publishVideo(sdpOffer, false);
        webRtcEndpoint.processAnswer(sdpAnswer);
        playerEndpoint.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
            @Override
            public void onEvent(EndOfStreamEvent event) {
                if (event.getSource().equals(playerEndpoint)) {
                    playerEndpoint.play();
                }
            }
        });

        playerEndpoint.play();
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }
}
