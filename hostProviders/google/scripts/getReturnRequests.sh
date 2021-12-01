#!/bin/sh
# This script:
#    - should be called as getReturnRequests.sh -f input.json
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
else
    echo "Java not installed. Google cloud provider plugin requires Java version 1.8 or up"
    exit 1
fi
 
if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    #echo version "$version"
    if [[ "$version" < "1.8" ]]; then
        echo "Java version error. Google cloud provider plugin requires Java version 1.8 or up"
        exit 1
    fi  
fi

$_java $SCRIPT_OPTIONS -Dgoogle-home-dir=$homeDir -jar $homeDir/lib/GcloudTool.jar --getReturnRequests $homeDir $inJson
