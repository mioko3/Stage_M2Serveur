package app;

import app.ihm.FenetrePrincipale;
import app.ihm.login.FenetreLogin;
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
 * ControleurClient — mode réseau.
 *
 * Étend Controleur pour que tous les panels IHM (PanelAffectation, etc.)
 * compilent sans modification. Toutes les méthodes sont redéfinies pour
 * passer par HTTP vers ServeurHTTP.
 *
 * Lancer : java -cp ... app.ControleurClient [IP_SERVEUR]
 */
public class ControleurClient extends Controleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	private ArrayList<Lot>     lots;
	private ArrayList<Societe> societes;

	// ── Constructeur ──────────────────────────────────────────────────────

	public ControleurClient(String ipServeur)
	{
		// On appelle super() mais on ne veut PAS qu'il lance FenetreLogin
		// → on va ouvrir FenetrePrincipale directement après chargement
		this.urlServeur = "http://" + ipServeur + ":8080";
		this.http       = HttpClient.newHttpClient();
		this.lots       = new ArrayList<>();
		this.societes   = new ArrayList<>();

		System.out.println("[Client] Connexion → " + urlServeur);

		SwingUtilities.invokeLater(() ->
		{
			chargerDepuisServeur();
			new FenetreLogin(this);
		});
	}

	// Constructeur privé pour éviter l'appel à super() qui lance FenetreLogin
	// On override le comportement
	private void chargerDepuisServeur()
	{
		try
		{
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			System.out.println("[Client] " + lots.size() + " lots, " + societes.size() + " sociétés.");
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null,
				"Impossible de contacter le serveur " + urlServeur + "\n" + e.getMessage(),
				"Erreur connexion", JOptionPane.ERROR_MESSAGE);
		}
	}

	public void rechargerDepuisServeur()
	{
		try
		{
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			if (fenetre != null)
				SwingUtilities.invokeLater(fenetre::rafraichirTout);
		}
		catch (Exception e) { err("recharger", e); }
	}

	// ── Données ───────────────────────────────────────────────────────────

	@Override public ArrayList<Societe> getSocietes() { return societes; }
	@Override public ArrayList<Lot>     getLots()     { return lots;     }

	// ── Lots ─────────────────────────────────────────────────────────────

	@Override
	public void ajouterLot(Lot lot)
	{
		try
		{
			this.lots = JsonSerialiser.deserialiserLots(
				post("/lots/ajouter", JsonSerialiser.serialiserLotSeul(lot)));
			autoSauvegarder();
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
				+ "\"numCDE\":"        + numCDE           + ",\"typologie\":"     + e(typologie)    + ","
				+ "\"affaire\":"       + e(affaire)       + ",\"nbPieces\":"      + nbPieces         + ","
				+ "\"cadence\":"       + cadence          + ",\"valeurVente\":"   + valeurVente      + ","
				+ "\"statut\":"        + e(statut)        + ",\"statutEchant\":"  + e(statutEchant)  + ","
				+ "\"semaine\":"       + e(semaine)       + ",\"priorite\":"      + priorite         + ","
				+ "\"lotACharge\":"    + e(lotACharge)    + ",\"emplacement\":"   + e(emplacement)   + ","
				+ "\"estSousDouane\":" + sousDouane       + ",\"dateReception\":" + e(dateReception) + ","
				+ "\"datePaiement\":"  + e(datePaiement)  + ",\"commentaire\":"   + e(commentaire)
				+ "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", corps));
			autoSauvegarder();
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
			autoSauvegarder();
		}
		catch (Exception e) { err("supprimerLot", e); }
	}

	@Override
	public void exportNewLot()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le fichier des nouveaux lots");
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel", "xlsx", "xlsm"));
		if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
		try
		{
			ArrayList<Lot> nouveaux = app.metier.collecte.ExcelReader.lireLots(
				fc.getSelectedFile().getAbsolutePath());
			int n = 0;
			for (Lot l : nouveaux)
			{
				boolean existe = false;
				for (Lot ex : lots) if (ex.getNumCDE() == l.getNumCDE()) { existe = true; break; }
				if (!existe) { ajouterLot(l); n++; }
			}
			JOptionPane.showMessageDialog(null, n + " lot(s) importé(s).", "Import", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception ex) { JOptionPane.showMessageDialog(null, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE); }
	}

	// ── Affectation ───────────────────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		try
		{
			String corps = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nomSociete\":" + e(societe.getNom())
				+ ",\"nomAce\":"     + e(ace.getNom()) + "}";
			majDual(post("/lots/affecter", corps));
			autoSauvegarder();
			return true;
		}
		catch (Exception e) { err("affecterLot", e); return false; }
	}

	@Override
	public void desaffecterLot(Lot lot)
	{
		try
		{
			majDual(post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			autoSauvegarder();
		}
		catch (Exception e) { err("desaffecterLot", e); }
	}

	// ── Modification lots ─────────────────────────────────────────────────

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
			String corps = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"typologie\":"     + e(typologie)    + ",\"affaire\":"       + e(affaire)
				+ ",\"nbPieces\":"      + nbPieces         + ",\"cadence\":"       + cadence
				+ ",\"valeurVente\":"   + valeurVente      + ",\"statut\":"        + e(statut)
				+ ",\"statutEchant\":"  + e(statutEchant)  + ",\"semaine\":"       + e(semaine)
				+ ",\"priorite\":"      + priorite         + ",\"lotACharge\":"    + e(lotACharge)
				+ ",\"emplacement\":"   + e(emplacement)   + ",\"estSousDouane\":" + sousDouane
				+ ",\"dateReception\":" + e(dateReception) + ",\"datePaiement\":"  + e(datePaiement)
				+ ",\"commentaire\":"   + e(commentaire)   + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", corps));
			autoSauvegarder();
		}
		catch (Exception e) { err("modifierLot", e); }
	}

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{
		try
		{
			String corps = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"preTri\":"     + preTri    + ",\"surPiste\":"   + surPiste
				+ ",\"sortieEtiq\":" + sortieEtiq + ",\"tri\":"        + tri
				+ ",\"finit\":"      + finit      + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", corps));
			autoSauvegarder();
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
			autoSauvegarder();
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

	// ── Sociétés ──────────────────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce,
	                            int totalHeuresCE, int effectif)
	{
		try
		{
			String corps = "{\"nomActuel\":" + e(soc.getNom())
				+ ",\"nom\":" + e(nom) + ",\"ce\":" + e(ce)
				+ ",\"totalHeuresCE\":" + totalHeuresCE + ",\"effectif\":" + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/societes/modifier", corps), this.lots);
			autoSauvegarder();
		}
		catch (Exception e) { err("modifierSociete", e); }
	}

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvelles)
	{
		try
		{
			StringBuilder ab = new StringBuilder("[");
			for (int i = 0; i < nouvelles.size(); i++) {
				Ace a = nouvelles.get(i);
				if (i > 0) ab.append(",");
				ab.append("{\"nom\":").append(e(a.getNom()))
				  .append(",\"nbPers\":").append(a.getNbPers())
				  .append(",\"effectif\":").append(a.getEffectifActuel()).append("}");
			}
			ab.append("]");
			String corps = "{\"societe\":" + e(soc.getNom()) + ",\"aces\":" + ab + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/aces/mettreajour", corps), this.lots);
			autoSauvegarder();
			return true;
		}
		catch (Exception e) { err("mettreAJourAces", e); return false; }
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		JOptionPane.showMessageDialog(null,
			"En mode client, les heures doivent être mises à jour directement sur le serveur.",
			"Information", JOptionPane.INFORMATION_MESSAGE);
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

	// ── Suivi prod ────────────────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		try
		{
			String corps = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nbPieceEtiq\":" + nbPieceEtiq
				+ ",\"nbPieceRepart\":" + nbPieceRepart + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", corps));
			autoSauvegarder();
		}
		catch (Exception e) { err("mettreAJourSuiviProd", e); }
	}

	// ── Recherche (sur cache local) ───────────────────────────────────────

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
		for (Societe s : societes) tout.addAll(s.getAces());
		return tout;
	}

	// ── Fiche de route ────────────────────────────────────────────────────

	@Override
	public FicheRoute genererFicheRoute(Societe societe)
	{
		// La fiche est calculée localement depuis le cache
		return new app.metier.ficheroute.FicheRoute(societe);
	}

	// ── Sauvegarde / Chargement ───────────────────────────────────────────

	public void lancerApp(String login, boolean utiliserExcel)
	{
		super.lancerApp(login, utiliserExcel);
		this.fenetre = super.fenetre; 
	}

	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try { post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}"); }
		catch (Exception e) { err("sauvegarderDonnees", e); }
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		try { majDual(post("/charger", "{\"chemin\":" + e(chemin) + "}")); }
		catch (Exception e) { throw new IOException(e.getMessage()); }
	}

	@Override
	public void nouveaux()
	{
		try
		{
			post("/nouveaux", "{}");
			rechargerDepuisServeur();
		}
		catch (Exception e) { err("nouveaux", e); }
	}

	@Override
	public void autoSauvegarde()
	{ autoSauvegarder(); }

	private void autoSauvegarder()
	{
		try { post("/autosave/lots",     "{}"); } catch (Exception ignored) {}
		try { post("/autosave/societes", "{}"); } catch (Exception ignored) {}
	}

	// ── Utilitaires HTTP ──────────────────────────────────────────────────

	private void majDual(String rep)
	{
		String jL = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jS = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jL != null) this.lots     = JsonSerialiser.deserialiserLots(jL);
		if (jS != null) this.societes = JsonSerialiser.deserialiserSocietes(jS, this.lots);
	}

	private String get(String route) throws Exception
	{
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
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
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	private static String e(String s) { return JsonSerialiser.esc(s); }

	private void err(String m, Exception e)
	{ System.err.println("[Client] " + m + " : " + (e != null ? e.getMessage() : "null")); }

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		String ip = args.length > 0 ? args[0] : "localhost";
		System.out.println("[Client] IP serveur : " + ip);
		SwingUtilities.invokeLater(() -> new ControleurClient(ip));
	}
}