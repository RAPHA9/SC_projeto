$PASS = "pass123"
$PBE_PASS = "passwordPBE"
$USER = "cliente1"
$PORT = "45678"

Write-Host "--- A limpar ambiente... ---" -ForegroundColor Cyan
Remove-Item -Recurse -Force server_data, bin, dist, *.keystore, *.truststore, *.cert -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path bin, dist, server_data -Force | Out-Null

Write-Host "--- A compilar e gerar JARs... ---" -ForegroundColor Cyan
javac -d bin -sourcepath src src/sperta/server/*.java src/sperta/client/*.java src/sperta/common/*.java
jar cfe dist/SpertaServer.jar sperta.server.SpertaServer -C bin .
jar cfe dist/SpertaClient.jar sperta.client.SpertaClient -C bin .

Write-Host "--- A gerar chaves e certificados... ---" -ForegroundColor Cyan
keytool -genkey -alias server -keyalg RSA -keysize 2048 -keystore server.keystore -validity 365 -dname "CN=localhost" -storepass $PASS -keypass $PASS

keytool -export -alias server -keystore server.keystore -file server.cert -storepass $PASS


keytool -import -alias server -file server.cert -keystore client.truststore -storepass $PASS -noprompt


keytool -genkey -alias $USER -keyalg RSA -keysize 2048 -keystore "$USER.keystore" -validity 365 -dname "CN=$USER" -storepass $PASS -keypass $PASS


"SpertaClient:dist/SpertaClient.jar" | Out-File -FilePath server_data/app_attestation.txt -Encoding ascii

Write-Host "--- TUDO PRONTO! ---" -ForegroundColor Green
Write-Host "Para correr o servidor: java -jar dist/SpertaServer.jar $PORT $PBE_PASS server.keystore $PASS"
Write-Host "Para correr o cliente:  java -jar dist/SpertaClient.jar localhost:$PORT client.truststore $PASS $USER.keystore $PASS $USER password123"