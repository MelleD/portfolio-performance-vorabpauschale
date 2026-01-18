package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests für TaxCalculator.
 */
@SuppressWarnings("java:S5976") // Move to jupiter test framework
public class TaxCalculatorTest
{
    private TaxCalculator calculator;

    @Before
    public void setUp()
    {
        calculator = new TaxCalculator();
    }

    @Test
    public void testGetKestFactorWithoutKirchensteuer()
    {
        // KESt 25% + Soli 5,5% = 25% * 1,055 = 26,375%
        double kestFactor = calculator.getKestFactor();
        assertThat(kestFactor).isEqualTo(0.26375);
    }

    @Test
    public void testGetKestFactorWith8PercentKirchensteuer()
    {
        calculator.setKirchensteuer(0.08);
        // KESt 25% + Soli 5,5% + Kirchensteuer 8% = 25% * (1 + 0,055 + 0,08) =
        // 25% * 1,135 = 28,375%
        double kestFactor = calculator.getKestFactor();
        assertThat(kestFactor).isEqualTo(0.28375);
    }

    @Test
    public void testGetKestFactorWith9PercentKirchensteuer()
    {
        calculator.setKirchensteuer(0.09);
        // KESt 25% + Soli 5,5% + Kirchensteuer 9% = 25% * (1 + 0,055 + 0,09) =
        // 25% * 1,145 = 28,625%
        double kestFactor = calculator.getKestFactor();
        assertThat(kestFactor).isEqualTo(0.28625);
    }

    @Test
    public void testSetAndGetKirchensteuer()
    {
        calculator.setKirchensteuer(0.08);
        assertThat(calculator.getKirchensteuer()).isEqualTo(0.08);

        calculator.setKirchensteuer(0.09);
        assertThat(calculator.getKirchensteuer()).isEqualTo(0.09);

        calculator.setKirchensteuer(0.0);
        assertThat(calculator.getKirchensteuer()).isEqualTo(0.0);
    }

    @Test
    public void testCalculateTaxableGainAfterTFSWithoutTFS()
    {
        double grossGain = 1000.0;
        double taxableGain = calculator.calculateTaxableGainAfterTFS(grossGain, 0);
        assertThat(taxableGain).isEqualTo(1000.0);
    }

    @Test
    public void testCalculateTaxableGainAfterTFSWith30Percent()
    {
        double grossGain = 1000.0;
        // 1000 * (100 - 30) / 100 = 700
        double taxableGain = calculator.calculateTaxableGainAfterTFS(grossGain, 30);
        assertThat(taxableGain).isEqualTo(700.0);
    }

