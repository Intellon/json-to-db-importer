# Projektstatus

**Stand: 2026-07-06 — Phase 1 fertig und in `main` gemerged.**

Verifikation zum Merge-Zeitpunkt: 76/76 Unit-Tests und 7/7 Integrationstests gegen einen echten MSSQL-Container (`mvn verify -Pit`) grün; App als Fat-JAR gestartet und Routen-Smoke-Test bestanden. Jeder Implementierungs-Task wurde einzeln reviewt, der Gesamt-Branch abschließend aus vier Perspektiven (Integration, Security, Spec-Abdeckung, Test-Hygiene); alle dort bestätigten Fehler wurden behoben — bis auf die zwei bewusst offen gelassenen Punkte unten.

## Bewusst offen (bestätigt, nicht behoben)

Beides Session-Randfälle eines Single-User-Tools auf localhost; ein sauberer Fix bräuchte ein Scan-Token-Konzept:

1. **Kein Doppel-Submit-Schutz auf „Import starten"** — zwei gleichzeitige Import-Läufe derselben Sitzung können sich beim Tabellen-Anlegen in die Quere kommen (falsche ERROR-/UPDATE-Meldungen, keine Datenkorruption durch den MERGE selbst).
2. **Veraltetes Formular nach Re-Scan** — ein vor einem erneuten Scan gerendertes Formular (Browser-Zurück-Button, zweiter Tab) referenziert Zeilen nur per Index und würde gegen die neue Dateiliste angewendet.

Workaround für beide: siehe „Grenzen" im [Benutzerhandbuch](docs/user-manual.md).

## Bekannte Minor-Findings (triagiert: akzeptabel, kein Handlungsbedarf)

Aus den Task-Reviews gesammelt; der finale Review hat sie alle als nicht merge-blockierend eingestuft. Aufgehoben als Backlog für Gelegenheits-Aufräumarbeiten:

**Robustheit / Verhalten**
- `IdentifierSanitizer.sanitize(null)` wirft NPE (Precondition undokumentiert); Regex wird pro Aufruf kompiliert
- `JsonValidator`: `StackOverflowError` bei pathologisch tief verschachteltem JSON wird nicht abgefangen
- `ImportService`: ein unerwartetes/null-Upsert-Ergebnis würde still als „aktualisiert" gemeldet statt als Fehler
- `ConfigController.testConnection` fängt breit `Exception` (könnte einen echten Bug als „Verbindungsfehler" maskieren); `DbConfigForm.dbType` hat keine deutsche Validierungsmeldung und kein Fehler-Span im Template
- `ConfigPersistenceService` loggt nur `e.getMessage()` ohne Stacktrace
- `FilesController`: `persistence.save` teilt sich den try/catch mit dem Scan (unscharfe Fehlergrenze); AppSettings-Merge-Logik doppelt in zwei Controllern; implizite Invariante „`setDbConfig` immer gepaart mit `setConnectionTested`" ist undokumentiert

**Testlücken (Logik jeweils manuell verifiziert)**
- Sanitizer: kein Leerstring-Test · Scanner: Randfälle untestet (Datei statt Ordner, Root ohne Namen, Datei namens `.json`)
- ImportValidator: kein 3-Wege-Konflikt- und kein null-Key-Test; Meldungstexte werden nicht geprüft
- DialectRegistry: Unknown-Type-Zweig untestet · ImportService: Rollback nur indirekt asserted
- ImportController: Guards (POST ohne Scan, /result ohne Verbindung) untestet · ConfigController: lastFolder-Erhalt-Zweig untestet · ConnectionFactory: kein dedizierter Test (Verhalten indirekt über ImportService-Tests und ITs abgedeckt)

**Kosmetik**
- Ungenutzter Mockito-Import in `FilesControllerTest`; 4× Stream-Count statt `groupingBy` auf der Ergebnisseite; gemischter Stil `@RequestParam`/`getParameter`; Integrationstests ohne `@AfterAll`-Close und mit hartkodiertem Falsch-Passwort; `'|'`-Kompositschlüssel im ImportValidator verlässt sich implizit auf den Sanitizer
- `JsonValidator`: fehlender Kommentar zum Second-Token-Mechanismus (warum das zweite `nextToken()` Trailing-Content erkennt); `ConfigPersistenceService.mapper` könnte `static final` sein, Import-Reihenfolge unüblich; `ImportService.importOne` hat 5 Parameter; `ConfigController`: `connectionTested`-Model-Attribut wird früh gesetzt und in den Zweigen überschrieben, `toDbConfig()` enthält einen toten Null-Check

## Nächster Schritt

**Phase 2** (geplant, nicht gebaut): eingelesene JSONs analysieren, automatischen Mapping-Vorschlag generieren (verschachtelte Objekte/Listen → relationale Tabellen mit Fremdschlüsseln), Ausführung per OK/Upload. Andockpunkte sind in der Architektur vorbereitet (`SqlDialect` erweiterbar, Rohdaten liegen vollständig in der DB, zusätzlicher Wizard-Schritt zwischen Scan und Import vorgesehen).
