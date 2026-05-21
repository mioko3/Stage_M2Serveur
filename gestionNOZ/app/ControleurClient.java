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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ControleurClient — mode réseau.
 *
 * Implémente IControleur → compatible avec FenetrePrincipale et tous les panels.
 * S'ouvre directement sur FenetrePrincipale, sans passer par FenetreLogin.
 *
 * Lancement :
 *   java -cp ... app.ControleurClient [IP_SERVEUR]
 *   java -cp ... app.ihm.login.FenetreConnexionClient   ← GUI pour choisir l'IP
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	private ArrayList<Lot>     lots     = new ArrayList<>();
	private ArrayList<Societe> societes = new ArrayList<>();

	// ── Constructeur ──────────────────────────────────────────────────────

	public ControleurClient(String ipServeur)
	{
		this.urlServeur = "http://" + ipServeur + ":8080";
		this.http       = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

		System.out.println("[Client] Connexion → " + urlServeur);

		SwingUtilities.invokeLater(() ->
		{
			boolean ok = chargerDepuisServeur();
			if (ok)
			{
				// Connexion réussie → ouvrir l'application
				this.fenetre = new FenetrePrincipale(this);
			}
			else
			{
				// Échec → retourner à l'écran de connexion
				new app.ihm.login.FenetreConnexionClient();
			}
		});
	}

	/**
	 * @return true si le chargement a réussi, false en cas d'erreur réseau
	 */
	private boolean chargerDepuisServeur()
	{
		try
		{
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			System.out.println("[Client] " + lots.size() + " lots, " + societes.size() + " sociétés.");
			return true;
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null,
				"Impossible de contacter le serveur :\n" + urlServeur
				+ "\n\n" + e.getMessage()
				+ "\n\nVérifiez que ServeurHTTP est démarré et que l'IP est correcte.",
				"Erreur de connexion", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	// ── IControleur : Données ─────────────────────────────────────────────

	@Override public ArrayList<Societe> getSocietes()          { return societes;          }
	@Override public ArrayList<Lot>     getLots()              { return lots;              }

	// ── IControleur : Lots ────────────────────────────────────────────────

	@Override
	public void ajouterLot(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", JsonSerialiser.serialiserLotSeul(lot))); autoSauvegarde(); }
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
			String c = "{\"numCDE\":" + numCDE
				+ ",\"typologie\":"    + e(typologie)    + ",\"affaire\":"       + e(affaire)
				+ ",\"nbPieces\":"     + nbPieces         + ",\"cadence\":"       + cadence
				+ ",\"valeurVente\":"  + valeurVente      + ",\"statut\":"        + e(statut)
				+ ",\"statutEchant\":" + e(statutEchant)  + ",\"semaine\":"       + e(semaine)
				+ ",\"priorite\":"     + priorite         + ",\"lotACharge\":"    + e(lotACharge)
				+ ",\"emplacement\":"  + e(emplacement)   + ",\"estSousDouane\":" + sousDouane
				+ ",\"dateReception\":" + e(dateReception) + ",\"datePaiement\":"  + e(datePaiement)
				+ ",\"commentaire\":"  + e(commentaire)   + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", c));
			autoSauvegarde();
		}
		catch (Exception e) { err("ajouterLot", e); }
	}

	@Override
	public void supprimerLot(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception e) { err("supprimerLot", e); }
	}

	public void sauvegarderLots() { autoSauvegarde(); }

	@Override
	public void exportNewLot()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Fichier des nouveaux lots");
		fc.setFileFilter(new FileNameExtensionFilter("Excel", "xlsx", "xlsm"));
		if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
		try
		{
			ArrayList<Lot> nouveaux = app.metier.collecte.ExcelReader.lireLots(fc.getSelectedFile().getAbsolutePath());
			int n = 0;
			for (Lot l : nouveaux) {
				boolean deja = lots.stream().anyMatch(x -> x.getNumCDE() == l.getNumCDE());
				if (!deja) { ajouterLot(l); n++; }
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
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nomSociete\":" + e(societe.getNom())
				+ ",\"nomAce\":"     + e(ace.getNom()) + "}";
			majDual(post("/lots/affecter", c));
			autoSauvegarde();
			return true;
		} catch (Exception e) { err("affecterLot", e); return false; }
	}

	@Override
	public void desaffecterLot(Lot lot)
	{
		try { majDual(post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
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
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"typologie\":"    + e(typologie)    + ",\"affaire\":"       + e(affaire)
				+ ",\"nbPieces\":"     + nbPieces         + ",\"cadence\":"       + cadence
				+ ",\"valeurVente\":"  + valeurVente      + ",\"statut\":"        + e(statut)
				+ ",\"statutEchant\":" + e(statutEchant)  + ",\"semaine\":"       + e(semaine)
				+ ",\"priorite\":"     + priorite         + ",\"lotACharge\":"    + e(lotACharge)
				+ ",\"emplacement\":"  + e(emplacement)   + ",\"estSousDouane\":" + sousDouane
				+ ",\"dateReception\":" + e(dateReception) + ",\"datePaiement\":"  + e(datePaiement)
				+ ",\"commentaire\":"  + e(commentaire)   + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", c));
			autoSauvegarde();
		} catch (Exception e) { err("modifierLot", e); }
	}

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste, boolean sortieEtiq, boolean tri, boolean finit)
	{
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"preTri\":"     + preTri    + ",\"surPiste\":"   + surPiste
				+ ",\"sortieEtiq\":" + sortieEtiq + ",\"tri\":"        + tri
				+ ",\"finit\":"      + finit      + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", c));
			autoSauvegarde();
		} catch (Exception e) { err("modifierPhase", e); }
	}

	@Override
	public void marquerLotTermine(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/terminer", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception e) { err("marquerLotTermine", e); }
	}

	@Override
	public void commencerLot(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/commencer", "{\"numCDE\":" + lot.getNumCDE() + "}")); }
		catch (Exception e) { err("commencerLot", e); }
	}

	@Override
	public void annulerLot(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/annuler", "{\"numCDE\":" + lot.getNumCDE() + "}")); }
		catch (Exception e) { err("annulerLot", e); }
	}

	// ── Sociétés / ACE ────────────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif)
	{
		try {
			String c = "{\"nomActuel\":" + e(soc.getNom()) + ",\"nom\":" + e(nom) + ",\"ce\":" + e(ce)
				+ ",\"totalHeuresCE\":" + totalHeuresCE + ",\"effectif\":" + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(post("/societes/modifier", c), this.lots);
			autoSauvegarde();
		} catch (Exception e) { err("modifierSociete", e); }
	}

	public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
	{
		Societe soc = getSocieteDeAce(ace);
		if (soc == null) return;
		try {
			String c = "{\"societe\":" + e(soc.getNom()) + ",\"nomActuel\":" + e(ace.getNom())
				+ ",\"nom\":" + e(nom) + ",\"nbPers\":" + nbPers + ",\"effectif\":" + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(post("/aces/modifier", c), this.lots);
			autoSauvegarde();
		} catch (Exception e) { err("modifierAce", e); }
	}

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvelles)
	{
		try {
			StringBuilder ab = new StringBuilder("[");
			for (int i = 0; i < nouvelles.size(); i++) {
				Ace a = nouvelles.get(i);
				if (i > 0) ab.append(",");
				ab.append("{\"nom\":").append(e(a.getNom()))
				  .append(",\"nbPers\":").append(a.getNbPers())
				  .append(",\"effectif\":").append(a.getEffectifActuel()).append("}");
			}
			ab.append("]");
			String c = "{\"societe\":" + e(soc.getNom()) + ",\"aces\":" + ab + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(post("/aces/mettreajour", c), this.lots);
			autoSauvegarde();
			return true;
		} catch (Exception e) { err("mettreAJourAces", e); return false; }
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Fichier des heures ACE");
		fc.setFileFilter(new FileNameExtensionFilter("Excel", "xlsx", "xlsm"));
		if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
		try {
			java.util.Map<String, Integer> heures = app.metier.collecte.ExcelReader.lireHeuresSocietes(
				fc.getSelectedFile().getAbsolutePath(), semaine);
			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heures.entrySet()) {
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey()))
				  .append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(post("/nouvelleheure", sb.toString()), this.lots);
			autoSauvegarde();
			JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		} catch (Exception e) { JOptionPane.showMessageDialog(null, "Erreur : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE); }
	}

	@Override
	public void semaineSup()
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/semainesup", "{}")); }
		catch (Exception e) { err("semaineSup", e); }
	}

	// ── Suivi prod ────────────────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nbPieceEtiq\":" + nbPieceEtiq + ",\"nbPieceRepart\":" + nbPieceRepart + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", c));
			autoSauvegarde();
		} catch (Exception e) { err("mettreAJourSuiviProd", e); }
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
	{ return new app.metier.ficheroute.FicheRoute(societe); }

	// ── Sauvegarde / Chargement ───────────────────────────────────────────

	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try { post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}"); }
		catch (Exception e) { err("sauvegarderDonnees", e); }
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		try { majDual(post("/charger", "{\"chemin\":" + e(chemin) + "}")); if (fenetre != null) fenetre.rafraichirTout(); }
		catch (Exception e) { throw new IOException(e.getMessage()); }
	}

	@Override
	public void nouveaux()
	{
		try { post("/nouveaux", "{}"); chargerDepuisServeur(); if (fenetre != null) fenetre.rafraichirTout(); }
		catch (Exception e) { err("nouveaux", e); }
	}

	@Override
	public void autoSauvegarde()
	{
		try { post("/autosave/lots",     "{}"); } catch (Exception ignored) {}
		try { post("/autosave/societes", "{}"); } catch (Exception ignored) {}
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private void majDual(String rep)
	{
		String jL = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jS = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jL != null) this.lots     = JsonSerialiser.deserialiserLots(jL);
		if (jS != null) this.societes = JsonSerialiser.deserialiserSocietes(jS, this.lots);
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
			.timeout(Duration.ofSeconds(15))
			.GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	private String post(String route, String json) throws Exception
	{
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	private static String e(String s) { return JsonSerialiser.esc(s); }

	private void err(String m, Exception e)
	{
		System.err.println("[Client] " + m + " : " + (e != null ? e.getMessage() : "null"));
		if (e != null) e.printStackTrace();
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		// Sans argument → fenêtre de connexion pour entrer l'IP
		SwingUtilities.invokeLater(app.ihm.login.FenetreConnexionClient::new);
	}
}
