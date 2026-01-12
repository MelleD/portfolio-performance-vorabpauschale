# Wertpapier Metadaten

Für eine korrekte Berechnung muss die Vorabpauschale vor TFS pro Anteil bestimmt und manuell ausgerechnet werden.
Die Informationen werden in einer CSV Datei der Simulation bereitgestellt.

## CSV-Datei Format

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
- **ID**: ISIN, WKN oder Name des Wertpapiers. Empfohlen ist genau diese Reihenfolge, wenn die Daten vorhanden sind.
- **Jahr des Wertzuwachses**: Das Steuerjahr für die Berechnung
- **Vorabpauschale vor TFS pro Anteil**: Vorabpauschale vor Teilfreistellung pro Anteil in Euro
- **Prozent Teilfreistellung**: Teilfreistellungssatz in Prozent (z.B. 30 für Aktienfonds)

## Wertpapier Metadaten hinzufügen

Gerne kann die Datei [etf_metadaten_vorabpauschalen.csv](https://github.com/MelleD/portfolio-performance-vorabpauschale/blob/main/etf_metadaten_vorabpauschalen.csv) genutzt und gepflegt werden, damit alle Nutzer davon profitieren!
 
Es kann ein Issue erstellt werden, aus dem direkt ein PR geöffnet wird: [Issue öffnen](https://github.com/MelleD/portfolio-performance-vorabpauschale/issues/new?template=new_metadaten_request.yaml&title=[Neue%20Metadaten%20für%20ein%20Wertpapier]%3A+)

![Image](../assets/create-issue.png)


## Bankdokumente verarbeiten

Die Metadaten können auch aus Bankdokumenten (z.B. Jahressteuerbescheinigungen) extrahiert werden:
