
public class CommandHandler {

    
    public static boolean validateCommand(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toUpperCase();

        switch (cmd) {
            case Protocol.CREATE: // CREATE <hm>
                return tokens.length == 2;
            case Protocol.ADD:    // ADD <user1> <hm> <s>
                return tokens.length == 4;
            case Protocol.RD:     // RD <hm> <s>
                return tokens.length == 3;
            case Protocol.EC:     // EC <hm> <d> <int>
                return tokens.length == 4;
            case Protocol.RT:     // RT <hm>
                return tokens.length == 2;
            case Protocol.RH:     // RH <hm> <d>
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
        System.out.println("HELP                # Mostrar este menu");
        System.out.println("EXIT                # Sair da aplicação\n");
    }
}