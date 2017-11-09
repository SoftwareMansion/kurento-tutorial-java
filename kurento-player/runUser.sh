#!/usr/bin/env bash

mvn compile -Denforcer.skip=true exec:java \
    -Dexec.mainClass="org.kurento.tutorial.player.UserApp" \
    -Dkms.url="ws://localhost:8888/kurento" \
    -Dexec.args="$@"
