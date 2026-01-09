package melled.portfolio.vorabpauschale;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import melled.portfolio.vorabpauschale.model.VapMetadata;

/**
 * Liest ETF-Metadaten und VAP-Daten aus CSV-Dateien. Entspricht den
 * Python-Funktionen read_etf_metadata() und read_vap().
 */
public class VapDataReader
{

    private static CSVFormat format = CSVFormat.Builder.create().setDelimiter(";").setHeader().setSkipHeaderRecord(true)
                    .get();

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
    public static Map<String, Set<VapMetadata>> readVapData(String vapFile) throws IOException
    {
        Map<String, Set<VapMetadata>> idToMetadatas = new HashMap<>();

        try (Reader reader = new FileReader(vapFile); CSVParser parser = CSVParser.parse(reader, format))
        {

            for (CSVRecord record : parser)
            {
                String id = record.get("ID");

                int year = Integer.parseInt(record.get("Jahr des Wertzuwachses"));
                double vapBeforeTfs = Double
                                .parseDouble(record.get("Vorabpauschale vor TFS pro Anteil").replace(',', '.'));
                int tfsPercentage = Integer.parseInt(record.get("Prozent Teilfreistellung"));
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
