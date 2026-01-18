package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import melled.portfolio.vorabpauschale.model.VapMetadata;
import melled.portfolio.vorabpauschale.service.VapCalculator.VapEntry;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction.Type;
import name.abuchen.portfolio.model.Security;

public class VapCalculatorTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private VapCalculator calculator;
    private VapCsvDataReader csvReader;
    private Client client;
    private File csvFile;
    private TestBuilder testBuilder;

    @Before
    public void setUp() throws IOException
    {
        client = new Client();
        csvReader = new VapCsvDataReader();
        calculator = new VapCalculator(csvReader);

        // Erstelle Test-CSV-Datei
        csvFile = tempFolder.newFile("test_vap.csv");
        try (FileWriter writer = new FileWriter(csvFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("DE0001;2020;0,50;30\n");
            writer.write("DE0001;2021;0,75;30\n");
            writer.write("DE0001;2022;1,00;30\n");
            writer.write("DE0002;2021;0,60;15\n");
            writer.write("DE0002;2022;0,80;15\n");
        }

        calculator.initializeVapData(csvFile.getAbsolutePath());

        testBuilder = new TestBuilder(client);
    }

    @Test
    public void testInitializeVapData()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0002");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        Set<VapMetadata> expectedMetadata = Set.of(new VapMetadata("DE0001", 2020, 0.50, 30),
                        new VapMetadata("DE0001", 2021, 0.75, 30), new VapMetadata("DE0001", 2022, 1.00, 30));

        assertThat(metadata).isEqualTo(expectedMetadata);

        Set<VapMetadata> metadata2 = calculator.getVapMedatasById(security2);

        Set<VapMetadata> expectedMetadata2 = Set.of(new VapMetadata("DE0002", 2021, 0.6, 15),
                        new VapMetadata("DE0002", 2022, 0.8, 15));

        assertThat(metadata2).isEqualTo(expectedMetadata2);
    }

    @Test
    public void testWrongVapMetadaMinus() throws IOException
    {
        File wknFile = tempFolder.newFile("test_wrong_vap_wkn.csv");
        try (FileWriter writer = new FileWriter(wknFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("123456;2020;0,50;-30\n");
        }

        String absolutePath = wknFile.getAbsolutePath();
        assertThatThrownBy(() -> calculator.initializeVapData(absolutePath))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("TFS-Prozentsatz muss zwischen 0 und 100 liegen: -30");

    }

    @Test
    public void testWrongVapMetadaOver100() throws IOException
    {
        File wknFile = tempFolder.newFile("test_wrong_vap_wkn.csv");
        try (FileWriter writer = new FileWriter(wknFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("123456;2020;0,50;130\n");
        }
        String absolutePath = wknFile.getAbsolutePath();

        assertThatThrownBy(() -> calculator.initializeVapData(absolutePath))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("TFS-Prozentsatz muss zwischen 0 und 100 liegen: 130");

    }

    @Test
    public void testGetVapMedatasByWkn() throws IOException
    {
        // Erstelle CSV mit WKN
        File wknFile = tempFolder.newFile("test_vap_wkn.csv");
        try (FileWriter writer = new FileWriter(wknFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("123456;2020;0,50;30\n");
        }

        calculator.initializeVapData(wknFile.getAbsolutePath());

        Security security = new SecurityBuilder().addTo(client);
        security.setWkn("123456");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        Set<VapMetadata> expectedMetadata = Set.of(new VapMetadata("123456", 2020, 0.50, 30));

        assertThat(metadata).isEqualTo(expectedMetadata);

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("");
        security2.setWkn("123456");

        Set<VapMetadata> metadata2 = calculator.getVapMedatasById(security);

        Set<VapMetadata> expectedMetadata2 = Set.of(new VapMetadata("123456", 2020, 0.50, 30));

        assertThat(metadata2).isEqualTo(expectedMetadata2);
    }

    @Test
    public void testGetVapMedatasByName() throws IOException
    {
        File nameFile = tempFolder.newFile("test_vap_name.csv");
        try (FileWriter writer = new FileWriter(nameFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("Test ETF;2020;0,50;30\n");
        }

        calculator.initializeVapData(nameFile.getAbsolutePath());

        Security security = new SecurityBuilder().addTo(client);
        security.setName("Test ETF");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        Set<VapMetadata> expectedMetadata = Set.of(new VapMetadata("Test ETF", 2020, 0.50, 30));

        assertThat(metadata).isEqualTo(expectedMetadata);

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("");
        security2.setWkn("");
        security2.setWkn("123456");

        Set<VapMetadata> metadata2 = calculator.getVapMedatasById(security);

        Set<VapMetadata> expectedMetadata2 = Set.of(new VapMetadata("Test ETF", 2020, 0.50, 30));

        assertThat(metadata2).isEqualTo(expectedMetadata2);
    }

    @Test
    public void testCalculateVapListForJanuaryPurchase()
    {
        testCalculateVapListForJanuaryPurchase(Type.BUY);
        testCalculateVapListForJanuaryPurchase(Type.DELIVERY_INBOUND);
    }

    public void testCalculateVapListForJanuaryPurchase(Type type)
    {
        UnsoldTransaction unsoldTx = testBuilder.transaction(type);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).containsOnlyKeys(2020, 2021, 2022);

        // Januar-Kauf: 12/12 des Jahres
        assertThat(vapList.get(2020).vap()).isEqualTo(0.50);
        assertThat(vapList.get(2020).tfsPercentage()).isEqualTo(30);

        assertThat(vapList.get(2021).vap()).isEqualTo(0.75);
        assertThat(vapList.get(2021).tfsPercentage()).isEqualTo(30);

        assertThat(vapList.get(2022).vap()).isEqualTo(1.00);
        assertThat(vapList.get(2022).tfsPercentage()).isEqualTo(30);

        assertThat(vapList.get(2023)).isNull();
    }

    @Test
    public void testCalculateVapListForDecemberPurchase()
    {

        testCalculateVapListForDecemberPurchase(Type.BUY);
        testCalculateVapListForDecemberPurchase(Type.DELIVERY_INBOUND);

    }

    public void testCalculateVapListForDecemberPurchase(Type type)
    {

        UnsoldTransaction unsoldTx = testBuilder.transaction("2020-12-15", type);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).containsOnlyKeys(2020, 2021, 2022);

        // Dezember-Kauf: 1/12 des Jahres = (13-12)/12 = 1/12
        double expectedVap = (0.50 * (13 - 12)) / 12.0;
        assertThat(vapList.get(2020).vap()).isEqualTo(expectedVap);

        assertThat(vapList.get(2021).vap()).isEqualTo(0.75);
        assertThat(vapList.get(2021).tfsPercentage()).isEqualTo(30);

        assertThat(vapList.get(2022).vap()).isEqualTo(1.00);
        assertThat(vapList.get(2022).tfsPercentage()).isEqualTo(30);

    }

    @Test
    public void testCalculateVapListForJunePurchase()
    {

        testCalculateVapListForJunePurchase(Type.BUY);
        testCalculateVapListForJunePurchase(Type.DELIVERY_INBOUND);

    }

    public void testCalculateVapListForJunePurchase(Type type)
    {

        UnsoldTransaction unsoldTx = testBuilder.transaction("2020-06-15", type);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).containsOnlyKeys(2020, 2021, 2022);

        // Juni-Kauf (Monat 6): (13-6)/12 = 7/12 des Jahres
        double expectedVap = (0.50 * (13 - 6)) / 12.0;
        assertThat(vapList.get(2020).vap()).isEqualTo(expectedVap);

        assertThat(vapList.get(2021).vap()).isEqualTo(0.75);
        assertThat(vapList.get(2021).tfsPercentage()).isEqualTo(30);

        assertThat(vapList.get(2022).vap()).isEqualTo(1.00);
        assertThat(vapList.get(2022).tfsPercentage()).isEqualTo(30);
    }

    @Test
    public void testCalculateVapListExcludesYearsBeforePurchase()
    {
        testCalculateVapListExcludesYearsBeforePurchase(Type.BUY);
        testCalculateVapListExcludesYearsBeforePurchase(Type.DELIVERY_INBOUND);
    }

    public void testCalculateVapListExcludesYearsBeforePurchase(Type type)
    {
        UnsoldTransaction unsoldTx = testBuilder.transaction("2021-06-15", type);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).hasSize(2);
        assertThat(vapList).doesNotContainKey(2020); // Vor Kaufdatum
        assertThat(vapList).containsOnlyKeys(2021, 2022);
    }

    @Test
    public void testCalculateTotalVap()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security, "2021-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security, "2022-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security, "2022-07-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .inbound_delivery(security, "2020-01-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1000))
                        .inbound_delivery(security, "2020-07-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1000))
                        .sell(security, "2021-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2022-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(1000))
                        .outbound_delivery(security, "2022-01-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1000), 0, 0)
                        .outbound_delivery(security, "2022-07-15", PortfolioBuilder.sharesOf(10),
                                        PortfolioBuilder.amountOf(1000), 0, 0)
                        .addTo(client);

        List<Double> totalVaps2020 = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVap(tx, 2020)).toList();

        assertThat(totalVaps2020).containsExactly(5.0, 0.0, 0.0, 0.0, 5.0, 2.5, 0.0, 0.0, 0.0, 0.0);

        List<Double> totalVaps2021 = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVap(tx, 2021)).toList();

        assertThat(totalVaps2021).containsExactly(7.5, 7.5, 0.0, 0.0, 7.5, 7.5, 1.875, 0.0, 0.0, 0.0);

        List<Double> totalVaps2022 = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVap(tx, 2022)).toList();

        assertThat(totalVaps2022).containsExactly(10.0, 10.0, 10.0, 5.0, 10.0, 10.0, 5.0, 2.5, 10.0, 5.0);

        List<Double> totalVapPerShares = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVapPerShare(tx)).toList();

        assertThat(totalVapPerShares).containsExactly(2.25, 1.75, 1.0, 0.5, 2.25, 2.0, 1.375, 0.5, 1.0, 0.5);
    }

    @Test
    public void testCalculateTotalVapWithBuy()
    {
        testCalculateTotalVapWithBuy(Type.BUY);
        testCalculateTotalVapWithBuy(Type.DELIVERY_INBOUND);
    }

    public void testCalculateTotalVapWithBuy(Type type)
    {
        UnsoldTransaction unsoldTx = testBuilder.transaction(type);

        double totalVap = calculator.calculateTotalVap(unsoldTx, 2020);

        // 10 Anteile * 0.50 VAP = 5.0
        assertThat(totalVap).isEqualTo(5.0);
    }

    @Test
    public void testCalculateTotalVapWithSell()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2020-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        List<Double> totalVaps = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVap(tx, 2020)).toList();

        assertThat(totalVaps).containsExactly(5.0, 1.25);
    }

    @Test
    public void testCalculateTotalVapPerShare()
    {
        testCalculateTotalVapPerShare(Type.BUY);
        testCalculateTotalVapPerShare(Type.DELIVERY_INBOUND);
    }

    public void testCalculateTotalVapPerShare(Type type)
    {
        UnsoldTransaction unsoldTx = testBuilder.transaction(type);

        double totalVapPerShare = calculator.calculateTotalVapPerShare(unsoldTx);

        // Summe: 0.50 + 0.75 + 1.00 = 2.25
        assertThat(totalVapPerShare).isEqualTo(2.25);
    }

    @Test
    public void testCalculateTotalVapPerShareWithSell()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2020-07-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        List<Double> totalVapPerShares = testBuilder.transactions(portfolio).stream()
                        .map(tx -> calculator.calculateTotalVapPerShare(tx)).toList();

        assertThat(totalVapPerShares).containsExactly(2.25, 2.0);
    }

    @Test
    public void testCalculateVapListForSecurityWithoutMetadata()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("UNKNOWN");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isEmpty();
    }

    @Test
    public void testCalculateTotalVapForNonExistentYear()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2021-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        double totalVap = calculator.calculateTotalVap(unsoldTx, 2019); // Jahr
                                                                        // vor
                                                                        // Kauf

        assertThat(totalVap).isZero();
    }

}
