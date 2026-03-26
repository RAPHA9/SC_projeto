package sperta.model;
import java.io.Serializable;
import java.util.*;

/**
 * Representa uma casa inteligente no sistema Sperta.
 */
public class Casa implements Serializable {
    private String name;
    private String ownerId;

    private Map<String, String> permissions;
    private List<Dispositivo> devices;

    public Casa(String name, String ownerId) {
        this.name = name;
        this.ownerId = ownerId;
        this.permissions = new HashMap<>();
        this.devices = new ArrayList<>();
    }


    /**
     * Verifica se um utilizador é o dono da casa.
     */
    public boolean isOwner(String userId) {
        return ownerId.equals(userId);
    }

    /**
     * Verifica se um utilizador tem permissão para uma secção específica.
     */
    public boolean hasPermission(String userId, String section) {
        if (isOwner(userId)) return true;

        String userPerms = permissions.get(userId);
        if (userPerms == null) return false;

        return userPerms.equals("all") || userPerms.contains(section.toUpperCase());
    }

    /**
     * Adiciona ou atualiza as permissões de um utilizador.
     */
    public void addPermission(String userId, String section) {
        if (section.equalsIgnoreCase("all")) {
            permissions.put(userId, "all");
        } else {
            String existing = permissions.getOrDefault(userId, "");
            if (!existing.contains(section.toUpperCase())) {
                String newVal = existing.isEmpty() ? section : existing + "," + section;
                permissions.put(userId, newVal);
            }
        }
    }

    // --- Getters e Setters ---
    public String getName() { return name; }
    public String getOwnerId() { return ownerId; }
    public Map<String, String> getPermissions() { return permissions; }
    public List<Dispositivo> getDevices() { return devices; }

    public void addDevice(Dispositivo d) {
        this.devices.add(d);
    }
}