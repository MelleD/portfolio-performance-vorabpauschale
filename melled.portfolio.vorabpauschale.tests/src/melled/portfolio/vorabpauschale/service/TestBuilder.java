package melled.portfolio.vorabpauschale.service;

import java.util.List;

import melled.portfolio.vorabpauschale.model.UnsoldTransaction;
import name.abuchen.portfolio.junit.repacked.PortfolioBuilder;
import name.abuchen.portfolio.junit.repacked.SecurityBuilder;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction.Type;
import name.abuchen.portfolio.model.Security;

public class TestBuilder
{

    private Client client;

    public TestBuilder(Client client)
    {
        this.client = client;
    }

    List<UnsoldTransaction> transactions(Portfolio portfolio)
    {
        return portfolio.getTransactions().stream().map(tx -> new UnsoldTransaction(tx)).toList();
    }

    UnsoldTransaction transaction(String date, Type type)
    {
        Security security = new SecurityBuilder().addTo(client);
        security.setIsin("DE0001");

        PortfolioBuilder builder = new PortfolioBuilder();

        if (type == Type.DELIVERY_INBOUND)
        {
            builder.inbound_delivery(security, date, PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000));
        }
        else
        {
            builder.buy(security, date, PortfolioBuilder.sharesOf(10), PortfolioBuilder.amountOf(1000));
        }

        Portfolio portfolio = builder.addTo(client);

        PortfolioTransaction tx = portfolio.getTransactions().get(0);
        return new UnsoldTransaction(tx);
    }

    UnsoldTransaction transaction(Type type)
    {
        return transaction("2020-01-15", type);
    }

}
