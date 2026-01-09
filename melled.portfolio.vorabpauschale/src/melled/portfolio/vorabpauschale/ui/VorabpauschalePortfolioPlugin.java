package melled.portfolio.vorabpauschale.ui;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class VorabpauschalePortfolioPlugin implements BundleActivator
{
    public static final String PLUGIN_ID = "name.abuchen.portfolio.vorabpauschale"; //$NON-NLS-1$

    private static VorabpauschalePortfolioPlugin instance;

    public VorabpauschalePortfolioPlugin()
    {
        super();
        instance = this; // NOSONAR bundle is singleton
    }

    @Override
    public void start(BundleContext context) throws Exception
    {

    }

    @Override
    public void stop(BundleContext context) throws Exception
    {

    }

}
