
import java.io.IOException;
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

                Thread newThread = new Thread(new ClientHandler(socket));
                newThread.start();

            }
        } catch(IOException exception) {
            System.err.print("Server error: "+ exception);
        }
    }
    
}
