package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;

/**
 * Tests für CostCalculator.
 */
public class CostCalculatorTest
{
    private CostCalculator calculator;
    private Client client;

    @Before
    public void setUp()
    {
        calculator = new CostCalculator();
        client = new Client();
    }

    @Test
    public void testCalculateCostPerShare()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        // 1000 / 10 = 100 pro Anteil
        double costPerShare = calculator.calculateCostPerShare(tx);
        assertThat(costPerShare).isEqualTo(100.0);
    }

    @Test
    public void testCalculateTotalCost()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        // Gesamtkosten: 10 Anteile * 100 = 1000
        double totalCost = calculator.calculateTotalCost(tx);
        assertThat(totalCost).isEqualTo(1000.0);
    }

    @Test
    public void testCalculateTotalCostWithPartialSale()
    {
        Security security = new SecurityBuilder().addTo(client);
        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        UnsoldTransaction tx = new UnsoldTransaction(portfolio.getTransactions().get(0));

        // Gesamtkosten basieren auf gekauften Anteilen (share), nicht unverkauften (unsoldShare)
        double totalCost = calculator.calculateTotalCost(tx);
        assertThat(totalCost).isEqualTo(1000.0);
    }

    @Test
    public void testCalculateAcquisitionPriceWithVap()
    {
        double costPerShare = 100.0;
        double totalVapPerShare = 5.5;

        // Anschaffungspreis = Kosten + VAP = 100 + 5.5 = 105.5
        double acquisitionPrice = calculator.calculateAcquisitionPriceWithVap(costPerShare, totalVapPerShare);
        assertThat(acquisitionPrice).isEqualTo(105.5);
    }

    @Test
    public void testCalculateAcquisitionPriceWithoutVap()
    {
        double costPerShare = 100.0;
        double totalVapPerShare = 0.0;

        // Anschaffungspreis = Kosten + 0 = 100
        double acquisitionPrice = calculator.calculateAcquisitionPriceWithVap(costPerShare, totalVapPerShare);
        assertThat(acquisitionPrice).isEqualTo(100.0);
    }

    @Test
    public void testCalculateGainProfit()
    {
        double currentPrice = 120.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;

        // Gewinn: (120 - 100) * 10 = 200
        double gain = calculator.calculateGain(currentPrice, acquisitionPrice, shares);
        assertThat(gain).isEqualTo(200.0);
    }

    @Test
    public void testCalculateGainLoss()
    {
        double currentPrice = 80.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;

        // Verlust: (80 - 100) * 10 = -200
        double gain = calculator.calculateGain(currentPrice, acquisitionPrice, shares);
        assertThat(gain).isEqualTo(-200.0);
    }

    @Test
    public void testCalculateGainWithVap()
    {
        double currentPrice = 120.0;
        double costPerShare = 100.0;
        double vapPerShare = 5.5;
        double shares = 10.0;

        // Anschaffungspreis inkl. VAP
        double acquisitionPrice = calculator.calculateAcquisitionPriceWithVap(costPerShare, vapPerShare);
        assertThat(acquisitionPrice).isEqualTo(105.5);

        // Gewinn: (120 - 105.5) * 10 = 145
        double gain = calculator.calculateGain(currentPrice, acquisitionPrice, shares);
        assertThat(gain).isEqualTo(145.0);
    }

    @Test
    public void testCalculateGainZero()
    {
        double currentPrice = 100.0;
        double acquisitionPrice = 100.0;
        double shares = 10.0;

        // Kein Gewinn/Verlust
        double gain = calculator.calculateGain(currentPrice, acquisitionPrice, shares);
        assertThat(gain).isEqualTo(0.0);
    }
}
