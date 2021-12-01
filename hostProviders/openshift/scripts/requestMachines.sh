#!/bin/sh -x
# This script:
#    - should be called as requestMachines.sh -f input.json
#    - exit with 0 if calling succeed and result will be in the stdOut
#    - exit with 1 if calling failed and error message will be in the stdOut
#
MYTMP="$(PATH=/sbin:/usr/sbin:/bin:/usr/bin mktemp -d)"
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
 
if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    #echo version "$version"
    if [[ "$version" < "1.8" ]]; then
        echo "Java version error. OpenShift provider plugin requires Java, version 1.8, or later"
        exit 1
    fi  
fi

temp_lsf_hosts_file=${MYTMP}/temp_lsf_host_file
lsf_hosts_file=${LSF_ENVDIR}/hosts
lsf_conf_file=${LSF_ENVDIR}/lsf.conf
lsf_top_dir=` echo $LSF_ENVDIR |sed -n -e 's@\(.*\)/conf@\1@p'`
sed -n -e '/# BEGIN_HOSTS_MANAGED_BY_RESOURCE_CONNECTOR/,/# END_HOSTS_MANAGED_BY_RESOURCE_CONNECTOR/! p' ${lsf_hosts_file} | sed -n -e '/lsf-rc-/! p' >  ${temp_lsf_hosts_file}
ENABLE_EGO=`sed -n -e 's/^[ \t]*LSF_ENABLE_EGO=\(.*\)/\1/p' ${lsf_conf_file}`

$_java $SCRIPT_OPTIONS -Dhome-dir=$homeDir -DLSF_TOP_DIR=${lsf_top_dir} -DTEMP_HOSTS_FILE=${temp_lsf_hosts_file} -DLSF_HOSTS_FILE=${LSF_ENVDIR}/hosts -DENABLE_EGO=${ENABLE_EGO} -jar $homeDir/lib/ocTool.jar --requestMachines $homeDir $inJson

rm -rf ${MYTMP} >/dev/null 2>&1
