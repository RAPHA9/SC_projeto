package sperta.server;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Base64;
import java.net.ServerSocket;
import java.net.Socket;

public class SpertaServer {

    public static void main(String[] args) {
        
        if (args.length < 4) {
            System.out.println("Usage: SpertaServer <port> <password-cifra> <keystore> <password-keystore>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String passwordCifra = args[1];    
        String keystorePath = args[2];     
        String keystorePass = args[3];       

        try {
           
            if (!DataManager.initialize(passwordCifra)) {
                System.out.println("NOK-INTEGRITY");
                System.exit(0);
            }

            
            SSLContext sslContext = createSSLContext(keystorePath, keystorePass);
            SSLServerSocketFactory factory = sslContext.getServerSocketFactory();

            try (SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port)) {
                System.out.println("SpertaServer iniciado no porto: " + port);

            
                while (true) {
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                    
                    Thread newThread = new Thread(new ClientHandler(clientSocket, passwordCifra));
                    newThread.start();
                }
            }

        } catch (Exception e) {
            System.err.println("Erro crítico no servidor: " + e.getMessage());
        }
    }

    
    private static SSLContext createSSLContext(String ksPath, String ksPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksPath), ksPass.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, ksPass.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null); 
        return ctx;
    }

    private static SecretKey decryptSectionKey(byte[] wrappedKey, String ksPath, String ksPass, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(ksPath)) {
            ks.load(fis, ksPass.toCharArray());
        }
        PrivateKey privKey = (PrivateKey) ks.getKey(alias, ksPass.toCharArray());

        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.UNWRAP_MODE, privKey);
        return (SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    private static String encryptValue(String value, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }
}