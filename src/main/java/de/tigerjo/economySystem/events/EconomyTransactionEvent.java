package de.tigerjo.economySystem.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Custom-Bukkit-Event, das bei Kontostandsänderungen im EconomySystem
 * ausgelöst wird (z. B. Überweisungen sowie Admin-Aktionen).
 */
public class EconomyTransactionEvent extends Event {

    // Von Bukkit benötigte statische Handler-Liste für dieses Event
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerUuid;
    private final double amount;
    private final TransactionType type;

    /**
     * Art der durchgeführten Transaktion.
     */
    public enum TransactionType {
        PAY, ADD, REMOVE, SET
    }

    /**
     * Erstellt ein neues {@code EconomyTransactionEvent}.
     *
     * @param playerUuid UUID des betroffenen Spielers
     * @param amount     Beteiligter Betrag
     * @param type       Art der Transaktion ({@link TransactionType})
     */
    public EconomyTransactionEvent(UUID playerUuid, double amount, TransactionType type) {
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.type = type;
    }

    /**
     * Gibt die UUID des betroffenen Spielers zurück.
     *
     * @return UUID des Spielers
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Gibt den bei der Transaktion verwendeten Betrag zurück.
     *
     * @return Betrag
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Gibt den Transaktionstyp zurück.
     *
     * @return {@link TransactionType} der Transaktion
     */
    public TransactionType getType() {
        return type;
    }

    /**
     * Gibt die Handler-Liste dieser Event-Instanz zurück.
     *
     * @return {@link HandlerList}
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Statische Methode, die Bukkit zum Registrieren von Listenern für dieses Event nutzt.
     *
     * @return {@link HandlerList}
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}