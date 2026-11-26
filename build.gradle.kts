plugins {
    // Aktiviert Standard-Java-Build- und Compile-Tasks für Gradle
    java
}

// Projekt-Identität und Version (ersetzt u. a. '${version}' in der plugin.yml)
group = "de.tigerjo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // Repository für Paper-API-Artefakte
    maven("https://repo.papermc.io/repository/maven-public/")
    // Repository für JitPack-Abhängigkeiten wie VaultAPI
    maven("https://jitpack.io")
}

dependencies {
    // compileOnly, da Paper zur Laufzeit auf dem Server bereits vorhanden ist
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // VaultAPI nur zum Kompilieren einbinden; doppelte Bukkit-Klassen ausschließen
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

// Ziel-Java-Version festlegen (Java 21 für Paper 1.21.4)
val targetJavaVersion = 21
java {
    val version = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = version
    targetCompatibility = version
    // Nutzt bei Bedarf eine passende Java-Toolchain, falls lokal eine ältere Version läuft
    if (JavaVersion.current() < version) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

// Einheitliches UTF-8-Encoding für Umlaute und Sonderzeichen im Quellcode
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}