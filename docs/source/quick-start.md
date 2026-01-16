# 🚀 Quick Start 

## Voraussetzungen

- Portfolio Performance (Version 0.81.1 oder kompatibel)

## Installation in Portfolio Performance


???+ failure "Anmerkung"
    Aktuell ist nur der umständliche Weg des manuellen Kopierens möglich, da Portfolio Performance Manager alle anderen Wege blockiert hat. Falls jemand einen besseren Weg findet, bitte Bescheid geben oder in diesem Ticket unterstützen: [https://github.com/portfolio-performance/portfolio/issues/5326](https://github.com/portfolio-performance/portfolio/issues/5326).

???+ tip 
    Erstellen Sie immer eine Kopie Ihrer Portfolio-Datei, auch wenn nichts damit gemacht wird.

1. Laden Sie das neueste Release-ZIP herunter: [melled.portfolio.updatesite-xxx.zip](https://github.com/MelleD/portfolio-performance-vorabpauschale/releases).
2. Entpacken Sie das ZIP-File.
3. Navigieren Sie zum Eclipse-`plugins`-Verzeichnis:
   - **Windows**: `C:\Program Files\PortfolioPerformance\eclipse\plugins`
   - **Mac**: `Programme > PortfolioPerformance.app > Contents > Eclipse`

4. Kopieren Sie den **Inhalt** des `plugins`-Ordners aus dem ZIP in Ihr Installations-`plugins`-Verzeichnis.
5. Öffnen Sie `configuration/config.ini` und fügen Sie **am Ende der Zeile** `osgi.bundles` (hinter `simpleconfigurator_xxxx`) folgenden Inhalt hinzu – ersetzen Sie **nicht** die gesamte Zeile:

```
,reference\:file\:org.apache.logging.log4j.core@3:start,reference\:file\:org.apache.logging.log4j.api@3:start,reference\:file\:wrapped.org.apache.poi.poi@4:start,reference\:file\:wrapped.org.apache.poi.poi-ooxml@4:start,reference\:file\:org.apache.commons.commons-collections4@4:start,reference\:file\:org.apache.commons.commons-compress@4:start,reference\:file\:org.apache.commons.commons-io@4:start,reference\:file\:org.apache.commons.commons-logging@4:start,reference\:file\:wrapped.org.apache.xmlbeans.xmlbeans@4:start,reference\:file\:wrapped.org.apache.poi.poi-ooxml-full@4:start,reference\:file\:melled.portfolio.vorabpauschale@4:start
```

**Beispiel nach der Änderung:**

```
osgi.bundles=reference\:file\:org.eclipse.equinox.simpleconfigurator_1.5.700.v20251111-1031.jar@1\:start,reference\:file\:org.apache.logging.log4j.core@3:start,reference\:file\:org.apache.logging.log4j.api@3:start,reference\:file\:wrapped.org.apache.poi.poi@4:start,reference\:file\:wrapped.org.apache.poi.poi-ooxml@4:start,reference\:file\:org.apache.commons.commons-collections4@4:start,reference\:file\:org.apache.commons.commons-compress@4:start,reference\:file\:org.apache.commons.commons-io@4:start,reference\:file\:org.apache.commons.commons-logging@4:start,reference\:file\:wrapped.org.apache.xmlbeans.xmlbeans@4:start,reference\:file\:wrapped.org.apache.poi.poi-ooxml-full@4:start,reference\:file\:melled.portfolio.vorabpauschale@4:start
```

6. Starten Sie Portfolio Performance neu.



## Vorstellung

Die Vorstellung ist, dass die Installation später einfach über das eine Updateseite funktioniert


