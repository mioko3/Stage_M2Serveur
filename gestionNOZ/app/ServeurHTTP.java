package app;

import app.ihm.serveur.FenetreServeur;
import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
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
 *  ServeurHTTP — SÉCURISÉ v3
 *
 *  NOUVEAUTÉS SÉCURITÉ :
 *   • POST /login   → authentification serveur, retourne un token
 *   • Toutes les autres routes exigent X-Auth-Token dans le header
 *   • Tokens expirés après 4h, nettoyage auto
 *   • Rate-limiting : 5 échecs login → blocage IP 5 min
 *   • Protection path traversal sur /sauvegarder
 *   • Validation rôle PAM sur les routes sensibles
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;

	private String cheminLotsJson;
	private String cheminSocietesJson;

	private volatile String semaineActive = "";

	private static final int PORT = 8080;

	private final Object verrou = new Object();

	private volatile long versionDonnees = System.currentTimeMillis();

	// ── Suivi clients actifs ──────────────────────────────────────────────
	private final Map<String, Long> clientsActifs = new ConcurrentHashMap<>();
	private static final long TIMEOUT_CLIENT_MS = 10_000;

	// ══════════════════════════════════════════════════════════════════════
	//  SÉCURITÉ — Sessions
	// ══════════════════════════════════════════════════════════════════════

	private static final long TOKEN_TTL_MS = 4 * 60 * 60 * 1000L; // 4 heures

	private final Map<String, SessionInfo> sessions    = new ConcurrentHashMap<>();
	private final SecureRandom             rng         = new SecureRandom();
	private final Map<String, Integer>     loginEchecs = new ConcurrentHashMap<>();
	private final Map<String, Long>        loginBlocage= new ConcurrentHashMap<>();

	private static final int  MAX_ECHECS = 5;
	private static final long BLOCAGE_MS = 5 * 60 * 1000L;

	private static class SessionInfo {
		final String  identifiant;
		final boolean accesPAM;
		final long    createdAt;
		SessionInfo(String id, boolean pam) { this.identifiant=id; this.accesPAM=pam; this.createdAt=System.currentTimeMillis(); }
		boolean estExpire() { return System.currentTimeMillis() - createdAt > TOKEN_TTL_MS; }
	}

	// ── Constructeur ─────────────────────────────────────────────────────

	public ServeurHTTP() throws Exception
	{
		this.metier     = new PlanningGlobal();
		this.savDonnees = new DonneesSauvegarder();
		this.cheminLotsJson     = "app/data/courutilisation/lots.json";
		this.cheminSocietesJson = "app/data/courutilisation/societes.json";

		try {
			savDonnees.charger(metier, "app/data/courutilisation");
			System.out.println("[Serveur] " + metier.getLots().size() + " lots chargés.");
			detecterSemaineActive();
		} catch (Exception e) {
			System.out.println("[Serveur] Aucun chargement initial : " + e.getMessage());
		}

		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// ── ROUTE PUBLIQUE ────────────────────────────────────────────────
		server.createContext("/login",             ex -> new LoginHandler()           .handle(ex));

		// ── LOTS ─────────────────────────────────────────────────────────
		server.createContext("/lots",             ex -> new GetLotsHandler()       .handle(ex));
		server.createContext("/lots/ajouter",     ex -> new AjouterLotHandler()    .handle(ex));
		server.createContext("/lots/supprimer",   ex -> new SupprimerLotHandler()  .handle(ex));
		server.createContext("/lots/modifier",    ex -> new ModifierLotHandler()   .handle(ex));
		server.createContext("/lots/affecter",    ex -> new AffecterLotHandler()   .handle(ex));
		server.createContext("/lots/desaffecter", ex -> new DesaffecterLotHandler().handle(ex));
		server.createContext("/lots/suiviprod",   ex -> new SuiviProdHandler()     .handle(ex));
		server.createContext("/lots/commencer",   ex -> new CommencerLotHandler()  .handle(ex));
		server.createContext("/lots/annuler",     ex -> new AnnulerLotHandler()    .handle(ex));
		server.createContext("/lots/terminer",    ex -> new TerminerLotHandler()   .handle(ex));
		server.createContext("/lots/phase",       ex -> new ModifierPhaseHandler() .handle(ex));

		// ── SOCIÉTÉS / ACE ────────────────────────────────────────────────
		server.createContext("/societes",          ex -> new GetSocietesHandler()    .handle(ex));
		server.createContext("/societes/modifier", ex -> new ModifierSocieteHandler().handle(ex));
		server.createContext("/aces/modifier",     ex -> new ModifierAceHandler()    .handle(ex));
		server.createContext("/aces/mettreajour",  ex -> new MettreAJourAcesHandler().handle(ex));

		// ── SYSTÈME ───────────────────────────────────────────────────────
		server.createContext("/ficheroute/",       ex -> new FicheRouteHandler()       .handle(ex));
		server.createContext("/sauvegarder",       ex -> new SauvegarderHandler()      .handle(ex));
		server.createContext("/nouvelleheure",     ex -> new NouvelleHeureHandler()    .handle(ex));
		server.createContext("/semainesup",        ex -> new SemaineSupHandler()       .handle(ex));
		server.createContext("/autosave/lots",     ex -> new AutoSaveLotsHandler()     .handle(ex));
		server.createContext("/autosave/societes", ex -> new AutoSaveSocietesHandler() .handle(ex));
		server.createContext("/version",           ex -> new VersionHandler()          .handle(ex));

		// ── ROUTES BLOQUÉES ───────────────────────────────────────────────
		server.createContext("/charger",  ex -> new ChargerBloqueHandler() .handle(ex));
		server.createContext("/nouveaux", ex -> new NouveauxBloqueHandler().handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
		System.out.println("[Serveur] HTTP démarré sur le port " + PORT);

		// Nettoyage sessions expirées toutes les heures
		Thread t = new Thread(() -> {
			while (true) {
				try { Thread.sleep(3_600_000L); } catch (InterruptedException e) { break; }
				sessions.entrySet().removeIf(e -> e.getValue().estExpire());
			}
		});
		t.setDaemon(true); t.setName("session-cleaner"); t.start();

		javax.swing.SwingUtilities.invokeLater(() -> new FenetreServeur(this));
	}

	// ══════════════════════════════════════════════════════════════════════
	//  MÉTHODES PUBLIQUES (appelées par FenetreServeur)
	// ══════════════════════════════════════════════════════════════════════

	public void chargerSemaine(String chemin) throws Exception
	{
		synchronized (verrou) {
			savDonnees.charger(metier, chemin);
			cheminLotsJson     = chemin + "/lots.json";
			cheminSocietesJson = chemin + "/societes.json";
			versionDonnees     = System.currentTimeMillis();
			detecterSemaineActive();
		}
		System.out.println("[Serveur] Semaine chargée : " + chemin);
	}

	public void nouvelleSemaine(Component parent) throws Exception
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des lots (XLSX / XLSM)");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String xlsxLots = fc.getSelectedFile().getAbsolutePath();

		ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsxLots);
		int semaine = 0;
		if (!tempLots.isEmpty()) {
			String sem = tempLots.get(0).getSemaine();
			try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
			catch (NumberFormatException ignored) {}
		}

		fc.setDialogTitle("Sélectionner le fichier des heures ACE (ou annuler)");
		String xlsxHeures = fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath() : xlsxLots;

		synchronized (verrou) {
			metier.chargerDepuisExcel(xlsxLots, "app/data/pastouche/societes.json", semaine, xlsxHeures);
			cheminLotsJson     = "app/data/courutilisation/lots.json";
			cheminSocietesJson = "app/data/courutilisation/societes.json";
			save();
			versionDonnees = System.currentTimeMillis();
			detecterSemaineActive();
		}
		System.out.println("[Serveur] Nouvelle semaine chargée depuis Excel.");
	}

	/** Sauvegarde dans un dossier S<numSemaine>. Appelé par FenetreServeur. */
	public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
	{
		synchronized (verrou) {
			String dossier = cheminDossier + "/S" + numSemaine;
			java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
			savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
			cheminLotsJson     = dossier + "/lots.json";
			cheminSocietesJson = dossier + "/societes.json";
			semaineActive      = numSemaine;
		}
	}

	/** Toggle heures sup. Appelé par FenetreServeur. */
	public void toggleHeuresSup()
	{
		synchronized (verrou) { metier.setestHeureSup(); save(); }
		System.out.println("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
	}

	public String getSemaineActive()    { return semaineActive; }

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
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
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

	/** Renvoie 401 si token absent/invalide. Retourne true si OK. */
	private boolean exigerToken(HttpExchange ex) throws IOException
	{
		SessionInfo info = verifierToken(ex);
		if (info == null) {
			rep(ex, 401, "{\"err\":\"Non authentifié. Connectez-vous via /login.\"}");
			return false;
		}
		clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
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
			System.out.println("[Sécurité] IP bloquée 5 min : " + ip);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLER — LOGIN
	// ══════════════════════════════════════════════════════════════════════

	class LoginHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			if (estBloquee(ip)) { rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return; }

			String corps      = lire(ex);
			String identifiant = JsonSerialiser.extraireString(corps, "identifiant").trim().toUpperCase();
			if (identifiant.isEmpty()) { rep(ex, 400, "{\"err\":\"Identifiant manquant\"}"); return; }

			boolean estPAM     = identifiant.equals("PAM");
			boolean estSociete = false;
			synchronized (verrou) {
				for (Societe s : metier.getSocietes())
					if (s.getNom() != null && s.getNom().toUpperCase().equals(identifiant)) { estSociete = true; break; }
			}

			if (!estPAM && !estSociete) {
				enregistrerEchecLogin(ip);
				System.out.println("[Sécurité] Login échoué : '" + identifiant + "' depuis " + ip);
				try { Thread.sleep(50); } catch (InterruptedException ignored) {}
				rep(ex, 401, "{\"err\":\"Identifiant inconnu ou non autorisé\"}");
				return;
			}

			loginEchecs.remove(ip); loginBlocage.remove(ip);
			String token = genererToken();
			sessions.put(token, new SessionInfo(identifiant, estPAM));
			System.out.println("[Sécurité] Login OK : " + identifiant + " depuis " + ip + " | sessions: " + sessions.size());
			rep(ex, 200, "{\"token\":" + JsonSerialiser.esc(token) + ",\"accesPAM\":" + estPAM + ",\"identifiant\":" + JsonSerialiser.esc(identifiant) + "}");
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — LOTS
	// ══════════════════════════════════════════════════════════════════════

	class GetLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			synchronized (verrou) { rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
		}
	}

	class AjouterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			synchronized (verrou) {
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
						JsonSerialiser.extraireString(c, "statut"),
						JsonSerialiser.extraireString(c, "statutEchant"),
						JsonSerialiser.extraireString(c, "semaine"),
						JsonSerialiser.extraireInt(c, "priorite"),
						JsonSerialiser.extraireString(c, "lotACharge"),
						JsonSerialiser.extraireString(c, "emplacement"),
						JsonSerialiser.extraireBool(c, "estSousDouane"),
						JsonSerialiser.extraireString(c, "dateReception"),
						JsonSerialiser.extraireString(c, "datePaiement"),
						JsonSerialiser.extraireString(c, "commentaire")
					);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SupprimerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Accès réservé à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					if (numCDE <= 0) { rep(ex, 400, "{\"err\":\"numCDE invalide\"}"); return; }
					Lot lot = findLot(numCDE);
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.supprimerLot(lot);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					String c = lire(ex);
					Lot lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					// Signature exacte : modifierLot(Lot, String, String, int, double, int, String, String, String, int, String, String, boolean, String, String, String)
					metier.modifierLot(lot,
						JsonSerialiser.extraireString(c, "typologie"),
						JsonSerialiser.extraireString(c, "affaire"),
						JsonSerialiser.extraireInt(c, "nbPieces"),
						JsonSerialiser.extraireDouble(c, "cadence"),
						JsonSerialiser.extraireInt(c, "valeurVente"),
						JsonSerialiser.extraireString(c, "statut"),
						JsonSerialiser.extraireString(c, "statutEchant"),
						JsonSerialiser.extraireString(c, "semaine"),
						JsonSerialiser.extraireInt(c, "priorite"),
						JsonSerialiser.extraireString(c, "lotACharge"),
						JsonSerialiser.extraireString(c, "emplacement"),
						JsonSerialiser.extraireBool(c, "estSousDouane"),
						JsonSerialiser.extraireString(c, "dateReception"),
						JsonSerialiser.extraireString(c, "datePaiement"),
						JsonSerialiser.extraireString(c, "commentaire")
					);
					// Champs logistiques supplémentaires via méthode dédiée
					metier.modifierLotMethodeDistribution(lot,
						JsonSerialiser.extraireString(c, "methode"),
						JsonSerialiser.extraireString(c, "lotACharge")
					);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Affectation réservée à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					String  c      = lire(ex);
					Lot     lot    = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
					Societe soc    = findSociete(JsonSerialiser.extraireString(c, "societe"));
					String  nomAce = JsonSerialiser.extraireString(c, "ace");
					if (lot == null || soc == null) { rep(ex, 404, "{\"err\":\"lot ou société introuvable\"}"); return; }
					Ace ace = soc.getAces().stream().filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null);
					if (ace == null) { rep(ex, 404, "{\"err\":\"ACE introuvable\"}"); return; }
					metier.affecterLot(lot, soc, ace);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, "{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
					           + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Désaffectation réservée à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.desaffecterLot(lot);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, "{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
					           + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SuiviProdHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					String c     = lire(ex);
					Lot    lot   = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
					int    etiq  = JsonSerialiser.extraireInt(c, "nbPieceEtiq");
					int    repart= JsonSerialiser.extraireInt(c, "nbPieceRepart");
					if (etiq < 0 || repart < 0) { rep(ex, 400, "{\"err\":\"Valeurs négatives non autorisées\"}"); return; }
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					// mettreAJourSuiviProd est sur le Controleur, pas PlanningGlobal — on modifie directement
					if (etiq   <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceEtiq(etiq);
					if (repart <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceRepart(repart);
					save();
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class CommencerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.commencerLot(lot);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.annulerLot(lot);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class TerminerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.marquerLotTermine(lot);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierPhaseHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					String c   = lire(ex);
					Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
					if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					metier.modifierPhase(lot,
						JsonSerialiser.extraireBool(c, "preTri"),
						JsonSerialiser.extraireBool(c, "surPiste"),
						JsonSerialiser.extraireBool(c, "sortieEtiq"),
						JsonSerialiser.extraireBool(c, "tri"),
						JsonSerialiser.extraireBool(c, "finit")
					);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SOCIÉTÉS / ACE
	// ══════════════════════════════════════════════════════════════════════

	class GetSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			synchronized (verrou) { rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes())); }
		}
	}

	class ModifierSocieteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					String  c   = lire(ex);
					Societe soc = findSociete(JsonSerialiser.extraireString(c, "nom"));
					if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
					// Signature : modifierSociete(Societe, String nom, String ce, int totalHeuresCE, int effectif)
					metier.modifierSociete(soc,
						JsonSerialiser.extraireString(c, "nouveauNom"),
						JsonSerialiser.extraireString(c, "ce"),
						JsonSerialiser.extraireInt(c, "totalHeuresCE"),
						JsonSerialiser.extraireInt(c, "effectif")
					);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierAceHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					String c = lire(ex);
					Societe soc = findSociete(JsonSerialiser.extraireString(c, "societe"));
					if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
					String nomAce = JsonSerialiser.extraireString(c, "ace");
					Ace ace = soc.getAces().stream().filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null);
					if (ace == null) { rep(ex, 404, "{\"err\":\"ACE introuvable\"}"); return; }
					// Signature : modifierAce(Ace, String nom, int nbPers, int effectif)
					metier.modifierAce(ace, ace.getNom(),
						JsonSerialiser.extraireInt(c, "nbPers"),
						JsonSerialiser.extraireInt(c, "effectifActuel")
					);
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class MettreAJourAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					String c       = lire(ex);
					String nomSoc  = JsonSerialiser.extraireString(c, "societe");
					Societe soc    = findSociete(nomSoc);
					if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
					String bloc    = JsonSerialiser.extraireBloc(c, "\"aces\"");
					ArrayList<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(bloc);
					// Logique de mise à jour directe (reproduit Controleur.mettreAJourAces)
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
					versionDonnees = System.currentTimeMillis();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
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
			if (s == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }
			rep(ex, 200, JsonSerialiser.serialiserFicheRoute(metier.genererFicheRoute(s)));
		}
	}

	class SauvegarderHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try {
					String c       = lire(ex);
					String chemin  = JsonSerialiser.extraireString(c, "chemin");
					String semaine = JsonSerialiser.extraireString(c, "semaine");
					// Protection path traversal
					if (chemin.contains("..")) { rep(ex, 400, "{\"err\":\"Chemin non autorisé\"}"); return; }
					String dossier = chemin + "/S" + semaine;
					java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
					savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
					savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
					cheminLotsJson     = dossier + "/lots.json";
					cheminSocietesJson = dossier + "/societes.json";
					rep(ex, 200, "{\"ok\":true}");
				} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ChargerBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			System.out.println("[Serveur] BLOCAGE /charger depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	class NouveauxBloqueHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			System.out.println("[Serveur] BLOCAGE /nouveaux depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
			rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
		}
	}

	class NouvelleHeureHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			SessionInfo info = verifierToken(ex);
			if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
			synchronized (verrou) {
				try {
					String c = lire(ex);
					String nomSoc = JsonSerialiser.extraireString(c, "societe");
					if (nomSoc != null && !nomSoc.isEmpty()) {
						Societe s = findSociete(nomSoc);
						if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
					} else {
						String bloc = JsonSerialiser.extraireBloc(c, "\"societes\"");
						if (bloc != null && !bloc.isEmpty()) {
							String[] entries = bloc.replace("[","").replace("]","").split("\\},\\{");
							for (String entry : entries) {
								entry = entry.replace("{","").replace("}","");
								String nom    = JsonSerialiser.extraireString("{" + entry + "}", "nom");
								int    heures = JsonSerialiser.extraireInt   ("{" + entry + "}", "heures");
								Societe s = findSociete(nom);
								if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + heures);
							}
						}
					}
					save();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SemaineSupHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) { metier.setestHeureSup(); save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
		}
	}

	class AutoSaveLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); rep(ex, 200, "{\"ok\":true}"); }
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AutoSaveSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			synchronized (verrou) {
				try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); rep(ex, 200, "{\"ok\":true}"); }
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class VersionHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			if (!exigerToken(ex)) return;
			enregistrerClient(ex);
			synchronized (verrou) {
				rep(ex, 200, "{\"v\":\"" + versionDonnees + "\",\"heureSup\":" + PlanningGlobal.estHeureSup
					+ ",\"semaine\":" + JsonSerialiser.esc(semaineActive) + "}");
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES
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
		if (!metier.getLots().isEmpty()) {
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
				semaineActive = sem.length() == 6 ? "S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
		}
	}

	private void enregistrerClient(HttpExchange ex)
	{
		clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
	}

	private static String lire(HttpExchange ex) throws IOException
	{
		return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private static void rep(HttpExchange ex, int code, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
	}

	public static void main(String[] args) throws Exception { new ServeurHTTP(); }
}