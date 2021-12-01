#!/bin/bash
#-----------------------------------
# Copyright IBM Corp. 1992, 2017. All rights reserved.
# US Government Users Restricted Rights - Use, duplication or disclosure
# restricted by GSA ADP Schedule Contract with IBM Corp.
#-----------------------------------

log() {
    local msg="$*"
    echo "`date +%F\ %H:%M:%S,%3N` - $1" >> ${LOG_FILE}
}

log_info () {
    local msg="INFO - $*"
    log "${msg}"
}

log_err() {
    local msg="ERROR - $*"
    log "${msg}"
}

log_info_sto () {
    local msg="$*"
    log_info "${msg}"
    echo "${msg}"
}

fail_and_exit() {
    local msg="$1"
    if [ -z "$msg" ]; then
        log_info_sto "An error occurred during installation. Check the $LOG_FILE for detailed error messages."
    else
        log_info_sto "$msg"
    fi
    rm -rf $MYTMP
    exit 1
}

check_lsf_operator()
{

    ${oc_command} get ns ${name_space}
    if [ "$?" != "0" ]; then
	fail_and_exit "namespace \"${name_space}\" not found."
    fi

    lsf_operator=`${oc_command} get pod --no-headers -l "name=ibm-lsf-operator" -n ${name_space} 2> /dev/null`
    if [ "x${lsf_operator}" = "x" ]; then
	log_info_sto "ibm-lsf-operator not found in the \"${name_space}\" namespace."
    fi

    # compute_image
    ${oc_command} get LSFCluster -n ${name_space} 2> /dev/null
    if [ "$?" != "0" ]; then
	log_info_sto "LSFCluster not found in the \"${name_space}\" namespace."
    else
	compute_image=`${oc_command} get LSFCluster -o jsonpath="{.items[0].spec.computes[0].image}" -n ${name_space} 2> /dev/null`
    fi

    if [ "x${compute_image}" = "x" ]; then
	local lsf_server=`${oc_command} get pod --no-headers  -l "app.kubernetes.io/instance=lsf,role=agent" -o jsonpath="{.items[0].metadata.name}" -n ${name_space} 2> /dev/null`
	if [ "x${lsf_server}" = "x" ]; then
	    pod_list=`${oc_command} get pods --no-headers -n ${name_space}|sed -n -e 's/^\([^ \t]\+\)[ \t].*/\1/p'`
	    for a_pod in ${pod_list}; do
		hasOne=`${oc_command} get pod ${a_pod} -o jsonpath="{.spec.containers[0].image}" -n ${name_space}|sed -n -e '\|/lsf-comp-|p'`
		if [ "x${hasOne}" != "x" ]; then
		    lsf_server=${a_pod}
		    break
		fi
	    done
	fi
	if [ "x${lsf_server}" != "x" ]; then
	    compute_image=`${oc_command} get pod ${lsf_server} -o jsonpath="{.spec.containers[0].image}" -n ${name_space}`
	fi
    fi
    if [ "x${compute_image}" = "x" ]; then
	fail_and_exit "ibm-spectrum-lsf compute image not found in the \"${name_space}\" namespace."
    fi

    # lsf_master 
    lsf_master_name=`${oc_command} get pod  -l "app.kubernetes.io/instance=lsf,role=master"  -o jsonpath="{.items[0].metadata.name}" -n ${name_space} 2> /dev/null`
    if [ "x${lsf_master_name}" = "x" ]; then
	pod_list=`${oc_command} get pods --no-headers -n ${name_space}|sed -n -e 's/^\([^ \t]\+\)[ \t].*/\1/p'`
	for a_pod in ${pod_list}; do
	    hasOne=`${oc_command} get pod ${a_pod} -o jsonpath="{.spec.containers[0].image}" -n ${name_space}|sed -n -e '\|/lsf-master-|p'`
	    if [ "x${hasOne}" != "x" ]; then
		lsf_master_name=${a_pod}
		break
	    fi
	done
    fi
    if [ "x${lsf_master_name}" = "x" ]; then
	fail_and_exit "LSF management pod not found in the \"${name_space}\" namespace."
    fi

    lsf_master_phase=`${oc_command} get pod  ${lsf_master_name} -o jsonpath="{.status.phase}" -n ${name_space} 2> /dev/null`
    if [ "x${lsf_master_phase}" != "xRunning" ]; then
	fail_and_exit "LSF management pod not in the Running phase."
    fi

    local hasLsfConfDir=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c 'if [ -f '"${lsf_topdir}"'/conf/lsf.conf ]; then echo -n "Y"; else echo -n "N"; fi'`
    if [ "x${hasLsfConfDir}" != "xY" ]; then
	fail_and_exit "${lsf_topdir}/conf/lsf.conf was not found on the LSF management pod <${lsf_master_name}>. Specify -d <LSF_TOP> (where lsf.conf is located in the <LSF_TOP>/conf directory)."
    fi

    lsftype=`${oc_command} get pod  ${lsf_master_name} -o jsonpath="{.metadata.labels.lsftype}" -n ${name_space} 2> /dev/null`

    operator_cluster=`${oc_command} get networkpolicy lsf-np -o 'jsonpath={.spec.podSelector.matchLabels.lsfcluster}' -n ${name_space} 2> /dev/null`
    if [ "x${operator_cluster}" = "x" ]; then
	np_list=`${oc_command} get networkpolicy --no-headers -n lsf |sed -n -e 's/^\([^ \t]\+\)[ \t].*/\1/p'`
	for a_np in ${np_list}; do
	    operator_cluster=`${oc_command} get networkpolicy ${a_np} --no-headers  -o jsonpath="{.spec.podSelector.matchLabels.lsfcluster}" -n ${name_space} 2> /dev/null`
	    if [ "x${operator_cluster}" != "x" ]; then
		break
	    fi
	done
    fi

    userauth_args=`${oc_command} get LSFCluster -o jsonpath="{.items[0].spec.cluster.userauth.authconfigargs}" -n ${name_space} 2> /dev/null`

    userauth_starts=`${oc_command} get LSFCluster -o jsonpath="{.items[0].spec.cluster.userauth.starts}" -n ${name_space} 2> /dev/null | sed -e 's/\[//g' -e 's/\]//g' -e 's/,/ /g'`

}

fill_blank()
{
    local filepath=$1
    sed -i -e "s/@_SA_NAME_@/${service_account}/g" ${filepath}
    sed -i -e "s/@_NAMESPACE_@/${name_space}/g" ${filepath}

}

prepare_service_account()
{

    local yaml_dir=`echo -n ${cmd} | sed -n -e 's@^\(.*\)/[^ \t/]\+$@\1@p'`
    local yaml_files="\
lsf_rc_sa.yaml \
lsf_rc_role.yaml \
lsf_rc_role_binding.yaml \
lsf_rc_cluster_role.yaml \
lsf_rc_cluster_role_binding.yaml \
lsf_subdomain.yaml \
"

    for yaml_file in ${yaml_files}; do
        cp ${yaml_dir}/${yaml_file} ${MYTMP}/
        fill_blank  ${MYTMP}/${yaml_file}
        local kind=`cat ${MYTMP}/${yaml_file}|sed -n -e 's/^kind: \(.*\)/\1/p'`
        local name=`cat ${MYTMP}/${yaml_file}| sed -n -e '/name:/{s/name: \(.*\)/\1/p;q}'`
        local hasOne=`${oc_command} get ${kind} ${name} -n ${name_space} 2> /dev/null`
        if [ "x${hasOne}" = "x" ]; then
	    ${oc_command} create -f ${MYTMP}/${yaml_file}
            if [ "$?" != "0" ]; then
	        fail_and_exit "${oc_command} create -f ${yaml_file} failed."
            fi
        fi
    done
     
    sa_token_secret=`${oc_command} get sa ${service_account} -o jsonpath='{.secrets}'|sed -e 's/.*\[name:\('"${service_account}"'-token-[^]]\+\)\].*/\1/'`
    if [ "x`echo ${sa_token_secret}| sed -n -e '/:/p'`" != "x" ]; then
        # multiple secrets 
	sa_token_secret=`${oc_command} get sa ${service_account} -o jsonpath='{.secrets}'|sed -n -e 's/.*"name":"\('"${service_account}"'-token-[^"}]\+\)"}.*/\1/p'`
    fi
    if [ "x${sa_token_secret}" = "x" ]; then
	fail_and_exit "Cannot get the service account secret."
    fi
    sa_token=`${oc_command} get secret ${sa_token_secret} -o jsonpath='{.data.token}'| base64 --decode`
    if [ "x${sa_token}" = "x" ]; then
	fail_and_exit "Cannot get the service account token."
    fi

}

configure_rc_on_master_pod()
{
    local rc_openshift_topdir=${lsf_topdir}/conf/resource_connector/openshift
    local rc_conf_filepath=${rc_openshift_topdir}/conf/openshiftprov_config.json
    local rc_template_filepath=${rc_openshift_topdir}/conf/openshiftprov_templates.json
    local TMP_CMD=exec_lsf_rc_config

    cat << EXECIT > ${MYTMP}/${TMP_CMD}
#!/bin/bash

echoErr()
{
    local msg="\$1"
    echo -n "ERR_MSG=\${msg}"
}
rollback_conf_bak()
{
    # move .conf_bak to conf
    local d_bak=${rc_openshift_topdir}/.conf_bak
    local d=${rc_openshift_topdir}/conf
    if [ -d "\${d_bak}" ]; then
	rm -rf "\${d}"
	mv -f "\${d_bak}" "\${d}"
    fi
}
    rollback_conf_bak
    if [ ! -f ${rc_conf_filepath} ]; then
	echoErr  "${rc_conf_filepath} not found."
	exit 1
    fi
    if [ ! -f ${rc_template_filepath} ]; then
	echoErr  "${rc_template_filepath} not found."
	exit 1
    fi

    sed -i -e 's|^\([ \t]*"NAME_SPACE":[ \t]*\)"@NAME_SPACE@"\(.*\)|\1"'"${name_space}"'"\2|g' ${rc_conf_filepath}
    sed -i -e 's|^\([ \t]*"SERVICE_ACCOUNT":[ \t]*\)"@SERVICE_ACCOUNT@"\(.*\)|\1"'"${service_account}"'"\2|g' ${rc_conf_filepath}
    sed -i -e 's|^\([ \t]*"SERVICE_ACCOUNT_TOKEN":[ \t]*\)"@SERVICE_ACCOUNT_TOKEN@"\(.*\)|\1"'"${sa_token}"'"\2|g' ${rc_conf_filepath}

    sed -i -e 's|^\([ \t]*"imageId":[ \t]*\)"@IMAGE_ID@"\(.*\)|\1"'"${compute_image}"'"\2|g' ${rc_template_filepath}
    # overwrite
    sed -i -e 's|^\([ \t]*"NAME_SPACE":[ \t]*"\).*\(".*\)$|\1'"${name_space}"'\2|g' ${rc_conf_filepath}
    sed -i -e 's|^\([ \t]*"SERVICE_ACCOUNT":[ \t]*"\).*\(".*\)$|\1'"${service_account}"'\2|g' ${rc_conf_filepath}
    sed -i -e 's|^\([ \t]*"SERVICE_ACCOUNT_TOKEN":[ \t]*"\).*\(".*\)$|\1'"${sa_token}"'\2|g' ${rc_conf_filepath}
    # LSF_OPERATOR_CLUSTER
    if [ "x${operator_cluster}" != "x" ]; then 
	sed -i -e '/"LSF_OPERATOR_CLUSTER"/d' ${rc_conf_filepath}
	sed -i -e '/"NAME_SPACE":/a "LSF_OPERATOR_CLUSTER": "'"${operator_cluster}"'",' ${rc_conf_filepath}
    fi
    # WAIT_POD_IP
    if [ "x${wait_pod_ip}" != "x" ]; then 
	sed -i -e '/"WAIT_POD_IP"/d' ${rc_conf_filepath}
	if [ "${wait_pod_ip}" = "Y" ]; then 
	    sed -i -e '/"NAME_SPACE":/a "WAIT_POD_IP": "'"${wait_pod_ip}"'",' ${rc_conf_filepath}
	fi
    fi
    # TIMEOUT_IN_SEC
    if [ "x${timeout_in_sec}" != "x" ]; then 
	sed -i -e '/"TIMEOUT_IN_SEC"/d' ${rc_conf_filepath}
	sed -i -e '/"NAME_SPACE":/a "TIMEOUT_IN_SEC": "'"${timeout_in_sec}"'",' ${rc_conf_filepath}
    fi
    if [ "x${userauth_args}" != "x" -o "x${userauth_starts}" != "x" ]; then
	hasEnvVars=\`sed -n -e '/"ENV_VARS":/p' ${rc_conf_filepath}\`
	if [ "x\${hasEnvVars}" = "x" ]; then
	    sed -i -e '/"NAME_SPACE":/a \},' ${rc_conf_filepath}
	    sed -i -e '/"NAME_SPACE":/a "ENV_VARS": {' ${rc_conf_filepath}
	fi
	   
	# AUTHCFGARGS
	if [ "x${userauth_args}" != "x" ]; then
	    sed -i -e '/"AUTHCFGARGS"/d' ${rc_conf_filepath}
	    sed -i -e '/"ENV_VARS":/a "AUTHCFGARGS": "'"${userauth_args}"'",' ${rc_conf_filepath}
	fi
	# AUTHDAEMONS
	if [ "x${userauth_starts}" != "x" ]; then
	    sed -i -e '/"AUTHDAEMONS"/d' ${rc_conf_filepath}
	    sed -i -e '/"ENV_VARS":/a "AUTHDAEMONS": "'"${userauth_starts}"'",' ${rc_conf_filepath}
	fi
        
	sed -i -e '$ ! {H;d};x;G; s/",[ ^t]*\n\([ \t]*},\)/"\n\1/g;' ${rc_conf_filepath}
	sed -i -e '1 {/$/ d}' ${rc_conf_filepath}
    fi

    # imageId 
    #sed -i -e '\|^[ \t]*{[ \t]*$|,\|^[ \t]*}[ \t]*$| {\|"templateId":[ \t]*"tmplate-example"|{N;N;N;N;N;N;N;s|\(.*[ \t]*"imageId":[ \t]*"\).*\(".*\)|\1'"${compute_image}"'\2|}}' ${rc_template_filepath}


    # configure lsf
    lsf_confdir=${lsf_topdir}/conf

EXECIT
    cat << 'EXECLSF' >> ${MYTMP}/${TMP_CMD}

    lsf_conf_file=${lsf_confdir}/lsf.conf
    lsf_shared_file=${lsf_confdir}/lsf.shared
    #cluster_name=`sed -n -e '/Begin Cluster/,/End Cluster/ {/Begin Cluster/d; /End Cluster/d;/^[ \t]*ClusterName/d;/^[ \t]*#/d;s/^\([^ \t]\+\).*/\1/p}' ${lsf_shared_file}`
    cluster_name=

    lsf_master_list=`sed -n -e 's/^[ \t]*LSF_MASTER_LIST=\([^#]\+\)/\1/p' ${lsf_conf_file} |sed -e 's/"//g'`

    cluster_name_list=`sed -n -e '/Begin Cluster/,/End Cluster/ {/Begin Cluster/d; /End Cluster/d;/^[ \t]*ClusterName/d;/^[ \t]*#/d;s/^\([^ \t]\+\).*/\1/p}' ${lsf_shared_file}`

    found=N
    for a_cluster_name in ${cluster_name_list}; do
	a_cluster_file=${lsf_confdir}/lsf.cluster.${a_cluster_name}
	if [ ! -f "${a_cluster_file}" ]; then
	     continue
	fi
	for a_master in ${lsf_master_list}; do
	     hasOne=`sed -n -e '/^[ \t]*Begin[ \t]\+Host/,/^[ \t]*End[ \t]\+Host/ {/'"${a_master}"'/p}' ${a_cluster_file}`
	     if [ "x${hasOne}" != "x" ]; then
		 found=Y
	     fi
	done
	if [ ${found} = "Y" ]; then
	    cluster_name=${a_cluster_name}
	    break
	fi
    done
    if [ ${found} = "N" ]; then
	echoErr "LSF cluster name not found."
	exit 1
    fi

    lsf_cluster_file=${lsf_confdir}/lsf.cluster.${cluster_name}
    lsb_modules_file=${lsf_confdir}/lsbatch/${cluster_name}/configdir/lsb.modules
    lsb_queues_file=${lsf_confdir}/lsbatch/${cluster_name}/configdir/lsb.queues

    hasOne=
    restart_lsf=N
    
    # lsf.conf
    hasOne=`sed -n -e '/^[ \t]*LSB_RC_EXTERNAL_HOST_FLAG=/p' ${lsf_conf_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e '$ a LSB_RC_EXTERNAL_HOST_FLAG="openshift"' ${lsf_conf_file}
        restart_lsf=Y
    else
        found=N
        host_flags=`sed -n -e 's/^[ \t]*LSB_RC_EXTERNAL_HOST_FLAG=\([^#]\+\).*/\1/p' ${lsf_conf_file} | sed -e 's/"//g'`
        for flag in ${host_flags}; do
            if [ "${flag}" = "openshift" ]; then
                 found=Y
            fi
        done
        if [ "${found}" = "N" ]; then
            host_flags="${host_flags} openshift"
            sed -i -e 's/^[ \t]*\(LSB_RC_EXTERNAL_HOST_FLAG=\).*/\1"'"${host_flags}"'"/g' ${lsf_conf_file}
	    restart_lsf=Y
        fi
    fi
    hasOne=`sed -n -e '/^[ \t]*LSF_DYNAMIC_HOST_WAIT_TIME=/p' ${lsf_conf_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e '$ a LSF_DYNAMIC_HOST_WAIT_TIME=60' ${lsf_conf_file}
        restart_lsf=Y
    fi
    
    # lsf.shared
    hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*openshift[ \t]\+/p}' ${lsf_shared_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e  '/Begin Resource/,/End Resource/ {\@^[ \t]*#\+[ \t]*openshift[ \t]\+@ {s@^[ \t]*#\+\([ \t]*openshift[ \t]\+.*\).*@\1@ ;t;}}' ${lsf_shared_file}
	hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*openshift[ \t]\+/p}' ${lsf_shared_file}`
	if [ "x${hasOne}" = "x" ]; then
	    sed -i -e '/Begin Resource/,/End Resource/ { /End Resource/ { i openshift Boolean    ()       ()       (instances from OpenShift)' -e '}}' ${lsf_shared_file}
	fi
        restart_lsf=Y
    fi
    hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*rc_account[ \t]\+/p}' ${lsf_shared_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e  '/Begin Resource/,/End Resource/ {\@^[ \t]*#\+[ \t]*rc_account[ \t]\+@ {s@^[ \t]*#\+\([ \t]*rc_account[ \t]\+.*\).*@\1@ ;t;}}' ${lsf_shared_file}
	hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*rc_account[ \t]\+/p}' ${lsf_shared_file}`
	if [ "x${hasOne}" = "x" ]; then
	    sed -i -e '/Begin Resource/,/End Resource/ { /End Resource/ { i rc_account String     ()       ()       (account name for the external hosts)' -e '}}' ${lsf_shared_file}
	fi
        restart_lsf=Y
    fi
    hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*clusterName[ \t]\+/p}' ${lsf_shared_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e  '/Begin Resource/,/End Resource/ {\@^[ \t]*#\+[ \t]*clusterName[ \t]\+@ {s@^[ \t]*#\+\([ \t]*clusterName[ \t]\+.*\).*@\1@ ;t;}}' ${lsf_shared_file}
	hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*clusterName[ \t]\+/p}' ${lsf_shared_file}`
	if [ "x${hasOne}" = "x" ]; then
	    sed -i -e '/Begin Resource/,/End Resource/ { /End Resource/ { i clusterName  String   ()       ()       (cluster name for the external hosts)' -e '}}' ${lsf_shared_file}
	fi
        restart_lsf=Y
    fi
    hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*templateID[ \t]\+/p}' ${lsf_shared_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e  '/Begin Resource/,/End Resource/ {\@^[ \t]*#\+[ \t]*templateID[ \t]\+@ {s@^[ \t]*#\+\([ \t]*templateID[ \t]\+.*\).*@\1@ ;t;}}' ${lsf_shared_file}
	hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*templateID[ \t]\+/p}' ${lsf_shared_file}`
	if [ "x${hasOne}" = "x" ]; then
	    sed -i -e '/Begin Resource/,/End Resource/ { /End Resource/ { i templateID   String   ()       ()       (template ID for the external hosts)' -e '}}' ${lsf_shared_file}
	fi
        restart_lsf=Y
    fi
    hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*providerName[ \t]\+/p}' ${lsf_shared_file}`
    if [ "x${hasOne}" = "x" ]; then
        sed -i -e  '/Begin Resource/,/End Resource/ {\@^[ \t]*#\+[ \t]*providerName[ \t]\+@ {s@^[ \t]*#\+\([ \t]*providerName[ \t]\+.*\).*@\1@ ;t;}}' ${lsf_shared_file}
	hasOne=`sed -n -e '/Begin Resource/,/End Resource/ {/^[ \t]*providerName[ \t]\+/p}' ${lsf_shared_file}`
	if [ "x${hasOne}" = "x" ]; then
	    sed -i -e '/Begin Resource/,/End Resource/ { /End Resource/ { i providerName String   ()       ()       (provider name for the external hosts)' -e '}}' ${lsf_shared_file}
	fi
        restart_lsf=Y
    fi
    
    
    # lsf.cluster
    hasOne=`sed -n -e '/Begin Parameters/,/End Parameters/ {/^[ \t]*LSF_HOST_ADDR_RANGE=/p}' ${lsf_cluster_file}`
    if [ "x${hasOne}" = "x" ]; then
	sed -i -e '/Begin Parameters/,/End Parameters/ { /End Parameters/ { i LSF_HOST_ADDR_RANGE=*.*.*.*' -e '}}' ${lsf_cluster_file}
        restart_lsf=Y
    fi

    # lsb.modules
    hasOne=`sed -n -e '/Begin PluginModule/,/End PluginModule/ {/^[ \t]*schmod_demand/p}' ${lsb_modules_file}`
    if [ "x${hasOne}" = "x" ]; then
	sed -i -e '/Begin PluginModule/,/End PluginModule/ { /End PluginModule/ { i schmod_demand                  ()                              ()' -e '}}' ${lsb_modules_file}
        restart_lsf=Y
    fi

    # lsb.queues
    hasOne=`sed -n -e '/^[ \t#]*RC_HOSTS[ \t]*=/p' ${lsb_queues_file}`
    if [ "x${hasOne}" = "x" ]; then
	restart_lsf=Y
	sed -i -e '$ a \
Begin Queue \
QUEUE_NAME=rc_example \
DESCRIPTION  = Example bursting threshold configuration \
PRIORITY=50 \
USERS  = lsfadmins \
RC_ACCOUNT = rc_example \
RC_DEMAND_POLICY  = THRESHOLD[[5,10] [1,60] [100,0]] \
RC_HOSTS = openshift \
End Queue ' ${lsb_queues_file}
    fi

    # hostProvider.json
    hostProviders_json_file=${lsf_confdir}/resource_connector/hostProviders.json

    if [ ! -f "${hostProviders_json_file}" ]; then
        restart_lsf=Y
	cat << ENDPROV > ${hostProviders_json_file}
{
    "providers":[
        {
            "name": "openshift",
            "type": "openshiftProv",
            "confPath": "resource_connector/openshift",
            "scriptPath": "resource_connector/openshift"
        }

    ]
}
ENDPROV
    else
	hasOne=`sed -n -e '/"name": "openshift"/p' ${hostProviders_json_file}`
	if [ "x${hasOne}" = "x" ]; then
	    restart_lsf=Y
	     sed -i -e '/"providers":\[/ a \
        { \
            "name": "openshift", \
            "type": "openshiftProv", \
            "confPath": "resource_connector/openshift", \
            "scriptPath": "resource_connector/openshift" \
        },' ${hostProviders_json_file}
	fi
    fi

    if [ "${restart_lsf}" = "Y" ]; then
        . ${lsf_confdir}/profile.lsf
        $LSF_SERVERDIR/lsf_daemons restart
    fi

    # check if Java is installed on LSF management pod

    if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
	_java="$JAVA_HOME/bin/java"
    elif type -p java > /dev/null 2>&1; then
	_java=java
    elif [ -x "/root/openjdk/bin/java" ]; then
	_java=/root/openjdk/bin/java
    else
	echoErr "Java not installed. OpenShift provider plugin requires Java version 1.8 or up"
	exit 1
    fi

    if [[ "$_java" ]]; then
	version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
	if [[ "$version" < "1.8" ]]; then
	    echoErr "OpenShift provider plugin requires Java version 1.8 or up"
	    exit 1
	fi
    fi
    exit 0

