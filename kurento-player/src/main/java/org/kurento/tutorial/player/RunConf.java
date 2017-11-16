package org.kurento.tutorial.player;

import org.kurento.client.KurentoClient;

public class RunConf {
    private final UserConf userConf;
    private final String websocketUrl;

    public RunConf(UserConf userConf, String websocketUrl) {
        this.userConf = userConf;
        this.websocketUrl = websocketUrl;
    }

    public UserConf getUserConf() {
        return userConf;
    }

    public String getAddress() {
        return websocketUrl;
    }

    public void run(KurentoClientManager kcm) throws Exception {
        final KurentoClient client = kcm.getKurentoClient(websocketUrl);
        userConf.run(client);
    }
}
