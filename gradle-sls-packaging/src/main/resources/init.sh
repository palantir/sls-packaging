#!/bin/bash
#
# Copyright 2016 Palantir Technologies
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

# Everything in this script is relative to the base directory of an SLSv2 distribution
pushd "`dirname \"$0\"`/../.." > /dev/null

# Select launcher binary for this OS
case "`uname`" in
  Linux*)
    LAUNCHER_CMD=service/bin/linux-amd64/go-java-launcher
    GO_INIT_CMD=service/bin/linux-amd64/go-init
    ;;
  Darwin*)
    LAUNCHER_CMD=service/bin/darwin-amd64/go-java-launcher
    GO_INIT_CMD=service/bin/darwin-amd64/go-init
    ;;
  *)
    echo "Unsupported operating system: $(uname)"; exit 1
esac

ACTION=$1
SCRIPT_DIR="service/bin"
SERVICE="@serviceName@"
PIDFILE="var/run/$SERVICE.pid"
STATIC_LAUNCHER_CONFIG="service/bin/launcher-static.yml"
CUSTOM_LAUNCHER_CONFIG="var/conf/launcher-custom.yml"
STATIC_LAUNCHER_CHECK_CONFIG="service/bin/launcher-check.yml"

DEPRECATION_MESSAGE="Command is deprecated: the next major release of sls-packaging will only support start/status/stop"

case $ACTION in
start)
    $GO_INIT_CMD start
;;
status)
    $GO_INIT_CMD status
;;
stop)
    $GO_INIT_CMD stop
;;
console)
    echo $DEPRECATION_MESSAGE
    if service/bin/init.sh status &> /dev/null; then
        echo "Process is already running"
        exit 1
    fi
    trap "service/bin/init.sh stop &> /dev/null" SIGTERM EXIT
    mkdir -p "$(dirname $PIDFILE)"

    $LAUNCHER_CMD $STATIC_LAUNCHER_CONFIG $CUSTOM_LAUNCHER_CONFIG &
    echo $! > $PIDFILE
    wait
;;
restart)
    echo $DEPRECATION_MESSAGE
    service/bin/init.sh stop
    service/bin/init.sh start
;;
check)
    echo $DEPRECATION_MESSAGE
    printf "%-50s" "Checking health of '$SERVICE'..."
    $LAUNCHER_CMD $STATIC_LAUNCHER_CHECK_CONFIG > var/log/$SERVICE-check.log 2>&1
    RESULT=$?
    if [ $RESULT -eq 0 ]; then
        printf "%s\n" "Healthy"
        exit 0
    else
        printf "%s\n" "Unhealthy"
        exit $RESULT
    fi
;;
*)
    # Support arbitrary additional actions; e.g. init-reload.sh will add a "reload" action
    if [[ -f "$SCRIPT_DIR/init-$ACTION.sh" ]]; then
        echo $DEPRECATION_MESSAGE
        export LAUNCHER_CMD
        shift
        /bin/bash "$SCRIPT_DIR/init-$ACTION.sh" "$@"
        exit $?
    else
        COMMANDS=$(ls $SCRIPT_DIR | sed -ne '/init-.*.sh/ { s/^init-\(.*\).sh$/|\1/g; p; }' | tr -d '\n')
        echo "Usage: $0 {status|start|stop|console|restart|check${COMMANDS}}"
        echo "All commands but start/status/stop are deprecated: the next major release will only support these commands"
        exit 1
    fi
esac
