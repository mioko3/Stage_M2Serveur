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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * ControleurClient — mode réseau.
 *
 * Implémente IControleur → compatible avec FenetrePrincipale et tous les panels.
 *
 * RESTRICTIONS v2 :
 *  - chargerDonnees() → BLOQUÉ (réservé au serveur)
 *  - nouveaux()       → BLOQUÉ (réservé au serveur)
 *  - sauvegarderDonnees() → autorisé (sauvegarde côté serveur)
 *
 * Ces actions sont désactivées dans le menu de FenetrePrincipale
 * lorsqu'on est en mode client (détecté par instanceof ControleurClient).
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

		new Thread(() ->
		{
			boolean ok = chargerDepuisServeur();
			SwingUtilities.invokeLater(() ->
			{
				if (ok)
					this.fenetre = new FenetrePrincipale(this);
				else
					new app.ihm.login.FenetreConnexionClient();
			});
		}).start();
		demarrerPolling();
	}

	private boolean chargerDepuisServeur()
	{
		try
		{
			System.out.println("[Client] GET /lots...");
			String jsonLots = get("/lots");
			System.out.println("[Client] /lots reçu (" + jsonLots.length() + " chars)");

			try {
				this.lots = JsonSerialiser.deserialiserLots(jsonLots);
			} catch (Exception ex) {
				System.err.println("[Client] CRASH deserialiserLots : " + ex);
				ex.printStackTrace(System.out);
				throw ex;
			}
			System.out.println("[Client] " + lots.size() + " lots désérialisés");

			System.out.println("[Client] GET /societes...");
			String jsonSoc = get("/societes");
			System.out.println("[Client] /societes reçu (" + jsonSoc.length() + " chars)");

			this.societes = JsonSerialiser.deserialiserSocietes(jsonSoc, this.lots);
			System.out.println("[Client] " + societes.size() + " sociétés désérialisées");

			System.out.println("[Client] ✓ Chargement OK → ouverture FenetrePrincipale");
			return true;
		}
		catch (Throwable e)
		{
			System.out.println("[Client] ERREUR chargerDepuisServeur : " + e.getClass().getName());
			System.out.println("[Client] Message : " + e.getMessage());
			e.printStackTrace(System.out);
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(null,
					"Impossible de charger les données :\n" + e.getClass().getName() + "\n" + e.getMessage(),
					"Erreur de connexion", JOptionPane.ERROR_MESSAGE)
			);
			return false;
		}
	}

	// ── IControleur : Données ─────────────────────────────────────────────

	@Override public ArrayList<Societe> getSocietes() { return societes; }
	@Override public ArrayList<Lot>     getLots()     { return lots;     }

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
			String c = "{\"numCDE\":"      + numCDE
				+ ",\"typologie\":"    + e(typologie)
				+ ",\"affaire\":"      + e(affaire)
				+ ",\"nbPieces\":"     + nbPieces
				+ ",\"cadence\":"      + cadence
				+ ",\"valeurVente\":"  + valeurVente
				+ ",\"statut\":"       + e(statut)
				+ ",\"statutEchant\":" + e(statutEchant)
				+ ",\"semaine\":"      + e(semaine)
				+ ",\"priorite\":"     + priorite
				+ ",\"lotACharge\":"   + e(lotACharge)
				+ ",\"emplacement\":"  + e(emplacement)
				+ ",\"estSousDouane\":" + sousDouane
				+ ",\"dateReception\":" + e(dateReception)
				+ ",\"datePaiement\":"  + e(datePaiement)
				+ ",\"commentaire\":"   + e(commentaire) + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", c));
			autoSauvegarde();
		}
		catch (Exception e) { err("ajouterLot(params)", e); }
	}

	@Override
	public void supprimerLot(Lot lot)
	{
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception e) { err("supprimerLot", e); }
	}

	@Override
	public void exportNewLot()
	{
		try { post("/nouveaux", "{}"); chargerDepuisServeur(); if (fenetre != null) fenetre.rafraichirTout(); }
		catch (Exception e) { err("exportNewLot", e); }
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
			String c = "{\"numCDE\":"      + lot.getNumCDE()
				+ ",\"typologie\":"    + e(typologie)
				+ ",\"affaire\":"      + e(affaire)
				+ ",\"nbPieces\":"     + nbPieces
				+ ",\"cadence\":"      + cadence
				+ ",\"valeurVente\":"  + valeurVente
				+ ",\"statut\":"       + e(statut)
				+ ",\"statutEchant\":" + e(statutEchant)
				+ ",\"semaine\":"      + e(semaine)
				+ ",\"priorite\":"     + priorite
				+ ",\"lotACharge\":"   + e(lotACharge)
				+ ",\"emplacement\":"  + e(emplacement)
				+ ",\"estSousDouane\":" + sousDouane
				+ ",\"dateReception\":" + e(dateReception)
				+ ",\"datePaiement\":"  + e(datePaiement)
				+ ",\"commentaire\":"   + e(commentaire) + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", c));
			autoSauvegarde();
		}
		catch (Exception e) { err("modifierLot", e); }
	}

	public void modifierLotMethodeDistribution(Lot lot, String methode, String lotACharge)
	{
		try
		{
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"methode\":"    + e(methode)
				+ ",\"lotACharge\":" + e(lotACharge) + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", c));
			autoSauvegarde();
		}
		catch (Exception ex) { err("modifierLotMethodeDistribution", ex); }
	}

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
							  boolean sortieEtiq, boolean tri, boolean finit)
	{
		try
		{
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"preTri\":"     + preTri
				+ ",\"surPiste\":"   + surPiste
				+ ",\"sortieEtiq\":" + sortieEtiq
				+ ",\"tri\":"        + tri
				+ ",\"finit\":"      + finit + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", c));
			autoSauvegarde();
		}
		catch (Exception e) { err("modifierPhase", e); }
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

	// ── IControleur : Affectation ─────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		try
		{
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"societe\":" + e(societe.getNom())
				+ ",\"ace\":"     + e(ace.getNom()) + "}";
			majDual(post("/lots/affecter", c));
			autoSauvegarde();
			return true;
		}
		catch (Exception ex) { err("affecterLot", ex); return false; }
	}

	@Override
	public void desaffecterLot(Lot lot)
	{
		try { majDual(post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception e) { err("desaffecterLot", e); }
	}

	// ── IControleur : Sociétés ────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif)
	{
		try
		{
			String c = "{\"nom\":"           + e(soc.getNom())
				+ ",\"nouveauNom\":"   + e(nom)
				+ ",\"ce\":"           + e(ce)
				+ ",\"totalHeuresCE\":" + totalHeuresCE
				+ ",\"effectif\":"     + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/societes/modifier", c), this.lots);
		}
		catch (Exception e) { err("modifierSociete", e); }
	}

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces)
	{
		try
		{
			StringBuilder sb = new StringBuilder("{\"societe\":" + e(soc.getNom()) + ",\"aces\":[");
			for (int i = 0; i < nouvellesAces.size(); i++)
			{
				Ace a = nouvellesAces.get(i);
				if (i > 0) sb.append(",");
				sb.append("{\"nom\":").append(e(a.getNom()))
				  .append(",\"nbPers\":").append(a.getNbPers())
				  .append(",\"effectifActuel\":").append(a.getEffectifActuel()).append("}");
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/aces/mettreajour", sb.toString()), this.lots);
			return true;
		}
		catch (Exception e) { err("mettreAJourAces", e); return false; }
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		// Le client sélectionne le fichier Excel localement et envoie les heures parsées au serveur
		String xlsx = demanderFichierExcel("Sélectionner le fichier des heures ACE");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Nouvelle heure", JOptionPane.INFORMATION_MESSAGE); return; }
		try
		{
			// Lire les heures localement puis envoyer au serveur
			java.util.Map<String, Integer> heuresParSoc = app.metier.collecte.ExcelReader.lireHeuresSocietes(xlsx, semaine);
			if (heuresParSoc == null || heuresParSoc.isEmpty()) { JOptionPane.showMessageDialog(null, "Aucune donnée dans le fichier.", "Nouvelle heure", JOptionPane.WARNING_MESSAGE); return; }

			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heuresParSoc.entrySet())
			{
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey())).append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");
			String rep = post("/nouvelleheure", sb.toString());
			this.societes = JsonSerialiser.deserialiserSocietes(rep, this.lots);
			autoSauvegarde();
			JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception e) { JOptionPane.showMessageDialog(null, "Erreur :\n" + e.getMessage(), "Nouvelle heure", JOptionPane.ERROR_MESSAGE); }
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

	/**
	 * Sauvegarde autorisée : le serveur l'exécute côté serveur.
	 */
	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try { post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}"); }
		catch (Exception e) { err("sauvegarderDonnees", e); }
	}

	/**
	 * ── BLOQUÉ CÔTÉ CLIENT ────────────────────────────────────────────────
	 * Le chargement de semaine est réservé au serveur (FenetreServeur).
	 * Cette méthode affiche un message d'erreur explicatif.
	 */
	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		SwingUtilities.invokeLater(() ->
			JOptionPane.showMessageDialog(null,
				"⛔  Action réservée au serveur.\n\n"
					+ "Le chargement d'une semaine ne peut être fait que\n"
					+ "depuis le panneau de contrôle du serveur.\n\n"
					+ "Demandez au responsable serveur de changer la semaine active.",
				"Action non autorisée", JOptionPane.WARNING_MESSAGE)
		);
	}

	/**
	 * ── BLOQUÉ CÔTÉ CLIENT ────────────────────────────────────────────────
	 * La création d'une nouvelle semaine est réservée au serveur.
	 */
	@Override
	public void nouveaux()
	{
		SwingUtilities.invokeLater(() ->
			JOptionPane.showMessageDialog(null,
				"⛔  Action réservée au serveur.\n\n"
					+ "La création d'une nouvelle semaine ne peut être faite que\n"
					+ "depuis le panneau de contrôle du serveur.\n\n"
					+ "Demandez au responsable serveur de charger la nouvelle semaine.",
				"Action non autorisée", JOptionPane.WARNING_MESSAGE)
		);
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

	private String demanderFichierExcel(String titre)
	{
		javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
		fc.setDialogTitle(titre);
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		return fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath() : null;
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

	// ── Polling ───────────────────────────────────────────────────────────

	private static final int POLLING_INTERVAL_MS = 3000;

	private void demarrerPolling()
	{
		Thread t = new Thread(() ->
		{
			while (true)
			{
				try
				{
					Thread.sleep(POLLING_INTERVAL_MS);
					boolean modif = rafraichirSiModif();
					if (modif && fenetre != null)
						SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());
				}
				catch (InterruptedException e) { break; }
				catch (Exception ignored) {}
			}
		});
		t.setDaemon(true);
		t.setName("polling-serveur");
		t.start();
	}

	private volatile String versionLocale = "";

	private boolean rafraichirSiModif()
	{
		try
		{
			String rep = get("/version");
			String v   = JsonSerialiser.extraireString(rep, "v");
			if (v != null && !v.equals(versionLocale))
			{
				versionLocale = v;
				chargerDepuisServeur();
				return true;
			}
		}
		catch (Exception ignored) {}
		return false;
	}

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		if (args.length > 0)
			SwingUtilities.invokeLater(() -> new ControleurClient(args[0]));
		else
			SwingUtilities.invokeLater(app.ihm.login.FenetreConnexionClient::new);
	}
}