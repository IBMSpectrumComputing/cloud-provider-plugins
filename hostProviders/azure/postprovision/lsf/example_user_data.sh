#!/bin/bash

echo START >> /var/log/user-data.log 2>&1
# run hostsetup
LSF_TOP=/usr/local/lsf
LSF_CONF_FILE=$LSF_TOP/conf/lsf.conf

# run user script to enable selecting template based on zone

logfile=/tmp/userscript.log
if [ -n "${rc_account}" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap ${rc_account}*rc_account]\"/" $LSF_CONF_FILE
echo "update LSF_LOCAL_RESOURCES lsf.conf successfully, add [resourcemap ${rc_account}*rc_account]" >> $logfile
fi
#
# Include origin information for fault tolerance
#
instance_id=$(curl -H Metadata:true "http://169.254.169.254/metadata/instance/compute/vmId?api-version=2018-10-01&format=text")
if [ -n "$instance_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
fi
vm_type=$(curl -H Metadata:true "http://169.254.169.254/metadata/instance/compute/vmSize?api-version=2018-10-01&format=text")
if [ -n "$vm_type" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $vm_type*vm_type]\"/" $LSF_CONF_FILE
fi
if [ -n "$template_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $template_id*templateID]\"/" $LSF_CONF_FILE
fi
if [ -n "$clustername" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $clustername*clusterName]\"/" $LSF_CONF_FILE
fi
if [ -n "$providerName" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $providerName*providerName]\"/" $LSF_CONF_FILE
fi

# NOTE: On azure, the default DNS server do not have reverse hostname/IP resolution. 
# 1) put the master server hostname and ip into /etc/hosts.
# 2) put all possible VMs' hostname and ip into /etc/hosts.
#echo "10.1.0.4 lsfmaster lsfmaster.l4bujz2d1erudmtehqszuf5ugg.ix.internal.cloudapp.net" >> /etc/hosts
#for ((i=1; i<=254; i++))
#do
#        echo "10.1.1.${i} host-10-1-1-${i} host-10-1-1-${i}.l4bujz2d1erudmtehqszuf5ugg.ix.internal.cloudapp.net" >> /etc/hosts
#done

# Install LSF as a service and start up
${LSF_TOP}/10.1/install/hostsetup --top="${LSF_TOP}" --boot="y" --start="y" --dynamic 2>&1 >> $logfile
systemctl status lsfd >> $logfile

# Start LSF Daemons
#source $LSF_TOP/conf/profile.lsf
#env > $logfile
#lsadmin limstartup
#lsadmin resstartup
#badmin hstartup

echo END >> /var/log/user-data.log 2>&1
