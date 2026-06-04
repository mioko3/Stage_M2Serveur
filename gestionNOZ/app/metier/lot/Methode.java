package app.metier.lot;

import java.awt.Desktop;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Méthode de production associée à un lot.
 *
 * Chaque méthode correspond à un fichier PDF dans app/data/pastouche/methodes/.
 * Les instances sont mises en cache (une par nom) : appeler getMetode() plusieurs
 * fois avec le même nom retourne toujours le même objet.
 *
 * Retourne null si le PDF n’existe pas sur le disque — jamais de méthode fantôme.
 */
public class Methode {

	/** Dossier contenant les PDF des méthodes de production. */
	private static final String LIEN_DOSIER = "app/data/pastouche/methodes/";

	/** Cache nom → instance pour éviter de recréer un objet à chaque appel. */
	private static final Map<String, Methode> CACHE = new HashMap<>();

	private String nom;
	private String lien;

	private Methode(String nom)
	{
		this.nom = nom;
		this.lien = LIEN_DOSIER + nom +".pdf";
	}

	/**
	 * Retourne la méthode correspondant au nom donné, ou null si le PDF est absent.
	 * Le résultat est mis en cache : deux appels avec le même nom renvoient le même objet.
	 *
	 * @param nom nom du fichier PDF sans extension (ex: "coupe-reglette")
	 * @return la Methode, ou null si app/data/pastouche/methodes/{nom}.pdf n’existe pas
	 */
	public static Methode getMetode(String nom)
	{
		if (!verifExist(nom))
		{
			return null;
		}

		return CACHE.computeIfAbsent(nom,Methode::new);
	}

	/** Vérifie que le fichier PDF correspondant existe physiquement sur le disque. */
	private static boolean verifExist(String nom)
	{
		File fichier = new File(LIEN_DOSIER, nom + ".pdf");

		if (fichier.exists() && fichier.isFile())
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	// ─────────────────────────────────────────────
	// Ouvrir le PDF (ou fichier méthode)
	// ─────────────────────────────────────────────
	public void ouvrir()
	{
		try
		{
			if (!verifExist(this.nom))
			{
				System.out.println("Fichier introuvable : " + this.lien);
				return;
			}

			if (Desktop.isDesktopSupported())
			{
				Desktop.getDesktop().open(new File(this.lien));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public String getNom() { return nom; }
	public String getLien() { return lien; }
}