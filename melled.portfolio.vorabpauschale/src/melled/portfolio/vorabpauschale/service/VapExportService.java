package melled.portfolio.vorabpauschale.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction.Type;
import name.abuchen.portfolio.model.PortfolioTransferEntry;

/**
 * Hauptklasse für VAP Excel Export aus Portfolio Performance Client.
 */
@Creatable
public class VapExportService
{

    private VapExcelExporter vapExcelExporter;

    @Inject
    public VapExportService(VapCalculator vapCalculator, VapExcelExporter vapExcelExporter)
    {
        this.vapExcelExporter = vapExcelExporter;
    }

    /**
     * Exportiert VAP-Daten aus einem Portfolio Performance Client nach Excel.
     *
     * @param client
     *            Portfolio Performance Client
     * @param metadataFile
     *            Pfad zur ETF-Metadaten CSV (etf_metadaten.csv)
     * @param outputFile
     *            Pfad zur Ausgabe-Excel-Datei
     * @throws Exception
     *             bei Fehlern
     */
    public void exportVap(Client client, String metadataFile, String outputFile) throws Exception
    {

        Map<Portfolio, List<UnsoldTransaction>> transactions = client.getPortfolios().stream().collect(Collectors.toMap(
                        Function.identity(),
                        portfolio -> PortfolioTransaction.sortByDate(portfolio.getTransactions()).stream()
                                        .filter(tx -> tx.getType().isPurchase() && (tx.getType() != Type.TRANSFER_IN))
                                        .map(UnsoldTransaction::new).collect(Collectors.toList())));

        Map<Portfolio, List<PortfolioTransaction>> mappedTransactions = client.getPortfolios().stream()
                        .collect(Collectors.toMap(Function.identity(),
                                        portfolio -> PortfolioTransaction.sortByDate(portfolio.getTransactions())));

        for (Entry<Portfolio, List<PortfolioTransaction>> portfolio : mappedTransactions.entrySet())
        {

            portfolio.getValue().stream().filter(tx -> tx.getType().isLiquidation())
                            .forEach(tx -> handleLiquidation(portfolio.getKey(), tx, transactions));

        }

        vapExcelExporter.export(metadataFile, outputFile, transactions);

    }

    private void handleLiquidation(Portfolio portfolio, PortfolioTransaction tx,
                    Map<Portfolio, List<UnsoldTransaction>> transactions)
    {

        Portfolio fromPortfolio = portfolio;

        if (tx.getCrossEntry() instanceof PortfolioTransferEntry entry)
        {
            fromPortfolio = entry.getSourcePortfolio();

        }

        if (tx.getCrossEntry() instanceof BuySellEntry entry)
        {
            fromPortfolio = entry.getPortfolio();

        }

        List<UnsoldTransaction> fromTransactions = transactions.get(fromPortfolio);

        double sharesToTransfer = UnsoldTransaction.calcluateShare(tx);
        if (sharesToTransfer <= 0)
        { return; }

        List<UnsoldTransaction> toTransfer = new ArrayList<>();

        for (UnsoldTransaction unsoldTx : fromTransactions)
        {
            if (sharesToTransfer <= 0)
            {
                break;
            }
            if (!unsoldTx.getTransaction().getSecurity().equals(tx.getSecurity()))
            {
                continue;
            }
            double unsoldShare = unsoldTx.getUnsoldShare();
            if (unsoldShare <= sharesToTransfer)
            {
                toTransfer.add(unsoldTx);
                sharesToTransfer -= unsoldShare;
            }
            else
            {
                toTransfer.add(new UnsoldTransaction(unsoldTx.getTransaction(), sharesToTransfer));
                unsoldTx.reduzeUnsoldShare(sharesToTransfer);
                sharesToTransfer = 0;
            }
        }

        if (tx.getCrossEntry() instanceof PortfolioTransferEntry crossTx)
        {
            Portfolio toPortfolio = crossTx.getTargetPortfolio();
            transactions.get(toPortfolio).addAll(toTransfer);
            transactions.get(fromPortfolio).removeAll(toTransfer);

        }

    }

}
