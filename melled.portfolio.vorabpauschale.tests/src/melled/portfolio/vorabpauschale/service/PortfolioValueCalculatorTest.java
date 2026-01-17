package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.Before;
import org.junit.Test;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.money.Values;

/**
 * Tests für PortfolioValueCalculator.
 */
public class PortfolioValueCalculatorTest
{
    private PortfolioValueCalculator valueCalculator;
    private CostCalculator costCalculator;
    private TaxCalculator taxCalculator;
    private Client client;

    @Before
    public void setUp()
    {
        costCalculator = new CostCalculator();
        taxCalculator = new TaxCalculator();
        valueCalculator = new PortfolioValueCalculator(costCalculator, taxCalculator);
        client = new Client();
    }

    @Test
    public void testCalculateCurrentPricePerShare()
    {
        Security security = new SecurityBuilder().addTo(client);

        // Aktuellen Preis setzen: 120 EUR
        SecurityPrice price = new SecurityPrice(LocalDate.now(), Values.Quote.factorize(120.0));
        security.addPrice(price);

        double currentPrice = valueCalculator.calculateCurrentPricePerShare(security);
        assertThat(currentPrice).isEqualTo(120.0);
    }

    @Test
    public void testCalculateCurrentPricePerShareNoPriceAvailable()
    {
        Security security = new SecurityBuilder().addTo(client);

        // Kein Preis vorhanden
        double currentPrice = valueCalculator.calculateCurrentPricePerShare(security);
        assertThat(currentPrice).isEqualTo(0.0);
    }

    @Test
    public void testCalculateGrossValue()
    {
        double currentPrice = 120.0;
        double shares = 10.0;

        // Bruttowert: 120 * 10 = 1200
        double grossValue = valueCalculator.calculateGrossValue(currentPrice, shares);
        assertThat(grossValue).isEqualTo(1200.0);
    }

    @Test
    public void testCalculateTaxableGainWithoutTFS()
    {
        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;
        int tfsPercentage = 0;
        double cumulativeGain = 0.0;

        // Gewinn: (120 - 100) * 10 = 200, ohne TFS = 200
        double taxableGain = valueCalculator.calculateTaxableGain(currentPrice, acquisitionPrice, shares,
                        tfsPercentage, cumulativeGain);
        assertThat(taxableGain).isEqualTo(200.0);
    }

    @Test
    public void testCalculateTaxableGainWith30PercentTFS()
    {
        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;
        int tfsPercentage = 30;
        double cumulativeGain = 0.0;

        // Gewinn: (120 - 100) * 10 = 200, nach 30% TFS = 140
        double taxableGain = valueCalculator.calculateTaxableGain(currentPrice, acquisitionPrice, shares,
                        tfsPercentage, cumulativeGain);
        assertThat(taxableGain).isEqualTo(140.0);
    }

    @Test
    public void testCalculateTaxableGainWithLossOffset()
    {
        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;
        int tfsPercentage = 0;
        double cumulativeGain = -50.0; // Vorheriger Verlust

        // Gewinn: 200, minus Verlust 50 = 150 steuerpflichtig
        double taxableGain = valueCalculator.calculateTaxableGain(currentPrice, acquisitionPrice, shares,
                        tfsPercentage, cumulativeGain);
        assertThat(taxableGain).isEqualTo(150.0);
    }

    @Test
    public void testCalculateTaxes()
    {
        double taxableGain = 1000.0;

        // Steuer: 1000 * 0.26375 = 263.75
        double taxes = valueCalculator.calculateTaxes(taxableGain);
        assertThat(taxes).isEqualTo(263.75);
    }

    @Test
    public void testCalculateNetValue()
    {
        double grossValue = 10000.0;
        double taxes = 263.75;

        // Nettowert: 10000 - 263.75 = 9736.25
        double netValue = valueCalculator.calculateNetValue(grossValue, taxes);
        assertThat(netValue).isEqualTo(9736.25);
    }

    @Test
    public void testCalculateTaxRatio()
    {
        double grossValue = 10000.0;
        double taxes = 263.75;

        // Steueranteil: 263.75 / 10000 = 0.026375
        double taxRatio = valueCalculator.calculateTaxRatio(grossValue, taxes);
        assertThat(taxRatio).isEqualTo(0.026375);
    }

