package app;

import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
	                   boolean sousDouane, String dateReception,
	                   String datePaiement, String commentaire);
	void    supprimerLot(Lot lot);
	void    exportNewLot();

	// ── Modification lots ─────────────────────────────────────────────────
	void    modifierLot(Lot lot, String typologie, String affaire,
	                    int nbPieces, double cadence, int valeurVente,
	                    String statut, String statutEchant,
	                    String semaine, int priorite,
	                    String lotACharge, String emplacement,
	                    boolean sousDouane, String dateReception,
	                    String datePaiement, String commentaire);
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
