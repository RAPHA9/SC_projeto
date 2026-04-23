package sperta.server;

import java.io.File;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Scanner;

public class AttestationManager {
    private static final String ATTESTATION_FILE = "server_data/app_attestation.txt";

    
    public static boolean isValid(byte[] clientHash, long nonce) {
        File infoFile = new File(ATTESTATION_FILE);
        
        if (!infoFile.exists()) {
            System.err.println("Erro: Ficheiro de configuração de atestação não encontrado.");
            return false;
        }

        try (Scanner sc = new Scanner(infoFile)) {
            if (sc.hasNextLine()) {
                String line = sc.nextLine();
                
                String[] parts = line.split(":");
                
                if (parts.length == 2 && parts[0].equals("SpertaClient")) {
                    String referenceJarPath = parts[1].trim();
                    File jarFile = new File(referenceJarPath);

                    if (!jarFile.exists()) {
                        System.err.println("Erro: JAR de referência não encontrado em " + referenceJarPath);
                        return false;
                    }

                   
                    byte[] jarBytes = Files.readAllBytes(jarFile.toPath());

                     
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    
                  
                    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(nonce);
                    md.update(buffer.array());
                    
                  
                    byte[] localHash = md.digest(jarBytes);

                  
                    return MessageDigest.isEqual(clientHash, localHash);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no processo de atestação: " + e.getMessage());
        }
        
        return false;
    }
}