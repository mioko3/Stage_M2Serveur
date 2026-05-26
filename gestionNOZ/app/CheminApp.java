package app;

import java.io.File;
import java.nio.file.Paths;

/**
 * ══════════════════════════════════════════════════════════════
 *  CheminApp — Résolution des chemins relatifs (CORRECTIF #1)
 * ══════════════════════════════════════════════════════════════
 *
 *  PROBLÈME RÉSOLU :
 *  ─────────────────
 *  Dans ServeurHTTP, les chemins comme "app/data/courutilisation/lots.json"
 *  sont relatifs au "répertoire courant" (working directory) du processus Java.
 *
 *  Sur un PC normal lancé avec run_SERVEUR.bat depuis le bon dossier → ça marche.
 *  Sur un serveur Linux lancé comme service systemd, ou si on double-clique
 *  l'EXE depuis l'Explorateur → le working directory est souvent C:\Windows\System32
 *  ou /root, et le programme ne trouve plus ses JSON. Il démarre "à vide" sans planter,
 *  ce qui est difficile à debugger.
 *
 *  SOLUTION :
 *  ──────────
 *  On ancre tous les chemins sur le dossier où se trouve le JAR/EXE,
 *  pas sur le dossier depuis lequel on a lancé la commande.
 *
 *  Comment ça marche :
 *  - getBaseDir() retrouve le dossier du JAR en cours d'exécution
 *  - resoudre("app/data/...") combine ce dossier de base + le chemin relatif
 *  - Si on est en développement (lancé depuis l'IDE sans JAR), on retombe
 *    sur le working directory classique pour ne pas casser le dev
 *
 *  COMMENT L'UTILISER dans ServeurHTTP :
 *  ──────────────────────────────────────
 *  Remplacer :
 *      this.cheminLotsJson = "app/data/courutilisation/lots.json";
 *  Par :
 *      this.cheminLotsJson = CheminApp.resoudre("app/data/courutilisation/lots.json");
 *
 *  C'est tout. Le reste du code ne change pas.
 */
public class CheminApp
{
	/** Dossier de base calculé une seule fois au démarrage. */
	private static final String BASE_DIR = calculerBaseDir();

	/**
	 * Retourne le chemin absolu correspondant au chemin relatif donné,
	 * ancré sur le dossier du JAR (et non sur le working directory).
	 *
	 * @param cheminRelatif  ex: "app/data/courutilisation/lots.json"
	 * @return               chemin absolu prêt à l'emploi
	 */
	public static String resoudre(String cheminRelatif)
	{
		return Paths.get(BASE_DIR, cheminRelatif).toString();
	}

	/**
	 * Retourne le dossier de base (là où est le JAR).
	 * Utile pour les JFileChooser qui veulent s'ouvrir au bon endroit.
	 */
	public static String getBaseDir()
	{
		return BASE_DIR;
	}

	// ── Calcul interne ────────────────────────────────────────────────────

	private static String calculerBaseDir()
	{
		try
		{
			// Récupère le chemin du JAR qui contient cette classe
			String jarPath = CheminApp.class
				.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()
				.getPath();

			File jarFile = new File(jarPath);

			// Si c'est un fichier JAR → son dossier parent est la base
			if (jarFile.isFile())
			{
				String base = jarFile.getParent();
				System.out.println("[CheminApp] Base ancrée sur le dossier du JAR : " + base);
				return base;
			}

			// Si c'est un dossier (ex: bin/ dans l'IDE) → remonter d'un niveau
			// pour retomber sur la racine du projet
			String base = jarFile.getParentFile().getParent();
			if (base == null) base = jarFile.getParent();
			System.out.println("[CheminApp] Mode développement, base : " + base);
			return base;
		}
		catch (Exception e)
		{
			// Dernier recours : working directory classique
			String fallback = System.getProperty("user.dir");
			System.err.println("[CheminApp] Impossible de détecter le JAR, fallback sur : " + fallback);
			return fallback;
		}
	}
}