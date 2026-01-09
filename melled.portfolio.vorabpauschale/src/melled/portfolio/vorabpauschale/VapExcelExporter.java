package melled.portfolio.vorabpauschale;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import melled.portfolio.vorabpauschale.VapCalculator.VapEntry;
import melled.portfolio.vorabpauschale.VapSummaryCollector.VapSummaryRow;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;

/**
 * Exportiert VAP-Daten nach Excel.
 */
public class VapExcelExporter
{
    private final Client client;
    private final VapCalculator vapCalculator;
    private final List<VapSummaryRow> summaryRows;
    private final Set<Integer> allYears;

    public VapExcelExporter(Client client, VapCalculator vapCalculator, List<VapSummaryRow> summaryRows)
    {
        this.client = client;
        this.vapCalculator = vapCalculator;
        this.summaryRows = summaryRows;
        this.allYears = extractAllYears(summaryRows);
    }

    private Set<Integer> extractAllYears(List<VapSummaryRow> rows)
    {
        Set<Integer> years = new TreeSet<>();
        for (VapSummaryRow row : rows)
        {
            years.addAll(row.vapBeforeTfs.keySet());
            years.addAll(row.vapAfterTfs.keySet());
        }
        return years;
    }

    /**
     * Exportiert VAP-Zusammenfassung und Detail-Sheets nach Excel.
     * 
     * @param outputFile
     *            Ausgabedatei
     * @throws IOException
     *             bei Schreibfehlern
     */
    public void export(String outputFile) throws IOException
    {
        try (Workbook workbook = new SXSSFWorkbook())
        {
            createVapSummarySheet(workbook);

            createDetailSheets(workbook);

            try (FileOutputStream fos = new FileOutputStream(outputFile))
            {
                workbook.write(fos);
            }
        }
    }

    /**
     * Erstellt das VAP-Zusammenfassungs-Sheet.
     */
    private void createVapSummarySheet(Workbook workbook)
    {
        Sheet sheet = workbook.createSheet("VAP");

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle moneyStyle = createMoneyStyle(workbook);
        CellStyle sumStyle = createSumStyle(workbook);

        createVapHeaderRow(sheet, headerStyle);

        int rowIndex = 1;
        for (VapSummaryRow summaryRow : summaryRows)
        {
            if (summaryRow.isEmptyRow)
            {
                rowIndex++;
                continue;
            }

            Row row = sheet.createRow(rowIndex++);
            CellStyle dataStyle = summaryRow.isSumRow || summaryRow.isTotalRow ? sumStyle : moneyStyle;

            // ISIN
            createCell(row, 0, summaryRow.isin, null);

            // Name
            createCell(row, 1, summaryRow.name != null ? summaryRow.name : "", null);

            // Depot
            createCell(row, 2, summaryRow.depot != null ? summaryRow.depot : "", null);

            // Jahr-Spalten
            int colIndex = 3;
            for (int year : allYears)
            {
                // vor TFS
                double vapBefore = summaryRow.vapBeforeTfs.getOrDefault(year, 0.0);
                createCell(row, colIndex++, vapBefore, dataStyle);

                // nach TFS
                double vapAfter = summaryRow.vapAfterTfs.getOrDefault(year, 0.0);
                createCell(row, colIndex++, vapAfter, dataStyle);
            }
        }

        adjustVapColumnWidths(sheet);
    }

    /**
     * Erstellt Detail-Sheets für jede Security in jedem Portfolio.
     */
    private void createDetailSheets(Workbook workbook)
    {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle moneyStyle = createMoneyStyle(workbook);
        CellStyle dateStyle = createDateStyle(workbook);
        CellStyle percentStyle = createPercentStyle(workbook);

        for (Portfolio portfolio : client.getPortfolios())
        {
            String broker = portfolio.getName();

            Map<Security, List<PortfolioTransaction>> transactionsBySecurity = new LinkedHashMap<>();
            for (PortfolioTransaction tx : portfolio.getTransactions())
            {
                if (!tx.getType().isPurchase() || tx.getSecurity() == null)
                {
                    continue;
                }

                transactionsBySecurity.computeIfAbsent(tx.getSecurity(), k -> new ArrayList<>()).add(tx);
            }

            for (Map.Entry<Security, List<PortfolioTransaction>> entry : transactionsBySecurity.entrySet())
            {
                Security security = entry.getKey();
                List<PortfolioTransaction> transactions = entry.getValue();

                String isin = security.getIsin() != null ? security.getIsin() : "";
                String sheetName = broker + " " + (isin.isEmpty() ? security.getName() : isin);
                sheetName = sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;

                Sheet sheet = workbook.createSheet(sheetName);
                createDetailSheet(sheet, security, transactions, broker, headerStyle, moneyStyle, dateStyle,
                                percentStyle);
            }
        }
    }

