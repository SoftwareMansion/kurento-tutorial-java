ROOM_NAME=$1
STREAMER_NAME=$2
mvn compile -Denforcer.skip=true exec:java \
    -Dexec.mainClass="org.kurento.tutorial.player.TestStreamerRoom" \
    -Dkms.url="wss://htdtest3.htdwork.com/kurento" \
    -DkurentoRoom.url="wss://kurento.htdwork.com:8443/room" \
    -Dexec.args="$ROOM_NAME $STREAMER_NAME"