EXECLSF

    ${oc_command} cp ${MYTMP}/${TMP_CMD} ${lsf_master_name}:${lsf_topdir}/conf
    if [ "$?" != "0" ]; then
	fail_and_exit "Cannot copy <${TMP_CMD}> to ${lsf_topdir}/conf on the LSF management pod <${lsf_master_name}>."
    fi
    local resultMsg=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c '(chmod a+x '"${lsf_topdir}/conf/${TMP_CMD}"'; '"${lsf_topdir}/conf/${TMP_CMD}"'; ret=$?; rm -f '"${lsf_topdir}/conf/${TMP_CMD}"'; echo -n ";EXIT=${ret};";exit ${ret};)'`
    local retCode="$?"
    local errMsg=`echo "${resultMsg}"|sed -n -e 's/.*ERR_MSG=\(.*\);EXIT=.*/\1/p'`
    local exitCode=`echo "${resultMsg}"|sed -n -e 's/.*;EXIT=\(.*\);.*/\1/p'`
    if [ "x${retCode}" != "x0" -o "x${exitCode}" != "x0" ]; then
        if [ "x${errMsg}" = "x" ]; then
	    errMsg="An error occurred during installation"
	fi
	fail_and_exit "Failed: ${errMsg} on the LSF management pod <${lsf_master_name}>."
    fi

}

copy_rc_tar()
{

    # Check if LSF_TOP/conf/resource_connector/openshift exists on LSF management pod
    local hasRCInConfDir=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c 'if [ -d '"${lsf_topdir}"'/conf/resource_connector/openshift ]; then echo -n "Y"; else echo -n "N"; fi'`
    if [ "x${rc_tar_filepath}" = "x" ]; then
	if [ "x${hasRCInConfDir}" != "xY" ]; then
	    fail_and_exit "${lsf_topdir}/conf/resource_connector/openshift was not found on the LSF management pod <${lsf_master_name}>. To copy the files to a specified directory on the LSF management pod, specify -f <rc_tar_filepath>."
	fi
        return
    fi
    # rc_tar_filepath specified
    local hasPlaceHolder=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c '(d='"${lsf_topdir}"'/conf/resource_connector/openshift/conf;rc_conf_file=${d}/openshiftprov_config.json;hasPlaceHolders=;if [ -f ${rc_conf_file} ]; then hasPlaceHolders=\`sed -n -e "/.*:.*\"@.*@\".*/p" ${rc_conf_file}\`; fi; if [ "x${hasPlaceHolders}" != "x" ]; then echo -n "Y"; else echo -n "N"; fi;)'`
    if [ "x${hasRCInConfDir}" = "xY" -a "x${hasPlaceHolder}" = "xN" ]; then
        # move conf to .conf_bak
	local errMsg=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c '(d='"${lsf_topdir}"'/conf/resource_connector/openshift/conf;d_bak='"${lsf_topdir}"'/conf/resource_connector/openshift/.conf_bak; if [ -d ${d} -a "x${hasPlaceHolders}" = "x" ]; then rm -rf ${d_bak}; mv -f ${d} ${d_bak}; ret=$?; else ret=0; fi; exit '"${ret}"')'`
	if [ "$?" != "0" ]; then
	    fail_and_exit "Cannot move <${lsf_topdir}/conf/conf> to  <${lsf_topdir}/conf/.conf_bak> on the LSF management pod <${lsf_master_name}>."
	fi
    fi
    log_info_sto "Copying files to the LSF management pod..."
    # resource_connector is the top directory of the tar.Z file.
    ${oc_command} cp ${rc_tar_filepath} ${lsf_master_name}:${lsf_topdir}/conf
    if [ "$?" != "0" ]; then
	fail_and_exit "Cannot copy <${rc_tar_filepath}> to ${lsf_topdir}/conf on the LSF management pod <${lsf_master_name}>."
    fi
    local tar_filename=`basename ${rc_tar_filepath}`
    local extracted=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c 'cd '"${lsf_topdir}"'/conf; tar zxof '"${lsf_topdir}"'/conf/'"${tar_filename}"' --no-same-owner --no-same-permissions -m; if [ "$?" = "0" ]; then echo -n "Y"; else echo -n "N"; fi; rm -f '"${lsf_topdir}"'/conf/'"${tar_filename}"';'`
    if [ "$?" != "0" -o "x${extracted}" != "xY" ]; then
	fail_and_exit "Cannot extract <${lsf_topdir}/conf/${rc_tar_filepath}> to ${lsf_topdir}/conf on the LSF management pod <${lsf_master_name}>."
    fi
    local modeChanged=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c 'chmod -R a+x '"${lsf_topdir}"'/conf/resource_connector/openshift; if [ "$?" = "0" ]; then echo -n "Y"; else echo -n "N"; fi'`
    if [ "$?" != "0" -o "x${modeChanged}" != "xY" ]; then
	fail_and_exit "Cannot change the <${lsf_topdir}/conf/resource_connector> mode to a+x on the LSF management pod <${lsf_master_name}>."
    fi
    local ownerChanged=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c 'chown -R lsfadmin '"${lsf_topdir}"'/conf/resource_connector/openshift; if [ "$?" = "0" ]; then echo -n "Y"; else echo -n "N"; fi'`
    if [ "$?" != "0" -o "x${ownerChanged}" != "xY" ]; then
	fail_and_exit "Cannot change the <${lsf_topdir}/conf/resource_connector> owner to lsfadmin on the LSF management pod <${lsf_master_name}>."
    fi
    if [ "x${hasRCInConfDir}" = "xY" ]; then
        # move .conf_bak to conf
	local errMsg=`${oc_command} exec -it ${lsf_master_name} -- /bin/bash -c '(d='"${lsf_topdir}"'/conf/resource_connector/openshift/conf;d_bak='"${lsf_topdir}"'/conf/resource_connector/openshift/.conf_bak; if [ -d ${d_bak} ]; then rm -rf ${d}; mv -f ${d_bak} ${d}; ret=$?; else ret=0; fi; exit '"${ret}"')'`
	if [ "$?" != "0" ]; then
	    fail_and_exit "Failed: ${errMsg} on the LSF management pod <${lsf_master_name}>."
	fi
    fi
}

setup()
{
    log_info_sto "Enabling ${RC_FULLNAME}..."
    log_info_sto ""

    if [ "x`which oc 2> /dev/null`" = "x" ]; then
        if [ "x`which kubectl 2> /dev/null`" != "x" ]; then
        oc_command=kubectl
	else 
	fail_and_exit "oc/kubectl command not found."
	fi
    fi
    log_info_sto "Checking the LSF operator..."
    check_lsf_operator

    log_info_sto "Preparing the service account <${service_account}>..."
    prepare_service_account

    copy_rc_tar

    log_info_sto "Configuring the LSF management pod <${lsf_master_name}>..."
    configure_rc_on_master_pod

    log_info_sto "${RC_FULLNAME} enabled"
    log_info_sto ""
}

RC_FULLNAME="IBM Spectrum LSF resource connector for OpenShift"
LOG_FILE="lsf-rc-install.log"
cmd=$0
lsf_topdir=/opt/ibm/lsfsuite/lsf
lsf_master_name=
lsftype=
sa_token=
compute_image=
# command line args
name_space=
service_account=
rc_tar_filepath=
operator_cluster=
wait_pod_ip=
timeout_in_sec=
oc_command=oc
userauth_args=
userauth_starts=

MYTMP="$(PATH=/sbin:/usr/sbin:/bin:/usr/bin mktemp -d)"
if [ $# -gt 1 ]; then
    while [[ $# -gt 1 ]]; do
        key="$1"
        case $key in
            -n)
            name_space="$2"
            shift
            ;;
            -s)
            service_account="$2"
            shift
            ;;
            -f)
            rc_tar_filepath="$2"
            shift
            ;;
            -d)
            lsf_topdir="$2"
            shift
            ;;
            -w)
            wait_pod_ip="$2"
            shift
            ;;
            -t)
            timeout_in_sec="$2"
            shift
            ;;
            *)
            shift
            ;;
        esac
        shift
    done
fi


if [ "x${name_space}" = "x" ]; then
    fail_and_exit "-n <name_space> was not specified."
fi
if [ "x${service_account}" = "x" ]; then
    fail_and_exit "-s <service_account> was not specified."
fi
if [ "${#name_space}" -gt "15" ]; then
    fail_and_exit "-n <name_space> is not valid. <name_space> cannot exceed 15 characters because the fully-qualified domain name exceeds the 63 character limit."
fi
isValid=`echo -n ${service_account} |sed -n -e '/^[a-zA-Z0-9]\+[a-zA-Z0-9\-]*[a-zA-Z0-9]\+$/p'`
if [[ "x${isValid}" = "x" ]] || [ "${#service_account}" -gt "63" ]; then
    fail_and_exit "-s <service_account> is not valid. \
<service_account> must contain up to 63 characters with only lowercase alphanumeric characters or '-'. \
<service_account> must start with an alphanumeric character and end with an alphanumeric character."
fi

if [ "x${rc_tar_filepath}" != "x" ]; then
if [ ! -f ${rc_tar_filepath} ]; then
    fail_and_exit "File path <${rc_tar_filepath}> is not valid."
fi
fi
if [ "x${wait_pod_ip}" != "x" ]; then
if [ "${wait_pod_ip}" = "Y" -o "${wait_pod_ip}" = "y" -o  "${wait_pod_ip}" = "N" -o "${wait_pod_ip}" = "n" ]; then
    :
else
    fail_and_exit "-w <wait_pod_ip> is not valid. Y or N are valid values."
fi
fi
if [ "x${timeout_in_sec}" != "x" ]; then
if [ ${timeout_in_sec} -lt 0 -o ${timeout_in_sec} -gt 3600 ]; then
    fail_and_exit "-t <timeout_in_sec> is not valid. <timeout_in_sec> must be a non-negative integer between 0 and 3600."
fi
fi

setup
rm -rf $MYTMP
exit 0
