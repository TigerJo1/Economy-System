package de.tigerjo.economySystem.vault;

import de.tigerjo.economySystem.EconomySystem;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementiert die Vault-{@link Economy}-Schnittstelle und verbindet
 * externe Plugins über den {@link de.tigerjo.economySystem.database.DatabaseManager}
 * mit der SQLite-Datenbank des EconomySystems.
 */
public class VaultEconomyProvider implements Economy {

    private final EconomySystem plugin;
    private final DecimalFormat decimalFormat;

    /**
     * Erstellt den Vault-Provider und konfiguriert die Zahlenformatierung
     * mit deutschem Trennzeichen-Stil.
     *
     * @param plugin Hauptinstanz des EconomySystem-Plugins
     */
    public VaultEconomyProvider(EconomySystem plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        this.decimalFormat = new DecimalFormat("#,##0.##", symbols);
    }

    /**
     * Ruft den aktuellen Kontostand synchron über den asynchronen DatabaseManager ab.
     *
     * @param uuid UUID des Spielers
     * @return Aktueller Kontostand oder {@code 0.0} im Fehlerfall
     */
    private double fetchBalanceSync(UUID uuid) {
        try {
            Double val = plugin.getDatabaseManager().getBalance(uuid).join();
            return val != null ? val : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Speichert den neuen Kontostand synchron über den DatabaseManager.
     *
     * @param uuid   UUID des Spielers
     * @param amount Neuer Kontostand
     */
    private void saveBalanceSync(UUID uuid, double amount) {
        try {
            plugin.getDatabaseManager().setBalance(uuid, amount).join();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isEnabled() {
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "EconomySystem";
    }

    @Override
    public boolean hasBankSupport() {
        // Bank-Features werden in diesem Plugin nicht bereitgestellt
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return decimalFormat.format(amount) + "$";
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    @Override
    public boolean hasAccount(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && player.hasPlayedBefore();
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return 0.0;
        }
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) {
            return 0.0;
        }
        return fetchBalanceSync(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        if (playerName == null || playerName.isBlank()) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Ungültiger Spielername.");
        }
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Spieler ist null.");
        }
        // Ungültige Zahlenwerte (NaN, Infinity) und negative Beträge abfangen
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0) {
            double current = fetchBalanceSync(player.getUniqueId());
            return new EconomyResponse(0.0, current, EconomyResponse.ResponseType.FAILURE, "Betrag darf nicht negativ sein.");
        }

        double current = fetchBalanceSync(player.getUniqueId());
        if (current < amount) {
            return new EconomyResponse(0.0, current, EconomyResponse.ResponseType.FAILURE, "Nicht genügend Guthaben.");
        }

        double updated = current - amount;
        saveBalanceSync(player.getUniqueId(), updated);
        return new EconomyResponse(amount, updated, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (playerName == null || playerName.isBlank()) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Ungültiger Spielername.");
        }
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Spieler ist null.");
        }
        // Ungültige Zahlenwerte (NaN, Infinity) und negative Beträge abfangen
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0.0) {
            double current = fetchBalanceSync(player.getUniqueId());
            return new EconomyResponse(0.0, current, EconomyResponse.ResponseType.FAILURE, "Betrag darf nicht negativ sein.");
        }

        double current = fetchBalanceSync(player.getUniqueId());
        double updated = current + amount;
        saveBalanceSync(player.getUniqueId(), updated);
        return new EconomyResponse(amount, updated, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken werden nicht unterstützt.");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        // Legt den Spieler-Eintrag in der Datenbank mit Startguthaben 0.0 an
        saveBalanceSync(player.getUniqueId(), 0.0);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}