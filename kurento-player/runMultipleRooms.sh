#!/usr/bin/env bash

# Usage:
# runMultipleRooms NO_OF_ROOMS [NO_OF_PUBS_PER_ROOM=3] [ROOM_PREFIX='test']

trap "ps aux | grep ':8888/kurento' | grep -v grep | awk '{ print \$2 }' | xargs kill" SIGINT SIGTERM EXIT

NO_OF_ROOMS=$1
NO_OF_PUBS_PER_ROOM=${2:=3}
ROOM_PREFIX=${3:=test}

for i in `seq 1 ${NO_OF_ROOMS}`; do
    ./runUser.sh `echo ${ROOM_PREFIX}${i}` ${NO_OF_PUBS_PER_ROOM} &
done

wait
