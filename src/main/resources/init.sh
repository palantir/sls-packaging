#!/bin/bash
#
# Copyright 2015 Palantir Technologies
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# <http://www.apache.org/licenses/LICENSE-2.0>
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ACTION=$1
SERVICE="@serviceName@"
PIDFILE="var/run/$SERVICE.pid"
ARGS="@args@"

# uses SERVICE_HOME when set, else, traverse up two directories respecting symlinks
SERVICE_HOME=${SERVICE_HOME:-$(cd "$(dirname "$0")/../../" && pwd)}
cd "$SERVICE_HOME"

is_process_active() {
   local PID=$1
   ps $PID > /dev/null;
   echo $?
}

case $ACTION in
start)
    $0 status > /dev/null 2>&1
    if [[ $? == 0 ]]; then
        echo "Process is already running"
        exit 1
    fi
    printf "%-50s" "Running '$SERVICE'..."
    
    # ensure log and pid directories exist
    mkdir -p "var/log"
    mkdir -p "var/run"
    PID=$(service/bin/$SERVICE $ARGS > var/log/$SERVICE-startup.log 2>&1 & echo $!)
    sleep 1
    if [ $(is_process_active $PID) -eq 0 ]; then
        echo $PID > $PIDFILE
        printf "%s\n" "Ok"
        exit 0
    else
        printf "%s\n" "Fail"
        exit 1
    fi
;;
status)
    printf "%-50s" "Checking '$SERVICE'..."
    if [ -f $PIDFILE ]; then
        PID=$(cat $PIDFILE)
        if [[ $(is_process_active $PID) -eq 0 ]]; then
            ps -o command $PID | grep -q "$SERVICE"
            if [[ $? == 0 ]]; then
                printf "%s\n" "Running"
                exit 0
            fi
        fi
        printf "%s\n" "Process dead but pidfile exists"
        exit 1
    else
        printf "%s\n" "Service not running"
        exit 3
    fi
;;
stop)
    printf "%-50s" "Stopping '$SERVICE'..."
    $0 status > /dev/null 2>&1
    if [[ $? == 0 ]]; then
        PID=$(cat $PIDFILE)
        kill $PID
        sleep 4
        $0 status > /dev/null 2>&1
        if [[ $? == 0 ]]; then
            printf "%s\n" "Failed"
            exit 1
        else
            rm -f $PIDFILE
            printf "%s\n" "Ok"
            exit 0
        fi
    else
        rm -f $PIDFILE
        printf "%s\n" "Not running"
        exit 0
    fi
;;
*)
    echo "Usage: $0 {status|start|stop}"
    exit 1
esac
