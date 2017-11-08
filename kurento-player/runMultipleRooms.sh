#!/bin/bash

trap 'kill $(jobs -pr)' SIGINT SIGTERM EXIT

NO_OF_ROOMS=$1
for i in `seq 1 $NO_OF_ROOMS`; do
    ./runUser.sh `echo test$i` 3 &
done

wait