    /**
     * Erstellt ein Detail-Sheet für eine Security.
     */
    private void createDetailSheet(Sheet sheet, Security security, List<PortfolioTransaction> transactions,
                    String broker, CellStyle headerStyle, CellStyle moneyStyle, CellStyle dateStyle,
                    CellStyle percentStyle)
    {

        boolean hasCurrentPrice = security.getCurrencyCode() != null
                        && security.getSecurityPrice(LocalDate.now()) != null;

        Row headerRow = sheet.createRow(0);
        int colIdx = 0;

        createCell(headerRow, colIdx++, "ISIN", headerStyle);
        createCell(headerRow, colIdx++, "Name", headerStyle);
        createCell(headerRow, colIdx++, "Datum Kauf", headerStyle);
        createCell(headerRow, colIdx++, "Anzahl (noch unverkauft)", headerStyle);
        createCell(headerRow, colIdx++, "Anzahl (gekauft)", headerStyle);
        createCell(headerRow, colIdx++, "Gesamtkosten", headerStyle);
        createCell(headerRow, colIdx++, "Kosten pro Anteil", headerStyle);

        Set<Integer> years = new TreeSet<>();
        int tfsPercentage = 0;
        transactions.sort(new Comparator<PortfolioTransaction>()
        {

            @Override
            public int compare(PortfolioTransaction o1, PortfolioTransaction o2)
            {
                return o1.getDateTime().compareTo(o2.getDateTime());
            }
        });
        for (PortfolioTransaction tx : transactions)
        {
            Map<Integer, VapEntry> vapList = vapCalculator.calculateVapList(tx);
            if (!vapList.isEmpty())
            {
                tfsPercentage = vapList.values().iterator().next().tfsPercentage();
            }
            years.addAll(vapList.keySet());
        }

        for (int year : years)
        {
            createCell(headerRow, colIdx++, "VAP " + year + " vor TFS pro Anteil", headerStyle);
        }

        boolean hasVap = !years.isEmpty();
        if (hasVap)
        {
            createCell(headerRow, colIdx++, "Summe VAP vor TFS pro Anteil", headerStyle);
            createCell(headerRow, colIdx++, "Anschaffungspreis inkl. VAP pro Anteil", headerStyle);
        }

        if (hasCurrentPrice)
        {
            createCell(headerRow, colIdx++, "Brutto-Wert", headerStyle);

            String taxableGainHeader = "KESt-pflichtiger Gewinn";
            if (hasVap)
            {
                taxableGainHeader += " nach VAP";
            }
            if (tfsPercentage > 0)
            {
                taxableGainHeader += " nach TFS";
            }
            createCell(headerRow, colIdx++, taxableGainHeader, headerStyle);

            DecimalFormat df = new DecimalFormat("#.##");
            String kest = df.format(getKestFactor() * 100);
            createCell(headerRow, colIdx++, "KESt (" + kest + "%)", headerStyle);
            createCell(headerRow, colIdx++, "Netto-Wert", headerStyle);
            createCell(headerRow, colIdx++, "Steueranteil an Brutto-Auszahlung", headerStyle);
        }

        int rowIdx = 1;
        double cumulativeTaxableGain = 0.0;

        for (PortfolioTransaction tx : transactions)
        {
            Row row = sheet.createRow(rowIdx++);
            colIdx = 0;

            String isin = security.getIsin() != null ? security.getIsin() : "";
            createCell(row, colIdx++, isin, null);
            createCell(row, colIdx++, security.getName(), null);

            LocalDate purchaseDate = tx.getDateTime().toLocalDate();
            Cell dateCell = row.createCell(colIdx++);
            dateCell.setCellValue(purchaseDate);
            dateCell.setCellStyle(dateStyle);

            long shares = tx.getShares();
            double sharesNum = shares / (double) Values.Share.factor();
            createCell(row, colIdx++, sharesNum, null);
            createCell(row, colIdx++, sharesNum, null);

            double totalCost = tx.getGrossValue().getAmount() / (double) Values.Amount.factor();
            createCell(row, colIdx++, totalCost, moneyStyle);

            double costPerShare = totalCost / sharesNum;
            createCell(row, colIdx++, costPerShare, moneyStyle);

            // VAP pro Jahr
            Map<Integer, VapEntry> vapList = vapCalculator.calculateVapList(tx);
            double totalVapPerShare = 0.0;

            for (int year : years)
            {
                VapEntry vapPerShare = vapList.getOrDefault(year, new VapEntry(0.0, 0));
                createCell(row, colIdx++, vapPerShare.vap(), moneyStyle);
                totalVapPerShare += vapPerShare.vap();
            }

            double acquisitionPricePerShare = costPerShare + totalVapPerShare;

            if (hasVap)
            {
                createCell(row, colIdx++, totalVapPerShare, moneyStyle);
                createCell(row, colIdx++, acquisitionPricePerShare, moneyStyle);
            }

            // Steuerberechnungen (nur wenn aktueller Kurs vorhanden)
            if (hasCurrentPrice)
            {
                var securityPrice = security.getSecurityPrice(LocalDate.now());
                double currentPricePerShare = securityPrice.getValue() / (double) Values.Quote.factor();

                // Brutto-Wert
                double grossValue = currentPricePerShare * sharesNum;
                createCell(row, colIdx++, grossValue, moneyStyle);

                // KESt-pflichtiger Gewinn
                double taxableGain = (currentPricePerShare - acquisitionPricePerShare) * sharesNum;

                // TFS anwenden
                if (tfsPercentage > 0)
                {
                    taxableGain = taxableGain * (100 - tfsPercentage) / 100.0;
                }

                createCell(row, colIdx++, taxableGain, moneyStyle);

                // Verlustverrechnung: nur positive Gewinne versteuern
                double taxableGainToConsider = determineTaxableGainsToConsider(cumulativeTaxableGain, taxableGain);
                cumulativeTaxableGain += taxableGain;

                double taxes = taxableGainToConsider * getKestFactor();
                createCell(row, colIdx++, taxes, moneyStyle);

                // Netto-Wert
                double netValue = grossValue - taxes;
                createCell(row, colIdx++, netValue, moneyStyle);

                // Steueranteil
                double taxRatio = grossValue > 0 ? taxes / grossValue : 0.0;
                createCell(row, colIdx++, taxRatio, percentStyle);
            }
        }

        // Spaltenbreiten
        int totalColumns = 7 + years.size() + (hasVap ? 2 : 0) + (hasCurrentPrice ? 5 : 0);
        adjustDetailColumnWidths(sheet, totalColumns);
    }

