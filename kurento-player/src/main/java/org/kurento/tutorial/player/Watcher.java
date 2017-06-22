package org.kurento.tutorial.player;

import org.json.JSONObject;
import org.kurento.client.*;
import org.kurento.room.client.KurentoRoomClient;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Logger;

public class Watcher {

    private static final String RECORDER_FILE_PATH = "file:///dev/null";
    private final Logger log = Logger.getLogger(Watcher.class.getName());

    private final KurentoClient kurento;
    private final LicodeConnector roomClient;
    private final Long streamId;

    private RecorderEndpoint recorder;
    private WebRtcEndpoint webRtcEndpoint;
    private MediaPipeline pipeline;

    public Watcher(KurentoClient kurento, LicodeConnector roomClient, Long streamId) {
        this.kurento = kurento;
        this.roomClient = roomClient;
        this.streamId = streamId;
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

        String sdpOffer = webRtcEndpoint.generateOffer();
        roomClient.subscribe(streamId, sdpOffer, new StreamCallback() {
            @Override
            public void onSdpOffer(JSONObject msg) {
                // nothing to do (?)
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
        webRtcEndpoint.addNewCandidatePairSelectedListener(new EventListener<NewCandidatePairSelectedEvent>() {
            @Override
            public void onEvent(NewCandidatePairSelectedEvent newCandidatePairSelectedEvent) {
                System.out.println(newCandidatePairSelectedEvent);
            }
        });

        webRtcEndpoint.addOnIceGatheringDoneListener(new EventListener<OnIceGatheringDoneEvent>() {
            @Override
            public void onEvent(OnIceGatheringDoneEvent onIceGatheringDoneEvent) {
                recorder.record();
            }
        });
    }

    private String getRecorderPath(Long streamId) {
        return "file:///tmp/" + streamId.toString() + new Date().getTime() + ".mp4";
    }

    WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }

    void stop() {
//        recorder.stop();
        pipeline.release();
    }

    private MediaProfileSpecType getMediaProfileSpecType() {
        return MediaProfileSpecType.WEBM_VIDEO_ONLY;
    }
}
