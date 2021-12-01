#!/bin/sh

###################################################################
VAR_OPENSTACK_DOMAIN=openstack.vm
VAR_OPENSTACK_INSTANCE_PREFIX=host
VAR_OPENSTACK_SUBNET_CIDR=10.110.135.0/26
VAR_OPENSTACK_DNS_ADDRESS=10.110.135.2
VAR_LSF_DOMAIN=lsf.domain
VAR_LSF_REVERSE=54.42.10
VAR_LSF_DNS_ADDRESS=10.42.54.77
###################################################################

#------------------------------------------------------------------
# Name: LOG_ECHO
# Synopsis: LOG "$message"
# Description:
#       Record message into log file ans show message on the console
#-------------------------------------------------------------------
LOG_ECHO ()
{
    echo "[`date`] $1" | tee -a $LOG_FILE
}

#---------------------------------------------------------------------------
# Name: install_expect
# Synopsis: install_expect
# Environment Variables:
#   None
# Description:
#    This function is to install expect package to communicate with other AUTO_MASTER
# Parameters:
#    None
# Return Value:
#   None
#---------------------------------------------------------------------------
install_expect(){
	which "expect"  >>$LOG_FILE 2>&1
	if [[ "$?" -ne "0" ]]; then
		LOG_ECHO "INFO: Start installing expect package."
		yum install -y expect >>$LOG_FILE 2>&1
		if [ $? -ne 0 ];then
			LOG_ECHO "ERR: Can not install expect package. Please check your yum configuration and log file $LOG_FILE."
			exit 1
		fi
	else
		LOG_ECHO "INFO: expect package already installed."
	fi
}

#------------------------------------------------------------------
# Name: ntpUpdate
# Synopsis: ntpUpdate
# Description:
#       setup ntp service
#------------------------------------------------------------------
ntpUpdate()
{
	LOG_ECHO "INFO: Starting to setup NTP server ..."

    # Install NTP package
    yum -y install ntp >> $LOG_FILE 2>&1

    # Configure NTP server
    ntp_conf=/etc/ntp.conf

    # Remove the existing NTP server
    sed -i '/^server /d' $ntp_conf
    sed -i "1i\server $VAR_OPENSTACK_NTP_SERVER" $ntp_conf

    # Restart NTP service
    chkconfig ntpd on >> $LOG_FILE 2>&1
    service ntpd restart >> $LOG_FILE 2>&1

    # Run an initial update
    ntpdate -u $VAR_OPENSTACK_NTP_SERVER >> $LOG_FILE 2>&1
}

#---------------------------------------------------------------------------
# Name:updateDNSConfig
# Synopsis: updateDNSConfig
# Environment Variables:
#   None
# Description:
#    This function is to update /etc/named.conf with new content
# Parameters:
#    None
# Return Value:
#   None
#---------------------------------------------------------------------------
updateDNSConfig()
{
    LOG_ECHO "INFO: Starting to update /etc/named.conf."
    VAR_FORWARDERS=""
    for ip in $(grep ^nameserver /etc/resolv.conf | awk '{print $2}'); do
        VAR_FORWARDERS="$VAR_FORWARDERS $ip; "
    done
    cp /etc/named.conf /etc/named.conf.orig
    echo > /etc/named.conf
    (
    cat << 'EOF'
options {
        directory       "/var/named";
        allow-query     { any; };
        empty-zones-enable no;
        forwarders      {VAR_FORWARDERS};
};

logging {
        channel default_debug {
                file "data/named.run";
                severity dynamic;
        };
};

//Domain VAR_OPENSTACK_DOMAIN definition
zone "VAR_OPENSTACK_DOMAIN" IN {
        type    master;
        /*Hostname-IP records are stored in the domain data file.
        Generally, the file is named "<domain>.zone".
        */
        file    "VAR_OPENSTACK_DOMAIN.zone";
        allow-update {
                key rndc-key;
        };
};

zone "VAR_SUBNET_REVERSED.IN-ADDR.ARPA." IN {
        type    master;
        /*DNS reverse lookup.*/
        file    "VAR_SUBNET_REVERSED.zone";
        allow-update {
                key rndc-key;
        };
};

zone "VAR_LSF_DOMAIN" IN {
        type    forward;
        forwarders { VAR_LSF_DNS_ADDRESS; };
};

zone "VAR_LSF_REVERSE.IN-ADDR.ARPA." IN {
        type    forward;
        forwarders { VAR_LSF_DNS_ADDRESS; };
};

include "/etc/rndc.key";
EOF
) > /etc/named.conf
    /bin/sed -i "s:VAR_FORWARDERS:$VAR_FORWARDERS:g" /etc/named.conf
    /bin/sed -i "s:VAR_OPENSTACK_DOMAIN:$VAR_OPENSTACK_DOMAIN:g" /etc/named.conf
    /bin/sed -i "s:VAR_SUBNET_REVERSED:$VAR_SUBNET_REVERSED:g" /etc/named.conf
    /bin/sed -i "s:VAR_LSF_DOMAIN:$VAR_LSF_DOMAIN:g" /etc/named.conf
    /bin/sed -i "s:VAR_LSF_REVERSE:$VAR_LSF_REVERSE:g" /etc/named.conf
    /bin/sed -i "s:VAR_LSF_DNS_ADDRESS:$VAR_LSF_DNS_ADDRESS:g" /etc/named.conf
    LOG_ECHO "INFO: Finished updating /etc/named.conf."
}

