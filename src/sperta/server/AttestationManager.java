package sperta.server;
import java.io.File;
import java.util.Scanner;

public class AttestationManager {
    private static final String ATTESTATION_FILE = "server_data/app_attestation.txt";

    /**
     * Valida se o tamanho enviado pelo cliente coincide com o esperado.
     * 
     */
    public static boolean isValid(long receivedSize) {
        File file = new File(ATTESTATION_FILE);
        
        
        if (!file.exists()) {
            System.err.println("Erro: Ficheiro de atestação não encontrado em " + ATTESTATION_FILE);
            return false;
        }

        try (Scanner sc = new Scanner(file)) {
            if (sc.hasNextLine()) {
                String line = sc.nextLine(); 
                
                String[] parts = line.split(":");
                
                if (parts.length == 2 && parts[0].equals("SpertaClient")) {
                    long expectedSize = Long.parseLong(parts[1].trim());
                    
                   
                    return receivedSize == expectedSize;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao ler ficheiro de atestação: " + e.getMessage());
        }
        
        return false;
    }
}