package melled.portfolio.vorabpauschale.model;

import java.util.Objects;

/**
 * Verwaltet Metadaten für ETFs/Wertpapiere. Entspricht der Python ETFMetadata
 * Klasse.
 */
public class VapMetadata
{

    // isin or wkn or name
    private final String id;
    private final int year;
    private final double vapBeforeTfs;
    private final int tfsPercentage; // Teilfreistellung in Prozent (0-100)

    public VapMetadata(String id, int year, double vapBeforeTfs, int tfsPercentage)
    {
        if (tfsPercentage < 0 || tfsPercentage > 100)
        { throw new IllegalArgumentException("TFS-Prozentsatz muss zwischen 0 und 100 liegen: " + tfsPercentage); }

        this.id = id;
        this.year = year;
        this.tfsPercentage = tfsPercentage;
        this.vapBeforeTfs = vapBeforeTfs;
    }

    public String getId()
    {
        return id;
    }

    public int getYear()
    {
        return year;
    }

    public int getTfsPercentage()
    {
        return tfsPercentage;
    }

    public double getTfsFactor()
    {
        return tfsPercentage / 100.0;
    }

    public double getVapBeforeTfs()
    {
        return vapBeforeTfs;
    }

    public double applyTfs(double amountBeforeTfs)
    {
        return amountBeforeTfs * (1.0 - getTfsFactor());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, tfsPercentage, year);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        { return true; }
        if (obj == null)
        { return false; }
        if (getClass() != obj.getClass())
        { return false; }
        VapMetadata other = (VapMetadata) obj;
        return Objects.equals(id, other.id) && tfsPercentage == other.tfsPercentage && year == other.year;
    }

    @Override
    public String toString()
    {
        return "VapMetadata [id=" + id + ", year=" + year + ", tfsPercentage=" + tfsPercentage + "]";
    }

}
