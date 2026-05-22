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
 * ControleurClient — mode réseau SÉCURISÉ v3.
 *
 * Chaque requête HTTP envoie le token de session dans X-Auth-Token.
 * En cas de 401 (token expiré), l'app redirige vers la fenêtre de connexion.
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	private final String     urlServeur;
	private final HttpClient http;

	// ── Identité ──────────────────────────────────────────────────────────
	private final String  identifiant;
	private final boolean accesPAM;

	// ── Token de session ──────────────────────────────────────────────────
	/** Token opaque reçu du serveur via /login. Jamais stocké sur disque. */
	private final String tokenSession;

	// ── Données ───────────────────────────────────────────────────────────
	private ArrayList<Lot>     lots     = new ArrayList<>();
	private ArrayList<Societe> societes = new ArrayList<>();

	// ── Mode désynchronisé ────────────────────────────────────────────────
	private volatile boolean   desynchronise = false;
	private DonneesSauvegarder savLocal;

	// ── Polling ───────────────────────────────────────────────────────────
	private volatile String  versionLocale = "";
	private volatile boolean heureSup      = false;
	private static final int POLLING_MS    = 3000;

	// ── Constructeurs ─────────────────────────────────────────────────────

	/** Constructeur principal — avec token de session. */
	public ControleurClient(String ipServeur, String identifiant, boolean accesPAM, String token)
	{
		this.urlServeur   = "http://" + ipServeur + ":8080";
		this.identifiant  = identifiant;
		this.accesPAM     = accesPAM;
		this.tokenSession = token;
		this.savLocal     = new DonneesSauvegarder();

		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

		new Thread(() -> {
			boolean ok = chargerDepuisServeur();
			SwingUtilities.invokeLater(() -> {
				if (ok) this.fenetre = new FenetrePrincipale(this);
				else    new app.ihm.login.FenetreConnexionClient();
			});
		}).start();
		demarrerPolling();
	}

	/** Constructeur legacy sans token (rétrocompatibilité). */
	public ControleurClient(String ipServeur, String identifiant, boolean accesPAM)
	{ this(ipServeur, identifiant, accesPAM, ""); }

	/** Constructeur legacy sans identifiant. */
	public ControleurClient(String ipServeur)
	{ this(ipServeur, "PAM", true, ""); }

	// ── Getters ───────────────────────────────────────────────────────────

	public String  getIdentifiant()  { return identifiant;   }
	public boolean isAccesPAM()      { return accesPAM;      }
	public boolean isDesynchronise() { return desynchronise; }

	// ── Mode désynchronisé ────────────────────────────────────────────────

	public void seDesynchroniser() { if (accesPAM) { desynchronise = true; } }

	public void seResynchroniser()
	{
		desynchronise = false;
		try { chargerDepuisServeur(); if (fenetre != null) SwingUtilities.invokeLater(() -> fenetre.rafraichirTout()); }
		catch (Exception ignored) {}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  IControleur — Données
	// ══════════════════════════════════════════════════════════════════════

	@Override public ArrayList<Lot>     getLots()     { return lots;     }
	@Override public ArrayList<Societe> getSocietes() { return societes; }

	// ══════════════════════════════════════════════════════════════════════
	//  IControleur — Lots
	// ══════════════════════════════════════════════════════════════════════

	@Override
	public void ajouterLot(Lot lot) {
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", JsonSerialiser.serialiserLotSeul(lot))); autoSauvegarde(); }
		catch (Exception e) { err("ajouterLot(Lot)", e); }
	}

	@Override
	public void ajouterLot(int numCDE, String typo, String affaire,
						   int nbPieces, double cadence, int valeurVente,
						   String statut, String statutEchant,
						   String semaine, int priorite,
						   String lotACharge, String emplacement,
						   boolean sousDouane, String dateReception,
						   String datePaiement, String commentaire)
	{
		try {
			String c = "{\"numCDE\":"      + numCDE
				+ ",\"typologie\":"    + e(typo)
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
		} catch (Exception ex) { err("ajouterLot", ex); }
	}

	@Override
	public void supprimerLot(Lot lot) {
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception ex) { err("supprimerLot", ex); }
	}

	@Override
	public void exportNewLot() {
		try { post("/nouveaux", "{}"); chargerDepuisServeur(); if (fenetre != null) fenetre.rafraichirTout(); }
		catch (Exception ex) { err("exportNewLot", ex); }
	}

	@Override
	public void nouveaux() {
		JOptionPane.showMessageDialog(null,
			"Action réservée au serveur.", "Information", JOptionPane.INFORMATION_MESSAGE);
	}

	// ── Modification lots ─────────────────────────────────────────────────

	@Override
	public void modifierLot(Lot lot, String typo, String affaire,
							int nbPieces, double cadence, int valeurVente,
							String statut, String statutEchant,
							String semaine, int priorite,
							String lotACharge, String emplacement,
							boolean sousDouane, String dateReception,
							String datePaiement, String commentaire)
	{
		try {
			String c = "{\"numCDE\":"      + lot.getNumCDE()
				+ ",\"typologie\":"    + e(typo)
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
		} catch (Exception ex) { err("modifierLot", ex); }
	}

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
							  boolean sortieEtiq, boolean tri, boolean finit)
	{
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"preTri\":"     + preTri
				+ ",\"surPiste\":"   + surPiste
				+ ",\"sortieEtiq\":" + sortieEtiq
				+ ",\"tri\":"        + tri
				+ ",\"finit\":"      + finit + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", c));
			autoSauvegarde();
		} catch (Exception ex) { err("modifierPhase", ex); }
	}

	@Override
	public void marquerLotTermine(Lot lot) {
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/terminer", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception ex) { err("marquerLotTermine", ex); }
	}

	@Override
	public void commencerLot(Lot lot) {
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/commencer", "{\"numCDE\":" + lot.getNumCDE() + "}")); }
		catch (Exception ex) { err("commencerLot", ex); }
	}

	@Override
	public void annulerLot(Lot lot) {
		try { this.lots = JsonSerialiser.deserialiserLots(post("/lots/annuler", "{\"numCDE\":" + lot.getNumCDE() + "}")); }
		catch (Exception ex) { err("annulerLot", ex); }
	}

	// ── Affectation ───────────────────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace) {
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"societe\":" + e(societe.getNom())
				+ ",\"ace\":"     + e(ace.getNom()) + "}";
			majDual(post("/lots/affecter", c));
			autoSauvegarde();
			return true;
		} catch (Exception ex) { err("affecterLot", ex); return false; }
	}

	@Override
	public void desaffecterLot(Lot lot) {
		try { majDual(post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}")); autoSauvegarde(); }
		catch (Exception ex) { err("desaffecterLot", ex); }
	}

	// ── Sociétés / ACE ────────────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif) {
		try {
			String c = "{\"nom\":"          + e(soc.getNom())
				+ ",\"nouveauNom\":"  + e(nom)
				+ ",\"ce\":"          + e(ce)
				+ ",\"totalHeuresCE\":" + totalHeuresCE
				+ ",\"effectif\":"    + effectif + "}";
			this.societes = JsonSerialiser.deserialiserSocietes(post("/societes/modifier", c), this.lots);
		} catch (Exception ex) { err("modifierSociete", ex); }
	}

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces) {
		try {
			StringBuilder sb = new StringBuilder("{\"societe\":" + e(soc.getNom()) + ",\"aces\":[");
			for (int i = 0; i < nouvellesAces.size(); i++) {
				Ace a = nouvellesAces.get(i);
				if (i > 0) sb.append(",");
				sb.append("{\"nom\":").append(e(a.getNom()))
				  .append(",\"nbPers\":").append(a.getNbPers())
				  .append(",\"effectifActuel\":").append(a.getEffectifActuel()).append("}");
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(post("/aces/mettreajour", sb.toString()), this.lots);
			return true;
		} catch (Exception ex) { err("mettreAJourAces", ex); return false; }
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine) {
		String xlsx = demanderFichierExcel("Sélectionner le fichier des heures ACE");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Nouvelle heure", JOptionPane.INFORMATION_MESSAGE); return; }
		try {
			java.util.Map<String, Integer> heuresParSoc = ExcelReader.lireHeuresSocietes(xlsx, semaine);
			if (heuresParSoc == null || heuresParSoc.isEmpty()) { JOptionPane.showMessageDialog(null, "Aucune donnée.", "Nouvelle heure", JOptionPane.WARNING_MESSAGE); return; }
			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heuresParSoc.entrySet()) {
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey())).append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(post("/nouvelleheure", sb.toString()), this.lots);
			autoSauvegarde();
			JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE);
			if (fenetre != null) fenetre.rafraichirTout();
		} catch (Exception ex) { err("nouvelleHeurePourSociete", ex); }
	}

	@Override
	public void semaineSup() {
		if (!accesPAM) {
			JOptionPane.showMessageDialog(null,
				"⛔  Action réservée au serveur.", "Non autorisé", JOptionPane.WARNING_MESSAGE);
			return;
		}
		JOptionPane.showMessageDialog(null,
			"Les heures supplémentaires sont gérées depuis le panneau de contrôle du serveur.",
			"Information", JOptionPane.INFORMATION_MESSAGE);
	}

	// ── Suivi prod ────────────────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart) {
		try {
			String c = "{\"numCDE\":" + lot.getNumCDE()
				+ ",\"nbPieceEtiq\":"   + nbPieceEtiq
				+ ",\"nbPieceRepart\":" + nbPieceRepart + "}";
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", c));
			autoSauvegarde();
		} catch (Exception ex) { err("mettreAJourSuiviProd", ex); }
	}

	// ── Recherche ─────────────────────────────────────────────────────────

	@Override
	public Societe getSocieteDuLot(Lot lot) {
		for (Societe s : societes) for (Lot l : s.getLots()) if (l.getNumCDE() == lot.getNumCDE()) return s;
		return null;
	}

	@Override
	public Ace getAceDuLot(Lot lot) {
		for (Societe s : societes) for (Ace a : s.getAces()) for (Lot l : a.getLots())
			if (l.getNumCDE() == lot.getNumCDE()) return a;
		return null;
	}

	@Override
	public ArrayList<Ace> getTouteAces() {
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
	public void sauvegarderDonnees(String cheminDossier, String semaine) {
		if (desynchronise) {
			try {
				String dossier = cheminDossier + "/S" + semaine;
				java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
				savLocal.sauvegarderLots    (lots,     dossier + "/lots.json");
				savLocal.sauvegarderSocietes(societes, lots, dossier + "/societes.json");
				JOptionPane.showMessageDialog(null, "Sauvegarde locale S" + semaine, "OK", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception ex) { err("sauvegarderDonnees (local)", ex); }
		} else {
			try { post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}"); }
			catch (Exception ex) { err("sauvegarderDonnees", ex); }
		}
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException {
		if (!desynchronise) return;
		try { savLocal.charger(new PlanningGlobal(), chemin); }
		catch (Exception ex) { err("chargerDonnees (désync)", ex); }
	}

	@Override
	public void autoSauvegarde() {
		if (desynchronise) return;
		try { post("/autosave/lots",     "{}"); } catch (Exception ignored) {}
		try { post("/autosave/societes", "{}"); } catch (Exception ignored) {}
	}

	// ── Polling ───────────────────────────────────────────────────────────

	private void demarrerPolling()
	{
		Thread t = new Thread(() -> {
			int echecsConsecutifs = 0;
			boolean avertissementAffiche = false;

			while (true) {
				try {
					Thread.sleep(POLLING_MS);
					if (desynchronise) continue;
					boolean modif = rafraichirSiModif();
					echecsConsecutifs = 0; // reset si succès
					avertissementAffiche = false;
					if (modif && fenetre != null)
						SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());

				} catch (InterruptedException ex) {
					break;
				} catch (Exception ex) {
					echecsConsecutifs++;
					if (echecsConsecutifs >= 3 && !avertissementAffiche) {
						avertissementAffiche = true;
						SwingUtilities.invokeLater(() -> {
							JOptionPane.showMessageDialog(fenetre,
								"⚠️ Connexion au serveur perdue.\n" +
								"Le serveur est peut-être arrêté.\n" +
								"Les modifications ne seront pas synchronisées.",
								"Serveur inaccessible",
								JOptionPane.WARNING_MESSAGE);
						});
					}
				}
			}
		});
		t.setDaemon(true); t.setName("polling-serveur"); t.start();
	}

	private boolean rafraichirSiModif() {
		try {
			String rep = get("/version");
			String v   = JsonSerialiser.extraireString(rep, "v");
			boolean hs = JsonSerialiser.extraireBool(rep, "heureSup");
			if (hs != heureSup) { heureSup = hs; PlanningGlobal.estHeureSup = hs; }
			if (v != null && !v.equals(versionLocale)) {
				versionLocale = v; chargerDepuisServeur(); return true;
			}
		} catch (Exception ignored) {}
		return false;
	}

	private boolean chargerDepuisServeur() {
		try {
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			String repVer = get("/version");
			versionLocale  = JsonSerialiser.extraireString(repVer, "v");
			heureSup       = JsonSerialiser.extraireBool  (repVer, "heureSup");
			PlanningGlobal.estHeureSup = heureSup;
			return true;
		} catch (Exception ex) { System.err.println("[Client] Échec chargement : " + ex.getMessage()); return false; }
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private void majDual(String rep) {
		String jL = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jS = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jL != null) this.lots     = JsonSerialiser.deserialiserLots(jL);
		if (jS != null) this.societes = JsonSerialiser.deserialiserSocietes(jS, this.lots);
	}

	private String demanderFichierExcel(String titre) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(titre);
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		return fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().getAbsolutePath() : null;
	}

	/** GET avec X-Auth-Token. */
	private String get(String route) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.timeout(Duration.ofSeconds(15))
			.GET();
		if (tokenSession != null && !tokenSession.isBlank()) b.header("X-Auth-Token", tokenSession);
		HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() == 401) { gererDeconnexion(); throw new Exception("Session expirée"); }
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	/** POST avec X-Auth-Token. */
	private String post(String route, String json) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/json");
		if (tokenSession != null && !tokenSession.isBlank()) b.header("X-Auth-Token", tokenSession);
		b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
		HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() == 401) { gererDeconnexion(); throw new Exception("Session expirée"); }
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	private void gererDeconnexion() {
		SwingUtilities.invokeLater(() -> {
			if (fenetre != null) fenetre.dispose();
			JOptionPane.showMessageDialog(null, "Session expirée. Veuillez vous reconnecter.",
				"Session expirée", JOptionPane.WARNING_MESSAGE);
			new app.ihm.login.FenetreConnexionClient();
		});
	}

	private static String e(String s) { return JsonSerialiser.esc(s); }

	private void err(String m, Exception ex) {
		System.err.println("[Client] " + m + " : " + (ex != null ? ex.getMessage() : "null"));
	}

	public static void main(String[] args) {
		if (args.length > 0) SwingUtilities.invokeLater(() -> new ControleurClient(args[0]));
		else SwingUtilities.invokeLater(app.ihm.login.FenetreConnexionClient::new);
	}
}