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
import app.securite.ChiffrementAES;
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
 * ══════════════════════════════════════════════════════════════
 *  ControleurClient — mode réseau sécurisé avec chiffrement AES
 *
 *  FONCTIONNEMENT GÉNÉRAL :
 *  ────────────────────────
 *  Ce contrôleur remplace Controleur.java quand l'application
 *  tourne en mode client réseau (connexion à un ServeurHTTP distant).
 *
 *  Il implémente IControleur, donc la FenetrePrincipale n'a pas
 *  besoin de savoir si elle tourne en mode solo ou réseau.
 *
 *  SÉQUENCE DE DÉMARRAGE :
 *  ───────────────────────
 *  1. Constructeur appelé avec IP, identifiant, token de session
 *  2. Thread de fond : chargerDepuisServeur() → charge lots + sociétés
 *  3. Thread de fond : recupererCle() → récupère la clé AES via GET /cle
 *  4. À partir de là, tous les échanges HTTP sont chiffrés
 *  5. FenetrePrincipale s'ouvre sur le thread Swing (invokeLater)
 *  6. Thread de polling démarre (toutes les 3s : GET /version)
 *
 *  CHIFFREMENT AES (correctif #9) :
 *  ──────────────────────────────────
 *  - La clé est reçue du serveur via GET /cle après authentification
 *  - get()  : déchiffre automatiquement la réponse si aes != null
 *  - post() : chiffre automatiquement le body si aes != null
 *  - aes = null jusqu'à recupererCle() → les premiers échanges
 *    (chargerDepuisServeur, /cle lui-même) transitent en clair, ce qui
 *    est inévitable puisqu'on ne peut pas déchiffrer avant d'avoir la clé
 *
 *  MODE DÉSYNCHRONISÉ :
 *  ─────────────────────
 *  PAM peut se désynchroniser du serveur pour préparer des semaines futures
 *  localement sans perturber les autres clients. Le polling est suspendu,
 *  les modifications sont sauvegardées en local uniquement.
 *  La resynchronisation recharge l'état du serveur et perd les modifs locales.
 * ══════════════════════════════════════════════════════════════
 */
public class ControleurClient implements IControleur
{
	private FenetrePrincipale fenetre;

	// URL de base du serveur, ex: "http://192.168.1.10:8080"
	private final String     urlServeur;

	// Client HTTP Java 11 — réutilisé pour toutes les requêtes
	private final HttpClient http;

	// ── Identité ──────────────────────────────────────────────────────────
	private final String  identifiant; // "PAM" ou nom de société
	private final boolean accesPAM;   // true = droits administrateur

	// ── Token de session ──────────────────────────────────────────────────
	// Reçu du serveur via POST /login. Envoyé dans X-Auth-Token sur chaque requête.
	// Jamais stocké sur disque, disparaît à la fermeture de l'application.
	private final String tokenSession;

	// ── Clé AES ───────────────────────────────────────────────────────────
	// null jusqu'à ce que recupererCle() l'initialise depuis GET /cle.
	// volatile car écrite depuis un thread de fond et lue depuis le thread HTTP.
	private volatile ChiffrementAES aes = null;

	// ── Données locales ───────────────────────────────────────────────────
	// Copie locale des données du serveur, mise à jour par le polling.
	// Tous les appels IControleur modifient le serveur ET mettent à jour
	// cette copie avec la réponse retournée par le serveur.
	private ArrayList<Lot>     lots     = new ArrayList<>();
	private ArrayList<Societe> societes = new ArrayList<>();

	// ── Mode désynchronisé ────────────────────────────────────────────────
	private volatile boolean   desynchronise = false;
	// Utilisé uniquement en mode désynchronisé pour sauvegarder localement
	private DonneesSauvegarder savLocal;

	// ── Polling ───────────────────────────────────────────────────────────
	// Numéro de version de la dernière synchronisation avec le serveur.
	// Si /version retourne un numéro différent, on recharge tout.
	private volatile String  versionLocale = "";
	private volatile boolean heureSup      = false;
	private static final int POLLING_MS    = 1000; // 1 secondes entre chaque sondage

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEURS
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Constructeur principal — appelé après un login réussi.
	 *
	 * @param ipServeur  adresse IP du serveur, ex: "192.168.1.10"
	 * @param identifiant  "PAM" ou nom de société
	 * @param accesPAM   true si l'identifiant est PAM
	 * @param token      token de session reçu via POST /login
	 */
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

		// Chargement initial et récupération de la clé AES dans un thread de fond.
		// On ne bloque pas le thread Swing pendant la connexion réseau.
		new Thread(() -> {
			boolean ok = chargerDepuisServeur();
			// recupererCle() s'exécute APRÈS chargerDepuisServeur() :
			// on a besoin d'un token valide, qui est vérifié côté serveur sur /cle
			if (ok) recupererCle();
			// Retour sur le thread Swing pour ouvrir la fenêtre
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

	/** Constructeur legacy minimal. */
	public ControleurClient(String ipServeur)
	{ this(ipServeur, "PAM", true, ""); }

	// ══════════════════════════════════════════════════════════════════════
	//  GETTERS
	// ══════════════════════════════════════════════════════════════════════

	public String  getIdentifiant()  { return identifiant;   }
	public boolean isAccesPAM()      { return accesPAM;      }
	public boolean isDesynchronise() { return desynchronise; }

	// ══════════════════════════════════════════════════════════════════════
	//  MODE DÉSYNCHRONISÉ
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Passe en mode désynchronisé (PAM uniquement).
	 * Le polling est suspendu, les sauvegardes se font en local.
	 * Les autres clients continuent de voir l'état du serveur.
	 */
	public void seDesynchroniser()
	{
		if (accesPAM) { desynchronise = true; }
	}

	/**
	 * Revient en mode synchronisé.
	 * Recharge l'état du serveur, ce qui écrase les modifications locales.
	 */
	public void seResynchroniser()
	{
		desynchronise = false;
		try {
			chargerDepuisServeur();
			if (fenetre != null) SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());
		} catch (Exception ignored) {}
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
		// 1. Ajouter localement IMMÉDIATEMENT
		this.lots.add(lot);
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur en arrière-plan
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter",
					JsonSerialiser.serialiserLotSeul(lot)));
			} catch (Exception ex) {
				err("ajouterLot(Lot)", ex);
			}
		}).start();
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
		String c = "{\"numCDE\":"       + numCDE
			+ ",\"typologie\":"     + e(typo)
			+ ",\"affaire\":"       + e(affaire)
			+ ",\"nbPieces\":"      + nbPieces
			+ ",\"cadence\":"       + cadence
			+ ",\"valeurVente\":"   + valeurVente
			+ ",\"statut\":"        + e(statut)
			+ ",\"statutEchant\":"  + e(statutEchant)
			+ ",\"semaine\":"       + e(semaine)
			+ ",\"priorite\":"      + priorite
			+ ",\"lotACharge\":"    + e(lotACharge)
			+ ",\"emplacement\":"   + e(emplacement)
			+ ",\"estSousDouane\":" + sousDouane
			+ ",\"dateReception\":" + e(dateReception)
			+ ",\"datePaiement\":"  + e(datePaiement)
			+ ",\"commentaire\":"   + e(commentaire) + "}";
		async("ajouterLot", () -> {
			this.lots = JsonSerialiser.deserialiserLots(post("/lots/ajouter", c));
		});
	}

	@Override
	public void supprimerLot(Lot lot) {
		// 1. Supprimer localement IMMÉDIATEMENT
		this.lots.remove(lot);
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur en arrière-plan
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(
					post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			} catch (Exception ex) {
				err("supprimerLot", ex);
			}
		}).start();
	}

	@Override
	public void exportNewLot() {
		async("exportNewLot", () -> {
			post("/nouveaux", "{}");
			chargerDepuisServeur();
		});
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
						String statut, String statutEchant, String semaine, int priorite,
						String lotACharge, String emplacement, boolean sousDouane,
						String dateReception, String datePaiement, String commentaire)
	{
		modifierLotComplet(lot,
			typo, affaire, semaine, emplacement, dateReception, datePaiement,
			nbPieces, lot.getPrixUnitaire(), valeurVente, cadence, lot.getHeures(),
			lotACharge, statut, statutEchant, sousDouane, commentaire,
			lot.getFormatCarton() != null ? lot.getFormatCarton() : "",
			lot.getCollisage(), lot.getNbPers(),
			lot.getDistribution() != null ? lot.getDistribution() : "",
			lot.getCadenceReel(), lot.getPoucentrecupCartonFour(),
			lot.getMethode() != null ? lot.getMethode().getNom() : "");
	}

	/** Signature COMPLÈTE — CarteLot. Champs administratifs + logistiques. */
	@Override
	public void modifierLotComplet(Lot lot,
							String typologie, String affaire,
							String semaine, String emplacement,
							String dateReception, String datePaiement,
							int nbPieces, double prixUnitaire, int valeurVente,
							double cadence, double heures,
							String lotACharge,
							String statut, String statutEchant,
							boolean sousDouane,
							String commentaire,
							String formatCarton, int collisage, int nbPers,
							String distribution, double cadenceReel,
							int poucentrecupCartonFour, String methode)
	{
		// 1. Mise à jour locale IMMÉDIATE
		lot.setTypologie(typologie);          lot.setAffaire(affaire);
		lot.setSemaine(semaine);              lot.setEmplacement(emplacement);
		lot.setDateReception(dateReception);  lot.setDatePaiement(datePaiement);
		lot.setNbPieces(nbPieces);            lot.setValeurVente(valeurVente);
		lot.setCadence(cadence);              lot.setLotACharge(lotACharge);
		lot.setStatut(statut);                lot.setStatutEchant(statutEchant);
		lot.setEstSousDouane(sousDouane);     lot.setCommentaire(commentaire);
		lot.setFormatCarton(formatCarton);    lot.setCollisage(collisage);
		lot.setNbPers(nbPers);                lot.setDistribution(distribution);
		lot.setCadenceReel(cadenceReel);
		lot.setPoucentrecupCartonFour(poucentrecupCartonFour);
		lot.setMethode(methode);
		if (fenetre != null) fenetre.rafraichirTout();

		// 2. Synchronisation serveur
		String c = "{\"numCDE\":"                   + lot.getNumCDE()
			+ ",\"typologie\":"               + e(typologie)
			+ ",\"affaire\":"                 + e(affaire)
			+ ",\"semaine\":"                 + e(semaine)
			+ ",\"emplacement\":"             + e(emplacement)
			+ ",\"dateReception\":"           + e(dateReception)
			+ ",\"datePaiement\":"            + e(datePaiement)
			+ ",\"nbPieces\":"                + nbPieces
			+ ",\"prixUnitaire\":"            + prixUnitaire
			+ ",\"valeurVente\":"             + valeurVente
			+ ",\"cadence\":"                 + cadence
			+ ",\"heures\":"                  + heures
			+ ",\"lotACharge\":"              + e(lotACharge)
			+ ",\"statut\":"                  + e(statut)
			+ ",\"statutEchant\":"            + e(statutEchant)
			+ ",\"sousDouane\":"              + sousDouane
			+ ",\"commentaire\":"             + e(commentaire)
			+ ",\"formatCarton\":"            + e(formatCarton)
			+ ",\"collisage\":"               + collisage
			+ ",\"nbPers\":"                  + nbPers
			+ ",\"distribution\":"            + e(distribution)
			+ ",\"cadenceReel\":"             + cadenceReel
			+ ",\"poucentrecupCartonFour\":"  + poucentrecupCartonFour
			+ ",\"methode\":"                 + e(methode) + "}";
		new Thread(() -> {
			try { majDual(post("/lots/modifier", c)); }
			catch (Exception ex) { err("modifierLotComplet", ex); }
		}).start();
	}



	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
							  boolean sortieEtiq, boolean tri, boolean finit)
	{
		// 1. Modifier localement IMMÉDIATEMENT
		lot.getPhase().setPreTri(preTri);
		lot.getPhase().setSurPiste(surPiste);
		lot.getPhase().setSortieEtiq(sortieEtiq);
		lot.getPhase().setTri(tri);
		lot.getPhase().setFinit(finit);
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur en arrière-plan
		String c = "{\"numCDE\":"    + lot.getNumCDE()
			+ ",\"preTri\":"     + preTri
			+ ",\"surPiste\":"   + surPiste
			+ ",\"sortieEtiq\":" + sortieEtiq
			+ ",\"tri\":"        + tri
			+ ",\"finit\":"      + finit + "}";
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(post("/lots/phase", c));
			} catch (Exception ex) {
				err("modifierPhase", ex);
			}
		}).start();
	}

	@Override
	public void marquerLotTermine(Lot lot) {
		// 1. Marquer localement IMMÉDIATEMENT
		lot.setStatut("MC");
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(
					post("/lots/terminer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			} catch (Exception ex) {
				err("marquerLotTermine", ex);
			}
		}).start();
	}

	@Override
	public void commencerLot(Lot lot) {
		// 1. Commencer localement IMMÉDIATEMENT
		lot.setStatut("TC");
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(
					post("/lots/commencer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			} catch (Exception ex) {
				err("commencerLot", ex);
			}
		}).start();
	}

	@Override
	public void annulerLot(Lot lot) {
		// 1. Annuler localement IMMÉDIATEMENT
		lot.setStatut("OU");
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(
					post("/lots/annuler", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			} catch (Exception ex) {
				err("annulerLot", ex);
			}
		}).start();
	}

	// ── Affectation ───────────────────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		// 1. Défaire une ancienne affectation si nécessaire
		Societe ancSociete = getSocieteDuLot(lot);
		Ace     ancAce     = getAceDuLot(lot);
		if (ancSociete == societe && ancAce == ace) return true;
		if (ancSociete != null)
		{
			if (ancAce != null) ancSociete.enleverLotACE(ancAce, lot);
			ancSociete.enleverLot(lot);
		}
		// 2. Affecter localement IMMÉDIATEMENT
		societe.ajouterLot(lot, ace);
		// 3. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 4. Synchroniser avec le serveur en arrière-plan
		String c = "{\"numCDE\":" + lot.getNumCDE()
			+ ",\"societe\":" + e(societe.getNom())
			+ ",\"ace\":"     + e(ace.getNom()) + "}";
		new Thread(() -> {
			try {
				majDual(post("/lots/affecter", c));
			} catch (Exception ex) {
				err("affecterLot", ex);
			}
		}).start();
		return true; // optimiste — le serveur confirmera
	}

	@Override
	public void desaffecterLot(Lot lot) {
		// 1. Trouver la societe du lot
		Societe soc = getSocieteDuLot(lot);
		Ace     ace = getAceDuLot(lot);
		if (soc != null) {
			// 2. Désaffecter localement IMMÉDIATEMENT
			if (ace != null) soc.enleverLotACE(ace, lot);
			soc.enleverLot(lot);
		}
		// 3. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 4. Synchroniser avec le serveur en arrière-plan
		new Thread(() -> {
			try {
				majDual(post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}"));
			} catch (Exception ex) {
				err("desaffecterLot", ex);
			}
		}).start();
	}

	// ── Sociétés / ACE ────────────────────────────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif) {
		String ancienNom = soc.getNom();
		// 1. Modifier localement IMMÉDIATEMENT
		soc.setNom(nom);
		soc.setCe(ce);
		soc.setTotalHeuresCE(totalHeuresCE);
		soc.setEffectifTotal(effectif);
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur en arrière-plan
		String c = "{\"nom\":"           + e(ancienNom)
			+ ",\"nouveauNom\":"   + e(nom)
			+ ",\"ce\":"           + e(ce)
			+ ",\"totalHeuresCE\":" + totalHeuresCE
			+ ",\"effectif\":"     + effectif + "}";
		new Thread(() -> {
			try {
				this.societes = JsonSerialiser.deserialiserSocietes(
					post("/societes/modifier", c), this.lots);
			} catch (Exception ex) {
				err("modifierSociete", ex);
			}
		}).start();
	}

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces) {
		StringBuilder sb = new StringBuilder("{\"societe\":" + e(soc.getNom()) + ",\"aces\":[");
		for (int i = 0; i < nouvellesAces.size(); i++) {
			Ace a = nouvellesAces.get(i);
			if (i > 0) sb.append(",");
			sb.append("{\"nom\":").append(e(a.getNom()))
			  .append(",\"nbPers\":").append(a.getNbPers())
			  .append(",\"effectif\":").append(a.getEffectifActuel()).append("}");
		}
		sb.append("]}");
		async("mettreAJourAces", () -> {
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/aces/mettreajour", sb.toString()), this.lots);
		});
		return true;
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine) {
		String xlsx = demanderFichierExcel("Sélectionner le fichier des heures ACE");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Nouvelle heure",
			JOptionPane.INFORMATION_MESSAGE); return; }
		async("nouvelleHeurePourSociete", () -> {
			java.util.Map<String, Integer> heuresParSoc = ExcelReader.lireHeuresSocietes(xlsx, semaine);
			if (heuresParSoc == null || heuresParSoc.isEmpty()) {
				JOptionPane.showMessageDialog(null, "Aucune donnée.", "Nouvelle heure",
					JOptionPane.WARNING_MESSAGE); return;
			}
			StringBuilder sb = new StringBuilder("{\"societes\":[");
			boolean first = true;
			for (java.util.Map.Entry<String, Integer> entry : heuresParSoc.entrySet()) {
				if (!first) sb.append(",");
				sb.append("{\"nom\":").append(e(entry.getKey()))
				  .append(",\"heures\":").append(entry.getValue()).append("}");
				first = false;
			}
			sb.append("]}");
			this.societes = JsonSerialiser.deserialiserSocietes(
				post("/nouvelleheure", sb.toString()), this.lots);
			JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE);
		});
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

	// ── Suivi production ─────────────────────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart) {
		// 1. Mettre à jour localement IMMÉDIATEMENT
		lot.getSuivieProd().setNbPieceEtiq(nbPieceEtiq);
		lot.getSuivieProd().setNbPieceRepart(nbPieceRepart);
		// 2. Rafraîchir la fenêtre IMMÉDIATEMENT
		if (fenetre != null) fenetre.rafraichirTout();
		// 3. Synchroniser avec le serveur en arrière-plan
		String c = "{\"numCDE\":"        + lot.getNumCDE()
			+ ",\"nbPieceEtiq\":"   + nbPieceEtiq
			+ ",\"nbPieceRepart\":" + nbPieceRepart + "}";
		new Thread(() -> {
			try {
				this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", c));
			} catch (Exception ex) {
				err("mettreAJourSuiviProd", ex);
			}
		}).start();
	}

	// ── Recherche ─────────────────────────────────────────────────────────

	@Override
	public Societe getSocieteDuLot(Lot lot) {
		for (Societe s : societes) for (Lot l : s.getLots())
			if (l.getNumCDE() == lot.getNumCDE()) return s;
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
			// Mode désynchronisé : sauvegarde locale, le serveur n'est pas contacté
			async("sauvegarderDonnees (local)", () -> {
				String dossier = cheminDossier + "/S" + semaine;
				java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
				savLocal.sauvegarderLots    (lots,     dossier + "/lots.json");
				savLocal.sauvegarderSocietes(societes, lots, dossier + "/societes.json");
				JOptionPane.showMessageDialog(null, "Sauvegarde locale S" + semaine, "OK",
					JOptionPane.INFORMATION_MESSAGE);
			});
		} else {
			// Mode normal : délègue la sauvegarde au serveur
			async("sauvegarderDonnees", () -> {
				post("/sauvegarder",
					"{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}");
			});
		}
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException {
		// Uniquement disponible en mode désynchronisé
		if (!desynchronise) return;
		try { savLocal.charger(new PlanningGlobal(), chemin); }
		catch (Exception ex) { err("chargerDonnees (désync)", ex); }
	}

	@Override
	public void autoSauvegarde() {
		if (desynchronise) return; // en désync, on ne sauvegarde pas sur le serveur
		try { post("/autosave/lots",     "{}"); } catch (Exception ignored) {}
		try { post("/autosave/societes", "{}"); } catch (Exception ignored) {}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  POLLING — surveillance des changements côté serveur
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Lance le thread de polling en arrière-plan.
	 * Toutes les POLLING_MS millisecondes, appelle GET /version.
	 * Si le numéro de version a changé, recharge tout depuis le serveur.
	 *
	 * Gestion des erreurs : après 3 échecs consécutifs, affiche un
	 * avertissement à l'utilisateur (une seule fois, pas en boucle).
	 */
	private void demarrerPolling()
	{
		Thread t = new Thread(() -> {
			int     echecsConsecutifs   = 0;
			boolean avertissementAffiche = false;

			while (true) {
				try {
					Thread.sleep(POLLING_MS);
					if (desynchronise) continue; // polling suspendu en mode désync

					boolean modif = rafraichirSiModif();
					echecsConsecutifs    = 0;
					avertissementAffiche = false;
					if (modif && fenetre != null)
						SwingUtilities.invokeLater(() -> fenetre.rafraichirTout());

				} catch (InterruptedException ex) {
					break; // thread interrompu → sortie propre
				} catch (Exception ex) {
					echecsConsecutifs++;
					if (echecsConsecutifs >= 3 && !avertissementAffiche) {
						avertissementAffiche = true;
						SwingUtilities.invokeLater(() ->
							JOptionPane.showMessageDialog(fenetre,
								"⚠️ Connexion au serveur perdue.\n" +
								"Le serveur est peut-être arrêté.\n" +
								"Les modifications ne seront pas synchronisées.",
								"Serveur inaccessible", JOptionPane.WARNING_MESSAGE));
					}
				}
			}
		});
		t.setDaemon(true); // daemon : s'arrête quand la fenêtre se ferme
		t.setName("polling-serveur");
		t.start();
	}

	/**
	 * Interroge GET /version et recharge les données si la version a changé.
	 * Retourne true si un rechargement a eu lieu.
	 */
	private boolean rafraichirSiModif() {
		try {
			String  rep = get("/version");
			String  v   = JsonSerialiser.extraireString(rep, "v");
			boolean hs  = JsonSerialiser.extraireBool(rep, "heureSup");
			if (hs != heureSup) { heureSup = hs; PlanningGlobal.estHeureSup = hs; }
			if (v != null && !v.equals(versionLocale)) {
				versionLocale = v;
				chargerDepuisServeur();
				return true;
			}
		} catch (Exception ignored) {}
		return false;
	}

	/**
	 * Charge (ou recharge) l'intégralité des lots et sociétés depuis le serveur.
	 * Appelé au démarrage, après un changement de version, et après resynchronisation.
	 * Retourne false si la connexion a échoué.
	 */
	private boolean chargerDepuisServeur() {
		try {
			this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
			this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
			String repVer  = get("/version");
			versionLocale  = JsonSerialiser.extraireString(repVer, "v");
			heureSup       = JsonSerialiser.extraireBool  (repVer, "heureSup");
			PlanningGlobal.estHeureSup = heureSup;
			return true;
		} catch (Exception ex) {
			System.err.println("[Client] Échec chargement : " + ex.getMessage());
			return false;
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHIFFREMENT — récupération de la clé AES
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Appelle GET /cle pour récupérer la clé AES du serveur.
	 * Cette méthode est appelée APRÈS un chargement initial réussi,
	 * donc le token de session est déjà valide.
	 *
	 * Une fois la clé reçue, toutes les communications suivantes
	 * (get et post) sont automatiquement chiffrées/déchiffrées.
	 *
	 * En cas d'échec, on continue sans chiffrement (dégradé mais fonctionnel).
	 */
	private void recupererCle()
	{
		try {
			// La réponse de /cle n'est PAS chiffrée (le serveur l'envoie en clair
			// car on n'a pas encore la clé). get() ne tentera pas de déchiffrer
			// car aes est encore null à ce moment-là.
			String rep       = get("/cle");
			String cleBase64 = JsonSerialiser.extraireString(rep, "cle");
			if (cleBase64 != null && !cleBase64.isBlank()) {
				this.aes = ChiffrementAES.depuisBase64(cleBase64);
				System.out.println("[Client] Chiffrement AES-256 activé.");
			}
		} catch (Exception e) {
			System.err.println("[Client] Impossible de récupérer la clé AES : " + e.getMessage());
			System.err.println("[Client] Les échanges continueront sans chiffrement.");
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HTTP — méthodes get() et post()
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Envoie une requête GET et retourne le corps de la réponse.
	 *
	 * Chiffrement (correctif #9) :
	 *   Si aes != null et que la réponse ne commence pas par '{',
	 *   on suppose que c'est du Base64 chiffré et on déchiffre.
	 *   La détection "commence par '{'" est une heuristique simple :
	 *   toutes les réponses JSON valides commencent par '{' ou '[',
	 *   alors que le Base64 commence par des caractères alphanumériques.
	 *
	 * Gestion 401 : token expiré → déconnexion automatique et retour
	 * à la fenêtre de connexion.
	 */
	private String get(String route) throws Exception
	{
		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.timeout(Duration.ofSeconds(15))
			.GET();

		if (tokenSession != null && !tokenSession.isBlank())
			b.header("X-Auth-Token", tokenSession);

		HttpResponse<String> resp = http.send(
			b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (resp.statusCode() == 401) { gererDeconnexion(); throw new Exception("Session expirée"); }
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());

		String body = resp.body();
		// Déchiffrer si AES actif et réponse en Base64 (pas du JSON brut)
		if (aes != null && body != null && !body.isBlank()
				&& !body.startsWith("{") && !body.startsWith("[")) {
			try { body = aes.dechiffrer(body); }
			catch (Exception ignored) { /* réponse non chiffrée, on la retourne telle quelle */ }
		}
		return body;
	}

	/**
	 * Envoie une requête POST avec un corps JSON et retourne la réponse.
	 *
	 * Chiffrement (correctif #9) :
	 *   Si aes != null, le corps JSON est chiffré avant envoi.
	 *   La réponse est déchiffrée de la même façon que dans get().
	 *
	 *   En cas d'échec de chiffrement du corps, on envoie le JSON brut
	 *   plutôt que de bloquer l'opération.
	 */
	private String post(String route, String json) throws Exception
	{
		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(urlServeur + route))
			.timeout(Duration.ofSeconds(15))
			.header("Content-Type", "application/json");
		if (tokenSession != null && !tokenSession.isBlank())
			b.header("X-Auth-Token", tokenSession);
		b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
		HttpResponse<String> resp = http.send(
			b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() == 401) { gererDeconnexion(); throw new Exception("Session expirée"); }
		if (resp.statusCode() >= 400) throw new Exception("HTTP " + resp.statusCode() + ": " + resp.body());
		return resp.body();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES INTERNES
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Met à jour lots ET societes depuis une réponse "dual" du serveur.
	 * Utilisé par affecterLot et desaffecterLot qui retournent les deux
	 * collections dans la même réponse JSON pour éviter un double aller-retour.
	 */
	private void majDual(String rep) {
		String jL = JsonSerialiser.extraireBloc(rep, "\"lots\"");
		String jS = JsonSerialiser.extraireBloc(rep, "\"societes\"");
		if (jL != null) this.lots     = JsonSerialiser.deserialiserLots(jL);
		if (jS != null) this.societes = JsonSerialiser.deserialiserSocietes(jS, this.lots);
	}

	/**
	 * Exécute une action HTTP dans un thread de fond.
	 * Le thread Swing n'est jamais bloqué. À la fin de l'action,
	 * la fenêtre est automatiquement rafraîchie.
	 * 
	 * Rafraîchissement optimiste : la fenêtre est d'abord rafraîchie 
	 * IMMÉDIATEMENT et SYNCHRONEMENT (feedback utilisateur instantané),
	 * puis à nouveau quand la réponse du serveur arrive.
	 * 
	 * @param nom   nom de la méthode pour les logs d'erreur
	 * @param action  le code à exécuter (requête HTTP + mise à jour des données)
	 */
	private void async(String nom, CheckedRunnable action) {
		// Rafraîchir IMMÉDIATEMENT et SYNCHRONEMENT (pas invokeLater!)
		// On est déjà dans le thread Swing, donc on peut rafraîchir directement
		if (fenetre != null) fenetre.rafraichirTout();

		new Thread(() -> {
			try {
				action.run();
				// Second refresh quand la vraie réponse du serveur arrive
				SwingUtilities.invokeLater(() -> {
					if (fenetre != null) fenetre.rafraichirTout();
				});
			} catch (Exception ex) {
				err(nom, ex);
			}
		}).start();
	}

	/**
	 * Interface fonctionnelle pour les Runnable qui peuvent lancer des exceptions.
	 * Permet aux lambdas utilisées avec async() de déclarer les exceptions lancées.
	 */
	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	/** Ouvre un JFileChooser pour sélectionner un fichier Excel. */
	private String demanderFichierExcel(String titre) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(titre);
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		java.io.File def = new java.io.File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		return fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
			? fc.getSelectedFile().getAbsolutePath() : null;
	}

	/** Redirige vers la fenêtre de connexion quand le token expire (code 401). */
	private void gererDeconnexion() {
		SwingUtilities.invokeLater(() -> {
			if (fenetre != null) fenetre.dispose();
			JOptionPane.showMessageDialog(null,
				"Session expirée. Veuillez vous reconnecter.",
				"Session expirée", JOptionPane.WARNING_MESSAGE);
			new app.ihm.login.FenetreConnexionClient();
		});
	}

	/** Alias court pour JsonSerialiser.esc() — échappe une String pour JSON. */
	private static String e(String s) { return JsonSerialiser.esc(s); }

	/** Log d'erreur uniforme pour les appels IControleur échoués. */
	private void err(String methode, Exception ex) {
		System.err.println("[Client] " + methode + " : "
			+ (ex != null ? ex.getMessage() : "null"));
	}

	// ── Point d'entrée autonome ───────────────────────────────────────────

	public static void main(String[] args) {
		if (args.length > 0)
			SwingUtilities.invokeLater(() -> new ControleurClient(args[0]));
		else
			SwingUtilities.invokeLater(app.ihm.login.FenetreConnexionClient::new);
	}
}