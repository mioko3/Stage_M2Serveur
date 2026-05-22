package app;

import app.ihm.FenetrePrincipale;
import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
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
 * Nouvelles fonctionnalités v3 :
 *   - identifiant + accesPAM : contrôle les permissions (affectation, désync)
 *   - estHeureSup synchronisé depuis /version
 *   - mode désynchronisé : le polling s'arrête, actions locales disponibles
 *
 * Lancement :
 *   java -cp ... app.ControleurClient [IP]
 *   java -cp ... app.ihm.login.FenetreConnexionClient  ← GUI
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	// ── Identité ──────────────────────────────────────────────────────────
	private final String  identifiant;   // ex: "PAM" ou "EUP"
	private final boolean accesPAM;      // true = accès complet

	// ── Données ───────────────────────────────────────────────────────────
	private ArrayList<Lot>     lots     = new ArrayList<>();
	private ArrayList<Societe> societes = new ArrayList<>();

	// ── Mode désynchronisé ────────────────────────────────────────────────
	private volatile boolean desynchronise = false;
	private DonneesSauvegarder savLocal;   // pour sauvegardes locales en mode désync

	// ── Polling ───────────────────────────────────────────────────────────
	private volatile String versionLocale    = "";
	private volatile boolean heureSup        = false;
	private static final int POLLING_MS      = 3000;

	// ── Constructeur principal ─────────────────────────────────────────────

	public ControleurClient(String ipServeur, String identifiant, boolean accesPAM)
	{
		this.urlServeur   = "http://" + ipServeur + ":8080";
		this.identifiant  = identifiant;
		this.accesPAM     = accesPAM;
		this.savLocal     = new DonneesSauvegarder();

		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

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

	/** Constructeur legacy (sans identifiant) — rétrocompatibilité. */
	public ControleurClient(String ipServeur)
	{
		this(ipServeur, "PAM", true);
	}

	// ── Getters identité ──────────────────────────────────────────────────

	public String  getIdentifiant() { return identifiant; }
	public boolean isAccesPAM()     { return accesPAM;    }
	public boolean isDesynchronise(){ return desynchronise; }

	// ── Mode désynchronisé ────────────────────────────────────────────────

	/**
	 * Se désynchronise du serveur.
	 * Le polling s'arrête, les actions locales (charger/sauvegarder/nouveaux)
	 * sont débloquées pour permettre de préparer des semaines futures.
	 * Réservé au compte PAM.
	 */
	public void seDesynchroniser()
	{
		if (!accesPAM) return;
		desynchronise = true;
		if (fenetre != null) SwingUtilities.invokeLater(() -> fenetre.majBandeauEtat());
	}

	/**
	 * Se resynchronise avec le serveur.
	 * Recharge les données serveur et reprend le polling.
	 */
	public void seResynchroniser()
	{
		desynchronise = false;
		new Thread(() ->
		{
			chargerDepuisServeur();
			SwingUtilities.invokeLater(() ->
			{
				if (fenetre != null)
				{
					fenetre.majBandeauEtat();
					fenetre.rafraichirTout();
				}
			});
		}).start();
	}

	// ── Chargement depuis serveur ──────────────────────────────────────────

	private boolean chargerDepuisServeur()
	{
		try
		{
			String jsonLots = get("/lots");
			this.lots = JsonSerialiser.deserialiserLots(jsonLots);

			String jsonSoc = get("/societes");
			this.societes  = JsonSerialiser.deserialiserSocietes(jsonSoc, this.lots);

			// Synchroniser l'état heure sup depuis /version
			rafraichirEtatServeur();

			return true;
		}
		catch (Throwable e)
		{
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(null,
					"Impossible de charger les données :\n" + e.getMessage(),
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
		try
		{
			post("/nouveaux", "{}");
			chargerDepuisServeur(); if (fenetre != null) fenetre.rafraichirTout();
		}
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
			String c = "{\"nom\":"          + e(soc.getNom())
				+ ",\"nouveauNom\":"  + e(nom)
				+ ",\"ce\":"          + e(ce)
				+ ",\"totalHeuresCE\":" + totalHeuresCE
				+ ",\"effectif\":"    + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(post("/societes/modifier", c), this.lots);
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
		String xlsx = demanderFichierExcel("Sélectionner le fichier des heures ACE");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Nouvelle heure", JOptionPane.INFORMATION_MESSAGE); return; }
		try
		{
			java.util.Map<String, Integer> heuresParSoc = ExcelReader.lireHeuresSocietes(xlsx, semaine);
			if (heuresParSoc == null || heuresParSoc.isEmpty()) { JOptionPane.showMessageDialog(null, "Aucune donnée.", "Nouvelle heure", JOptionPane.WARNING_MESSAGE); return; }

			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heuresParSoc.entrySet())
			{
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey())).append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(post("/nouvelleheure", sb.toString()), this.lots);
			autoSauvegarde();
			JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		}
		catch (Exception e) { JOptionPane.showMessageDialog(null, "Erreur :\n" + e.getMessage(), "Nouvelle heure", JOptionPane.ERROR_MESSAGE); }
	}

	/**
	 * Heures sup — RÉSERVÉ AU SERVEUR.
	 * Le client ne peut pas déclencher cela lui-même.
	 * L'état est synchronisé via /version.
	 */
	@Override
	public void semaineSup()
	{
		if (!accesPAM)
		{
			JOptionPane.showMessageDialog(null,
				"⛔  Action réservée au serveur.\nDemandez au responsable de gérer les heures supplémentaires.",
				"Action non autorisée", JOptionPane.WARNING_MESSAGE);
			return;
		}
		// PAM peut voir le message mais l'action reste serveur
		JOptionPane.showMessageDialog(null,
			"Les heures supplémentaires sont gérées depuis le panneau de contrôle du serveur.",
			"Information", JOptionPane.INFORMATION_MESSAGE);
	}

	// ── Suivi prod ────────────────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		try
		{
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nbPieceEtiq\":" + nbPieceEtiq
				+ ",\"nbPieceRepart\":" + nbPieceRepart + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", c));
			autoSauvegarde();
		}
		catch (Exception e) { err("mettreAJourSuiviProd", e); }
	}

	// ── Recherche ─────────────────────────────────────────────────────────

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
		if (desynchronise)
		{
			// En mode désync, sauvegarde locale
			try
			{
				String dossier = cheminDossier + "/S" + semaine;
				java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
				savLocal.sauvegarderLots    (lots,     dossier + "/lots.json");
				savLocal.sauvegarderSocietes(societes, lots, dossier + "/societes.json");
				JOptionPane.showMessageDialog(null,
					"Sauvegarde locale effectuée dans S" + semaine,
					"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
			}
			catch (Exception e) { err("sauvegarderDonnees (local)", e); }
		}
		else
		{
			try { post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}"); }
			catch (Exception e) { err("sauvegarderDonnees", e); }
		}
	}

	/**
	 * En mode désynchronisé : charge un dossier local.
	 * En mode synchronisé : BLOQUÉ (réservé au serveur).
	 */
	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		if (desynchronise)
		{
			// Mode désync : chargement local
			savLocal.charger(creerPlanningLocal(), chemin);
			// On recharge les listes depuis le PlanningGlobal local
			// En pratique on utilise directement JsonSerialiser
			try
			{
				this.lots     = JsonSerialiser.deserialiserLots(
					new String(java.nio.file.Files.readAllBytes(
						java.nio.file.Paths.get(chemin + "/lots.json")),
						StandardCharsets.UTF_8));
				this.societes = JsonSerialiser.deserialiserSocietes(
					new String(java.nio.file.Files.readAllBytes(
						java.nio.file.Paths.get(chemin + "/societes.json")),
						StandardCharsets.UTF_8), this.lots);
				if (fenetre != null) SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());
			}
			catch (Exception ex) { throw new IOException(ex.getMessage()); }
		}
		else
		{
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(null,
					"⛔  Action réservée au serveur.\n\n"
						+ "Pour préparer des semaines futures, utilisez le bouton\n"
						+ "\"Se désynchroniser\" puis chargez vos données.",
					"Action non autorisée", JOptionPane.WARNING_MESSAGE)
			);
		}
	}

	/**
	 * En mode désynchronisé : importe depuis Excel localement.
	 * En mode synchronisé : BLOQUÉ.
	 */
	@Override
	public void nouveaux()
	{
		if (desynchronise)
		{
			String xlsx = demanderFichierExcel("Sélectionner le fichier des lots (XLSX / XLSM)");
			if (xlsx == null) return;
			try
			{
				ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsx);
				int semaine = 0;
				if (!tempLots.isEmpty())
				{
					String sem = tempLots.get(0).getSemaine();
					try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
					catch (NumberFormatException ignored) {}
				}
				String xlsxH = demanderFichierExcel("Sélectionner le fichier des heures ACE (ou annuler)");
				if (xlsxH == null) xlsxH = xlsx;

				app.metier.PlanningGlobal pg = new app.metier.PlanningGlobal();
				pg.chargerDepuisExcel(xlsx, "app/data/pastouche/societes.json", semaine, xlsxH);
				this.lots     = pg.getLots();
				this.societes = pg.getSocietes();
				if (fenetre != null) SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());
			}
			catch (Exception e) { JOptionPane.showMessageDialog(null, "Erreur :\n" + e.getMessage(), "Import", JOptionPane.ERROR_MESSAGE); }
		}
		else
		{
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(null,
					"⛔  Action réservée au serveur.\n\n"
						+ "Pour préparer des semaines futures, utilisez le bouton\n"
						+ "\"Se désynchroniser\" puis importez votre fichier Excel.",
					"Action non autorisée", JOptionPane.WARNING_MESSAGE)
			);
		}
	}

	@Override
	public void autoSauvegarde()
	{
		if (desynchronise) return; // pas d'autosave vers le serveur en mode désync
		try { post("/autosave/lots",     "{}"); } catch (Exception ignored) {}
		try { post("/autosave/societes", "{}"); } catch (Exception ignored) {}
	}

	// ── Polling ───────────────────────────────────────────────────────────

	private void demarrerPolling()
	{
		Thread t = new Thread(() ->
		{
			while (true)
			{
				try
				{
					Thread.sleep(POLLING_MS);
					if (desynchronise) continue; // pause le poll en mode désync
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

	private boolean rafraichirSiModif()
	{
		try
		{
			String rep = get("/version");
			String v   = JsonSerialiser.extraireString(rep, "v");
			// Synchroniser l'état heureSup depuis la réponse version
			boolean hs = JsonSerialiser.extraireBool(rep, "heureSup");
			if (hs != heureSup)
			{
				heureSup = hs;
				PlanningGlobal.estHeureSup = hs;
			}
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

	private void rafraichirEtatServeur()
	{
		try
		{
			String rep = get("/version");
			boolean hs = JsonSerialiser.extraireBool(rep, "heureSup");
			heureSup = hs;
			PlanningGlobal.estHeureSup = hs;
			versionLocale = JsonSerialiser.extraireString(rep, "v");
		}
		catch (Exception ignored) {}
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private void majDual(String rep)
	{
		String jL = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jS = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jL != null) this.lots     = JsonSerialiser.deserialiserLots(jL);
		if (jS != null) this.societes = JsonSerialiser.deserialiserSocietes(jS, this.lots);
	}

	private app.metier.PlanningGlobal creerPlanningLocal()
	{
		app.metier.PlanningGlobal pg = new app.metier.PlanningGlobal();
		pg.getLots().addAll(lots);
		pg.getSocietes().addAll(societes);
		return pg;
	}

	private String demanderFichierExcel(String titre)
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(titre);
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		return fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
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

	// ── Main ──────────────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		if (args.length > 0)
			SwingUtilities.invokeLater(() -> new ControleurClient(args[0]));
		else
			SwingUtilities.invokeLater(app.ihm.login.FenetreConnexionClient::new);
	}
}