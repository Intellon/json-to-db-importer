# Projektstatus

**Stand: 2026-07-20 — Phase 1 released, zuletzt 1.1.0.**

Verifikation: 101/101 Unit-Tests und 8/8 Integrationstests gegen einen echten MSSQL-Container
(`mvn verify -Pit`) grün.

## Bewusst offen (bestätigt, nicht behoben)

Beides Session-Randfälle eines Single-User-Tools auf localhost; ein sauberer Fix bräuchte ein Scan-Token-Konzept:

1. **Kein Doppel-Submit-Schutz auf „Import starten"** — zwei gleichzeitige Import-Läufe derselben Sitzung können sich beim Tabellen-Anlegen in die Quere kommen (falsche ERROR-/UPDATE-Meldungen, keine Datenkorruption durch den MERGE selbst).
2. **Veraltetes Formular nach Re-Scan** — ein vor einem erneuten Scan gerendertes Formular (Browser-Zurück-Button, zweiter Tab) referenziert Zeilen nur per Index und würde gegen die neue Dateiliste angewendet.

Workaround für beide: siehe „Grenzen" im [Benutzerhandbuch](docs/user-manual.md).

## Nächster Schritt

**Phase 2** (geplant, nicht gebaut): eingelesene JSONs analysieren, automatischen Mapping-Vorschlag generieren (verschachtelte Objekte/Listen → relationale Tabellen mit Fremdschlüsseln), Ausführung per OK/Upload.

Andockpunkte (übernommen aus der inzwischen gelöschten Design-Spec §10):

- Der Wizard erhält einen zusätzlichen Schritt zwischen Scan und Import: **„Mapping-Vorschlag"** — eingelesene JSONs werden präsentiert, das Tool generiert einen Entflechtungs-Vorschlag (verschachtelte Objekte/Listen → relationale Tabellen mit FK-Beziehungen), den der User per OK/Upload ausführt.
- Dafür docken in `service/` ein `JsonAnalyzer` (Strukturanalyse) und ein `MappingProposal`-Modell an; `SqlDialect` erhält zusätzliche DDL-Methoden.
- Da Phase 1 den Roh-Inhalt vollständig in der DB ablegt, kann Phase 2 auch bereits importierte JSONs nachträglich analysieren.
