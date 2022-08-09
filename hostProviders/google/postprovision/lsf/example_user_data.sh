#!/bin/sh

LSF_TOP=/usr/share/lsf
LSF_CONF_FILE=$LSF_TOP/conf/lsf.conf
logfile=$LSF_TOP/log/user_data.log

echo START `date '+%Y-%m-%d %H:%M:%S'` >> $logfile

#
# Export user data, which is defined with the "UserData" attribute 
# in the template
#
%EXPORT_USER_DATA%

#
# Add your customization script here
#

#
# Source LSF enviornment at the VM host
#
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
instance_id=$(curl "http://metadata.google.internal/computeMetadata/v1/instance/id" -H "Metadata-Flavor: Google")
if [ -n "$instance_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
fi
vm_type=`curl "http://metadata.google.internal/computeMetadata/v1/instance/machine-type" -H "Metadata-Flavor: Google" | awk -F/ '{print $NF}'`
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
  
#
# Run lsreghost command to register the host to LSF master if no DNS update
#
#echo "lsfdev1.test.com" > $LSF_ENVDIR/hostregsetup
#lsreghost -s $LSF_ENVDIR/hostregsetup

#
# Mount local SSDs
#
#SSD_MOUNT_DIR=/tmp
#
#mountLocalSSDs()
#{
#    echo "Start mounting local SSDs..." >> $logfile
#
#    #Get SSD number
#    ssdNum=$(lsblk | egrep "nvme|sd" | egrep -v "sda|grep" | wc -l)
#    if [ ${ssdNum} -eq 0 ]; then
#        echo "Error: not find local SSD devices. Aborting..." >> $logfile
#        return 
#    fi
#	
#    #Check required commands: lsblk, mkfs.ext4 (should already installed by default)
#    if ! [ -x "$(command -v lsblk)" -a -x "$(command -v mkfs.ext4)" ]; then
#        echo "Error: lsblk or mkfs.ext4 is not installed. Aborting..." >> $logfile
#        return 
#    fi
#
#    #Install mdadm on different OS accordingly
#    if ! [ -x "$(command -v mdadm)" ]; then
#	    if [ -x "$(command -v apt)" ]; then     #Debian and ubuntu
#            apt update && apt install mdadm --no-install-recommends > /dev/null 2>&1
#	    elif [ -x "$(command -v yum)" ]; then 	#Centos and redhat
#            yum install mdadm -y > /dev/null 2>&1
#	    elif [ -x "$(command -v zypper)" ]; then #SLES and openSUSE
#            zypper install -y mdadm > /dev/null 2>&1    
#	    fi
#	    if ! [ -x "$(command -v mdadm)" ]; then
#           echo "Error: mdadm can not be installed. Aborting..." >> $logfile
#           return 
#        fi
#    fi
#
#    #Combine multiple local SSD partitions into a single logical volume
#    ssdList=$(lsblk | egrep "nvme|sd" | grep -v "sda" | awk '{print "/dev/"$1}' | xargs)
#    echo "SSD num: ${ssdNum}, SSD list: ${ssdList}" >> $logfile
#    if [ $ssdNum -gt 1 ]; then
#    	#Multiple SSDs, combine them into a single logical volume 
#    	mdadm --create /dev/md0 --level=0 --raid-devices=${ssdNum} ${ssdList} > /dev/null 2>&1
#    	if [ "$?" = "0" ]; then
#            echo "Combining multiple local SSD partitions into a single logical volume (/dev/md0) succeeded. Continuing..." >> $logfile
#        else
#            echo "Combining multiple local SSD partitions into a single logical volume (/dev/md0) failed. Aborting..." >> $logfile
#            return 
#        fi
#	    device=/dev/md0
#    else  
#        #Only one SSD device
#        device=$ssdList
#    fi
#    
#    #Format SSD devices
#    mkfs.ext4 -F $device > /dev/null 2>&1
#    if [ "$?" = "0" ]; then
#        echo "Formating SSDs succeeded. Continuing..." >> $logfile
#    else
#        echo "Formatting SSDs failed. Aborting..." >> $logfile
#        return 
#    fi
#
#    if ! [ -d $SSD_MOUNT_DIR ]; then
#        mkdir -p $SSD_MOUNT_DIR
#    fi
#
#    #Mount to SSD_MOUNT_DIR
#    mount $device $SSD_MOUNT_DIR
#
#    if [ "$?" = "0" ]; then
#        echo "Mounting SSDs succeeded. Continuing..." >> $logfile
#    else
#        echo "Mounting SSDs failed. Aborting..." >> $logfile
# 	     return	
#    fi
#
#    #Change permission of SSD_MOUNT_DIR accordingly
#    chmod a+w $SSD_MOUNT_DIR
#
#    echo "End mounting local SSDs..." >> $logfile
#}
#
#mountLocalSSDs

#
# Start LSF Daemons 
#
$LSF_SERVERDIR/lsf_daemons start

echo END AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile
