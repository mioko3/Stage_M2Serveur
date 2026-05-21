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
 * Serveur HTTP — port 8080
 * Lance avec : java -cp ... app.ServeurHTTP
 *
 * Pas de Swing, pas de Controleur — utilise PlanningGlobal directement.
 */
public class ServeurHTTP
{
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;

	private String cheminLotsJson     = "app/data/courutilisation/lots.json";
	private String cheminSocietesJson = "app/data/courutilisation/societes.json";

	private static final int    PORT   = 8080;
	private final        Object verrou = new Object();

	// ── Démarrage ─────────────────────────────────────────────────────────

	public ServeurHTTP() throws Exception
	{
		this.metier     = new PlanningGlobal();
		this.savDonnees = new DonneesSauvegarder();

		// Chargement initial si des données existent
		try
		{
			savDonnees.charger(metier, "app/data/courutilisation");
			System.out.println("[Serveur] " + metier.getLots().size() + " lots chargés.");
		}
		catch (Exception e)
		{
			System.out.println("[Serveur] Démarrage à vide : " + e.getMessage());
		}

		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// ── Routes LOTS ──────────────────────────────────────────────────
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

		// ── Routes SOCIETES / ACE ─────────────────────────────────────────
		server.createContext("/societes",          ex -> new GetSocietesHandler()    .handle(ex));
		server.createContext("/societes/modifier", ex -> new ModifierSocieteHandler().handle(ex));
		server.createContext("/aces/modifier",     ex -> new ModifierAceHandler()    .handle(ex));
		server.createContext("/aces/mettreajour",  ex -> new MettreAJourAcesHandler().handle(ex));

		// ── Routes SYSTEME ────────────────────────────────────────────────
		server.createContext("/ficheroute/",       ex -> new FicheRouteHandler()    .handle(ex));
		server.createContext("/sauvegarder",       ex -> new SauvegarderHandler()   .handle(ex));
		server.createContext("/charger",           ex -> new ChargerHandler()       .handle(ex));
		server.createContext("/nouveaux",          ex -> new NouveauxHandler()      .handle(ex));
		server.createContext("/semainesup",        ex -> new SemaineSupHandler()    .handle(ex));
		server.createContext("/autosave/lots",     ex -> new AutoSaveLotsHandler()  .handle(ex));
		server.createContext("/autosave/societes", ex -> new AutoSaveSocHandler()   .handle(ex));

		server.setExecutor(Executors.newFixedThreadPool(10));
		server.start();
		System.out.println("[Serveur] Démarré → http://localhost:" + PORT);
		System.out.println("[Serveur] Lancer le client avec : java -cp ... app.ControleurClient <IP>");
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private String lire(HttpExchange ex) throws IOException
	{ return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); }

	private void rep(HttpExchange ex, int code, String body) throws IOException
	{
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type",                "application/json; charset=UTF-8");
		ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
		ex.sendResponseHeaders(code, b.length);
		try (OutputStream os = ex.getResponseBody()) { os.write(b); }
	}

	private Lot findLot(int id)
	{ for (Lot l : metier.getLots()) if (l.getNumCDE() == id) return l; return null; }

	private Societe findSociete(String nom)
	{ if (nom==null) return null; for (Societe s : metier.getSocietes()) if (s.getNom().equals(nom)) return s; return null; }

	private void save()
	{
		try
		{
			savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
			savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
		}
		catch (Exception e) { System.err.println("[Serveur] Save : " + e.getMessage()); }
	}

