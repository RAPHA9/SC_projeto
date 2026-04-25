package sperta.server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import sperta.common.Protocol;

public class DataManager {
    private static final String DATA_PATH = "server_data/";
    private static final String USERS_FILE = DATA_PATH + "users.txt";
    private static final String HOUSES_FILE = DATA_PATH + "casas.txt";
    private static final String COUNTERS_FILE = DATA_PATH + "contadores.txt";
    private static final String STATES_FILE = DATA_PATH + "estados.txt";
    private static final String HOUSES_ROOT = DATA_PATH + "houses/";
    private static final String PBE_SALT_FILE = DATA_PATH + "pbe_salt.bin";

    private static String serverPbePassword; 

    private static final Map<String, String> SECTION_MAP = Map.of(
        "E", "Electros", "G", "Garden", "L", "Luzes",
        "M", "Multimedia", "P", "Portas", "S", "Stores"
    );

    
    
    public static boolean initialize(String password) {
        serverPbePassword = password;
        try {
            new File(DATA_PATH).mkdirs();
            new File(HOUSES_ROOT).mkdirs();

            String[] criticalFiles = {USERS_FILE, HOUSES_FILE, COUNTERS_FILE, STATES_FILE};

            for (String path : criticalFiles) {
                File f = new File(path);
                if (!f.exists()) {
                    if (path.equals(HOUSES_FILE)) {
                        encryptAndSaveFile(path, "".getBytes());
                    } else {
                        f.createNewFile();
                    }
                    updateFileHash(path);
                }
            }
            return verifyFileIntegrity(USERS_FILE) && verifyFileIntegrity(HOUSES_FILE) && 
                   verifyFileIntegrity(COUNTERS_FILE) && verifyFileIntegrity(STATES_FILE);
        } catch (Exception e) { return false; }
    }

   

