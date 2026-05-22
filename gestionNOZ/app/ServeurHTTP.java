package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.JsonSerialiser;
import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — serveur de données pour gestionNOZ
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
 *    POST /aces/mettreajour      ← ajouté
 *
 *  Routes SYSTÈME :
 *    GET  /ficheroute/{nom}
 *    POST /sauvegarder
 *    POST /charger
 *    POST /nouveaux
 *    POST /nouvelleheure         ← implémenté (données JSON du fichier Excel non dispo côté serveur
 *                                   → le serveur met à jour les heures depuis le JSON reçu)
 *    POST /semainesup            ← ajouté
 *    POST /autosave/lots         ← ajouté
 *    POST /autosave/societes     ← ajouté
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;

	private String cheminLotsJson;
	private String cheminSocietesJson;

	private static final int PORT = 8080;

	private final Object verrou = new Object();

	private volatile long versionDonnees = System.currentTimeMillis();

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
		server.createContext("/ficheroute/",       ex -> new FicheRouteHandler()    .handle(ex));
		server.createContext("/sauvegarder",       ex -> new SauvegarderHandler()   .handle(ex));
		server.createContext("/charger",           ex -> new ChargerHandler()       .handle(ex));
		server.createContext("/nouveaux",          ex -> new NouveauxHandler()      .handle(ex));
		server.createContext("/nouvelleheure",     ex -> new NouvelleHeureHandler() .handle(ex));
		server.createContext("/semainesup",        ex -> new SemaineSupHandler()    .handle(ex));
		server.createContext("/autosave/lots",     ex -> new AutoSaveLotsHandler()  .handle(ex));
		server.createContext("/autosave/societes", ex -> new AutoSaveSocietesHandler().handle(ex));
		server.createContext("/version",           ex -> new VersionHandler()       .handle(ex)); 

		server.setExecutor(Executors.newFixedThreadPool(10));
		server.start();

		System.out.println("[Serveur] Démarré sur port " + PORT);
	}

	// ══════════════════════════════════════════════════════════════════════
	// UTILS
	// ══════════════════════════════════════════════════════════════════════

	private String lire(HttpExchange ex) throws IOException
	{
		return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private void rep(HttpExchange ex, int code, String body) throws IOException
	{
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type",                 "application/json; charset=UTF-8");
		ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
		ex.sendResponseHeaders(code, b.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(b); }
	}

	private Lot findLot(int id)
	{
		for (Lot l : metier.getLots())
			if (l.getNumCDE() == id) return l;
		return null;
	}

	private Societe findSociete(String nom)
	{
		if (nom == null) return null;
		for (Societe s : metier.getSocietes())
			if (s.getNom().equals(nom)) return s;
		return null;
	}

	/** Sauvegarde automatique dans les fichiers courants. */
	private void save()
	{
		try
		{
			savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
			miseAJourVersion(); // ← ajouter cette ligne
		}
		catch (Exception e)
		{
			System.err.println("[Serveur] Save error: " + e.getMessage());
		}
	}

	private void miseAJourVersion() // ← nouvelle méthode juste après
	{
		versionDonnees = System.currentTimeMillis();
	}

	/** Réponse JSON double : lots + sociétés. */
	private String repDual()
	{
		return "{\"lots\":"     + JsonSerialiser.serialiserLots    (metier.getLots())
			 + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes())
			 + "}";
	}

	// ══════════════════════════════════════════════════════════════════════
	// HANDLERS — LOTS
	// ══════════════════════════════════════════════════════════════════════

	class GetLotsHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
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
					Lot l = JsonSerialiser.deserialiserLot(lire(ex));
					metier.ajouterLot(l);
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
				int id = JsonSerialiser.extraireInt(lire(ex), "numCDE");
				Lot l  = findLot(id);
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.supprimerLot(l);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class ModifierLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				String c = lire(ex);
				Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }

				metier.modifierLot(l,
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
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class AffecterLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				String  c   = lire(ex);
				Lot     l   = findLot(JsonSerialiser.extraireInt   (c, "numCDE"));
				Societe s   = findSociete(JsonSerialiser.extraireString(c, "nomSociete"));
				if (l == null || s == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				Ace     a   = s.getAce(JsonSerialiser.extraireString(c, "nomAce"));
				if (a == null) { rep(ex, 404, "{\"err\":\"ACE not found\"}"); return; }

				boolean ok = metier.affecterLot(l, s, a);
				if (!ok) { rep(ex, 409, "{\"err\":\"heures insuffisantes\"}"); return; }

				save();
				rep(ex, 200, repDual());
			}
		}
	}

	class DesaffecterLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.desaffecterLot(l);
				save();
				rep(ex, 200, repDual());
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
					String c = lire(ex);
					Lot    l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
					if (l == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
					l.getSuivieProd().setNbPieceEtiq  (JsonSerialiser.extraireInt(c, "nbPieceEtiq"));
					l.getSuivieProd().setNbPieceRepart(JsonSerialiser.extraireInt(c, "nbPieceRepart"));
					save();
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
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.commencerLot(l);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class AnnulerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.annulerLot(l);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class TerminerLotHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.marquerLotTermine(l);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class ModifierPhaseHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				String c = lire(ex);
				Lot    l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }

				metier.modifierPhase(l,
					JsonSerialiser.extraireBool(c, "preTri"),
					JsonSerialiser.extraireBool(c, "surPiste"),
					JsonSerialiser.extraireBool(c, "sortieEtiq"),
					JsonSerialiser.extraireBool(c, "tri"),
					JsonSerialiser.extraireBool(c, "finit")
				);
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	// HANDLERS — SOCIÉTÉS / ACE
	// ══════════════════════════════════════════════════════════════════════

	class GetSocietesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
		}
	}

	class ModifierSocieteHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				String  c = lire(ex);
				Societe s = findSociete(JsonSerialiser.extraireString(c, "nomActuel"));
				if (s == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }

				metier.modifierSociete(s,
					JsonSerialiser.extraireString(c, "nom"),
					JsonSerialiser.extraireString(c, "ce"),
					JsonSerialiser.extraireInt   (c, "totalHeuresCE"),
					JsonSerialiser.extraireInt   (c, "effectif")
				);
				save();
				rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
			}
		}
	}

	/**
	 * Modifier une ACE individuelle.
	 * Corps : { "societe":"NomSoc", "nomActuel":"AncienNom",
	 *           "nom":"NouveauNom", "nbPers":N, "effectif":N }
	 * Note : le ControleurClient envoie "societe" (pas "nomSociete").
	 */
	class ModifierAceHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String  c   = lire(ex);
					// ← clé "societe" (envoyée par ControleurClient)
					Societe soc = findSociete(JsonSerialiser.extraireString(c, "societe"));
					if (soc == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }

					Ace a = soc.getAce(JsonSerialiser.extraireString(c, "nomActuel"));
					if (a == null) { rep(ex, 404, "{\"err\":\"ACE not found\"}"); return; }

					metier.modifierAce(a,
						JsonSerialiser.extraireString(c, "nom"),
						JsonSerialiser.extraireInt   (c, "nbPers"),
						JsonSerialiser.extraireInt   (c, "effectif")
					);
					save();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				}
				catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	/**
	 * Remplacer la liste complète des ACE d'une société.
	 * Corps : { "societe":"NomSoc",
	 *           "aces": [{"nom":"A1","nbPers":3,"effectif":2}, ...] }
	 */
	class MettreAJourAcesHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String  c   = lire(ex);
					Societe soc = findSociete(JsonSerialiser.extraireString(c, "societe"));
					if (soc == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }

					// Désérialiser la liste d'ACE depuis le JSON
					ArrayList<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(
						JsonSerialiser.extraireBloc(c, "\"aces\"")
					);

					// Vérifier qu'on ne supprime pas une ACE avec des lots
					java.util.List<Ace> aces = soc.getAces();
					for (int i = nouvellesAces.size(); i < aces.size(); i++)
						if (!aces.get(i).getLots().isEmpty())
						{
							rep(ex, 409, "{\"err\":\"ACE avec lots affectés\"}");
							return;
						}

					// Appliquer les modifications
					int min = Math.min(aces.size(), nouvellesAces.size());
					for (int i = 0; i < min; i++)
					{
						Ace ancien  = aces.get(i);
						Ace nouveau = nouvellesAces.get(i);
						metier.modifierAce(ancien,
							nouveau.getNom(), nouveau.getNbPers(), nouveau.getEffectifActuel());
					}
					for (int i = aces.size() - 1; i >= nouvellesAces.size(); i--)
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
	// HANDLERS — SYSTÈME
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

	class ChargerHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String chemin = JsonSerialiser.extraireString(lire(ex), "chemin");
					savDonnees.charger(metier, chemin);
					cheminLotsJson     = chemin + "/lots.json";
					cheminSocietesJson = chemin + "/societes.json";
					rep(ex, 200, repDual());
				}
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class NouveauxHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				metier.nouveau();
				save();
				rep(ex, 200, "{\"ok\":true}");
			}
		}
	}

	/**
	 * Nouvelle heure pour une société.
	 * Le fichier Excel n'est pas disponible côté serveur — le client
	 * envoie directement les données d'heures parsées.
	 * Corps : { "societe":"NomSoc", "heures":N }
	 * OU pour mettre à jour plusieurs sociétés :
	 * Corps : { "societes": [{"nom":"S1","heures":120}, ...] }
	 */
	class NouvelleHeureHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			synchronized (verrou)
			{
				try
				{
					String c = lire(ex);

					// Cas 1 : mise à jour d'une seule société
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
						// Cas 2 : liste de sociétés
						// Format: {"societes":[{"nom":"X","heures":N},...]}
						String bloc = JsonSerialiser.extraireBloc(c, "\"societes\"");
						if (bloc != null && !bloc.isEmpty())
						{
							// Parsing simple : chaque {"nom":"X","heures":N}
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

	/**
	 * Toggle heures supplémentaires.
	 * Corps : {} (simple toggle côté serveur)
	 */
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

	/**
	 * Auto-sauvegarde lots (appelé périodiquement par le client).
	 * Contenu ignoré — on sauvegarde simplement l'état courant.
	 */
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

	/**
	 * Auto-sauvegarde sociétés (appelé périodiquement par le client).
	 */
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

	// ── Version (polling client) ──────────────────────────────────────────
	class VersionHandler implements HttpHandler
	{
		public void handle(HttpExchange ex) throws IOException
		{
			rep(ex, 200, "{\"v\":\"" + versionDonnees + "\"}");
		}
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args) throws Exception
	{
		new ServeurHTTP();
	}
}
