package melled.portfolio.vorabpauschale.service;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.VapMetadata;

/**
 * Liest ETF-Metadaten und VAP-Daten aus CSV-Dateien. Mit @Creatable annotiert,
 * um als Injectable Service verfügbar zu sein.
 */
@Creatable
@Singleton
public class VapCsvDataReader
{

    enum CsvColumns
    {
        ID("ID"), YEAR_OF_VALUE_INCREASE("Jahr des Wertzuwachses"), VAP_BEFORE_TFS_SHARE(
                        "Vorabpauschale vor TFS pro Anteil"), TFS_PERCENTAGE("Prozent Teilfreistellung");

        private final String columnName;

        CsvColumns(String columnName)
        {
            this.columnName = columnName;
        }

        public String getColumnName()
        {
            return columnName;
        }
    }

    private static final CSVFormat format = CSVFormat.Builder.create().setDelimiter(";").setHeader()
                    .setSkipHeaderRecord(true).get();

    /**
     * Liest VAP-Daten aus CSV. Format: Name,Jahr des Wertzuwachses,VAP vor TFS
     * pro Anteil
     *
     * @param vapFile
     *            Pfad zur VAP-CSV
     * @return Map: Security Name -> (Jahr -> VAP vor TFS pro Anteil)
     * @throws IOException
     *             bei Lesefehlern
     */
    public Map<String, Set<VapMetadata>> readVapData(String vapFile) throws IOException
    {
        Map<String, Set<VapMetadata>> idToMetadatas = new HashMap<>();

        try (Reader reader = new FileReader(vapFile); CSVParser parser = CSVParser.parse(reader, format))
        {

            for (CSVRecord csvRecord : parser)
            {
                String id = csvRecord.get(CsvColumns.ID.getColumnName());

                int year = Integer.parseInt(csvRecord.get(CsvColumns.YEAR_OF_VALUE_INCREASE.getColumnName()));
                double vapBeforeTfs = Double.parseDouble(
                                csvRecord.get(CsvColumns.VAP_BEFORE_TFS_SHARE.getColumnName()).replace(',', '.'));
                int tfsPercentage = Integer.parseInt(csvRecord.get(CsvColumns.TFS_PERCENTAGE.getColumnName()));
                VapMetadata metadata = new VapMetadata(id, year, vapBeforeTfs, tfsPercentage);
                Set<VapMetadata> metadatas = idToMetadatas.get(id);

                if (metadatas == null)
                {
                    metadatas = new HashSet<>();
                    idToMetadatas.put(id, metadatas);
                }
                metadatas.add(metadata);

            }
        }

        return idToMetadatas;
    }

}
