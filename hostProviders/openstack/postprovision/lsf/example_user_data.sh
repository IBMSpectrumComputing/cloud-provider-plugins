#!/bin/sh

logfile=/tmp/user_data.log
echo START AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile

#
# Export user data, which is defined with the "UserData" attribute in 
# the template
#
%EXPORT_USER_DATA%

#
# Update sudoer file for Centos 6. Centos 7 does not need this step.
#
if [ -e /etc/redhat-release ]; then
    rel_string=$(cat /etc/redhat-release)
    if [[ $rel_string == "CentOS release 6"* ]]; then
        if [ ! -f /etc/sudoers.d/90-cloud-init-users ]; then
        (
                cat <<EOF
# User rules for centos
centos ALL=(ALL) NOPASSWD:ALL
EOF
        ) > /etc/sudoers.d/90-cloud-init-users
                chmod 440 /etc/sudoers.d/90-cloud-init-users
        else
                echo "/etc/sudoers.d/90-cloud-init-users exists" >> $logfile
                cat /etc/sudoers.d/90-cloud-init-users >> $logfile 
        fi
    fi
fi

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
. $LSF_TOP/conf/profile.lsf
env >> $logfile

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
if [ -n "$template_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $template_id*templateID]\"/" $LSF_CONF_FILE
fi
if [ -n "$clustername" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $clustername*clusterName]\"/" $LSF_CONF_FILE
fi

#
# Start LSF Daemons 
#
$LSF_SERVERDIR/lsf_daemons start

echo END AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile
