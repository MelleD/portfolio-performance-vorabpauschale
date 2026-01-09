package melled.portfolio.vorabpauschale;

import java.util.List;
import java.util.Map;
import java.util.Set;

import melled.portfolio.vorabpauschale.VapSummaryCollector.VapSummaryRow;
import melled.portfolio.vorabpauschale.model.VapMetadata;
import name.abuchen.portfolio.model.Client;

/**
 * Hauptklasse für VAP-Export aus Portfolio Performance Client. Diese Klasse
 * kann von einem Eclipse RCP Menü-Command aufgerufen werden.
 */
public class VapExportService
{

    /**
     * Exportiert VAP-Daten aus einem Portfolio Performance Client nach Excel.
     * 
     * @param client
     *            Portfolio Performance Client
     * @param metadataFile
     *            Pfad zur ETF-Metadaten CSV (etf_metadaten.csv)
     * @param outputFile
     *            Pfad zur Ausgabe-Excel-Datei
     * @throws Exception
     *             bei Fehlern
     */
    public static void exportVap(Client client, String metadataFile, String outputFile) throws Exception
    {

        Map<String, Set<VapMetadata>> vapData = VapDataReader.readVapData(metadataFile);

        VapCalculator vapCalculator = new VapCalculator(vapData);

        VapSummaryCollector collector = new VapSummaryCollector(client, vapCalculator, vapData);
        List<VapSummaryRow> summaryRows = collector.collectSummary();

        if (summaryRows.isEmpty())
        { return; }

        VapExcelExporter exporter = new VapExcelExporter(client, vapCalculator, summaryRows);
        exporter.export(outputFile);

    }

}
