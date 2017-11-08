package org.kurento.tutorial.player;

import org.json.JSONObject;
import org.kurento.client.*;
import java.util.Date;
import java.util.logging.Logger;

public class Watcher {

    private final Logger log = Logger.getLogger(Watcher.class.getName());

    private final KurentoClient kurento;
    private final LicodeConnector roomClient;
    private final Long streamId;
    private final String username;

    private RecorderEndpoint recorder;
    private WebRtcEndpoint webRtcEndpoint;
    private MediaPipeline pipeline;

    public Watcher(KurentoClient kurento, LicodeConnector roomClient, Long streamId, String username) {
        this.kurento = kurento;
        this.roomClient = roomClient;
        this.streamId = streamId;
        this.username = username;
    }

    synchronized void createPipeline() throws Exception {
        pipeline = kurento.createMediaPipeline();
        webRtcEndpoint = new WebRtcEndpoint
                .Builder(pipeline)
                .build();

        MediaProfileSpecType profile = getMediaProfileSpecType();
        recorder = new RecorderEndpoint
                .Builder(pipeline, getRecorderPath(streamId))
                .withMediaProfile(profile)
                .build();
        webRtcEndpoint.connect(recorder, MediaType.VIDEO);

        roomClient.subscribe(streamId, new StreamCallback() {
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
                String sdpOffer = webRtcEndpoint.generateOffer();
                roomClient.sendSdpOffer(streamId, sdpOffer);
            }

            @Override
            public void onStreamReady() {
//                log.info("Recording stream...");
//                recorder.record();
            }

            @Override
            public void onRemoveStream() {
                log.info("Stopping recording...");
                pipeline.release();
            }
        });

        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                IceCandidate candidate = event.getCandidate();
                try {
                    synchronized (Watcher.this) {
                        roomClient.sendIceCandidate(
                                streamId,
                                candidate.getCandidate(),
                                candidate.getSdpMid(),
                                candidate.getSdpMLineIndex());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String getRecorderPath(Long streamId) {
        return "file:///tmp/" + username + "_" + streamId.toString() + "_" + new Date().getTime() + ".mp4";
    }

    private MediaProfileSpecType getMediaProfileSpecType() {
        return MediaProfileSpecType.MP4_VIDEO_ONLY;
    }
}
