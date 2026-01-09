package melled.portfolio.vorabpauschale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import melled.portfolio.vorabpauschale.VapCalculator.VapEntry;
import melled.portfolio.vorabpauschale.model.VapMetadata;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;

/**
 * Sammelt VAP-Daten für die Zusammenfassung im Excel-Export.
 */
public class VapSummaryCollector
{

    private final Client client;
    private final VapCalculator vapCalculator;
    private Map<String, Set<VapMetadata>> vapData;

    /**
     * Schlüssel für VAP-Aggregation: (ISIN, Name, Broker, TFS%)
     */
    private static class VapKey
    {
        final String isin;
        final String name;
        final String broker;
        final int tfsPercentage;

        VapKey(String isin, String name, String broker, int tfsPercentage)
        {
            this.isin = isin;
            this.name = name;
            this.broker = broker;
            this.tfsPercentage = tfsPercentage;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            { return true; }
            if (o == null || getClass() != o.getClass())
            { return false; }
            VapKey vapKey = (VapKey) o;
            return tfsPercentage == vapKey.tfsPercentage && Objects.equals(isin, vapKey.isin)
                            && Objects.equals(name, vapKey.name) && Objects.equals(broker, vapKey.broker);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(isin, name, broker, tfsPercentage);
        }
    }

    /**
     * Zeile in der VAP-Zusammenfassung.
     */
    public static class VapSummaryRow
    {
        public String isin;
        public String name;
        public String depot;
        public Map<Integer, Double> vapBeforeTfs = new HashMap<>();
        public Map<Integer, Double> vapAfterTfs = new HashMap<>();
        public boolean isSumRow;
        public boolean isTotalRow;
        public boolean isEmptyRow;

        public VapSummaryRow()
        {
        }

        public static VapSummaryRow empty()
        {
            VapSummaryRow row = new VapSummaryRow();
            row.isEmptyRow = true;
            return row;
        }

        public static VapSummaryRow sumRow(String depot)
        {
            VapSummaryRow row = new VapSummaryRow();
            row.isin = "Summe";
            row.depot = depot;
            row.isSumRow = true;
            return row;
        }

        public static VapSummaryRow totalRow()
        {
            VapSummaryRow row = new VapSummaryRow();
            row.isin = "GESAMTSUMME";
            row.isTotalRow = true;
            return row;
        }
    }

    public VapSummaryCollector(Client client, VapCalculator vapCalculator, Map<String, Set<VapMetadata>> vapData)
    {
        this.client = client;
        this.vapCalculator = vapCalculator;
        this.vapData = vapData;
    }

