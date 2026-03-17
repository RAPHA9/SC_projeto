
import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SpertaServer {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        
        int port = 22345;

        if(args.length > 0){
            port = Integer.parseInt(args[0]);
        }

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.print("Server started. Listening on PORT: "+ port);

            while(true){
                Socket socket = serverSocket.accept();
                System.out.println("Client connected with address: " + socket.getInetAddress());

                new Thread(()-> {
                    try {
                        handleClient(socket);
                    } catch (ClassNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }).start();;

            }
        } catch(IOException exception) {
            System.err.print("Server error: "+ exception);
        }
    }

    private static void handleClient(Socket socket) throws ClassNotFoundException {

        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            String user = (String) in.readObject();
            String password = (String) in.readObject();

            System.out.println("User: " + user);
            System.out.println("Password: " + password);

            out.writeObject("OK-USER");
            out.flush();

            socket.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        

       
    }
    
}
