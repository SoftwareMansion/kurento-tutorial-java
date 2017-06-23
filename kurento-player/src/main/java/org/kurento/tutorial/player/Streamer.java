package org.kurento.tutorial.player;

import org.json.JSONException;
import org.json.JSONObject;
import org.kurento.client.*;
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

        roomClient.publish(new StreamCallback() {
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
            public void onStreamStarted(Long sid) {
                log.info("Stream " + sid.toString() + " started, sending SDP offer");
                streamId = sid;
                String sdpOffer = webRtcEndpoint.generateOffer();
                roomClient.sendSdpOffer(streamId, sdpOffer);
            }

            @Override
            public void onStreamReady() {
                log.info("Playing video");
//                playerEndpoint.play();
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

        webRtcEndpoint.addOnIceGatheringDoneListener(new EventListener<OnIceGatheringDoneEvent>() {
            @Override
            public void onEvent(OnIceGatheringDoneEvent onIceGatheringDoneEvent) {
                try {
                    log.severe("!!!!");
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
    }

    private String getVideoFilePath() {
        return "file:///home/kurento/jellyfish-3-mbps-hd-h264.mkv";
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }
}
