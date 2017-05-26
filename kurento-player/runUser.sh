ROOM_NAME=$1
USERS_COUNT=$2
mvn compile -Denforcer.skip=true exec:java \
    -Dexec.mainClass="org.kurento.tutorial.player.UserApp" \
    -Dkms.url="ws://52.212.147.1:8888/kurento" \
    -DkurentoRoom.url="wss://kurento.htdwork.com:8443/room" \
    -Dexec.args="$ROOM_NAME $USERS_COUNT"
