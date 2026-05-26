package app;

import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface du contrôleur — implémentée par Controleur (solo) et ControleurClient (réseau).
 *
 * STRATÉGIE pour les champs logistiques de CarteLot :
 * ────────────────────────────────────────────────────
 * On ajoute UNE NOUVELLE MÉTHODE modifierLotComplet() distincte.
 * L'ancienne modifierLot() n'est PAS modifiée → DialogEditLot, PanelAffectation,
 * DialogAjoutLot et tout autre code IHM existant continuent de compiler sans
 * aucun changement.
 *
 * CarteLot appelle modifierLotComplet() avec tous les champs logistiques.
 * DialogEditLot continue d'appeler modifierLot() avec les champs de base.
 */
public interface IControleur
{
	// ── Données ───────────────────────────────────────────────────────────
	ArrayList<Societe> getSocietes();
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
}