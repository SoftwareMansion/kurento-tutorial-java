ROOM_NAME=$1
USERS_COUNT=$2
mvn compile -Denforcer.skip=true exec:java \
    -Dexec.mainClass="org.kurento.tutorial.player.UserApp" \
    -Dkms.url="ws://138.68.108.51:8888/kurento" \
    -Dexec.args="$ROOM_NAME $USERS_COUNT"
