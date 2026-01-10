package melled.portfolio.vorabpauschale.service;

import jakarta.inject.Inject;

import org.eclipse.e4.core.di.annotations.Creatable;

import name.abuchen.portfolio.model.Client;

/**
 * Hauptklasse für VAP Excel Export aus Portfolio Performance Client.
 */
@Creatable
public class VapExportService
{

    private VapExcelExporter vapExcelExporter;

    @Inject
    public VapExportService(VapCalculator vapCalculator, VapExcelExporter vapExcelExporter)
    {
        this.vapExcelExporter = vapExcelExporter;
    }

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
    public void exportVap(Client client, String metadataFile, String outputFile) throws Exception
    {
        vapExcelExporter.export(metadataFile, outputFile, client);

    }

}
