package org.kurento.tutorial.player;

import org.kurento.client.KurentoClient;

public class UserConf {
    private UserApp userApp;
    private String room;
    private String userName;

    public UserConf(UserApp userApp, String room, String userName) {
        this.userApp = userApp;
        this.room = room;
        this.userName = userName;
    }

    public UserApp getUserApp() {
        return userApp;
    }

    public String getRoom() {
        return room;
    }

    public String getUserName() {
        return userName;
    }

    public void run(KurentoClient kurentoClient) throws Exception {
        userApp.run(kurentoClient, room, userName);
    }
}
