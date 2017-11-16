package org.kurento.tutorial.player;

import java.util.ArrayList;
import java.util.List;

public class KurentoBalancer {
    public static List<RunConf> balance(List<UserConf> userConfs, List<String> kurentoClients) {
        int totalLoad = userConfs.size();
        int loadPerKurento = (int) Math.ceil((double) totalLoad / kurentoClients.size());

        System.err.printf("Total load:   %6d publishers\n", totalLoad);
        System.err.printf("Load/Kurento: %6d\n", loadPerKurento);

        List<RunConf> runConfs = new ArrayList<>();

        for (String websocketUrl : kurentoClients) {
            List<UserConf> confs = pullUserConfs(userConfs, loadPerKurento);
            for (UserConf userConf : confs) {
                runConfs.add(new RunConf(userConf, websocketUrl));
            }
        }

        assert userConfs.isEmpty();

        return runConfs;
    }

    private static List<UserConf> pullUserConfs(List<UserConf> userConfs, int loadPerKurento) {
        List<UserConf> l = new ArrayList<>();
        final int iters = Math.min(userConfs.size(), loadPerKurento);
        for (int i = 0; i < iters; i++) {
            l.add(userConfs.get(0));
            userConfs.remove(0);
        }
        return l;
    }
}
