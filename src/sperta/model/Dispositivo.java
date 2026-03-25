import java.io.Serializable;

/**
 * Representa um dispositivo inteligente (smart thing) no sistema Sperta.
 * Implementa Serializable para permitir o envio do objeto via Socket, se necessário.
 */
public class Dispositivo implements Serializable {
    private String id;      // Ex: "M1", "L2", "G1" 
    private String section; // "M", "L", "P", "G", "S", "E" 
    private int state;      // 0 (off), 1 (on), 1-600 (timer) 

    public Dispositivo(String id, int state) {
        this.id = id;
        this.section = String.valueOf(id.charAt(0)).toUpperCase();
        this.state = state;
    }

    // --- Getters ---
    public String getId() { return id; }
    
    public String getSection() { return section; }
    
    public int getState() { return state; }

    /**
     * Atualiza o estado do dispositivo validando os valores permitidos.
     * @param state 0 para desligar, 1 para ligar, ou 1..600 para temporização. 
     */
    public void setState(int state) {
        if (state >= 0 && state <= 600) { 
            this.state = state;
        }
    }

    @Override
    public String toString() {
        return id + ":" + state;
    }
}