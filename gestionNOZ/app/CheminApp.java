package app;

import java.io.File;
import java.nio.file.Paths;

/**
 * CheminApp — ancrage des chemins sur la racine du projet
 *
 * LOGIQUE SIMPLE :
 * On cherche le dossier "app/data" en remontant depuis la classe compilée.
 * C'est plus fiable que de déduire depuis le JAR car le projet est lancé
 * soit depuis bin/ (développement), soit depuis un JAR (production).
 *
 * Stratégie :
 *  1. Partir du working directory (là où on a tapé la commande java)
 *  2. Vérifier si app/data/ existe à cet endroit → c'est la racine
 *  3. Sinon, chercher depuis le dossier du JAR/classes
 *  4. Sinon, fallback sur le working directory (comportement original)
 *
 * Ainsi, si on lance depuis la racine du projet (ce que font run.bat et
 * run_SERVEUR.bat grâce à "cd /d %~dp0"), ça marche directement.
 * Et si on lance depuis ailleurs, on tente quand même de trouver app/data/.
 */
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