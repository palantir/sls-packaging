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

SERVICE="@serviceName@"
SERVICE_CMD="service/bin/$SERVICE"
PIDFILE="var/run/$SERVICE.pid"
CHECK_ARGS="@checkArgs@"

# uses SERVICE_HOME when set, else, traverse up three directories respecting symlinks
# (this file emits to service/monitoring/bin)
SERVICE_HOME=${SERVICE_HOME:-$(cd "$(dirname "$0")/../../../" && pwd)}
cd "$SERVICE_HOME"

# prefer java 8 when available to deal with poor environment management
if [ -n "$JAVA_8_HOME" ]; then
    export JAVA_HOME=$JAVA_8_HOME
fi

source service/bin/config.sh

# now check health
printf "%-50s" "Checking health of '$SERVICE'..."
$SERVICE_CMD $CHECK_ARGS
RESULT=$?
if [ $RESULT -eq 0 ]; then
    printf "%s\n" "Healthy"
    exit 0
else
    printf "%s\n" "Unhealthy"
    exit $RESULT
fi
