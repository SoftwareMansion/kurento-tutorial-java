ROOM_NAME=$1
USERS_COUNT=$2
mvn clean
mvn compile -Denforcer.skip=true exec:java \
    -Dexec.mainClass="org.kurento.tutorial.player.UserApp" \
    -Dkms.url="ws://52.18.241.211:8888/kurento" \
    -DkurentoRoom.url="wss://kurento.htdwork.com:8443/room" \
    -Dexec.args="$ROOM_NAME $USERS_COUNT"
