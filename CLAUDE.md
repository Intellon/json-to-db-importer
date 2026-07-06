# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JSON-zu-MSSQL Importer: lokale Spring-Boot-Web-App (Wizard mit 3 Schritten), die JSON-Files aus einem Ordner rekursiv einliest und ihren kompletten Inhalt per MERGE-Upsert als Key-Value-Zeilen in MSSQL-Tabellen ablegt (Tabelle = Elternordnername, Spalten file_key/content/imported_at). Spec: `docs/superpowers/specs/2026-07-06-json-to-mssql-importer-design.md`, Plan: `docs/superpowers/plans/2026-07-06-json-to-mssql-importer-phase1.md`.

## Commands

- Build + Unit-Tests: `mvn test`
- Einzelner Test: `mvn test -Dtest=IdentifierSanitizerTest`
- Integrationstests (brauchen Docker): `mvn verify -Pit`
- Fat-JAR bauen: `mvn -DskipTests package`
- Starten: `java -jar target/json-to-db-importer-0.1.0-SNAPSHOT.jar` → http://127.0.0.1:8080

## Architecture

Java 21, Spring Boot 4.x (Achtung: Web-Starter heißt `spring-boot-starter-webmvc`). Schichten unter `io.intellon.jsonimporter`:

- `web/` — 3 Controller (Config/Files/Import) + Thymeleaf-Templates; Session-Zustand in `WizardState` (@SessionScope), hält DbConfig inkl. Passwort nur im Speicher
- `service/` — `FolderScanService` (rekursiver Scan), `ImportService` (eine Transaktion pro File, Fehler isoliert), `ImportValidator` (Keys getrimmt + case-insensitiv verglichen, weil MSSQL-Kollationen case-insensitiv sind), `JsonValidator` (genau EIN JSON-Dokument, Trailing-Content = ungültig), `ConfigPersistenceService` (~/.json-to-db-importer/config.json, ohne Passwort)
- `db/` — `SqlDialect`-Interface; ALLES MSSQL-spezifische SQL lebt nur in `MssqlDialect` (CREATE TABLE IF..., MERGE mit HOLDLOCK + OUTPUT $action). Neuer DB-Typ = neue Dialekt-Klasse + DbType-Konstante
- `model/` — Records

## Constraints

- Commits ohne Attribution (keine Co-Authored-By-Zeilen) — explizite User-Vorgabe
- SQL-Identifier immer über `IdentifierSanitizer` + `[...]`; Values nur als PreparedStatement-Parameter
- UI-Texte Deutsch; in Thymeleaf Record-Accessoren als Methodenaufruf (`${f.defaultKey()}`)
- Phase 2 (Mapping-Vorschlag/Entflechtung) ist geplant, aber NICHT gebaut — Andockpunkte siehe Spec §10
