package de.tigerjo.economySystem;

import de.tigerjo.economySystem.database.DatabaseManager;
import de.tigerjo.economySystem.vault.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hauptklasse des EconomySystem-Plugins für Paper.
 * Initialisiert Konfiguration, SQLite-Datenbank, den {@code /money}-Befehl
 * sowie die Vault-Economy-Integration.
 */
public class EconomySystem extends JavaPlugin {

    private DatabaseManager databaseManager;

    /**
     * Wird beim Aktivieren des Plugins aufgerufen und richtet Datenbank,
     * Commands sowie Vault ein.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.databaseManager = new DatabaseManager(this);

        // Registriert Executor und Tab-Completer gemeinsam über dieselbe Command-Instanz
        PluginCommand moneyCmd = getCommand("money");
        if (moneyCmd != null) {
            MoneyCommand executor = new MoneyCommand(this);
            moneyCmd.setExecutor(executor);
            moneyCmd.setTabCompleter(executor);
        }

        setupVault();

        getLogger().info("EconomySystem erfolgreich aktiviert!");
    }

    /**
     * Registriert den {@link VaultEconomyProvider} beim Vault-Service,
     * sofern das Vault-Plugin auf dem Server vorhanden ist.
     */
    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServicesManager().register(Economy.class, new VaultEconomyProvider(this), this, ServicePriority.Highest);
            getLogger().info("Erfolgreich in Vault registriert!");
        } else {
            getLogger().warning("Vault nicht gefunden. Vault-Integration übersprungen.");
        }
    }

    /**
     * Schließt beim Deaktivieren des Plugins sauber die Datenbankverbindung.
     */
    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("EconomySystem deaktiviert.");
    }

    /**
     * Gibt den aktiven {@link DatabaseManager} für Datenbank- und Log-Zugriffe zurück.
     *
     * @return Instanz des {@link DatabaseManager}
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}