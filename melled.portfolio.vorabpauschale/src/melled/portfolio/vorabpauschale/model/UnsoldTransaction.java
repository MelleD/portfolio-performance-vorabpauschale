package melled.portfolio.vorabpauschale.model;

import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.money.Values;

public class UnsoldTransaction implements Comparable<UnsoldTransaction>
{

    private final PortfolioTransaction transaction;
    private double unsoldShare;
    private double share;

    public UnsoldTransaction(PortfolioTransaction transaction, double share)
    {
        this.transaction = transaction;
        this.share = share;
        this.unsoldShare = share;
    }

    public UnsoldTransaction(PortfolioTransaction transaction)
    {
        this(transaction, calcluateShare(transaction));
    }

    public PortfolioTransaction getTransaction()
    {
        return transaction;
    }

    public double getUnsoldShare()
    {
        return unsoldShare;
    }

    public double getShare()
    {
        return share;
    }

    public void reduzeUnsoldShare(double reduzeUnsoldShare)
    {
        unsoldShare = unsoldShare - reduzeUnsoldShare;
    }

    public static double calcluateShare(PortfolioTransaction tx)
    {
        return tx.getShares() / (double) Values.Share.factor();
    }

    @Override
    public int compareTo(UnsoldTransaction o)
    {
        return getTransaction().getDateTime().compareTo(o.getTransaction().getDateTime());
    }
}