    private double getKestFactor()
    {
        // TODO Kichensteuern einfügen im Dialog
        double factorKESt = 1.0 + 0.055;
        double kirchensteuer = 0;
        // kirchensteuer = 0.08
        // kirchensteuer = 0.0

        factorKESt += kirchensteuer;

        return 0.25 * factorKESt;
    }

    /**
     * Bestimmt den steuerpflichtigen Gewinn unter Berücksichtigung von
     * Verlusten. Verluste aus früheren Lots können mit Gewinnen verrechnet
     * werden.
     * 
     * @param previousGains
     *            Kumulierte Gewinne/Verluste aus früheren Lots
     * @param currentGain
     *            Gewinn/Verlust des aktuellen Lots
     * @return Steuerpflichtiger Gewinn (0 wenn durch Verluste kompensiert)
     */
    private double determineTaxableGainsToConsider(double previousGains, double currentGain)
    {

        if (previousGains > 0)
        { return Math.max(0.0, currentGain); }

        double remainingLoss = Math.abs(previousGains);
        if (currentGain <= remainingLoss)
        { return 0.0; }

        return currentGain - remainingLoss;

    }

    private void createVapHeaderRow(Sheet sheet, CellStyle headerStyle)
    {
        Row headerRow = sheet.createRow(0);

        createCell(headerRow, 0, "ISIN", headerStyle);
        createCell(headerRow, 1, "Name", headerStyle);
        createCell(headerRow, 2, "Depot", headerStyle);

        int colIndex = 3;
        for (int year : allYears)
        {
            createCell(headerRow, colIndex++, year + " vor TFS", headerStyle);
            createCell(headerRow, colIndex++, year + " nach TFS", headerStyle);
        }
    }

    private void createCell(Row row, int colIndex, String value, CellStyle style)
    {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        if (style != null)
        {
            cell.setCellStyle(style);
        }
    }

    private void createCell(Row row, int colIndex, double value, CellStyle style)
    {
        Cell cell = row.createCell(colIndex);
        if (value != 0)
        {
            cell.setCellValue(value);
        }

        if (style != null)
        {
            cell.setCellStyle(style);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00 [$€-de-DE];-#,##0.00 [$€-de-DE]"));
        return style;
    }

    private CellStyle createSumStyle(Workbook workbook)
    {
        CellStyle style = createMoneyStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd.mm.yyyy"));
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook)
    {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        return style;
    }

    private void adjustVapColumnWidths(Sheet sheet)
    {
        sheet.setColumnWidth(0, 15 * 256); // ISIN
        sheet.setColumnWidth(1, 30 * 256); // Name
        sheet.setColumnWidth(2, 20 * 256); // Depot

        // Jahr-Spalten
        int colIndex = 3;
        for (int year : allYears)
        {
            sheet.setColumnWidth(colIndex++, 15 * 256); // vor TFS
            sheet.setColumnWidth(colIndex++, 15 * 256); // nach TFS
        }
    }

    private void adjustDetailColumnWidths(Sheet sheet, int numColumns)
    {
        sheet.setColumnWidth(0, 15 * 256); // ISIN
        sheet.setColumnWidth(1, 30 * 256); // Name
        sheet.setColumnWidth(2, 12 * 256); // Datum
        sheet.setColumnWidth(3, 12 * 256); // Anzahl unverkauft
        sheet.setColumnWidth(4, 12 * 256); // Anzahl gekauft
        sheet.setColumnWidth(5, 15 * 256); // Gesamtkosten
        sheet.setColumnWidth(6, 15 * 256); // Kosten pro Anteil

        for (int i = 7; i < numColumns; i++)
        {
            sheet.setColumnWidth(i, 15 * 256);
        }
    }
}
