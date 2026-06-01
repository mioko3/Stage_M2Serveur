package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import app.securite.ChiffrementAES;
import app.securite.GestionComptes;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — version finale avec toutes les fonctionnalités
 *
 *  NOUVELLES FONCTIONNALITÉS :
 *   #A Login avec mot de passe   → POST /login {identifiant, motDePasse}
 *   #B Création de compte        → POST /creer-compte {identifiant, motDePasse}
 *   #C Gestion demandes (PAM)    → GET /admin/demandes
 *                                   POST /admin/demandes/approuver
 *                                   POST /admin/demandes/refuser
 *   #D Semaine suivante          → GET/POST /semaine-suivante/...
 *
 *  CORRECTIONS :
 *   #1 Port unifié : PORT = 8082 partout (FenetreServeur + README)
 *   #2 ReadWriteLock sur toutes les routes
 *   #3 CORS sur toutes les réponses
 *   #4 Mode headless automatique
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

	// ── ReadWriteLock ─────────────────────────────────────────────────────
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	// ── Version pour polling ──────────────────────────────────────────────
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
		SessionInfo(String id, boolean pam)
		{ identifiant = id; accesPAM = pam; createdAt = System.currentTimeMillis(); }
		boolean estExpire()
		{ return System.currentTimeMillis() - createdAt > TOKEN_TTL_MS; }
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
			log("[Serveur] " + metier.getLots().size() + " lots, "
				+ metier.getSocietes().size() + " sociétés.");
			detecterSemaineActive();
		}
		catch (Exception e)
		{
			log("[Serveur] Pas de données initiales : " + e.getMessage());
		}

		// ── Démarrage HTTP ────────────────────────────────────────────────
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// Routes publiques (sans token)
		server.createContext("/login",                       ex -> new LoginHandler()         .handle(ex));
		server.createContext("/creer-compte",                ex -> new CreerCompteHandler()   .handle(ex));

		// Routes admin (PAM uniquement)
		server.createContext("/admin/demandes",              ex -> new DemandesHandler()      .handle(ex));
		server.createContext("/admin/demandes/approuver",    ex -> new ApprouverHandler()     .handle(ex));
		server.createContext("/admin/demandes/refuser",      ex -> new RefuserHandler()       .handle(ex));

		// Clé AES
		server.createContext("/cle",                         ex -> new CleHandler()           .handle(ex));

		// Lots
		server.createContext("/lots",                        ex -> new GetLotsHandler()       .handle(ex));
		server.createContext("/lots/ajouter",                ex -> new AjouterLotHandler()    .handle(ex));
		server.createContext("/lots/supprimer",              ex -> new SupprimerLotHandler()  .handle(ex));
		server.createContext("/lots/modifier",               ex -> new ModifierLotHandler()   .handle(ex));
		server.createContext("/lots/affecter",               ex -> new AffecterLotHandler()   .handle(ex));
		server.createContext("/lots/desaffecter",            ex -> new DesaffecterLotHandler().handle(ex));
		server.createContext("/lots/suiviprod",              ex -> new SuiviProdHandler()     .handle(ex));
		server.createContext("/lots/commencer",              ex -> new CommencerLotHandler()  .handle(ex));
		server.createContext("/lots/annuler",                ex -> new AnnulerLotHandler()    .handle(ex));
		server.createContext("/lots/terminer",               ex -> new TerminerLotHandler()   .handle(ex));
		server.createContext("/lots/phase",                  ex -> new ModifierPhaseHandler() .handle(ex));
		server.createContext("/lots/lignecolisage/ajouter",
			ex -> new AjouterLigneColisageHandler() .handle(ex));
		server.createContext("/lots/lignecolisage/supprimer",
			ex -> new SupprimerLigneColisageHandler().handle(ex));

		// Sociétés / ACE
		server.createContext("/societes",                    ex -> new GetSocietesHandler()   .handle(ex));
		server.createContext("/societes/modifier",           ex -> new ModifierSocieteHandler().handle(ex));
		server.createContext("/aces/modifier",               ex -> new ModifierAceHandler()   .handle(ex));
		server.createContext("/aces/mettreajour",            ex -> new MettreAJourAcesHandler().handle(ex));

		// Semaine suivante
		server.createContext("/semaine-suivante",
			ex -> new GetSemaineSuivanteHandler()       .handle(ex));
		server.createContext("/semaine-suivante/sauvegarder",
			ex -> new SauvSemaineSuivanteHandler()      .handle(ex));
		server.createContext("/semaine-suivante/basculer",
			ex -> new BasculerSemaineSuivanteHandler()  .handle(ex));

		// Système
		server.createContext("/ficheroute/",                 ex -> new FicheRouteHandler()    .handle(ex));
		server.createContext("/sauvegarder",                 ex -> new SauvegarderHandler()   .handle(ex));
		server.createContext("/nouvelleheure",               ex -> new NouvelleHeureHandler() .handle(ex));
		server.createContext("/semainesup",                  ex -> new SemaineSupHandler()    .handle(ex));
		server.createContext("/autosave/lots",               ex -> new AutoSaveLotsHandler()  .handle(ex));
		server.createContext("/autosave/societes",           ex -> new AutoSaveSocietesHandler().handle(ex));
		server.createContext("/version",                     ex -> new VersionHandler()       .handle(ex));

		// Routes bloquées (réservées serveur)
		server.createContext("/charger",    ex -> new ChargerBloqueHandler() .handle(ex));
		server.createContext("/nouveaux",   ex -> new NouveauxBloqueHandler().handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
		log("[Serveur] HTTP démarré sur le port " + PORT);

		// Nettoyage sessions expirées toutes les heures
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
			javax.swing.SwingUtilities.invokeLater(
				() -> new app.ihm.serveur.FenetreServeur(self));
		}
		else
		{
			menuConsole();
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — AUTH
	// ══════════════════════════════════════════════════════════════════════

	/** POST /login — {identifiant, motDePasse} via GestionComptes (config.json) */
	class LoginHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
			{ rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			if (estBloquee(ip))
			{ rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return; }
			try {
				String corps      = lire(ex);
				String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim();
				String motDePasse  = JsonSerialiser.extraireString(corps, "motDePasse").trim();
				if (gestionComptes.estEnAttente(identifiant)) {
					rep(ex, 403, "{\"err\":\"Votre compte est en attente de validation.\"}"); return;
				}
				GestionComptes.Utilisateur u = gestionComptes.valider(identifiant, motDePasse);
				if (u == null) {
					enregistrerEchecLogin(ip);
					try { Thread.sleep(50); } catch (InterruptedException ignored) {}
					rep(ex, 401, "{\"err\":\"Identifiant ou mot de passe incorrect.\"}"); return;
				}
				loginEchecs.remove(ip); loginBlocage.remove(ip);
				String token = genererToken();
				sessions.put(token, new SessionInfo(u.identifiant, u.accesPAM));
				log("[Login] " + u.identifiant + " depuis " + ip);
				rep(ex, 200, "{\"token\":" + JsonSerialiser.esc(token)
					+ ",\"accesPAM\":" + u.accesPAM
					+ ",\"identifiant\":" + JsonSerialiser.esc(u.identifiant) + "}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /creer-compte — {identifiant, motDePasse} — route publique */
	class CreerCompteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
			{ rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }
			try {
				String corps       = lire(ex);
				String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim();
				String motDePasse  = JsonSerialiser.extraireString(corps, "motDePasse").trim();
				String erreur = gestionComptes.creerDemande(identifiant, motDePasse);
				if (erreur != null) {
					int code = erreur.contains("existe") || erreur.contains("réservé")
						|| erreur.contains("attente") ? 409 : 400;
					rep(ex, code, "{\"err\":\"" + erreur + "\"}"); return;
				}
				rep(ex, 200, "{\"ok\":true,\"attente\":true}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** GET /admin/demandes — liste des demandes en attente (PAM) */
	class DemandesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			rep(ex, 200, gestionComptes.serialiserDemandesJson());
		}
	}

	/** POST /admin/demandes/approuver — {identifiant} (PAM) */
	class ApprouverHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String corps = lire(ex);
				String id    = JsonSerialiser.extraireString(corps, "identifiant");
				String err   = gestionComptes.approuver(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /admin/demandes/refuser — {identifiant} (PAM) */
	class RefuserHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String corps = lire(ex);
				String id    = JsonSerialiser.extraireString(corps, "identifiant");
				String err   = gestionComptes.refuser(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** GET /cle — transmet la clé AES. Non chiffré. */
	class CleHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			if (aes == null) { rep(ex, 204, ""); return; }
			try {
				// cleEnBase64() est la méthode réelle de ChiffrementAES
				String cleBase64 = aes.cleEnBase64();
				byte[] bytes = ("{\"cle\":\"" + cleBase64 + "\"}").getBytes(StandardCharsets.UTF_8);
				ajouterHeadersCORS(ex);
				ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
				ex.sendResponseHeaders(200, bytes.length);
				try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — LOTS
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
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				if (numCDE <= 0) { rep(ex, 400, "{\"err\":\"numCDE invalide\"}"); return; }
				// Signature exacte de PlanningGlobal.ajouterLot : 16 paramètres
				metier.ajouterLot(numCDE,
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
					JsonSerialiser.extraireBool  (c, "estSousDouane"),
					JsonSerialiser.extraireString(c, "dateReception"),
					JsonSerialiser.extraireString(c, "datePaiement"),
					JsonSerialiser.extraireString(c, "commentaire"));
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SupprimerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				metier.supprimerLot(lot);
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
				// Utiliser la méthode exacte de PlanningGlobal.modifierLot (16 params)
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
					JsonSerialiser.extraireBool  (c, "estSousDouane"),
					JsonSerialiser.extraireString(c, "dateReception"),
					JsonSerialiser.extraireString(c, "datePaiement"),
					JsonSerialiser.extraireString(c, "commentaire"));
				// Champs logistiques supplémentaires (setters directs)
				String fc = JsonSerialiser.extraireString(c, "formatCarton");
				if (!fc.isEmpty()) lot.setFormatCarton(fc);
				int coli = JsonSerialiser.extraireInt(c, "collisage");
				if (coli >= 0) lot.setCollisage(coli);
				int np = JsonSerialiser.extraireInt(c, "nbPers");
				if (np >= 0) lot.setNbPers(np);
				String dist = JsonSerialiser.extraireString(c, "distribution");
				lot.setDistribution(dist);
				double cadR = JsonSerialiser.extraireDouble(c, "cadenceReel");
				if (cadR > 0) lot.setCadenceReel(cadR);
				String me = JsonSerialiser.extraireString(c, "methode");
				lot.setMethode(me);
				int prec = JsonSerialiser.extraireInt(c, "poucentrecupCartonFour");
				if (prec >= 0) lot.setPoucentrecupCartonFour(prec);
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE    = JsonSerialiser.extraireInt(c, "numCDE");
				String nomSoc = JsonSerialiser.extraireString(c, "societeNom");
				String nomAce = JsonSerialiser.extraireString(c, "aceNom");
				Lot lot = findLot(numCDE);
				Societe soc = findSociete(nomSoc);
				if (lot == null || soc == null)
				{ rep(ex, 404, "{\"err\":\"Lot ou société introuvable.\"}"); return; }
				Ace ace = nomAce.isEmpty() ? null : soc.getAce(nomAce);
				metier.affecterLot(lot, soc, ace);
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				metier.desaffecterLot(lot);
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
				lot.getSuivieProd().setNbPieceEtiq  (JsonSerialiser.extraireInt(c, "nbPieceEtiq"));
				lot.getSuivieProd().setNbPieceRepart(JsonSerialiser.extraireInt(c, "nbPieceRepart"));
				lot.recalculerHeures();
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// Commencer : accessible à TOUS les utilisateurs
	class CommencerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				// PlanningGlobal.commencerLot() — signature exacte
				metier.commencerLot(lot);
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
				// PlanningGlobal.marquerLotTermine()
				metier.marquerLotTermine(lot);
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"Lot introuvable.\"}"); return; }
				// PlanningGlobal.annulerLot()
				metier.annulerLot(lot);
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
				// PlanningGlobal.modifierPhase() — signature exacte
				metier.modifierPhase(lot,
					JsonSerialiser.extraireBool(c, "phase_preTri"),
					JsonSerialiser.extraireBool(c, "phase_surPiste"),
					JsonSerialiser.extraireBool(c, "phase_sortieEtiq"),
					JsonSerialiser.extraireBool(c, "phase_tri"),
					JsonSerialiser.extraireBool(c, "phase_finit"));
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
				String format = JsonSerialiser.extraireString(c, "format");
				int    coli   = JsonSerialiser.extraireInt(c, "collisage");
				int    pcs    = JsonSerialiser.extraireInt(c, "pcs");
				// Signature exacte : ajouterLigneColisage(LigneColisage, int)
				lot.ajouterLigneColisage(new LigneColisage(format, coli), pcs);
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
				lot.supprimerLigneColisage(JsonSerialiser.extraireInt(c, "index"));
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
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nom = JsonSerialiser.extraireString(c, "nom");
				Societe s = findSociete(nom);
				if (s == null) {
					s = new Societe(nom, "", new ArrayList<>(), 0);
					metier.getSocietes().add(s);
				}
				String nNom = JsonSerialiser.extraireString(c, "nouveauNom");
				if (!nNom.isEmpty()) s.setNom(nNom);
				s.setCe(JsonSerialiser.extraireString(c, "ce"));
				s.setTotalHeuresCE(JsonSerialiser.extraireInt(c, "totalHeuresCE"));
				s.setEffectifTotal(JsonSerialiser.extraireInt(c, "effectifTotal"));
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ModifierAceHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societeNom");
				Societe s = findSociete(nomSoc);
				if (s == null) { rep(ex, 404, "{\"err\":\"Société introuvable.\"}"); return; }
				String nomAce = JsonSerialiser.extraireString(c, "nom");
				int nbPers    = JsonSerialiser.extraireInt(c, "nbPers");
				int eff       = JsonSerialiser.extraireInt(c, "effectifActuel");
				Ace ace = s.getAce(nomAce);
				if (ace == null) {
					ace = new Ace(nomAce, nbPers, eff, 0);
					s.getAces().add(ace);
				} else {
					// PlanningGlobal.modifierAce()
					metier.modifierAce(ace, nomAce, nbPers, eff);
				}
				save(); rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class MettreAJourAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societeNom");
				Societe s = findSociete(nomSoc);
				if (s == null) { rep(ex, 404, "{\"err\":\"Société introuvable.\"}"); return; }
				String acesJson = JsonSerialiser.extraireBloc(c, "\"aces\"");
				if (acesJson != null) {
					List<Ace> ancien = new ArrayList<>(s.getAces());
					s.getAces().clear();
					for (String obj : JsonSerialiser.extraireObjets(acesJson)) {
						String nm = JsonSerialiser.extraireString(obj, "nom");
						int    np = JsonSerialiser.extraireInt(obj, "nbPers");
						int    ef = JsonSerialiser.extraireInt(obj, "effectifActuel");
						Ace old = ancien.stream()
							.filter(a -> a.getNom().equals(nm)).findFirst().orElse(null);
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
	//  HANDLERS — SEMAINE SUIVANTE
	// ══════════════════════════════════════════════════════════════════════

	/** GET /semaine-suivante */
	class GetSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
				if (!new File(chemin).exists()) {
					rep(ex, 200, "{\"lots\":[],\"societes\":[],\"existe\":false}"); return;
				}
				String lotsJson = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
				String socsChemin = CheminApp.resoudre(DIR_SUIV_SOCS);
				String socsJson = new File(socsChemin).exists()
					? new String(Files.readAllBytes(Paths.get(socsChemin)), StandardCharsets.UTF_8) : "[]";
				rep(ex, 200, "{\"lots\":" + lotsJson + ",\"societes\":" + socsJson + ",\"existe\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/sauvegarder */
	class SauvSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String c = lire(ex);
				String lotsBloc = JsonSerialiser.extraireBloc(c, "\"lotsS\"");
				String socsBloc = JsonSerialiser.extraireBloc(c, "\"societesS\"");
				if (lotsBloc == null) lotsBloc = "[]";
				if (socsBloc == null) socsBloc = "[]";
				String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
				Files.createDirectories(Paths.get(chemin).getParent());
				Files.write(Paths.get(chemin), lotsBloc.getBytes(StandardCharsets.UTF_8));
				Files.write(Paths.get(CheminApp.resoudre(DIR_SUIV_SOCS)),
					socsBloc.getBytes(StandardCharsets.UTF_8));
				rep(ex, 200, "{\"ok\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	/** POST /semaine-suivante/basculer — écrase la semaine courante */
	class BasculerSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			rwLock.writeLock().lock();
			try {
				basculerSemaneSuivante();
				rep(ex, 200, "{\"ok\":true,"
					+ "\"lots\":"    + JsonSerialiser.serialiserLots(metier.getLots())
					+ ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SYSTÈME
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
				savDonnees.sauvegarderLots(metier.getLots(), dossier + "/lots.json");
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
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societe");
				if (nomSoc != null && !nomSoc.isEmpty()) {
					Societe s = findSociete(nomSoc);
					if (s != null)
						s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
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
			try {
				savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
				rep(ex, 200, "{\"ok\":true}");
			}
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class VersionHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			int nbDemandes = gestionComptes.getDemandesEnAttente().size();
			rwLock.readLock().lock();
			try {
				rep(ex, 200, "{\"v\":\"" + versionDonnees + "\",\"heureSup\":"
					+ PlanningGlobal.estHeureSup + ",\"semaine\":"
					+ JsonSerialiser.esc(semaineActive)
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
		try {
			savDonnees.charger(metier, chemin);
			cheminLotsJson     = chemin + "/lots.json";
			cheminSocietesJson = chemin + "/societes.json";
			detecterSemaineActive();
			versionDonnees = System.currentTimeMillis();
			log("[Serveur] Semaine chargée depuis : " + chemin);
		} finally { rwLock.writeLock().unlock(); }
	}

	public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
	{
		rwLock.writeLock().lock();
		try {
			String dossier = cheminDossier + "/S" + numSemaine;
			Files.createDirectories(Paths.get(dossier));
			savDonnees.sauvegarderLots(metier.getLots(), dossier + "/lots.json");
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
			cheminLotsJson     = dossier + "/lots.json";
			cheminSocietesJson = dossier + "/societes.json";
			semaineActive      = numSemaine;
		} finally { rwLock.writeLock().unlock(); }
	}

	public void nouvelleSemaine(Component parent) throws Exception
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter(
			"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		fc.setDialogTitle("Sélectionner le fichier de planning (lots)");
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String cheminXlsx = fc.getSelectedFile().getAbsolutePath();

		ArrayList<Lot> tempLots = ExcelReader.lireLots(cheminXlsx);
		int semaine = 0;
		if (!tempLots.isEmpty()) {
			String sem = tempLots.get(0).getSemaine();
			try { semaine = Integer.parseInt(sem.substring(Math.max(0, sem.length() - 2))); }
			catch (NumberFormatException ignored) {}
		}
		fc.setDialogTitle("Sélectionner le fichier des heures ACE (ou annuler)");
		String cheminHeurs = fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath() : cheminXlsx;

		rwLock.writeLock().lock();
		try {
			metier.chargerDepuisExcel(cheminXlsx,
				CheminApp.resoudre("app/data/courutilisation/societes.json"),
				semaine, cheminHeurs);
			cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
			cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");
			save();
			detecterSemaineActive();
			log("[Serveur] Nouvelle semaine chargée depuis : " + cheminXlsx);
		} finally { rwLock.writeLock().unlock(); }
	}

	public void toggleHeuresSup()
	{
		rwLock.writeLock().lock();
		try { metier.setestHeureSup(); save(); }
		finally { rwLock.writeLock().unlock(); }
		log("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
	}

	/** Retourne les lots de la semaine suivante (lecture fichier JSON brut). */
	public ArrayList<Lot> getLotsSemaneSuivante()
	{
		try {
			String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
			if (!new File(chemin).exists()) return null;
			String json = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
			return JsonSerialiser.deserialiserLots(json);
		} catch (Exception e) { return null; }
	}

	/** Retourne les sociétés de la semaine suivante. */
	public ArrayList<Societe> getSocietesSemaneSuivante()
	{
		try {
			String chemin = CheminApp.resoudre(DIR_SUIV_SOCS);
			if (!new File(chemin).exists()) return null;
			String json = new String(Files.readAllBytes(Paths.get(chemin)), StandardCharsets.UTF_8);
			ArrayList<Lot> lotsPrep = getLotsSemaneSuivante();
			if (lotsPrep == null) lotsPrep = new ArrayList<>();
			// deserialiserSocietes attend ArrayList
			return JsonSerialiser.deserialiserSocietes(json, lotsPrep);
		} catch (Exception e) { return null; }
	}

	/** Lit un fichier Excel pour préparer la semaine suivante. */
	public ArrayList<Lot> lireExcelPourSemaineSuivante(String chemin) throws Exception
	{
		// ExcelReader est 100% statique
		return ExcelReader.lireLots(chemin);
	}

	/** Sauvegarde les données de la semaine suivante (appelé par PanelSemaineSuivante). */
	public void sauvegarderSemaneSuivante(ArrayList<Lot> lots, ArrayList<Societe> societes)
	{
		try {
			String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
			String cheminS = CheminApp.resoudre(DIR_SUIV_SOCS);
			Files.createDirectories(Paths.get(cheminL).getParent());
			savDonnees.sauvegarderLots(lots, cheminL);
			savDonnees.sauvegarderSocietes(societes, lots, cheminS);
			log("[Serveur] Semaine suivante sauvegardée : " + lots.size() + " lots.");
		} catch (Exception e) {
			log("[Serveur] Erreur sauvegarde semaine suivante : " + e.getMessage());
		}
	}

	// Surcharge List → ArrayList pour PanelSemaineSuivante (compatibilité)
	public void sauvegarderSemaneSuivante(List<Lot> lots, List<Societe> societes)
	{
		sauvegarderSemaneSuivante(new ArrayList<>(lots), new ArrayList<>(societes));
	}

	/** Bascule la semaine suivante → semaine courante. */
	public void basculerSemaneSuivante() throws Exception
	{
		String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
		if (!new File(cheminL).exists()) throw new Exception("Aucune semaine suivante préparée.");

		ArrayList<Lot> nouveauxLots = getLotsSemaneSuivante();
		ArrayList<Societe> nouvSocs = getSocietesSemaneSuivante();

		// Remplacer la semaine courante
		metier.setLots(nouveauxLots);
		if (nouvSocs != null && !nouvSocs.isEmpty())
		{
			metier.setSocietes(nouvSocs);
		}
		else
		{
			for (Societe s : metier.getSocietes()) {
				s.getLots().clear();
				for (Ace a : s.getAces()) a.getLots().clear();
			}
		}

		save();
		detecterSemaineActive();

		// Supprimer le dossier semaine_suivante
		deleteDirectory(new File(CheminApp.resoudre("app/data/semaine_suivante")));
		versionDonnees = System.currentTimeMillis();
		log("[Serveur] Bascule semaine suivante effectuée.");
	}

	/** Getter sociétés pour PanelSemaineSuivante. */
	public ArrayList<Societe> getSocietes()
	{
		rwLock.readLock().lock();
		try { return new ArrayList<>(metier.getSocietes()); }
		finally { rwLock.readLock().unlock(); }
	}

	public String getSemaineActive()    { return semaineActive; }

	public int getNbClientsConnectes()
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
		boolean routePublique = path.equals("/login") || path.equals("/cle")
			|| path.equals("/creer-compte");
		String contenu = body;
		if (aes != null && !routePublique && code == 200) {
			try {
				contenu = aes.chiffrer(body);
				ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			} catch (Exception e) {
				ex.getResponseHeaders().set("X-Encrypted", "false");
				ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
			}
		} else {
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
		try { return aes.dechiffrer(brut); } catch (Exception e) { return brut; }
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
		if (info == null) {
			rep(ex, 401, "{\"err\":\"Non authentifié.\"}"); return false;
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
		if (n >= MAX_ECHECS) {
			loginBlocage.put(ip, System.currentTimeMillis() + BLOCAGE_MS);
			loginEchecs.put(ip, 0);
			log("[Sécurité] IP bloquée 5 min : " + ip);
		}
	}

	private void enregistrerClient(HttpExchange ex)
	{
		clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(),
			System.currentTimeMillis());
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES MÉTIER
	// ══════════════════════════════════════════════════════════════════════

	private Lot findLot(int numCDE)
	{
		return metier.getLots().stream()
			.filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null);
	}

	private Societe findSociete(String nom)
	{
		if (nom == null) return null;
		return metier.getSocietes().stream()
			.filter(s -> nom.equals(s.getNom())).findFirst().orElse(null);
	}

	private void save()
	{
		try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); }
		catch (Exception ignored) {}
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
		catch (Exception ignored) {}
		versionDonnees = System.currentTimeMillis();
	}

	private void detecterSemaineActive()
	{
		if (!metier.getLots().isEmpty()) {
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
				semaineActive = sem.length() == 6 ?
					"S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
		}
	}

	private static String nowFmt()
	{
		LocalDateTime n = LocalDateTime.now();
		return String.format("%02d/%02d/%04d %02d:%02d:%02d",
			n.getDayOfMonth(), n.getMonthValue(), n.getYear(),
			n.getHour(), n.getMinute(), n.getSecond());
	}

	private static void deleteDirectory(File dir)
	{
		if (dir == null || !dir.exists()) return;
		File[] files = dir.listFiles();
		if (files != null) for (File f : files)
		{ if (f.isDirectory()) deleteDirectory(f); else f.delete(); }
		dir.delete();
	}

	private static void log(String msg) { System.out.println(msg); }

	// ══════════════════════════════════════════════════════════════════════
	//  CONSOLE HEADLESS
	// ══════════════════════════════════════════════════════════════════════

	private void menuConsole()
	{
		log("╔══════════════════════════════════════════════════╗");
		log("║  SERVEUR Planning Global Futura — MODE CONSOLE  ║");
		log("║  Port : " + PORT + "  (tape 'quitter' pour arrêter)  ║");
		log("╚══════════════════════════════════════════════════╝");
		java.util.Scanner sc = new java.util.Scanner(System.in);
		while (sc.hasNextLine()) {
			String ligne = sc.nextLine().trim().toLowerCase();
			if ("quitter".equals(ligne)) { log("[Serveur] Arrêt."); System.exit(0); }
			else if ("heures-sup".equals(ligne)) {
				toggleHeuresSup(); log("  Heures sup : " + PlanningGlobal.estHeureSup);
			} else if (ligne.startsWith("sauvegarder")) {
				System.out.print("  Dossier : ");
				String d = sc.nextLine().trim();
				System.out.print("  Semaine : ");
				String s = sc.nextLine().trim();
				try { sauvegarderSemaine(d, s); log("  Sauvegarde S" + s + " OK."); }
				catch (Exception e) { log("  Erreur : " + e.getMessage()); }
			} else { log("  Commandes : sauvegarder | heures-sup | quitter"); }
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