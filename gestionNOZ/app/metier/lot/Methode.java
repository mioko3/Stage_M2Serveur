package app.metier.lot;

import java.awt.Desktop;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Methode {

	private static final String LIEN_DOSIER = "app/data/pastouche/methodes/";

	private static final Map<String, Methode> CACHE = new HashMap<>();

	private String nom;
	private String lien;

	private Methode(String nom)
	{
		this.nom = nom;
		this.lien = LIEN_DOSIER + nom +".pdf";
	}

	public static Methode getMetode(String nom)
	{
		if (!verifExist(nom))
		{
			return null;
		}

		return CACHE.computeIfAbsent(nom,Methode::new);
	}

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