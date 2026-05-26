package app.securite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * ══════════════════════════════════════════════════════════════
 *  ChiffrementAES — chiffrement symétrique AES-256-CBC
 * ══════════════════════════════════════════════════════════════
 *
 *  POURQUOI AES ?
 *  ──────────────
 *  AES (Advanced Encryption Standard) est l'algorithme de
 *  chiffrement symétrique standard. "Symétrique" signifie que
 *  la même clé sert à chiffrer ET à déchiffrer.
 *  Il est intégré dans Java, rapide, et considéré incassable
 *  en pratique avec une clé de 256 bits.
 *
 *  POURQUOI CBC + IV ?
 *  ───────────────────
 *  CBC (Cipher Block Chaining) est un mode de chiffrement par blocs.
 *  Sans IV, deux messages identiques produiraient le même texte chiffré,
 *  ce qui permettrait de détecter des patterns. L'IV (vecteur d'initialisation)
 *  est un nombre aléatoire de 16 octets généré pour CHAQUE chiffrement.
 *  Il est préfixé au message chiffré et n'est pas secret.
 *
 *  FORMAT D'UN MESSAGE CHIFFRÉ :
 *  ─────────────────────────────
 *  Base64( IV(16 octets) + données_chiffrées )
 *  → une seule chaîne Base64 facile à transporter en HTTP
 *
 *  UTILISATION :
 *  ─────────────
 *  Côté serveur (ServeurHTTP) :
 *      ChiffrementAES aes = ChiffrementAES.chargerOuCreer("secret.key");
 *      String chiffre = aes.chiffrer(jsonBrut);
 *      String brut    = aes.dechiffrer(bodyRecu);
 *
 *  Côté client (ControleurClient) :
 *      ChiffrementAES aes = ChiffrementAES.depuisBase64(cleReçueServeur);
 *      String chiffre = aes.chiffrer(jsonBrut);
 *      String brut    = aes.dechiffrer(bodyRecu);
 *
 *  CETTE CLASSE EST IDENTIQUE côté serveur et côté client.
 *  Copiez-la dans les deux projets (Stage_M2serveur ET Stage_M-2).
 * ══════════════════════════════════════════════════════════════
 */
public class ChiffrementAES
{
	private static final String ALGORITHME = "AES/CBC/PKCS5Padding";
	private static final int    TAILLE_IV  = 16; // octets

	private final SecretKey cle;

	// ── Constructeur privé ────────────────────────────────────────────────

