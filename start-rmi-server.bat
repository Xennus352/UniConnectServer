@echo off
set "RMI_SHARED_SECRET=uni-dev-secret-change-me"
"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" -jar "D:\UniConnect\UniConnectServer\uniconnect-rmi-server\target\uniconnect-rmi-server-0.0.1-SNAPSHOT.jar" > "D:\UniConnect\UniConnectServer\rmi-server.log" 2> "D:\UniConnect\UniConnectServer\rmi-server.err.log"
