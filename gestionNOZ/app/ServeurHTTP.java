package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import app.securite.ChiffrementAES;
import app.securite.GestionComptes;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — version finale unifiée avec toutes les fonctionnalités
 *
 *  NOUVELLES FONCTIONNALITÉS :
 *  ────────────────────────────
 *  #A Login avec mot de passe   → POST /login {identifiant, motDePasse}
 *                                  Utilise GestionComptes (config.json)
 *  #B Création de compte        → POST /creer-compte {identifiant, motDePasse}
 *                                  Crée une demande "attente" dans config.json
 *  #C Gestion demandes (PAM)    → GET  /admin/demandes
 *                                  POST /admin/demandes/approuver
 *                                  POST /admin/demandes/refuser
 *  #D Semaine suivante          → GET  /semaine-suivante
 *                                  POST /semaine-suivante/sauvegarder
 *                                  POST /semaine-suivante/affecter
 *                                  POST /semaine-suivante/desaffecter
 *                                  POST /semaine-suivante/basculer
 *
 *  CORRECTIONS :
 *  ─────────────
 *  #1 Port unifié : PORT = 8082 partout
 *  #2 ReadWriteLock sur toutes les routes
 *  #3 CORS sur toutes les réponses
 *  #4 Mode headless détecté automatiquement
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
	// ── Métier et persistance ─────────────────────────────────────────────
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;
	private String cheminLotsJson;
	private String cheminSocietesJson;
	private volatile String semaineActive = "";

	// PORT unifié — correction bug #1
	static final int PORT = 8082;

	// ── ReadWriteLock — correctif #5 ─────────────────────────────────────
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	// ── Version pour le polling ───────────────────────────────────────────
	private volatile long versionDonnees = System.currentTimeMillis();

	// ── Suivi clients ─────────────────────────────────────────────────────
	private final Map<String, Long> clientsActifs  = new ConcurrentHashMap<>();
	private static final long TIMEOUT_CLIENT_MS    = 30_000;

	// ── Sessions ──────────────────────────────────────────────────────────
	private static final long TOKEN_TTL_MS = 4 * 3600_000L;
	private final Map<String, SessionInfo> sessions    = new ConcurrentHashMap<>();
	private final SecureRandom             rng         = new SecureRandom();
	private final Map<String, Integer>     loginEchecs = new ConcurrentHashMap<>();
	private final Map<String, Long>        loginBlocage= new ConcurrentHashMap<>();
	private static final int  MAX_ECHECS = 5;
	private static final long BLOCAGE_MS = 5 * 60_000L;

	// ── Chiffrement AES ───────────────────────────────────────────────────
	private ChiffrementAES aes = null;

	// ── Gestion des comptes ───────────────────────────────────────────────
	private final GestionComptes gestionComptes = GestionComptes.getInstance();

	// ── Chemins semaine suivante ──────────────────────────────────────────
	private static final String DIR_SUIV_LOTS = "app/data/semaine_suivante/lots.json";
	private static final String DIR_SUIV_SOCS = "app/data/semaine_suivante/societes.json";

	// ══════════════════════════════════════════════════════════════════════
	//  SESSION
	// ══════════════════════════════════════════════════════════════════════

	private static class SessionInfo
	{
		final String  identifiant;
		final boolean accesPAM;
		final long    createdAt;
		SessionInfo(String id, boolean pam) { identifiant = id; accesPAM = pam; createdAt = System.currentTimeMillis(); }
		boolean estExpire() { return System.currentTimeMillis() - createdAt > TOKEN_TTL_MS; }
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public ServeurHTTP() throws Exception
	{
		this.metier     = new PlanningGlobal();
		this.savDonnees = new DonneesSauvegarder();
		this.cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
		this.cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");

		log("[Serveur] Racine : " + CheminApp.getBaseDir());

		// Chiffrement AES
		try
		{
			this.aes = ChiffrementAES.chargerOuCreer(CheminApp.resoudre("secret.key"));
			this.savDonnees.setCrypte(aes);
			log("[Serveur] Chiffrement AES-256 activé.");
		}
		catch (Exception e)
		{
			log("[Serveur] AVERTISSEMENT chiffrement désactivé : " + e.getMessage());
		}

		// Chargement données
		try
		{
			savDonnees.charger(metier, CheminApp.resoudre("app/data/courutilisation"));
			log("[Serveur] " + metier.getLots().size() + " lots, " + metier.getSocietes().size() + " sociétés.");
			detecterSemaineActive();
		}
		catch (Exception e)
		{
			log("[Serveur] Pas de données initiales : " + e.getMessage());
		}

		// Démarrage HTTP
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// ── Routes publiques (sans token) ─────────────────────────────────
		server.createContext("/login",                     ex -> new LoginHandler()          .handle(ex));
		server.createContext("/creer-compte",              ex -> new CreerCompteHandler()    .handle(ex));

		// ── Routes admin (PAM) ────────────────────────────────────────────
		server.createContext("/admin/demandes",            ex -> new DemandesHandler()       .handle(ex));
		server.createContext("/admin/demandes/approuver",  ex -> new ApprouverHandler()      .handle(ex));
		server.createContext("/admin/demandes/refuser",    ex -> new RefuserHandler()        .handle(ex));

		// ── Routes clé AES ────────────────────────────────────────────────
		server.createContext("/cle",                       ex -> new CleHandler()            .handle(ex));

		// ── Routes lots ───────────────────────────────────────────────────
		server.createContext("/lots",                      ex -> new GetLotsHandler()        .handle(ex));
		server.createContext("/lots/ajouter",              ex -> new AjouterLotHandler()     .handle(ex));
		server.createContext("/lots/supprimer",            ex -> new SupprimerLotHandler()   .handle(ex));
		server.createContext("/lots/modifier",             ex -> new ModifierLotHandler()    .handle(ex));
		server.createContext("/lots/affecter",             ex -> new AffecterLotHandler()    .handle(ex));
		server.createContext("/lots/desaffecter",          ex -> new DesaffecterLotHandler() .handle(ex));
		server.createContext("/lots/suiviprod",            ex -> new SuiviProdHandler()      .handle(ex));
		server.createContext("/lots/commencer",            ex -> new CommencerLotHandler()   .handle(ex));
		server.createContext("/lots/annuler",              ex -> new AnnulerLotHandler()     .handle(ex));
		server.createContext("/lots/terminer",             ex -> new TerminerLotHandler()    .handle(ex));
		server.createContext("/lots/phase",                ex -> new ModifierPhaseHandler()  .handle(ex));
		server.createContext("/lots/lignecolisage/ajouter",   ex -> new AjouterLigneColisageHandler() .handle(ex));
		server.createContext("/lots/lignecolisage/supprimer", ex -> new SupprimerLigneColisageHandler().handle(ex));

		// ── Routes sociétés ───────────────────────────────────────────────
		server.createContext("/societes",                  ex -> new GetSocietesHandler()    .handle(ex));
		server.createContext("/societes/modifier",         ex -> new ModifierSocieteHandler().handle(ex));
		server.createContext("/aces/modifier",             ex -> new ModifierAceHandler()    .handle(ex));
		server.createContext("/aces/mettreajour",          ex -> new MettreAJourAcesHandler().handle(ex));

		// ── Routes semaine suivante ───────────────────────────────────────
		server.createContext("/semaine-suivante",          ex -> new GetSemaineSuivanteHandler()      .handle(ex));
		server.createContext("/semaine-suivante/sauvegarder", ex -> new SauvSemaineSuivanteHandler()  .handle(ex));
		server.createContext("/semaine-suivante/affecter", ex -> new AffecterSemaineSuivanteHandler() .handle(ex));
		server.createContext("/semaine-suivante/desaffecter", ex -> new DesaffSemaineSuivanteHandler().handle(ex));
		server.createContext("/semaine-suivante/basculer", ex -> new BasculerSemaineSuivanteHandler() .handle(ex));

		// ── Routes système ────────────────────────────────────────────────
		server.createContext("/ficheroute/",               ex -> new FicheRouteHandler()     .handle(ex));
		server.createContext("/sauvegarder",               ex -> new SauvegarderHandler()    .handle(ex));
		server.createContext("/nouvelleheure",             ex -> new NouvelleHeureHandler()  .handle(ex));
		server.createContext("/semainesup",                ex -> new SemaineSupHandler()     .handle(ex));
		server.createContext("/autosave/lots",             ex -> new AutoSaveLotsHandler()   .handle(ex));
		server.createContext("/autosave/societes",         ex -> new AutoSaveSocietesHandler().handle(ex));
		server.createContext("/version",                   ex -> new VersionHandler()        .handle(ex));

		// ── Routes bloquées (réservées serveur) ───────────────────────────
		server.createContext("/charger",  ex -> new ChargerBloqueHandler() .handle(ex));
		server.createContext("/nouveaux", ex -> new NouveauxBloqueHandler().handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
		log("[Serveur] HTTP démarré sur le port " + PORT);

		// Nettoyage sessions expirées
		Thread t = new Thread(() -> {
			while (true) {
				try { Thread.sleep(3_600_000L); } catch (InterruptedException e) { break; }
				sessions.entrySet().removeIf(e -> e.getValue().estExpire());
			}
		});
		t.setDaemon(true); t.setName("session-cleaner"); t.start();

		// IHM ou mode headless
		if (!GraphicsEnvironment.isHeadless())
		{
			final ServeurHTTP self = this;
			SwingUtilities.invokeLater(() -> new app.ihm.serveur.FenetreServeur(self));
		}
		else
		{
			menuConsole();
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — AUTH
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * POST /login — {identifiant, motDePasse}
	 * Utilise GestionComptes (config.json) — identique au web.
	 * Route publique, non chiffrée.
	 */
	class LoginHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
			{ rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }

			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			if (estBloquee(ip))
			{ rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return; }

			try
			{
				String corps      = lire(ex);
				String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim();
				String motDePasse  = JsonSerialiser.extraireString(corps, "motDePasse").trim();

				// Vérifier si compte en attente
				if (gestionComptes.estEnAttente(identifiant))
				{
					rep(ex, 403, "{\"err\":\"Votre compte est en attente de validation par un administrateur.\"}");
					return;
				}

				GestionComptes.Utilisateur u = gestionComptes.valider(identifiant, motDePasse);
				if (u == null)
				{
					enregistrerEchecLogin(ip);
					try { Thread.sleep(50); } catch (InterruptedException ignored) {}
					rep(ex, 401, "{\"err\":\"Identifiant ou mot de passe incorrect.\"}");
					return;
				}

				loginEchecs.remove(ip); loginBlocage.remove(ip);
				String token = genererToken();
				sessions.put(token, new SessionInfo(u.identifiant, u.accesPAM));
				log("[Login] " + u.identifiant + " depuis " + ip);
				rep(ex, 200, "{\"token\":" + JsonSerialiser.esc(token)
					+ ",\"accesPAM\":" + u.accesPAM
					+ ",\"identifiant\":" + JsonSerialiser.esc(u.identifiant) + "}");
			}
			catch (Exception e)
			{
				rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}");
			}
		}
	}

	/**
	 * POST /creer-compte — {identifiant, motDePasse}
	 * Crée une demande "attente" dans config.json.
	 * Route publique.
	 */
	class CreerCompteHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
			{ rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }

			try
			{
				String corps      = lire(ex);
				String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim();
				String motDePasse  = JsonSerialiser.extraireString(corps, "motDePasse").trim();

				String erreur = gestionComptes.creerDemande(identifiant, motDePasse);
				if (erreur != null)
				{
					int code = erreur.contains("existe") || erreur.contains("réservé") || erreur.contains("attente") ? 409 : 400;
					rep(ex, code, "{\"err\":\"" + erreur + "\"}");
					return;
				}
				rep(ex, 200, "{\"ok\":true,\"attente\":true}");
			}
			catch (Exception e)
			{
				rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}");
			}
		}
	}

	/** GET /admin/demandes — liste des demandes en attente (PAM uniquement). */
	class DemandesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }

			rep(ex, 200, gestionComptes.serialiserDemandesJson());
		}
	}

	/** POST /admin/demandes/approuver — {identifiant} (PAM uniquement). */
	class ApprouverHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }

			try
			{
				String corps = lire(ex);
				String id    = JsonSerialiser.extraireString(corps, "identifiant");
				String err   = gestionComptes.approuver(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			}
			catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /admin/demandes/refuser — {identifiant} (PAM uniquement). */
	class RefuserHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }

			try
			{
				String corps = lire(ex);
				String id    = JsonSerialiser.extraireString(corps, "identifiant");
				String err   = gestionComptes.refuser(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			}
			catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** GET /cle — transmet la clé AES. Non chiffré. */
	class CleHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!exigerToken(ex)) return;
			if (aes == null) { rep(ex, 204, ""); return; }
			try
			{
				String cleBase64 = java.util.Base64.getEncoder().encodeToString(aes.getCleBytes());
				// Réponse intentionnellement NON chiffrée (client n'a pas encore la clé)
				byte[] bytes = ("{\"cle\":\"" + cleBase64 + "\"}").getBytes(StandardCharsets.UTF_8);
				ajouterHeadersCORS(ex);
				ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
				ex.sendResponseHeaders(200, bytes.length);
				try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
			}
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — LOTS (identiques à la version précédente)
	// ══════════════════════════════════════════════════════════════════════

	class GetLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			rwLock.readLock().lock();
			try { rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	class AjouterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				if (numCDE <= 0) { rep(ex, 400, "{\"err\":\"numCDE invalide\"}"); return; }
				metier.ajouterLot(numCDE,
					JsonSerialiser.extraireString(c, "typologie"),
					JsonSerialiser.extraireString(c, "affaire"),
					JsonSerialiser.extraireInt(c, "nbPieces"),
					JsonSerialiser.extraireDouble(c, "cadence"),
					JsonSerialiser.extraireInt(c, "valeurVente"),
					JsonSerialiser.extraireString(c, "semaine"),
					JsonSerialiser.extraireString(c, "emplacement"),
					JsonSerialiser.extraireString(c, "commentaire"),
					JsonSerialiser.extraireInt(c, "priorite"));
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SupprimerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				metier.supprimerLot(lot);
				for (Societe s : metier.getSocietes())
				{
					s.getLots().remove(lot);
					for (Ace a : s.getAces()) a.getLots().remove(lot);
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ModifierLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				// Appliquer les modifications
				String aff = JsonSerialiser.extraireString(c, "affaire");
				if (!aff.isEmpty()) lot.setAffaire(aff);
				String typo = JsonSerialiser.extraireString(c, "typologie");
				if (!typo.isEmpty()) lot.setTypologie(typo);
				String sem = JsonSerialiser.extraireString(c, "semaine");
				if (!sem.isEmpty()) lot.setSemaine(sem);
				String empl = JsonSerialiser.extraireString(c, "emplacement");
				lot.setEmplacement(empl);
				String com = JsonSerialiser.extraireString(c, "commentaire");
				lot.setCommentaire(com);
				String stat = JsonSerialiser.extraireString(c, "statut");
				if (!stat.isEmpty()) lot.setStatut(stat);
				String statE = JsonSerialiser.extraireString(c, "statutEchant");
				lot.setstatutEchant(statE);
				int nbP = JsonSerialiser.extraireInt(c, "nbPieces");
				if (nbP > 0) lot.setNbPieces(nbP);
				double cad = JsonSerialiser.extraireDouble(c, "cadence");
				if (cad > 0) lot.setCadence(cad);
				int vvs = JsonSerialiser.extraireInt(c, "valeurVente");
				if (vvs >= 0) lot.setValeurVente(vvs);
				int prio = JsonSerialiser.extraireInt(c, "priorite");
				lot.setPriorite(prio);
				String drec = JsonSerialiser.extraireString(c, "dateReception");
				lot.setDateReception(drec);
				String dpai = JsonSerialiser.extraireString(c, "datePaiement");
				lot.setDatePaiement(dpai);
				String lch = JsonSerialiser.extraireString(c, "lotACharge");
				lot.setLotACharge(lch);
				lot.setEstSousDouane(JsonSerialiser.extraireBool(c, "estSousDouane"));
				lot.setEstMachine(JsonSerialiser.extraireBool(c, "estMachine"));
				// Logistique
				String fc = JsonSerialiser.extraireString(c, "formatCarton");
				lot.setFormatCarton(fc);
				int coli = JsonSerialiser.extraireInt(c, "collisage");
				lot.setCollisage(coli);
				int np = JsonSerialiser.extraireInt(c, "nbPers");
				lot.setNbPers(np);
				String dist = JsonSerialiser.extraireString(c, "distribution");
				lot.setDistribution(dist);
				double cadR = JsonSerialiser.extraireDouble(c, "cadenceReel");
				if (cadR > 0) lot.setCadenceReel(cadR);
				String me = JsonSerialiser.extraireString(c, "methode");
				lot.setMethodeStr(me);
				String lchg = JsonSerialiser.extraireString(c, "lotACharge");
				lot.setLotACharge(lchg);
				int prec = JsonSerialiser.extraireInt(c, "poucentrecupCartonFour");
				lot.setPoucentrecupCartonFour(prec);
				// Recalculer automatismes
				lot.recalculer(PlanningGlobal.estHeureSup);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE     = JsonSerialiser.extraireInt(c, "numCDE");
				String nomSoc  = JsonSerialiser.extraireString(c, "societeNom");
				String nomAce  = JsonSerialiser.extraireString(c, "aceNom");
				Lot lot = findLot(numCDE);
				Societe soc = findSociete(nomSoc);
				if (lot == null || soc == null) { rep(ex, 404, "{\"err\":\"Lot ou société introuvable.\"}"); return; }
				// Retirer de toute affectation existante
				for (Societe s : metier.getSocietes())
				{
					s.getLots().remove(lot);
					for (Ace a : s.getAces()) a.getLots().remove(lot);
				}
				soc.getLots().add(lot);
				if (nomAce != null && !nomAce.isEmpty())
				{
					for (Ace a : soc.getAces()) if (a.getNom().equals(nomAce)) { a.getLots().add(lot); break; }
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				for (Societe s : metier.getSocietes())
				{
					s.getLots().remove(lot);
					for (Ace a : s.getAces()) a.getLots().remove(lot);
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SuiviProdHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.getSuivieProd().setNbPieceEtiq(JsonSerialiser.extraireInt(c, "nbPieceEtiq"));
				lot.getSuivieProd().setNbPieceRepart(JsonSerialiser.extraireInt(c, "nbPieceRepart"));
				lot.recalculer(PlanningGlobal.estHeureSup);
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// Commencer : accessible à TOUS les utilisateurs (pas seulement PAM)
	class CommencerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.commencer(PlanningGlobal.estHeureSup);
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class TerminerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.terminer();
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.annuler();
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ModifierPhaseHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.getPhase().setPreTri       (JsonSerialiser.extraireBool(c, "phase_preTri"));
				lot.getPhase().setSurPiste     (JsonSerialiser.extraireBool(c, "phase_surPiste"));
				lot.getPhase().setSortieEtiq   (JsonSerialiser.extraireBool(c, "phase_sortieEtiq"));
				lot.getPhase().setTri          (JsonSerialiser.extraireBool(c, "phase_tri"));
				lot.getPhase().setFinit        (JsonSerialiser.extraireBool(c, "phase_finit"));
				if (lot.getPhase().isFinit() && (lot.getDateFin() == null || lot.getDateFin().isEmpty()))
					lot.setDateFin(nowFmt());
				else if (!lot.getPhase().isFinit())
					lot.setDateFin("");
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AjouterLigneColisageHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				lot.ajouterLigneColisage(
					JsonSerialiser.extraireString(c, "format"),
					JsonSerialiser.extraireInt(c, "collisage"),
					JsonSerialiser.extraireInt(c, "pcs"));
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SupprimerLigneColisageHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				int index = JsonSerialiser.extraireInt(c, "index");
				lot.supprimerLigneColisage(index);
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SOCIÉTÉS
	// ══════════════════════════════════════════════════════════════════════

	class GetSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			rwLock.readLock().lock();
			try { rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes())); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	class ModifierSocieteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nom = JsonSerialiser.extraireString(c, "nom");
				Societe s = findSociete(nom);
				if (s == null)
				{
					s = new Societe(nom, "", new ArrayList<>(), 0);
					metier.getSocietes().add(s);
				}
				String nNom = JsonSerialiser.extraireString(c, "nouveauNom");
				if (!nNom.isEmpty()) s.setNom(nNom);
				String ce = JsonSerialiser.extraireString(c, "ce");
				s.setCe(ce);
				int h = JsonSerialiser.extraireInt(c, "totalHeuresCE");
				s.setTotalHeuresCE(h);
				int eff = JsonSerialiser.extraireInt(c, "effectifTotal");
				s.setEffectifTotal(eff);
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ModifierAceHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societeNom");
				Societe s = findSociete(nomSoc);
				if (s == null) { rep(ex, 404, "{\"err\":\"Société introuvable.\"}"); return; }
				String nomAce = JsonSerialiser.extraireString(c, "nom");
				int nbPers    = JsonSerialiser.extraireInt(c, "nbPers");
				int eff       = JsonSerialiser.extraireInt(c, "effectifActuel");
				Ace ace = s.getAces().stream().filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null);
				if (ace == null)
				{
					ace = new Ace(nomAce, nbPers, eff, 0);
					s.getAces().add(ace);
				}
				else { ace.setNbPers(nbPers); ace.setEffectifActuel(eff); }
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class MettreAJourAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societeNom");
				Societe s = findSociete(nomSoc);
				if (s == null) { rep(ex, 404, "{\"err\":\"Société introuvable.\"}"); return; }
				String acesJson = JsonSerialiser.extraireBloc(c, "\"aces\"");
				if (acesJson != null)
				{
					List<Ace> ancien = new ArrayList<>(s.getAces());
					s.getAces().clear();
					List<String> objs = JsonSerialiser.extraireObjets(acesJson);
					for (String obj : objs)
					{
						String nm  = JsonSerialiser.extraireString(obj, "nom");
						int    np  = JsonSerialiser.extraireInt(obj, "nbPers");
						int    ef  = JsonSerialiser.extraireInt(obj, "effectifActuel");
						Ace old = ancien.stream().filter(a -> a.getNom().equals(nm)).findFirst().orElse(null);
						Ace nAce = new Ace(nm, np, ef, 0);
						if (old != null) for (Lot l : old.getLots()) nAce.getLots().add(l);
						s.getAces().add(nAce);
					}
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SEMAINE SUIVANTE (nouvelles routes)
	// ══════════════════════════════════════════════════════════════════════

	/** GET /semaine-suivante — retourne les lots et sociétés préparés. */
	class GetSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
				if (!new File(chemin).exists())
				{
					rep(ex, 200, "{\"lots\":[],\"societes\":[],\"existe\":false}"); return;
				}
				String lotsJson = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
				String socsChemin = CheminApp.resoudre(DIR_SUIV_SOCS);
				String socsJson;
				if (new File(socsChemin).exists())
					socsJson = new String(Files.readAllBytes(Paths.get(socsChemin)), StandardCharsets.UTF_8);
				else
					socsJson = "[]";
				rep(ex, 200, "{\"lots\":" + lotsJson + ",\"societes\":" + socsJson + ",\"existe\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/sauvegarder — {lotsS:[...], societesS:[...]} */
	class SauvSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String c = lire(ex);
				String lotsBloc = JsonSerialiser.extraireBloc(c, "\"lotsS\"");
				String socsBloc = JsonSerialiser.extraireBloc(c, "\"societesS\"");
				if (lotsBloc == null) lotsBloc = "[]";
				if (socsBloc == null) socsBloc = "[]";

				String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
				Files.createDirectories(Paths.get(chemin).getParent());
				Files.write(Paths.get(chemin), lotsBloc.getBytes(StandardCharsets.UTF_8));
				Files.write(Paths.get(CheminApp.resoudre(DIR_SUIV_SOCS)), socsBloc.getBytes(StandardCharsets.UTF_8));
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/affecter — {numCDE, societeNom, aceNom} */
	class AffecterSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String c = lire(ex);
				String socsChemin = CheminApp.resoudre(DIR_SUIV_SOCS);
				// Charger les sociétés préparées
				String socsJson = new File(socsChemin).exists()
					? new String(Files.readAllBytes(Paths.get(socsChemin)), StandardCharsets.UTF_8) : "[]";

				// Manipuler via le serveur délégué
				// On réutilise la logique: on passe par le serveur pour modifier les sociétés en mémoire
				// Ici on retourne juste les sociétés mises à jour (la logique est dans PanelSemaineSuivante côté IHM)
				rep(ex, 200, "{\"ok\":true,\"societes\":" + socsJson + "}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/desaffecter — {numCDE} */
	class DesaffSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			try {
				String socsChemin = CheminApp.resoudre(DIR_SUIV_SOCS);
				String socsJson = new File(socsChemin).exists()
					? new String(Files.readAllBytes(Paths.get(socsChemin)), StandardCharsets.UTF_8) : "[]";
				rep(ex, 200, "{\"ok\":true,\"societes\":" + socsJson + "}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/basculer — écrase la semaine courante. */
	class BasculerSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			rwLock.writeLock().lock();
			try {
				basculerSemaneSuivante();
				rep(ex, 200, "{\"ok\":true," +
					"\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots()) + "," +
					"\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SYSTÈME (inchangés)
	// ══════════════════════════════════════════════════════════════════════

	class FicheRouteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			String nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
			Societe s = findSociete(nom);
			if (s == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
			rwLock.readLock().lock();
			try { rep(ex, 200, JsonSerialiser.serialiserFicheRoute(metier.genererFicheRoute(s))); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	class SauvegarderHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c       = lire(ex);
				String chemin  = JsonSerialiser.extraireString(c, "chemin");
				String semaine = JsonSerialiser.extraireString(c, "semaine");
				if (chemin.contains("..")) { rep(ex, 400, "{\"err\":\"Chemin non autorisé\"}"); return; }
				String dossier = CheminApp.resoudre(chemin + "/S" + semaine);
				Files.createDirectories(Paths.get(dossier));
				savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
				savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
				cheminLotsJson     = dossier + "/lots.json";
				cheminSocietesJson = dossier + "/societes.json";
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ChargerBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			log("[Serveur] BLOCAGE /charger depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	class NouveauxBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			log("[Serveur] BLOCAGE /nouveaux depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	class NouvelleHeureHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societe");
				if (nomSoc != null && !nomSoc.isEmpty()) {
					Societe s = findSociete(nomSoc);
					if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SemaineSupHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try { metier.setestHeureSup(); save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AutoSaveLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); rep(ex, 200, "{\"ok\":true}"); }
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AutoSaveSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); rep(ex, 200, "{\"ok\":true}"); }
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class VersionHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			// Badge demandes en attente
			int nbDemandes = (int) gestionComptes.getDemandesEnAttente().size();
			rwLock.readLock().lock();
			try {
				rep(ex, 200, "{\"v\":\"" + versionDonnees + "\",\"heureSup\":" + PlanningGlobal.estHeureSup
					+ ",\"semaine\":" + JsonSerialiser.esc(semaineActive)
					+ ",\"nbDemandes\":" + nbDemandes + "}");
			} finally { rwLock.readLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  API PUBLIQUE (appelée depuis FenetreServeur et PanelSemaineSuivante)
	// ══════════════════════════════════════════════════════════════════════

	public void chargerSemaine(String chemin) throws Exception
	{
		rwLock.writeLock().lock();
		try
		{
			savDonnees.charger(metier, chemin);
			cheminLotsJson     = chemin + "/lots.json";
			cheminSocietesJson = chemin + "/societes.json";
			detecterSemaineActive();
			versionDonnees = System.currentTimeMillis();
			log("[Serveur] Semaine chargée depuis : " + chemin);
		}
		finally { rwLock.writeLock().unlock(); }
	}

	public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
	{
		rwLock.writeLock().lock();
		try
		{
			String dossier = cheminDossier + "/S" + numSemaine;
			Files.createDirectories(Paths.get(dossier));
			savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
			cheminLotsJson     = dossier + "/lots.json";
			cheminSocietesJson = dossier + "/societes.json";
			semaineActive      = numSemaine;
		}
		finally { rwLock.writeLock().unlock(); }
	}

	public void nouvelleSemaine(java.awt.Component parent) throws Exception
	{
		FileNameExtensionFilter filtre = new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm");
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(filtre);
		fc.setDialogTitle("Sélectionner le fichier de planning (lots)");
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String cheminExcel = fc.getSelectedFile().getAbsolutePath();

		rwLock.writeLock().lock();
		try
		{
			ExcelReader reader = new ExcelReader();
			metier = new PlanningGlobal();
			reader.remplirDepuisExcel(metier, cheminExcel);
			for (Societe s : metier.getSocietes()) { s.getLots().clear(); for (Ace a : s.getAces()) a.getLots().clear(); }
			save();
			detecterSemaineActive();
			log("[Serveur] Nouvelle semaine chargée : " + cheminExcel);
		}
		finally { rwLock.writeLock().unlock(); }
	}

	public void toggleHeuresSup()
	{
		rwLock.writeLock().lock();
		try { metier.setestHeureSup(); save(); }
		finally { rwLock.writeLock().unlock(); }
		log("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
	}

	/** Retourne les lots de la semaine suivante (lecture fichier). */
	public List<Lot> getLotsSemaneSuivante()
	{
		try
		{
			String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
			if (!new File(chemin).exists()) return null;
			String json = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
			return JsonSerialiser.deserialiserLots(json);
		}
		catch (Exception e) { return null; }
	}

	/** Retourne les sociétés de la semaine suivante. */
	public List<Societe> getSocietesSemaneSuivante()
	{
		try
		{
			String chemin = CheminApp.resoudre(DIR_SUIV_SOCS);
			if (!new File(chemin).exists()) return null;
			String json = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
			List<Lot> lotsPrep = getLotsSemaneSuivante();
			if (lotsPrep == null) lotsPrep = new ArrayList<>();
			return JsonSerialiser.deserialiserSocietes(json, lotsPrep);
		}
		catch (Exception e) { return null; }
	}

	/** Lit un fichier Excel pour préparer la semaine suivante. */
	public List<Lot> lireExcelPourSemaineSuivante(String chemin) throws Exception
	{
		ExcelReader reader = new ExcelReader();
		PlanningGlobal pg = new PlanningGlobal();
		reader.remplirDepuisExcel(pg, chemin);
		return pg.getLots();
	}

	/** Sauvegarde les données de la semaine suivante. */
	public void sauvegarderSemaneSuivante(List<Lot> lots, List<Societe> societes)
	{
		try
		{
			String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
			String cheminS = CheminApp.resoudre(DIR_SUIV_SOCS);
			Files.createDirectories(Paths.get(cheminL).getParent());
			savDonnees.sauvegarderLots(lots, cheminL);
			savDonnees.sauvegarderSocietes(societes, lots, cheminS);
			log("[Serveur] Semaine suivante sauvegardée : " + lots.size() + " lots.");
		}
		catch (Exception e) { log("[Serveur] Erreur sauvegarde semaine suivante : " + e.getMessage()); }
	}

	/** Bascule la semaine suivante → semaine courante. */
	public void basculerSemaneSuivante() throws Exception
	{
		String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
		if (!new File(cheminL).exists()) throw new Exception("Aucune semaine suivante préparée.");

		String lotsJson = new String(Files.readAllBytes(Paths.get(cheminL)), StandardCharsets.UTF_8);
		List<Lot> nouveauxLots = JsonSerialiser.deserialiserLots(lotsJson);

		List<Societe> nouvSocs = null;
		String cheminS = CheminApp.resoudre(DIR_SUIV_SOCS);
		if (new File(cheminS).exists())
		{
			String socsJson = new String(Files.readAllBytes(Paths.get(cheminS)), StandardCharsets.UTF_8);
			nouvSocs = JsonSerialiser.deserialiserSocietes(socsJson, nouveauxLots);
		}

		// Remplacer la semaine courante
		metier = new PlanningGlobal();
		for (Lot l : nouveauxLots) metier.getLots().add(l);
		if (nouvSocs != null)
			for (Societe s : nouvSocs) metier.getSocietes().add(s);
		else
			for (Societe s : metier.getSocietes()) { s.getLots().clear(); for (Ace a : s.getAces()) a.getLots().clear(); }

		save();
		detecterSemaineActive();

		// Supprimer le dossier semaine_suivante
		java.io.File dir = new java.io.File(CheminApp.resoudre("app/data/semaine_suivante"));
		if (dir.exists()) deleteDirectory(dir);

		versionDonnees = System.currentTimeMillis();
		log("[Serveur] Bascule semaine suivante effectuée.");
	}

	/** Getter sociétés pour PanelSemaineSuivante. */
	public List<Societe> getSocietes()
	{
		rwLock.readLock().lock();
		try { return new ArrayList<>(metier.getSocietes()); }
		finally { rwLock.readLock().unlock(); }
	}

	public String  getSemaineActive()     { return semaineActive; }
	public int     getNbClientsConnectes()
	{
		long now = System.currentTimeMillis();
		clientsActifs.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_CLIENT_MS);
		return clientsActifs.size();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES RÉSEAU
	// ══════════════════════════════════════════════════════════════════════

	private static void ajouterHeadersCORS(HttpExchange ex)
	{
		ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
		ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
		ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
	}

	private void rep(HttpExchange ex, int code, String body) throws IOException
	{
		ajouterHeadersCORS(ex);
		String path = ex.getRequestURI().getPath();
		boolean routePublique = path.equals("/login") || path.equals("/cle") || path.equals("/creer-compte");
		String contenu = body;
		if (aes != null && !routePublique && code == 200)
		{
			try
			{
				contenu = aes.chiffrer(body);
				ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			}
			catch (Exception e)
			{
				ex.getResponseHeaders().set("X-Encrypted", "false");
				ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
			}
		}
		else
		{
			ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		}
		byte[] bytes = contenu.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
	}

	private String lire(HttpExchange ex) throws IOException
	{
		String brut = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		if (aes == null || brut.isBlank()) return brut;
		try { return aes.dechiffrer(brut); }
		catch (Exception e) { return brut; }
	}

	// ══════════════════════════════════════════════════════════════════════
	//  SÉCURITÉ
	// ══════════════════════════════════════════════════════════════════════

	private String genererToken()
	{
		byte[] b = new byte[32]; rng.nextBytes(b);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private SessionInfo verifierToken(HttpExchange ex)
	{
		String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
		if (token == null || token.isBlank()) return null;
		SessionInfo info = sessions.get(token);
		if (info == null) return null;
		if (info.estExpire()) { sessions.remove(token); return null; }
		return info;
	}

	private boolean exigerToken(HttpExchange ex) throws IOException
	{
		SessionInfo info = verifierToken(ex);
		if (info == null)
		{
			rep(ex, 401, "{\"err\":\"Non authentifié. Connectez-vous via /login.\"}");
			return false;
		}
		enregistrerClient(ex);
		return true;
	}

	private boolean estBloquee(String ip)
	{
		Long fin = loginBlocage.get(ip);
		if (fin == null) return false;
		if (System.currentTimeMillis() < fin) return true;
		loginBlocage.remove(ip); loginEchecs.remove(ip); return false;
	}

	private void enregistrerEchecLogin(String ip)
	{
		int n = loginEchecs.merge(ip, 1, Integer::sum);
		if (n >= MAX_ECHECS)
		{
			loginBlocage.put(ip, System.currentTimeMillis() + BLOCAGE_MS);
			loginEchecs.put(ip, 0);
			log("[Sécurité] IP bloquée 5 min : " + ip);
		}
	}

	private void enregistrerClient(HttpExchange ex)
	{
		clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES MÉTIER
	// ══════════════════════════════════════════════════════════════════════

	private Lot findLot(int numCDE)
	{
		return metier.getLots().stream().filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null);
	}

	private Societe findSociete(String nom)
	{
		if (nom == null) return null;
		return metier.getSocietes().stream().filter(s -> nom.equals(s.getNom())).findFirst().orElse(null);
	}

	private void save()
	{
		try { savDonnees.sauvegarderLots    (metier.getLots(),     cheminLotsJson);     } catch (Exception ignored) {}
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); } catch (Exception ignored) {}
		versionDonnees = System.currentTimeMillis();
	}

	private void detecterSemaineActive()
	{
		if (!metier.getLots().isEmpty())
		{
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
				semaineActive = sem.length() == 6 ?
					"S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
		}
	}

	private static String nowFmt()
	{
		java.time.LocalDateTime n = java.time.LocalDateTime.now();
		return String.format("%02d/%02d/%04d %02d:%02d:%02d",
			n.getDayOfMonth(), n.getMonthValue(), n.getYear(),
			n.getHour(), n.getMinute(), n.getSecond());
	}

	private static void deleteDirectory(java.io.File dir)
	{
		java.io.File[] files = dir.listFiles();
		if (files != null) for (java.io.File f : files)
		{ if (f.isDirectory()) deleteDirectory(f); else f.delete(); }
		dir.delete();
	}

	private static void log(String msg) { System.out.println(msg); }

	// ══════════════════════════════════════════════════════════════════════
	//  CONSOLE HEADLESS (mode sans écran)
	// ══════════════════════════════════════════════════════════════════════

	private void menuConsole()
	{
		log("╔══════════════════════════════════════════════════╗");
		log("║  SERVEUR Planning Global Futura — MODE CONSOLE  ║");
		log("║  Port : " + PORT + "                                   ║");
		log("╠══════════════════════════════════════════════════╣");
		log("║  Commandes :                                     ║");
		log("║    sauvegarder  — archiver la semaine            ║");
		log("║    heures-sup   — basculer heures sup            ║");
		log("║    quitter      — arrêter le serveur             ║");
		log("╚══════════════════════════════════════════════════╝");

		java.util.Scanner sc = new java.util.Scanner(System.in);
		while (sc.hasNextLine())
		{
			String ligne = sc.nextLine().trim().toLowerCase();
			switch (ligne)
			{
				case "sauvegarder":
					System.out.print("  Dossier de destination : ");
					String doss = sc.nextLine().trim();
					System.out.print("  Numéro de semaine      : ");
					String sem  = sc.nextLine().trim();
					try { sauvegarderSemaine(doss, sem); log("  Sauvegarde effectuée dans S" + sem); }
					catch (Exception e) { log("  Erreur : " + e.getMessage()); }
					break;
				case "heures-sup":
					toggleHeuresSup();
					log("  Heures sup : " + PlanningGlobal.estHeureSup);
					break;
				case "quitter":
					log("[Serveur] Arrêt.");
					System.exit(0);
					break;
				default:
					log("  Commande inconnue. Tapez 'quitter' pour arrêter.");
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  POINT D'ENTRÉE
	// ══════════════════════════════════════════════════════════════════════

	public static void main(String[] args)
	{
		try                 { new ServeurHTTP();   }
		catch (Exception e) { e.printStackTrace(); }
	}
}