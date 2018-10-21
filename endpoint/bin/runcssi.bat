
@ECHO off
SETLOCAL ENABLEDELAYEDEXPANSION
SET CP=

SET WLTHIN_10_3_5="wlthint3client-10.3.5"
SET WLTHIN_10_3_6="wlthint3client-10.3.6"
SET WLFULL_10_3_5="wlfullclient-10.3.5"
SET WLFULL_10_3_6="wlfullclient-10.3.6"

FOR /F %%F IN ('dir ..\lib\wls\ /B ^| findstr %WLTHIN_10_3_6%') DO (
  SET CP=!CP!..\lib\wls\%%F;
)

FOR /F %%F IN ('dir ..\lib\ /b') DO (
  SET CP=!CP!..\lib\%%F;
)

FOR /F %%F IN ('dir ..\lib\activemq\ /b') DO (
  SET CP=!CP!..\lib\activemq\%%F;
)

FOR /F %%F IN ('dir ..\lib\solace\ /b') DO (
  SET CP=!CP!..\lib\solace\%%F;
)

FOR /F %%F IN ('dir ..\lib\log\ /b') DO (
  SET CP=!CP!..\lib\log\%%F;
)

java -Dlog4j.configuration="file:../lib/log4j.properties" -cp "%CP%;." com/cssi/CssiConsumer ../conf/consumerConnection.properties

ENDLOCAL
