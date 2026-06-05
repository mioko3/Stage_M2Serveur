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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — Planning Global Futura
 *
 *  CORRECTIF :
 *  • ModifierLotHandler : estMachine appliqué après modifierLot()
 *  • Tous les champs logistiques appliqués dans le handler
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

	static final int PORT = 8082;

	// ── ReadWriteLock ─────────────────────────────────────────────────────
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	// ── Version pour polling ──────────────────────────────────────────────
	private volatile long versionDonnees = System.currentTimeMillis();

	// ── Suivi clients ─────────────────────────────────────────────────────
	private final Map<String, Long> clientsActifs  = new ConcurrentHashMap<>();
	private static final long TIMEOUT_CLIENT_MS    = 30_000;

	// ── Sessions ──────────────────────────────────────────────────────────
	/** Durée de vie d'un token de session (4 heures). */
	private static final long TOKEN_TTL_MS = 4 * 3600_000L;
	private final Map<String, SessionInfo> sessions    = new ConcurrentHashMap<>();
	private final SecureRandom             rng         = new SecureRandom();
	/** Compteur et verrou de blocage par IP (protection brute-force). */
	private final Map<String, Integer>     loginEchecs = new ConcurrentHashMap<>();
	private final Map<String, Long>        loginBlocage= new ConcurrentHashMap<>();
	/** Nombre max de tentatives de login avant blocage de l'IP. */
	private static final int  MAX_ECHECS = 5;
	/** Durée du blocage après trop de tentatives échouées. */
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

		// Dossiers obligatoires
		new File(CheminApp.resoudre("app/data/courutilisation")).mkdirs();
		new File(CheminApp.resoudre("app/data/semaine_suivante")).mkdirs();
		new File(CheminApp.resoudre("app/data/enregistrementparsemaine")).mkdirs();

		log("[Serveur] Racine : " + CheminApp.getBaseDir());

		// Chiffrement AES
		try {
			this.aes = ChiffrementAES.chargerOuCreer(CheminApp.resoudre("/app/data/pastouche/secret.key"));
			this.savDonnees.setCrypte(aes);
			log("[Serveur] Chiffrement AES-256 activé.");
		} catch (Exception e) {
			log("[Serveur] AVERTISSEMENT chiffrement désactivé : " + e.getMessage());
		}

		// Chargement données
		try {
			savDonnees.charger(metier, CheminApp.resoudre("app/data/courutilisation"));
			log("[Serveur] " + metier.getLots().size() + " lots, "
				+ metier.getSocietes().size() + " sociétés.");
			detecterSemaineActive();
		} catch (Exception e) {
			log("[Serveur] Pas de données initiales : " + e.getMessage());
		}

		// ── Démarrage HTTP ────────────────────────────────────────────────
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// Routes publiques
		server.createContext("/login",                       ex -> new LoginHandler()              .handle(ex));
		server.createContext("/creer-compte",                ex -> new CreerCompteHandler()        .handle(ex));

		// Routes admin PAM
		server.createContext("/admin/demandes",              ex -> new DemandesHandler()           .handle(ex));
		server.createContext("/admin/demandes/approuver",    ex -> new ApprouverHandler()          .handle(ex));
		server.createContext("/admin/demandes/refuser",      ex -> new RefuserHandler()            .handle(ex));

		// Clé AES
		server.createContext("/cle",                         ex -> new CleHandler()                .handle(ex));

		// Lots
		server.createContext("/lots",                        ex -> new GetLotsHandler()            .handle(ex));
		server.createContext("/lots/ajouter",                ex -> new AjouterLotHandler()         .handle(ex));
		server.createContext("/lots/supprimer",              ex -> new SupprimerLotHandler()       .handle(ex));
		server.createContext("/lots/modifier",               ex -> new ModifierLotHandler()        .handle(ex));
		server.createContext("/lots/affecter",               ex -> new AffecterLotHandler()        .handle(ex));
		server.createContext("/lots/desaffecter",            ex -> new DesaffecterLotHandler()     .handle(ex));
		server.createContext("/lots/suiviprod",              ex -> new SuiviProdHandler()          .handle(ex));
		server.createContext("/lots/commencer",              ex -> new CommencerLotHandler()       .handle(ex));
		server.createContext("/lots/annuler",                ex -> new AnnulerLotHandler()         .handle(ex));
		server.createContext("/lots/terminer",               ex -> new TerminerLotHandler()        .handle(ex));
		server.createContext("/lots/phase",                  ex -> new ModifierPhaseHandler()      .handle(ex));
		server.createContext("/lots/lignecolisage/ajouter",  ex -> new AjouterLigneColisageHandler() .handle(ex));
		server.createContext("/lots/lignecolisage/supprimer",ex -> new SupprimerLigneColisageHandler().handle(ex));

		// Sociétés / ACE
		server.createContext("/societes",                    ex -> new GetSocietesHandler()        .handle(ex));
		server.createContext("/societes/modifier",           ex -> new ModifierSocieteHandler()    .handle(ex));
		server.createContext("/aces/modifier",               ex -> new ModifierAceHandler()        .handle(ex));
		server.createContext("/aces/mettreajour",            ex -> new MettreAJourAcesHandler()    .handle(ex));

		// Semaine suivante
		server.createContext("/semaine-suivante",            ex -> new GetSemaineSuivanteHandler()       .handle(ex));
		server.createContext("/semaine-suivante/sauvegarder",ex -> new SauvSemaineSuivanteHandler()      .handle(ex));
		server.createContext("/semaine-suivante/basculer",   ex -> new BasculerSemaineSuivanteHandler()  .handle(ex));

		// Système
		server.createContext("/ficheroute/",                 ex -> new FicheRouteHandler()         .handle(ex));
		server.createContext("/sauvegarder",                 ex -> new SauvegarderHandler()        .handle(ex));
		server.createContext("/nouvelleheure",               ex -> new NouvelleHeureHandler()      .handle(ex));
		server.createContext("/semainesup",                  ex -> new SemaineSupHandler()         .handle(ex));
		server.createContext("/autosave/lots",               ex -> new AutoSaveLotsHandler()       .handle(ex));
		server.createContext("/autosave/societes",           ex -> new AutoSaveSocietesHandler()   .handle(ex));
		server.createContext("/version",                     ex -> new VersionHandler()            .handle(ex));

		// Routes bloquées
		server.createContext("/charger",                     ex -> new ChargerBloqueHandler()      .handle(ex));
		server.createContext("/nouveaux",                    ex -> new NouveauxBloqueHandler()     .handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
		log("[Serveur] HTTP démarré sur le port " + PORT);

		// Nettoyage sessions
		Thread t = new Thread(() -> {
			while (true) {
				try { Thread.sleep(3_600_000L); } catch (InterruptedException e) { break; }
				sessions.entrySet().removeIf(e -> e.getValue().estExpire());
			}
		});
		t.setDaemon(true); t.setName("session-cleaner"); t.start();

		// IHM
		if (!GraphicsEnvironment.isHeadless())
		{
			final ServeurHTTP self = this;
			javax.swing.SwingUtilities.invokeLater(
				() -> new app.ihm.serveur.FenetreServeur(self));
		}
		else { menuConsole(); }
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — AUTH
	// ══════════════════════════════════════════════════════════════════════

	class LoginHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod()))
			{ rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			if (estBloquee(ip))
			{ rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return; }
			try {
				String corps = lire(ex);
				String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim();
				String motDePasse  = JsonSerialiser.extraireString(corps, "motDePasse").trim();
				if (identifiant.isEmpty()) { rep(ex, 400, "{\"err\":\"Identifiant manquant\"}"); return; }

				// Compte en attente de validation → bloquer
				if (gestionComptes.estEnAttente(identifiant)) {
					rep(ex, 403, "{\"err\":\"Votre compte est en attente de validation.\"}"); return;
				}

				// valider() retourne l'Utilisateur si ok, null sinon
				GestionComptes.Utilisateur u = gestionComptes.valider(identifiant, motDePasse);
				if (u == null) {
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
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

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
					int code = erreur.contains("existe") || erreur.contains("attente") ? 409 : 400;
					rep(ex, code, "{\"err\":\"" + erreur + "\"}"); return;
				}
				rep(ex, 200, "{\"ok\":true,\"attente\":true}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	class DemandesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			rep(ex, 200, gestionComptes.serialiserDemandesJson());
		}
	}

	class ApprouverHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String id  = JsonSerialiser.extraireString(lire(ex), "identifiant");
				String err = gestionComptes.approuver(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	class RefuserHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info == null || !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			try {
				String id  = JsonSerialiser.extraireString(lire(ex), "identifiant");
				String err = gestionComptes.refuser(id);
				if (err != null) { rep(ex, 404, "{\"err\":\"" + err + "\"}"); return; }
				rep(ex, 200, gestionComptes.serialiserDemandesJson());
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

	class CleHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			if (aes == null) { rep(ex, 204, ""); return; }
			try {
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
					JsonSerialiser.extraireBool  (c, "estMachine"),
					JsonSerialiser.extraireString(c, "dateReception"),
					JsonSerialiser.extraireString(c, "datePaiement"),
					JsonSerialiser.extraireString(c, "commentaire")
				);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
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
				int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
				if (numCDE <= 0) { rep(ex, 400, "{\"err\":\"numCDE invalide\"}"); return; }
				Lot lot = findLot(numCDE);
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.supprimerLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
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
				Lot lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }

				// ── Sauvegarder les lignes et pcsUtiliser AVANT toute modification ──
				// modifierLot() appelle setNbPieces() → recalculerLignesColisage()
				// ce qui écrase pcsUtiliser avec nbPieces, perdant l'état des lignes
				ArrayList<app.metier.lot.LigneColisage> lignesSauvees =
					new ArrayList<>(lot.getLignesColisage());
				int pcsUtiliserSauve = lot.getPcsUtiliser();

				// Champs administratifs
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
					JsonSerialiser.extraireString(c, "commentaire")
				);

				lot.setEstMachine(JsonSerialiser.extraireBool(c, "estMachine"));

				// ── Restaurer les lignes et pcsUtiliser après modifierLot() ──
				lot.getLignesColisage().clear();
				lot.getLignesColisage().addAll(lignesSauvees);
				lot.setPcsUtiliser(pcsUtiliserSauve);

				// Champs logistiques — collisage AVANT formatCarton
				lot.setMethode              (JsonSerialiser.extraireString(c, "methode"));
				lot.setDistribution         (JsonSerialiser.extraireString(c, "distribution"));
				lot.setNbPers               (JsonSerialiser.extraireInt   (c, "nbPers"));
				lot.setCadenceReel          (JsonSerialiser.extraireDouble (c, "cadenceReel"));
				lot.setPoucentrecupCartonFour(JsonSerialiser.extraireInt  (c, "poucentrecupCartonFour"));
				lot.setCollisage            (JsonSerialiser.extraireInt   (c, "collisage"));
				lot.setFormatCarton         (JsonSerialiser.extraireString(c, "formatCarton"));
				lot.recalculNbPalette();

				save();
				versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}


	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Affectation réservée à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String  c      = lire(ex);
				Lot     lot    = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				Societe soc    = findSociete(JsonSerialiser.extraireString(c, "societe"));
				String  nomAce = JsonSerialiser.extraireString(c, "ace");
				if (lot == null || soc == null) { rep(ex, 404, "{\"err\":\"lot ou société introuvable\"}"); return; }
				Ace ace = soc.getAces().stream().filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null);
				metier.affecterLot(lot, soc, ace);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
					+ ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Désaffectation réservée à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.desaffecterLot(lot);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
					+ ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SuiviProdHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c    = lire(ex);
				Lot    lot  = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				int etiq  = extraireIntPref(c, "nbPieceEtiq",  "sp_nbPieceEtiq");
				int repart= extraireIntPref(c, "nbPieceRepart","sp_nbPieceRepart");
				if (etiq  >= 0 && etiq  <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceEtiq(etiq);
				if (repart>= 0 && repart<= lot.getNbPieces()) lot.getSuivieProd().setNbPieceRepart(repart);
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

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

	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
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

	class ModifierPhaseHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c   = lire(ex);
				Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				metier.modifierPhase(lot,
					extraireBoolPref(c, "phase_preTri",     "preTri"),
					extraireBoolPref(c, "phase_surPiste",   "surPiste"),
					extraireBoolPref(c, "phase_sortieEtiq", "sortieEtiq"),
					extraireBoolPref(c, "phase_tri",        "tri"),
					extraireBoolPref(c, "phase_finit",      "finit"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
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
				Lot lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				lot.ajouterLigneColisage(
					new LigneColisage(JsonSerialiser.extraireString(c, "format"),
						JsonSerialiser.extraireInt(c, "collisage")),
					JsonSerialiser.extraireInt(c, "pcs"));
				save();
				versionDonnees = System.currentTimeMillis(); // ← AJOUT
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
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
				Lot lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
				lot.supprimerLigneColisage(JsonSerialiser.extraireInt(c, "index"));
				save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SOCIÉTÉS / ACE
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
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String  c   = lire(ex);
				Societe soc = findSociete(JsonSerialiser.extraireString(c, "nom"));
				if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
				metier.modifierSociete(soc,
					JsonSerialiser.extraireString(c, "nouveauNom"),
					JsonSerialiser.extraireString(c, "ce"),
					JsonSerialiser.extraireInt   (c, "totalHeuresCE"),
					JsonSerialiser.extraireInt   (c, "effectif"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class ModifierAceHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try {
				String c = lire(ex);
				Societe soc = findSociete(JsonSerialiser.extraireString(c, "societe"));
				if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
				String nomAce = JsonSerialiser.extraireString(c, "ace");
				Ace ace = soc.getAces().stream()
					.filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null);
				if (ace == null) { rep(ex, 404, "{\"err\":\"ACE introuvable\"}"); return; }
				metier.modifierAce(ace, ace.getNom(),
					JsonSerialiser.extraireInt(c, "nbPers"),
					JsonSerialiser.extraireInt(c, "effectifActuel"));
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class MettreAJourAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			rwLock.writeLock().lock();
			try {
				String c      = lire(ex);
				String nomSoc = JsonSerialiser.extraireString(c, "societe");
				Societe soc   = findSociete(nomSoc);
				if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
				String bloc = JsonSerialiser.extraireBloc(c, "\"aces\"");
				List<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(bloc);
				List<Ace> aces = soc.getAces();
				int min = Math.min(aces.size(), nouvellesAces.size());
				for (int i = 0; i < min; i++)
					metier.modifierAce(aces.get(i), nouvellesAces.get(i).getNom(),
						nouvellesAces.get(i).getNbPers(), nouvellesAces.get(i).getEffectifActuel());
				for (int i = aces.size() - 1; i >= nouvellesAces.size(); i--) aces.remove(i);
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
	 * Ajoute les heures d'un fichier Excel aux sociétés existantes.
	 * Appelé par FenetreServeur.
	 */
	public void nouvelleHeure(Component parent) throws Exception
	{
		// Demander le numéro de semaine
		String input = JOptionPane.showInputDialog(parent,
			"Numéro de semaine (1-53) :", "Nouvelle heure", JOptionPane.PLAIN_MESSAGE);
		if (input == null || input.isBlank()) return;
		int semaine;
		try {
			semaine = Integer.parseInt(input.trim());
			if (semaine < 1 || semaine > 53) throw new NumberFormatException();
		} catch (NumberFormatException e) {
			throw new Exception("Numéro de semaine invalide : " + input);
		}

		// Demander le fichier Excel
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter(
			"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		fc.setDialogTitle("Sélectionner le fichier des heures ACE");
		File def = new File(CheminApp.resoudre("app/data"));
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String cheminXlsx = fc.getSelectedFile().getAbsolutePath();

		rwLock.writeLock().lock();
		try {
			metier.mettreAJourHeuresSocietes(cheminXlsx, semaine);
			save();
			versionDonnees = System.currentTimeMillis();
			log("[Serveur] Nouvelle heure S" + semaine + " appliquée.");
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	/**
	 * Fusionne les lots d'un fichier Excel avec la liste en cours.
	 * Les lots déjà présents (même numCDE) ne sont pas touchés.
	 * Appelé par FenetreServeur.
	 */
	public void importerLotsSupplementaires(Component parent) throws Exception
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter(
			"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		fc.setDialogTitle("Sélectionner le fichier Excel des lots supplémentaires");
		File def = new File(CheminApp.resoudre("app/data"));
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String cheminXlsx = fc.getSelectedFile().getAbsolutePath();

		ArrayList<Lot> nouveaux = ExcelReader.lireLots(cheminXlsx);
		if (nouveaux.isEmpty())
			throw new Exception("Aucun lot trouvé dans ce fichier.");

		rwLock.writeLock().lock();
		try {
			Set<Integer> existants = new java.util.HashSet<>();
			for (Lot l : metier.getLots()) existants.add(l.getNumCDE());

			int compteur = 0;
			for (Lot l : nouveaux) {
				if (!existants.contains(l.getNumCDE())) {
					metier.getLots().add(l);
					existants.add(l.getNumCDE());
					compteur++;
				}
			}
			if (compteur == 0)
				throw new Exception("Aucun nouveau lot : tous déjà présents.");

			save();
			versionDonnees = System.currentTimeMillis();
			log("[Serveur] Import lots : " + compteur + " lot(s) ajouté(s).");
		} finally {
			rwLock.writeLock().unlock();
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SEMAINE SUIVANTE
	// ══════════════════════════════════════════════════════════════════════

	class GetSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			try {
				String lotsChemin = CheminApp.resoudre(DIR_SUIV_LOTS);
				String socsChemin = CheminApp.resoudre(DIR_SUIV_SOCS);
				if (!new File(lotsChemin).exists())
				{ rep(ex, 200, "{\"existe\":false,\"lots\":[],\"societes\":[]}"); return; }
				String lotsJson = new String(Files.readAllBytes(Paths.get(lotsChemin)), StandardCharsets.UTF_8);
				String socsJson = new File(socsChemin).exists()
					? new String(Files.readAllBytes(Paths.get(socsChemin)), StandardCharsets.UTF_8) : "[]";
				rep(ex, 200, "{\"lots\":" + lotsJson + ",\"societes\":" + socsJson + ",\"existe\":true}");
			} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
		}
	}

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

	class BasculerSemaineSuivanteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM)
			{ rep(ex, 403, "{\"err\":\"Réservé à PAM.\"}"); return; }
			rwLock.writeLock().lock();
			try {
				basculerSemaneSuivante();
				rep(ex, 200, "{\"ok\":true,\"lots\":"
					+ JsonSerialiser.serialiserLots(metier.getLots())
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
			String  nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
			Societe s   = findSociete(nom);
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
				if (!nomSoc.isEmpty()) {
					Societe s = findSociete(nomSoc);
					if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
				}
				save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class SemaineSupHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.writeLock().lock();
			try { metier.setestHeureSup(); save(); versionDonnees = System.currentTimeMillis();
				rep(ex, 200, "{\"heureSup\":" + PlanningGlobal.estHeureSup + "}"); }
			finally { rwLock.writeLock().unlock(); }
		}
	}

	class AutoSaveLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.readLock().lock();
			try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); rep(ex, 200, "{\"ok\":true}"); }
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	class AutoSaveSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			rwLock.readLock().lock();
			try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
				rep(ex, 200, "{\"ok\":true}"); }
			catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	class VersionHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			rwLock.readLock().lock();
			try { rep(ex, 200, "{\"v\":\"" + versionDonnees
				+ "\",\"heureSup\":" + PlanningGlobal.estHeureSup
				+ ",\"semaine\":" + JsonSerialiser.esc(semaineActive)
				+ ",\"nbDemandes\":" + gestionComptes.getDemandesEnAttente().size() + "}"); }
			finally { rwLock.readLock().unlock(); }
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  API PUBLIQUE (FenetreServeur + PanelSemaineSuivante)
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
			cheminLotsJson = dossier + "/lots.json"; cheminSocietesJson = dossier + "/societes.json";
			semaineActive  = numSemaine;
		} finally { rwLock.writeLock().unlock(); }
	}

	public void nouvelleSemaine(Component parent) throws Exception
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
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
				CheminApp.resoudre("app/data/pastouche/societes.json"),
				semaine, cheminHeurs);
			cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
			cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");
			save(); detecterSemaineActive();
			log("[Serveur] Nouvelle semaine chargée depuis Excel.");
		} finally { rwLock.writeLock().unlock(); }
	}

	public void toggleHeuresSup()
	{
		rwLock.writeLock().lock();
		try { metier.setestHeureSup(); save(); }
		finally { rwLock.writeLock().unlock(); }
		log("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
	}

	public ArrayList<Lot> getLotsSemaneSuivante()
	{
		try {
			String chemin = CheminApp.resoudre(DIR_SUIV_LOTS);
			if (!new File(chemin).exists()) return null;
			String dossier = Paths.get(chemin).getParent().toString();
			PlanningGlobal temp = new PlanningGlobal();
			savDonnees.charger(temp, dossier);
			ArrayList<Lot> lots = temp.getLots();
			log("[Serveur] Semaine suivante : " + lots.size() + " lots chargés.");
			return lots.isEmpty() ? null : lots;
		} catch (Exception e) { log("[ERREUR] getLotsSemaneSuivante : " + e.getMessage()); return null; }
	}

	public ArrayList<Societe> getSocietesSemaneSuivante()
	{
		try {
			String chemin = CheminApp.resoudre(DIR_SUIV_SOCS);
			if (!new File(chemin).exists()) return null;
			String dossier = Paths.get(chemin).getParent().toString();
			PlanningGlobal temp = new PlanningGlobal();
			savDonnees.charger(temp, dossier);
			ArrayList<Societe> socs = temp.getSocietes();
			return socs.isEmpty() ? null : socs;
		} catch (Exception e) { log("[ERREUR] getSocietesSemaneSuivante : " + e.getMessage()); return null; }
	}

	public ArrayList<Lot> lireExcelPourSemaineSuivante(String chemin) throws Exception
	{ return ExcelReader.lireLots(chemin); }

	public void sauvegarderSemaneSuivante(ArrayList<Lot> lots, ArrayList<Societe> societes)
	{
		try {
			String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
			String cheminS = CheminApp.resoudre(DIR_SUIV_SOCS);
			Files.createDirectories(Paths.get(cheminL).getParent());
			savDonnees.sauvegarderLots(lots, cheminL);
			savDonnees.sauvegarderSocietes(societes, lots, cheminS);
			log("[Serveur] Semaine suivante sauvegardée : " + lots.size() + " lots.");
		} catch (Exception e) { log("[Serveur] Erreur sauvegarde semaine suivante : " + e.getMessage()); }
	}

	public void sauvegarderSemaneSuivante(List<Lot> lots, List<Societe> societes)
	{ sauvegarderSemaneSuivante(new ArrayList<>(lots), new ArrayList<>(societes)); }

	public void basculerSemaneSuivante() throws Exception
	{
		String cheminL = CheminApp.resoudre(DIR_SUIV_LOTS);
		if (!new File(cheminL).exists()) throw new Exception("Aucune semaine suivante préparée.");
		ArrayList<Lot> nouveauxLots = getLotsSemaneSuivante();
		ArrayList<Societe> nouvSocs = getSocietesSemaneSuivante();
		metier.setLots(nouveauxLots);
		if (nouvSocs != null && !nouvSocs.isEmpty()) { metier.setSocietes(nouvSocs); }
		else { for (Societe s : metier.getSocietes()) { s.getLots().clear(); for (Ace a : s.getAces()) a.getLots().clear(); } }
		save(); detecterSemaineActive();
		deleteDirectory(new File(CheminApp.resoudre("app/data/semaine_suivante")));
		versionDonnees = System.currentTimeMillis();
		log("[Serveur] Bascule semaine suivante effectuée.");
	}

	public ArrayList<Societe> getSocietes()
	{
		rwLock.readLock().lock();
		try { return new ArrayList<>(metier.getSocietes()); }
		finally { rwLock.readLock().unlock(); }
	}

	public String getSemaineActive() { return semaineActive; }

	public int getNbClientsConnectes()
	{
		long now = System.currentTimeMillis();
		clientsActifs.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_CLIENT_MS);
		return clientsActifs.size();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  SÉCURITÉ — Helpers
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
		if (n >= MAX_ECHECS) {
			loginBlocage.put(ip, System.currentTimeMillis() + BLOCAGE_MS);
			loginEchecs.put(ip, 0);
			log("[Sécurité] IP bloquée 5 min : " + ip);
		}
	}

	private void enregistrerClient(HttpExchange ex)
	{
		clientsActifs.put(
			ex.getRemoteAddress().getAddress().getHostAddress(),
			System.currentTimeMillis());
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
		boolean publique = path.equals("/login") || path.equals("/cle")
			|| path.equals("/creer-compte") || path.startsWith("/methode/");
		String contenu = body;
		if (aes != null && !publique && code == 200) {
			try { contenu = aes.chiffrer(body);
				ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			} catch (Exception e) {
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
		String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		if (aes != null && !raw.startsWith("{") && !raw.isBlank()) {
			try { return aes.dechiffrer(raw); } catch (Exception ignored) {}
		}
		return raw;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES MÉTIER
	// ══════════════════════════════════════════════════════════════════════

	private Lot findLot(int numCDE)
	{ return metier.getLots().stream().filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null); }

	private Societe findSociete(String nom)
	{
		if (nom == null) return null;
		return metier.getSocietes().stream().filter(s -> nom.equals(s.getNom())).findFirst().orElse(null);
	}

	private void save()
	{
		try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); }
		catch (Exception e) { log("[ERREUR] save lots : " + e.getMessage()); }
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
		catch (Exception e) { log("[ERREUR] save societes : " + e.getMessage()); }
		versionDonnees = System.currentTimeMillis();
	}

	private void detecterSemaineActive()
	{
		if (!metier.getLots().isEmpty()) {
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
				semaineActive = sem.length() == 6
					? "S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
		}
	}

	/** Extrait un booléen en testant deux clés (préfixe long puis court). */
	private static boolean extraireBoolPref(String json, String cle1, String cle2)
	{
		boolean v = JsonSerialiser.extraireBool(json, cle1);
		return v || JsonSerialiser.extraireBool(json, cle2);
	}

	/** Extrait un entier en testant deux clés. */
	private static int extraireIntPref(String json, String cle1, String cle2)
	{
		int v = JsonSerialiser.extraireInt(json, cle1);
		return v != 0 ? v : JsonSerialiser.extraireInt(json, cle2);
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