package melled.portfolio.vorabpauschale.service;

import jakarta.inject.Singleton;

import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;

/**
 * Berechnet Kosten und Anschaffungspreise für Wertpapiere.
 */
@Creatable
@Singleton
public class CostCalculator
{
    /**
     * Berechnet die Kosten pro Anteil aus einer Transaktion.
     *
     * @param tx
     *            Transaktion
     * @return Kosten pro Anteil
     */
    public double calculateCostPerShare(UnsoldTransaction tx)
    {
        return tx.getTransaction().getGrossPricePerShare().toBigDecimal().doubleValue();
    }

    /**
     * Berechnet die Gesamtkosten einer Transaktion.
     *
     * @param tx
     *            Transaktion
     * @return Gesamtkosten
     */
    public double calculateTotalCost(UnsoldTransaction tx)
    {
        double costPerShare = calculateCostPerShare(tx);
        return tx.getShare() * costPerShare;
    }

    /**
     * Berechnet den Anschaffungspreis pro Anteil inklusive VAP.
     *
     * @param costPerShare
     *            Ursprüngliche Kosten pro Anteil
     * @param totalVapPerShare
     *            Gesamte VAP pro Anteil
     * @return Anschaffungspreis inkl. VAP
     */
    public double calculateAcquisitionPriceWithVap(double costPerShare, double totalVapPerShare)
    {
        return costPerShare + totalVapPerShare;
    }

    /**
     * Berechnet den Gewinn/Verlust aus einem Verkauf.
     *
     * @param currentPrice
     *            Aktueller Preis pro Anteil
     * @param acquisitionPrice
     *            Anschaffungspreis pro Anteil (inkl. VAP)
     * @param shares
     *            Anzahl Anteile
     * @return Gewinn/Verlust (positiv = Gewinn, negativ = Verlust)
     */
    public double calculateGain(double currentPrice, double acquisitionPrice, double shares)
    {
        return (currentPrice - acquisitionPrice) * shares;
    }
}
