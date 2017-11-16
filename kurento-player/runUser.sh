#!/usr/bin/env bash

mvn compile -Denforcer.skip=true exec:java \
    -Dbenchmark.kurentos="67.207.93.65" \
    -Dexec.mainClass="org.kurento.tutorial.player.UserApp" \
    -Dexec.args="$@"
