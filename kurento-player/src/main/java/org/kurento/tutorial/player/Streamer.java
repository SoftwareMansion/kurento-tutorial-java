package org.kurento.tutorial.player;

import org.json.JSONException;
import org.json.JSONObject;
import org.kurento.client.*;
import org.kurento.room.client.KurentoRoomClient;

import java.io.IOException;
import java.util.logging.Logger;

public class Streamer {

    private final KurentoClient kurento;
    private final LicodeConnector roomClient;
    private Long streamId;
    private final Logger log = Logger.getLogger(Watcher.class.getName());

    private PlayerEndpoint playerEndpoint;
    private WebRtcEndpoint webRtcEndpoint;

    public Streamer(KurentoClient kurento, LicodeConnector roomClient) {
        this.kurento = kurento;
        this.roomClient = roomClient;
    }

    void createPipeline() throws IOException, JSONException {
        MediaPipeline pipeline = kurento.createMediaPipeline();
        webRtcEndpoint = new WebRtcEndpoint
                .Builder(pipeline)
                .build();

        playerEndpoint = new PlayerEndpoint
                .Builder(pipeline, getVideoFilePath())
                .build();
        playerEndpoint.connect(webRtcEndpoint);

        String sdpOffer = webRtcEndpoint.generateOffer();
        roomClient.publish(sdpOffer, new StreamCallback() {
            @Override
            public void onSdpOffer(JSONObject msg) {
                // nothing to do
                try {
                    log.info("Received SDP offer: " + msg.toString(2));
                } catch (Exception e) {}
            }

            @Override
            public void onSdpAnswer(JSONObject msg) {
                try {
                    log.info("Received SDP answer: " + msg.toString(2));
                    String answer = msg.getString("sdp");
                    webRtcEndpoint.processAnswer(answer);
                    webRtcEndpoint.gatherCandidates();
                } catch (Exception e) {}
            }

            @Override
            public void onIceCandidate(JSONObject msg) {
                // nothing to do (?)
                try {
                    log.info("Received ICE candidate: " + msg.toString(2));
                } catch (Exception e) {}
            }
        });

        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                IceCandidate candidate = event.getCandidate();
                try {
                    roomClient.sendIceCandidate(
                            streamId,
                            candidate.getCandidate(),
                            candidate.getSdpMid(),
                            candidate.getSdpMLineIndex());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        playerEndpoint.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
            @Override
            public void onEvent(EndOfStreamEvent event) {
                if (event.getSource().equals(playerEndpoint)) {
                    playerEndpoint.play();
                }
            }
        });

        webRtcEndpoint.addOnIceGatheringDoneListener(new EventListener<OnIceGatheringDoneEvent>() {
            @Override
            public void onEvent(OnIceGatheringDoneEvent onIceGatheringDoneEvent) {
                try {
                    roomClient.sendIceCandidate(
                            streamId,
                            "end",
                            "end",
                            -1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        playerEndpoint.play();
    }

//    private String getVideoFilePath() {
//        return "file:///home/kurento/jellyfish-3-mbps-hd-h264.mkv";
//    }
    private String getVideoFilePath() {
        return "file:///home/kurento/vp8.webm";
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }
}
