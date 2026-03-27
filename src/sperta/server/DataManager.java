package sperta.server;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import sperta.common.Protocol;

public class DataManager {
    private static final String DATA_PATH = "server_data/";
    private static final String LOGS_PATH = "logs/";
    private static final String USERS_FILE = DATA_PATH + "users.txt";
    private static final String HOUSES_FILE = DATA_PATH + "casas.txt";
    private static final String COUNTERS_FILE = DATA_PATH + "contadores.txt";
    private static final String STATES_FILE = DATA_PATH + "estados.txt";
    

    public static synchronized String authenticateOrRegister(String userId, String password) {
        File file = new File(USERS_FILE);
        try {
            if (!file.exists()) file.createNewFile();
            
            Scanner sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split(":");
                if (parts[0].equals(userId)) {
                    sc.close();
                    return parts[1].equals(password) ? "OK-USER" : "WRONG-PWD";
                }
            }
            sc.close();

            
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(USERS_FILE, true)));
            out.println(userId + ":" + password);
            out.close();
            return "OK-NEW-USER"; 
        } catch (IOException e) { return "ERROR"; }
    }

   
    public static synchronized String createHouse(String houseName, String owner) {
        try {
            if (houseExists(houseName)) {
                return Protocol.NOK;
            }

           
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(HOUSES_FILE, true)))) {
                out.println(houseName + ";owner:" + owner + ";perms:");
            }

            
            try (PrintWriter outCount = new PrintWriter(new BufferedWriter(new FileWriter(COUNTERS_FILE, true)))) {
                outCount.println(houseName + ":M:1:L:1:P:1:G:1:S:1:E:1");
            }

           
            File logsDir = new File(LOGS_PATH + houseName);
            logsDir.mkdirs();

            return Protocol.OK;

        } catch (Exception e) { 
            e.printStackTrace(); 
            return Protocol.NOK; 
        }
    }

    
    public static synchronized String registerDevice(String houseName, String section) {
        File file = new File(COUNTERS_FILE);
        List<String> lines = new ArrayList<>();
        String newDeviceId = "";
        boolean houseFound = false;

        try {
            if (!file.exists()) return "NOHM";

            Scanner sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
               
                if (line.startsWith(houseName + ":")) {
                    houseFound = true;
                    String[] parts = line.split(":");
                    StringBuilder newLine = new StringBuilder(parts[0]);
                    
                    for (int i = 1; i < parts.length; i += 2) {
                        if (parts[i].equals(section)) {
                            int currentCount = Integer.parseInt(parts[i+1]);
                            newDeviceId = section + currentCount; 
                            newLine.append(":").append(parts[i]).append(":").append(currentCount + 1);
                        } else {
                            newLine.append(":").append(parts[i]).append(":").append(parts[i+1]);
                        }
                    }
                    lines.add(newLine.toString());
                } else {
                    lines.add(line);
                }
            }
            sc.close();

            if (!houseFound) return Protocol.NOHM;

            
            PrintWriter pw = new PrintWriter(new FileWriter(COUNTERS_FILE));
            for (String l : lines) pw.println(l);
            pw.close();

            return "OK"; 
        } catch (IOException e) {
            return "NOK";
        }
    }

    public static synchronized boolean userExists(String userId) {
        try (Scanner sc = new Scanner(new File(USERS_FILE))) {
            while (sc.hasNextLine()) {
                if (sc.nextLine().split(":")[0].equals(userId)) return true;
            }
        } catch (IOException e) { return false; }
        return false;
    }

    public static synchronized boolean houseExists(String name) {
        try (Scanner sc = new Scanner(new File(HOUSES_FILE))) {
            while (sc.hasNextLine()) {
                if (sc.nextLine().startsWith(name + ";")) return true;
            }
        } catch (IOException e) { return false; }
        return false;
    }

    /**
     * Verifica se um dispositivo (ex: B3) foi registado na casa.
     */
    public static synchronized boolean deviceExists(String house, String device) {
        if (device == null || device.length() < 2) return false;
        
        String section = String.valueOf(device.charAt(0));
        int deviceNum;
        try {
            deviceNum = Integer.parseInt(device.substring(1));
        } catch (NumberFormatException e) {
            return false;
        }

        try (Scanner sc = new Scanner(new File(COUNTERS_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ":")) {
                    String[] parts = line.split(":");
                    // O formato é casa:S1:C1:S2:C2...
                    for (int i = 1; i < parts.length; i += 2) {
                        if (parts[i].equals(section)) {
                            int nextId = Integer.parseInt(parts[i+1]);
                            // Se o contador for 4, os IDs registados são 1, 2 e 3.
                            return deviceNum > 0 && deviceNum < nextId;
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    
    public static synchronized String updateDeviceState(String house, String device, int value, String user) {
        
        String section = String.valueOf(device.charAt(0));
        
        if (!hasPermission(house, user, section)) {
            return "NOPERM";
        }
        if(!houseExists(house)){
            return Protocol.NOHM;
        }
        if(!deviceExists(house, device)){
            return Protocol.NOD;
        }

        try {
            
            String logEntry = String.format("%s, %d", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), value);
            File logFile = new File(LOGS_PATH + house + "/" + device + ".csv");
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
                out.println(logEntry);
            }

            
            updateStatesFile(house, device, value);

            return "OK";
        } catch (IOException e) { return "NOK"; }
    }

    public static synchronized boolean hasPermission(String house, String user, String section) {
        if (isOwner(house, user)) return true;

        File f = new File(HOUSES_FILE);
        try (Scanner sc = new Scanner(f)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ";")) {
                    String[] parts = line.split(";");
                    for (int i = 2; i < parts.length; i++) {
                        String p = parts[i]; 
                        if (p.contains(":")) {
                            String[] pair = p.split(":");
                            if (pair.length < 2) continue;
                            String u = pair[0].trim();
                            String s = pair[1].trim();

                            if (u.equals(user)) {
                                
                                if (section == null || s.equals(section)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { return false; }
        return false;
    }

    public static synchronized boolean hasAnyPermissionInHouse(String user, String house) {
        if (isOwner(house, user)) return true;

        try (Scanner sc = new Scanner(new File(HOUSES_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ";")) {
                    return line.contains(";" + user + ":");
                }
            }
        } catch (IOException e) { return false; }
        return false;
    }


    public static synchronized boolean isOwner(String houseName, String userId) {
        try (Scanner sc = new Scanner(new File(HOUSES_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(houseName + ";")) {
                    String[] parts = line.split(";");
                    if (parts.length > 1) {
                        
                        String actualOwner = parts[1].replace("owner:", "").trim();
                        return actualOwner.equals(userId);
                    }
                }
            }
        } catch (IOException e) { return false; }
        return false;
    }

    public static synchronized String addPermission(String owner, String targetUser, String house, String section) {
        if(!houseExists(house)){
            return Protocol.NOHM;
        }
        if(!userExists(targetUser)){
            return Protocol.NOUSER;
        }
        if (!isOwner(house, owner)) {
            return Protocol.NOPERM;
        }
        
        List<String> lines = new ArrayList<>();
        boolean houseFound = false;
        String targetPerm = targetUser + ":" + section;
        
        try {
            File f = new File(HOUSES_FILE);
            if (!f.exists()) return Protocol.NOHM;

            try (Scanner sc = new Scanner(f)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith(house + ";")) {
                        houseFound = true;
                        
                        String[] parts = line.split(";");
                        boolean permExists = false;
                        for (String part : parts) {
                            if (part.equals(targetPerm)) {
                                permExists = true;
                                break;
                            }
                        }

                        if (!permExists) {
                            if (!line.endsWith(";")) line += ";";
                            line += targetPerm + ";";
                        }
                    }
                    lines.add(line);
                }
            }

            if (!houseFound) return Protocol.NOHM;

            try (PrintWriter out = new PrintWriter(new FileWriter(HOUSES_FILE))) {
                for (String l : lines) out.println(l);
            }
            return Protocol.OK;
        } catch (IOException e) { 
            return Protocol.NOK; 
        }
    }

    
    public static synchronized File getFileForCommand(String user, String house, String device, String type) {
       
        if (!houseExists(house)) return null; 

       
        if (type.equals("RH")) {
           
            String section = String.valueOf(device.charAt(0));
            if (!hasPermission(house, user, section)) return null;

            File logFile = new File(LOGS_PATH + house + "/" + device + ".csv");
            return logFile.exists() ? logFile : null; 
        }

       
        if (type.equals("RT")) {
            if (isOwner(house, user) || hasAnyPermissionInHouse(user, house)) {
                return generateFilteredStatesFile(user, house);
            }
        }

        return null;
    }

    private static File generateFilteredStatesFile(String user, String house) {
        try {
            File tempFile = File.createTempFile("RT_" + house + "_", ".txt");
            PrintWriter writer = new PrintWriter(new FileWriter(tempFile));

            
            File statesFile = new File(STATES_FILE); 
            if (!statesFile.exists()) {
                writer.close();
                return null; 
            }

            Scanner sc = new Scanner(statesFile);
            boolean foundData = false;
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ":")) {
                    String[] parts = line.split(":");
                    for (int i = 1; i < parts.length; i += 2) {
                        String deviceId = parts[i];
                        String value = parts[i+1];
                        String section = String.valueOf(deviceId.charAt(0));

                        if (hasPermission(house, user, section)) {
                            writer.println(deviceId + ":" + value);
                            foundData = true;
                        }
                    }
                }
            }
            sc.close();
            writer.close();

            return foundData ? tempFile : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void updateStatesFile(String house, String device, int value) throws IOException {
        File file = new File(STATES_FILE);
        List<String> lines = new ArrayList<>();
        boolean houseFound = false;

        if (file.exists()) {
            try (Scanner sc = new Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith(house + ":")) {
                        houseFound = true;
                        line = line.contains(device + ":") 
                            ? line.replaceAll(device + ":\\d+", device + ":" + value)
                            : line + ":" + device + ":" + value;
                    }
                    lines.add(line);
                }
            }
        }

        if (!houseFound) {
            lines.add(house + ":" + device + ":" + value);
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(STATES_FILE))) {
            for (String l : lines) pw.println(l);
        }
    }
    
}