#---------------------------------------------------------------------------
# Name:createDNSFiles
# Synopsis:createDNSFiles
# Environment Variables:
#   None
# Description:
#    This function is to create DNS files /var/named/VAR_OPENSTACK_DOMAIN.zone and  /var/named/10.zone
# Parameters:
#    None
# Return Value:
#   None
#---------------------------------------------------------------------------
createDNSFiles()
{
    LOG_ECHO "INFO: Start creating /var/named/$VAR_OPENSTACK_DOMAIN.zone and /var/named/$VAR_SUBNET_REVERSED.zone."
    touch /var/named/${VAR_OPENSTACK_DOMAIN}.zone /var/named/$VAR_SUBNET_REVERSED.zone
    chown named:named /var/named/${VAR_OPENSTACK_DOMAIN}.zone /var/named/$VAR_SUBNET_REVERSED.zone
    (
    cat << 'EOF'
$ORIGIN .
$TTL 600
VAR_OPENSTACK_DOMAIN        IN SOA  VAR_DNS_SERVER_HOSTNAME. root.VAR_DNS_SERVER_HOSTNAME. (
                                20140309   ; serial, change the value
                                120        ; refresh (2 minutes)
                                14400      ; retry (4 hours)
                                3600000    ; expire (5 weeks 6 days 16 hours)
                                600      ; minimum
                                )
                        NS      VAR_DNS_SERVER_HOSTNAME.
$ORIGIN VAR_OPENSTACK_DOMAIN.
VAR_DNS_SERVER_SHORTNAME      A       VAR_DNS_SERVER_IP
EOF
) > /var/named/${VAR_OPENSTACK_DOMAIN}.zone

    (
    cat << 'EOF'
$ORIGIN .
$TTL 600
VAR_SUBNET_REVERSED.IN-ADDR.ARPA         IN SOA  VAR_DNS_SERVER_HOSTNAME. root.VAR_DNS_SERVER_HOSTNAME. (
                                20140309   ; serial
                                120        ; refresh (2 minutes)
                                14400      ; retry (4 hours)
                                3600000    ; expire (5 weeks 6 days 16 hours)
                                600      ; minimum
                                )
                        NS      VAR_DNS_SERVER_HOSTNAME.
$ORIGIN VAR_SUBNET_REVERSED.IN-ADDR.ARPA.
EOF
) > /var/named/$VAR_SUBNET_REVERSED.zone

    /bin/sed -i "s:VAR_OPENSTACK_DOMAIN:$VAR_OPENSTACK_DOMAIN:g" /var/named/${VAR_OPENSTACK_DOMAIN}.zone
    /bin/sed -i "s:VAR_SUBNET_REVERSED:$VAR_SUBNET_REVERSED:g" /var/named/${VAR_OPENSTACK_DOMAIN}.zone
    /bin/sed -i "s:VAR_DNS_SERVER_HOSTNAME:$VAR_DNS_SERVER_HOSTNAME:g" /var/named/${VAR_OPENSTACK_DOMAIN}.zone
    /bin/sed -i "s:VAR_DNS_SERVER_SHORTNAME:$VAR_DNS_SERVER_SHORTNAME:g" /var/named/${VAR_OPENSTACK_DOMAIN}.zone
    /bin/sed -i "s:VAR_DNS_SERVER_IP:$VAR_DNS_SERVER_IP:g" /var/named/${VAR_OPENSTACK_DOMAIN}.zone

    /bin/sed -i "s:VAR_SUBNET_REVERSED:$VAR_SUBNET_REVERSED:g" /var/named/$VAR_SUBNET_REVERSED.zone
    /bin/sed -i "s:VAR_DNS_SERVER_HOSTNAME:$VAR_DNS_SERVER_HOSTNAME:g" /var/named/$VAR_SUBNET_REVERSED.zone
    /bin/sed -i "s:VAR_DNS_SERVER_NETIP_REVERSE:$VAR_DNS_SERVER_NETIP_REVERSE:g" /var/named/$VAR_SUBNET_REVERSED.zone
    /bin/sed -i "s:VAR_DNS_SERVER_HOSTNUM:$VAR_DNS_SERVER_HOSTNUM:g" /var/named/$VAR_SUBNET_REVERSED.zone

    VAR_ADDRESS_COUNT=$(( 1 << ( 32 - $VAR_NETWORK_BITS )))
    VAR_IP_NUMBER=$((0x$(printf "%02x%02x%02x%02x\n" $VAR_IP1 $VAR_IP2 $VAR_IP3 $VAR_IP4)))
    for ((N=1; N<VAR_ADDRESS_COUNT; N++))
    {
        VAL=$((VAR_IP_NUMBER|N))
        IP1=$(( (VAL >> 24) & 255 ))
        IP2=$(( (VAL >> 16) & 255 ))
        IP3=$(( (VAL >> 8 ) & 255 ))
        IP4=$(( (VAL      ) & 255 ))
        HOSTNAME=${VAR_OPENSTACK_INSTANCE_PREFIX}-${IP1}-${IP2}-${IP3}-${IP4}
        IP=${IP1}.${IP2}.${IP3}.${IP4}
        echo "${HOSTNAME}    IN  A     ${IP}" >> /var/named/${VAR_OPENSTACK_DOMAIN}.zone
        if (($VAR_NETWORK_BITS >= 24)); then
            REVERSE_NAME=${IP4}
        elif (($VAR_NETWORK_BITS >= 16)); then
            REVERSE_NAME=${IP4}.${IP3}
        else
            REVERSE_NAME=${IP4}.${IP3}.${IP2}
        fi
        echo "${REVERSE_NAME}    IN  PTR     ${HOSTNAME}.${VAR_OPENSTACK_DOMAIN}." >> /var/named/$VAR_SUBNET_REVERSED.zone
    }

    LOG_ECHO "INFO: Finished creating /var/named/$VAR_OPENSTACK_DOMAIN.zone and /var/named/$VAR_SUBNET_REVERSED.zone."
}

