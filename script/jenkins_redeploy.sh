#!/bin/bash

LISTENING_PORT=8080
RUN_DIR=/home/scaladex/run
mkdir -p $RUN_DIR
echo "======== Running redeploy for Jenkins build =========="
echo "--------------------------------"
echo -n "My id is:"
id
echo "Environment:"
env
echo "--------------------------------"
echo "========= If process is running, Kill it ========"
[[ -e ${RUN_DIR}/PID ]] && kill $(cat ${RUN_DIR}/PID)
PID2=$(lsof -i:${LISTENING_PORT} -t)
if [ -n "$PID2" ]
then
    echo "Process with PID $PID2 is listening on port ${LISTENING_PORT}"
    [[ -e /proc/$PID2 ]] && kill $PID2
    sleep 1
    [[ -e /proc/$PID2 ]] && kill -9 $PID2
fi
echo "========= Starting Scaladex: ========"
echo "Systemd should take care of restarting automatically the process, checking.$
maxdelay=10
delay=$maxdelay
IS_SCALADEX_RUNNING=0
while [ $delay -gt 0 ]; do
        PID3=$(lsof -i:${LISTENING_PORT} -t)
        #echo "PID3 is >>$PID3<<"
        if [ -n "$PID3" ]
        then
                echo "Scaladex is listening on port ${LISTENING_PORT} with PID $P$
                IS_SCALADEX_RUNNING=1
                break
        fi
        delay=$(($delay - 1))
        sleep 1
done

if [ $IS_SCALADEX_RUNNING = 0 ]
then
        echo "Not running after $maxdelay seconds, starting it !!!!!"
        cd $RUN_DIR
        echo "Indexing"
        /home/scaladex/scaladex/data/current/data/bin/data \
          -DELASTICSEARCH=remote
          -J-Xms1G \
          -J-Xmx3G \
          -Dlogback.configurationFile=/home/scaladex/bin/logback.xml \
          -Dconfig.file=/home/scaladex/scaladex-credentials/application.conf > \
          /home/scaladex/scaladex-contrib \
          /home/scaladex/scaladex-index \
          /home/scaladex/scaladex-credentials \
          /home/scaladex/run/data.log 2> \
          /home/scaladex/run/data.log &

        echo "Starting Webserver"
        nohup /home/scaladex/scaladex/server/current/scaladex/bin/server \
          -Dlogback.configurationFile=/home/scaladex/bin/logback.xml \
          8080 \
          /home/scaladex/scaladex-contrib \
          /home/scaladex/scaladex-index \
          /home/scaladex/scaladex-credentials \
          -Dconfig.file=/home/scaladex/scaladex-credentials/application.conf > \
          /home/scaladex/run/scaladex.log 2> \
          /home/scaladex/run/scaladex.log &
fi

echo "======== End =========="
exit 0
