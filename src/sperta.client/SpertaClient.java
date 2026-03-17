import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class SpertaClient {

    public static void main(String[] args) throws UnknownHostException {

        if (args.length < 3) {
            System.out.println("Usage: SpertaClient <serverAddress> <user-id> <password>");
            return;
        }
    
        InetAddress ipAddress;
        String address = args[0];
        String user = args[1];
        String password = args[2];
        String ipAddressString;
        int port = 22345;

        if(address.contains(":")){
            String[] parts = address.split(":");
            ipAddressString = parts[0];
            port = Integer.parseInt(parts[1]);
            ipAddress = InetAddress.getByName(ipAddressString);
        }


        
        try {
            Socket socket = new Socket();
            socket.bind(new InetSocketAddress(ipAddress, port));
            socket.connect(socket.getLocalSocketAddress());


            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            
            
            
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
}