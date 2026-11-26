package de.tigerjo.economySystem.database;

import de.tigerjo.economySystem.EconomySystem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Verwaltet die SQLite-Datenbankverbindung für Kontostände sowie
 * das asynchrone Schreiben von Transaktions-Logs in eine Textdatei.
 */
public class DatabaseManager {

    private final EconomySystem plugin;
    private Connection connection;
    private final File logFile;

    /**
     * Repräsentiert einen Eintrag im Top-Ranking inklusive UUID und Kontostand.
     */
    public record TopBalanceEntry(UUID uuid, double balance) {}

    /**
     * Erstellt den DatabaseManager, initialisiert die SQLite-Datenbank und
     * legt bei Bedarf die Log-Datei {@code transactions.log} an.
     *
     * @param plugin Hauptinstanz des EconomySystem-Plugins
     */
    public DatabaseManager(EconomySystem plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "transactions.log");
        initDatabase();
        initLogFile();
    }

    /**
     * Stellt die Verbindung zur SQLite-Datenbank her und erstellt die
     * {@code economy}-Tabelle, falls sie noch nicht existiert.
     */
    private void initDatabase() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "economy.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS economy (uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE NOT NULL DEFAULT 0.0);");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Initialisieren der Datenbank: " + e.getMessage());
        }
    }

    /**
     * Erstellt die Datei {@code transactions.log} im Plugin-Ordner, falls sie fehlt.
     */
    private void initLogFile() {
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Konnte transactions.log nicht erstellen.");
            }
        }
    }

    /**
     * Hängt eine Transaktionsmeldung mit Zeitstempel asynchron an {@code transactions.log} an.
     *
     * @param logMessage Zu protokollierende Nachricht
     */
    public synchronized void logTransaction(String logMessage) {
        CompletableFuture.runAsync(() -> {
            // Try-with-resources schließt FileWriter und PrintWriter nach dem Schreiben automatisch
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                pw.println("[" + timestamp + "] " + logMessage);
            } catch (IOException e) {
                plugin.getLogger().warning("Fehler beim Schreiben des Transaktions-Logs: " + e.getMessage());
            }
        });
    }

    /**
     * Ruft den aktuellen Kontostand eines Spielers asynchron aus der Datenbank ab.
     * Gibt {@code 0.0} zurück, falls kein Eintrag existiert oder ein Fehler auftritt.
     *
     * @param uuid UUID des Spielers
     * @return CompletableFuture mit dem Kontostand
     */
    public CompletableFuture<Double> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM economy WHERE uuid = ?;")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0.0;
        });
    }

    /**
     * Speichert oder aktualisiert (Upsert) den Kontostand eines Spielers asynchron.
     *
     * @param uuid    UUID des Spielers
     * @param balance Neuer Kontostand
     * @return CompletableFuture nach Abschluss des Schreibvorgangs
     */
    public CompletableFuture<Void> setBalance(UUID uuid, double balance) {
        return CompletableFuture.runAsync(() -> {
            // Legt den Spieler an oder überschreibt den Kontostand bei vorhandener UUID
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO economy (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = ?;")) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, balance);
                ps.setDouble(3, balance);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Lädt die Einträge mit dem höchsten Kontostand absteigend sortiert.
     *
     * @param limit Maximale Anzahl an Einträgen
     * @return CompletableFuture mit der sortierten Liste der Top-Kontostände
     */
    public CompletableFuture<List<TopBalanceEntry>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopBalanceEntry> topList = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT uuid, balance FROM economy ORDER BY balance DESC LIMIT ?;")) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    double balance = rs.getDouble("balance");
                    topList.add(new TopBalanceEntry(uuid, balance));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return topList;
        });
    }

    /**
     * Schließt die aktive SQLite-Verbindung sauber (z. B. beim Deaktivieren des Plugins).
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}