Sperta - Sistema de Gestão de Casas Inteligentes (Fase 1)
Este projeto implementa uma arquitetura Cliente-Servidor para a gestão remota de dispositivos domésticos, focando-se nos pilares de Segurança e Confiabilidade.

1. Estrutura do Projeto
src/: Código fonte organizado por pacotes (sperta.client, sperta.server, sperta.common, sperta.model).

bin/: Ficheiros compilados (.class).

dist/: Executáveis gerados (.jar).

server_data/: Base de dados em texto (utilizadores, casas, permissões, estados).

logs/: Histórico de comandos por dispositivo (.csv).

2. Como Compilar
Certifique que está na raiz do projeto (projetoSC).

Limpar e criar pastas necessárias:

Remove-Item -Recurse -Force bin, dist -ErrorAction SilentlyContinue
mkdir bin, dist

Compilar todos os pacotes:
javac -d bin -sourcepath src src/sperta/server/*.java src/sperta/client/*.java src/sperta/common/*.java src/sperta/model/*.java

Gerar os ficheiros JAR:
# Servidor
jar cfe dist/SpertaServer.jar sperta.server.SpertaServer -C bin .

# Cliente
jar cfe dist/SpertaClient.jar sperta.client.SpertaClient -C bin .

3. Como Executar
3.1. Iniciar o Servidor
O servidor necessita de um porto (ex: 22345).
java -jar dist/SpertaServer.jar 22345


3.2. Configurar Atestação (Obrigatório)
Antes de ligar o cliente, verifique o tamanho do ficheiro jar para atualizar o servidor:

Comando: (Get-Item dist/SpertaClient.jar).Length

No ficheiro server_data/app_attestation.txt, coloque: SpertaClient:<VALOR_OBTIDO>

3.3. Iniciar o Cliente
java -jar dist/SpertaClient.jar localhost:22345 <utilizador> <password>
Se o utilizador não existir, será criado automaticamente com a password fornecida.

4. Comandos Disponíveis
CREATE <casa>: Cria uma nova casa (o utilizador torna-se Owner).

ADD <user> <casa> <seccao>: O Owner dá permissão a outro utilizador.

RD <casa> <seccao>: Regista um novo dispositivo.

EC <casa> <dispositivo> <valor>: Altera estado (0/1) ou temporização (2-600).

RT <casa>: Descarrega o estado atual dos dispositivos permitidos.

RH <casa> <dispositivo>: Descarrega o histórico (.csv) do dispositivo.

5. Limitações do Trabalho

Armazenamento de Passwords: As passwords são guardadas em texto simples no ficheiro users.txt (ausência de Hashing).

Atestação Básica: A validação do cliente é baseada apenas no tamanho do ficheiro .jar, o que é vulnerável a ataques de colisão de tamanho.

Concorrência de Ficheiros: Embora os métodos de escrita sejam synchronized, a escalabilidade para milhares de casas pode ser limitada pelo uso de ficheiros de texto.

6. Autoria
Rafael Matias 61847
João Nunes 61786

Disciplina: Segurança e Confiabilidade