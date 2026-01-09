# Portfolio Performance - Vorabpauschale Extension

[![PayPal.Me][paypal-me-badge]][paypal-me-url]
[![BuyMeCoffee][buy-me-a-coffee-shield]][buy-me-a-coffee-url]


Eine Erweiterung für [Portfolio Performance](https://www.portfolio-performance.info/), die die Berechnung und Simulation der Vorabpauschale für KESt-pflichtige Wertpapiere wie Fonds/ETFs und Aktien ermöglicht. Die Ergebnisse werden pro Depot in Excel exportiert.

**Inspiration und Dank:** Dieses Projekt wurde inspiriert durch [pyfifovap](https://github.com/nspo/pyfifovap). Vielen Dank für die wertvollen Ideen und Ansätze!

## 📋 Funktionen

- **Vorabpauschale-Berechnung**: Automatische Berechnung der Vorabpauschale für Fonds und ETFs
- **KESt-Simulation**: Simulation der Kapitalertragsteuer für verschiedene Wertpapiertypen
- **Excel-Export**: Übersichtlicher Export der Berechnungen pro Depot
- **Integration**: Nahtlose Integration in Portfolio Performance als Plugin

## 🚀 Installation

### Voraussetzungen

- Portfolio Performance (Version 0.81.1 oder kompatibel)


### Installation in Portfolio Performance

**Aktuell TODO**: Detaillierte Installationsanleitung für die Integration des Plugins in Portfolio Performance wird noch erstellt.

Ideen sind:
- P2-Repository für einfache Installation
- Dropins-Ordner Installation
- Eclipse Marketplace Integration (langfristig)

## 📖 Anwendung

### Voraussetzungen

- Vorbereitung einer CSV für die Metadaten der Vorabpauschale siehe:

### Excel-Export durchführen

1. Öffnen Sie Portfolio Performance
2. Navigieren Sie zu: **Datei → Exportieren → Vorabpauschale Excel exportieren**
3. Wählen Sie CSV-Datei für die Metdataen aus (siehe: Metadaten vorbereiten)
4. Speichern Sie die Excel-Datei am gewünschten Ort

Die exportierte Excel-Datei enthält:
- Detaillierte Berechnungen der Vorabpauschale
- Zusammenfassung der Vorabpauschale
- KESt-Berechnungen pro Wertpapier
- Zusammenfassungen pro Depot


### Metadaten vorbereiten

Für die korrekte Berechnung werden Metadaten zu den Wertpapieren benötigt.

#### CSV-Datei Format

Die CSV-Datei muss folgendes Format haben (Semikolon-getrennt):

```csv
ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung
```

**Beispiel:**

```csv
ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung
IE00BK5BQT80;2023;1.6235;30
IE00BK5BQT80;2024;1.7165;30
IE00BK5BQT80;2025;2.4029;30
IE00BKM4GZ66;2023;0.4793;30
IE00BKM4GZ66;2024;0.4609;30
IE00BKM4GZ66;2025;0.5933;30
LU1829221024;2019;0;30
LU1829221024;2020;0;30
```

**Spaltenbeschreibung:**
- **ID**: ISIN des Wertpapiers
- **Jahr des Wertzuwachses**: Das Steuerjahr für die Berechnung
- **Vorabpauschale vor TFS pro Anteil**: Vorabpauschale vor Teilfreistellung pro Anteil in Euro
- **Prozent Teilfreistellung**: Teilfreistellungssatz in Prozent (z.B. 30 für Aktienfonds)

#### Bankdokumente verarbeiten

Die Metadaten können auch aus Bankdokumenten (z.B. Jahressteuerbescheinigungen) extrahiert werden:

## 🛠️ Technische Details

### Projektstruktur

```
portfolio-performance-vorabpauschale/
├── melle-portfolio-target-definition/   # Target Platform Definition
├── melled.portfolio.parent/             # Parent POM
├── melled.portfolio.vorabpauschale/     # Haupt-Plugin
│   ├── src/melled/portfolio/vorabpauschale/
│   │   ├── VapCalculator.java          # Berechnungslogik
│   │   ├── VapExcelExporter.java       # Excel-Export
│   │   ├── VapExportService.java       # Export-Service
│   │   └── model/                      # Datenmodelle
│   └── plugin.xml
└── melled.portfolio.feature/            # Feature Definition
```

### Verwendete Technologien

- **Eclipse RCP/E4**: Plugin-Framework
- **Apache POI**: Excel-Generierung
- **Tycho**: Maven-Build für Eclipse Plugins
- **Java 21**: Entwicklungssprache

## 📝 TODO

### Kurzfristig
- [ ] **Automatischer Build & Release**: GitHub Actions Workflow für automatische Builds einrichten
- [ ] **i10n-Übersetzungen**: Mehrsprachigkeit implementieren
  - [ ] Deutsch (Hauptsprache)
  - [ ] Englisch
  - [ ] Weitere Sprachen nach Bedbedarf
- [ ] **Installationsanleitung**: Detaillierte Dokumentation der Installation vervollständigen
- [ ] **Entwicklung**: Nutzung und Umstellung auf Depedency Injection und weg von statischen Aufrufen

### Mittelfristig
- [ ] **Unit-Tests**: Test-Coverage erhöhen
- [ ] **Dokumentation**: Benutzerhandbuch erweitern
- [ ] **Beispieldaten**: Beispiel-CSV-Dateien bereitstellen

## 🤝 Mitwirken

Beiträge sind willkommen! Bitte erstellen Sie:
1. Einen Fork des Repositories
2. Einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Ihre Änderungen committen (`git commit -m 'Add some AmazingFeature'`)
4. Den Branch pushen (`git push origin feature/AmazingFeature`)
5. Einen Pull Request öffnen

## 📄 Lizenz

Dieses Projekt steht unter der Eclipse Public License v1.0. Siehe [EPL-1.0](http://www.eclipse.org/legal/epl-v10.html) für Details.

## 🔗 Links

- [Portfolio Performance](https://www.portfolio-performance.info/)
- [Portfolio Performance GitHub](https://github.com/buchen/portfolio)
- [pyfifovap - Python FIFO Vorabpauschale](https://github.com/nspo/pyfifovap) - Inspiration für dieses Projekt

## 💡 Inspiration

Ein besonderer Dank geht an das [pyfifovap](https://github.com/nspo/pyfifovap) Projekt von @nspo(Nicolai Spohrer). Die Ideen und Konzepte aus diesem Python-basierten Tool haben maßgeblich zur Entwicklung dieser Portfolio Performance Extension beigetragen.

## 📧 Kontakt

Bei Fragen oder Problemen öffnen Sie bitte ein Issue im GitHub-Repository.

---

## ⚠️ Haftungsausschluss & Hinweise

**Wichtige Hinweise zur Nutzung:**

### Freizeitprojekt
Dieses Plugin wird ausschließlich in der Freizeit entwickelt und gepflegt. Es handelt sich um ein privates Projekt ohne kommerzielle Unterstützung.

### Keine Garantie für Richtigkeit
**WICHTIG:** Der Entwickler verwaltet ausschließlich ETFs und ist kein Aktien- oder Steuerexperte. 

- ✅ **ETF-Berechnungen** sind aus eigener Erfahrung getestet
- ⚠️ **Aktien-Berechnungen** sollten besonders sorgfältig geprüft werden
- ❗ **Steuerliche Korrektheit**: Alle Ergebnisse müssen eigenverantwortlich kontrolliert werden
- 📋 Im Zweifelsfall konsultieren Sie bitte einen Steuerberater

**Es wird keinerlei Garantie für die Richtigkeit der Berechnungen übernommen. Die Verwendung erfolgt auf eigenes Risiko.**

### Keine Garantie für Weiterentwicklung
Die Weiterentwicklung und Implementierung neuer Features erfolgt:
- In der persönlichen Freizeit
- Nach anderen privaten Projekten
- Ohne festen Zeitplan oder Roadmap
- Ohne Verpflichtung zur Fertigstellung angekündigter Features

**Es kann keine Garantie für zukünftige Weiterentwicklung, Updates oder neue Features gegeben werden.**

### Empfehlungen
1. ✅ Prüfen Sie alle Berechnungen eigenständig
2. ✅ Vergleichen Sie die Ergebnisse mit anderen Quellen
3. ✅ Konsultieren Sie bei Unsicherheit einen Steuerberater
4. ✅ Verwenden Sie das Plugin als Hilfsmittel, nicht als alleinige Grundlage für Steuererklärungen

---

**Hinweis**: Dieses Plugin befindet sich in aktiver Entwicklung. Funktionen und APIs können sich noch ändern.


<!-- Badges -->

[paypal-me-badge]: https://img.shields.io/static/v1.svg?label=%20&message=PayPal.Me&logo=paypal
[buy-me-a-coffee-shield]: https://img.shields.io/static/v1.svg?label=%20&message=Buy%20me%20a%20coffee&color=6f4e37&logo=buy%20me%20a%20coffee&logoColor=white

<!-- References -->

[paypal-me-url]: https://www.paypal.me/MelleDennis
[buy-me-a-coffee-url]: https://www.buymeacoffee.com/melled