import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String currentUser;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            
            if (!handleAttestation()) {
                return; 
            }

           
            if (!handleAuthentication()) {
                return; 
            }

            
            processCommands();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro na sessão do cliente: " + e.getMessage());
            e.printStackTrace();
        }catch(Exception e){
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private boolean handleAttestation() throws IOException, ClassNotFoundException {
        
        long clientSize = (long) in.readObject();
        
       
        if (AttestationManager.isValid(clientSize)) {
            out.writeObject(Protocol.ATTESTATION_OK);
            out.flush();
            return true;
        } else {
            out.writeObject(Protocol.ATTESTATION_FAILED);
            out.flush();
            return false;
        }
    }

    private boolean handleAuthentication() throws IOException, ClassNotFoundException {
        String user = (String) in.readObject(); 
        String password = (String) in.readObject();
 
        String response = DataManager.authenticateOrRegister(user, password);
        out.writeObject(response);
        out.flush();

        if (response.equals(Protocol.WRONG_PWD)) {
            return false;
        }

        this.currentUser = user;
        return true;
    }

    private void processCommands() throws IOException, ClassNotFoundException {
        try{
            while (true) {
                String clientInput = (String) in.readObject();
                if (clientInput == null) break;

                String[] tokens = clientInput.trim().split("\\s+");
                String command = tokens[0].toUpperCase();

                switch (command) {

                    case Protocol.RD:
                        if (tokens.length < 3) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            String house = tokens[1];
                            String section = tokens[2].toUpperCase();

                            if (!DataManager.houseExists(house)) {
                                out.writeObject(Protocol.NOHM); 
                            } 
                          
                            else if (!DataManager.isOwner(house, currentUser)) {
                                out.writeObject(Protocol.NOPERM); 
                            } 
                         
                            else {
                                String result = DataManager.registerDevice(house, section);
                                out.writeObject(result);
                            }
                        }
                        break;

                    case Protocol.ADD:
                        if (tokens.length < 4) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            String targetUser = tokens[1];
                            String house = tokens[2];
                            String section = tokens[3];
                            
                            String result = DataManager.addPermission(currentUser, targetUser, house, section);
                            out.writeObject(result);
                        }
                        break;
                    
                    case Protocol.CREATE:
                        if (tokens.length < 2) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            
                            String result = DataManager.createHouse(tokens[1], currentUser);
                            out.writeObject(result);
                        }
                        break;

                    case Protocol.EC:
                        if (tokens.length < 4) {
                            out.writeObject(Protocol.NOK);
                        } else {
                            try {
                                int value = Integer.parseInt(tokens[3]);
                                if(value < 0 || value > 600){
                                   out.writeObject(Protocol.NOK);
                                }else{
                                    String result = DataManager.updateDeviceState(tokens[1], tokens[2], value, currentUser);
                                    out.writeObject(result);
                                }
                            } catch (NumberFormatException e) {
                                out.writeObject(Protocol.NOK);
                            }
                        }
                        break;

                    case Protocol.RT: 
                        handleFileCommand(tokens, Protocol.RT);
                        break;

                    case Protocol.RH:
                        handleFileCommand(tokens, Protocol.RH);
                        break;
                }
                out.flush();
            }
        }catch(EOFException e){
            System.out.println("O cliente fechou a ligação.");
        }
    }

    private void handleFileCommand(String[] tokens, String type) throws IOException {
            String house = tokens[1];
            String device = (type.equals(Protocol.RH)) ? tokens[2] : null;

            
            if (!DataManager.houseExists(house)) {
                out.writeObject(Protocol.NOHM);
                return;
            }

            String section = (device != null) ? device.substring(0, 1).toUpperCase() : "all";

            
            if (!DataManager.hasPermission(currentUser, house, section)) {
                out.writeObject(Protocol.NOPERM);
                return;
            }

           
            File fileToSend = DataManager.getFileForCommand(currentUser, house, device, type);

            if (fileToSend == null || !fileToSend.exists()) {
               
                out.writeObject(Protocol.NODATA); 
            } else {
                 
                out.writeObject(Protocol.OK);
                out.writeObject(fileToSend.length());
                out.flush();

               
                try (FileInputStream fis = new FileInputStream(fileToSend)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                out.flush();
            }
        }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}