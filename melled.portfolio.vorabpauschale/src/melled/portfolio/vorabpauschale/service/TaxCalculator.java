package melled.portfolio.vorabpauschale.service;

import java.text.DecimalFormat;

import jakarta.inject.Singleton;

import org.eclipse.e4.core.di.annotations.Creatable;

/**
 * Berechnet Steuern (KESt, Kirchensteuer) für Wertpapiergewinne.
 */
@Creatable
@Singleton
public class TaxCalculator
{
    private static final double KEST_BASE_RATE = 0.25;
    private static final double SOLIDARITY_SURCHARGE = 0.055;

    private double kirchensteuer = 0.0; // 0.08 für 8% oder 0.09 für 9%

    /**
     * Berechnet den KESt-Faktor inkl. Solidaritätszuschlag und Kirchensteuer.
     *
     * @return KESt-Faktor
     */
    public double getKestFactor()
    {
        double factor = 1.0 + SOLIDARITY_SURCHARGE + kirchensteuer;
        return KEST_BASE_RATE * factor;
    }

    /**
     * Setzt den Kirchensteuer-Satz.
     *
     * @param kirchensteuer
     *            Kirchensteuer-Satz (z.B. 0.08 für 8%, 0.09 für 9%)
     */
    public void setKirchensteuer(double kirchensteuer)
    {
        this.kirchensteuer = kirchensteuer;
    }

    /**
     * Gibt den aktuellen Kirchensteuer-Satz zurück.
     *
     * @return Kirchensteuer-Satz
     */
    public double getKirchensteuer()
    {
        return kirchensteuer;
    }

    /**
     * Berechnet den steuerpflichtigen Gewinn nach Teilfreistellung.
     *
     * @param grossGain
     *            Bruttogewinn
     * @param tfsPercentage
     *            Teilfreistellungs-Prozentsatz (z.B. 30 für 30%)
     * @return Steuerpflichtiger Gewinn nach TFS
     */
    public double calculateTaxableGainAfterTFS(double grossGain, int tfsPercentage)
    {
        if (tfsPercentage <= 0)
        { return grossGain; }
        return (grossGain * (100 - tfsPercentage)) / 100.0;
    }

    /**
     * Bestimmt den steuerpflichtigen Gewinn unter Berücksichtigung von
     * Verlusten. Verluste aus früheren Lots können mit Gewinnen verrechnet
     * werden.
     *
     * @param cumulativeGains
     *            Kumulierte Gewinne/Verluste aus früheren Lots
     * @param currentGain
     *            Gewinn/Verlust des aktuellen Lots
     * @return Steuerpflichtiger Gewinn (0 wenn durch Verluste kompensiert)
     */
    public double calculateTaxableGainWithLossOffset(double cumulativeGains, double currentGain)
    {
        // Wenn bereits Gewinne vorhanden sind, voll versteuern
        if (cumulativeGains > 0)
        { return Math.max(0.0, currentGain); }

        // Verluste verrechnen
        double remainingLoss = Math.abs(cumulativeGains);
        if (currentGain <= remainingLoss)
        { return 0.0; }

        return currentGain - remainingLoss;
    }

    /**
     * Berechnet die zu zahlende Steuer auf einen Gewinn.
     *
     * @param taxableGain
     *            Steuerpflichtiger Gewinn
     * @return Zu zahlende Steuer
     */
    public double calculateTax(double taxableGain)
    {
        return taxableGain * getKestFactor();
    }

    /**
     * Berechnet den Netto-Wert nach Abzug der Steuern.
     *
     * @param grossValue
     *            Brutto-Wert
     * @param taxes
     *            Zu zahlende Steuern
     * @return Netto-Wert
     */
    public double calculateNetValue(double grossValue, double taxes)
    {
        return grossValue - taxes;
    }

    /**
     * Berechnet den Steueranteil am Brutto-Wert.
     *
     * @param grossValue
     *            Brutto-Wert
     * @param taxes
     *            Zu zahlende Steuern
     * @return Steueranteil (0.0 bis 1.0)
     */
    public double calculateTaxRatio(double grossValue, double taxes)
    {
        return grossValue > 0 ? taxes / grossValue : 0.0;
    }

    public String formatKest()
    {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(getKestFactor() * 100);
    }
}