    /**
     * Sammelt VAP-Zusammenfassung für alle Portfolios.
     * 
     * @return Liste von VAP-Zeilen, sortiert nach Depot
     */
    public List<VapSummaryRow> collectSummary()
    {
        Map<VapKey, Map<Integer, Double>> vapSummary = new HashMap<>();
        Set<Integer> allYears = new TreeSet<>();

        for (Portfolio portfolio : client.getPortfolios())
        {
            String broker = portfolio.getName();

            for (PortfolioTransaction transaction : portfolio.getTransactions())
            {
                if (!transaction.getType().isPurchase())
                {
                    continue;
                }

                Security security = transaction.getSecurity();
                if (security == null)
                {
                    continue;
                }

                String securityName = security.getName();
                String isin = security.getIsin() != null ? security.getIsin() : "";

                Set<VapMetadata> metadata = getVapMedatasById(vapData, security);

                int tfsPercentage = 0;
                if (!metadata.isEmpty())
                {
                    tfsPercentage = metadata.iterator().next().getTfsPercentage();
                }

                VapKey key = new VapKey(isin, securityName, broker, tfsPercentage);

                // Berechne VAP für diese Transaktion
                Map<Integer, VapEntry> vapList = vapCalculator.calculateVapList(transaction);

                for (Map.Entry<Integer, VapEntry> entry : vapList.entrySet())
                {
                    int year = entry.getKey();
                    double vapPerShare = entry.getValue().vap();

                    // Berechne Gesamt-VAP
                    double totalVap = vapCalculator.calculateTotalVap(transaction, year);

                    // Aggregiere
                    vapSummary.computeIfAbsent(key, k -> new HashMap<>()).merge(year, totalVap, Double::sum);

                    allYears.add(year);
                }
            }
        }

        if (vapSummary.isEmpty())

        { return Collections.emptyList(); }

        List<VapKey> sortedKeys = vapSummary.keySet().stream().sorted(Comparator.comparing((VapKey k) -> k.broker)
                        .thenComparing(k -> k.isin).thenComparing(k -> k.name)).toList();

        List<VapSummaryRow> rows = new ArrayList<>();
        String lastBroker = null;
        Map<String, Map<Integer, Double>> depotSumsBeforeTfs = new HashMap<>();
        Map<String, Map<Integer, Double>> depotSumsAfterTfs = new HashMap<>();
        Map<Integer, Double> totalSumsBeforeTfs = new HashMap<>();
        Map<Integer, Double> totalSumsAfterTfs = new HashMap<>();

        for (VapKey key : sortedKeys)
        {

            if (lastBroker != null && !key.broker.equals(lastBroker))
            {
                VapSummaryRow sumRow = VapSummaryRow.sumRow(lastBroker);
                sumRow.vapBeforeTfs = depotSumsBeforeTfs.getOrDefault(lastBroker, new HashMap<>());
                sumRow.vapAfterTfs = depotSumsAfterTfs.getOrDefault(lastBroker, new HashMap<>());
                rows.add(sumRow);
                rows.add(VapSummaryRow.empty());
            }

            VapSummaryRow row = new VapSummaryRow();
            row.isin = key.isin;
            row.name = key.name;
            row.depot = key.broker;

            Map<Integer, Double> yearVaps = vapSummary.get(key);
            for (int year : allYears)
            {
                double vapBefore = yearVaps.getOrDefault(year, 0.0);
                double tfsAmount = vapBefore * key.tfsPercentage / 100.0;
                double vapAfter = vapBefore - tfsAmount;

                row.vapBeforeTfs.put(year, vapBefore);
                row.vapAfterTfs.put(year, vapAfter);

                // Depot-Summen
                depotSumsBeforeTfs.computeIfAbsent(key.broker, k -> new HashMap<>()).merge(year, vapBefore,
                                Double::sum);
                depotSumsAfterTfs.computeIfAbsent(key.broker, k -> new HashMap<>()).merge(year, vapAfter, Double::sum);

                // Gesamt-Summen
                totalSumsBeforeTfs.merge(year, vapBefore, Double::sum);
                totalSumsAfterTfs.merge(year, vapAfter, Double::sum);
            }

            rows.add(row);
            lastBroker = key.broker;
        }

        // Letzte Depot-Summe
        if (lastBroker != null)
        {
            VapSummaryRow sumRow = VapSummaryRow.sumRow(lastBroker);
            sumRow.vapBeforeTfs = depotSumsBeforeTfs.getOrDefault(lastBroker, new HashMap<>());
            sumRow.vapAfterTfs = depotSumsAfterTfs.getOrDefault(lastBroker, new HashMap<>());
            rows.add(sumRow);
        }

        // Leerzeile vor Gesamtsumme
        rows.add(VapSummaryRow.empty());

        // Gesamtsumme
        VapSummaryRow totalRow = VapSummaryRow.totalRow();
        totalRow.vapBeforeTfs = totalSumsBeforeTfs;
        totalRow.vapAfterTfs = totalSumsAfterTfs;
        rows.add(totalRow);

        return rows;
    }

    public static Set<VapMetadata> getVapMedatasById(Map<String, Set<VapMetadata>> vapData, Security security)
    {
        String name = security.getName();
        String isin = security.getIsin() != null ? security.getIsin() : "";
        String wkn = security.getWkn() != null ? security.getWkn() : "";

        Set<VapMetadata> metadata = vapData.get(isin);
        if (metadata != null)
        { return metadata; }
        metadata = vapData.get(wkn);
        if (metadata != null)
        { return metadata; }
        metadata = vapData.get(name);
        if (metadata != null)
        { return metadata; }
        // throw new IllegalStateException("No assignement found for isin, wkn
        // or name");
        return Collections.emptySet();
    }
}
