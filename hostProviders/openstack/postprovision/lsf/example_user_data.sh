#!/bin/sh

logfile=/tmp/user_data.log
echo START AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile

#
# Export user data, which is defined with the "UserData" attribute in 
# the template
#
%EXPORT_USER_DATA%

#
# Run the script, which is defined with the "UserScript" attribute in 
# the template
#
USER_SCRIPT=/root/userscript.sh
if [ -e $USER_SCRIPT ]; then
    chmod +x $USER_SCRIPT
    $USER_SCRIPT
fi

#
# Update DNS server
#
#DNS_SERVER="%OS_DNS_SERVER%"
#if [ -n "$DNS_SERVER" ]; then
#    sed -i "1inameserver $DNS_SERVER" /etc/resolv.conf
#    DHCLIENT_CONF="/etc/dhcp/dhclient-eth0.conf"
#    echo >> $DHCLIENT_CONF
#    echo "prepend domain-name-servers $DNS_SERVER;" >> $DHCLIENT_CONF
#fi

#
# Add your customization script here
#

#
# Source LSF enviornment at the VM host
#
LSF_TOP=/opt/lsf
LSF_CONF_FILE=$LSF_TOP/conf/lsf.conf

# 
# Support rc_account resource to enable RC_ACCOUNT policy  
# Add additional local resources if needed 
#
if [ -n "${rc_account}" ]; then
    sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap ${rc_account}*rc_account]\"/" $LSF_CONF_FILE
    echo "update LSF_LOCAL_RESOURCES lsf.conf successfully, add [resourcemap ${rc_account}*rc_account]" >> $logfile
fi

#
# Include origin information for fault tolerance
#
instance_id=$(curl http://169.254.169.254/2009-04-04/meta-data/instance-id)
if [ -n "$instance_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
fi
vm_type=$(curl http://169.254.169.254/2009-04-04/meta-data/instance-type)
if [ -n "$vm_type" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $vm_type*vm_type]\"/" $LSF_CONF_FILE
fi
if [ -n "$template_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $template_id*templateID]\"/" $LSF_CONF_FILE
fi
if [ -n "$clustername" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $clustername*clusterName]\"/" $LSF_CONF_FILE
fi

# Install LSF as a service and startup
${LSF_TOP}/10.1/install/hostsetup --top="/opt/zhchgbj/lsf10.1" --boot="y" --start="y" --dynamic 2>&1 >> $logfile
systemctl status lsfd >> $logfile

#
# Start LSF Daemons 
#. $LSF_TOP/conf/profile.lsf
#env >> $logfile
#$LSF_SERVERDIR/lsf_daemons start

echo END AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile
