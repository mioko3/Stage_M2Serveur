package app;

import app.ihm.FenetrePrincipale;
import app.metier.collecte.JsonSerialiser;
import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ControleurClient — mode réseau.
 *
 *  Toutes les actions passent par HTTP vers ServeurHTTP.
 *  Implémente IControleur → interchangeable avec Controleur.
 *
 *  Particularités vs Controleur :
 *    - exportNewLot()         → non supporté en mode client (Excel local)
 *    - nouvelleHeurePourSociete() → le client lit l'Excel localement,
 *      parse les heures, et envoie les données JSON au serveur
 *    - mettreAJourAces()      → sérialise la liste complète et envoie au serveur
 * ══════════════════════════════════════════════════════════════
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	private ArrayList<Lot>     lots;
	private ArrayList<Societe> societes;

	private final String cheminLotsJson     = "";
	private final String cheminSocietesJson = "";

	// ── Constructeur ─────────────────────────────────────────────────────

	public ControleurClient(String ipServeur)
	{
		this.urlServeur = "http://" + ipServeur + ":8080";
		this.http       = HttpClient.newHttpClient();
		this.lots       = new ArrayList<>();
		this.societes   = new ArrayList<>();

		System.out.println("[Client] Connexion → " + urlServeur);

		SwingUtilities.invokeLater(() ->
		{
			chargerDepuisServeur();
			this.fenetre = new FenetrePrincipale(this);
		});
	}

	// ── Chargement initial ────────────────────────────────────────────────

	private void chargerDepuisServeur()
	{
		try
		{
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			System.out.println("[Client] " + lots.size() + " lots chargés.");
		}
		catch (Exception e)
		{
			System.err.println("[Client] Impossible de contacter le serveur : " + e.getMessage());
		}
	}

	public boolean rechargerDepuisServeur()
	{
		try
		{
			ArrayList<Lot>     nouveauxLots     = JsonSerialiser.deserialiserLots(get("/lots"));
			ArrayList<Societe> nouvellesSocietes = JsonSerialiser.deserialiserSocietes(
				get("/societes"), nouveauxLots);

			boolean changed = !JsonSerialiser.serialiserLots(nouveauxLots)
				.equals(JsonSerialiser.serialiserLots(this.lots));

			this.lots     = nouveauxLots;
			this.societes = nouvellesSocietes;
			return changed;
		}
		catch (Exception e)
		{
			System.err.println("[Client] Erreur sync : " + e.getMessage());
			return false;
		}
	}

	// ── IControleur : Données ─────────────────────────────────────────────

	@Override public ArrayList<Societe> getSocietes()          { return societes;          }
	@Override public ArrayList<Lot>     getLots()              { return lots;              }
	@Override public String             getCheminLotsJson()    { return cheminLotsJson;    }
	@Override public String             getCheminSocietesJson(){ return cheminSocietesJson;}

	// ── IControleur : Gestion des lots ───────────────────────────────────

	@Override
	public void ajouterLot(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/ajouter", JsonSerialiser.serialiserLotSeul(lot)));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("ajouterLot", e); }
	}

	@Override
	public void ajouterLot(int numCDE, String typologie, String affaire,
	                       int nbPieces, double cadence, int valeurVente,
	                       String statut, String statutEchant,
	                       String semaine, int priorite,
	                       String lotACharge, String emplacement,
	                       boolean sousDouane, String dateReception,
	                       String datePaiement, String commentaire)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"        + numCDE                 + ","
				+ "\"typologie\":"     + e(typologie)           + ","
				+ "\"affaire\":"       + e(affaire)             + ","
				+ "\"nbPieces\":"      + nbPieces               + ","
				+ "\"cadence\":"       + cadence                + ","
				+ "\"valeurVente\":"   + valeurVente            + ","
				+ "\"statut\":"        + e(statut)              + ","
				+ "\"statutEchant\":"  + e(statutEchant)        + ","
				+ "\"semaine\":"       + e(semaine)             + ","
				+ "\"priorite\":"      + priorite               + ","
				+ "\"lotACharge\":"    + e(lotACharge)          + ","
				+ "\"emplacement\":"   + e(emplacement)         + ","
				+ "\"estSousDouane\":" + sousDouane             + ","
				+ "\"dateReception\":" + e(dateReception)       + ","
				+ "\"datePaiement\":"  + e(datePaiement)        + ","
				+ "\"commentaire\":"   + e(commentaire)
				+ "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", corps));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("ajouterLot", e); }
	}

	@Override
	public void supprimerLot(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("supprimerLot", e); }
	}

	@Override
	public void sauvegarderLots()
	{
		autoSauvegarderLots();
	}

	@Override
	public void exportNewLot()
	{
		// En mode client, l'import Excel se fait localement
		// puis les lots sont envoyés un par un au serveur.
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des nouveaux lots");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

		try
		{
			ArrayList<Lot> nouveaux = app.metier.collecte.ExcelReader.lireLots(
				fc.getSelectedFile().getAbsolutePath());
			int importes = 0;
			for (Lot l : nouveaux)
			{
				boolean deja = false;
				for (Lot ex : this.lots)
					if (ex.getNumCDE() == l.getNumCDE()) { deja = true; break; }
				if (!deja) { ajouterLot(l); importes++; }
			}
			JOptionPane.showMessageDialog(null, importes + " lot(s) importé(s).",
				"Import terminé", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(null, "Erreur : " + ex.getMessage(),
				"Import lots", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ── IControleur : Modification lots ──────────────────────────────────

	@Override
	public void modifierLot(Lot lot, String typologie, String affaire,
	                        int nbPieces, double cadence, int valeurVente,
	                        String statut, String statutEchant,
	                        String semaine, int priorite,
	                        String lotACharge, String emplacement,
	                        boolean sousDouane, String dateReception,
	                        String datePaiement, String commentaire)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"        + lot.getNumCDE()        + ","
				+ "\"typologie\":"     + e(typologie)           + ","
				+ "\"affaire\":"       + e(affaire)             + ","
				+ "\"nbPieces\":"      + nbPieces               + ","
				+ "\"cadence\":"       + cadence                + ","
				+ "\"valeurVente\":"   + valeurVente            + ","
				+ "\"statut\":"        + e(statut)              + ","
				+ "\"statutEchant\":"  + e(statutEchant)        + ","
				+ "\"semaine\":"       + e(semaine)             + ","
				+ "\"priorite\":"      + priorite               + ","
				+ "\"lotACharge\":"    + e(lotACharge)          + ","
				+ "\"emplacement\":"   + e(emplacement)         + ","
				+ "\"estSousDouane\":" + sousDouane             + ","
				+ "\"dateReception\":" + e(dateReception)       + ","
				+ "\"datePaiement\":"  + e(datePaiement)        + ","
				+ "\"commentaire\":"   + e(commentaire)
				+ "}";

			this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", corps));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("modifierLot", e); }
	}

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"     + lot.getNumCDE() + ","
				+ "\"preTri\":"     + preTri          + ","
				+ "\"surPiste\":"   + surPiste        + ","
				+ "\"sortieEtiq\":" + sortieEtiq      + ","
				+ "\"tri\":"        + tri             + ","
				+ "\"finit\":"      + finit
				+ "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", corps));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("modifierPhase", e); }
	}

	@Override
	public void marquerLotTermine(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/terminer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("marquerLotTermine", e); }
	}

	@Override
	public void commencerLot(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/commencer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
		}
		catch (Exception e) { err("commencerLot", e); }
	}

	@Override
	public void annulerLot(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/annuler", "{\"numCDE\":" + lot.getNumCDE() + "}"));
		}
		catch (Exception e) { err("annulerLot", e); }
	}

	// ── IControleur : Affectation ─────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"    + lot.getNumCDE()    + ","
				+ "\"nomSociete\":" + e(societe.getNom()) + ","
				+ "\"nomAce\":"     + e(ace.getNom())
				+ "}";
			mettreAJourDepuisReponseDual(post("/lots/affecter", corps));
			autoSauvegarderSocietes();
			return true;
		}
		catch (Exception e) { err("affecterLot", e); return false; }
	}

	@Override
	public void desaffecterLot(Lot lot)
	{
		try
		{
			mettreAJourDepuisReponseDual(
				post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			autoSauvegarderSocietes();
		}
		catch (Exception e) { err("desaffecterLot", e); }
	}

	// ── IControleur : Sociétés ────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce,
	                            int totalHeuresCE, int effectif)
	{
		try
		{
			String corps = "{"
				+ "\"nomActuel\":"     + e(soc.getNom()) + ","
				+ "\"nom\":"           + e(nom)          + ","
				+ "\"ce\":"            + e(ce)           + ","
				+ "\"totalHeuresCE\":" + totalHeuresCE   + ","
				+ "\"effectif\":"      + effectif
				+ "}";
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/societes/modifier", corps), this.lots);
			autoSauvegarderSocietes();
		}
		catch (Exception e) { err("modifierSociete", e); }
	}

	@Override
	public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
	{
		try
		{
			Societe soc = getSocieteDeAce(ace);
			if (soc == null) { err("modifierAce — ACE sans société", null); return; }

			String corps = "{"
				+ "\"societe\":"    + e(soc.getNom())  + ","
				+ "\"nomActuel\":"  + e(ace.getNom())  + ","
				+ "\"nom\":"        + e(nom)           + ","
				+ "\"nbPers\":"     + nbPers           + ","
				+ "\"effectif\":"   + effectif
				+ "}";
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/aces/modifier", corps), this.lots);
			autoSauvegarderSocietes();
		}
		catch (Exception e) { err("modifierAce", e); }
	}

	/**
	 * Envoie la liste complète des nouvelles ACE au serveur.
	 * Corps : { "societe":"NomSoc", "aces": [...] }
	 */
	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces)
	{
		try
		{
			// Sérialiser la liste des ACE
			StringBuilder acesJson = new StringBuilder("[");
			for (int i = 0; i < nouvellesAces.size(); i++)
			{
				Ace a = nouvellesAces.get(i);
				acesJson.append("{\"nom\":").append(e(a.getNom()))
				        .append(",\"nbPers\":").append(a.getNbPers())
				        .append(",\"effectif\":").append(a.getEffectifActuel())
				        .append("}");
				if (i < nouvellesAces.size() - 1) acesJson.append(",");
			}
			acesJson.append("]");

			String corps = "{\"societe\":" + e(soc.getNom())
				+ ",\"aces\":" + acesJson + "}";

			String rep = post("/aces/mettreajour", corps);
			this.societes = JsonSerialiser.deserialiserSocietes(rep, this.lots);
			autoSauvegarderSocietes();
			return true;
		}
		catch (Exception e) { err("mettreAJourAces", e); return false; }
	}

	/**
	 * Nouvelle heure pour une société.
	 * Le client lit l'Excel localement, parse les heures, et envoie au serveur.
	 * Corps envoyé : { "societes": [{"nom":"X","heures":N}, ...] }
	 */
	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des heures ACE");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
		{
			JOptionPane.showMessageDialog(null, "Aucun fichier sélectionné.",
				"Nouvelle heure", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		try
		{
			// Lire les heures depuis l'Excel localement
			java.util.Map<String, Integer> heuresParSoc =
				app.metier.collecte.ExcelReader.lireHeuresSocietes(
					fc.getSelectedFile().getAbsolutePath(), semaine);

			// Construire le JSON et envoyer au serveur
			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heuresParSoc.entrySet())
			{
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey()))
				  .append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");

			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/nouvelleheure", sb.toString()), this.lots);
			autoSauvegarderSocietes();

			JOptionPane.showMessageDialog(null, "Heures ajoutées avec succès !",
				"Nouvelle heure", JOptionPane.INFORMATION_MESSAGE);

			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null, "Erreur : " + e.getMessage(),
				"Nouvelle heure", JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void semaineSup()
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(post("/semainesup", "{}"));
		}
		catch (Exception e) { err("semaineSup", e); }
	}

	// ── IControleur : Suivi prod ──────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"        + lot.getNumCDE() + ","
				+ "\"nbPieceEtiq\":"   + nbPieceEtiq    + ","
				+ "\"nbPieceRepart\":" + nbPieceRepart
				+ "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", corps));
			autoSauvegarderLots();
		}
		catch (Exception e) { err("mettreAJourSuiviProd", e); }
	}

	// ── IControleur : Recherche (locale sur cache) ────────────────────────

	@Override
	public Societe getSocieteDuLot(Lot lot)
	{
		for (Societe s : societes)
			for (Lot l : s.getLots())
				if (l.getNumCDE() == lot.getNumCDE()) return s;
		return null;
	}

	@Override
	public Ace getAceDuLot(Lot lot)
	{
		for (Societe s : societes)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l.getNumCDE() == lot.getNumCDE()) return a;
		return null;
	}

	@Override
	public ArrayList<Ace> getTouteAces()
	{
		ArrayList<Ace> tout = new ArrayList<>();
		for (Societe s : societes)
			tout.addAll(s.getAces());
		return tout;
	}

	// ── IControleur : Fiche de route ─────────────────────────────────────

	@Override
	public FicheRoute genererFicheRoute(Societe societe)
	{
		try
		{
			String json = get("/ficheroute/" + java.net.URLEncoder.encode(
				societe.getNom(), StandardCharsets.UTF_8));
			return JsonSerialiser.deserialiserFicheRoute(json, societe);
		}
		catch (Exception e)
		{
			err("genererFicheRoute", e);
			return new app.metier.ficheroute.FicheRoute(societe);
		}
	}

	// ── IControleur : Sauvegarde / Chargement ────────────────────────────

	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try
		{
			post("/sauvegarder",
				"{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}");
		}
		catch (Exception e) { err("sauvegarderDonnees", e); }
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		try
		{
			mettreAJourDepuisReponseDual(
				post("/charger", "{\"chemin\":" + e(chemin) + "}"));
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception e) { throw new IOException(e.getMessage()); }
	}

	@Override
	public void nouveaux()
	{
		try
		{
			post("/nouveaux", "{}");
			rechargerDepuisServeur();
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception e) { err("nouveaux", e); }
	}

	@Override
	public void autoSauvegarde()
	{
		autoSauvegarderLots();
		autoSauvegarderSocietes();
	}

	// ── Auto-sauvegarde ───────────────────────────────────────────────────

	private void autoSauvegarderLots()
	{
		try { post("/autosave/lots", "{}"); }
		catch (Exception e) { err("autoSauvegarderLots", e); }
	}

	private void autoSauvegarderSocietes()
	{
		try { post("/autosave/societes", "{}"); }
		catch (Exception e) { err("autoSauvegarderSocietes", e); }
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private void mettreAJourDepuisReponseDual(String rep)
	{
		String jsonLots = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jsonSoc  = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jsonLots != null) this.lots     = JsonSerialiser.deserialiserLots(jsonLots);
		if (jsonSoc  != null) this.societes = JsonSerialiser.deserialiserSocietes(jsonSoc, this.lots);
	}

	private Societe getSocieteDeAce(Ace ace)
	{
		for (Societe s : societes)
			for (Ace a : s.getAces())
				if (a.getNom().equals(ace.getNom())) return s;
		return null;
	}

	private String get(String route) throws Exception
	{
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.GET()
			.build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() >= 400)
			throw new Exception("HTTP " + resp.statusCode() + " : " + resp.body());
		return resp.body();
	}

	private String post(String route, String json) throws Exception
	{
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() >= 400)
			throw new Exception("HTTP " + resp.statusCode() + " : " + resp.body());
		return resp.body();
	}

	private static String e(String s) { return JsonSerialiser.esc(s); }

	private void err(String methode, Exception e)
	{
		System.err.println("[Client] " + methode + " : " + (e != null ? e.getMessage() : "null"));
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		String ip = args.length > 0 ? args[0] : "localhost";
		SwingUtilities.invokeLater(() -> new ControleurClient(ip));
	}
}