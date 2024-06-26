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

# By default TAG_INSTANCEID is unenabled in awsprov_config.json.
# Uncomment following lines If you still want to tag 
# both instance and ebs volumes with "InstnceID".
# NOTE: It is required to install AWS CLI in your compute image.
#
#echo "tagging InstanceID to both instance and ebs volumes" >> $logfile
#AWS_INSTANCE_ID=`curl -s http://169.254.169.254/latest/meta-data/instance-id`
#ROOT_DISK_ID=`aws ec2 describe-volumes --filter Name=attachment.instance-id,Values="${AWS_INSTANCE_ID}" --query "Volumes[*].[VolumeId]"  --out text`
#aws ec2 create-tags --resources "${AWS_INSTANCE_ID}" "${ROOT_DISK_ID}" --tags "Key=InstanceID,Value=${AWS_INSTANCE_ID}"
#if [ $? -eq 0 ]; then
#    echo "Done tagging InstanceID ${AWS_INSTANCE_ID} to instance and ebs volumes $ROOT_DISK_ID" >> $logfile
#fi

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
instance_id=$(curl http://169.254.169.254/latest/meta-data/instance-id)
if [ -n "$instance_id" ]; then
sed -i "s/\(LSF_LOCAL_RESOURCES=.*\)\"/\1 [resourcemap $instance_id*instanceID]\"/" $LSF_CONF_FILE
fi
vm_type=$(curl http://169.254.169.254/latest/meta-data/instance-type)
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


# Install LSF as a service and start up
${LSF_TOP}/10.1/install/hostsetup --top="${LSF_TOP}" --boot="y" --start="y" --dynamic 2>&1 >> $logfile
systemctl status lsfd >> $logfile

# Start up LSF
#
#. $LSF_TOP/conf/profile.lsf
#env >> $logfile
#
#$LSF_SERVERDIR/lsf_daemons start

echo END AT `date '+%Y-%m-%d %H:%M:%S'` >> $logfile
