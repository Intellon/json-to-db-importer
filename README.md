# json-to-db-importer

Lokale Web-App, die JSON-Files aus einem Ordner rekursiv einliest und ihren kompletten, unveränderten Inhalt als Key-Value-Zeilen in MSSQL-Tabellen importiert (Tabelle = Name des Elternordners, Upsert per MERGE — beliebig wiederholbar).

## Voraussetzungen

- JDK 21
- Maven 3.9+
- Docker (nur für die Integrationstests)

## Build & Start

```bash
mvn -DskipTests package
java -jar target/json-to-db-importer-1.1.0.jar
```

Danach im Browser: **http://127.0.0.1:8080** — die App bindet ausschließlich an localhost.

## Tests

```bash
mvn test              # Unit-Tests
mvn verify -Pit       # zusätzlich Integrationstests gegen echten MSSQL-Container (braucht Docker)
```

Manuell gegen eine lokale Wegwerf-DB testen: siehe [Anleitung](docs/manual-testing.md).

## Bedienung

Siehe [Benutzerhandbuch](docs/user-manual.md). Kurzfassung: 3-Schritte-Wizard — Verbindung konfigurieren → Ordner scannen & Keys vergeben → Import ausführen.

## Tech-Stack

Java 21, Spring Boot 4.x (Thymeleaf, JDBC), MSSQL via `mssql-jdbc`. Die DB-Zugriffsschicht ist hinter einem `SqlDialect`-Interface abstrahiert — weitere Datenbanktypen lassen sich als zusätzliche Dialekt-Klasse ergänzen.

## Lizenz

MIT — siehe [LICENSE](LICENSE).
