Sperta - Sistema de Gestão de Casas Inteligentes (Fase 2)
Este projeto implementa uma arquitetura Cliente-Servidor para a gestão remota de dispositivos domésticos, acrescentando mecanismos de Segurança e Confiabilidade à fase 1.

1. Estrutura do Projeto

src/: Código fonte organizado por pacotes (sperta.client, sperta.server, sperta.common, sperta.model).

bin/: Ficheiros compilados (.class).

dist/: Executáveis gerados (.jar).

server_data/: Base de dados do servidor (utilizadores, casas, estados, certificados e hashes).

Ficheiros CSV: logs e históricos dos dispositivos são guardados na raiz do projeto (ex: log.csv, historico.csv).

2. Como Compilar

Certifique que está na raiz do projeto.

Remove-Item -Recurse -Force bin, dist -ErrorAction SilentlyContinue
mkdir bin, dist

javac -d bin -sourcepath src src/sperta/server/*.java src/sperta/client/*.java src/sperta/common/*.java src/sperta/model/*.java

jar cfe dist/SpertaServer.jar sperta.server.SpertaServer -C bin .
jar cfe dist/SpertaClient.jar sperta.client.SpertaClient -C bin .

3. Como Executar

3.1. Preparar o Ambiente

Antes de executar o servidor e os clientes, pode ser usado o script build_and_run.ps1 para compilar o projeto, gerar os ficheiros JAR, criar keystores/truststores e configurar a atestação.

Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process

.\build_and_run.ps1

3.2. Iniciar o Servidor

O servidor necessita de quatro argumentos:
<port> <password-cifra> <keystore> <password-keystore>

Exemplo:

java -jar dist/SpertaServer.jar 45678 passwordPBE server.keystore pass123


3.3. Configurar Atestação

O ficheiro server_data/app_attestation.txt deve conter o caminho para o JAR de referência do cliente:

SpertaClient:dist/SpertaClient.jar


3.4. Iniciar o Cliente

O cliente necessita de sete argumentos:
<serverAddress> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>

Exemplo:

java -jar dist/SpertaClient.jar localhost:45678 client.truststore pass123 cliente1.keystore pass123 cliente1 password123

Se o utilizador não existir, será criado automaticamente e o seu certificado será enviado para o servidor.

4. Comandos Disponíveis
CREATE <casa>: Cria uma nova casa (o utilizador torna-se Owner).

ADD <user> <casa> <seccao>: O Owner dá permissão a outro utilizador.

RD <casa> <seccao>: Regista um novo dispositivo.

EC <casa> <dispositivo> <valor>: Envia valor para o dispositivo (cifrado no cliente).

RT <casa>: Descarrega o estado atual dos dispositivos permitidos.

RH <casa> <dispositivo>: Descarrega o histórico (.csv) do dispositivo.

5. Melhorias da Fase 2

- Passwords protegidas com hash + salt (SHA-256)
- Comunicação segura com TLS
- Ficheiros do servidor cifrados (PBE + AES-128)
- Verificação de integridade com .hash
- Atestação remota com nonce + SHA256
- Gestão de certificados
- E2E encryption (servidor não tem acesso aos dados em claro)

6. Limitações do Trabalho

- Uso de ficheiros de texto (escalabilidade limitada)
- Sincronização com synchronized

7. Autoria
Rafael Matias 61847
João Nunes 61786
João Ferreira 58191

Disciplina: Segurança e Confiabilidade
