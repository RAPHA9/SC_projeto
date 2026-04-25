package sperta.server;

import javax.net.ssl.*;

import sperta.common.Protocol;

import java.io.*;
import java.security.KeyStore;

public class SpertaServer {

    public static void main(String[] args) {

        if (args.length < 4) {
            System.out.println("Usage: SpertaServer <port> <password-cifra> <keystore> <password-keystore>");
            return;
        }

        int port            = Integer.parseInt(args[0]);
        String passwordCifra = args[1];
        String keystorePath  = args[2];
        String keystorePass  = args[3];

        try {
            if (!DataManager.initialize(passwordCifra)) {
                System.out.println(Protocol.NOKINTEGRITY);
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
}
