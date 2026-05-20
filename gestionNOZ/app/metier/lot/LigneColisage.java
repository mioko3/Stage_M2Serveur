package app.metier.lot;

public class LigneColisage
{
	private int    pcs;
	private String formatCarton;
	private int    collisage;
	private int    nbColis;
	private int    nbPalettes;

	public LigneColisage(String formatCarton, int collisage)
	{
		this.formatCarton = formatCarton;
		this.collisage    = collisage;
	}

	public void recalculer(int nbPieces)
	{
		if (collisage <= 0 || formatCarton == null) return;
		this.nbColis = (int) Math.ceil((double) nbPieces / collisage);
		int parPalette;
		switch (formatCarton)
		{
			case "1/16": parPalette = 64; break;
			case "1/8":  parPalette = 32; break;
			case "1/4":  parPalette = 16; break;
			case "1/2":  parPalette =  8; break;
			case "box":  parPalette =  1; break;
			default:     parPalette =  1;
		}
		this.nbPalettes = (int) Math.ceil((double) nbColis / parPalette);
		this.pcs = nbPieces;
	}

	public int    getPcs()          { return this.pcs;     }
	public String getFormatCarton() { return formatCarton; }
	public int    getCollisage()    { return collisage;    }
	public int    getNbColis()      { return nbColis;      }
	public int    getNbPalettes()   { return nbPalettes;   }

	public void setFormatCarton(String v) { this.formatCarton = v; }
	public void setCollisage(int v)       { this.collisage    = v; }

	public String toJson()
	{
		return String.format(
			"{\"format\":\"%s\",\"collisage\":%d,\"nbColis\":%d,\"nbPalettes\":%d}",
			formatCarton, collisage, nbColis, nbPalettes);
	}

	public static LigneColisage fromJson(String obj)
	{
		String fmt = getString(obj, "format");
		int    col = getInt(obj, "collisage");
		return new LigneColisage(fmt, col);
	}

	private static String getString(String obj, String cle)
	{
		String p = "\"" + cle + "\"";
		int pos = obj.indexOf(p);
		if (pos < 0) return "";
		pos = obj.indexOf('"', pos + p.length() + 1);
		if (pos < 0) return "";
		int end = obj.indexOf('"', pos + 1);
		if (end < 0) return "";
		return obj.substring(pos + 1, end);
	}

	private static int getInt(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return 0;
		pos += p.length();
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); }
		catch (NumberFormatException e) { return 0; }
	}
}