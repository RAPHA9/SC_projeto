import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
        if (houseExists(houseName)) return "NOK"; 

        try {
           
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(HOUSES_FILE, true)));
            out.println(houseName + ";owner:" + owner + ";perms:");
            out.close();

           
            PrintWriter outCount = new PrintWriter(new BufferedWriter(new FileWriter(COUNTERS_FILE, true)));
            outCount.println(houseName + ":M:1:L:1:P:1:G:1:S:1:E:1");
            outCount.close();

           
            new File(LOGS_PATH + houseName).mkdirs();
            
            return "OK";
        } catch (IOException e) { return "NOK"; }
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

            if (!houseFound) return "NOHM";

            
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

    
    public static synchronized String updateDeviceState(String house, String device, int value, String user) {
        
        String section = String.valueOf(device.charAt(0));
        if (!hasPermission(user, house, section)) return "NOPERM";

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

    public static synchronized boolean hasPermission(String user, String house, String section) {
        if (isOwner(house, user)) return true;

        try (Scanner sc = new Scanner(new File(HOUSES_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ";")) {
                    String[] parts = line.split(";");
                    for (String part : parts) {
                        if (part.startsWith(user + ":")) {
                            String permissions = part.split(":")[1];
                            return permissions.contains("all") || permissions.contains(section);
                        }
                    }
                }
            }
        } catch (IOException e) { return false; }
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
                    return line.contains("owner:" + userId + ";");
                }
            }
        } catch (FileNotFoundException e) {
            return false;
        }
        return false;
    }

    public static synchronized String addPermission(String admin, String targetUser, String house, String section) {
        if (!houseExists(house)) return Protocol.NOHM;
        if (!isOwner(house, admin)) return Protocol.NOPERM; 
        if (!userExists(targetUser)) return Protocol.NOUSER;

        List<String> lines = new ArrayList<>();
        try (Scanner sc = new Scanner(new File(HOUSES_FILE))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith(house + ";")) {
                   
                    if (!line.endsWith(";")) line += ";";
                    line += targetUser + ":" + section + ";";
                }
                lines.add(line);
            }
            
            try (PrintWriter pw = new PrintWriter(new FileWriter(HOUSES_FILE))) {
                for (String l : lines) pw.println(l);
            }
            return Protocol.OK; 
        } catch (IOException e) {
            return Protocol.NOK;
        }
    }

    /**
     * Retorna o ficheiro solicitado (log ou estados) após validar permissões.
     */
    public static synchronized File getFileForCommand(String user, String house, String device, String type) {
       
        if (!houseExists(house)) return null; 

       
        if (type.equals("RH")) {
           
            String section = String.valueOf(device.charAt(0));
            if (!hasPermission(user, house, section)) return null;

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

                        if (hasPermission(user, house, section)) {
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