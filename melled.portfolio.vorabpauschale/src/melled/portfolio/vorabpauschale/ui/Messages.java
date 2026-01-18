package melled.portfolio.vorabpauschale.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "melled.portfolio.vorabpauschale.messages"; //$NON-NLS-1$
    // TODO I!10N

    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    protected Messages()
    {
    }
}
