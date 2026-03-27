package sperta.client;
import java.io.*;
import java.net.*;
import java.util.Scanner;


public class SpertaClient {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: SpertaClient <serverAddress> <user-id> <password>");
            return;
        }

        String address = args[0];
        String user = args[1];
        String password = args[2];
        String host = address;
        int port = 22345;

        if (address.contains(":")) {
            String[] parts = address.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            // 1. ATESTAÇÃO DINÂMICA
            File currentFile = new File(SpertaClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            long clientSize = currentFile.length(); 
            out.writeObject(clientSize);
            out.flush();

            String attestationMsg = (String) in.readObject();
            System.out.println(attestationMsg); 
            
            if (attestationMsg.contains("FAILED")) return; 

            // 2. AUTENTICAÇÃO
            out.writeObject(user);
            out.writeObject(password);
            out.flush();

            String authStatus = (String) in.readObject();
            System.out.println("Server Response: " + authStatus); 

            if (authStatus.equals("WRONG-PWD")) return;

            // 3. CICLO DE COMANDOS
            System.out.println("Introduza o comando (ou CTRL+C para sair):");
            CommandHandler.showHelp();
            while (true) { 
                System.out.print("> ");
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("EXIT")) break;
                if (command.equalsIgnoreCase("HELP")) {
                    CommandHandler.showHelp();
                    continue;
                }

                if (CommandHandler.validateCommand(command)) {
                    out.writeObject(command);
                    out.flush();
                    
                    Object response = in.readObject();
                    String respStr = response.toString();

                   
                    if (respStr.equals("OK") && (command.toUpperCase().startsWith("RT") || command.toUpperCase().startsWith("RH"))) {
                        String localFileName = command.toUpperCase().startsWith("RH") ? "historico.csv" : "estado_casa.txt";
                        receiveFile(in, localFileName, respStr);
                    } else {
                        System.out.println(respStr); 
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Erro de ligação: " + e.getMessage());
        }
    }

    private static void receiveFile(ObjectInputStream in, String fileName, String status) throws Exception {
        long fileSize = (long) in.readObject();
        
       
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int read;
            
            while (totalRead < fileSize && (read = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fos.write(buffer, 0, read);
                totalRead += read;
            }
            System.out.println(status + ", " + fileSize + " (long), seguido de " + totalRead + " bytes de dados.");
        }
    }
}