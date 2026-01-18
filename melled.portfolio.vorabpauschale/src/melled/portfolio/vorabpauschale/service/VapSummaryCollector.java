package melled.portfolio.vorabpauschale.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import melled.portfolio.vorabpauschale.model.VapMetadata;
import melled.portfolio.vorabpauschale.service.VapCalculator.VapEntry;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;

/**
 * Sammelt VAP-Daten für die Zusammenfassung im Excel-Export.
 */
@Creatable
public class VapSummaryCollector
{

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
            if ((o == null) || (getClass() != o.getClass()))
            { return false; }
            VapKey vapKey = (VapKey) o;
            return (tfsPercentage == vapKey.tfsPercentage) && Objects.equals(isin, vapKey.isin)
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

        public String isin;
        public String name;
        public String depot;
        public Map<Integer, Double> vapBeforeTfs = new HashMap<>();
        public Map<Integer, Double> vapAfterTfs = new HashMap<>();

        public boolean isSumRow;

        public boolean isTotalRow;

        public boolean isEmptyRow;

    }

    private final VapCalculator vapCalculator;

    @Inject
    public VapSummaryCollector(VapCalculator vapCalculator)
    {
        this.vapCalculator = vapCalculator;
    }

    /**
     * Sammelt VAP-Zusammenfassung für alle Portfolios.
     *
     * @return Liste von VAP-Zeilen, sortiert nach Depot
     */
    public List<VapSummaryRow> collectSummary(Map<Portfolio, List<UnsoldTransaction>> transactions)
    {
        Map<VapKey, Map<Integer, Double>> vapSummary = new HashMap<>();
        Set<Integer> allYears = new TreeSet<>();

        collectTransactions(transactions, vapSummary, allYears);

        if (vapSummary.isEmpty())
        {
            return Collections.emptyList();
        }

        List<VapKey> sortedKeys = vapSummary.keySet().stream().sorted(Comparator.comparing((VapKey k) -> k.broker)
                        .thenComparing(k -> k.isin).thenComparing(k -> k.name)).toList();

        List<VapSummaryRow> rows = new ArrayList<>();
        String lastBroker = null;
        Map<String, Map<Integer, Double>> depotSumsBeforeTfs = new HashMap<>();
        Map<String, Map<Integer, Double>> depotSumsAfterTfs = new HashMap<>();
        Map<Integer, Double> totalSumsBeforeTfs = new HashMap<>();
        Map<Integer, Double> totalSumsAfterTfs = new HashMap<>();

        lastBroker = collectVapKeys(vapSummary, allYears, sortedKeys, rows, lastBroker, depotSumsBeforeTfs,
                        depotSumsAfterTfs, totalSumsBeforeTfs, totalSumsAfterTfs);

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

    private String collectVapKeys(Map<VapKey, Map<Integer, Double>> vapSummary, Set<Integer> allYears,
                    List<VapKey> sortedKeys, List<VapSummaryRow> rows, String lastBroker,
                    Map<String, Map<Integer, Double>> depotSumsBeforeTfs,
                    Map<String, Map<Integer, Double>> depotSumsAfterTfs, Map<Integer, Double> totalSumsBeforeTfs,
                    Map<Integer, Double> totalSumsAfterTfs)
    {
        for (VapKey key : sortedKeys)
        {

            if ((lastBroker != null) && !key.broker.equals(lastBroker))
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
                double tfsAmount = (vapBefore * key.tfsPercentage) / 100.0;
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
        return lastBroker;
    }

    private void collectTransactions(Map<Portfolio, List<UnsoldTransaction>> transactions,
                    Map<VapKey, Map<Integer, Double>> vapSummary, Set<Integer> allYears)
    {
        for (Entry<Portfolio, List<UnsoldTransaction>> portfolio : transactions.entrySet())
        {
            String broker = portfolio.getKey().getName();

            for (UnsoldTransaction transaction : portfolio.getValue())
            {
                Security security = transaction.getTransaction().getSecurity();
                if (security == null)
                {
                    continue;
                }

                String securityName = security.getName();
                String isin = security.getIsin() != null ? security.getIsin() : "";

                Set<VapMetadata> metadata = vapCalculator.getVapMedatasById(security);

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
    }

}
