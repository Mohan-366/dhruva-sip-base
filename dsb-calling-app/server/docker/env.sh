if [ `head -c 3 /sys/devices/virtual/dmi/id/product_uuid` == "ec2" ]
    then
        AWS_PUBLIC_INT_MAC=`cat /sys/class/net/eth2/address`
        export EXTERNAL_IP_ETH2=`curl -s http://169.254.169.254/latest/meta-data/network/interfaces/macs/${AWS_PUBLIC_INT_MAC}/public-ipv4s`
        echo "External IP is $EXTERNAL_IP_ETH2"
fi
