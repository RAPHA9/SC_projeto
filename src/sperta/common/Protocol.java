
public class Protocol {

    // --- Mensagens de Atestação ---
    public static final String ATTESTATION_OK = "ATTESTATION OK";
    public static final String ATTESTATION_FAILED = "ATTESTATION FAILED";

    // --- Mensagens de Autenticação ---
    public static final String WRONG_PWD = "WRONG-PWD";
    public static final String OK_NEW_USER = "OK-NEW-USER";
    public static final String OK_USER = "OK-USER"; 

    // --- Comandos do Cliente ---
    public static final String CREATE = "CREATE"; 
    public static final String ADD = "ADD"; 
    public static final String RD = "RD";
    public static final String EC = "EC"; 
    public static final String RT = "RT";
    public static final String RH = "RH";

    // --- Respostas de Sucesso e Erro Geral ---
    public static final String OK = "OK"; 
    public static final String NOK = "NOK"; 
    public static final String NOPERM = "NOPERM"; 
    public static final String NOHM = "NOHM"; 
    public static final String NOUSER = "NOUSER";
    public static final String NOD = "NOD"; 
    public static final String NODATA = "NODATA"; 

    // --- Configurações por Omissão ---
    public static final int DEFAULT_PORT = 22345; 
}