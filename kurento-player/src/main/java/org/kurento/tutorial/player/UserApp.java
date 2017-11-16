package org.kurento.tutorial.player;

import org.json.JSONObject;
import org.kurento.client.KurentoClient;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UserApp {

    private LicodeConnector connector = null;
    private Streamer streamer = null;
    static final Logger log = Logger.getLogger(UserApp.class.getName());

    public void run(final KurentoClient kurento, final String roomName, final String userName) throws Exception {
        System.err.printf("[%s]: Starting user\n", userName);
        SubscribeCallback subscribeCallback = (JSONObject stream) -> {
            try {
                Long streamId = stream.getLong("id");
                // don't watch myself
                if (streamer == null || !streamId.equals(streamer.getStreamId())) {
                    System.err.printf("[%s]: subscribe %d\n", userName, streamId);
                    Watcher watcher = new Watcher(kurento, connector, streamId, userName);
                    watcher.createPipeline();
                }
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        };
        PublishCallback publishCallback = () -> {
            try {
                System.err.printf("[%s]: publish\n", userName);
                streamer = new Streamer(kurento, connector);
                streamer.createPipeline();
            } catch (Exception e) {
                log.severe("Error");
                e.printStackTrace();
            }
        };
        connector = new LicodeConnector(userName, roomName, subscribeCallback, publishCallback);
        connector.connect();
    }

    /**
     * 32:2 72:3
     */
    public static void main(String[] args) {
        try {
            final String propKurentos = System.getProperty("benchmark.kurentos");
            if(propKurentos == null) {
                System.err.println("Please define -Dbenchmark.kurentos");
                System.exit(1);
            }

            List<String> kurentoUrls = Arrays.stream(propKurentos.split("\\s"))
                    .map(s -> String.format("ws://%s:8888/kurento", s))
                    .collect(Collectors.toList());

            if (kurentoUrls.isEmpty()) {
                System.err.println("-Dbenchmark.kurentos is empty");
                System.exit(1);
            }

            final List<UserConf> userConfs = parseUserConfs(args);
            List<RunConf> runConfs = KurentoBalancer.balance(userConfs, kurentoUrls);

            printSummaryTable(runConfs);

            Collections.shuffle(runConfs);

            System.err.println("Starting in 5 seconds...");
            Thread.sleep(5000);

            final KurentoClientManager kcm = new KurentoClientManager();
            for (RunConf runConf : runConfs) {
                runConf.run(kcm);
            }

            System.in.read();

            for (RunConf runConf : runConfs) {
                runConf.getUserConf().getUserApp().stopStreamer();
            }

            System.err.println("Will exit in 5 seconds");
            Thread.sleep(5000);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<UserConf> parseUserConfs(String[] args) throws Exception {
        final List<UserConf> userConfs = new ArrayList<>();
        final String roomPrefix = "test";

        int roomId = 1;

        for (String arg : args) {
            int rooms;
            int users;

            try {
                final String[] split = arg.split(":", 2);

                if (split.length != 2) {
                    throw new Exception("config should have 2 items");
                }

                rooms = Integer.parseUnsignedInt(split[0]);
                users = Integer.parseUnsignedInt(split[1]);
            } catch (Exception e) {
                System.err.printf("Invalid pattern %s\n", arg);
                throw e;
            }

            assert rooms > 0;
            assert users > 0;

            for (int i = 1; i <= rooms; i++) {
                String room = roomPrefix + Integer.toString(roomId);
                for (int j = 1; j <= users; j++) {
                    final UserApp userApp = new UserApp();
                    final String userName = roomPrefix + String.format("%d-%d", roomId, j);

                    final UserConf userConf = new UserConf(userApp, room, userName);
                    userConfs.add(userConf);
                }

                roomId++;
            }
        }
        return userConfs;
    }

    private static void printSummaryTable(List<RunConf> runConfs) {
        final ArrayList<RunConf> rcs = new ArrayList<>(runConfs);
        rcs.sort(Comparator.comparing(RunConf::getAddress));

        int i = 0;
        String addr = null;
        for (RunConf rc : rcs) {
            if (!Objects.equals(rc.getAddress(), addr)) {
                i = 1;
                addr = rc.getAddress();
                System.err.printf("\n%s:\n", rc.getAddress());
                System.err.println(" ID  |    ROOM    | USER NAME");
            }

            System.err.printf("%4d | %-10s | %s\n", i, rc.getUserConf().getRoom(), rc.getUserConf().getUserName());

            i++;
        }
    }

    private void stopStreamer() {
        if (streamer != null) {
            streamer.stop();
        }
    }
}

/*
"v=0
↵o=- 1075563043874770881 2 IN IP4 127.0.0.1
↵s=-
↵t=0 0
↵a=group:BUNDLE audio video
↵a=msid-semantic: WMS Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf
↵m=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 0 8 106 105 13 126
↵c=IN IP4 0.0.0.0
↵a=rtcp:9 IN IP4 0.0.0.0
↵a=ice-ufrag:tL54
↵a=ice-pwd:yQeZess77v69T7ERpzaTYEYR
↵a=fingerprint:sha-256 9A:E2:33:5A:FC:AD:23:23:6C:86:12:B6:48:49:A6:72:B0:E5:27:BE:DC:AB:2C:9D:F5:83:05:D2:50:34:03:A7
↵a=setup:actpass
↵a=mid:audio
↵a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level
↵a=sendonly
↵a=rtcp-mux
↵a=rtpmap:111 opus/48000/2
↵a=rtcp-fb:111 nack
↵a=rtcp-fb:111 transport-cc
↵a=fmtp:111 minptime=10;useinbandfec=1
↵a=rtpmap:103 ISAC/16000
↵a=rtpmap:104 ISAC/32000
↵a=rtpmap:9 G722/8000
↵a=rtpmap:0 PCMU/8000
↵a=rtpmap:8 PCMA/8000
↵a=rtpmap:106 CN/32000
↵a=rtpmap:105 CN/16000
↵a=rtpmap:13 CN/8000
↵a=rtpmap:126 telephone-event/8000
↵a=ssrc:3837080436 cname:9avuy6fe89cjSoBD
↵a=ssrc:3837080436 msid:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf 199106f3-266f-4725-af97-5a6c006d06e9
↵a=ssrc:3837080436 mslabel:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf
↵a=ssrc:3837080436 label:199106f3-266f-4725-af97-5a6c006d06e9
↵m=video 9 UDP/TLS/RTP/SAVPF 100 101 107 116 117 96 97 99 98
↵b=AS:1000
↵c=IN IP4 0.0.0.0
↵a=rtcp:9 IN IP4 0.0.0.0
↵a=ice-ufrag:tL54
↵a=ice-pwd:yQeZess77v69T7ERpzaTYEYR
↵a=fingerprint:sha-256 9A:E2:33:5A:FC:AD:23:23:6C:86:12:B6:48:49:A6:72:B0:E5:27:BE:DC:AB:2C:9D:F5:83:05:D2:50:34:03:A7
↵a=setup:actpass
↵a=mid:video
↵a=extmap:2 urn:ietf:params:rtp-hdrext:toffset
↵a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
↵a=extmap:4 urn:3gpp:video-orientation
↵a=extmap:5 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01
↵a=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay
↵a=sendonly
↵a=rtcp-mux
↵a=rtcp-rsize
↵a=rtpmap:100 VP8/90000
↵a=rtcp-fb:100 ccm fir
↵a=rtcp-fb:100 nack
↵a=rtcp-fb:100 nack pli
↵a=rtcp-fb:100 goog-remb
↵a=rtcp-fb:100 transport-cc
↵a=rtpmap:101 VP9/90000
↵a=rtcp-fb:101 ccm fir
↵a=rtcp-fb:101 nack
↵a=rtcp-fb:101 nack pli
↵a=rtcp-fb:101 goog-remb
↵a=rtcp-fb:101 transport-cc
↵a=rtpmap:107 H264/90000
↵a=rtcp-fb:107 ccm fir
↵a=rtcp-fb:107 nack
↵a=rtcp-fb:107 nack pli
↵a=rtcp-fb:107 goog-remb
↵a=rtcp-fb:107 transport-cc
↵a=fmtp:107 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f
↵a=rtpmap:116 red/90000
↵a=rtpmap:117 ulpfec/90000
↵a=rtpmap:96 rtx/90000
↵a=fmtp:96 apt=100
↵a=rtpmap:97 rtx/90000
↵a=fmtp:97 apt=101
↵a=rtpmap:99 rtx/90000
↵a=fmtp:99 apt=107
↵a=rtpmap:98 rtx/90000
↵a=fmtp:98 apt=116
↵a=ssrc-group:FID 473786898 1905438237
↵a=ssrc:473786898 cname:9avuy6fe89cjSoBD
↵a=ssrc:473786898 msid:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf 6d8dcb5e-1f67-42f8-9ffc-128e04724f69
↵a=ssrc:473786898 mslabel:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf
↵a=ssrc:473786898 label:6d8dcb5e-1f67-42f8-9ffc-128e04724f69
↵a=ssrc:1905438237 cname:9avuy6fe89cjSoBD
↵a=ssrc:1905438237 msid:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf 6d8dcb5e-1f67-42f8-9ffc-128e04724f69
↵a=ssrc:1905438237 mslabel:Of5rs9M806g8ymMqIhN5e3vldiA1b3AdMgGf
↵a=ssrc:1905438237 label:6d8dcb5e-1f67-42f8-9ffc-128e04724f69
↵"

v=0
o=- 3707647463 3707647463 IN IP4 0.0.0.0
s=Kurento Media Server
c=IN IP4 0.0.0.0
t=0 0
a=group:BUNDLE audio0 video0
m=audio 1 RTP/SAVPF 96 0
a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
a=rtpmap:96 opus/48000/2
a=rtcp:9 IN IP4 0.0.0.0
a=rtcp-mux
a=mid:audio0
a=ssrc:457943781 cname:user2847223984@host-6c8624b3
a=ice-ufrag:8LXj
a=ice-pwd:CkPqYZh+xF87HyuA7ZioQY
a=fingerprint:sha-256 27:BE:CC:74:13:52:06:DC:B9:7E:56:01:FE:97:79:A1:D2:16:80:F7:E7:42:40:20:5F:75:E9:CB:09:4E:44:67
m=video 1 RTP/SAVPF 100 101
b=AS:500
a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time
a=rtpmap:100 VP8/90000
a=rtpmap:101 H264/90000
a=rtcp:9 IN IP4 0.0.0.0
a=rtcp-mux
a=mid:video0
a=rtcp-fb:100 nack
a=rtcp-fb:100 nack pli
a=rtcp-fb:100 goog-remb
a=rtcp-fb:100 ccm fir
a=rtcp-fb:101 nack
a=rtcp-fb:101 nack pli
a=rtcp-fb:101 ccm fir\
a=ssrc:3550959356 cname:user2847223984@host-6c8624b3
a=ice-ufrag:8LXj
a=ice-pwd:CkPqYZh+xF87HyuA7ZioQY
a=fingerprint:sha-256 27:BE:CC:74:13:52:06:DC:B9:7E:56:01:FE:97:79:A1:D2:16:80:F7:E7:42:40:20:5F:75:E9:CB:09:4E:44:67

*/
