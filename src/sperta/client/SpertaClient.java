package sperta.client;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import sperta.common.Protocol;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class SpertaClient {

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Usage: SpertaClient <serverAddress> <truststore> <password-truststore> " +
                               "<keystore> <password-keystore> <user-id> <password>");
            return;
        }

        String address    = args[0];
        String trustStorePath = args[1];
        String trustStorePass = args[2];
        String keyStorePath   = args[3];
        String keyStorePass   = args[4];
        String userId         = args[5];
        String password       = args[6];

        String host = address.contains(":") ? address.split(":")[0] : address;
        int port    = address.contains(":") ? Integer.parseInt(address.split(":")[1]) : 22345;

        try {
            SSLContext sslContext = createSSLContext(keyStorePath, keyStorePass, trustStorePath, trustStorePass);
            SSLSocketFactory factory = sslContext.getSocketFactory();

            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in   = new ObjectInputStream(socket.getInputStream());
                 Scanner scanner = new Scanner(System.in)) {

                
                long nonce = in.readLong();
                File jarFile = new File(SpertaClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(longToBytes(nonce));
                byte[] hashAtestacao = md.digest(jarBytes);

                out.writeObject(hashAtestacao);
                out.flush();

                String attestationStatus = (String) in.readObject();
                System.out.println("Attestation: " + attestationStatus);
                if (attestationStatus.equals("NOK-ATTEST")) return;

                
                out.writeObject(userId);
                out.writeObject(password);
                out.flush();

                String authStatus = (String) in.readObject();
                if (authStatus.equals("SEND-CERT")) {
                    byte[] certBytes = extractCertificate(keyStorePath, keyStorePass, userId);
                    out.writeObject(certBytes);
                    out.flush();
                    authStatus = (String) in.readObject();
                }
                System.out.println("Server Auth Response: " + authStatus);
                if (authStatus.equals("WRONG-PWD")) return;

                
                System.out.println("Introduza o comando:");
                CommandHandler.showHelp();

                while (true) {
                    System.out.print("> ");
                    String command = scanner.nextLine().trim();
                    if (command.equalsIgnoreCase("EXIT")) break;
                    if (command.isEmpty()) continue;

                    if (command.equalsIgnoreCase("HELP")) {
                        CommandHandler.showHelp();
                        continue;
                    }

                    if (!CommandHandler.validateCommand(command)) continue;

                    String[] tokens = command.split("\\s+");
                    String cmdType = tokens[0].toUpperCase();

                    if (cmdType.equals("CREATE")) {
                        out.writeObject("CREATE " + tokens[1].toLowerCase());
                        List<byte[]> keys = generateAndWrapSectionKeys(keyStorePath, keyStorePass, userId);
                        out.writeObject(keys);

                    } else if (cmdType.equals("ADD")) {
                        PublicKey targetPubKey = getTargetPublicKey(tokens[1], out, in, trustStorePath, trustStorePass);
                        out.writeObject("GET_KEY " + tokens[2].toLowerCase() + " " + tokens[3].toUpperCase());
                        out.flush();

                        Object respKey = in.readObject();
                        if (respKey instanceof byte[]) {
                            SecretKey secKey = decryptSectionKey((byte[]) respKey, keyStorePath, keyStorePass, userId);
                            byte[] wrappedForTarget = wrapKeyForUser(secKey, targetPubKey);

                            out.writeObject("ADD " + tokens[1] + " " + tokens[2].toLowerCase() + " " + tokens[3].toUpperCase());
                            out.writeObject(wrappedForTarget);
                        } else {
                            System.out.println("Erro ao obter chave para partilha: " + respKey);
                            continue;
                        }

                    } else if (cmdType.equals("RD")) {
                        String house   = tokens[1].toLowerCase();
                        String section = tokens[2].toUpperCase();
                        out.writeObject("RD " + house + " " + section);

                    } else if (cmdType.equals("EC")) {
                        String house        = tokens[1].toLowerCase();
                        String device       = tokens[2].toUpperCase();
                        String valToEncrypt = tokens[3];

                        
                        int intVal;
                        try {
                            intVal = Integer.parseInt(valToEncrypt);
                        } catch (NumberFormatException e) {
                            System.out.println(Protocol.NOK);
                            continue;
                        }
                        if (intVal < 0 || intVal > 600) {
                            System.out.println(Protocol.NOK);
                            continue;
                        }

                        out.writeObject("EC " + house + " " + device);
                        out.flush();

                        Object resp = in.readObject();

                        if (resp instanceof byte[]) {
                            SecretKey secKey = decryptSectionKey((byte[]) resp, keyStorePath, keyStorePass, userId);
                            String encVal = encryptValue(valToEncrypt, secKey);
                            out.writeObject(encVal);
                            out.flush();
                            System.out.println(in.readObject());
                        } else {
                            System.out.println(resp);
                        }
                        continue;

                    } else if (cmdType.equals("RT") || cmdType.equals("RH")) {
                        String house = tokens[1].toLowerCase();
                        String rest  = (tokens.length > 2) ? " " + tokens[2].toUpperCase() : "";
                        out.writeObject(cmdType + " " + house + rest);

                    } else {
                        out.writeObject(command);
                    }

                    out.flush();
                    Object response = in.readObject();
                    handleResponse(response, in, cmdType, tokens, keyStorePath, keyStorePass, userId);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro de segurança ou ligação: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleResponse(Object response, ObjectInputStream in, String cmdType, String[] tokens,
                                       String ksPath, String ksPass, String userId) throws Exception {

        if (response.equals("OK") && (cmdType.equals("RT") || cmdType.equals("RH"))) {

            Object keyData = in.readObject();
            Map<String, SecretKey> decryptedKeys = new HashMap<>();

            if (keyData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, byte[]> wrappedMap = (Map<String, byte[]>) keyData;
                for (Map.Entry<String, byte[]> entry : wrappedMap.entrySet()) {
                    decryptedKeys.put(entry.getKey(), decryptSectionKey(entry.getValue(), ksPath, ksPass, userId));
                }
            } else if (keyData instanceof byte[]) {
                String section = tokens[2].substring(0, 1).toUpperCase();
                decryptedKeys.put(section, decryptSectionKey((byte[]) keyData, ksPath, ksPass, userId));
            }

            byte[] encryptedFileData = (byte[]) in.readObject();
            boolean isHistory = cmdType.equals("RH");

            
            String fileName = isHistory
                ? tokens[1].toLowerCase() + "_" + tokens[2].toUpperCase() + "_log.csv"
                : "RT_" + tokens[1].toLowerCase() + ".txt";

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileName), true)) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(encryptedFileData)));
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    String currentSec = isHistory
                        ? tokens[2].substring(0, 1).toUpperCase()
                        : line.split("\\|", 2)[0].substring(0, 1).toUpperCase();

                    if (decryptedKeys.containsKey(currentSec)) {
                        String decrypted = decryptLine(line, decryptedKeys.get(currentSec), isHistory);
                        writer.println(decrypted);
                    } else {
                        System.out.println(line + " (Sem permissão para secção " + currentSec + ")");
                    }
                }
            }
            System.out.println("\nFicheiro guardado em: " + fileName);

        } else {
            System.out.println(response);
        }
    }

    private static String decryptLine(String line, SecretKey key, boolean isHistory) throws Exception {
        try {
            if (isHistory) {
                
                int sep = line.indexOf(", ");
                if (sep < 0) return line;
                String timestamp = line.substring(0, sep);
                String encPart   = line.substring(sep + 2);
                return timestamp + ", " + decryptValue(encPart, key);
            } else {
              
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) return line;
                return parts[0] + ":" + decryptValue(parts[1], key);
            }
        } catch (Exception e) {
            return line + " (Erro na decifração)";
        }
    }


    private static SSLContext createSSLContext(String ksPath, String ksPass, String tsPath, String tsPass) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksPath), ksPass.toCharArray());
        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(new FileInputStream(tsPath), tsPass.toCharArray());

        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, ksPass.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

   

    private static List<byte[]> generateAndWrapSectionKeys(String ksPath, String ksPass, String userId) throws Exception {
        List<byte[]> wrappedKeys = new ArrayList<>();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksPath), ksPass.toCharArray());
        PublicKey pubKey = ks.getCertificate(userId).getPublicKey();
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.WRAP_MODE, pubKey);
        for (int i = 0; i < 6; i++) {
            SecretKey secKey = keyGen.generateKey();
            wrappedKeys.add(rsaCipher.wrap(secKey));
        }
        return wrappedKeys;
    }

    private static SecretKey decryptSectionKey(byte[] wrappedKey, String ksPath, String ksPass, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksPath), ksPass.toCharArray());
        PrivateKey privKey = (PrivateKey) ks.getKey(alias, ksPass.toCharArray());
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.UNWRAP_MODE, privKey);
        return (SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    private static String encryptValue(String value, SecretKey key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(value.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
    }


    private static String decryptValue(String encryptedData, SecretKey key) throws Exception {
        String[] parts = encryptedData.split(":");
        if (parts.length < 2) throw new IllegalArgumentException("Formato cifrado inválido");
        byte[] iv         = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }

    private static byte[] extractCertificate(String ksPath, String ksPass, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(ksPath), ksPass.toCharArray());
        return ks.getCertificate(alias).getEncoded();
    }

    private static PublicKey getTargetPublicKey(String targetUser, ObjectOutputStream out, ObjectInputStream in,
                                                String tsPath, String tsPass) throws Exception {
        KeyStore ts = KeyStore.getInstance("JKS");
        File tsFile = new File(tsPath);
        if (tsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(tsFile)) {
                ts.load(fis, tsPass.toCharArray());
            }
        } else {
            ts.load(null, tsPass.toCharArray());
        }

        if (ts.containsAlias(targetUser)) {
            return ts.getCertificate(targetUser).getPublicKey();
        }

        out.writeObject("GET_CERT " + targetUser);
        out.flush();
        Object response = in.readObject();

        if (response instanceof byte[]) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream((byte[]) response));
            ts.setCertificateEntry(targetUser, cert);
            try (FileOutputStream fos = new FileOutputStream(tsPath)) {
                ts.store(fos, tsPass.toCharArray());
            }
            return cert.getPublicKey();
        } else {
            throw new Exception("Utilizador não encontrado: " + response);
        }
    }


    private static byte[] wrapKeyForUser(SecretKey secKey, PublicKey pubKey) throws Exception {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsaCipher.init(Cipher.WRAP_MODE, pubKey);
        return rsaCipher.wrap(secKey);
    }


    private static byte[] longToBytes(long x) {
        return java.nio.ByteBuffer.allocate(Long.BYTES).putLong(x).array();
    }
}
