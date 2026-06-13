package be.panchito.pointRush.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Stuurt spelers tussen servers achter een Velocity/BungeeCord proxy via het standaard
 * {@code BungeeCord} plugin-messaging kanaal (subcommando {@code Connect}). Velocity ondersteunt dit
 * out-of-the-box zolang {@code bungee-plugin-message-channel = true} in {@code velocity.toml} staat.
 *
 * <p>Plugin-messages reizen mee op de verbinding van een online speler: elke speler draagt dus zijn
 * eigen {@code Connect}-bericht. Daarom is er geen aparte proxy-plugin nodig om deelnemers naar de
 * events-server (en terug) te sturen.
 */
public final class ProxyTransport {

    /** Het legacy kanaalnaam dat zowel BungeeCord als Velocity begrijpen. */
    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final NetworkSettings settings;
    private volatile boolean registered;

    public ProxyTransport(Plugin plugin, NetworkSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Registreert het uitgaande plugin-kanaal. Idempotent. */
    public void register() {
        Messenger messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        registered = true;
    }

    /** Verwijdert het uitgaande plugin-kanaal (bij disable). */
    public void unregister() {
        if (!registered) {
            return;
        }
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        registered = false;
    }

    /**
     * Stuurt {@code player} naar de server met naam {@code serverName} op de proxy. Doet niets en geeft
     * {@code false} terug als het kanaal niet geregistreerd is, de speler offline is of de naam leeg is.
     */
    public boolean send(Player player, String serverName) {
        if (player == null || !player.isOnline() || serverName == null || serverName.isBlank()) {
            return false;
        }

        // Eigen transfer-commando (bv. een netwerk met /move i.p.v. het standaard Connect-bericht).
        String command = settings.getTransferCommand();
        if (command != null && !command.isBlank()) {
            String full = command.replace("{server}", serverName).trim();
            if (full.startsWith("/")) {
                full = full.substring(1);
            }
            return player.performCommand(full);
        }

        if (!registered) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Kon proxy-transfer niet opbouwen voor " + player.getName(), ex);
            return false;
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        return true;
    }

    /** Stuurt de speler naar de geconfigureerde events-server. */
    public boolean sendToEvents(Player player) {
        return send(player, settings.getEventsServer());
    }

    /** Stuurt de speler terug naar de geconfigureerde survival-server. */
    public boolean sendToSurvival(Player player) {
        return send(player, settings.getSurvivalServer());
    }

    public boolean isRegistered() {
        return registered;
    }
}