    @Test
    public void testCalculateTaxableGainAfterTFSWith15Percent()
    {
        double grossGain = 1000.0;
        // 1000 * (100 - 15) / 100 = 850
        double taxableGain = calculator.calculateTaxableGainAfterTFS(grossGain, 15);
        assertThat(taxableGain).isEqualTo(850.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffsetNoLoss()
    {
        // Keine vorherigen Verluste, voller Gewinn versteuern
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(0.0, 500.0);
        assertThat(taxableGain).isEqualTo(500.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffsetWithPreviousGain()
    {
        // Bereits Gewinne vorhanden, voller Gewinn versteuern
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(1000.0, 500.0);
        assertThat(taxableGain).isEqualTo(500.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffsetFullyCompensated()
    {
        // Verlust von -1000, Gewinn von 500 -> komplett kompensiert
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(-1000.0, 500.0);
        assertThat(taxableGain).isEqualTo(0.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffsetPartiallyCompensated()
    {
        // Verlust von -300, Gewinn von 500 -> 200 steuerpflichtig
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(-300.0, 500.0);
        assertThat(taxableGain).isEqualTo(200.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffsetCurrentLoss()
    {
        // Kein vorheriger Verlust, aktueller Verlust -> 0 steuerpflichtig
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(0.0, -500.0);
        assertThat(taxableGain).isEqualTo(0.0);
    }

    @Test
    public void testCalculateTax()
    {
        // 1000 * 0.26375 = 263.75
        double tax = calculator.calculateTax(1000.0);
        assertThat(tax).isEqualTo(263.75);
    }

    @Test
    public void testCalculateTaxWithKirchensteuer()
    {
        calculator.setKirchensteuer(0.08);
        // 1000 * 0.28375 = 283.75
        double tax = calculator.calculateTax(1000.0);
        assertThat(tax).isEqualTo(283.75);
    }

    @Test
    public void testCalculateTaxOnZeroGain()
    {
        double tax = calculator.calculateTax(0.0);
        assertThat(tax).isEqualTo(0.0);
    }

    @Test
    public void testCalculateNetValue()
    {
        double grossValue = 10000.0;
        double taxes = 263.75;
        // 10000 - 263.75 = 9736.25
        double netValue = calculator.calculateNetValue(grossValue, taxes);
        assertThat(netValue).isEqualTo(9736.25);
    }

    @Test
    public void testCalculateTaxRatio()
    {
        double grossValue = 10000.0;
        double taxes = 263.75;
        // 263.75 / 10000 = 0.026375
        double taxRatio = calculator.calculateTaxRatio(grossValue, taxes);
        assertThat(taxRatio).isEqualTo(0.026375);
    }

    @Test
    public void testCalculateTaxRatioZeroGrossValue()
    {
        double taxRatio = calculator.calculateTaxRatio(0.0, 100.0);
        assertThat(taxRatio).isEqualTo(0.0);
    }

    @Test
    public void testCompleteScenarioWithTFSAndKirchensteuer()
    {
        calculator.setKirchensteuer(0.08);

        // Bruttogewinn 1000 EUR
        double grossGain = 1000.0;

        // Nach 30% TFS: 700 EUR
        double taxableGainAfterTFS = calculator.calculateTaxableGainAfterTFS(grossGain, 30);
        assertThat(taxableGainAfterTFS).isEqualTo(700.0);

        // Keine Verluste: voller Betrag steuerpflichtig
        double taxableGain = calculator.calculateTaxableGainWithLossOffset(0.0, taxableGainAfterTFS);
        assertThat(taxableGain).isEqualTo(700.0);

        // Steuer: 700 * 0.28375 = 198.625
        double tax = calculator.calculateTax(taxableGain);
        assertThat(tax).isEqualTo(198.625);

        // Bruttowert 5000 EUR
        double grossValue = 5000.0;

        // Nettowert: 5000 - 198.625 = 4801.375
        double netValue = calculator.calculateNetValue(grossValue, tax);
        assertThat(netValue).isEqualTo(4801.375);

        // Steueranteil: 198.625 / 5000 = 0.039725
        double taxRatio = calculator.calculateTaxRatio(grossValue, tax);
        assertThat(taxRatio).isEqualTo(0.039725);
    }

    @Test
    public void testCompleteScenarioWithLossOffset()
    {

        double cumulativeGain = 0.0;
        // Lot 1: Verlust von -500 EU
        double currentGain1 = -500.0;
        double taxableGain1 = calculator.calculateTaxableGainWithLossOffset(0.0, currentGain1);
        assertThat(taxableGain1).isEqualTo(0.0);
        cumulativeGain += currentGain1;

        // Lot 2: Gewinn von 300 EUR -> durch Verlust kompensiert
        double currentGain2 = 300.0;
        double taxableGain2 = calculator.calculateTaxableGainWithLossOffset(cumulativeGain, currentGain2);
        assertThat(taxableGain2).isEqualTo(0.0);
        cumulativeGain += currentGain2;
        assertThat(cumulativeGain).isEqualTo(-200.0);

        // Lot 3: Gewinn von 500 EUR -> 300 EUR steuerpflichtig
        double currentGain3 = 500.0;
        double taxableGain3 = calculator.calculateTaxableGainWithLossOffset(cumulativeGain, currentGain3);
        assertThat(taxableGain3).isEqualTo(300.0);
        cumulativeGain += currentGain3;
        assertThat(cumulativeGain).isEqualTo(300.0);

        // Lot 4: Gewinn von 200 EUR -> voller Betrag steuerpflichtig
        double currentGain4 = 200.0;
        double taxableGain4 = calculator.calculateTaxableGainWithLossOffset(cumulativeGain, currentGain4);
        assertThat(taxableGain4).isEqualTo(200.0);
        cumulativeGain += currentGain4;
        assertThat(cumulativeGain).isEqualTo(500.0);
    }
}
