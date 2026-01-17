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
        assertThat(firstRow.vapBeforeTfs.get(2020)).isEqualTo(10.0);

        // VAP nach TFS: 10.0 - (10.0 * 30%) = 7.0
        assertThat(firstRow.vapAfterTfs.get(2020)).isEqualTo(7.0);

        // VAP vor TFS: 10 Anteile * 1.50 = 15.0
        assertThat(firstRow.vapBeforeTfs.get(2021)).isEqualTo(15.0);

        // VAP nach TFS: 15.0 - (15.0 * 30%) = 10.5
        assertThat(firstRow.vapAfterTfs.get(2021)).isEqualTo(10.5);
        assertThat(firstRow.isTotalRow).isFalse();
        assertThat(firstRow.isEmptyRow).isFalse();
        assertThat(firstRow.depot).isEqualTo("Broker A");
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

        VapSummaryRow firstRow = summary.get(0);

        assertThat(firstRow.vapBeforeTfs.get(2020)).isEqualTo(10.0);
        assertThat(firstRow.vapAfterTfs.get(2020)).isEqualTo(7.0);
        assertThat(firstRow.vapBeforeTfs.get(2021)).isEqualTo(15.0);
        assertThat(firstRow.vapAfterTfs.get(2021)).isEqualTo(10.5);

        VapSummaryRow secondRow = summary.get(0);
        assertThat(secondRow.vapBeforeTfs.get(2020)).isEqualTo(10.0);
        assertThat(secondRow.vapAfterTfs.get(2020)).isEqualTo(7.0);
        assertThat(secondRow.vapBeforeTfs.get(2021)).isEqualTo(15.0);
        assertThat(secondRow.vapAfterTfs.get(2021)).isEqualTo(10.5);

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

    @Test
    public void testCollectSummaryWithBuyAndSell()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .sell(security1, "2021-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(600))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).hasSize(4); // Security Row, Sum Row, Empty Row,
                                        // Total Row

        VapSummaryRow securityRow = summary.get(0);
        assertThat(securityRow.isin).isEqualTo("DE0001");
        assertThat(securityRow.name).isEqualTo("Test ETF 1");
        assertThat(securityRow.depot).isEqualTo("Broker A");
        assertThat(securityRow.isSumRow).isFalse();
        assertThat(securityRow.isTotalRow).isFalse();
        assertThat(securityRow.isEmptyRow).isFalse();

        // 2020: 20 Anteile * 1.00 = 20.0
        assertThat(securityRow.vapBeforeTfs.get(2020)).isEqualTo(20.0);
        assertThat(securityRow.vapAfterTfs.get(2020)).isEqualTo(14.0); // 20 -
                                                                       // (20 *
                                                                       // 0.3)

        // 2021: 20 Anteile * 1.50 = 30.0, Verkauf von 5 Anteilen im Juli: 5 *
        // 1.50 * (13-7)/12 = 3.75
        assertThat(securityRow.vapBeforeTfs.get(2021)).isEqualTo(33.75);
        assertThat(securityRow.vapAfterTfs.get(2021)).isEqualTo(23.625); // 33.75
                                                                         // -
                                                                         // (33.75
                                                                         // *
                                                                         // 0.3)

        VapSummaryRow sumRow = summary.get(1);
        assertThat(sumRow.isSumRow).isTrue();
        assertThat(sumRow.isin).isEqualTo("Summe");
        assertThat(sumRow.depot).isEqualTo("Broker A");
        assertThat(sumRow.vapBeforeTfs.get(2020)).isEqualTo(20.0);
        assertThat(sumRow.vapAfterTfs.get(2020)).isEqualTo(14.0);

        VapSummaryRow emptyRow = summary.get(2);
        assertThat(emptyRow.isEmptyRow).isTrue();

        VapSummaryRow totalRow = summary.get(3);
        assertThat(totalRow.isTotalRow).isTrue();
        assertThat(totalRow.isin).isEqualTo("GESAMTSUMME");
        assertThat(totalRow.vapBeforeTfs.get(2020)).isEqualTo(20.0);
        assertThat(totalRow.vapBeforeTfs.get(2021)).isEqualTo(33.75);
    }

    @Test
    public void testCollectSummaryWithInboundDelivery()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder().inbound_delivery(security1, "2020-01-15",
                        PortfolioBuilder.sharesOf(15), PortfolioBuilder.amountOf(1500)).addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).hasSize(4); // Security Row, Sum Row, Empty Row,
                                        // Total Row

        VapSummaryRow securityRow = summary.get(0);
        assertThat(securityRow.isin).isEqualTo("DE0001");
        assertThat(securityRow.name).isEqualTo("Test ETF 1");
        assertThat(securityRow.depot).isEqualTo("Broker A");
        assertThat(securityRow.isSumRow).isFalse();
        assertThat(securityRow.isTotalRow).isFalse();

        // 2020: 15 Anteile * 1.00 = 15.0
        assertThat(securityRow.vapBeforeTfs.get(2020)).isEqualTo(15.0);
        assertThat(securityRow.vapAfterTfs.get(2020)).isEqualTo(10.5); // 15 -
                                                                       // (15 *
                                                                       // 0.3)

        // 2021: 15 Anteile * 1.50 = 22.5
        assertThat(securityRow.vapBeforeTfs.get(2021)).isEqualTo(22.5);
        assertThat(securityRow.vapAfterTfs.get(2021)).isEqualTo(15.75); // 22.5
                                                                        // -
                                                                        // (22.5
                                                                        // *
                                                                        // 0.3)
    }

    @Test
    public void testCollectSummaryWithOutboundDelivery()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .outbound_delivery(security1, "2021-03-15", PortfolioBuilder.sharesOf(5),
                                        PortfolioBuilder.amountOf(600), 0, 0)
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).hasSize(4);

        VapSummaryRow securityRow = summary.get(0);
        assertThat(securityRow.isin).isEqualTo("DE0001");
        assertThat(securityRow.name).isEqualTo("Test ETF 1");
        assertThat(securityRow.depot).isEqualTo("Broker A");

        // 2020: 20 Anteile * 1.00 = 20.0
        assertThat(securityRow.vapBeforeTfs.get(2020)).isEqualTo(20.0);
        assertThat(securityRow.vapAfterTfs.get(2020)).isEqualTo(14.0);

        // 2021: Kauf: 20 * 1.50 = 30.0, Auslieferung: 5 * 1.50 * (13-3)/12 =
        // 6.25
        assertThat(securityRow.vapBeforeTfs.get(2021)).isEqualTo(36.25);
        assertThat(securityRow.vapAfterTfs.get(2021)).isEqualTo(25.375); // 36.25
                                                                         // -
                                                                         // (36.25
                                                                         // *
                                                                         // 0.3)
    }

    @Test
    public void testCollectSummaryWithPortfolioTransfer()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio1 = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .outbound_delivery(security1, "2020-06-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1100), 0, 0)
                        .addTo(client);
        portfolio1.setName("Broker A");

        Portfolio portfolio2 = new PortfolioBuilder().inbound_delivery(security1, "2020-06-15",
                        PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1100)).addTo(client);
        portfolio2.setName("Broker B");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio1, getUnsoldTransactions(portfolio1));
        transactions.put(portfolio2, getUnsoldTransactions(portfolio2));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).hasSize(7); // Broker A Row, Broker A Sum, Empty,
                                        // Broker B Row, Broker B Sum, Empty,
                                        // Total

        // Broker A
        VapSummaryRow brokerARow = summary.stream()
                        .filter(r -> "Broker A".equals(r.depot) && !r.isSumRow && !r.isTotalRow && !r.isEmptyRow)
                        .findFirst().orElseThrow();

        assertThat(brokerARow.isin).isEqualTo("DE0001");
        assertThat(brokerARow.name).isEqualTo("Test ETF 1");
        assertThat(brokerARow.depot).isEqualTo("Broker A");

        // Broker A 2020: Kauf 20 * 1.00 = 20.0, Auslieferung 10 * 1.00 *
        // (13-6)/12 = 5.833...
        assertThat(brokerARow.vapBeforeTfs.get(2020)).isEqualTo(25.833333333333336);
        assertThat(brokerARow.vapAfterTfs.get(2020)).isEqualTo(18.083333333333336); // -
                                                                                    // 30%

        // Broker B
        VapSummaryRow brokerBRow = summary.stream()
                        .filter(r -> "Broker B".equals(r.depot) && !r.isSumRow && !r.isTotalRow && !r.isEmptyRow)
                        .findFirst().orElseThrow();

        assertThat(brokerBRow.isin).isEqualTo("DE0001");
        assertThat(brokerBRow.name).isEqualTo("Test ETF 1");
        assertThat(brokerBRow.depot).isEqualTo("Broker B");

        // Broker B 2020: Einlieferung 10 Anteile ab Juni: 10 * 1.00 * (13-6)/12
        // = 5.833...
        assertThat(brokerBRow.vapBeforeTfs.get(2020)).isEqualTo(5.833333333333334);
        assertThat(brokerBRow.vapAfterTfs.get(2020)).isEqualTo(4.083333333333334); // -
                                                                                   // 30%

        // Total Row
        VapSummaryRow totalRow = summary.stream().filter(r -> r.isTotalRow).findFirst().orElseThrow();

        assertThat(totalRow.isin).isEqualTo("GESAMTSUMME");
        assertThat(totalRow.vapBeforeTfs.get(2020)).isEqualTo(31.66666666666667);
        assertThat(totalRow.vapAfterTfs.get(2020)).isEqualTo(22.16666666666667);
    }

    @Test
    public void testCollectSummaryWithMixedTransactions()
    {
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("Test ETF 1");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .inbound_delivery(security1, "2020-07-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1100))
                        .sell(security1, "2021-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(600))
                        .addTo(client);
        portfolio.setName("Broker A");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolio, getUnsoldTransactions(portfolio));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).hasSize(4);

        VapSummaryRow securityRow = summary.get(0);
        assertThat(securityRow.isin).isEqualTo("DE0001");
        assertThat(securityRow.name).isEqualTo("Test ETF 1");
        assertThat(securityRow.depot).isEqualTo("Broker A");
        assertThat(securityRow.isSumRow).isFalse();
        assertThat(securityRow.isTotalRow).isFalse();

        // 2020: Kauf 10 * 1.00 = 10.0, Inbound 10 * 1.00 * (13-7)/12 = 5.0
        assertThat(securityRow.vapBeforeTfs.get(2020)).isEqualTo(15.0);
        assertThat(securityRow.vapAfterTfs.get(2020)).isEqualTo(10.5); // 15.0 -
                                                                       // 30%

        // 2021: Kauf 10 * 1.50 = 15.0, Inbound 10 * 1.50 = 15.0, Verkauf 5 *
        // 1.50 * (13-7)/12 = 3.75
        assertThat(securityRow.vapBeforeTfs.get(2021)).isEqualTo(33.75);
        assertThat(securityRow.vapAfterTfs.get(2021)).isEqualTo(23.625); // 33.75
                                                                         // -
                                                                         // 30%

        VapSummaryRow totalRow = summary.get(3);
        assertThat(totalRow.isTotalRow).isTrue();
        assertThat(totalRow.vapBeforeTfs.get(2020)).isEqualTo(15.0);
        assertThat(totalRow.vapBeforeTfs.get(2021)).isEqualTo(33.75);
    }

    @Test
    public void testCollectSummaryCompleteUseCase()
    {
        // Kompletter Realitätsnaher Use Case
        Security security1 = new SecurityBuilder().addTo(client);
        security1.setIsin("DE0001");
        security1.setName("iShares Core MSCI World");

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0002");
        security2.setName("Vanguard FTSE All-World");

        // Broker A: Hauptdepot
        Portfolio portfolioA = new PortfolioBuilder()
                        .buy(security1, "2020-01-15", PortfolioBuilder.sharesOf(100), PortfolioBuilder.amountOf(10000))
                        .buy(security2, "2020-06-15", PortfolioBuilder.sharesOf(50), PortfolioBuilder.amountOf(5000))
                        .sell(security1, "2021-09-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2500))
                        .addTo(client);
        portfolioA.setName("Trade Republic");

        // Broker B: Zweitdepot mit Übertrag
        Portfolio portfolioB = new PortfolioBuilder()
                        .inbound_delivery(security1, "2020-12-15", PortfolioBuilder.sharesOf(30),
                                        PortfolioBuilder.amountOf(3500))
                        .buy(security2, "2021-03-15", PortfolioBuilder.sharesOf(25), PortfolioBuilder.amountOf(2500))
                        .outbound_delivery(security2, "2021-11-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1200), 0, 0)
                        .addTo(client);
        portfolioB.setName("Scalable Capital");

        Map<Portfolio, List<UnsoldTransaction>> transactions = new HashMap<>();
        transactions.put(portfolioA, getUnsoldTransactions(portfolioA));
        transactions.put(portfolioB, getUnsoldTransactions(portfolioB));

        List<VapSummaryRow> summary = collector.collectSummary(transactions);

        assertThat(summary).isNotNull();
        assertThat(summary).isNotEmpty();

        // Struktur-Validierung
        long securityRowCount = summary.stream().filter(r -> !r.isSumRow && !r.isTotalRow && !r.isEmptyRow).count();
        assertThat(securityRowCount).isEqualTo(4); // 2 Securities in 2 Brokers

        long sumRowCount = summary.stream().filter(r -> r.isSumRow).count();
        assertThat(sumRowCount).isEqualTo(2); // Eine Summe pro Broker

        long emptyRowCount = summary.stream().filter(r -> r.isEmptyRow).count();
        assertThat(emptyRowCount).isEqualTo(2); // Zwischen Brokern und vor
                                                // Total

        long totalRowCount = summary.stream().filter(r -> r.isTotalRow).count();
        assertThat(totalRowCount).isEqualTo(1);

        // Scalable Capital - DE0001
        VapSummaryRow sc1 = summary.stream()
                        .filter(r -> "Scalable Capital".equals(r.depot) && "DE0001".equals(r.isin) && !r.isSumRow)
                        .findFirst().orElseThrow();

        assertThat(sc1.name).isEqualTo("iShares Core MSCI World");
        // 2020: Inbound 30 * 1.00 * (13-12)/12 = 2.5
        assertThat(sc1.vapBeforeTfs.get(2020)).isEqualTo(2.5);
        assertThat(sc1.vapAfterTfs.get(2020)).isEqualTo(1.75); // - 30%
        // 2021: Inbound 30 * 1.50 = 45.0
        assertThat(sc1.vapBeforeTfs.get(2021)).isEqualTo(45.0);
        assertThat(sc1.vapAfterTfs.get(2021)).isEqualTo(31.5); // - 30%

        // Scalable Capital - DE0002
        VapSummaryRow sc2 = summary.stream()
                        .filter(r -> "Scalable Capital".equals(r.depot) && "DE0002".equals(r.isin) && !r.isSumRow)
                        .findFirst().orElseThrow();

        assertThat(sc2.name).isEqualTo("Vanguard FTSE All-World");
        assertThat(sc2.vapBeforeTfs.get(2020)).isZero();
        // 2021: Buy 25 * 0.75 * (13-3)/12 = 15.625, Outbound 10 * 0.75 *
        // (13-11)/12 = 1.25
        assertThat(sc2.vapBeforeTfs.get(2021)).isEqualTo(16.875);
        assertThat(sc2.vapAfterTfs.get(2021)).isEqualTo(14.34375); // - 15%

        // Trade Republic - DE0001
        VapSummaryRow tr1 = summary.stream()
                        .filter(r -> "Trade Republic".equals(r.depot) && "DE0001".equals(r.isin) && !r.isSumRow)
                        .findFirst().orElseThrow();

        assertThat(tr1.name).isEqualTo("iShares Core MSCI World");
        // 2020: Buy 100 * 1.00 = 100.0
        assertThat(tr1.vapBeforeTfs.get(2020)).isEqualTo(100.0);
        assertThat(tr1.vapAfterTfs.get(2020)).isEqualTo(70.0); // - 30%
        // 2021: Buy 100 * 1.50 = 150.0, Sell 20 * 1.50 * (13-9)/12 = 10.0
        assertThat(tr1.vapBeforeTfs.get(2021)).isEqualTo(160.0);
        assertThat(tr1.vapAfterTfs.get(2021)).isEqualTo(112.0); // - 30%

        // Trade Republic - DE0002
        VapSummaryRow tr2 = summary.stream()
                        .filter(r -> "Trade Republic".equals(r.depot) && "DE0002".equals(r.isin) && !r.isSumRow)
                        .findFirst().orElseThrow();

        assertThat(tr2.name).isEqualTo("Vanguard FTSE All-World");
        // 2020: Buy 50 * 0.50 * (13-6)/12 = 14.583...
        assertThat(tr2.vapBeforeTfs.get(2020)).isEqualTo(14.583333333333334);
        assertThat(tr2.vapAfterTfs.get(2020)).isEqualTo(12.395833333333334); // -
                                                                             // 15%
        // 2021: Buy 50 * 0.75 = 37.5
        assertThat(tr2.vapBeforeTfs.get(2021)).isEqualTo(37.5);
        assertThat(tr2.vapAfterTfs.get(2021)).isEqualTo(31.875); // - 15%

        // Summen validieren
        VapSummaryRow scSum = summary.stream().filter(r -> "Scalable Capital".equals(r.depot) && r.isSumRow).findFirst()
                        .orElseThrow();

        assertThat(scSum.isin).isEqualTo("Summe");
        assertThat(scSum.depot).isEqualTo("Scalable Capital");
        assertThat(scSum.vapBeforeTfs.get(2020)).isEqualTo(2.5);
        assertThat(scSum.vapBeforeTfs.get(2021)).isEqualTo(61.875); // 45.0 +
                                                                    // 16.875
        assertThat(scSum.vapAfterTfs.get(2021)).isEqualTo(45.84375); // 31.5 +
                                                                     // 14.34375

        VapSummaryRow trSum = summary.stream().filter(r -> "Trade Republic".equals(r.depot) && r.isSumRow).findFirst()
                        .orElseThrow();

        assertThat(trSum.isin).isEqualTo("Summe");
        assertThat(trSum.vapBeforeTfs.get(2020)).isEqualTo(114.58333333333333); // 100.0
                                                                                // +
                                                                                // 14.583...
        assertThat(trSum.vapBeforeTfs.get(2021)).isEqualTo(197.5); // 160.0 +
                                                                   // 37.5

        // Gesamtsumme validieren
        VapSummaryRow total = summary.stream().filter(r -> r.isTotalRow).findFirst().orElseThrow();

        assertThat(total.isin).isEqualTo("GESAMTSUMME");
        assertThat(total.vapBeforeTfs.get(2020)).isEqualTo(117.08333333333333); // 2.5
                                                                                // +
                                                                                // 114.583...
        assertThat(total.vapAfterTfs.get(2020)).isEqualTo(84.14583333333333); // 1.75
                                                                              // +
                                                                              // 82.395...
        assertThat(total.vapBeforeTfs.get(2021)).isEqualTo(259.375); // 61.875 +
                                                                     // 197.5
        assertThat(total.vapAfterTfs.get(2021)).isEqualTo(189.71875); // 45.84375
                                                                      // +
                                                                      // 143.875
    }
}
