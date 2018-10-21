#!/bin/bash -x


LOG=-Dlog4j.configuration="file:../lib/log4j.properties"

CP="../lib/wls/wlthint3client-10.3.6.jar;"
CP+="../lib/activemq;"
CP+="../lib/CSSIfirst.jar;"
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
CP+="../lib/CSSIfirst.jar;"
CP+="."

java $LOG  -cp $CP  com/cssi/CssiConsumer ../conf/consumerConnection.properties


