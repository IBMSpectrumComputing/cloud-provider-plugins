#!/bin/bash
#
#***********************************************************#
#                                                           #
# Name: example_user_data.sh                                #
#                                                           #
# (c) Copyright International Business Machines Corp 2019.  #
# US Government Users Restricted Rights -                   #
# Use, duplication or disclosure                            #
# restricted by GSA ADP Schedule Contract with IBM Corp.    #
#                                                           #
#***********************************************************#
#
LSF_TOP=/opt/lsf
LSF_CONF_FILE=$LSF_TOP/conf/lsf.conf
LOG_FILE=/tmp/user-data.log

echo Start at `date '+%Y-%m-%d %H:%M:%S'` > $LOG_FILE 2>&1

#
# Support rc_account resource to enable RC_ACCOUNT policy
# Set rc_account to the file lsf.conf
#
if [ -n "${rc_account}" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap ${rc_account}*rc_account]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES $LSF_CONF_FILE successfully, add [resourcemap ${rc_account}*rc_account]" >> $LOG_FILE
fi

#
# Include origin information for fault tolerance
# Set instance ID to the file lsf.conf
#
instance_id=$(curl -H Metadata:true "http://169.254.169.254/metadata/instance/compute/vmId?api-version=2018-10-01&format=text")
if [ -n "$instance_id" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${instance_id}*instanceID]" >> $LOG_FILE
else
    echo "Can not get instance ID" >> $LOG_FILE
fi

vm_type=$(curl -H Metadata:true "http://169.254.169.254/metadata/instance/compute/vmSize?api-version=2018-10-01&format=text")
if [ -n "$vm_type" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $vm_type*vm_type]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${vm_type}*vm_type]" >> $LOG_FILE
else
    echo "Cannot get vm type" >> $LOG_FILE
fi

#
# Set template ID to the file lsf.conf
#
if [ -n "$template_id" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $template_id*templateID]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${template_id}*templateID]" >> $LOG_FILE
else
    echo "The template ID is not specified by environment variable 'template_id'" >> $LOG_FILE
fi

#
# Set cluster name to the file lsf.conf
#
if [ -n "$clustername" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $clustername*clusterName]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${clustername}*clusterName]" >> $LOG_FILE
else
    echo "The cluster name is not specified by environment variable 'clustername'" >> $LOG_FILE
fi

#
# Set provider name to the file lsf.conf
#
if [ -n "$providerName" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $providerName*providerName]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${providerName}*providerName]" >> $LOG_FILE
else
    echo "The provider name is not specified by environment variable 'providerName'" >> $LOG_FILE
fi

#
# Add customization script code here
#


#
# Start LSF Daemons
#
# Install LSF as a service and start up
${LSF_TOP}/10.1/install/hostsetup --top="${LSF_TOP}" --boot="y" --start="y" --dynamic  2>&1 >> $logfile
systemctl status lsfd >> $logfile

# Source LSF enviornment on the VM host
#source $LSF_TOP/conf/profile.lsf
#env >> $LOG_FILE
#
#lsadmin limstartup
#lsadmin resstartup
#badmin hstartup


echo End at `date '+%Y-%m-%d %H:%M:%S'` >> $LOG_FILE 2>&1
