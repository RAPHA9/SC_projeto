package sperta.client;
import sperta.common.Protocol;

public class CommandHandler {

    
    public static boolean validateCommand(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toUpperCase();

        switch (cmd) {
            case Protocol.CREATE:
                if(tokens.length != 2){
                    System.out.println("Sintaxe correta: CREATE <hm>");
                    return false;
                }
                return true;
            case Protocol.ADD:
                if(tokens.length != 4){
                    System.out.println("Sintaxe correta: ADD <user> <hm> <s>");
                    return false;
                }
                return true;
            case Protocol.RD: 
                if(tokens.length != 3){
                    System.out.println("Sintaxe correta: RD <hm> <s>");
                    return false;
                }
                return true;
            case Protocol.EC:
                if(tokens.length != 4){
                    System.out.println("Sintaxe correta: EC <hm> <d> <int>");
                    return false;
                }
                return true;
            case Protocol.RT:
                if(tokens.length != 2){
                    System.out.println("Sintaxe correta: RT <hm>");
                    return false;
                }
                return true;
            case Protocol.RH:
                if(tokens.length != 3){
                    System.out.println("Sintaxe correta: RH <hm> <d>");
                    return false;
                }
                return true;
            default:
                System.out.println("Comando desconhecido");
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