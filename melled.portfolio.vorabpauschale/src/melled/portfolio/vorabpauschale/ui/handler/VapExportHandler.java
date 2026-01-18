package melled.portfolio.vorabpauschale.ui.handler;

import java.io.File;
import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import melled.portfolio.vorabpauschale.service.VapExportService;
import name.abuchen.portfolio.model.Client;

/**
 * Eclipse RCP Command Handler für VAP-Export. Wird über das Menü aufgerufen und
 * zeigt Datei-Dialoge zur Auswahl der CSVs und der Ziel-Excel-Datei.
 */
// NOSONAR
@SuppressWarnings("java:S6813") // Eclipse Command Handler field injection
public class VapExportHandler
{

    private static final String GET_CLIENT_METHOD_NAME = "getClient";

    @Inject
    private VapExportService vapExportService;

    @Inject
    private IEclipseContext context;

    @CanExecute
    boolean isVisible(@Named(IServiceConstants.ACTIVE_PART) MPart part)
    {
        return getClient(part) != null;
    }

    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_PART) MPart part,
                    @Named(IServiceConstants.ACTIVE_SHELL) Shell shell)
    {
        Client client = getClient(part);

        if (client != null)
        {
            exportVap(client, shell);
        }
        else
        {
            MessageDialog.openError(shell, "Fehler",
                            "Kein Portfolio geöffnet. Bitte öffnen Sie zuerst eine Portfolio-Datei.");
        }
    }

    private Client getClient(MPart part)
    {
        Client client = context.get(Client.class);
        if (client == null)
        { return extractClient(part.getObject()); }
        return client;
    }

    /**
     * Extrahiert den Client aus dem Part-Object via Reflection. Dies umgeht die
     * Access Restrictions auf interne Klassen.
     */
    private Client extractClient(Object partObject)
    {
        if (partObject == null)
        { return null; }

        try
        {

            java.lang.reflect.Method getClientMethod = partObject.getClass().getMethod(GET_CLIENT_METHOD_NAME);
            Object result = getClientMethod.invoke(partObject);

            if (result instanceof Client client)
            { return client; }

            // Falls getClient() ClientInput zurückgibt, hole den Client daraus
            if (result != null)
            {
                java.lang.reflect.Method getClientFromInput = result.getClass().getMethod(GET_CLIENT_METHOD_NAME);
                Object clientResult = getClientFromInput.invoke(result);
                if (clientResult instanceof Client client)
                { return client; }
            }
        }
        catch (Exception e)
        {

            try
            {

                java.lang.reflect.Method getClientInputMethod = partObject.getClass().getMethod("getClientInput");
                Object clientInput = getClientInputMethod.invoke(partObject);
                if (clientInput != null)
                {
                    java.lang.reflect.Method getClientMethod = clientInput.getClass().getMethod(GET_CLIENT_METHOD_NAME);
                    Object clientResult = getClientMethod.invoke(clientInput);
                    if (clientResult instanceof Client client)
                    { return client; }
                }
            }
            catch (Exception ex)
            {
                // nothing to do
            }
        }

        return null;
    }

    public void exportVap(Client client, Shell shell)
    {

        String vapFile = selectFile(shell, "VAP-Daten CSV auswählen", "etf_vorabpauschalen.csv",
                        new String[] { "*.csv", "*.*" }, new String[] { "CSV Dateien (*.csv)", "Alle Dateien (*.*)" });

        if (vapFile == null)
        { return; }

        FileDialog dialog = new FileDialog(shell, SWT.SAVE);
        dialog.setText("Ausgabe-Excel-Datei");
        dialog.setFileName("VAP_Export.xlsx");
        dialog.setFilterExtensions("*.xlsx", "*.*");
        dialog.setFilterNames("Excel Dateien (*.xlsx)", "Alle Dateien (*.*)");
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

                    vapExportService.exportVap(client, metadataFile, outputFile);
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
                    shell.getDisplay().asyncExec(() -> MessageDialog.openError(shell, "VAP Export Fehler",
                                    "Fehler beim Export:\n\n" + e.getMessage()));

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
