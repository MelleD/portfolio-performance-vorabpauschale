package melled.portfolio.vorabpauschale.ui.handler;

import java.io.File;
import java.io.IOException;

import jakarta.inject.Named;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import melled.portfolio.vorabpauschale.VapExportService;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.ui.handlers.MenuHelper;

/**
 * Eclipse RCP Command Handler für VAP-Export. Wird über das Menü aufgerufen und
 * zeigt Datei-Dialoge zur Auswahl der CSVs und der Ziel-Excel-Datei.
 */
public class VapExportHandler
{

    @CanExecute
    boolean isVisible(@Named(IServiceConstants.ACTIVE_PART) MPart part)
    {
        // return MenuHelper.isClientPartActive(part);
        return true;
    }

    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_PART) MPart part,
                    @Named(IServiceConstants.ACTIVE_SHELL) Shell shell)
    {
        MenuHelper.getActiveClient(part).ifPresent(input -> execute(input, shell));
    }

    public void execute(Client client, Shell shell)
    {

        String vapFile = selectFile(shell, "VAP-Daten CSV auswählen", "etf_vorabpauschalen.csv",
                        new String[] { "*.csv", "*.*" }, new String[] { "CSV Dateien (*.csv)", "Alle Dateien (*.*)" });

        if (vapFile == null)
        { return; }

        FileDialog dialog = new FileDialog(shell, SWT.SAVE);
        dialog.setText("Ausgabe-Excel-Datei");
        dialog.setFileName("VAP_Export.xlsx");
        dialog.setFilterExtensions(new String[] { "*.xlsx", "*.*" });
        dialog.setFilterNames(new String[] { "Excel Dateien (*.xlsx)", "Alle Dateien (*.*)" });
        dialog.setOverwrite(true);

        String outputFile = dialog.open();
        if (outputFile == null)
        { return; }

        startExportJob(shell, client, vapFile, outputFile);
    }

    private String selectFile(Shell shell, String title, String defaultFileName, String[] extensions, String[] names)
    {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setText(title);
        dialog.setFileName(defaultFileName);
        dialog.setFilterExtensions(extensions);
        dialog.setFilterNames(names);
        return dialog.open();
    }

    private void startExportJob(Shell shell, Client client, String metadataFile, String outputFile)
    {

        Job job = new Job("VAP Export")
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                monitor.beginTask("Exportiere VAP-Daten...", 5);

                try
                {
                    monitor.subTask("Lade Metadaten...");
                    monitor.worked(1);

                    VapExportService.exportVap(client, metadataFile, outputFile);
                    monitor.worked(4);

                    shell.getDisplay().asyncExec(() -> {
                        boolean openFile = MessageDialog.openQuestion(shell, "VAP Export erfolgreich",
                                        "VAP-Export wurde erfolgreich abgeschlossen:\n\n" + outputFile
                                                        + "\n\nMöchten Sie die Datei jetzt öffnen?");

                        if (openFile)
                        {
                            openFile(outputFile, shell);
                        }
                    });
                    return Status.OK_STATUS;

                }
                catch (Exception e)
                {
                    shell.getDisplay().asyncExec(() -> {
                        MessageDialog.openError(shell, "VAP Export Fehler", "Fehler beim Export:\n\n" + e.getMessage());
                    });

                    return Status.error("VAP Export fehlgeschlagen", e);

                }
                finally
                {
                    monitor.done();
                }
            }
        };

        job.setUser(true);
        job.schedule();
    }

    private void openFile(String filePath, Shell shell)
    {

        if (java.awt.Desktop.isDesktopSupported())
        {
            try
            {
                java.awt.Desktop.getDesktop().open(new File(filePath));
            }
            catch (IOException e)
            {
                MessageDialog.openError(shell, "VAP Anzeigefehler", "Fehler beim Anzeigen:\n\n" + e.getMessage());
            }
        }

    }
}