	private String dual()
	{
		return "{\"lots\":"     + JsonSerialiser.serialiserLots    (metier.getLots())
		     + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}";
	}

	// ── Handlers LOTS ─────────────────────────────────────────────────────

	class GetLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
		}
	}

	class AjouterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try {
					Lot l = JsonSerialiser.deserialiserLot(lire(ex));
					metier.ajouterLot(l);
					save();
					rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class SupprimerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.supprimerLot(l); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class ModifierLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
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

	class AffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				String  c   = lire(ex);
				Lot     l   = findLot(JsonSerialiser.extraireInt   (c, "numCDE"));
				Societe s   = findSociete(JsonSerialiser.extraireString(c, "nomSociete"));
				if (l == null || s == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				Ace     a   = s.getAce(JsonSerialiser.extraireString(c, "nomAce"));
				if (a == null) { rep(ex, 404, "{\"err\":\"ACE not found\"}"); return; }
				if (!metier.affecterLot(l, s, a)) { rep(ex, 409, "{\"err\":\"heures insuffisantes\"}"); return; }
				save();
				rep(ex, 200, dual());
			}
		}
	}

	class DesaffecterLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.desaffecterLot(l); save();
				rep(ex, 200, dual());
			}
		}
	}

	class SuiviProdHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				String c = lire(ex);
				Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				l.getSuivieProd().setNbPieceEtiq  (JsonSerialiser.extraireInt(c, "nbPieceEtiq"));
				l.getSuivieProd().setNbPieceRepart(JsonSerialiser.extraireInt(c, "nbPieceRepart"));
				save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class CommencerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.commencerLot(l); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class AnnulerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.annulerLot(l); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class TerminerLotHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
				if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
				metier.marquerLotTermine(l); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class ModifierPhaseHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				String c = lire(ex);
				Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
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

	// ── Handlers SOCIETES / ACE ───────────────────────────────────────────

	class GetSocietesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
		}
	}

	class ModifierSocieteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				String  c = lire(ex);
				Societe s = findSociete(JsonSerialiser.extraireString(c, "nomActuel"));
				if (s == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
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

	class ModifierAceHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try {
					String  c   = lire(ex);
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
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class MettreAJourAcesHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try {
					String  c   = lire(ex);
					Societe soc = findSociete(JsonSerialiser.extraireString(c, "societe"));
					if (soc == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }

					ArrayList<Ace> nouvelles = JsonSerialiser.deserialiserAces(
						JsonSerialiser.extraireBloc(c, "\"aces\""));

					java.util.List<Ace> aces = soc.getAces();
					for (int i = nouvelles.size(); i < aces.size(); i++)
						if (!aces.get(i).getLots().isEmpty())
						{ rep(ex, 409, "{\"err\":\"ACE avec lots\"}"); return; }

					int min = Math.min(aces.size(), nouvelles.size());
					for (int i = 0; i < min; i++)
						metier.modifierAce(aces.get(i), nouvelles.get(i).getNom(),
							nouvelles.get(i).getNbPers(), nouvelles.get(i).getEffectifActuel());
					for (int i = aces.size()-1; i >= nouvelles.size(); i--) aces.remove(i);
					for (int i = min; i < nouvelles.size(); i++) {
						Ace n = nouvelles.get(i);
						aces.add(new Ace(n.getNom(), n.getNbPers(), n.getEffectifActuel()));
					}
					save();
					rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
				} catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	// ── Handlers SYSTEME ─────────────────────────────────────────────────

	class FicheRouteHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			String  nom = java.net.URLDecoder.decode(
				ex.getRequestURI().getPath().replace("/ficheroute/", ""),
				StandardCharsets.UTF_8);
			Societe s = findSociete(nom);
			if (s == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
			rep(ex, 200, JsonSerialiser.serialiserFicheRoute(metier.genererFicheRoute(s)));
		}
	}

	class SauvegarderHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try {
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
				} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class ChargerHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try {
					String chemin = JsonSerialiser.extraireString(lire(ex), "chemin");
					savDonnees.charger(metier, chemin);
					cheminLotsJson     = chemin + "/lots.json";
					cheminSocietesJson = chemin + "/societes.json";
					rep(ex, 200, dual());
				} catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class NouveauxHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				metier.nouveau(); save();
				rep(ex, 200, "{\"ok\":true}");
			}
		}
	}

	class SemaineSupHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				metier.setestHeureSup(); save();
				rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
			}
		}
	}

	class AutoSaveLotsHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); rep(ex, 200, "{\"ok\":true}"); }
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	class AutoSaveSocHandler implements HttpHandler {
		public void handle(HttpExchange ex) throws IOException {
			synchronized (verrou) {
				try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); rep(ex, 200, "{\"ok\":true}"); }
				catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
			}
		}
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args) throws Exception
	{
		new ServeurHTTP();
		// Le thread HTTP reste actif — pas besoin de boucle
	}
}