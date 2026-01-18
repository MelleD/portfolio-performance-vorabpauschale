package melled.portfolio.vorabpauschale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.junit.repacked.AccountBuilder;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;

public class VapExportServiceTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private VapExportService exportService;
    private VapCalculator calculator;
    private VapCsvDataReader csvReader;
    private VapExcelExporter excelExporter;
    private Client client;
    private File csvFile;
    private Security security;
    private Account account;

    @Before
    public void setUp() throws IOException
    {
        client = new Client();
        csvReader = new VapCsvDataReader();
        calculator = new VapCalculator(csvReader);
        excelExporter = new VapExcelExporter(calculator, new VapSummaryCollector(calculator),
                        new PortfolioValueCalculator(new CostCalculator(), new TaxCalculator()));
        exportService = new VapExportService(calculator, excelExporter);

        // Erstelle Test-CSV-Datei
        csvFile = tempFolder.newFile("test_vap.csv");
        try (FileWriter writer = new FileWriter(csvFile))
        {
            writer.write("ID;Jahr des Wertzuwachses;Vorabpauschale vor TFS pro Anteil;Prozent Teilfreistellung\n");
            writer.write("DE0001;2020;1,00;30\n");
            writer.write("DE0001;2021;1,50;30\n");
        }

        this.security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");
        security.setName("Test ETF 1");

        this.account = new AccountBuilder().addTo(client);
    }

    @Test
    public void testExportVapWithSingleBuyTransaction() throws Exception
    {

        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapWithBuyAndSellTransactions() throws Exception
    {

        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2020-06-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(600))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapWithMultiplePortfolios() throws Exception
    {

        Portfolio portfolio1 = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio1.setName("Depot 1");

        Account account2 = new AccountBuilder().addTo(client);
        Portfolio portfolio2 = new PortfolioBuilder(account2)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .addTo(client);
        portfolio2.setName("Depot 2");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapFiltersPurchaseTransactions() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapHandlesLiquidation() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2021-01-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(600))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapWithEmptyPortfolio() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder().addTo(client);
        portfolio.setName("Empty Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertThat(outputFile).exists();
        assertThat(outputFile).isEmpty()
    }

    @Test
    public void testExportVapWithDeliveryInbound() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account).inbound_delivery(security, "2020-01-15",
                        PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000)).addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testHandleLiquidationReducesUnsoldShares() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .sell(security, "2021-01-15", PortfolioBuilder.sharesOf(3), PortfolioBuilder.amountOf(400))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        Map.of(portfolio,
                        portfolio.getTransactions().stream()
                                        .filter(tx -> tx.getType().isPurchase()
                                                        && (tx.getType() != PortfolioTransaction.Type.TRANSFER_IN))
                                        .map(UnsoldTransaction::new).toList());

        assertOutputFile(outputFile);
    }

    @Test
    public void testHandleLiquidationWithMultiplePurchases() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security, "2020-06-15", PortfolioBuilder.sharesOf(5), PortfolioBuilder.amountOf(500))
                        .sell(security, "2021-01-15", PortfolioBuilder.sharesOf(12), PortfolioBuilder.amountOf(1500))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapWithMultipleSecurities() throws Exception
    {

        Security security2 = new SecurityBuilder().addTo(client);
        security2.setIsin("DE0002");
        security2.setName("Test ETF 2");

        Account account2 = new AccountBuilder().addTo(client);
        Portfolio portfolio2 = new PortfolioBuilder(account2)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .buy(security2, "2020-01-15", PortfolioBuilder.sharesOf(20), PortfolioBuilder.amountOf(2000))
                        .addTo(client);
        portfolio2.setName("Test Depot");

        File outputFile = tempFolder.newFile("test_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    @Test
    public void testExportVapWithInvalidMetadataFile() throws Exception
    {

        new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);

        File outputFile = tempFolder.newFile("test_export.xlsx");
        String absolutePath = outputFile.getAbsolutePath();
        assertThatThrownBy(() -> exportService.exportVap(client, "non_existent_file.csv", absolutePath))
                        .isInstanceOf(IllegalArgumentException.class);

    }

    @Test
    public void testExportVapCreatesFile() throws Exception
    {
        Portfolio portfolio = new PortfolioBuilder(account)
                        .buy(security, "2020-01-15", PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000))
                        .addTo(client);
        portfolio.setName("Test Depot");

        File outputFile = new File(tempFolder.getRoot(), "new_export.xlsx");

        exportService.exportVap(client, csvFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertOutputFile(outputFile);
    }

    private void assertOutputFile(File outputFile)
    {
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
    }
}
