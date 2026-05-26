package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import app.securite.ChiffrementAES;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — version finale avec tous les correctifs
 *
 *  CORRECTIFS APPLIQUÉS :
 *  ─────────────────────
 *  #1 Chemins relatifs  → CheminApp.resoudre() ancre les chemins sur le
 *                         dossier du JAR, pas sur le répertoire courant.
 *                         Sans ça, le serveur ne trouve plus app/data/ si
 *                         on ne le lance pas depuis exactement le bon dossier.
 *
 *  #2 CORS              → Headers Access-Control-* ajoutés sur chaque réponse
 *                         via rep(). Permet à une future interface web de
 *                         contacter ce serveur sans être bloquée par le navigateur.
 *
 *  #3 Sécurité HTTP     → Avertissement console au démarrage rappelant que
 *                         les tokens voyagent en clair. Solution complète = nginx+HTTPS.
 *
 *  #4 Mode headless     → GraphicsEnvironment.isHeadless() détecte si on tourne
 *                         sans écran (Linux, service). Dans ce cas FenetreServeur
 *                         n'est jamais instanciée et un menu console la remplace.
 *
 *  #5 ReadWriteLock     → Remplace synchronized(verrou) unique par un verrou
 *                         lecture/écriture : plusieurs GET simultanés autorisés,
 *                         seuls les POST sont exclusifs.
 *
 *  #6 Timeout clients   → Passé de 10s à 30s. Les déconnexions sont loguées.
 *
 *  #7 compile.list      → Voir le fichier compile.list à la racine du projet.
 *
 *  #8 JsonSerialiser    → getString() et esc() mis à jour pour gérer tous les
 *                         caractères d'échappement JSON (\t, \r, \ uXXXX, etc.)
 *
 *  #9 Chiffrement AES   → Les réponses HTTP (200) sont chiffrées en AES-256-CBC.
 *                         Les corps de requêtes POST sont déchiffrés à la réception.
 *                         Les fichiers JSON sur le disque sont également chiffrés.
 *                         La clé est stockée dans secret.key (généré au premier démarrage).
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
	// ── Métier et persistance ─────────────────────────────────────────────
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;

	// Chemins vers les fichiers JSON courants (mis à jour après chaque sauvegarde)
	private String cheminLotsJson;
	private String cheminSocietesJson;

	// Semaine actuellement chargée (affichée dans FenetreServeur)
	private volatile String semaineActive = "";

	// Port d'écoute HTTP — fixé à 8080, doit être ouvert dans le firewall
	private static final int PORT = 8080;

	// ── CORRECTIF #5 — ReadWriteLock ─────────────────────────────────────
	// Un verrou classique synchronized(verrou) bloque TOUT le monde,
	// même les simples lectures (GET /lots, GET /version).
	//
	// ReadWriteLock distingue deux cas :
	//   readLock()  → plusieurs threads peuvent lire EN MÊME TEMPS
	//   writeLock() → un seul thread écrit, tous les autres attendent
	//
	// Règle d'utilisation dans ce fichier :
	//   Handler GET  → rwLock.readLock().lock()  / unlock() dans finally
	//   Handler POST → rwLock.writeLock().lock() / unlock() dans finally
	//
	// IMPORTANT : toujours mettre unlock() dans un bloc finally{} pour
	// garantir la libération même en cas d'exception. Sans ça, le serveur
	// se fige définitivement si un handler plante avec le verrou pris.
	private final java.util.concurrent.locks.ReadWriteLock rwLock =
		new java.util.concurrent.locks.ReentrantReadWriteLock();

	// Numéro de version incrémenté à chaque modification des données.
	// Les clients le comparent via /version pour détecter les changements
	// et déclencher un rechargement. Volatile car lu par plusieurs threads.
	private volatile long versionDonnees = System.currentTimeMillis();

	// ── CORRECTIF #6 — Suivi des clients connectés ───────────────────────
	// Chaque requête authentifiée met à jour l'horodatage de l'IP émettrice.
	// getNbClientsConnectes() purge les entrées dont le dernier contact
	// remonte à plus de TIMEOUT_CLIENT_MS. Passé à 30s (l'original était
	// 10s, trop court pour les clients sur WiFi ou en opération longue).
	private final Map<String, Long> clientsActifs   = new ConcurrentHashMap<>();
	private static final long       TIMEOUT_CLIENT_MS = 30_000;

	// ── CORRECTIF #4 — Mode headless ─────────────────────────────────────
	// Sur un serveur Linux sans affichage (pas de X11, service systemd,
	// conteneur Docker...), toute instanciation Swing lève HeadlessException.
	// On détecte cette situation UNE SEULE FOIS au chargement de la classe.
	// Utilisé ensuite pour choisir entre FenetreServeur et menu console.
	private static final boolean HEADLESS = GraphicsEnvironment.isHeadless();

	// ── CORRECTIF #9 — Chiffrement AES ───────────────────────────────────
	// Instance partagée de l'algorithme de chiffrement.
	// Initialisée dans le constructeur depuis le fichier secret.key.
	// null si le chiffrement n'a pas pu être activé (on continue sans).
	private ChiffrementAES aes;

	// ══════════════════════════════════════════════════════════════════════
	//  SÉCURITÉ — Gestion des sessions
	// ══════════════════════════════════════════════════════════════════════

	// Durée de vie d'un token de session : 4 heures.
	// Après ce délai, le serveur renvoie 401 et le client est redirigé
	// vers la fenêtre de connexion.
	private static final long TOKEN_TTL_MS = 4 * 60 * 60 * 1000L;

	// Map token → informations de session (identifiant, rôle, date de création)
	private final Map<String, SessionInfo> sessions     = new ConcurrentHashMap<>();

	// Générateur cryptographiquement sûr pour les tokens et les IVs AES.
	// SecureRandom est thread-safe, une seule instance suffit.
	private final SecureRandom rng = new SecureRandom();

	// Compteur d'échecs de login par IP pour le rate-limiting.
	// Au-delà de MAX_ECHECS tentatives, l'IP est bloquée BLOCAGE_MS millisecondes.
	private final Map<String, Integer> loginEchecs  = new ConcurrentHashMap<>();
	private final Map<String, Long>    loginBlocage = new ConcurrentHashMap<>();
	private static final int  MAX_ECHECS = 5;
	private static final long BLOCAGE_MS = 5 * 60 * 1000L; // 5 minutes

	/**
	 * Données associées à un token de session actif.
	 * Stockées côté serveur uniquement, jamais transmises au client.
	 */
	private static class SessionInfo
	{
		final String  identifiant; // "PAM" ou nom de société
		final boolean accesPAM;   // true = droits administrateur complets
		final long    createdAt;  // timestamp de création pour calcul expiration

		SessionInfo(String id, boolean pam)
		{
			this.identifiant = id;
			this.accesPAM    = pam;
			this.createdAt   = System.currentTimeMillis();
		}

		/** Retourne true si le token a dépassé TOKEN_TTL_MS sans être renouvelé. */
		boolean estExpire()
		{
			return System.currentTimeMillis() - createdAt > TOKEN_TTL_MS;
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR — initialisation complète du serveur
	// ══════════════════════════════════════════════════════════════════════

	public ServeurHTTP() throws Exception
	{
		this.metier     = new PlanningGlobal();
		this.savDonnees = new DonneesSauvegarder();

		// ── CORRECTIF #1 — Ancrage des chemins ───────────────────────────
		// CheminApp.resoudre() calcule le chemin absolu depuis le dossier
		// du JAR ou du projet, et non depuis System.getProperty("user.dir")
		// qui peut être n'importe quel dossier selon comment on a lancé Java.
		this.cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
		this.cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");

		log("[Serveur] Racine détectée : " + CheminApp.getBaseDir());

		// ── CORRECTIF #9 — Initialisation du chiffrement AES ─────────────
		// ChiffrementAES.chargerOuCreer() :
		//   - Si secret.key existe → charge la clé existante
		//   - Sinon → génère une clé AES-256 aléatoire et la sauvegarde
		// savDonnees.setCrypte(aes) active le chiffrement transparent sur
		// toutes les lectures/écritures de fichiers JSON (lots.json, societes.json).
		//
		// ATTENTION : si secret.key est supprimé, les fichiers JSON chiffrés
		// sur le disque deviennent définitivement illisibles.
		try
		{
			this.aes = ChiffrementAES.chargerOuCreer(CheminApp.resoudre("secret.key"));
			this.savDonnees.setCrypte(aes);
			log("[Serveur] Chiffrement AES-256 activé.");
		}
		catch (Exception e)
		{
			log("[Serveur] AVERTISSEMENT : chiffrement désactivé (" + e.getMessage() + ")");
			log("[Serveur] Les données seront stockées en clair sur le disque.");
		}

		// ── Chargement des données initiales ─────────────────────────────
		// On essaie de charger les JSON existants. Si le dossier n'existe pas
		// encore (première exécution), on démarre avec un état vide sans planter.
		try
		{
			savDonnees.charger(metier, CheminApp.resoudre("app/data/courutilisation"));
			log("[Serveur] " + metier.getLots().size() + " lots chargés.");
			detecterSemaineActive();
		}
		catch (Exception e)
		{
			log("[Serveur] Aucun chargement initial : " + e.getMessage());
			log("[Serveur] Démarrage avec un état vide.");
		}

		// ── CORRECTIF #3 — Avertissement sécurité HTTP ───────────────────
		// com.sun.net.httpserver ne supporte pas TLS nativement.
		// Les tokens de session voyagent donc en clair sur le réseau.
		// Sur un LAN d'entreprise fermé c'est acceptable.
		// Pour sécuriser davantage : installer nginx en reverse proxy avec
		// un certificat SSL et pointer les clients vers https://IP:443.
		log("");
		log("╔══════════════════════════════════════════════════╗");
		log("║  AVERTISSEMENT SÉCURITÉ                          ║");
		log("║  Ce serveur tourne en HTTP (non chiffré).        ║");
		log("║  Les tokens de session voyagent en clair.        ║");
		log("║  Sur réseau local d'entreprise : tolérable.      ║");
		log("║  Sur internet : utilisez nginx + HTTPS.          ║");
		log("╚══════════════════════════════════════════════════╝");
		log("");

		// ── Enregistrement des routes HTTP ───────────────────────────────
		// Chaque context associe un chemin URL à un handler.
		// L'exécuteur newCachedThreadPool() crée un thread par requête entrante,
		// ce qui permet à plusieurs clients d'être traités en parallèle.
		// Le ReadWriteLock (correctif #5) synchronise ensuite l'accès aux données.
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// Route publique — pas de token requis pour se connecter
		server.createContext("/login",             ex -> new LoginHandler()           .handle(ex));

		// Route publique — fournit la clé AES au client APRÈS authentification
		// (le token est quand même vérifié, mais la réponse n'est pas chiffrée
		// car le client n'a pas encore la clé)
		server.createContext("/cle",               ex -> new CleHandler()             .handle(ex));

		// Routes lots — toutes protégées par token
		server.createContext("/lots",              ex -> new GetLotsHandler()         .handle(ex));
		server.createContext("/lots/ajouter",      ex -> new AjouterLotHandler()      .handle(ex));
		server.createContext("/lots/supprimer",    ex -> new SupprimerLotHandler()    .handle(ex));
		server.createContext("/lots/modifier",     ex -> new ModifierLotHandler()     .handle(ex));
		server.createContext("/lots/affecter",     ex -> new AffecterLotHandler()     .handle(ex));
		server.createContext("/lots/desaffecter",  ex -> new DesaffecterLotHandler()  .handle(ex));
		server.createContext("/lots/suiviprod",    ex -> new SuiviProdHandler()       .handle(ex));
		server.createContext("/lots/commencer",    ex -> new CommencerLotHandler()    .handle(ex));
		server.createContext("/lots/annuler",      ex -> new AnnulerLotHandler()      .handle(ex));
		server.createContext("/lots/terminer",     ex -> new TerminerLotHandler()     .handle(ex));
		server.createContext("/lots/phase",        ex -> new ModifierPhaseHandler()   .handle(ex));

		// Routes sociétés / ACE
		server.createContext("/societes",          ex -> new GetSocietesHandler()     .handle(ex));
		server.createContext("/societes/modifier", ex -> new ModifierSocieteHandler() .handle(ex));
		server.createContext("/societes/aces",     ex -> new ModifierAcesHandler()    .handle(ex));

		// Routes système
		server.createContext("/ficheroute/",       ex -> new FicheRouteHandler()      .handle(ex));
		server.createContext("/sauvegarder",       ex -> new SauvegarderHandler()     .handle(ex));
		server.createContext("/heure/nouvelle",    ex -> new NouvelleHeureHandler()   .handle(ex));
		server.createContext("/semaine/sup",       ex -> new SemaineSupHandler()      .handle(ex));
		server.createContext("/autosave/lots",     ex -> new AutoSaveLotsHandler()    .handle(ex));
		server.createContext("/autosave/societes", ex -> new AutoSaveSocietesHandler().handle(ex));
		server.createContext("/version",           ex -> new VersionHandler()         .handle(ex));

		// Routes bloquées côté serveur (actions réservées à FenetreServeur)
		server.createContext("/charger",           ex -> new ChargerBloqueHandler()   .handle(ex));
		server.createContext("/nouveaux",          ex -> new NouveauxBloqueHandler()  .handle(ex));

		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		log("[Serveur] Démarré sur le port " + PORT);

		// ── CORRECTIF #4 — Démarrage conditionnel de l'IHM ───────────────
		// Si on a un écran → FenetreServeur Swing normale.
		// Si on n'en a pas (Linux headless) → menu console dans le terminal.
		if (!HEADLESS)
		{
			javax.swing.SwingUtilities.invokeLater(() -> new app.ihm.serveur.FenetreServeur(this));
		}
		else
		{
			log("[Serveur] Mode HEADLESS — interface console activée.");
			log("[Serveur] Tapez 'help' pour voir les commandes disponibles.");
			demarrerConsoleHeadless();
		}

		// ── Thread de nettoyage des sessions expirées ─────────────────────
		// Tourne toutes les 30 minutes, retire les tokens dont createdAt
		// dépasse TOKEN_TTL_MS. Daemon = s'arrête automatiquement avec la JVM.
		Thread cleaner = new Thread(() -> {
			while (true) {
				try { Thread.sleep(30 * 60 * 1000L); }
				catch (InterruptedException e) { break; }
				int avant = sessions.size();
				sessions.entrySet().removeIf(e -> e.getValue().estExpire());
				int apres = sessions.size();
				if (avant != apres)
					log("[Serveur] " + (avant - apres) + " session(s) expirée(s) purgée(s).");
			}
		});
		cleaner.setDaemon(true);
		cleaner.setName("session-cleaner");
		cleaner.start();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CORRECTIF #4 — Console headless
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Menu textuel interactif pour piloter le serveur sans interface graphique.
	 * Remplace FenetreServeur quand HEADLESS est true.
	 * Tourne dans son propre thread NON-daemon pour maintenir le processus
	 * en vie même après le démarrage du serveur HTTP.
	 */
	private void demarrerConsoleHeadless()
	{
		Thread t = new Thread(() -> {
			java.util.Scanner sc = new java.util.Scanner(System.in);
			afficherAideConsole();
			while (true)
			{
				System.out.print("\n[Serveur] > ");
				if (!sc.hasNextLine()) break;
				String cmd = sc.nextLine().trim().toLowerCase();
				switch (cmd)
				{
					case "status":
						log("  Semaine active : " + (semaineActive.isBlank() ? "—" : semaineActive));
						log("  Clients actifs : " + getNbClientsConnectes());
						log("  Heures sup     : " + (PlanningGlobal.estHeureSup ? "OUI" : "non"));
						log("  Lots chargés   : " + metier.getLots().size());
						log("  Sociétés       : " + metier.getSocietes().size());
						log("  Chiffrement    : " + (aes != null ? "AES-256 actif" : "désactivé"));
						break;
					case "heures":
						toggleHeuresSup();
						log("  Heures sup → " + (PlanningGlobal.estHeureSup ? "ACTIVÉES" : "désactivées"));
						break;
					case "sauvegarder":
						System.out.print("  Dossier de destination : ");
						String dossier = sc.nextLine().trim();
						System.out.print("  Numéro de semaine      : ");
						String sem = sc.nextLine().trim();
						try {
							sauvegarderSemaine(dossier, sem);
							log("  Sauvegarde effectuée dans S" + sem);
						} catch (Exception e) { log("  ERREUR : " + e.getMessage()); }
						break;
					case "help": case "aide":
						afficherAideConsole();
						break;
					case "quitter": case "exit": case "quit":
						log("[Serveur] Arrêt demandé. Au revoir.");
						System.exit(0);
						break;
					default:
						log("  Commande inconnue. Tapez 'help'.");
				}
			}
		});
		t.setDaemon(false); // non-daemon : maintient le processus en vie
		t.setName("console-headless");
		t.start();
	}

	private void afficherAideConsole()
	{
		log("  Commandes disponibles :");
		log("    status      → état du serveur (semaine, clients, heures sup)");
		log("    heures      → activer/désactiver les heures supplémentaires");
		log("    sauvegarder → sauvegarder les données dans un dossier par semaine");
		log("    quitter     → arrêter le serveur proprement");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  MÉTHODES PUBLIQUES — appelées par FenetreServeur ou la console
	// ══════════════════════════════════════════════════════════════════════

	/** Charge une semaine depuis un dossier de sauvegarde existant. */
	public void chargerSemaine(String chemin) throws Exception
	{
		rwLock.writeLock().lock();
		try {
			savDonnees.charger(metier, chemin);
			cheminLotsJson     = chemin + "/lots.json";
			cheminSocietesJson = chemin + "/societes.json";
			versionDonnees     = System.currentTimeMillis();
			detecterSemaineActive();
		} finally {
			rwLock.writeLock().unlock();
		}
		log("[Serveur] Semaine chargée : " + chemin);
	}

	/** Charge une nouvelle semaine depuis un fichier Excel (mode graphique uniquement). */
	public void nouvelleSemaine(java.awt.Component parent) throws Exception
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des lots (XLSX / XLSM)");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File(CheminApp.resoudre("app/data"));
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String xlsxLots = fc.getSelectedFile().getAbsolutePath();

		// Extraire le numéro de semaine depuis le premier lot du fichier
		ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsxLots);
		int semaine = 0;
		if (!tempLots.isEmpty()) {
			String sem = tempLots.get(0).getSemaine();
			try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
			catch (NumberFormatException ignored) {}
		}

		fc.setDialogTitle("Sélectionner le fichier des heures ACE (ou Annuler pour ignorer)");
		String xlsxHeures = fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath() : xlsxLots;

		rwLock.writeLock().lock();
		try {
			metier.chargerDepuisExcel(xlsxLots,
				CheminApp.resoudre("app/data/pastouche/societes.json"), semaine, xlsxHeures);
			cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
			cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");
			save();
			versionDonnees = System.currentTimeMillis();
			detecterSemaineActive();
		} finally {
			rwLock.writeLock().unlock();
		}
		log("[Serveur] Nouvelle semaine chargée depuis Excel.");
	}

	/** Sauvegarde les données dans un sous-dossier S<numSemaine>. */
	public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
	{
		rwLock.writeLock().lock();
		try {
			String dossier = cheminDossier + "/S" + numSemaine;
			java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
			savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
			cheminLotsJson     = dossier + "/lots.json";
			cheminSocietesJson = dossier + "/societes.json";
			semaineActive      = numSemaine;
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	/** Active ou désactive les heures supplémentaires. */
	public void toggleHeuresSup()
	{
		rwLock.writeLock().lock();
		try { metier.setestHeureSup(); save(); }
		finally { rwLock.writeLock().unlock(); }
		log("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
	}

	public String getSemaineActive() { return semaineActive; }

	/**
	 * Retourne le nombre de clients ayant envoyé une requête
	 * dans les TIMEOUT_CLIENT_MS dernières millisecondes.
	 * Purge automatiquement les entrées expirées.
	 */
	public int getNbClientsConnectes()
	{
		long now = System.currentTimeMillis();
		clientsActifs.entrySet().removeIf(e -> {
			boolean expire = now - e.getValue() > TIMEOUT_CLIENT_MS;
			if (expire) log("[Serveur] Client déconnecté (timeout) : " + e.getKey());
			return expire;
		});
		return clientsActifs.size();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CORRECTIF #2 — En-têtes CORS
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Ajoute les headers CORS à chaque réponse HTTP.
	 * Sans ces headers, un navigateur web bloquerait toutes les requêtes
	 * vers ce serveur avec l'erreur "has been blocked by CORS policy".
	 *
	 * Access-Control-Allow-Origin "*" autorise tout le monde.
	 * Pour restreindre à une IP précise : remplacer par "http://192.168.1.X"
	 */
	private static void ajouterHeadersCORS(HttpExchange ex)
	{
		ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
		ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
		ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CORRECTIF #9 — rep() et lire() avec chiffrement AES
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Envoie une réponse HTTP au client.
	 * Cette méthode centrale est utilisée par TOUS les handlers.
	 *
	 * Chiffrement (correctif #9) :
	 *   - Les réponses 200 sont chiffrées en AES si aes != null
	 *   - Exception : /login et /cle ne sont PAS chiffrées car le client
	 *     n'a pas encore reçu la clé à ce moment-là
	 *   - Les erreurs (4xx, 5xx) ne sont pas chiffrées pour rester lisibles
	 *     lors du debug (elles ne contiennent pas de données sensibles)
	 *
	 * CORS (correctif #2) :
	 *   - ajouterHeadersCORS() est appelé sur chaque réponse
	 */
	private void rep(HttpExchange ex, int code, String body) throws IOException
	{
		// CORRECTIF #2 : headers CORS sur toutes les réponses
		ajouterHeadersCORS(ex);

		// CORRECTIF #9 : chiffrer les réponses 200 sauf sur les routes publiques
		String path = ex.getRequestURI().getPath();
		boolean routePublique = path.equals("/login") || path.equals("/cle");

		String contenuFinal = body;
		if (aes != null && !routePublique && code == 200)
		{
			try {
				contenuFinal = aes.chiffrer(body);
				// Le contenu chiffré est du Base64, plus du JSON
				ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			}
			catch (Exception e)
			{
				// Si le chiffrement échoue, on envoie quand même en clair
				// et on pose un header pour que le client puisse le détecter
				ex.getResponseHeaders().set("X-Encrypted", "false");
				log("[AES] Erreur chiffrement réponse : " + e.getMessage());
				ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
			}
		}
		else
		{
			ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		}

		byte[] bytes = contenuFinal.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
	}

	/**
	 * Lit le corps de la requête HTTP entrante.
	 * Si AES est actif, tente de déchiffrer le contenu.
	 *
	 * Tolérance : si le déchiffrement échoue (client ancien, requête brute),
	 * on retourne le contenu tel quel plutôt que de rejeter la requête.
	 * Un message est loggué pour signaler la situation.
	 */
	private String lire(HttpExchange ex) throws IOException
	{
		String brut = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

		// Pas de chiffrement actif ou body vide → retourner tel quel
		if (aes == null || brut.isBlank()) return brut;

		try {
			return aes.dechiffrer(brut);
		}
		catch (Exception e)
		{
			// Le body n'est pas chiffré (ancien client ou test manuel avec curl)
			log("[AES] Body non chiffré reçu depuis "
				+ ex.getRemoteAddress().getAddress().getHostAddress()
				+ " — traité comme JSON brut.");
			return brut;
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  Logger unifié
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Point d'entrée unique pour tous les logs du serveur.
	 * En mode graphique, FenetreServeur lit l'état via refresh() — les logs
	 * vont quand même sur System.out pour les traces en console.
	 * En mode headless, c'est le seul canal de communication avec l'opérateur.
	 */
	private static void log(String msg)
	{
		System.out.println(msg);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  SÉCURITÉ — Helpers tokens et sessions
	// ══════════════════════════════════════════════════════════════════════

	/** Génère un token de session aléatoire de 32 octets encodé en Base64 URL-safe. */
	private String genererToken()
	{
		byte[] b = new byte[32];
		rng.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	/**
	 * Vérifie le header X-Auth-Token de la requête.
	 * Retourne les infos de session si le token est valide et non expiré,
	 * null sinon (token absent, inconnu, ou expiré).
	 */
	private SessionInfo verifierToken(HttpExchange ex)
	{
		String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
		if (token == null || token.isBlank()) return null;
		SessionInfo info = sessions.get(token);
		if (info == null) return null;
		if (info.estExpire()) { sessions.remove(token); return null; }
		return info;
	}

	/**
	 * Vérifie le token et renvoie 401 si absent ou invalide.
	 * Retourne true si authentifié, false si la réponse 401 a déjà été envoyée.
	 * Tous les handlers protégés appellent cette méthode en premier.
	 */
	private boolean exigerToken(HttpExchange ex) throws IOException
	{
		SessionInfo info = verifierToken(ex);
		if (info == null) {
			rep(ex, 401, "{\"err\":\"Non authentifié. Connectez-vous via /login.\"}");
			return false;
		}
		// Met à jour l'horodatage du client pour le compteur de connectés
		enregistrerClient(ex);
		return true;
	}

	/** Retourne true si l'IP est en période de blocage suite à trop d'échecs. */
	private boolean estBloquee(String ip)
	{
		Long fin = loginBlocage.get(ip);
		if (fin == null) return false;
		if (System.currentTimeMillis() < fin) return true;
		// Blocage expiré : nettoyer les compteurs
		loginBlocage.remove(ip);
		loginEchecs.remove(ip);
		return false;
	}

	/**
	 * Incrémente le compteur d'échecs pour cette IP.
	 * Au 5e échec, bloque l'IP pendant 5 minutes.
	 */
	private void enregistrerEchecLogin(String ip)
	{
		int n = loginEchecs.merge(ip, 1, Integer::sum);
		if (n >= MAX_ECHECS) {
			loginBlocage.put(ip, System.currentTimeMillis() + BLOCAGE_MS);
			loginEchecs.put(ip, 0);
			log("[Sécurité] IP bloquée 5 min après " + MAX_ECHECS + " échecs : " + ip);
		}
	}

	/** Met à jour l'horodatage de dernière activité du client (pour getNbClientsConnectes). */
	private void enregistrerClient(HttpExchange ex)
	{
		clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(),
			System.currentTimeMillis());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS HTTP
	//
	//  Convention de verrouillage (correctif #5) :
	//    GET  (lecture)  → rwLock.readLock().lock()  — lectures simultanées OK
	//    POST (écriture) → rwLock.writeLock().lock() — exclusif
	//
	//  Le unlock() EST TOUJOURS dans un bloc finally{} pour garantir
	//  la libération même si une exception est levée dans le handler.
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * POST /login — authentification par identifiant uniquement, sans mot de passe.
	 * Route publique : pas de token requis, corps et réponse non chiffrés.
	 *
	 * Corps attendu : {"identifiant":"PAM"}
	 * Réponse 200   : {"token":"...","accesPAM":true}
	 * Réponse 401   : identifiant inconnu
	 * Réponse 429   : IP bloquée après trop de tentatives
	 */
	class LoginHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
				rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return;
			}
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			if (estBloquee(ip)) {
				rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return;
			}
			try {
				// Lecture brute — pas de déchiffrement, le client n'a pas encore la clé AES
				String c           = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				String identifiant = JsonSerialiser.extraireString(c, "identifiant");

				if (!validerIdentite(identifiant, null)) {
					enregistrerEchecLogin(ip);
					rep(ex, 401, "{\"err\":\"Identifiant non reconnu.\"}");
					return;
				}
				loginEchecs.remove(ip);
				boolean pam   = "PAM".equalsIgnoreCase(identifiant.trim());
				String  token = genererToken();
				sessions.put(token, new SessionInfo(identifiant.trim().toUpperCase(), pam));
				rep(ex, 200, "{\"token\":" + JsonSerialiser.esc(token) + ",\"accesPAM\":" + pam + "}");
				log("[Serveur] Connexion : " + identifiant + " depuis " + ip);
			} catch (Exception e) {
				rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}");
			}
		}
	}


	/**
	 * GET /cle — transmet la clé AES au client authentifié.
	 * Route protégée par token mais réponse NON chiffrée (le client n'a
	 * pas encore la clé pour déchiffrer la réponse).
	 *
	 * Réponse 200 : {"cle":"<Base64 de la clé AES-256>"}
	 * Réponse 503 : chiffrement non initialisé côté serveur
	 */
	class CleHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			if (aes == null) {
				rep(ex, 503, "{\"err\":\"Chiffrement non initialisé sur le serveur.\"}");
				return;
			}
			// Réponse JSON brute, pas chiffrée (routePublique = /cle dans rep())
			rep(ex, 200, "{\"cle\":\"" + aes.cleEnBase64() + "\"}");
		}
	}

	/**
	 * GET /lots — retourne la liste complète des lots au format JSON.
	 * Utilise readLock : plusieurs clients peuvent appeler cette route simultanément.
	 */
	class GetLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.readLock().lock();
			try {
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} finally {
				rwLock.readLock().unlock();
			}
		}
	}

	/**
	 * GET /societes — retourne la liste des sociétés avec leurs ACEs et lots affectés.
	 * Utilise readLock.
	 */
	class GetSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.readLock().lock();
			try {
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} finally {
				rwLock.readLock().unlock();
			}
		}
	}

	/**
	 * GET /version — retourne le numéro de version des données, l'état
	 * des heures sup et la semaine active.
	 * Les clients appellent cette route toutes les 3s (polling) pour détecter
	 * les changements et déclencher un rechargement complet si nécessaire.
	 */
	class VersionHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex); // compte comme activité client
			rwLock.readLock().lock();
			try {
				rep(ex, 200, "{\"v\":\"" + versionDonnees
					+ "\",\"heureSup\":" + PlanningGlobal.estHeureSup
					+ ",\"semaine\":"    + JsonSerialiser.esc(semaineActive) + "}");
			} finally {
				rwLock.readLock().unlock();
			}
		}
	}

	/**
	 * POST /lots/ajouter — ajoute un nouveau lot (PAM uniquement).
	 * Corps attendu : JSON d'un lot complet (même format que serialiserLotSeul).
	 */
	class AjouterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				Lot lot = JsonSerialiser.deserialiserLot(lire(ex));
				if (lot == null) { rep(ex, 400, "{\"err\":\"JSON invalide\"}"); return; }
				metier.ajouterLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/supprimer — supprime un lot par son numCDE (PAM uniquement).
	 * Corps attendu : {"numCDE":12345}
	 */
	class SupprimerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.supprimerLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/modifier — modifie les champs d'un lot (PAM uniquement).
	 * Corps attendu : JSON avec numCDE + tous les champs modifiables.
	 */
	class ModifierLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c   = lire(ex);
				Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.modifierLot(lot,
					JsonSerialiser.extraireString(c, "typologie"),
					JsonSerialiser.extraireString(c, "affaire"),
					JsonSerialiser.extraireInt   (c, "nbPieces"),
					JsonSerialiser.extraireDouble (c, "cadence"),
					JsonSerialiser.extraireInt   (c, "valeurVente"),
					JsonSerialiser.extraireString(c, "statut"),
					JsonSerialiser.extraireString(c, "statutEchant"),
					JsonSerialiser.extraireString(c, "semaine"),
					JsonSerialiser.extraireInt   (c, "priorite"),
					JsonSerialiser.extraireString(c, "lotACharge"),
					JsonSerialiser.extraireString(c, "emplacement"),
					JsonSerialiser.extraireBool  (c, "sousDouane"),
					JsonSerialiser.extraireString(c, "dateReception"),
					JsonSerialiser.extraireString(c, "datePaiement"),
					JsonSerialiser.extraireString(c, "commentaire"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/phase — met à jour les phases de production d'un lot.
	 * Accessible à tous les clients authentifiés (sociétés incluses).
	 * Corps attendu : {"numCDE":12345,"preTri":true,"surPiste":false,...}
	 */
	class ModifierPhaseHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c   = lire(ex);
				Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.modifierPhase(lot,
					JsonSerialiser.extraireBool(c, "preTri"),
					JsonSerialiser.extraireBool(c, "surPiste"),
					JsonSerialiser.extraireBool(c, "sortieEtiq"),
					JsonSerialiser.extraireBool(c, "tri"),
					JsonSerialiser.extraireBool(c, "finit"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/affecter — affecte un lot à une société et une ACE (PAM uniquement).
	 * Corps attendu : {"numCDE":12345,"societe":"EUP","ace":"ACE1"}
	 * Retourne les lots ET les sociétés mis à jour (dual response).
	 */
	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Affectation réservée à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String  c       = lire(ex);
				Lot     lot     = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				Societe societe = findSociete(JsonSerialiser.extraireString(c, "societe"));
				String  aceNom  = JsonSerialiser.extraireString(c, "ace");
				if (lot == null || societe == null) { rep(ex, 404, "{\"err\":\"lot ou société introuvable\"}"); return; }
				Ace ace = societe.getAces().stream()
					.filter(a -> a.getNom().equals(aceNom)).findFirst().orElse(null);
				boolean ok = metier.affecterLot(lot, societe, ace);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, ok ? 200 : 400,
					"{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
					+ ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/desaffecter — retire l'affectation d'un lot (PAM uniquement).
	 * Corps attendu : {"numCDE":12345}
	 */
	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Désaffectation réservée à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.desaffecterLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200,
					"{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
					+ ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /lots/suiviprod — met à jour le nombre de pièces étiquetées/réparties.
	 * Accessible à tous les clients authentifiés.
	 * Corps attendu : {"numCDE":12345,"nbPieceEtiq":100,"nbPieceRepart":50}
	 * Validation : les valeurs ne peuvent pas dépasser nbPieces du lot ni être négatives.
	 */
	class SuiviProdHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c      = lire(ex);
				Lot    lot    = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				int    etiq   = JsonSerialiser.extraireInt(c, "nbPieceEtiq");
				int    repart = JsonSerialiser.extraireInt(c, "nbPieceRepart");
				if (etiq < 0 || repart < 0) { rep(ex, 400, "{\"err\":\"Valeurs négatives non autorisées\"}"); return; }
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				if (etiq   <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceEtiq(etiq);
				if (repart <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceRepart(repart);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /lots/commencer — marque un lot comme démarré, calcule la date de fin théorique. */
	class CommencerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.commencerLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /lots/annuler — annule le démarrage d'un lot. */
	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.annulerLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /lots/terminer — marque un lot comme terminé à 100%. */
	class TerminerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.marquerLotTermine(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /societes/modifier — modifie les infos d'une société (PAM uniquement).
	 * Corps attendu : {"nom":"EUP","ce":"Dupont","totalHeuresCE":200,"effectif":15}
	 */
	class ModifierSocieteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String  c   = lire(ex);
				Societe soc = findSociete(JsonSerialiser.extraireString(c, "nom"));
				if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
				metier.modifierSociete(soc,
					JsonSerialiser.extraireString(c, "nom"),
					JsonSerialiser.extraireString(c, "ce"),
					JsonSerialiser.extraireInt   (c, "totalHeuresCE"),
					JsonSerialiser.extraireInt   (c, "effectif"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /societes/aces — met à jour la liste des ACEs d'une société (PAM uniquement).
	 * Corps attendu : {"societe":"EUP","aces":[{"nom":"ACE1","nbPers":5,"effectif":4},...]}
	 */
	class ModifierAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String  c      = lire(ex);
				Societe soc    = findSociete(JsonSerialiser.extraireString(c, "societe"));
				if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
				ArrayList<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(
					JsonSerialiser.extraireBloc(c, "\"aces\""));
				List<Ace> aces = soc.getAces();
				int min = Math.min(aces.size(), nouvellesAces.size());
				for (int i = 0; i < min; i++)
					metier.modifierAce(aces.get(i), nouvellesAces.get(i).getNom(),
						nouvellesAces.get(i).getNbPers(), nouvellesAces.get(i).getEffectifActuel());
				for (int i = aces.size()-1; i >= nouvellesAces.size(); i--) aces.remove(i);
				for (int i = min; i < nouvellesAces.size(); i++) {
					Ace n = nouvellesAces.get(i);
					aces.add(new Ace(n.getNom(), n.getNbPers(), n.getEffectifActuel()));
				}
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * GET /ficheroute/{nomSociete} — génère et retourne la fiche de route d'une société.
	 * Le nom de la société est dans l'URL, URL-encodé si nécessaire.
	 */
	class FicheRouteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			String  nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
			Societe s   = findSociete(nom);
			if (s == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
			rwLock.readLock().lock();
			try {
				rep(ex, 200, JsonSerialiser.serialiserFicheRoute(metier.genererFicheRoute(s)));
			} finally {
				rwLock.readLock().unlock();
			}
		}
	}

	/**
	 * POST /sauvegarder — sauvegarde dans un dossier S<semaine>.
	 * Corps attendu : {"chemin":"app/data/enregistrementparsemaine","semaine":"17"}
	 * Protection path traversal : refuse les chemins contenant ".."
	 */
	class SauvegarderHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c       = lire(ex);
				String chemin  = JsonSerialiser.extraireString(c, "chemin");
				String semaine = JsonSerialiser.extraireString(c, "semaine");
				if (chemin.contains("..")) { rep(ex, 400, "{\"err\":\"Chemin non autorisé (path traversal)\"}"); return; }
				String dossier = chemin + "/S" + semaine;
				java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
				savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
				savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
				cheminLotsJson     = dossier + "/lots.json";
				cheminSocietesJson = dossier + "/societes.json";
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * POST /heure/nouvelle — ajoute des heures à une ou plusieurs sociétés (PAM uniquement).
	 * Corps attendu : {"societes":[{"nom":"EUP","heures":8},{"nom":"LTX","heures":4}]}
	 * ou pour une seule : {"societe":"EUP","heures":8}
	 */
	class NouvelleHeureHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c      = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societe");
				if (nomSoc != null && !nomSoc.isEmpty()) {
					// Cas simple : une seule société
					Societe s = findSociete(nomSoc);
					if (s != null)
						s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
				} else {
					// Cas multiple : tableau de sociétés
					String bloc = JsonSerialiser.extraireBloc(c, "\"societes\"");
					if (bloc != null && !bloc.isEmpty()) {
						String[] entries = bloc.replace("[","").replace("]","").split("\\},\\{");
						for (String entry : entries) {
							entry = entry.replace("{","").replace("}","");
							String  nom    = JsonSerialiser.extraireString("{" + entry + "}", "nom");
							int     heures = JsonSerialiser.extraireInt   ("{" + entry + "}", "heures");
							Societe s      = findSociete(nom);
							if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + heures);
						}
					}
				}
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /semaine/sup — toggle les heures supplémentaires pour toutes les sociétés. */
	class SemaineSupHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try { metier.setestHeureSup(); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /autosave/lots — déclenche une sauvegarde automatique des lots. */
	class AutoSaveLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/** POST /autosave/societes — déclenche une sauvegarde automatique des sociétés. */
	class AutoSaveSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	/**
	 * Bloque la route /charger côté client réseau.
	 * Le chargement d'une semaine est réservé à FenetreServeur.
	 */
	class ChargerBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			log("[Serveur] Tentative bloquée /charger depuis "
				+ ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	/**
	 * Bloque la route /nouveaux côté client réseau.
	 * L'import de nouveaux lots Excel est réservé à FenetreServeur.
	 */
	class NouveauxBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			log("[Serveur] Tentative bloquée /nouveaux depuis "
				+ ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES INTERNES
	// ══════════════════════════════════════════════════════════════════════

	/** Recherche un lot par son numéro de commande. Retourne null si absent. */
	private Lot findLot(int numCDE)
	{
		return metier.getLots().stream()
			.filter(l -> l.getNumCDE() == numCDE)
			.findFirst().orElse(null);
	}

	/** Recherche une société par son nom exact. Retourne null si absente. */
	private Societe findSociete(String nom)
	{
		if (nom == null) return null;
		return metier.getSocietes().stream()
			.filter(s -> nom.equals(s.getNom()))
			.findFirst().orElse(null);
	}

	/**
	 * Sauvegarde immédiate des lots et sociétés sur le disque.
	 * Doit être appelé depuis un bloc writeLock (thread-safe).
	 * Met également à jour versionDonnees pour notifier les clients via /version.
	 */
	private void save()
	{
		try { savDonnees.sauvegarderLots    (metier.getLots(),     cheminLotsJson);     }
		catch (Exception e) { log("[Save] Erreur lots     : " + e.getMessage()); }
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
		catch (Exception e) { log("[Save] Erreur sociétés : " + e.getMessage()); }
		versionDonnees = System.currentTimeMillis();
	}

	/**
	 * Extrait et formate la semaine active depuis le premier lot chargé.
	 * Appelé après chaque chargement de données.
	 * Format attendu : "202617" → affiche "S17 / 2026"
	 */
	private void detecterSemaineActive()
	{
		if (!metier.getLots().isEmpty()) {
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
				semaineActive = sem.length() == 6
					? "S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
		}
	}

	/**
	 * Valide un couple identifiant/mot de passe.
	 *
	 * Actuellement les mots de passe sont en dur dans le code.
	 * Pour un déploiement plus sérieux, stocker des hash BCrypt dans
	 * un fichier externe et utiliser une bibliothèque comme jBCrypt.
	 */
	/**
	 * Valide l'identifiant reçu à la connexion.
	 *
	 * FenetreConnexionClient n'envoie pas de mot de passe — uniquement
	 * {"identifiant":"PAM"}. On valide donc sur l'identifiant seul,
	 * comme le faisait l'original du projet.
	 *
	 * Pour ajouter un vrai mot de passe plus tard :
	 *   1. Ajouter un JPasswordField dans FenetreConnexionClient
	 *   2. L'inclure dans le JSON envoyé
	 *   3. Réactiver la vérification motDePasse ici
	 */
	private boolean validerIdentite(String identifiant, String motDePasse)
	{
		if (identifiant == null || identifiant.isBlank()) return false;

		// PAM = administrateur, toujours autorisé
		if ("PAM".equalsIgnoreCase(identifiant.trim())) return true;

		// Société : l'identifiant doit correspondre au nom d'une société chargée
		return metier.getSocietes().stream()
			.anyMatch(s -> s.getNom() != null
				&& s.getNom().equalsIgnoreCase(identifiant.trim()));
	}

	// ── Point d'entrée ────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		try                 { new ServeurHTTP();   }
		catch (Exception e) { e.printStackTrace(); }
	}
}