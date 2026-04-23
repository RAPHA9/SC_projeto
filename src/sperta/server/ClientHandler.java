package sperta.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import sperta.common.Protocol;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String currentUser;
    private String passwordCifra;

    public ClientHandler(Socket socket, String passwordCifra) {
        this.socket = socket;
        this.passwordCifra = passwordCifra;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            if (!handleAttestation()) return; 
            if (!handleAuthentication()) return; 

            processCommands();
        } catch (Exception e) {
            System.err.println("Erro na sessão do cliente: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private boolean handleAttestation() throws IOException, ClassNotFoundException {
        long nonce = new SecureRandom().nextLong();
        out.writeLong(nonce);
        out.flush();

        byte[] clientHash = (byte[]) in.readObject();
        if (AttestationManager.isValid(clientHash, nonce)) {
            out.writeObject(Protocol.OK_ATTEST);
            out.flush();
            return true;
        } else {
            out.writeObject(Protocol.NOKATTEST);
            out.flush();
            return false;
        }
    }

    private boolean handleAuthentication() throws IOException, ClassNotFoundException {
        String user = (String) in.readObject(); 
        String password = (String) in.readObject();
        try {
            String response = DataManager.authenticateOrRegister(user, password);
            if (response.equals(Protocol.OK_NEW_USER)) {
                out.writeObject("SEND-CERT");
                out.flush();
                byte[] certBytes = (byte[]) in.readObject();
                DataManager.saveUserCertificate(user, certBytes);
                out.writeObject(Protocol.OK); 
            } else {
                out.writeObject(response);
            }
            out.flush();
            if (response.equals(Protocol.WRONG_PWD)) return false;
            this.currentUser = user;
            return true;
        } catch (Exception e) {
            out.writeObject(Protocol.NOK);
            return false;
        }
    }

    private void processCommands() throws IOException, ClassNotFoundException {
        try {
            while (true) {
                Object input = in.readObject();
                if (input == null) break;
                
                String clientInput = (String) input;
                String[] tokens = clientInput.trim().split("\\s+");
                String command = tokens[0].toUpperCase();

                switch (command) {
                    case "GET_CERT": 
                        byte[] cert = DataManager.getUserCertificate(tokens[1]);
                        out.writeObject(cert != null ? cert : Protocol.NOUSER);
                        break;

                    
                    case "GET_KEY": 
                        if (tokens.length < 3) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            String houseGK = tokens[1];
                            String sec = tokens[2].toUpperCase();
                            
                            if (!DataManager.houseExists(houseGK)) {
                                out.writeObject(Protocol.NOHM);
                            } else if (!"EGLMPS".contains(sec)) {
                                out.writeObject(Protocol.NOK);
                            } else if (!DataManager.hasPermission(houseGK, currentUser, sec)) {
                                out.writeObject(Protocol.NOPERM);
                            } else {
                                byte[] wrappedKey = DataManager.getEncryptedSectionKey(houseGK, sec, currentUser);
                                out.writeObject(wrappedKey != null ? wrappedKey : Protocol.NOK);
                            }
                        }
                        break;

                    case Protocol.CREATE:
                        List<byte[]> keys = (List<byte[]>) in.readObject(); 
                        out.writeObject(DataManager.createHouse(tokens[1], currentUser, keys));
                        break;

                    case Protocol.ADD:
                        byte[] newKey = (byte[]) in.readObject();
                        out.writeObject(DataManager.addPermission(currentUser, tokens[1], tokens[2], tokens[3], newKey));
                        break;

                    case Protocol.EC:
                        if (tokens.length < 3) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            String houseEC = tokens[1].toLowerCase();
                            String deviceEC = tokens[2].toUpperCase();
                            String sectionEC = deviceEC.substring(0, 1).toUpperCase();

                           
                            if (!DataManager.houseExists(houseEC)) {
                                out.writeObject(Protocol.NOHM);
                            } else if (!DataManager.hasPermission(houseEC, currentUser, sectionEC)) {
                                out.writeObject(Protocol.NOPERM);
                            } else {
                                byte[] wrappedKey = DataManager.getEncryptedSectionKey(houseEC, sectionEC, currentUser);
                                out.writeObject(wrappedKey);
                                out.flush();

                                
                                String encryptedValue = (String) in.readObject();
                                String resEC = DataManager.updateDeviceState(houseEC, deviceEC, encryptedValue, currentUser);
                                out.writeObject(resEC);
                            }
                        }
                        break;

                    case Protocol.RD:
                        if (tokens.length < 3) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            
                            String resRD = DataManager.registerDevice(tokens[1], tokens[2], currentUser);
                            out.writeObject(resRD);
                        }
                        break;

                    case Protocol.RT:
                    case Protocol.RH:
                        handleFileCommand(tokens, command);
                        break;

                    default:
                        out.writeObject(Protocol.NOK);
                }
                out.flush();
            }
        } catch (EOFException e) {
            System.out.println("O cliente " + currentUser + " fechou a ligação.");
        }
    }

    private void handleFileCommand(String[] tokens, String type) throws IOException, ClassNotFoundException {
        String house = tokens[1].toLowerCase();
        String device = (type.equals(Protocol.RH) && tokens.length > 2) ? tokens[2] : null;

        File fileToSend = DataManager.getFileForCommand(currentUser, house, device, type);
        
        if (fileToSend == null || !fileToSend.exists()) {
            out.writeObject(Protocol.NODATA);
            out.flush();
        } else {
            try {
                
                out.writeObject(Protocol.OK);
                
            
                if (type.equals(Protocol.RT)) {
                    java.util.Map<String, byte[]> keysMap = new java.util.HashMap<>();
                    for (char s : "EGLMPS".toCharArray()) {
                        String sec = String.valueOf(s);
                        if (DataManager.hasPermission(house, currentUser, sec)) {
                            byte[] k = DataManager.getEncryptedSectionKey(house, sec, currentUser);
                            if (k != null) keysMap.put(sec, k);
                        }
                    }
                    out.writeObject(keysMap);
                } else {
                    String section = device.substring(0, 1).toUpperCase();
                    byte[] key = DataManager.getEncryptedSectionKey(house, section, currentUser);
                    out.writeObject(key != null ? key : new byte[0]);
                }

            
                byte[] fileBytes = Files.readAllBytes(fileToSend.toPath());
                out.writeObject(fileBytes); 
                out.flush();
                
            } catch (Exception e) {
            
                out.writeObject("Erro ao processar ficheiro: " + e.getMessage());
                out.flush();
            }
        }
    }

    private void closeConnection() {
        try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
    }
}