#================= Main Entry =======================================
LOG_FILE=/tmp/install_dns.log
VAR_DNS_SERVER_HOSTNAME=`hostname`
VAR_DNS_SERVER_IP=${VAR_OPENSTACK_DNS_ADDRESS}
VAR_DNS_SERVER_SHORTNAME=${VAR_DNS_SERVER_HOSTNAME%%.*}
#VAR_OPENSTACK_DOMAIN=`echo ${VAR_DNS_SERVER_HOSTNAME#${VAR_DNS_SERVER_SHORTNAME}.}`

VAR_SUBNET_ADDRESS=${VAR_OPENSTACK_SUBNET_CIDR/\/*/}
VAR_IP1=$(echo $VAR_SUBNET_ADDRESS | awk -F. '{print $1}')
VAR_IP2=$(echo $VAR_SUBNET_ADDRESS | awk -F. '{print $2}')
VAR_IP3=$(echo $VAR_SUBNET_ADDRESS | awk -F. '{print $3}')
VAR_IP4=$(echo $VAR_SUBNET_ADDRESS | awk -F. '{print $4}')
VAR_NETWORK_BITS=${VAR_OPENSTACK_SUBNET_CIDR/*\//}
if (($VAR_NETWORK_BITS >= 24)); then
    VAR_SUBNET_REVERSED=$VAR_IP3.$VAR_IP2.$VAR_IP1
    VAR_DNS_SERVER_NETIP_REVERSE=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $3"."$2"."$1}')
    VAR_DNS_SERVER_HOSTNUM=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $4}')
elif (($VAR_NETWORK_BITS >= 16)); then
    VAR_SUBNET_REVERSED=$VAR_IP2.$VAR_IP1
    VAR_DNS_SERVER_NETIP_REVERSE=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $2"."$1}')
    VAR_DNS_SERVER_HOSTNUM=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $4"."$3}')
else
    VAR_SUBNET_REVERSED=$VAR_IP1
    VAR_DNS_SERVER_NETIP_REVERSE=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $1}')
    VAR_DNS_SERVER_HOSTNUM=$(echo $VAR_DNS_SERVER_IP | awk -F'.' '{print $4"."$3"."$2}')
fi

echo "dns" > /tmp/.auto_role
LOG_ECHO "INFO: yum install -y bind bind-chroot bind-utils"
yum install -y bind bind-chroot bind-utils  >> $LOG_FILE 2>&1
if [ $? -ne 0 ];then
    LOG_ECHO "ERR: Can not install bind bind-chroot bind-utils package. Please check your yum configuration, then run this script again."
    exit 1
fi
LOG_ECHO "INFO: Create rndc.key file."
rndc-confgen -r /dev/urandom -a >> $LOG_FILE 2>&1
chown root:named /etc/rndc.key >> $LOG_FILE 2>&1
chmod 640 /etc/rndc.key >> $LOG_FILE 2>&1
LOG_ECHO "INFO: Start named service."
service named start >> $LOG_FILE 2>&1
chkconfig --level 235 named on

#ntpUpdate
updateDNSConfig
createDNSFiles
LOG_ECHO "INFO: Restart named service."
service named restart >> $LOG_FILE 2>&1
chown named:named /var/named/chroot/* -R
echo > /etc/resolv.conf
echo "search  $VAR_OPENSTACK_DOMAIN" >> /etc/resolv.conf
echo "nameserver      $VAR_DNS_SERVER_IP" >> /etc/resolv.conf

nslookup $VAR_DNS_SERVER_SHORTNAME.$VAR_OPENSTACK_DOMAIN >> $LOG_FILE 2>&1
if [ $? -ne 0 ];then
    LOG_ECHO "ERR: DNS service can not be created successfully. Refer to $LOG_FILE on $VAR_DNS_SERVER_IP for more details."
    exit 1
else
    LOG_ECHO "INFO: DNS service is created successfully."
fi

#install_expect

LOG_ECHO "INFO: SUCCESS."
