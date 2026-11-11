@echo off
setlocal
cd /d "%~dp0"

if not exist "target\seatkeeper-gateway.jar" (
  echo [gateway] jar not found, building...
  call mvn -q -DskipTests package
  if errorlevel 1 exit /b 1
)

echo [gateway] starting on port 8082 ...
java -jar target\seatkeeper-gateway.jar --server.port=8082
endlocal