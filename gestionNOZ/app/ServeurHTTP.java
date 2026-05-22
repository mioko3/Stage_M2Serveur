package app;

import app.ihm.serveur.FenetreServeur;
import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.ficheroute.FicheRoute;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — serveur de données pour gestionNOZ
 *
 *  NOUVEAUTÉS v2 :
 *   • Lance une FenetreServeur (IHM) au démarrage
 *   • Le serveur seul peut charger / créer une semaine
 *   • Les clients ont ces routes BLOQUÉES (403)
 *   • Compteur de clients actifs (polling < 10 s)
 *
 *  Routes LOTS :
 *    GET  /lots
 *    POST /lots/ajouter
 *    POST /lots/supprimer
 *    POST /lots/modifier
 *    POST /lots/affecter
 *    POST /lots/desaffecter
 *    POST /lots/suiviprod
 *    POST /lots/commencer
 *    POST /lots/annuler
 *    POST /lots/terminer
 *    POST /lots/phase
 *
 *  Routes SOCIÉTÉS / ACE :
 *    GET  /societes
 *    POST /societes/modifier
 *    POST /aces/modifier
 *    POST /aces/mettreajour
 *
 *  Routes SYSTÈME (réservées serveur — 403 pour clients distants) :
 *    POST /charger      → bloqué pour les clients
 *    POST /nouveaux     → bloqué pour les clients
 *    POST /sauvegarder  → autorisé (lecture seule côté client de toute façon)
 *    POST /nouvelleheure
 *    POST /semainesup
 *    POST /autosave/lots
 *    POST /autosave/societes
 *    GET  /version
 *    GET  /ficheroute/{nom}
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;

	private String cheminLotsJson;
	private String cheminSocietesJson;

	// Semaine courante affichée dans la FenetreServeur
	private volatile String semaineActive = "";

	private static final int PORT = 8080;

	private final Object verrou = new Object();

	private volatile long versionDonnees = System.currentTimeMillis();

	// ── Suivi clients actifs ──────────────────────────────────────────────
	// Clé = IP cliente, valeur = timestamp du dernier poll (ms)
	private final Map<String, Long> clientsActifs = new ConcurrentHashMap<>();
	private static final long TIMEOUT_CLIENT_MS = 10_000; // 10 secondes

	// ── Constructeur ─────────────────────────────────────────────────────

	public ServeurHTTP() throws Exception
	{
		this.metier     = new PlanningGlobal();
		this.savDonnees = new DonneesSauvegarder();

		this.cheminLotsJson     = "app/data/courutilisation/lots.json";
		this.cheminSocietesJson = "app/data/courutilisation/societes.json";

		try
		{
			savDonnees.charger(metier, "app/data/courutilisation");
			System.out.println("[Serveur] " + metier.getLots().size() + " lots chargés.");
			detecterSemaineActive();
		}
		catch (Exception e)
		{
			System.out.println("[Serveur] Aucun chargement initial : " + e.getMessage());
		}

		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

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

		// ── ROUTES BLOQUÉES POUR LES CLIENTS DISTANTS ─────────────────────
		server.createContext("/charger",  ex -> new ChargerBloqueHandler() .handle(ex));
		server.createContext("/nouveaux", ex -> new NouveauxBloqueHandler().handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
		System.out.println("[Serveur] HTTP démarré sur le port " + PORT);

		// Lancer l'IHM serveur sur l'EDT
		javax.swing.SwingUtilities.invokeLater(() -> new FenetreServeur(this));
	}

	// ══════════════════════════════════════════════════════════════════════
	//  MÉTHODES PUBLIQUES appelées par FenetreServeur
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Charge une semaine depuis un dossier.
	 * Appelé uniquement depuis FenetreServeur (sur l'EDT Swing, mais méthode thread-safe).
	 */
	public void chargerSemaine(String chemin) throws Exception
	{
		synchronized (verrou)
		{
			savDonnees.charger(metier, chemin);
			cheminLotsJson     = chemin + "/lots.json";
			cheminSocietesJson = chemin + "/societes.json";
			versionDonnees     = System.currentTimeMillis();
			detecterSemaineActive();
		}
		System.out.println("[Serveur] Semaine chargée : " + chemin);
	}

	/**
	 * Crée une nouvelle semaine depuis un fichier Excel.
	 * Ouvre les boîtes de dialogue de sélection de fichier.
	 * Appelé uniquement depuis FenetreServeur.
	 */
	public void nouvelleSemaine(Component parent) throws Exception
	{
		// Sélection du fichier lots
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des lots (XLSX / XLSM)");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);

		if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
		String xlsxLots = fc.getSelectedFile().getAbsolutePath();

		// Détecter la semaine depuis les lots
		ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsxLots);
		int semaine = 0;
		if (!tempLots.isEmpty())
		{
			String sem = tempLots.get(0).getSemaine();
			try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
			catch (NumberFormatException ignored) {}
		}

		// Sélection du fichier heures
		fc.setDialogTitle("Sélectionner le fichier des heures ACE (ou annuler pour réutiliser le même)");
		String xlsxHeures = fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath()
			: xlsxLots;

		synchronized (verrou)
		{
			metier.chargerDepuisExcel(xlsxLots, "app/data/pastouche/societes.json", semaine, xlsxHeures);
			cheminLotsJson     = "app/data/courutilisation/lots.json";
			cheminSocietesJson = "app/data/courutilisation/societes.json";
			save();
			versionDonnees = System.currentTimeMillis();
			detecterSemaineActive();
		}
		System.out.println("[Serveur] Nouvelle semaine chargée depuis Excel.");
	}

	/**
	 * Sauvegarde la semaine courante dans un dossier.
	 * Appelé uniquement depuis FenetreServeur.
	 */
	public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
	{
		synchronized (verrou)
		{
			String dossier = cheminDossier + "/S" + numSemaine;
			java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
			savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
			cheminLotsJson     = dossier + "/lots.json";
			cheminSocietesJson = dossier + "/societes.json";
			semaineActive      = numSemaine;
		}
		System.out.println("[Serveur] Semaine S" + numSemaine + " sauvegardée.");
	}

	/** Retourne la semaine active (affichée dans FenetreServeur). */
	public String getSemaineActive() { return semaineActive; }

	/** Retourne le nombre de clients ayant pollé dans les 10 dernières secondes. */
	public int getNbClientsConnectes()
	{
		long now = System.currentTimeMillis();
		clientsActifs.entrySet().removeIf(e -> now - e.getValue() > TIMEOUT_CLIENT_MS);
		return clientsActifs.size();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — LOTS
	// ══════════════════════════════════════════════════════════════════════

	class GetLotsHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			enregistrerClient(ex);
			if (!"GET".equals(ex.getRequestMethod())) { rep(ex, 405, "{}"); return; }
			synchronized (verrou) { rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
		}
	}

	class AjouterLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c = lire(ex);
					Lot lot  = JsonSerialiser.deserialiserLot(c);
					metier.ajouterLot(lot);
					save();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SupprimerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null) { metier.supprimerLot(lot); save(); }
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c   = lire(ex);
					int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null)
					{
						metier.modifierLot(lot,
							JsonSerialiser.extraireString(c, "typologie"),
							JsonSerialiser.extraireString(c, "affaire"),
							JsonSerialiser.extraireInt   (c, "nbPieces"),
							JsonSerialiser.extraireDouble(c, "cadence"),
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
						// Champs métier supplémentaires
						String methode = JsonSerialiser.extraireString(c, "methode");
						String lotCharge = JsonSerialiser.extraireString(c, "lotACharge");
						if (methode != null && !methode.isEmpty())
							metier.modifierLotMethodeDistribution(lot, methode, lotCharge);
						save();
					}
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AffecterLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c    = lire(ex);
					int numCDE  = JsonSerialiser.extraireInt   (c, "numCDE");
					String soc  = JsonSerialiser.extraireString(c, "societe");
					String ace  = JsonSerialiser.extraireString(c, "ace");
					Lot    lot  = findLot(numCDE);
					Societe s   = findSociete(soc);
					Ace     a   = (s != null) ? findAce(s, ace) : null;
					if (lot != null && s != null && a != null)
					{ metier.affecterLot(lot, s, a); save(); }
					rep(ex, 200, repDual());
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class DesaffecterLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null) { metier.desaffecterLot(lot); save(); }
					rep(ex, 200, repDual());
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SuiviProdHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c        = lire(ex);
					int    numCDE   = JsonSerialiser.extraireInt(c, "numCDE");
					int    nbEtiq   = JsonSerialiser.extraireInt(c, "nbPieceEtiq");
					int    nbRepart = JsonSerialiser.extraireInt(c, "nbPieceRepart");
					Lot    lot      = findLot(numCDE);
					if (lot != null && nbEtiq <= lot.getNbPieces() && nbRepart <= lot.getNbPieces())
					{
						lot.getSuivieProd().setNbPieceEtiq  (nbEtiq);
						lot.getSuivieProd().setNbPieceRepart(nbRepart);
						save();
					}
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class CommencerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null) { metier.commencerLot(lot); save(); }
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AnnulerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null) { metier.annulerLot(lot); save(); }
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class TerminerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					int numCDE = JsonSerialiser.extraireInt(lire(ex), "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null) { metier.marquerLotTermine(lot); save(); }
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierPhaseHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c   = lire(ex);
					int numCDE = JsonSerialiser.extraireInt (c, "numCDE");
					Lot lot    = findLot(numCDE);
					if (lot != null)
					{
						metier.modifierPhase(lot,
							JsonSerialiser.extraireBool(c, "preTri"),
							JsonSerialiser.extraireBool(c, "surPiste"),
							JsonSerialiser.extraireBool(c, "sortieEtiq"),
							JsonSerialiser.extraireBool(c, "tri"),
							JsonSerialiser.extraireBool(c, "finit"));
						save();
					}
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SOCIÉTÉS / ACE
	// ══════════════════════════════════════════════════════════════════════

	class GetSocietesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			enregistrerClient(ex);
			if (!"GET".equals(ex.getRequestMethod())) { rep(ex, 405, "{}"); return; }
			synchronized (verrou) { rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes())); }
		}
	}

	class ModifierSocieteHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c    = lire(ex);
					String nom  = JsonSerialiser.extraireString(c, "nom");
					Societe soc = findSociete(nom);
					if (soc != null)
					{
						metier.modifierSociete(soc,
							JsonSerialiser.extraireString(c, "nom"),
							JsonSerialiser.extraireString(c, "ce"),
							JsonSerialiser.extraireInt   (c, "totalHeuresCE"),
							JsonSerialiser.extraireInt   (c, "effectif"));
						save();
					}
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ModifierAceHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c      = lire(ex);
					String nomSoc = JsonSerialiser.extraireString(c, "societe");
					String nomAce = JsonSerialiser.extraireString(c, "nom");
					Societe soc   = findSociete(nomSoc);
					if (soc != null)
					{
						Ace ace = findAce(soc, nomAce);
						if (ace != null)
						{
							metier.modifierAce(ace,
								JsonSerialiser.extraireString(c, "nom"),
								JsonSerialiser.extraireInt   (c, "nbPers"),
								JsonSerialiser.extraireInt   (c, "effectifActuel"));
							save();
						}
					}
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class MettreAJourAcesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String  c      = lire(ex);
					String  nomSoc = JsonSerialiser.extraireString(c, "societe");
					Societe soc    = findSociete(nomSoc);

					if (soc == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }

					String blocAces = JsonSerialiser.extraireBloc(c, "\"aces\"");
					ArrayList<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(blocAces);

					ArrayList<Ace> aces = soc.getAces();
					int min = Math.min(aces.size(), nouvellesAces.size());
					for (int i = 0; i < min; i++)
						metier.modifierAce(aces.get(i), nouvellesAces.get(i).getNom(),
							nouvellesAces.get(i).getNbPers(), nouvellesAces.get(i).getEffectifActuel());
					for (int i = aces.size()-1; i >= nouvellesAces.size(); i--)
						aces.remove(i);
					for (int i = min; i < nouvellesAces.size(); i++)
					{
						Ace n = nouvellesAces.get(i);
						aces.add(new Ace(n.getNom(), n.getNbPers(), n.getEffectifActuel()));
					}

					save();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HANDLERS — SYSTÈME
	// ══════════════════════════════════════════════════════════════════════

	class FicheRouteHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			String  nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
			Societe s   = findSociete(nom);
			if (s == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }
			FicheRoute f = metier.genererFicheRoute(s);
			rep(ex, 200, JsonSerialiser.serialiserFicheRoute(f));
		}
	}

	class SauvegarderHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c       = lire(ex);
					String chemin  = JsonSerialiser.extraireString(c, "chemin");
					String semaine = JsonSerialiser.extraireString(c, "semaine");
					String dossier = chemin + "/S" + semaine;

					java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
					savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
					savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");

					cheminLotsJson     = dossier + "/lots.json";
					cheminSocietesJson = dossier + "/societes.json";
					rep(ex, 200, "{\"ok\":true}");
				}
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	/**
	 * ── ROUTE BLOQUÉE : /charger ──────────────────────────────────────────
	 * Les clients distants ne peuvent PAS changer la semaine.
	 * Le serveur utilise chargerSemaine() via FenetreServeur.
	 */
	class ChargerBloqueHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			System.out.println("[Serveur] BLOCAGE /charger depuis " + ip);
			rep(ex, 403,
				"{\"err\":\"Action réservée au serveur. Utilisez le panneau de contrôle du serveur.\"}");
		}
	}

	/**
	 * ── ROUTE BLOQUÉE : /nouveaux ─────────────────────────────────────────
	 * Les clients distants ne peuvent PAS créer une nouvelle semaine.
	 */
	class NouveauxBloqueHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			String ip = ex.getRemoteAddress().getAddress().getHostAddress();
			System.out.println("[Serveur] BLOCAGE /nouveaux depuis " + ip);
			rep(ex, 403,
				"{\"err\":\"Action réservée au serveur. Utilisez le panneau de contrôle du serveur.\"}");
		}
	}

	class NouvelleHeureHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c = lire(ex);

					String nomSoc = JsonSerialiser.extraireString(c, "societe");
					if (nomSoc != null && !nomSoc.isEmpty())
					{
						Societe s = findSociete(nomSoc);
						if (s != null)
						{
							int heures = JsonSerialiser.extraireInt(c, "heures");
							s.setTotalHeuresCE(s.getTotalHeuresCE() + heures);
						}
					}
					else
					{
						String bloc = JsonSerialiser.extraireBloc(c, "\"societes\"");
						if (bloc != null && !bloc.isEmpty())
						{
							String[] entries = bloc.replace("[","").replace("]","").split("\\},\\{");
							for (String entry : entries)
							{
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
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SemaineSupHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				metier.setestHeureSup();
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class AutoSaveLotsHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
					rep(ex, 200, "{\"ok\":true}");
				}
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AutoSaveSocietesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
					rep(ex, 200, "{\"ok\":true}");
				}
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class VersionHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			enregistrerClient(ex);
			rep(ex, 200, "{\"v\":\"" + versionDonnees + "\"}");
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES
	// ══════════════════════════════════════════════════════════════════════

	private void enregistrerClient(HttpExchange ex)
	{
		String ip = ex.getRemoteAddress().getAddress().getHostAddress();
		clientsActifs.put(ip, System.currentTimeMillis());
	}

	private void detecterSemaineActive()
	{
		if (!metier.getLots().isEmpty())
		{
			String sem = metier.getLots().get(0).getSemaine();
			if (sem != null && sem.length() >= 2)
			{
				// Format "202617" → afficher "S17 / 2026"
				if (sem.length() == 6)
					semaineActive = "S" + sem.substring(4) + " / " + sem.substring(0, 4);
				else
					semaineActive = sem;
			}
		}
	}

	private void save()
	{
		try { savDonnees.sauvegarderLots    (metier.getLots(),     cheminLotsJson);     } catch (Exception e) { System.err.println("[Save lots] "     + e.getMessage()); }
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); } catch (Exception e) { System.err.println("[Save soc] "      + e.getMessage()); }
		versionDonnees = System.currentTimeMillis();
	}

	private String repDual()
	{
		return "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
			 + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}";
	}

	private Lot     findLot    (int numCDE) { for (Lot l     : metier.getLots())     if (l.getNumCDE()     == numCDE) return l; return null; }
	private Societe findSociete(String nom) { for (Societe s : metier.getSocietes()) if (s.getNom().equals(nom))       return s; return null; }
	private Ace     findAce    (Societe s, String nom) { for (Ace a : s.getAces()) if (a.getNom().equals(nom)) return a; return null; }

	private String lire(HttpExchange ex) throws IOException
	{
		return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private void rep(HttpExchange ex, int code, String json) throws IOException
	{
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
		ex.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args) throws Exception
	{
		new ServeurHTTP();
	}
}