	private ChiffrementAES(SecretKey cle)
	{
		this.cle = cle;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  FABRIQUE — comment obtenir une instance
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * CÔTÉ SERVEUR.
	 * Charge la clé depuis un fichier, ou en génère une nouvelle si le fichier
	 * n'existe pas encore (premier démarrage).
	 *
	 * Le fichier contient la clé en Base64 sur une seule ligne.
	 * IMPORTANT : ce fichier doit rester sur le serveur uniquement.
	 * Ne le mettez pas dans git, ne l'envoyez pas par email.
	 *
	 * @param cheminFichier  ex: "secret.key" ou CheminApp.resoudre("secret.key")
	 */
	public static ChiffrementAES chargerOuCreer(String cheminFichier) throws Exception
	{
		Path p = Paths.get(cheminFichier);

		if (Files.exists(p))
		{
			// Charger la clé existante
			String base64 = Files.readString(p).trim();
			byte[] octets = Base64.getDecoder().decode(base64);
			SecretKey cle = new SecretKeySpec(octets, "AES");
			System.out.println("[AES] Clé chargée depuis : " + cheminFichier);
			return new ChiffrementAES(cle);
		}
		else
		{
			// Première exécution : générer une clé AES-256 aléatoire
			KeyGenerator gen = KeyGenerator.getInstance("AES");
			gen.init(256); // 256 bits = 32 octets
			SecretKey cle = gen.generateKey();

			// Sauvegarder en Base64
			String base64 = Base64.getEncoder().encodeToString(cle.getEncoded());
			Files.createDirectories(p.getParent() == null ? Paths.get(".") : p.getParent());
			Files.writeString(p, base64);

			System.out.println("[AES] Nouvelle clé générée et sauvegardée : " + cheminFichier);
			System.out.println("[AES] IMPORTANT : si vous supprimez ce fichier, les données");
			System.out.println("[AES]             chiffrées sur le disque seront illisibles.");
			return new ChiffrementAES(cle);
		}
	}

	/**
	 * CÔTÉ CLIENT.
	 * Reconstruit l'instance depuis la chaîne Base64 reçue du serveur via /cle.
	 *
	 * @param base64  la clé en Base64, telle que renvoyée par GET /cle
	 */
	public static ChiffrementAES depuisBase64(String base64) throws Exception
	{
		byte[] octets = Base64.getDecoder().decode(base64.trim());
		SecretKey cle = new SecretKeySpec(octets, "AES");
		return new ChiffrementAES(cle);
	}

	/**
	 * Retourne la clé en Base64 (pour l'envoyer au client via /cle).
	 */
	public String cleEnBase64()
	{
		return Base64.getEncoder().encodeToString(cle.getEncoded());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHIFFREMENT / DÉCHIFFREMENT
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Chiffre un texte (JSON ou autre) et retourne le résultat en Base64.
	 *
	 * Format du résultat : Base64( IV(16 octets) || données_chiffrées )
	 * L'IV est aléatoire à chaque appel → deux appels avec le même texte
	 * donnent deux résultats différents (sécurité renforcée).
	 *
	 * @param texte  le JSON brut à chiffrer
	 * @return       chaîne Base64 transportable en HTTP
	 */
	public String chiffrer(String texte) throws Exception
	{
		// Générer un IV aléatoire pour cet envoi
		byte[] iv = new byte[TAILLE_IV];
		new SecureRandom().nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);

		// Chiffrer
		Cipher cipher = Cipher.getInstance(ALGORITHME);
		cipher.init(Cipher.ENCRYPT_MODE, cle, ivSpec);
		byte[] donnéesChiffrées = cipher.doFinal(texte.getBytes("UTF-8"));

		// Concaténer IV + données chiffrées, puis encoder en Base64
		byte[] resultat = new byte[TAILLE_IV + donnéesChiffrées.length];
		System.arraycopy(iv,               0, resultat, 0,         TAILLE_IV);
		System.arraycopy(donnéesChiffrées, 0, resultat, TAILLE_IV, donnéesChiffrées.length);

		return Base64.getEncoder().encodeToString(resultat);
	}

	/**
	 * Déchiffre un texte Base64 reçu (inverse de chiffrer).
	 *
	 * @param base64Chiffré  la chaîne Base64 reçue
	 * @return               le texte original (JSON brut)
	 */
	public String dechiffrer(String base64Chiffré) throws Exception
	{
		byte[] données = Base64.getDecoder().decode(base64Chiffré.trim());

		// Extraire l'IV (premiers 16 octets)
		byte[] iv = new byte[TAILLE_IV];
		System.arraycopy(données, 0, iv, 0, TAILLE_IV);

		// Extraire les données chiffrées (le reste)
		byte[] donnéesChiffrées = new byte[données.length - TAILLE_IV];
		System.arraycopy(données, TAILLE_IV, donnéesChiffrées, 0, donnéesChiffrées.length);

		// Déchiffrer
		Cipher cipher = Cipher.getInstance(ALGORITHME);
		cipher.init(Cipher.DECRYPT_MODE, cle, new IvParameterSpec(iv));
		byte[] brut = cipher.doFinal(donnéesChiffrées);

		return new String(brut, "UTF-8");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHIFFREMENT FICHIERS (pour lots.json / societes.json)
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Chiffre un fichier texte et écrit le résultat dans un fichier .enc.
	 *
	 * Exemple :
	 *   aes.chiffrerFichier("app/data/lots.json", "app/data/lots.json.enc");
	 *
	 * Le fichier original n'est PAS supprimé automatiquement —
	 * vérifiez que tout fonctionne avant de le supprimer.
	 */
	public void chiffrerFichier(String cheminSource, String cheminDest) throws Exception
	{
		String contenu  = Files.readString(Paths.get(cheminSource));
		String chiffré  = chiffrer(contenu);
		Files.writeString(Paths.get(cheminDest), chiffré);
	}

	/**
	 * Déchiffre un fichier .enc et retourne le contenu texte.
	 *
	 * Exemple :
	 *   String json = aes.dechiffrerFichier("app/data/lots.json.enc");
	 */
	public String dechiffrerFichier(String cheminFichier) throws Exception
	{
		String base64 = Files.readString(Paths.get(cheminFichier)).trim();
		return dechiffrer(base64);
	}
}