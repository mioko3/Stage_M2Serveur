/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  CheminApp — Résolution robuste des chemins relatifs
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 * ──────────
 * Sans cet utilitaire, le serveur lancé depuis un répertoire quelconque ne trouve
 * pas app/data/. Exemple réel : si le JAR est en /home/user/futura/app.jar mais
 * qu'on le lance depuis /home/user/, les chemins relatifs échouent → crash.
 *
 * SOLUTION :
 * ──────────
 * Ancrer tous les chemins relatifs sur la racine DU PROJET (où se trouve app/).
 * Cette racine est calculée UNE SEULE FOIS au chargement de la classe, puis cachée.
 *
 * UTILISATION :
 * ─────────────
 *   // Au lieu de :
 *   File data = new File("app/data/lots.json");  // ❌ Fragile
 *
 *   // Faire :
 *   File data = new File(CheminApp.resoudre("app/data/lots.json"));  // ✅ Robuste
 *
 * STRATÉGIE DE LOCALISATION (cascadante) :
 * ────────────────────────────────────────
 *   1️⃣  Working directory contient déjà app/data/ ?
 *       → CAS NORMAL (run_SERVEUR.bat fait cd /d %~dp0)
 *       → Racine = working dir
 *
 *   2️⃣  Chercher depuis le dossier du JAR ou classes compilées
 *       → Pour les environnements de production / packaging
 *       → Parcourt les répertoires parents jusqu'à trouver app/data
 *
 *   3️⃣  Fallback sur working directory
 *       → Ancien comportement
 *       → Probablement va échouer, mais au moins cohérent
 *
 * ARCHITECTURE :
 * ──────────────
 *   Appelé TRÈS TÔT au démarrage → éviter dépendances circulaires
 *   Classe statique → un seul calcul pour toute la JVM
 *   Résultat mis en cache dans BASE_DIR (final)
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
package app;
public class CheminApp
{
	private static final String BASE_DIR = calculerBaseDir();

	public static String resoudre(String cheminRelatif)
	{
		return Paths.get(BASE_DIR, cheminRelatif).toString();
	}

	public static String getBaseDir()
	{
		return BASE_DIR;
	}

	private static String calculerBaseDir()
	{
		// Stratégie 1 : le working directory contient déjà app/data/ ?
		// C'est le cas normal quand run_SERVEUR.bat fait "cd /d %~dp0"
		String wd = System.getProperty("user.dir");
		if (new File(wd, "app/data").exists())
		{
			System.out.println("[CheminApp] Racine trouvée via working directory : " + wd);
			return wd;
		}

		// Stratégie 2 : chercher depuis le dossier du JAR ou du dossier bin/
		try
		{
			String codePath = CheminApp.class
				.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()
				.getPath();

			File f = new File(codePath);

			// On remonte jusqu'à trouver un dossier qui contient app/data/
			// Maximum 4 niveaux pour ne pas partir trop loin
			for (int i = 0; i < 4; i++)
			{
				if (f == null) break;
				if (new File(f, "app/data").exists())
				{
					System.out.println("[CheminApp] Racine trouvée en remontant depuis le code : " + f.getAbsolutePath());
					return f.getAbsolutePath();
				}
				f = f.getParentFile();
			}
		}
		catch (Exception e)
		{
			System.err.println("[CheminApp] Recherche depuis le code échouée : " + e.getMessage());
		}

		// Stratégie 3 : fallback — on retourne le working directory et on
		// affiche un avertissement clair pour aider au diagnostic
		System.err.println("[CheminApp] ATTENTION : dossier app/data/ introuvable !");
		System.err.println("[CheminApp] Working directory : " + wd);
		System.err.println("[CheminApp] Vérifiez que vous lancez le serveur depuis la racine du projet.");
		System.err.println("[CheminApp] Exemple : cd C:\\monprojet && java -cp bin app.ServeurHTTP");
		return wd;
	}
}