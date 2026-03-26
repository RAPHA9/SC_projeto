package sperta.client;
import sperta.common.Protocol;

public class CommandHandler {

    
    public static boolean validateCommand(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toUpperCase();

        switch (cmd) {
            case Protocol.CREATE: 
                return tokens.length == 2;
            case Protocol.ADD:
                return tokens.length == 4;
            case Protocol.RD: 
                return tokens.length == 3;
            case Protocol.EC:
                return tokens.length == 4;
            case Protocol.RT:
                return tokens.length == 2;
            case Protocol.RH:
                return tokens.length == 3;
            default:
                System.out.println("Comando desconhecido: " + cmd);
                return false;
        }
    }

   
    public static void showHelp() {
        System.out.println("\nComandos disponíveis:");
        System.out.println("CREATE <hm>         # Criar casa <hm>");
        System.out.println("ADD <user> <hm> <s> # Adicionar utilizador à casa na seção <s>");
        System.out.println("RD <hm> <s>         # Registar um Dispositivo na casa/seção");
        System.out.println("EC <hm> <d> <int>   # Enviar valor de estado/temporização");
        System.out.println("RT <hm>             # Receber informação sobre último comando");
        System.out.println("RH <hm> <d>         # Receber o Histórico (log.csv)");
        System.out.println("HELP                # Mostrar este menu de ajuda");
        System.out.println("EXIT                # Sair da aplicação\n");
    }
}