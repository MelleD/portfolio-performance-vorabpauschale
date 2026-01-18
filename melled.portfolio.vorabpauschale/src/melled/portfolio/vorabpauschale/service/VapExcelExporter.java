package melled.portfolio.vorabpauschale.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;

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
import org.eclipse.e4.core.di.annotations.Creatable;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import melled.portfolio.vorabpauschale.service.VapCalculator.VapEntry;
import melled.portfolio.vorabpauschale.service.VapSummaryCollector.VapSummaryRow;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;

/**
 * Exportiert VAP-Daten nach Excel.
 */
@Creatable
public class VapExcelExporter
{

    private final VapCalculator vapCalculator;
    private final VapSummaryCollector vapSummaryCollector;
    private final PortfolioValueCalculator portfolioValueCalculator;

    private Map<Portfolio, List<UnsoldTransaction>> transactions;
    private List<VapSummaryRow> summaryRows;
    private Set<Integer> allYears;

    @Inject
    public VapExcelExporter(VapCalculator vapCalculator, VapSummaryCollector vapSummaryCollector,
                    PortfolioValueCalculator portfolioValueCalculator)
    {
        this.vapCalculator = vapCalculator;
        this.vapSummaryCollector = vapSummaryCollector;
        this.portfolioValueCalculator = portfolioValueCalculator;
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
    public void export(String metadataFile, String outputFile, Map<Portfolio, List<UnsoldTransaction>> transactions)
                    throws IOException
    {
        vapCalculator.initializeVapData(metadataFile);
        this.transactions = transactions;
        this.summaryRows = vapSummaryCollector.collectSummary(transactions);
        this.allYears = extractAllYears(summaryRows);

        if (summaryRows.isEmpty())
        { return; }

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

        sheet.createFreezePane(0, 1);

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

        for (Entry<Portfolio, List<UnsoldTransaction>> portfolio : transactions.entrySet())
        {

            String broker = portfolio.getKey().getName();

            Map<Security, List<UnsoldTransaction>> transactionsBySecurity = new LinkedHashMap<>();
            for (UnsoldTransaction tx : portfolio.getValue())
            {
                if (tx.getTransaction().getSecurity() == null)
                {
                    continue;
                }

                transactionsBySecurity.computeIfAbsent(tx.getTransaction().getSecurity(), k -> new ArrayList<>())
                                .add(tx);
            }

            for (Map.Entry<Security, List<UnsoldTransaction>> entry : transactionsBySecurity.entrySet())
            {
                Security security = entry.getKey();
                List<UnsoldTransaction> transactions = entry.getValue();

                String isin = getIsin(security);
                String sheetName = getSheetName(broker, security, isin);

                Sheet sheet = workbook.createSheet(sheetName);
                createDetailSheet(sheet, security, transactions, broker, headerStyle, moneyStyle, dateStyle,
                                percentStyle);
            }
        }
    }

    private String getSheetName(String broker, Security security, String isin)
    {
        String sheetName = broker + " " + (isin.isEmpty() ? security.getName() : isin);
        return sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
    }

    private String getIsin(Security security)
    {
        return security.getIsin() != null ? security.getIsin() : "";
    }

    /**
     * Erstellt ein Detail-Sheet für eine Security.
     */
    private void createDetailSheet(Sheet sheet, Security security, List<UnsoldTransaction> transactions, String broker,
                    CellStyle headerStyle, CellStyle moneyStyle, CellStyle dateStyle, CellStyle percentStyle)
    {

        boolean hasCurrentPrice = (security.getCurrencyCode() != null)
                        && (security.getSecurityPrice(LocalDate.now()) != null);
        int tfsPercentage = 0;
        Set<Integer> years = new TreeSet<>();
        for (UnsoldTransaction tx : transactions)
        {
            Map<Integer, VapEntry> vapList = vapCalculator.calculateVapList(tx);
            if (!vapList.isEmpty())
            {
                tfsPercentage = vapList.values().iterator().next().tfsPercentage();
            }
            years.addAll(vapList.keySet());
        }

        boolean hasVap = createDeteilSheetHeader(sheet, headerStyle, hasCurrentPrice, tfsPercentage, years);

        int colIdx;
        int rowIdx = 1;
        double cumulativeTaxableGain = 0.0;

        for (UnsoldTransaction tx : transactions)
        {
            Row row = sheet.createRow(rowIdx++);
            colIdx = 0;

            String isin = security.getIsin() != null ? security.getIsin() : "";
            createCell(row, colIdx++, isin, null);
            createCell(row, colIdx++, security.getName(), null);

            LocalDate purchaseDate = tx.getTransaction().getDateTime().toLocalDate();
            Cell dateCell = row.createCell(colIdx++);
            dateCell.setCellValue(purchaseDate);
            dateCell.setCellStyle(dateStyle);

            createCell(row, colIdx++, tx.getUnsoldShare(), null);
            createCell(row, colIdx++, tx.getShare(), null);

            // Kostenberechnungen mit CostCalculator
            double costPerShare = portfolioValueCalculator.getCostCalculator().calculateCostPerShare(tx);
            double totalCost = portfolioValueCalculator.getCostCalculator().calculateTotalCost(tx);
            createCell(row, colIdx++, totalCost, moneyStyle);
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

            double acquisitionPricePerShare = portfolioValueCalculator.getCostCalculator()
                            .calculateAcquisitionPriceWithVap(costPerShare, totalVapPerShare);

            if (hasVap)
            {
                createCell(row, colIdx++, totalVapPerShare, moneyStyle);
                createCell(row, colIdx++, acquisitionPricePerShare, moneyStyle);
            }

            // Steuerberechnungen (nur wenn aktueller Kurs vorhanden)
            if (hasCurrentPrice)
            {
                double currentPricePerShare = portfolioValueCalculator.calculateCurrentPricePerShare(security);

                // Alle Werte mit PortfolioValueCalculator berechnen
                var values = portfolioValueCalculator.calculatePositionValues(tx, currentPricePerShare,
                                acquisitionPricePerShare, tfsPercentage, cumulativeTaxableGain);

                // Kumulativen Gewinn aktualisieren
                cumulativeTaxableGain += values.taxableGain;

                // Brutto-Wert
                createCell(row, colIdx++, values.grossValue, moneyStyle);

                // KESt-pflichtiger Gewinn
                createCell(row, colIdx++, values.taxableGain, moneyStyle);

                // KESt
                createCell(row, colIdx++, values.taxes, moneyStyle);

                // Netto-Wert
                createCell(row, colIdx++, values.netValue, moneyStyle);

                // Steueranteil
                createCell(row, colIdx++, values.taxRatio, percentStyle);
            }
        }

        // Spaltenbreiten
        int totalColumns = 7 + years.size() + (hasVap ? 2 : 0) + (hasCurrentPrice ? 5 : 0);
        adjustDetailColumnWidths(sheet, totalColumns);
    }

    private boolean createDeteilSheetHeader(Sheet sheet, CellStyle headerStyle, boolean hasCurrentPrice,
                    int tfsPercentage, Set<Integer> years)
    {
        Row headerRow = sheet.createRow(0);
        int colIdx = 0;

        createCell(headerRow, colIdx++, "ISIN", headerStyle);
        createCell(headerRow, colIdx++, "Name", headerStyle);
        createCell(headerRow, colIdx++, "Datum Kauf", headerStyle);
        createCell(headerRow, colIdx++, "Anzahl (noch unverkauft)", headerStyle);
        createCell(headerRow, colIdx++, "Anzahl (gekauft)", headerStyle);
        createCell(headerRow, colIdx++, "Gesamtkosten", headerStyle);
        createCell(headerRow, colIdx++, "Kosten pro Anteil", headerStyle);

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

            String kest = portfolioValueCalculator.getTaxCalculator().formatKest();
            createCell(headerRow, colIdx++, "KESt (" + kest + "%)", headerStyle);
            createCell(headerRow, colIdx++, "Netto-Wert", headerStyle);
            createCell(headerRow, colIdx++, "Steueranteil an Brutto-Auszahlung", headerStyle);
        }

        sheet.createFreezePane(0, 1);
        return hasVap;
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
