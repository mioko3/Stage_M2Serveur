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
import javax.swing.SwingUtilities;

/**
 * Contrôleur MVC — mode CLIENT.
 *
 * Même structure que Controleur mais
 * toutes les actions passent par HTTP.
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	private ArrayList<Lot>     lots;
	private ArrayList<Societe> societes;

	private String cheminLotsJson;
	private String cheminSocietesJson;

	// ── Constructeur ─────────────────────────────────────────────

	public ControleurClient(String ipServeur)
	{
		this.urlServeur = "http://" + ipServeur + ":8080";
		this.http       = HttpClient.newHttpClient();

		this.lots       = new ArrayList<>();
		this.societes   = new ArrayList<>();

		this.cheminLotsJson     = "";
		this.cheminSocietesJson = "";

		System.out.println("[Client] Connexion → " + urlServeur);

		SwingUtilities.invokeLater(() ->
		{
			chargerDepuisServeur();
			this.fenetre = new FenetrePrincipale(this);
		});
	}

	// ── Chargement ───────────────────────────────────────────────

	private void chargerDepuisServeur()
	{
		try
		{
			this.lots =
				JsonSerialiser.deserialiserLots(
					get("/lots"));

			this.societes =
				JsonSerialiser.deserialiserSocietes(
					get("/societes"),
					this.lots);

			System.out.println(
				"[Client] "
				+ lots.size()
				+ " lots chargés.");
		}
		catch (Exception e)
		{
			System.err.println(
				"[Client] Impossible de contacter le serveur : "
				+ e.getMessage());
		}
	}

	public boolean rechargerDepuisServeur()
	{
		try
		{
			String jsonLots = get("/lots");
			String jsonSoc  = get("/societes");

			ArrayList<Lot> nouveauxLots =
				JsonSerialiser.deserialiserLots(jsonLots);

			ArrayList<Societe> nouvellesSocietes =
				JsonSerialiser.deserialiserSocietes(
					jsonSoc,
					nouveauxLots);

			boolean changed =
				!JsonSerialiser.serialiserLots(nouveauxLots)
					.equals(
						JsonSerialiser.serialiserLots(this.lots));

			this.lots      = nouveauxLots;
			this.societes  = nouvellesSocietes;

			return changed;
		}
		catch (Exception e)
		{
			System.err.println(
				"[Client] Erreur sync : "
				+ e.getMessage());

			return false;
		}
	}

	// ── IControleur : Données ───────────────────────────────────

	@Override
	public ArrayList<Societe> getSocietes()
	{
		return societes;
	}

	@Override
	public ArrayList<Lot> getLots()
	{
		return lots;
	}

	@Override
	public String getCheminLotsJson()
	{
		return cheminLotsJson;
	}

	@Override
	public String getCheminSocietesJson()
	{
		return cheminSocietesJson;
	}

	// ── IControleur : Gestion lots ──────────────────────────────

	@Override
	public void ajouterLot(Lot lot)
	{
		try
		{
			String rep =
				post(
					"/lots/ajouter",
					JsonSerialiser.serialiserLotSeul(lot));

			this.lots =
				JsonSerialiser.deserialiserLots(rep);

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("ajouterLot", e);
		}
	}

	@Override
	public void ajouterLot(
		int numCDE,
		String typologie,
		String affaire,
		int nbPieces,
		double cadence,
		int valeurVente,
		String statut,
		String statutEchant,
		String semaine,
		int priorite,
		String lotACharge,
		String emplacement,
		boolean sousDouane,
		String dateReception,
		String datePaiement,
		String commentaire)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"        + numCDE           + ","
				+ "\"typologie\":"     + e(typologie)     + ","
				+ "\"affaire\":"       + e(affaire)       + ","
				+ "\"nbPieces\":"      + nbPieces         + ","
				+ "\"cadence\":"       + cadence          + ","
				+ "\"valeurVente\":"   + valeurVente      + ","
				+ "\"statut\":"        + e(statut)        + ","
				+ "\"statutEchant\":"  + e(statutEchant)  + ","
				+ "\"semaine\":"       + e(semaine)       + ","
				+ "\"priorite\":"      + priorite         + ","
				+ "\"lotACharge\":"    + e(lotACharge)    + ","
				+ "\"emplacement\":"   + e(emplacement)   + ","
				+ "\"sousDouane\":"    + sousDouane       + ","
				+ "\"dateReception\":" + e(dateReception) + ","
				+ "\"datePaiement\":"  + e(datePaiement)  + ","
				+ "\"commentaire\":"   + e(commentaire)
				+ "}";

			this.lots =
				JsonSerialiser.deserialiserLots(
					post("/lots/ajoutercomplet", corps));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("ajouterLotComplet", e);
		}
	}

	@Override
	public void supprimerLot(Lot lot)
	{
		try
		{
			this.lots =
				JsonSerialiser.deserialiserLots(
					post(
						"/lots/supprimer",
						"{\"numCDE\":"
						+ lot.getNumCDE()
						+ "}"));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("supprimerLot", e);
		}
	}

	@Override
	public void sauvegarderLots()
	{
		autoSauvegarderLots();
	}

	@Override
	public void exportNewLot()
	{
		try
		{
			post("/lots/exportnew", "{}");
			chargerDepuisServeur();
		}
		catch (Exception e)
		{
			err("exportNewLot", e);
		}
	}

	// ── Affectation ─────────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":" + lot.getNumCDE() + ","
				+ "\"societe\":" + e(societe.getNom()) + ","
				+ "\"ace\":" + e(ace.getNom())
				+ "}";

			String rep =
				post("/lots/affecter", corps);

			mettreAJourDepuisReponseDual(rep);

			autoSauvegarderSocietes();

			return true;
		}
		catch (Exception e)
		{
			err("affecterLot", e);
			return false;
		}
	}

	@Override
	public void desaffecterLot(Lot lot)
	{
		try
		{
			String rep =
				post(
					"/lots/desaffecter",
					"{\"numCDE\":"
					+ lot.getNumCDE()
					+ "}");

			mettreAJourDepuisReponseDual(rep);

			autoSauvegarderSocietes();
		}
		catch (Exception e)
		{
			err("desaffecterLot", e);
		}
	}

	// ── Modification lots ───────────────────────────────────────

	@Override
	public void modifierLot(
		Lot lot,
		String typologie,
		String affaire,
		int nbPieces,
		double cadence,
		int valeurVente,
		String statut,
		String statutEchant,
		String semaine,
		int priorite,
		String lotACharge,
		String emplacement,
		boolean sousDouane,
		String dateReception,
		String datePaiement,
		String commentaire)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":"        + lot.getNumCDE() + ","
				+ "\"typologie\":"     + e(typologie)    + ","
				+ "\"affaire\":"       + e(affaire)      + ","
				+ "\"nbPieces\":"      + nbPieces        + ","
				+ "\"cadence\":"       + cadence         + ","
				+ "\"valeurVente\":"   + valeurVente     + ","
				+ "\"statut\":"        + e(statut)       + ","
				+ "\"statutEchant\":"  + e(statutEchant) + ","
				+ "\"semaine\":"       + e(semaine)      + ","
				+ "\"priorite\":"      + priorite        + ","
				+ "\"lotACharge\":"    + e(lotACharge)   + ","
				+ "\"emplacement\":"   + e(emplacement)  + ","
				+ "\"sousDouane\":"    + sousDouane      + ","
				+ "\"dateReception\":" + e(dateReception)+ ","
				+ "\"datePaiement\":"  + e(datePaiement) + ","
				+ "\"commentaire\":"   + e(commentaire)
				+ "}";

			this.lots =
				JsonSerialiser.deserialiserLots(
					post("/lots/modifier", corps));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("modifierLot", e);
		}
	}

	@Override
	public void modifierPhase(
		Lot lot,
		boolean preTri,
		boolean surPiste,
		boolean sortieEtiq,
		boolean tri,
		boolean finit)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":" + lot.getNumCDE() + ","
				+ "\"preTri\":" + preTri + ","
				+ "\"surPiste\":" + surPiste + ","
				+ "\"sortieEtiq\":" + sortieEtiq + ","
				+ "\"tri\":" + tri + ","
				+ "\"finit\":" + finit
				+ "}";

			this.lots =
				JsonSerialiser.deserialiserLots(
					post("/lots/modifierphase", corps));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("modifierPhase", e);
		}
	}

	@Override
	public void marquerLotTermine(Lot lot)
	{
		try
		{
			this.lots =
				JsonSerialiser.deserialiserLots(
					post(
						"/lots/terminer",
						"{\"numCDE\":"
						+ lot.getNumCDE()
						+ "}"));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("marquerLotTermine", e);
		}
	}

	@Override
	public void commencerLot(Lot l)
	{
		try
		{
			this.lots =
				JsonSerialiser.deserialiserLots(
					post(
						"/lots/commencer",
						"{\"numCDE\":"
						+ l.getNumCDE()
						+ "}"));
		}
		catch (Exception e)
		{
			err("commencerLot", e);
		}
	}

	@Override
	public void annulerLot(Lot l)
	{
		try
		{
			this.lots =
				JsonSerialiser.deserialiserLots(
					post(
						"/lots/annuler",
						"{\"numCDE\":"
						+ l.getNumCDE()
						+ "}"));
		}
		catch (Exception e)
		{
			err("annulerLot", e);
		}
	}

	// ── Sociétés ────────────────────────────────────────────────

	@Override
	public void modifierSociete(
		Societe soc,
		String nom,
		String ce,
		int totalHeuresCE,
		int effectif)
	{
		try
		{
			String corps = "{"
				+ "\"nomActuel\":" + e(soc.getNom()) + ","
				+ "\"nom\":" + e(nom) + ","
				+ "\"ce\":" + e(ce) + ","
				+ "\"totalHeuresCE\":" + totalHeuresCE + ","
				+ "\"effectif\":" + effectif
				+ "}";

			this.societes =
				JsonSerialiser.deserialiserSocietes(
					post("/societes/modifier", corps),
					this.lots);

			autoSauvegarderSocietes();
		}
		catch (Exception e)
		{
			err("modifierSociete", e);
		}
	}

	@Override
	public void modifierAce(
		Ace ace,
		String nom,
		int nbPers,
		int effectif)
	{
		try
		{
			Societe soc = getSocieteDeAce(ace);

			String corps = "{"
				+ "\"societe\":" + e(soc.getNom()) + ","
				+ "\"nomActuel\":" + e(ace.getNom()) + ","
				+ "\"nom\":" + e(nom) + ","
				+ "\"nbPers\":" + nbPers + ","
				+ "\"effectif\":" + effectif
				+ "}";

			this.societes =
				JsonSerialiser.deserialiserSocietes(
					post("/aces/modifier", corps),
					this.lots);

			autoSauvegarderSocietes();
		}
		catch (Exception e)
		{
			err("modifierAce", e);
		}
	}

	@Override
	public boolean mettreAJourAces(
		Societe soc,
		List<Ace> nouvellesAces)
	{
		try
		{
			post("/aces/mettreajour", "{}");

			chargerDepuisServeur();

			autoSauvegarderSocietes();

			return true;
		}
		catch (Exception e)
		{
			err("mettreAJourAces", e);
			return false;
		}
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		try
		{
			post(
				"/nouvelleheure",
				"{\"semaine\":"
				+ semaine
				+ "}");

			chargerDepuisServeur();

			autoSauvegarderSocietes();
		}
		catch (Exception e)
		{
			err("nouvelleHeurePourSociete", e);
		}
	}

	@Override
	public void semaineSup()
	{
		try
		{
			post("/semainesup", "{}");
			chargerDepuisServeur();
		}
		catch (Exception e)
		{
			err("semaineSup", e);
		}
	}

	// ── Suivi prod ──────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(
		Lot lot,
		int nbPieceEtiq,
		int nbPieceRepart)
	{
		try
		{
			String corps = "{"
				+ "\"numCDE\":" + lot.getNumCDE() + ","
				+ "\"nbPieceEtiq\":" + nbPieceEtiq + ","
				+ "\"nbPieceRepart\":" + nbPieceRepart
				+ "}";

			this.lots =
				JsonSerialiser.deserialiserLots(
					post("/lots/suiviprod", corps));

			autoSauvegarderLots();
		}
		catch (Exception e)
		{
			err("mettreAJourSuiviProd", e);
		}
	}

	// ── Recherche ───────────────────────────────────────────────

	@Override
	public Societe getSocieteDuLot(Lot lot)
	{
		for (Societe s : societes)
			for (Lot l : s.getLots())
				if (l.getNumCDE() == lot.getNumCDE())
					return s;

		return null;
	}

	@Override
	public Ace getAceDuLot(Lot lot)
	{
		for (Societe s : societes)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l.getNumCDE() == lot.getNumCDE())
						return a;

		return null;
	}

	@Override
	public ArrayList<Ace> getTouteAces()
	{
		ArrayList<Ace> lst = new ArrayList<>();

		for (Societe s : societes)
			lst.addAll(s.getAces());

		return lst;
	}

	// ── Fiche route ─────────────────────────────────────────────

	@Override
	public FicheRoute genererFicheRoute(Societe societe)
	{
		return new FicheRoute(societe);
	}

	// ── Sauvegarde / chargement ─────────────────────────────────

	@Override
	public void sauvegarderDonnees(
		String cheminDossier,
		String semaine)
	{
		try
		{
			post(
				"/sauvegarder",
				"{\"chemin\":"
				+ e(cheminDossier)
				+ ",\"semaine\":"
				+ e(semaine)
				+ "}");
		}
		catch (Exception e)
		{
			err("sauvegarderDonnees", e);
		}
	}

	@Override
	public void chargerDonnees(String chemin)
		throws IOException
	{
		try
		{
			post(
				"/charger",
				"{\"chemin\":"
				+ e(chemin)
				+ "}");

			chargerDepuisServeur();

			if (fenetre != null)
				fenetre.rafraichirTout();
		}
		catch (Exception e)
		{
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public void nouveaux()
	{
		try
		{
			post("/nouveaux", "{}");

			chargerDepuisServeur();

			this.autoSauvegarde();
		}
		catch (Exception e)
		{
			err("nouveaux", e);
		}
	}

	// ── Auto save ───────────────────────────────────────────────

	@Override
	public void autoSauvegarde()
	{
		autoSauvegarderLots();
		autoSauvegarderSocietes();
	}

	private void autoSauvegarderLots()
	{
		try
		{
			post("/autosave/lots", "{}");
		}
		catch (Exception e)
		{
			err("autoSauvegarderLots", e);
		}
	}

	private void autoSauvegarderSocietes()
	{
		try
		{
			post("/autosave/societes", "{}");
		}
		catch (Exception e)
		{
			err("autoSauvegarderSocietes", e);
		}
	}

	// ── Utilitaires ─────────────────────────────────────────────

	private void mettreAJourDepuisReponseDual(String rep)
	{
		String jsonLots =
			JsonSerialiser.extraireBloc(rep, "\"lots\"");

		String jsonSoc =
			JsonSerialiser.extraireBloc(rep, "\"societes\"");

		if (jsonLots != null)
			this.lots =
				JsonSerialiser.deserialiserLots(jsonLots);

		if (jsonSoc != null)
			this.societes =
				JsonSerialiser.deserialiserSocietes(
					jsonSoc,
					this.lots);
	}

	private Societe getSocieteDeAce(Ace ace)
	{
		for (Societe s : societes)
			for (Ace a : s.getAces())
				if (a.getNom().equals(ace.getNom()))
					return s;

		return null;
	}

	private String get(String route) throws Exception
	{
		HttpRequest req =
			HttpRequest.newBuilder()
				.uri(URI.create(urlServeur + route))
				.GET()
				.build();

		HttpResponse<String> resp =
			http.send(
				req,
				HttpResponse.BodyHandlers.ofString(
					StandardCharsets.UTF_8));

		if (resp.statusCode() >= 400)
			throw new Exception(
				"HTTP "
				+ resp.statusCode()
				+ " : "
				+ resp.body());

		return resp.body();
	}

	private String post(String route, String json)
		throws Exception
	{
		HttpRequest req =
			HttpRequest.newBuilder()
				.uri(URI.create(urlServeur + route))
				.header(
					"Content-Type",
					"application/json")
				.POST(
					HttpRequest.BodyPublishers.ofString(
						json,
						StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> resp =
			http.send(
				req,
				HttpResponse.BodyHandlers.ofString(
					StandardCharsets.UTF_8));

		if (resp.statusCode() >= 400)
			throw new Exception(
				"HTTP "
				+ resp.statusCode()
				+ " : "
				+ resp.body());

		return resp.body();
	}

	private static String e(String s)
	{
		return JsonSerialiser.esc(s);
	}

	private void err(String methode, Exception e)
	{
		System.err.println(
			"[Client] "
			+ methode
			+ " : "
			+ e.getMessage());
	}

	// ── MAIN ────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		String ip =
			args.length > 0
				? args[0]
				: "localhost";

		SwingUtilities.invokeLater(
			() -> new ControleurClient(ip));
	}
}