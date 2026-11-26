# EconomySystem

Ein eigenes Economy-Plugin für **Paper (1.21.4)** auf Basis von **Java 21** und **Gradle**. Es speichert Kontostände in einer lokalen **SQLite-Datenbank**, protokolliert Transaktionen in einer Log-Datei, stellt ein **Custom Event** für andere Plugins bereit und bindet sich über **Vault** in bestehende Server-Systeme ein.

## Funktionsweise im Detail

### 1. Einstiegspunkt & Lifecycle (`EconomySystem.java`)
- Beim Aktivieren des Plugins (`onEnable`) wird zunächst die Standardkonfiguration (`config.yml`) angelegt bzw. geladen.
- Anschließend wird der `DatabaseManager` initialisiert, um die Verbindung zur Datenbank und zum Transaktions-Log aufzubauen.
- Der `/money`-Befehl (inklusive Aliase `/bal` und `/balance` aus der `plugin.yml`) wird mit demselben Objekt als `CommandExecutor` und `TabCompleter` registriert.
- Über `setupVault()` prüft das Plugin, ob **Vault** auf dem Server läuft, und registriert den `VaultEconomyProvider` mit hoher Priorität beim Bukkit `ServicesManager`. Falls Vault fehlt, läuft das Plugin trotzdem weiter und gibt eine Warnung aus.
- Beim Deaktivieren (`onDisable`) wird die Datenbankverbindung sauber geschlossen.

### 2. Befehle & Überweisungslogik (`MoneyCommand.java`)
- **Kontostand abfragen:** `/money` (sowie `/bal` und `/balance`) lädt den aktuellen Kontostand asynchron aus der Datenbank und zeigt ihn im deutschen Zahlenformat (`#,##0.##`) mit `$` an.
- **Spieler-Überweisungen (`/money pay <Spieler> <Betrag>`):**
  - Sucht den Zielspieler unter den aktuell Online-Spielern (erst exakter Name, dann Teil-/Groß-/Kleinschreibungstolerant über Bukkit).
  - Verhindert Überweisungen an Offline-Spieler sowie an sich selbst.
  - Akzeptiert sowohl Komma (`,`) als auch Punkt (`.`) als Dezimaltrennzeichen und prüft auf gültige positive Beträge (`> 0`).
  - **Schutz bei Großüberweisungen:** Beträge ab `10.000$` werden nicht sofort gebucht, sondern für **30 Sekunden** vorgemerkt und müssen mit `/money confirm` bestätigt werden.
- **Bestätigung (`/money confirm`):** Führt eine offene, noch nicht abgelaufene Großüberweisung aus, sofern das Guthaben zum Zeitpunkt der Bestätigung noch ausreicht.
- **Bestenliste (`/money top`):** Ruft die Top 10 der höchsten Kontostände ab und löst die zugehörigen Spielernamen über `Bukkit.getOfflinePlayer(...)` auf.
- **Admin-Befehle (`/money give|take|set <Spieler> <Betrag>`):**
  - `/money give <Spieler> <Betrag>` – Erhöht den Kontostand eines Spielers um den angegebenen Betrag (`> 0`).
  - `/money take <Spieler> <Betrag>` – Verringert den Kontostand eines Spielers, begrenzt das Ergebnis aber auf mindestens `0`.
  - `/money set <Spieler> <Betrag>` – Setzt den Kontostand eines Spielers direkt auf einen festen Wert (`>= 0`).
  - *Hinweis zur Permission:* Im Code wird aktuell `economysystem.admin` abgefragt, während in der `plugin.yml` `economy.admin` steht – beide sollten für konsistente Rechte auf denselben Namen vereinheitlicht werden.

### 3. Datenbank, Logging, Event & Vault
- **SQLite & Log (`DatabaseManager.java`):** Verwaltet Kontostände asynchron (`CompletableFuture`) in der Datei `economy.db` im Plugin-Ordner und schreibt Buchungen zusätzlich in `transactions.log`.
- **Custom Event (`EconomyTransactionEvent.java`):** Wird nach einer Transaktion über den Scheduler auf dem **Main-Thread** ausgelöst (`PAY`, `ADD`, `REMOVE`, `SET`), damit Listener sicher mit der Bukkit-/Paper-API arbeiten können.
- **Vault-Bridge (`VaultEconomyProvider.java`):** Implementiert Vaults `Economy`-Schnittstelle und leitet Abfragen und Änderungen an Kontoständen an den `DatabaseManager` weiter.

## Projektstruktur

```text
├── build.gradle.kts
└── src/
    └── main/
        ├── java/
        │   └── de/
        │       └── tigerjo/
        │           └── economySystem/
        │               ├── database/
        │               │   └── DatabaseManager.java
        │               ├── events/
        │               │   └── EconomyTransactionEvent.java
        │               ├── vault/
        │               │   └── VaultEconomyProvider.java
        │               ├── EconomySystem.java
        │               └── MoneyCommand.java
        └── resources/
            ├── config.yml
            └── plugin.yml
