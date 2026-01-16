package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import name.abuchen.portfolio.model.Security;

public class VapCalculatorTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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
    }

    @Test
    public void testInitializeVapData()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        assertThat(metadata).isNotNull();
        assertThat(metadata).hasSize(3);
    }

    @Test
    public void testCalculateVapListForJanuaryPurchase()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isNotNull();
        assertThat(vapList).containsKeys(2020, 2021, 2022);

        // Januar-Kauf: 12/12 des Jahres
        assertThat(vapList.get(2020).vap()).isCloseTo(0.50, within(0.01));
        assertThat(vapList.get(2020).tfsPercentage()).isEqualTo(30);
    }

    @Test
    public void testCalculateVapListForDecemberPurchase()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-12-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isNotNull();
        assertThat(vapList).containsKey(2020);

        // Dezember-Kauf: 1/12 des Jahres = (13-12)/12 = 1/12
        double expectedVap = (0.50 * (13 - 12)) / 12.0;
        assertThat(vapList.get(2020).vap()).isCloseTo(expectedVap, within(0.01));
    }

    @Test
    public void testCalculateVapListForJunePurchase()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-06-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isNotNull();
        assertThat(vapList).containsKey(2020);

        // Juni-Kauf (Monat 6): (13-6)/12 = 7/12 des Jahres
        double expectedVap = (0.50 * (13 - 6)) / 12.0;
        assertThat(vapList.get(2020).vap()).isCloseTo(expectedVap, within(0.01));

        // Volle Jahre danach
        assertThat(vapList.get(2021).vap()).isCloseTo(0.75, within(0.01));
        assertThat(vapList.get(2022).vap()).isCloseTo(1.00, within(0.01));
    }

    @Test
    public void testCalculateVapListExcludesYearsBeforePurchase()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2021-06-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isNotNull();
        assertThat(vapList).hasSize(2);
        assertThat(vapList).doesNotContainKey(2020); // Vor Kaufdatum
        assertThat(vapList).containsKeys(2021, 2022);
    }

    @Test
    public void testCalculateTotalVap()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        double totalVap = calculator.calculateTotalVap(unsoldTx, 2020);

        // 10 Anteile * 0.50 VAP = 5.0
        assertThat(totalVap).isCloseTo(5.0, within(0.01));
    }

    @Test
    public void testCalculateTotalVapPerShare()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        double totalVapPerShare = calculator.calculateTotalVapPerShare(unsoldTx);

        // Summe: 0.50 + 0.75 + 1.00 = 2.25
        assertThat(totalVapPerShare).isCloseTo(2.25, within(0.01));
    }

    @Test
    public void testGetVapMedatasByIsin()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        assertThat(metadata).isNotNull();
        assertThat(metadata).hasSize(3);
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
        security.setIsin("");
        security.setWkn("123456");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        assertThat(metadata).isNotNull();
        assertThat(metadata).hasSize(1);
    }

    @Test
    public void testGetVapMedatasByName() throws IOException
    {
        // Erstelle CSV mit Name
        File nameFile = tempFolder.newFile("test_vap_name.csv");
        try (FileWriter writer = new FileWriter(nameFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("Test ETF;2020;0,50;30\n");
        }

        calculator.initializeVapData(nameFile.getAbsolutePath());

        Security security = new SecurityBuilder().addTo(client);
        security.setName("Test ETF");
        security.setIsin("");
        security.setWkn("");

        Set<VapMetadata> metadata = calculator.getVapMedatasById(security);

        assertThat(metadata).isNotNull();
        assertThat(metadata).hasSize(1);
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

        assertThat(vapList).isNotNull();
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

        assertThat(totalVap).isCloseTo(0.0, within(0.01));
    }

    @Test
    public void testVapEntryRecord()
    {
        VapEntry entry = new VapEntry(1.5, 30);

        assertThat(entry.vap()).isCloseTo(1.5, within(0.01));
        assertThat(entry.tfsPercentage()).isEqualTo(30);
    }

    @Test
    public void testDifferentTfsPercentages()
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0002");

        Portfolio portfolio = new PortfolioBuilder()
                        .buy(security, "2021-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        UnsoldTransaction unsoldTx = new UnsoldTransaction(tx);

        Map<Integer, VapEntry> vapList = calculator.calculateVapList(unsoldTx);

        assertThat(vapList).isNotNull();
        assertThat(vapList.get(2021).tfsPercentage()).isEqualTo(15);
        assertThat(vapList.get(2022).tfsPercentage()).isEqualTo(15);
    }
}
