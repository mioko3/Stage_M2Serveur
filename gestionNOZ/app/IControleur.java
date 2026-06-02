package app;

import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  IControleur — Interface abstraite du contrôleur
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE GLOBAL :
 * ─────────────
 * Interface contrat implémentée par deux contrôleurs distincts :
 *   • Controleur (solo)        : accès local direct à PlanningGlobal
 *   • ControleurClient (réseau): accès via HTTP au ServeurHTTP distant
 *
 * L'IHM (FenetrePrincipale, dialogues) n'a PAS besoin de savoir quel
 * contrôleur est utilisé → elle appelle simplement les méthodes
 * définies ici, le reste est transparent.
 *
 * ARCHITECTURE : Patron Strategy appliqué au contrôle de l'application
 *   FenetrePrincipale --depends-on--> IControleur <--impl-- Controleur
 *                                                    <--impl-- ControleurClient
 *
 * CHAMPS LOGISTIQUES — CarteLot vs DialogEditLot :
 * ───────────────────────────────────────────────
 * Pour éviter les conflits lors de mise à jour côté métier :
 *   • modifierLot()        : champs administratifs de base (DialogEditLot, inchangé)
 *   • modifierLotComplet() : TOUS les champs + logistiques (CarteLot)
 * Pourquoi ? CarteLot ajoute des champs qu'DialogEditLot ne gère pas
 * (formatCarton, collisage, etc.) → deux signatures maintiennent la rétrocompatibilité.
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public interface IControleur
{
	// ───────────────────────────────────────────────────────────────────
	// ACCÈS AUX DONNÉES
	// ───────────────────────────────────────────────────────────────────
	/**
	 * Retourne la liste des sociétés en cours.
	 * ⚠️  En réseau : copie (ne pas modifier), effectue GET /societes
	 */
	ArrayList<Societe> getSocietes();

	/**
	 * Retourne la liste des lots en cours.
	 * ⚠️  En réseau : copie (ne pas modifier), effectue GET /lots
	 */
	ArrayList<Lot>     getLots();

	// ── Lots ──────────────────────────────────────────────────────────────
	void    ajouterLot(Lot lot);
	void    ajouterLot(int numCDE, String typologie, String affaire,
	                   int nbPieces, double cadence, int valeurVente,
	                   String statut, String statutEchant,
	                   String semaine, int priorite,
	                   String lotACharge, String emplacement,
	                   boolean sousDouane, boolean machine,
	                   String dateReception,
	                   String datePaiement, String commentaire);
	void    supprimerLot(Lot lot);
	void    exportNewLot();

	boolean isAccesPAM();

	// ── Modification lots — signature DE BASE (DialogEditLot, inchangée) ──
	/**
	 * Modifie les champs administratifs d'un lot.
	 * Appelé par DialogEditLot. Ne touche PAS aux champs logistiques.
	 */
	void    modifierLot(Lot lot,
	                    String typologie, String affaire,
	                    int nbPieces, double cadence, int valeurVente,
	                    String statut, String statutEchant,
	                    String semaine, int priorite,
	                    String lotACharge, String emplacement,
	                    boolean sousDouane, boolean machine, String dateReception,
	                    String datePaiement, String commentaire);

	// ── Modification lots — signature COMPLÈTE (CarteLot) ─────────────────
	/**
	 * Modifie TOUS les champs d'un lot : administratifs + logistiques.
	 * Appelé exclusivement par CarteLot.actionPerformed().
	 *
	 * Champs logistiques ajoutés :
	 *   formatCarton, collisage, nbPers, distribution,
	 *   cadenceReel, poucentrecupCartonFour, methode (nom)
	 *
	 * L'ordre des paramètres correspond exactement à l'appel existant
	 * dans CarteLot.actionPerformed().
	 */
	void    modifierLotComplet(Lot lot,
	                           String typologie, String affaire,
	                           String semaine, String emplacement,
	                           String dateReception, String datePaiement,
	                           int nbPieces, double prixUnitaire, int valeurVente,
	                           double cadence, double heures,
	                           String lotACharge,
	                           String statut, String statutEchant,
	                           boolean sousDouane, boolean machine,
	                           String commentaire,
	                           String formatCarton, int collisage, int nbPers,
	                           String distribution, double cadenceReel,
	                           int poucentrecupCartonFour, String methode);

	void    modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                      boolean sortieEtiq, boolean tri, boolean finit);
	void    marquerLotTermine(Lot lot);
	void    commencerLot(Lot lot);
	void    annulerLot(Lot lot);

	// ── Affectation ───────────────────────────────────────────────────────
	boolean affecterLot(Lot lot, Societe societe, Ace ace);
	void    desaffecterLot(Lot lot);

	// ── Sociétés / ACE ────────────────────────────────────────────────────
	void    modifierSociete(Societe soc, String nom, String ce,
	                        int totalHeuresCE, int effectif);
	boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces);
	void    nouvelleHeurePourSociete(int semaine);
	void    semaineSup();

	// ── Suivi production ──────────────────────────────────────────────────
	void    mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart);

	// ── Recherche ─────────────────────────────────────────────────────────
	Societe        getSocieteDuLot(Lot lot);
	Ace            getAceDuLot(Lot lot);
	ArrayList<Ace> getTouteAces();

	// ── Fiche de route ────────────────────────────────────────────────────
	FicheRoute genererFicheRoute(Societe societe);

	// ── Sauvegarde / Chargement ───────────────────────────────────────────
	void sauvegarderDonnees(String cheminDossier, String semaine);
	void chargerDonnees(String chemin) throws IOException;
	void nouveaux();
	void autoSauvegarde();

	// ── Synchronisation serveur (IHM réseau) ───────────────────────────────
	/**
	 * Démarre un thread de polling qui interroge régulièrement le serveur
	 * pour détecter les modifications (toutes les 5 secondes).
	 * Si une modification est détectée, rafraîchit l'IHM.
	 * En cas d'erreur de communication, affiche un avertissement à l'utilisateur
	 * après 3 échecs consécutifs (une seule fois, pas en boucle).
	 * En mode désynchronisé (PAM uniquement), le polling est stoppé et un message
	 * informe l'utilisateur que les modifications ne seront pas synchronisées.
	 */
	boolean isPollingActif();
}