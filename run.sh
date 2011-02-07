#!/bin/bash
#
#     Copyright 2011 Membase, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# Description: Populate the test directories with the necessary files
#              and kick off the build
# Version: 1.0
# Author: Trond Norbye
#

popuate_file() {
    source="$1"
    if ! test -e ${source}
    then
        echo "FATAL: ${source} does not exists!"
        exit 1
    fi

    destination="$2"
    if ! test -e ${destination}
    then
        if ! test -d `dirname ${destination}`
        then
	    echo "Creating directory `dirname ${destination}`"
            mkdir -p `dirname ${destination}`
        fi
        echo "Installing file ${destination}"
        ln -s ${source} ${destination}
    fi
}

#
# Install all of the deps if they don't exist:
#
set -e
popuate_file ${HOME}/.m2/repository/com/sun/jet/jag-ops/1.0-SNAPSHOT/jag-ops-1.0-SNAPSHOT.jar .deps/testsuite/lib/jag-ops.jar
popuate_file ${HOME}/.m2/repository/com/sun/jet/jet/1.0-SNAPSHOT/jet-1.0-SNAPSHOT.jar .deps/testsuite/lib/jet.jar
popuate_file ${HOME}/.m2/repository/com/sun/jet/jet-batch/1.0-SNAPSHOT/jet-batch-1.0-SNAPSHOT.jar .deps/testsuite/lib/jet-batch.jar
popuate_file ${HOME}/.m2/repository/com/sun/jet/jet-util/1.0-SNAPSHOT/jet-util-1.0-SNAPSHOT.jar .deps/testsuite/lib/jet-util.jar
popuate_file ${HOME}/.m2/repository/org/membase/JMemcachedTest/1.0-SNAPSHOT/JMemcachedTest-1.0-SNAPSHOT.jar .deps/testsuite/lib/jmemcachetest.jar
popuate_file `pwd`/target/performance-testing-1.0-SNAPSHOT.jar .deps/testsuite/lib/testsuite.jar
set +e

#
# Set up the class-path:
#
my_classpath=".deps/testsuite/lib/testsuite.jar:.deps/testsuite/lib/jet-batch.jar"

PROP_PARAMS=""
for i in $*
do
    if ! test -e ${i}
    then
        echo "FATAL: property file \"${i}\" does not exists!"
        exit 1
    fi
    PROP_PARAMS="${PROP_PARAMS} -propfile ${i}"
done

#
# Be nice to the user and give a default setup if they didn't provide any
# configuration files
#
if test "x${PROP_PARAMS}" = "x"
then
    for i in config/batches/basic.properties \
             config/platforms/common.properties \
             config/platforms/`uname -s`-`uname -p`.properties \
             config/users/`logname`.properties
    do
        if ! test -e ${i}
        then
            echo "FATAL: property file \"${i}\" does not exists!"
            exit 1
        fi
        PROP_PARAMS="${PROP_PARAMS} -propfile ${i}"
    done
fi

exec java -Xms64m -Xmx512m -cp ${my_classpath} \
          com.sun.jet.batch.JETBatch -reportdir results/batch ${PROP_PARAMS}
