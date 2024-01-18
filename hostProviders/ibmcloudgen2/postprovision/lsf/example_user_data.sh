#!/bin/sh

logfile=/tmp/user_data.log
echo START `date '+%Y-%m-%d %H:%M:%S'` >> $logfile

#
# Export user data, which is defined with the "UserData" attribute 
# in the template
#
%EXPORT_USER_DATA%

#
# Add your customization script here
#

#Change the vm host name based on the internal ip
privateIP=$(ip addr show eth0 | awk '$1 == "inet" {gsub(/\/.*$/, "", $2); print $2}')
hostname=ibm-gen2host-${privateIP//./-}
hostnamectl set-hostname ${hostname}
#hostname ${hostname}
hostname >> $logfile

# NOTE: On ibm gen2, the default DNS server do not have reverse hostname/IP resolution.
# 1) put the master server hostname and ip into /etc/hosts.
# 2) put all possible VMs' hostname and ip into /etc/hosts.
for ((i=1; i<=254; i++))
do
   echo "10.240.0.${i} ibm-gen2host-10-240-0-${i} ibm-gen2host-10-1-1-${i}" >> /etc/hosts
done
#update the lsf master hostname
sed -i "s/ibm-gen2host-10-240-0-135/lsf-master/" /etc/hosts

cp /etc/hosts /tmp/
#
# Source LSF enviornment at the VM host
#
LSF_TOP=/opt/lsf_dynamic_host
LSF_CONF_FILE=$LSF_TOP/conf/lsf.conf

#update lim port
#sed -i "s/LSF_LIM_PORT=7869/LSF_LIM_PORT=17869/" $LSF_CONF_FILE
#update master host name
sed -i "s/ib19b07/lsf-master/" $LSF_CONF_FILE
sed -i '$ a LSF_CALL_LIM_WITH_TCP=Y'  $LSF_CONF_FILE

cat $LSF_CONF_FILE >> $logfile
# Support rc_account resource to enable RC_ACCOUNT policy  
# Add additional local resources if needed 
#
if [ -n "${rc_account}" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap ${rc_account}*rc_account]\"/" $LSF_CONF_FILE
echo "update LSF_LOCAL_RESOURCES lsf.conf successfully, add [resourcemap ${rc_account}*rc_account]" >> $logfile
fi

#if [ -n "${zone}" ]; then
#sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap ${zone}*zone]\"/" $LSF_CONF_FILE
#echo "update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${zone}*zone]" >> $logfile
#else
#echo "zone doesn't exist in envrionment variable" >> $logfile
#fi

instance_id=$(dmidecode | grep Family | cut -d ' ' -f 2 |head -1)
if [ -n "$instance_id" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${instance_id}*instanceID]" >> $logfile
else
    echo "Can not get instance ID" >> $logfile
fi

#if [ -n "$vm_type" ]; then
#    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $vm_type*vm_type]\"/" $LSF_CONF_FILE
#    echo "Update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${vm_type}*vm_type]" >> $logfile
#else
#    echo "Cannot get vm type" >> $logfile
#fi

#if [ -n "$template_id" ]; then
#sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $template_id*templateID]\"/" $LSF_CONF_FILE
#echo "update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${template_id}*templateID]" >> $logfile
#else
#echo "templateID doesn't exist in envrionment variable" >> $logfile
#fi

#if [ -n "$clusterName" ]; then
#sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $clusterName*clusterName]\"/" $LSF_CONF_FILE
#echo "update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${clusterName}*clusterName]" >> $logfile
#else
#echo "clusterName doesn't exist in envrionment variable" >> $logfile
#fi

#if [ -n "$providerName" ]; then
#sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $providerName*providerName]\"/" $LSF_CONF_FILE
#echo "update LSF_LOCAL_RESOURCES in $LSF_CONF_FILE successfully, add [resourcemap ${providerName}*providerName]" >> $logfile
#else
#echo "providerName doesn't exist in envrionment variable" >> $logfile
#fi

# Install LSF as a service and start up
${LSF_TOP}/10.1/install/hostsetup --top="${LSF_TOP}" --boot="y" --start="y" --dynamic 2>&1 >> $logfile
systemctl status lsfd >> $logfile

# Start up LSF
#LSF_TOP will get lost after you source profile.lsf
#. $LSF_TOP/conf/profile.lsf
#env >> $logfile
#nohup lsf_daemons start &
#
#lsf_daemons status >> $logfile
echo END `date '+%Y-%m-%d %H:%M:%S'` >> $logfile

