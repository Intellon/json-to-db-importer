# Manuell testen — Schritt für Schritt

Kompletter Durchlauf gegen eine lokale Wegwerf-Datenbank (MSSQL im Docker-Container). Dauer: ~5 Minuten. Voraussetzung: Docker Desktop läuft.

## 1. Lokale MSSQL-Datenbank starten

```bash
docker run -d --name mssql-local -e ACCEPT_EULA=Y -e MSSQL_SA_PASSWORD='Test!Passw0rd' -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

Ein paar Sekunden warten, dann eine Test-Datenbank anlegen:

```bash
docker exec mssql-local /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Test!Passw0rd' -C -Q "CREATE DATABASE importer_test"
```

> Das Passwort muss der MSSQL-Richtlinie genügen (min. 8 Zeichen, Groß-/Kleinbuchstaben, Ziffer, Sonderzeichen) — sonst beendet sich der Container sofort. Prüfen mit `docker logs mssql-local`.

## 2. Testdaten anlegen

Einen Ordner mit ein paar JSON-Dateien in Unterordnern erstellen, z.B.:

```
D:\testdaten\
├── 01_Login\
│   └── session.json         {"logins": [{"user": "sa", "erfolgreich": true}]}
├── kunden\
│   ├── adressen.json        {"kunden": [{"name": "Müller", "ort": "Zürich"}]}
│   └── vertraege.json       {"vertraege": [{"nr": 1, "aktiv": true}]}
└── produkte\
    └── katalog.json         [{"artikel": "A-100", "preis": 9.90}]
```

Genau dieser Ordner liegt als `testdaten/` im Repo und kann direkt verwendet werden.

Der Unterordnername wird zum Tabellennamen, der Dateiname zum Key-Vorschlag. `01_Login` ist bewusst dabei: Ordner mit führender Ziffer müssen unverändert als Tabelle `01_Login` ankommen (früher wurde daraus `t_01_Login`).

## 3. App starten

```bash
mvn -DskipTests package
java -jar target/json-to-db-importer-1.0.0.jar
```

Browser: **http://127.0.0.1:8080**

## 4. Durch den Wizard klicken

**Schritt 1 — Verbindung:**

| Feld | Wert |
|---|---|
| Host | `localhost` |
| Port | `1433` |
| DB-Name | `importer_test` |
| User | `sa` |
| Passwort | `Test!Passw0rd` |

„Verbindung testen" → grüne Erfolgsmeldung → „Weiter zu Schritt 2".

**Schritt 2 — Dateien & Keys:** Ordner `D:\testdaten` eintragen → „Scannen". Erwartung: 4 Dateien, Zieltabellen `01_Login`, `kunden` und `produkte`, Keys vorbelegt (`session`, `adressen`, `vertraege`, `katalog`), alle „gültig".

**Schritt 3 — Import:** „Import starten". Erwartung: 4 × **neu angelegt**.

## 5. Ergebnis in der Datenbank prüfen

```bash
docker exec mssql-local /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Test!Passw0rd' -C -d importer_test -Q "SELECT file_key, LEFT(content, 60) AS preview, imported_at FROM [kunden]"
```

Erwartung: zwei Zeilen (`adressen`, `vertraege`) mit dem unveränderten JSON als Inhalt. Alternativ per SSMS/Azure Data Studio auf `localhost,1433` verbinden.

Und die Gegenprobe für den Ordner mit führender Ziffer — die Tabelle muss exakt `01_Login` heißen:

```bash
docker exec mssql-local /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Test!Passw0rd' -C -d importer_test -Q "SELECT name FROM sys.tables ORDER BY name"
```

## 6. Upsert testen (optional, lohnt sich)

Eine der JSON-Dateien inhaltlich ändern, in der App zurück zu Schritt 2, erneut scannen und importieren. Erwartung: geänderte Datei → **aktualisiert**, Rest → ebenfalls „aktualisiert" (gleicher Inhalt wird überschrieben, es entstehen keine Duplikate). Die Abfrage aus Schritt 5 zeigt den neuen Inhalt und ein frisches `imported_at`.

Weitere schnelle Experimente:

- Kaputtes JSON (`{"a":` ) in den Ordner legen → wird beim Scan als **ungültig** markiert und nicht importiert
- Zwei angewählte Dateien im selben Ordner denselben Key geben (auch `Foo`/`foo`) → Import wird mit Konflikt-Markierung blockiert
- Key leeren → Import wird blockiert („Key darf nicht leer sein")

## 7. Aufräumen

```bash
docker rm -f mssql-local
```

App mit `Ctrl+C` beenden. Die gemerkten Verbindungsdaten liegen in `~/.json-to-db-importer/config.json` (ohne Passwort) und können bei Bedarf gelöscht werden.

---

**Automatisierte Alternative:** `mvn verify -Pit` macht das alles ohne Klicken — startet selbst einen MSSQL-Container und testet Verbindung, Tabellenanlage (auch mit führender Ziffer), Upsert und Inhaltstreue (8 Integrationstests).
