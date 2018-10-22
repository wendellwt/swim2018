#!/bin/bash -x

echo "number of arguments is:"
echo $#

if test $# -lt 1 ; then
    echo "argument must be: rvr fdps stdds asdex"
    exit 1
fi

case "$1" in
    "rvr")
        echo "doing rvr"
        CONF=rvr.properties
        ;;
    "fdps")
        echo "doing fdps"
        CONF=fdps.properties
        ;;
    "stdds")
        echo "doing stdds"
        CONF=stdds.properties
        ;;
    "asdex")
        echo "doing asdex"
        CONF=asdex.properties
        ;;
    *)
        echo "argument must be: rvr fdps stdds asdex"
        exit 1
        ;;
esac

echo "will do $CONF"
#exit 1

LOG=-Dlog4j.configuration="file:../lib/log4j.properties"

CP="../lib/wls/wlthint3client-10.3.6.jar;"
CP+="../lib/activemq;"
CP+="../lib/JumpstartKit.jar;"
CP+="../lib/log;"
CP+="../lib/log4j.properties;"
CP+="../lib/postgresql-42.2.5.jar;"
CP+="../lib/solace;"
CP+="../lib/wls;"
CP+="../lib/activemq/activemq-all-5.10.2.jar;"
CP+="../lib/solace/sol-common-7.1.0.207.jar;"
CP+="../lib/solace/sol-jcsmp-7.1.0.207.jar;"
CP+="../lib/solace/sol-jms-7.1.0.207.jar;"
CP+="../lib/log/commons-lang-2.6.jar;"
CP+="../lib/log/commons-logging-1.1.3.jar;"
CP+="../lib/log/log4j-1.2.16.jar;"
CP+="../lib/log/slf4j-api-1.7.5.jar;"
CP+="../lib/CSSIendpoint.jar;"
CP+="."

java $LOG  -cp $CP  com/cssi/CssiConsumer ../conf/$CONF

