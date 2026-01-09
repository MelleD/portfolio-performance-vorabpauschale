package melled.portfolio.vorabpauschale;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import melled.portfolio.vorabpauschale.model.VapMetadata;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;

/**
 * Berechnet die Vorabpauschale (VAP) für Portfolio-Transaktionen.
 */
public class VapCalculator
{

    /**
     * VAP-Daten: Jahr -> VAP vor TFS pro Anteil
     */
    private final Map<String, Set<VapMetadata>> vapBySecurityAndYear;

    public VapCalculator(Map<String, Set<VapMetadata>> vapBySecurityAndYear)
    {
        this.vapBySecurityAndYear = vapBySecurityAndYear;
    }

    /**
     * Berechnet die VAP-Liste für eine Transaktion.
     * 
     * @param transaction
     *            Portfolio-Transaktion
     * @param securityName
     *            Name des Wertpapiers
     * @return Map von Jahr -> VAP pro Anteil vor TFS
     */
    public Map<Integer, VapEntry> calculateVapList(PortfolioTransaction transaction)
    {
        Map<Integer, VapEntry> vapList = new HashMap<>();

        Security security = transaction.getSecurity();

        Set<VapMetadata> vapYears = VapSummaryCollector.getVapMedatasById(vapBySecurityAndYear, security);

        if (vapYears.isEmpty())
        {
            return vapList; // Keine VAP-Daten für dieses Wertpapier
        }

        LocalDate purchasedDate = transaction.getDateTime().toLocalDate();
        int purchasedYear = purchasedDate.getYear();

        for (VapMetadata metadata : vapYears)
        {
            int year = metadata.getYear();
            double vapPerShareBeforeTfs = metadata.getVapBeforeTfs();

            if (year < purchasedYear)
            {

                continue;
            }

            double proportionOfYear;
            if (purchasedYear == year)
            {
                // Anteilige VAP für jeden Teilmonat
                // 12/12 für Januar, 1/12 für Dezember
                proportionOfYear = (13 - purchasedDate.getMonthValue()) / 12.0;
            }
            else
            {
                // Volles Jahr
                proportionOfYear = 1.0;
            }

            double vap = proportionOfYear * vapPerShareBeforeTfs;
            if (vap > 0)
            {
                vapList.put(year, new VapEntry(vap, metadata.getTfsPercentage()));

            }
        }

        return vapList;
    }

    /**
     * Berechnet die Gesamt-VAP für eine Transaktion unter Berücksichtigung der
     * Anteile.
     * 
     * @param transaction
     *            Portfolio-Transaktion
     * @param securityName
     *            Name des Wertpapiers
     * @param year
     *            Jahr
     * @return Gesamt-VAP (VAP pro Anteil * Anzahl Anteile)
     */
    public double calculateTotalVap(PortfolioTransaction transaction, int year)
    {
        Map<Integer, VapEntry> vapList = calculateVapList(transaction);

        if (!vapList.containsKey(year))
        { return 0.0; }

        double vapPerShare = vapList.get(year).vap;
        // In Portfolio Performance sind Shares als long gespeichert
        // (factorized)
        double shares = transaction.getShares() / Values.Share.divider();
        return vapPerShare * shares;
    }

    /**
     * Berechnet die Summe aller VAP für eine Transaktion.
     * 
     * @param transaction
     *            Portfolio-Transaktion
     * @param securityName
     *            Name des Wertpapiers
     * @return Summe VAP vor TFS pro Anteil
     */
    public double calculateTotalVapPerShare(PortfolioTransaction transaction)
    {
        Map<Integer, VapEntry> vapList = calculateVapList(transaction);
        return vapList.values().stream().map(VapEntry::vap).mapToDouble(Double::doubleValue).sum();
    }

    public record VapEntry(Double vap, Integer tfsPercentage)
    {
    }
}
