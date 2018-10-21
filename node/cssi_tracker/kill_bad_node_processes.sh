#!/bin/bash -x

# apparently, when 'npm start' is used to launch a server, ^C does NOT cause it to stop
# merely suspend

# this script should kill mis-behaving node.exe processes

  #544  ps -W | grep "node.exe" | awk '{print $1}' | grep "^20616" | xargs powershell kill
  #545  history > kill_bad_node_processes.sh

# the 'grep "^14" ' part is just me being cautious...
#for PID in `ps -W | grep "node.exe" | awk '{print $1}' | grep "^11" ` ;  do

# 'npm start' starts _lots_ of processes;
#  killing the _first_ one seems to take care of all of them

for PID in `ps -W | grep "node.exe" | head -1 | awk '{print $1}' ` ;  do

    echo "about to kill PID=$PID"

    powershell kill $PID
done

