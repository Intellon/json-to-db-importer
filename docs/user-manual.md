# Benutzerhandbuch — JSON → DB Importer

Der Importer überträgt den Inhalt von `.json`-Dateien unverändert in MSSQL-Tabellen. Pro Datei entsteht eine Zeile: ein frei wählbarer **Key** und als **Value** der komplette JSON-Inhalt. Die Zieltabelle ergibt sich aus dem Ordner, in dem die Datei liegt. Der Import ist wiederholbar: Existiert ein Key bereits, wird sein Inhalt aktualisiert statt doppelt angelegt.

## Starten

```bash
java -jar json-to-db-importer-1.0.0.jar
```

Browser öffnen: **http://127.0.0.1:8080**. Die App läuft nur lokal (kein Zugriff von anderen Rechnern) und ist für eine Person gedacht — nicht in mehreren Tabs parallel bedienen.

Die Oberfläche führt durch drei Schritte: **1 · Verbindung → 2 · Dateien & Keys → 3 · Import**.

## Schritt 1 — Verbindung

Felder: **DB-Typ** (derzeit MSSQL), **Host**, **Port** (Standard 1433), **DB-Name**, **User**, **Passwort**.

- **„Verbindung testen"** baut eine echte Verbindung auf. Schlägt sie fehl, erscheint die Fehlermeldung des Treibers direkt auf der Seite.
- **„Weiter zu Schritt 2"** erscheint erst nach einem erfolgreichen Test. Schlägt ein erneuter Test fehl (z.B. nach Serverwechsel), wird der Weiter-Link wieder ausgeblendet — es wird nie gegen eine ungetestete Verbindung importiert.
- Die Verbindungsdaten (ohne Passwort) und der zuletzt genutzte Ordner werden lokal gespeichert (`~/.json-to-db-importer/config.json`) und beim nächsten Start vorbelegt. Das **Passwort wird nie gespeichert** und ist pro Sitzung neu einzugeben. Eine beschädigte Config-Datei wird ignoriert.

## Schritt 2 — Dateien & Keys

Ordnerpfad eingeben (z.B. `D:\daten\json`) und **„Scannen"** klicken. Der Ordner wird **rekursiv** durchsucht; gefunden werden alle Dateien mit Endung `.json` (Groß-/Kleinschreibung egal).

Die Tabelle zeigt pro Datei:

| Spalte | Bedeutung |
|---|---|
| Checkbox | Datei für den Import an-/abwählen |
| Datei | Pfad relativ zum gescannten Ordner |
| Größe | Dateigröße in Bytes |
| Zieltabelle | Abgeleitet aus dem **direkten Elternordner** der Datei; Dateien direkt im gescannten Ordner nutzen dessen Namen. Sonderzeichen werden zu `_` bereinigt (z.B. `2024-daten` → `2024_daten`), Groß-/Kleinschreibung und führende Ziffern bleiben erhalten (`01_Login` → `01_Login`) — angezeigt wird der endgültige Tabellenname |
| JSON | **gültig** / **ungültig** (kein korrektes JSON-Dokument) / **Lesefehler** (Datei nicht lesbar, z.B. falsche Kodierung) |
| Key | Vorbelegt mit dem Dateinamen ohne `.json`, frei änderbar |

Regeln für Keys:

- Leere Keys sind nicht erlaubt — der Import wird blockiert und die Zeile markiert.
- Zwei angewählte Dateien dürfen für dieselbe Zieltabelle nicht denselben Key haben. Der Vergleich ignoriert Groß-/Kleinschreibung (`Foo` = `foo`), weil MSSQL das beim Schlüssel ebenso tut — sonst würde eine Datei die andere unbemerkt überschreiben.
- Ungültige Dateien und Lesefehler sind automatisch abgewählt und nicht anwählbar.

**„Import starten"** führt Schritt 3 aus.

## Schritt 3 — Import & Ergebnis

Jede Datei wird einzeln importiert (eigene Transaktion) — ein Fehler bei einer Datei stoppt nicht den Rest. Die Ergebnisliste zeigt **jede gescannte Datei** mit Status:

| Status | Bedeutung |
|---|---|
| **neu angelegt** | Key war neu, Zeile wurde eingefügt |
| **aktualisiert** | Key existierte, Inhalt wurde überschrieben |
| **übersprungen** | Datei war abgewählt, ungültig oder nicht lesbar (Grund in „Details") |
| **Fehler** | Datenbankfehler bei dieser Datei (Ursache in „Details") |

Ein erneuter Import derselben Dateien ist unkritisch: bestehende Keys werden aktualisiert.

## Was in der Datenbank entsteht

Fehlende Tabellen werden automatisch angelegt:

```sql
CREATE TABLE [<zieltabelle>] (
    file_key    NVARCHAR(450)  NOT NULL PRIMARY KEY,
    content     NVARCHAR(MAX)  NOT NULL,          -- kompletter JSON-Inhalt, unverändert
    imported_at DATETIME2      NOT NULL           -- Zeitpunkt des letzten Imports (UTC)
);
```

Abfragebeispiel:

```sql
SELECT file_key, imported_at FROM [folderxxx];
SELECT content FROM [folderxxx] WHERE file_key = 'file_xyz';
```

> **Geändert nach 1.0.0:** Tabellennamen, die mit einer Ziffer beginnen, bekamen früher ein `t_`
> davor (`01_Login` → `t_01_Login`). Das ist entfallen, die Tabelle heißt jetzt wie der Ordner.
> Mit 1.0.0 importierte Daten liegen weiterhin in der alten `t_`-Tabelle; der nächste Import legt
> daneben die neue Tabelle an. Falls die alten Daten weiter genutzt werden sollen, die alte Tabelle
> vorher umbenennen: `EXEC sp_rename 't_01_Login', '01_Login';`

## Fehlerbehebung

| Problem | Ursache / Lösung |
|---|---|
| Verbindungstest schlägt fehl | Host/Port/Zugangsdaten prüfen; die angezeigte Treibermeldung nennt die Ursache (z.B. „Login failed") |
| Datei als „ungültig" markiert | Die Datei enthält kein einzelnes, vollständiges JSON-Dokument (auch Text nach dem JSON macht sie ungültig) |
| „Lesefehler" | Datei ist nicht als UTF-8 lesbar — Kodierung prüfen |
| Wizard startet ohne gespeicherte Werte | `~/.json-to-db-importer/config.json` war beschädigt und wurde ignoriert — einfach neu eingeben |
| Sehr große Dateien | Dateien werden komplett in den Speicher gelesen; geeignet bis einige hundert MB pro Datei |

## Grenzen

- Ein Import-Lauf pro Sitzung: den „Import starten"-Button nur einmal klicken und das Ergebnis abwarten; nicht parallel in mehreren Tabs arbeiten.
- Nach einem erneuten Scan immer aus der aktuell angezeigten Tabelle importieren (nicht über den Browser-Zurück-Button ein altes Formular absenden).
- Phase 2 (automatischer Mapping-Vorschlag: verschachtelte Strukturen in relationale Tabellen entflechten) ist geplant, aber noch nicht enthalten.
