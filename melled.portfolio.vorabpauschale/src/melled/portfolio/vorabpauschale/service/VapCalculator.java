package melled.portfolio.vorabpauschale.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import melled.portfolio.vorabpauschale.model.VapMetadata;
import name.abuchen.portfolio.model.Security;

/**
 * Berechnet die Vorabpauschale (VAP) für Portfolio-Transaktionen.
 */
@Creatable
@Singleton
public class VapCalculator
{

    /**
     * VAP-Daten: Jahr -> VAP vor TFS pro Anteil
     */
    private Map<String, Set<VapMetadata>> vapBySecurityAndYear;
    private VapCsvDataReader csvDataReader;

    @Inject
    public VapCalculator(VapCsvDataReader csvDataReader)
    {
        this.csvDataReader = csvDataReader;
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
    public Map<Integer, VapEntry> calculateVapList(UnsoldTransaction transaction)
    {
        Map<Integer, VapEntry> vapList = new HashMap<>();

        Security security = transaction.getTransaction().getSecurity();

        Set<VapMetadata> vapYears = getVapMedatasById(security);

        if (vapYears.isEmpty())
        {
            return vapList; // Keine VAP-Daten für dieses Wertpapier
        }

        LocalDate purchasedDate = transaction.getTransaction().getDateTime().toLocalDate();
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
    public double calculateTotalVap(UnsoldTransaction transaction, int year)
    {
        Map<Integer, VapEntry> vapList = calculateVapList(transaction);

        if (!vapList.containsKey(year))
        { return 0.0; }

        double vapPerShare = vapList.get(year).vap;

        double shares = transaction.getUnsoldShare();
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
    public double calculateTotalVapPerShare(UnsoldTransaction transaction)
    {
        Map<Integer, VapEntry> vapList = calculateVapList(transaction);
        return vapList.values().stream().map(VapEntry::vap).mapToDouble(Double::doubleValue).sum();
    }

    public Set<VapMetadata> getVapMedatasById(Security security)
    {
        String name = security.getName();
        String isin = security.getIsin();
        String wkn = security.getWkn();

        Set<VapMetadata> metadata = vapBySecurityAndYear.get(isin);
        if (metadata != null)
        { return metadata; }
        metadata = vapBySecurityAndYear.get(wkn);
        if (metadata != null)
        { return metadata; }
        metadata = vapBySecurityAndYear.get(name);
        if (metadata != null)
        { return metadata; }

        return Collections.emptySet();
    }

    public void initializeVapData(String metadataFile)
    {
        try
        {
            vapBySecurityAndYear = csvDataReader.readVapData(metadataFile);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Fehler beim Lesen der VAP-Metadaten-Datei: " + metadataFile, e);
        }

    }

    public record VapEntry(Double vap, Integer tfsPercentage)
    {
    }

}
