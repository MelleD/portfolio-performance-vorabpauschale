package melled.portfolio.vorabpauschale.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;

/**
 * Berechnet Portfolio-Werte wie Brutto-Wert, steuerpflichtige Gewinne und
 * Netto-Wert.
 */
@Creatable
@Singleton
public class PortfolioValueCalculator
{
    private final CostCalculator costCalculator;
    private final TaxCalculator taxCalculator;

    @Inject
    public PortfolioValueCalculator(CostCalculator costCalculator, TaxCalculator taxCalculator)
    {
        this.costCalculator = costCalculator;
        this.taxCalculator = taxCalculator;
    }

    public CostCalculator getCostCalculator()
    {
        return costCalculator;
    }

    public TaxCalculator getTaxCalculator()
    {
        return taxCalculator;
    }

    /**
     * Berechnet den aktuellen Preis pro Anteil aus einem Security.
     *
     * @param security
     *            Wertpapier
     * @return Preis pro Anteil
     */
    public double calculateCurrentPricePerShare(Security security)
    {
        var securityPrice = security.getSecurityPrice(java.time.LocalDate.now());
        if (securityPrice == null)
        { return 0.0; }
        return securityPrice.getValue() / (double) Values.Quote.factor();
    }

    /**
     * Berechnet den Brutto-Wert (aktueller Marktwert) einer Position.
     *
     * @param currentPricePerShare
     *            Aktueller Preis pro Anteil
     * @param shares
     *            Anzahl unverkaufter Anteile
     * @return Brutto-Wert
     */
    public double calculateGrossValue(double currentPricePerShare, double shares)
    {
        return currentPricePerShare * shares;
    }

    /**
     * Berechnet den steuerpflichtigen Gewinn unter Berücksichtigung von VAP,
     * TFS und Verlustverrechnung.
     *
     * @param currentPricePerShare
     *            Aktueller Preis pro Anteil
     * @param acquisitionPricePerShare
     *            Anschaffungspreis inkl. VAP pro Anteil
     * @param shares
     *            Anzahl unverkaufter Anteile
     * @param tfsPercentage
     *            Teilfreistellungs-Prozentsatz
     * @param cumulativeTaxableGain
     *            Kumulierter steuerpflichtiger Gewinn aus vorherigen Lots
     * @return Steuerpflichtiger Gewinn
     */
    public double calculateTaxableGain(double currentPricePerShare, double acquisitionPricePerShare, double shares,
                    int tfsPercentage, double cumulativeTaxableGain)
    {
        // Bruttogewinn
        double grossGain = costCalculator.calculateGain(currentPricePerShare, acquisitionPricePerShare, shares);

        // TFS anwenden
        double taxableGainAfterTFS = taxCalculator.calculateTaxableGainAfterTFS(grossGain, tfsPercentage);

        // Verlustverrechnung
        return taxCalculator.calculateTaxableGainWithLossOffset(cumulativeTaxableGain, taxableGainAfterTFS);
    }

    /**
     * Berechnet die Steuern auf einen steuerpflichtigen Gewinn.
     *
     * @param taxableGain
     *            Steuerpflichtiger Gewinn
     * @return Zu zahlende Steuern
     */
    public double calculateTaxes(double taxableGain)
    {
        return taxCalculator.calculateTax(taxableGain);
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
        return taxCalculator.calculateNetValue(grossValue, taxes);
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
        return taxCalculator.calculateTaxRatio(grossValue, taxes);
    }

    /**
     * Komplett-Berechnung für eine Transaktion: Berechnet alle Werte
     * (Brutto-Wert, steuerpflichtiger Gewinn, Steuern, Netto-Wert,
     * Steueranteil) in einem Durchgang.
     */
    public static class PositionValues
    {
        public final double grossValue;
        public final double taxableGain;
        public final double taxableGainToConsider;
        public final double taxes;
        public final double netValue;
        public final double taxRatio;

        public PositionValues(double grossValue, double taxableGain, double taxableGainToConsider, double taxes,
                        double netValue, double taxRatio)
        {
            this.grossValue = grossValue;
            this.taxableGain = taxableGain;
            this.taxableGainToConsider = taxableGainToConsider;
            this.taxes = taxes;
            this.netValue = netValue;
            this.taxRatio = taxRatio;
        }
    }

    /**
     * Berechnet alle Portfolio-Werte für eine Transaktion.
     *
     * @param tx
     *            Transaktion
     * @param currentPricePerShare
     *            Aktueller Preis pro Anteil
     * @param acquisitionPricePerShare
     *            Anschaffungspreis inkl. VAP pro Anteil
     * @param tfsPercentage
     *            Teilfreistellungs-Prozentsatz
     * @param cumulativeTaxableGain
     *            Kumulierter steuerpflichtiger Gewinn
     * @return Alle berechneten Werte
     */
    public PositionValues calculatePositionValues(UnsoldTransaction tx, double currentPricePerShare,
                    double acquisitionPricePerShare, int tfsPercentage, double cumulativeTaxableGain)
    {
        double shares = tx.getUnsoldShare();

        // Brutto-Wert
        double grossValue = calculateGrossValue(currentPricePerShare, shares);

        // Bruttogewinn und TFS
        double grossGain = costCalculator.calculateGain(currentPricePerShare, acquisitionPricePerShare, shares);
        double taxableGain = taxCalculator.calculateTaxableGainAfterTFS(grossGain, tfsPercentage);

        // Verlustverrechnung
        double taxableGainToConsider = taxCalculator.calculateTaxableGainWithLossOffset(cumulativeTaxableGain,
                        taxableGain);

        // Steuern
        double taxes = calculateTaxes(taxableGainToConsider);

        // Netto-Wert
        double netValue = calculateNetValue(grossValue, taxes);

        // Steueranteil
        double taxRatio = calculateTaxRatio(grossValue, taxes);

        return new PositionValues(grossValue, taxableGain, taxableGainToConsider, taxes, netValue, taxRatio);
    }
}
