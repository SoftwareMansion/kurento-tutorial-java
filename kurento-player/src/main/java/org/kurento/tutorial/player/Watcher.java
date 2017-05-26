package org.kurento.tutorial.player;

import org.kurento.client.*;
import org.kurento.room.client.KurentoRoomClient;

import java.io.IOException;
import java.util.Date;

public class Watcher {

    private static final String RECORDER_FILE_PATH = "file:///dev/null";

    private final KurentoClient kurento;
    private final KurentoRoomClient roomClient;
    private final String streamerName;
    private final String watchStream;

    private RecorderEndpoint recorder;
    private WebRtcEndpoint webRtcEndpoint;
    private MediaPipeline pipeline;

    public Watcher(KurentoClient kurento, KurentoRoomClient roomClient, String streamerName, String watchStream) {
        this.kurento = kurento;
        this.roomClient = roomClient;
        this.streamerName = streamerName;
        this.watchStream = watchStream;
    }

    synchronized void createPipeline() throws IOException {
        pipeline = kurento.createMediaPipeline();
        webRtcEndpoint = new WebRtcEndpoint
                .Builder(pipeline)
                .build();
//        webRtcEndpoint.connect(webRtcEndpoint);

        MediaProfileSpecType profile = getMediaProfileSpecType();
        recorder = new RecorderEndpoint
                .Builder(pipeline, getRecorderPath(watchStream))
                .withMediaProfile(profile)
                .build();
//        webRtcEndpoint.connect(recorder);
        webRtcEndpoint.connect(recorder, MediaType.VIDEO);

        String sdpOffer = webRtcEndpoint.generateOffer();
        String answer = roomClient.receiveVideoFrom(watchStream, sdpOffer);
        webRtcEndpoint.processAnswer(answer);

        webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                IceCandidate candidate = event.getCandidate();
                try {
                    synchronized (Watcher.this) {
                        roomClient.onIceCandidate(
                                streamerName,
                                candidate.getCandidate(),
                                candidate.getSdpMid(),
                                candidate.getSdpMLineIndex());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        webRtcEndpoint.gatherCandidates();
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

    private String getRecorderPath(String streamName) {
        return "file:///tmp/" + streamName + new Date().getTime() + ".webm";
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