    @Test
    public void testCalculatePositionValues()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        int tfsPercentage = 30;
        double cumulativeGain = 0.0;

        var values = valueCalculator.calculatePositionValues(tx, currentPrice, acquisitionPrice, tfsPercentage,
                        cumulativeGain);

        // Bruttowert: 120 * 10 = 1200
        assertThat(values.grossValue).isEqualTo(1200.0);

        // Gewinn: (120 - 100) * 10 = 200, nach TFS: 200 * 0.7 = 140
        assertThat(values.taxableGain).isEqualTo(140.0);

        // Kein Verlust zum Verrechnen
        assertThat(values.taxableGainToConsider).isEqualTo(140.0);

        // Steuer: 140 * 0.26375 = 36.925
        assertThat(values.taxes).isEqualTo(36.925);

        // Nettowert: 1200 - 36.925 = 1163.075
        assertThat(values.netValue).isEqualTo(1163.075);

        // Steueranteil: 36.925 / 1200 = 0.0307708...
        assertThat(values.taxRatio).isEqualTo(0.030770833333333334);
    }

    @Test
    public void testCalculatePositionValuesWithLoss()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        double currentPrice = 80.0; // Verlust!
        double acquisitionPrice = 100.0;
        int tfsPercentage = 0;
        double cumulativeGain = 0.0;

        var values = valueCalculator.calculatePositionValues(tx, currentPrice, acquisitionPrice, tfsPercentage,
                        cumulativeGain);

        // Bruttowert: 80 * 10 = 800
        assertThat(values.grossValue).isEqualTo(800.0);

        // Verlust: (80 - 100) * 10 = -200
        assertThat(values.taxableGain).isEqualTo(-200.0);

        // Verlust = keine Steuer
        assertThat(values.taxableGainToConsider).isEqualTo(0.0);
        assertThat(values.taxes).isEqualTo(0.0);

        // Nettowert = Bruttowert (keine Steuer)
        assertThat(values.netValue).isEqualTo(800.0);

        // Kein Steueranteil
        assertThat(values.taxRatio).isEqualTo(0.0);
    }

    @Test
    public void testCalculatePositionValuesWithPreviousLoss()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        int tfsPercentage = 0;
        double cumulativeGain = -150.0; // Vorheriger Verlust

        var values = valueCalculator.calculatePositionValues(tx, currentPrice, acquisitionPrice, tfsPercentage,
                        cumulativeGain);

        // Bruttowert: 120 * 10 = 1200
        assertThat(values.grossValue).isEqualTo(1200.0);

        // Gewinn: (120 - 100) * 10 = 200
        assertThat(values.taxableGain).isEqualTo(200.0);

        // Mit Verlustverrechnung: 200 - 150 = 50 steuerpflichtig
        assertThat(values.taxableGainToConsider).isEqualTo(50.0);

        // Steuer: 50 * 0.26375 = 13.1875
        assertThat(values.taxes).isEqualTo(13.1875);

        // Nettowert: 1200 - 13.1875 = 1186.8125
        assertThat(values.netValue).isEqualTo(1186.8125);
    }

    @Test
    public void testCalculatePositionValuesCompleteScenario()
    {
        taxCalculator.setKirchensteuer(0.08); // 8% Kirchensteuer

        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(100), PortfolioBuilder.amountOf(5000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        double currentPrice = 60.0;
        double acquisitionPrice = 50.0; // Inkl. VAP
        int tfsPercentage = 30;
        double cumulativeGain = 0.0;

        var values = valueCalculator.calculatePositionValues(tx, currentPrice, acquisitionPrice, tfsPercentage,
                        cumulativeGain);

        // Bruttowert: 60 * 100 = 6000
        assertThat(values.grossValue).isEqualTo(6000.0);

        // Gewinn: (60 - 50) * 100 = 1000, nach TFS: 1000 * 0.7 = 700
        assertThat(values.taxableGain).isEqualTo(700.0);

        // Steuer mit Kirchensteuer: 700 * 0.28375 = 198.625
        assertThat(values.taxes).isEqualTo(198.625);

        // Nettowert: 6000 - 198.625 = 5801.375
        assertThat(values.netValue).isEqualTo(5801.375);

        // Steueranteil: 198.625 / 6000 = 0.0331041...
        assertThat(values.taxRatio).isEqualTo(0.03310416666666667);
    }
}
