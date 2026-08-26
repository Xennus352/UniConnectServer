@echo off
set "RMI_ENABLED=true"
set "RMI_SHARED_SECRET=uni-dev-secret-change-me"
"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" -jar "D:\UniConnect\UniConnectServer\unicconnect-api\target\unicconnect-api-0.0.1-SNAPSHOT.jar" > "D:\UniConnect\UniConnectServer\api.log" 2> "D:\UniConnect\UniConnectServer\api.err.log"
