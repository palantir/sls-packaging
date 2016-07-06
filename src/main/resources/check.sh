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

# Everything in this script is relative to the base directory of an SLSv2 distribution
pushd "`dirname \"$0\"`/../../.." > /dev/null

# Prefer java 8 when available to deal with poor environment management
if [ -n "$JAVA_8_HOME" ]; then
    export JAVA_HOME=$JAVA_8_HOME
fi

service/bin/init.sh check