    public static synchronized String authenticateOrRegister(String userId, String password) throws Exception {
        if (!verifyFileIntegrity(USERS_FILE)) {
            System.out.println("CRITICAL: Falha de integridade em users.txt");
            System.exit(0);
        }
        
        File file = new File(USERS_FILE);
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts[0].equals(userId)) {
                if (parts[1].equals(computeHash(password, parts[2]))) return Protocol.OK_USER;
                else return Protocol.WRONG_PWD;
            }
        }

        String salt = generateSalt();
        String hash = computeHash(password, salt);
        String newUserLine = userId + ":" + hash + ":" + salt + System.lineSeparator();
        Files.write(file.toPath(), newUserLine.getBytes(), StandardOpenOption.APPEND);
        updateFileHash(USERS_FILE);
        return Protocol.OK_NEW_USER;
    }

    

    public static synchronized String createHouse(String houseName, String owner, List<byte[]> encryptedSectionKeys) {
        try {
            if (houseExists(houseName)) return Protocol.NOK;

            File houseDir = new File(HOUSES_ROOT + houseName);
            houseDir.mkdirs();

            String[] sections = {"E", "G", "L", "M", "P", "S"};
            for (int i = 0; i < sections.length; i++) {
                File sectionDir = new File(houseDir, sections[i]);
                sectionDir.mkdirs();
                saveCounter(new File(sectionDir, "counter.txt"), 1);
                
               
                String keyName = String.format("key.%s.%s.%s", houseName, sections[i], owner);
                saveSectionKey(keyName, encryptedSectionKeys.get(i));
            }
            
            updateHousesFileSecurely(houseName, owner);
            return Protocol.OK;
        } catch (Exception e) { return Protocol.NOK; }
    }

    public static String getPathToCounter(String house, String section) {
       
        return HOUSES_ROOT + house + "/" + section.toUpperCase() + "/counter.txt";
    }

    private static void updateHousesFileSecurely(String houseName, String owner) throws Exception {
        byte[] content = getDecryptedFileContent(HOUSES_FILE);
        String newData = new String(content) + houseName + ";owner:" + owner + ";devices:;perms:" + System.lineSeparator();
        encryptAndSaveFile(HOUSES_FILE, newData.getBytes());
    }

    public static synchronized String addPermission(String owner, String targetUser, String house, String section, byte[] encryptedKey) {
        try {
            if (!houseExists(house)) return Protocol.NOHM;
            if (!isOwner(house, owner)) return Protocol.NOPERM;

            byte[] content = getDecryptedFileContent(HOUSES_FILE);
            List<String> lines = new ArrayList<>(Arrays.asList(new String(content).split(System.lineSeparator())));
            boolean found = false;

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(house + ";")) {
                    lines.set(i, lines.get(i) + targetUser + ":" + section + ",");
                    found = true;
                    break;
                }
            }
            if (!found) return Protocol.NOHM;

            encryptAndSaveFile(HOUSES_FILE, String.join(System.lineSeparator(), lines).getBytes());
            updateFileHash(HOUSES_FILE);

            
            saveSectionKey(String.format("key.%s.%s.%s", house, section, targetUser), encryptedKey);
            return Protocol.OK;
        } catch (Exception e) { return Protocol.NOK; }
    }

    

    private static SecretKey deriveKey(byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(serverPbePassword.toCharArray(), salt, 10000, 128);
        return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
    }

    public static byte[] getDecryptedFileContent(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) return new byte[0];
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, deriveKey(readPbeSalt()));
        return c.doFinal(Files.readAllBytes(file.toPath()));
    }

    public static void encryptAndSaveFile(String filePath, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, deriveKey(readPbeSalt()));
        Files.write(new File(filePath).toPath(), c.doFinal(data));
        updateFileHash(filePath);
    }

    private static byte[] readPbeSalt() throws IOException {
        File f = new File(PBE_SALT_FILE);
        if (!f.exists()) {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            Files.write(f.toPath(), salt);
            return salt;
        }
        return Files.readAllBytes(f.toPath());
    }

    public static boolean verifyFileIntegrity(String filePath, byte[] content) {
        try {
            File hashFile = new File(filePath + ".hash");
            
            
            if (!hashFile.exists()) {
                return content.length == 0; 
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] computedHash = md.digest(content);
            byte[] storedHash = Base64.getDecoder().decode(Files.readAllBytes(hashFile.toPath()));

            return MessageDigest.isEqual(computedHash, storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyFileIntegrity(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return true;
            byte[] data = path.equals(HOUSES_FILE) ? getDecryptedFileContent(path) : Files.readAllBytes(f.toPath());
            
            File hFile = new File(path + ".hash");
            if (!hFile.exists()) return data.length == 0;

            byte[] stored = Base64.getDecoder().decode(Files.readAllBytes(hFile.toPath()));
            return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(data), stored);
        } catch (Exception e) { return false; }
    }

   

    private static void saveCounter(File file, int value) throws Exception {
        Files.write(file.toPath(), String.valueOf(value).getBytes());
        updateFileHash(file.getPath());
    }

    public static void saveSectionKey(String name, byte[] key) throws IOException {
        File f = new File(DATA_PATH + name);
        Files.write(f.toPath(), key);
        try { updateFileHash(f.getPath()); } catch (Exception e) {}
    }

    private static String generateSalt() {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        return Base64.getEncoder().encodeToString(s);
    }

    private static String computeHash(String p, String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(p.getBytes());
        md.update(Base64.getDecoder().decode(s));
        return Base64.getEncoder().encodeToString(md.digest());
    }

    private static void updateFileHash(String path) throws Exception {
        byte[] data;
        if (path.equals(HOUSES_FILE)) data = getDecryptedFileContent(path);
        else data = Files.readAllBytes(new File(path).toPath());
        
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        Files.write(new File(path + ".hash").toPath(), Base64.getEncoder().encode(hash));
    }

    public static boolean houseExists(String name) { 
        return new File(HOUSES_ROOT + name).exists(); 
    }

    public static synchronized boolean isOwner(String house, String user) {
        try {
            byte[] data = getDecryptedFileContent(HOUSES_FILE);
            Scanner sc = new Scanner(new ByteArrayInputStream(data));
            while (sc.hasNextLine()) {
                String l = sc.nextLine();
                if (l.startsWith(house + ";") && l.contains("owner:" + user)) return true;
            }
        } catch (Exception e) { return false; }
        return false;
    }

    public static byte[] getEncryptedSectionKey(String house, String section, String user) throws IOException {
        File f = new File(DATA_PATH + String.format("key.%s.%s.%s", house, section, user));
        return f.exists() ? Files.readAllBytes(f.toPath()) : null;
    }

    
    public static synchronized String registerDevice(String houseName, String sectionLetter, String requestingUser) {
        if (!houseExists(houseName)) return Protocol.NOHM;
        if (!isOwner(houseName, requestingUser)) return Protocol.NOPERM;

        String sectionUpper = sectionLetter.toUpperCase();
        File counterFile = new File(HOUSES_ROOT + houseName + "/" + sectionUpper + "/counter.txt");

        try {
            if (!verifyFileIntegrity(counterFile.getPath())) return "NOK-INTEGRITY";

            int currentCount;
            try (Scanner sc = new Scanner(counterFile)) {
                currentCount = sc.hasNextInt() ? sc.nextInt() : 1;
            }
            
            String newDeviceId = sectionUpper + currentCount;
            saveCounter(counterFile, currentCount + 1);

            updateGlobalCounters(houseName, sectionUpper, currentCount + 1);
            securelyAddDeviceToHouse(houseName, newDeviceId);

            return "OK";
        } catch (Exception e) { return "NOK"; }
    }


   
    private static void updateGlobalCounters(String houseName, String section, int nextId) throws Exception {
        if (!verifyFileIntegrity(COUNTERS_FILE)) {
            System.out.println("NOK-INTEGRITY");
            System.exit(0);
        }
        
        File file = new File(COUNTERS_FILE);
        List<String> lines = file.exists() ? Files.readAllLines(file.toPath()) : new ArrayList<>();
        boolean houseFound = false;
        String entry = section + ":" + nextId;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(houseName + ";")) {
                lines.set(i, lines.get(i) + entry + ",");
                houseFound = true;
                break;
            }
        }
        if (!houseFound) lines.add(houseName + ";" + entry + ",");
        
        Files.write(file.toPath(), lines);
        updateFileHash(COUNTERS_FILE);
    }

    
    private static synchronized void securelyAddDeviceToHouse(String houseName, String deviceId) throws Exception {
        byte[] content = getDecryptedFileContent(HOUSES_FILE);
        List<String> lines = new ArrayList<>();
        
        try (Scanner sc = new Scanner(new ByteArrayInputStream(content))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(houseName + ";")) {
                    
                    if (line.contains("devices:")) {
                        line = line.replaceFirst("devices:([^;]*)", "devices:$1," + deviceId);
                    } else {
                        line = line.replace("owner:", "devices:" + deviceId + ";owner:");
                    }
                }
                lines.add(line);
            }
        }
        
        encryptAndSaveFile(HOUSES_FILE, String.join(System.lineSeparator(), lines).getBytes());
    }

    private static String updatePermissionString(String line, String targetPerm) {
        if (line.contains("perms:")) {
            return line.endsWith("perms:") ? line + targetPerm : line + "," + targetPerm;
        }
        return line + ";perms:" + targetPerm;
    }
    
    public static void saveUserCertificate(String userId, byte[] certBytes) throws IOException {
        String certPath = DATA_PATH + userId + ".cert";
        File certFile = new File(certPath);
        
       
        Files.write(certFile.toPath(), certBytes);
        
        
        try {
            updateFileHash(certPath);
        } catch (Exception e) {
            throw new IOException("Erro ao gerar hash de integridade para o certificado: " + e.getMessage());
        }
    }


    public static synchronized boolean userExists(String userId) {
      
        if (!verifyFileIntegrity(USERS_FILE)) {
            System.out.println(Protocol.NOKINTEGRITY); 
            System.exit(0); 
        }

        try (Scanner sc = new Scanner(new File(USERS_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isEmpty()) continue;
               
                if (line.split(":")[0].equals(userId)) return true;
            }
        } catch (IOException e) { 
            return false; 
        }
        return false;
    }

    
    public static synchronized boolean deviceExists(String house, String device) {
        
        if (device == null || device.length() < 2) return false;
        
        String section = String.valueOf(device.charAt(0)).toUpperCase();
        File counterFile = new File(HOUSES_ROOT + house + "/" + section + "/counter.txt");
        
        if (!counterFile.exists()) return false;

        try (Scanner sc = new Scanner(counterFile)) {
            int nextId = sc.hasNextInt() ? sc.nextInt() : 1;
            
            int deviceNum = Integer.parseInt(device.substring(1));
            return deviceNum > 0 && deviceNum < nextId;
        } catch (Exception e) { 
            return false; 
        }
    }

    
    public static synchronized String updateDeviceState(String house, String device, String encryptedValue, String user) {
        if (!houseExists(house)) return Protocol.NOHM; 

        String section = String.valueOf(device.charAt(0)).toUpperCase();
        
        if (!hasPermission(house, user, section)) {
            return Protocol.NOPERM; 
        }
        
        if (!deviceExists(house, device)) {
            return Protocol.NOD; 
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logEntry = String.format("%s, %s", timestamp, encryptedValue);
            
            File logFile = new File(HOUSES_ROOT + house + "/" + section + "/" + device + ".csv");

            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
                pw.println(logEntry);
            }
            
            updateFileHash(logFile.getPath());
            updateStatesFile(house, device, encryptedValue);
            return Protocol.OK;
        } catch (Exception e) { 
            e.printStackTrace();
            return Protocol.NOK; 
        }
    }

    public static synchronized boolean hasPermission(String house, String user, String section) {
        if (isOwner(house, user)) return true;
        try {
            byte[] content = getDecryptedFileContent(HOUSES_FILE);
            Scanner sc = new Scanner(new ByteArrayInputStream(content));
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ";")) {
                    return line.contains("perms:" + user + ":" + section)
                        ||line.contains("perms:" + user + ":all")
                        || line.contains("," + user + ":" + section)
                        || line.contains("," + user + ":all");
                }
            }
        } catch (Exception e) { return false; }
        return false;
    }

    public static synchronized boolean hasAnyPermissionInHouse(String user, String house) {
        
        if (isOwner(house, user)) return true;

        try {
         
            byte[] decryptedContent = getDecryptedFileContent(HOUSES_FILE);
            
          
            if (!verifyFileIntegrity(HOUSES_FILE, decryptedContent)) {
                System.out.println(Protocol.NOKINTEGRITY); 
                System.exit(0); 
            }

           
            try (Scanner sc = new Scanner(new ByteArrayInputStream(decryptedContent))) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    
                    if (line.startsWith(house + ";")) {
                        
                        return line.contains(":" + user + ":") || line.contains("," + user + ":") || line.contains(":" + user + ",");
                    }
                }
            }
        } catch (Exception e) {
           
            System.out.println(Protocol.NOKINTEGRITY);
            System.exit(0);
        }
        return false;
    }




    
    public static synchronized File getFileForCommand(String user, String house, String device, String type) {
        if (!houseExists(house)) return null; 

       
        if (type.equals("RH")) {
            String sectionLetter = String.valueOf(device.charAt(0)).toUpperCase();
            String folderName = sectionLetter;

            if (folderName == null) return null;
           
            if (!hasPermission(house, user, sectionLetter)) return null;

            File logFile = new File(HOUSES_ROOT + house + "/" + folderName + "/" + device + ".csv");
            
            if (logFile.exists() && !verifyFileIntegrity(logFile.getPath())) {
                System.out.println(Protocol.NOKINTEGRITY);
                System.exit(0);
            }
            
            return logFile.exists() ? logFile : null; 
        }

      
        if (type.equals("RT")) {
         
            if (hasAnyPermissionInHouse(user, house)) {
              
                return generateFilteredStatesFile(user, house);
            }
        }

        return null;
    }

    private static File generateFilteredStatesFile(String user, String house) {
        try {
            
            if (!verifyFileIntegrity(STATES_FILE)) {
                System.out.println(Protocol.NOKINTEGRITY); // 
                System.exit(0);
            }

            File tempFile = File.createTempFile("RT_" + house + "_", ".txt");
            PrintWriter writer = new PrintWriter(new FileWriter(tempFile));

            File statesFile = new File(STATES_FILE);
            if (!statesFile.exists()) {
                writer.close();
                return null;
            }

            
            try (Scanner sc = new Scanner(statesFile)) {
                boolean foundData = false;
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith(house + ":")) {
                        String[] parts = line.split(":");
                      
                        for (int i = 1; i < parts.length; i += 2) {
                            String deviceId = parts[i];
                            String encryptedValue = parts[i+1]; 
                            String section = String.valueOf(deviceId.charAt(0));

                           
                            if (hasPermission(house, user, section)) {
                                writer.println(deviceId + ":" + encryptedValue);
                                foundData = true;
                            }
                        }
                    }
                }
                writer.close();
                return foundData ? tempFile : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    
    public static synchronized void updateStatesFile(String house, String device, String encryptedValue) throws Exception {
        File file = new File(STATES_FILE);
        List<String> lines = file.exists() ? Files.readAllLines(file.toPath()) : new ArrayList<>();
        boolean houseFound = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(house + ":")) {
                houseFound = true;
                if (line.contains(device + ":")) {
                    lines.set(i, line.replaceFirst(device + ":[^:]+", device + ":" + encryptedValue));
                } else {
                    lines.set(i, line + ":" + device + ":" + encryptedValue);
                }
                break;
            }
        }
        if (!houseFound) lines.add(house + ":" + device + ":" + encryptedValue);
        Files.write(file.toPath(), lines);
        updateFileHash(STATES_FILE);
    }

    
    public static byte[] getUserCertificate(String userId) throws IOException {
        File certFile = new File(DATA_PATH + userId + ".cert");
        if (!certFile.exists()) return null;
        return Files.readAllBytes(certFile.toPath());
    }

    public static byte[] getSectionKey(String house, String section, String user) throws IOException {
        File f = new File(DATA_PATH + String.format("key.%s.%s.%s", house, section, user));
        if (!f.exists()) return null;
        try {
            if (!verifyFileIntegrity(f.getPath())) return null;
        } catch (Exception e) { return null; }
        return Files.readAllBytes(f.toPath());
    }
    
}