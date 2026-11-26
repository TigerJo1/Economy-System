package de.tigerjo.economySystem;

import de.tigerjo.economySystem.database.DatabaseManager;
import de.tigerjo.economySystem.events.EconomyTransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Verarbeitet den {@code /money}-Befehl inklusive Spieler-Überweisungen,
 * Bestätigung für Großüberweisungen, Top-10-Ranking, Admin-Befehlen
 * sowie Tab-Completion.
 */
public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final EconomySystem plugin;
    private final DecimalFormat decimalFormat;
    // Speichert offene Großüberweisungen pro Spieler-UUID (mit Ablaufzeit)
    private final Map<UUID, PendingTransaction> pendingConfirmations = new HashMap<>();
    // Ab diesem Betrag muss eine Überweisung mit /money confirm bestätigt werden
    private static final double CONFIRM_THRESHOLD = 10000.0;

    private record PendingTransaction(UUID targetUuid, String targetName, double amount, long expiresAt) {}

    private enum Mode {
        GIVE, TAKE, SET
    }

    /**
     * Erstellt den Command-Executor und Tab-Completer für {@code /money}.
     *
     * @param plugin Hauptinstanz des EconomySystem-Plugins
     */
    public MoneyCommand(EconomySystem plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        this.decimalFormat = new DecimalFormat("#,##0.##", symbols);
    }

    /**
     * Formatiert Geldbeträge einheitlich ohne wissenschaftliche Notation.
     *
     * @param amount Betrag
     * @return Formatierter Betrag als Zeichenkette
     */
    private String formatAmount(double amount) {
        return decimalFormat.format(amount);
    }

    /**
     * Wertet {@code /money} und dessen Unterbefehle ({@code top}, {@code confirm},
     * {@code pay} sowie Admin-Befehle {@code give}, {@code take}, {@code set}) aus.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        // Ohne Argumente wird der eigene Kontostand angezeigt
        if (args.length == 0) {
            plugin.getDatabaseManager().getBalance(player.getUniqueId()).thenAccept(balance ->
                    player.sendMessage(ChatColor.GOLD + "Kontostand: " + ChatColor.GREEN + formatAmount(balance) + "$")
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top" -> handleTop(player);
            case "confirm" -> handleConfirm(player);
            case "pay" -> {
                if (args.length < 3) {
                    sendUsage(player);
                    return true;
                }
                double amount = parsePositiveAmount(player, args[2]);
                if (amount > 0) {
                    handlePayRequest(player, args[1], amount);
                }
            }
            case "give" -> {
                if (!player.hasPermission("economysystem.admin")) {
                    player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                    return true;
                }
                if (args.length < 3) {
                    sendUsage(player);
                    return true;
                }
                double amount = parsePositiveAmount(player, args[2]);
                if (amount > 0) {
                    handleAdminModify(player, args[1], amount, Mode.GIVE);
                }
            }
            case "take" -> {
                if (!player.hasPermission("economysystem.admin")) {
                    player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                    return true;
                }
                if (args.length < 3) {
                    sendUsage(player);
                    return true;
                }
                double amount = parsePositiveAmount(player, args[2]);
                if (amount > 0) {
                    handleAdminModify(player, args[1], amount, Mode.TAKE);
                }
            }
            case "set" -> {
                if (!player.hasPermission("economysystem.admin")) {
                    player.sendMessage(ChatColor.RED + "Keine Berechtigung.");
                    return true;
                }
                if (args.length < 3) {
                    sendUsage(player);
                    return true;
                }
                double amount = parseNonNegativeAmount(player, args[2]);
                if (amount >= 0) {
                    handleAdminModify(player, args[1], amount, Mode.SET);
                }
            }
            default -> sendUsage(player);
        }

        return true;
    }

    /**
     * Lädt die Top-10-Kontostände und gibt sie formatiert im Chat aus.
     *
     * @param player Spieler, der die Liste angefordert hat
     */
    private void handleTop(Player player) {
        plugin.getDatabaseManager().getTopBalances(10).thenAccept(topList -> {
            player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Top 10 Reichste Spieler" + ChatColor.GOLD + " ===");
            int rank = 1;
            for (DatabaseManager.TopBalanceEntry entry : topList) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid());
                String name = op.getName() != null ? op.getName() : "Unbekannt";
                player.sendMessage(ChatColor.GOLD + "#" + rank + " " + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.GREEN + formatAmount(entry.balance()) + "$");
                rank++;
            }
        });
    }

    /**
     * Prüft Voraussetzungen für eine Überweisung und fordert bei Beträgen ab
     * {@link #CONFIRM_THRESHOLD} eine Bestätigung über {@code /money confirm} an.
     *
     * @param senderPlayer Überweisender Spieler
     * @param targetName   Name des Zielspielers
     * @param amount       Überweisungsbetrag
     */
    private void handlePayRequest(Player senderPlayer, String targetName, double amount) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null) {
            senderPlayer.sendMessage(ChatColor.RED + "Spieler ist nicht online!");
            return;
        }

        if (target.getUniqueId().equals(senderPlayer.getUniqueId())) {
            senderPlayer.sendMessage(ChatColor.RED + "Du kannst dir selbst kein Geld senden.");
            return;
        }

        Player finalTarget = target;
        plugin.getDatabaseManager().getBalance(senderPlayer.getUniqueId()).thenAccept(senderBalance -> {
            if (senderBalance < amount) {
                senderPlayer.sendMessage(ChatColor.RED + "Du hast nicht genügend Guthaben.");
                return;
            }

            // Großüberweisungen zwischenspeichern und für 30 Sekunden zur Bestätigung freigeben
            if (amount >= CONFIRM_THRESHOLD) {
                long expiresAt = System.currentTimeMillis() + 30_000L;
                pendingConfirmations.put(
                        senderPlayer.getUniqueId(),
                        new PendingTransaction(finalTarget.getUniqueId(), finalTarget.getName(), amount, expiresAt)
                );
                senderPlayer.sendMessage(ChatColor.YELLOW + "Großüberweisung über " + ChatColor.GOLD + formatAmount(amount) + "$"
                        + ChatColor.YELLOW + " an " + ChatColor.WHITE + finalTarget.getName()
                        + ChatColor.YELLOW + ". Bestätige mit " + ChatColor.GOLD + "/money confirm" + ChatColor.YELLOW + " (30s).");
                return;
            }

            executeTransfer(senderPlayer, finalTarget.getUniqueId(), finalTarget.getName(), amount);
        });
    }

    /**
     * Führt eine zuvor vorgemerkte Großüberweisung aus, sofern sie noch nicht abgelaufen ist.
     *
     * @param player Bestätigender Spieler
     */
    private void handleConfirm(Player player) {
        PendingTransaction pending = pendingConfirmations.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(ChatColor.RED + "Es liegt keine offene Überweisung zur Bestätigung vor.");
            return;
        }

        if (System.currentTimeMillis() > pending.expiresAt()) {
            player.sendMessage(ChatColor.RED + "Die Bestätigungszeit ist abgelaufen.");
            return;
        }

        plugin.getDatabaseManager().getBalance(player.getUniqueId()).thenAccept(senderBalance -> {
            if (senderBalance < pending.amount()) {
                player.sendMessage(ChatColor.RED + "Du hast inzwischen nicht mehr genügend Guthaben.");
                return;
            }
            executeTransfer(player, pending.targetUuid(), pending.targetName(), pending.amount());
        });
    }

    /**
     * Bucht den Betrag vom Absender ab, schreibt ihn dem Empfänger gut, feuert das
     * {@link EconomyTransactionEvent} auf dem Main-Thread und protokolliert die Buchung.
     *
     * @param senderPlayer Absender
     * @param targetUuid   UUID des Empfängers
     * @param targetName   Name des Empfängers
     * @param amount       Betrag
     */
    private void executeTransfer(Player senderPlayer, UUID targetUuid, String targetName, double amount) {
        plugin.getDatabaseManager().getBalance(senderPlayer.getUniqueId()).thenCombine(
                plugin.getDatabaseManager().getBalance(targetUuid),
                (senderBal, targetBal) -> {
                    if (senderBal < amount) {
                        return false;
                    }
                    double newSenderBal = senderBal - amount;
                    double newTargetBal = targetBal + amount;
                    plugin.getDatabaseManager().setBalance(senderPlayer.getUniqueId(), newSenderBal).join();
                    plugin.getDatabaseManager().setBalance(targetUuid, newTargetBal).join();
                    return true;
                }
        ).thenAccept(success -> {
            if (!success) {
                senderPlayer.sendMessage(ChatColor.RED + "Überweisung fehlgeschlagen (unzureichendes Guthaben).");
                return;
            }

            // Bukkit-Events müssen auf dem Server-Main-Thread ausgelöst werden
            Bukkit.getScheduler().runTask(plugin, () -> {
                EconomyTransactionEvent event = new EconomyTransactionEvent(
                        targetUuid,
                        amount,
                        EconomyTransactionEvent.TransactionType.PAY
                );
                Bukkit.getPluginManager().callEvent(event);
            });

            plugin.getDatabaseManager().logTransaction(
                    "PAY: " + senderPlayer.getName() + " (" + senderPlayer.getUniqueId() + ") -> "
                            + targetName + " (" + targetUuid + ") | Betrag: " + amount
            );

            senderPlayer.sendMessage(ChatColor.GREEN + "Du hast " + ChatColor.GOLD + formatAmount(amount) + "$"
                    + ChatColor.GREEN + " an " + ChatColor.WHITE + targetName + ChatColor.GREEN + " gesendet.");

            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null) {
                onlineTarget.sendMessage(ChatColor.GREEN + "Du hast " + ChatColor.GOLD + formatAmount(amount) + "$"
                        + ChatColor.GREEN + " von " + ChatColor.WHITE + senderPlayer.getName() + ChatColor.GREEN + " erhalten.");
            }
        });
    }

    /**
     * Passt den Kontostand eines Zielspielers per Admin-Befehl ({@code give}, {@code take}, {@code set}) an.
     *
     * @param admin      Ausführender Admin
     * @param targetName Name des Zielspielers
     * @param amount     Betrag
     * @param mode       Art der Kontostandsänderung
     */
    private void handleAdminModify(Player admin, String targetName, double amount, Mode mode) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();
        String resolvedName = target.getName() != null ? target.getName() : targetName;

        plugin.getDatabaseManager().getBalance(targetUuid).thenAccept(currentBal -> {
            double updatedBal = switch (mode) {
                case GIVE -> currentBal + amount;
                case TAKE -> Math.max(0.0, currentBal - amount);
                case SET -> amount;
            };

            plugin.getDatabaseManager().setBalance(targetUuid, updatedBal).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    EconomyTransactionEvent.TransactionType txType = switch (mode) {
                        case GIVE, TAKE, SET -> EconomyTransactionEvent.TransactionType.SET;
                    };
                    EconomyTransactionEvent event = new EconomyTransactionEvent(
                            targetUuid,
                            amount,
                            txType
                    );
                    Bukkit.getPluginManager().callEvent(event);
                });

                plugin.getDatabaseManager().logTransaction(
                        "ADMIN_" + mode.name() + ": " + admin.getName() + " -> "
                                + resolvedName + " (" + targetUuid + ") | Betrag: " + amount + " | Neu: " + updatedBal
                );

                admin.sendMessage(ChatColor.GREEN + "Neuer Kontostand von " + ChatColor.WHITE + resolvedName
                        + ChatColor.GREEN + ": " + ChatColor.GOLD + formatAmount(updatedBal) + "$");
            });
        });
    }

    /**
     * Parst einen Betrag größer als {@code 0} und akzeptiert sowohl Komma als auch Punkt als Dezimaltrennzeichen.
     *
     * @param player Spieler für Fehlermeldungen
     * @param raw    Rohe Eingabe
     * @return Geparster Betrag oder {@code -1} bei ungültiger Eingabe
     */
    private double parsePositiveAmount(Player player, String raw) {
        try {
            double val = Double.parseDouble(raw.replace(",", "."));
            if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0) {
                player.sendMessage(ChatColor.RED + "Der Betrag muss größer als 0 sein.");
                return -1;
            }
            return val;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Ungültiger Betrag: " + raw);
            return -1;
        }
    }

    /**
     * Parst einen Betrag größer oder gleich {@code 0} und akzeptiert Komma sowie Punkt.
     *
     * @param player Spieler für Fehlermeldungen
     * @param raw    Rohe Eingabe
     * @return Geparster Betrag oder {@code -1} bei ungültiger Eingabe
     */
    private double parseNonNegativeAmount(Player player, String raw) {
        try {
            double val = Double.parseDouble(raw.replace(",", "."));
            if (Double.isNaN(val) || Double.isInfinite(val) || val < 0) {
                player.sendMessage(ChatColor.RED + "Der Betrag darf nicht negativ sein.");
                return -1;
            }
            return val;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Ungültiger Betrag: " + raw);
            return -1;
        }
    }

    /**
     * Sendet eine Übersicht aller verfügbaren {@code /money}-Befehle an den Spieler.
     *
     * @param player Empfänger der Hilfe-Nachricht
     */
    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Money Befehle ===");
        player.sendMessage(ChatColor.YELLOW + "/money" + ChatColor.GRAY + " - Zeigt deinen Kontostand");
        player.sendMessage(ChatColor.YELLOW + "/money pay <Spieler> <Betrag>" + ChatColor.GRAY + " - Sendet Geld");
        player.sendMessage(ChatColor.YELLOW + "/money confirm" + ChatColor.GRAY + " - Bestätigt eine Großüberweisung");
        player.sendMessage(ChatColor.YELLOW + "/money top" + ChatColor.GRAY + " - Zeigt die Top 10");
        if (player.hasPermission("economysystem.admin")) {
            player.sendMessage(ChatColor.RED + "/money give|take|set <Spieler> <Betrag>");
        }
    }

    /**
     * Liefert passende Tab-Vorschläge für Unterbefehle und Online-Spieler.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("pay");
            completions.add("confirm");
            completions.add("top");
            if (sender.hasPermission("economysystem.admin")) {
                completions.add("give");
                completions.add("take");
                completions.add("set");
            }
        } else if (args.length == 2 && List.of("pay", "give", "take", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }
}