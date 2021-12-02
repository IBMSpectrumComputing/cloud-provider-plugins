#!/bin/sh
# This script:
#    - should be called asrequestReturnMachines -f input.json
#    - exit with 0 if calling succeed and result will be in the stdOut
#    - exit with 1 if calling failed and error message will be in the stdOut
#
inJson=$2
scriptDir=`dirname $0`
homeDir="$(cd "$scriptDir" && cd .. && pwd)"

# check if the required Java version is installed
if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    #echo found java executable in JAVA_HOME
    _java="$JAVA_HOME/bin/java"
elif type -p java >/dev/null 2>&1; then
    #echo found java executable in PATH
    _java=java
elif [ -x "/root/openjdk/bin/java" ]; then
    _java=/root/openjdk/bin/java
else
    echo "Java is not installed. OpenShift provider plugin requires Java, version 1.8, or later"
    exit 1
fi

if ! which bc > /dev/null; then
   echo -e "Command bc not found! please install \c"
fi
 
if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}'|cut -f1-2 -d .)
    #echo version "$version"
    if (( $(echo "$version < 1.8" |bc -l) )); then
        echo "Java version error. OpenShift provider plugin requires Java, version 1.8, or later"
        exit 1
    fi  
fi

$_java $SCRIPT_OPTIONS -Dhome-dir=$homeDir -jar $homeDir/lib/ocTool.jar --requestReturnMachines $homeDir $inJson
