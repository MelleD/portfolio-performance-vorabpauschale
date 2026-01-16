package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import melled.portfolio.vorabpauschale.service.VapSummaryCollector.VapSummaryRow;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;

public class VapSummaryCollectorTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private VapSummaryCollector collector;
    private VapCalculator calculator;
    private VapCsvDataReader csvReader;
    private Client client;
    private File csvFile;

    @Before
    public void setUp() throws IOException
    {
        client = new Client();
        csvReader = new VapCsvDataReader();
        calculator = new VapCalculator(csvReader);
        collector = new VapSummaryCollector(calculator);

        // Erstelle Test-CSV-Datei
        csvFile = tempFolder.newFile("test_vap.csv");
        try (FileWriter writer = new FileWriter(csvFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("DE0001;2020;1,00;30\n");
            writer.write("DE0001;2021;1,50;30\n");
            writer.write("DE0002;2020;0,50;15\n");
            writer.write("DE0002;2021;0,75;15\n");
        }

        calculator.initializeVapData(csvFile.getAbsolutePath());
    }

    /**
     * Hilfsmethode um UnsoldTransactions aus einem Portfolio zu holen
     */
    private List<UnsoldTransaction> getUnsoldTransactions(Portfolio portfolio)
    {

        return portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList();
    }

    @Test
    public void testCollectSummaryWithSinglePortfolio()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).isNotEmpty();

        // Erste Zeile sollte das Wertpapier sein
        VapSummaryRow firstRow = summary.get(0);
        assertThat(firstRow.isin).isEqualTo("DE0001");
        assertThat(firstRow.name).isEqualTo("Test ETF 1");
        assertThat(firstRow.depot).isEqualTo("Broker A");
        assertThat(firstRow.isSumRow).isFalse();
        assertThat(firstRow.isTotalRow).isFalse();
    }

    @Test
    public void testCollectSummaryCalculatesVapCorrectly()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        VapSummaryRow firstRow = summary.get(0);

        // VAP vor TFS: 10 Anteile * 1.00 = 10.0
        assertThat(firstRow.vapBeforeTfs.get(2020)).isCloseTo(10.0, within(0.01));

        // VAP nach TFS: 10.0 - (10.0 * 30%) = 7.0
        assertThat(firstRow.vapAfterTfs.get(2020)).isCloseTo(7.0, within(0.01));

        // VAP vor TFS: 10 Anteile * 1.50 = 15.0
        assertThat(firstRow.vapBeforeTfs.get(2021)).isCloseTo(15.0, within(0.01));

        // VAP nach TFS: 15.0 - (15.0 * 30%) = 10.5
        assertThat(firstRow.vapAfterTfs.get(2021)).isCloseTo(10.5, within(0.01));
    }

    @Test
    public void testCollectSummaryWithMultipleSecurities()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0002");
        security2.setName("Test ETF 2");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security2, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Sollte mindestens 2 Wertpapier-Zeilen enthalten
        long securityRows = summary.stream().filter(r -> !r.isSumRow && !r.isTotalRow && !r.isEmptyRow).count();
        assertThat(securityRows).isEqualTo(2);
    }

    @Test
    public void testCollectSummaryWithMultiplePortfolios()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio1 = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio1.setName("Broker A");

        Portfolio portfolio2 = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .addTo(client);
        portfolio2.setName("Broker B");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio1, portfolio1.getTransactions().stream().map(UnsoldTransaction::new).toList());
        transactions.put(portfolio2, portfolio2.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Sollte zwei verschiedene Broker enthalten
        long brokerARows = summary.stream().filter(r -> "Broker A".equals(r.depot)).count();
        long brokerBRows = summary.stream().filter(r -> "Broker B".equals(r.depot)).count();

        assertThat(brokerARows).isGreaterThan(0);
        assertThat(brokerBRows).isGreaterThan(0);
    }

    @Test
    public void testCollectSummaryIncludesSumRows()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Sollte Summen-Zeile für Broker enthalten
        long sumRows = summary.stream().filter(r -> r.isSumRow).count();
        assertThat(sumRows).isGreaterThan(0);

        VapSummaryRow sumRow = summary.stream().filter(r -> r.isSumRow).findFirst().get();
        assertThat(sumRow.isin).isEqualTo("Summe");
        assertThat(sumRow.depot).isEqualTo("Broker A");
    }

    @Test
    public void testCollectSummaryIncludesTotalRow()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Letzte Zeile sollte Gesamtsumme sein
        VapSummaryRow totalRow = summary.get(summary.size() - 1);
        assertThat(totalRow.isTotalRow).isTrue();
        assertThat(totalRow.isin).isEqualTo("GESAMTSUMME");
    }

    @Test
    public void testCollectSummaryTotalRowSumsCorrectly()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0002");
        security2.setName("Test ETF 2");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security2, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        VapSummaryRow totalRow = summary.get(summary.size() - 1);

        // Security1: 10 * 1.00 = 10.0
        // Security2: 20 * 0.50 = 10.0
        // Total: 20.0
        assertThat(totalRow.vapBeforeTfs.get(2020)).isCloseTo(20.0, within(0.01));
    }

    @Test
    public void testCollectSummaryWithEmptyTransactions()
    {
        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).isEmpty();
    }

    @Test
    public void testCollectSummaryIncludesEmptyRows()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio1 = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio1.setName("Broker A");

        Portfolio portfolio2 = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .addTo(client);
        portfolio2.setName("Broker B");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio1, portfolio1.getTransactions().stream().map(UnsoldTransaction::new).toList());
        transactions.put(portfolio2, portfolio2.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Sollte leere Zeilen zwischen Depots und vor Gesamtsumme enthalten
        long emptyRows = summary.stream().filter(r -> r.isEmptyRow).count();
        assertThat(emptyRows).isGreaterThan(0L);
    }

    @Test
    public void testVapSummaryRowEmptyFactory()
    {
        VapSummaryRow row = VapSummaryRow.empty();
        assertThat(row.isEmptyRow).isTrue();
        assertThat(row.isSumRow).isFalse();
        assertThat(row.isTotalRow).isFalse();
    }

    @Test
    public void testVapSummaryRowSumFactory()
    {
        VapSummaryRow row = VapSummaryRow.sumRow("Test Depot");
        assertThat(row.isSumRow).isTrue();
        assertThat(row.isEmptyRow).isFalse();
        assertThat(row.isTotalRow).isFalse();
        assertThat(row.isin).isEqualTo("Summe");
        assertThat(row.depot).isEqualTo("Test Depot");
    }

    @Test
    public void testVapSummaryRowTotalFactory()
    {
        VapSummaryRow row = VapSummaryRow.totalRow();
        assertThat(row.isTotalRow).isTrue();
        assertThat(row.isEmptyRow).isFalse();
        assertThat(row.isSumRow).isFalse();
        assertThat(row.isin).isEqualTo("GESAMTSUMME");
    }

    @Test
    public void testCollectSummaryGroupsBySameSecurityInSameBroker()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security1, "2020-06-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(500))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        // Sollte nur eine Zeile für das Wertpapier geben (aggregiert)
        long securityRows = summary.stream()
                        .filter(r -> !r.isSumRow && !r.isTotalRow && !r.isEmptyRow && "DE0001".equals(r.isin)).count();
        assertThat(securityRows).isEqualTo(1);

        VapSummaryRow row = summary.stream()
                        .filter(r -> !r.isSumRow && !r.isTotalRow && !r.isEmptyRow && "DE0001".equals(r.isin))
                        .findFirst().get();

        // Erste Transaktion: 10 * 1.00 = 10.0
        // Zweite Transaktion: 5 * 1.00 * (13-6)/12 = 5 * 1.00 * 7/12 = 2.917
        // Total: ~12.917
        assertThat(row.vapBeforeTfs.get(2020)).isGreaterThan(12.0);
        assertThat(row.vapBeforeTfs.get(2020)).isLessThan(13.0);
    }

    @Test
    public void testCollectSummarySortsByBrokerAndIsin()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0002");
        security1.setName("Test ETF 2");

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0001");
        security2.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security2, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, portfolio.getTransactions().stream().map(UnsoldTransaction::new).toList());

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        List<VapSummaryRow> securityRows = summary.stream().filter(r -> !r.isSumRow && !r.isTotalRow && !r.isEmptyRow)
                        .toList();

        // Sollte nach ISIN sortiert sein
        assertThat(securityRows.get(0).isin).isEqualTo("DE0001");
        assertThat(securityRows.get(1).isin).isEqualTo("DE0002");
    }
}
