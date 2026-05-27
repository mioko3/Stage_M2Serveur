package app.metier;

import app.metier.collecte.ExcelReader;
import app.metier.ficheroute.FicheRoute;
import app.metier.ficheroute.Phase;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class PlanningGlobal
{
	public static boolean estHeureSup;

	private ArrayList<Societe>    societes;
	private ArrayList<Lot>        lots;
	private ArrayList<FicheRoute> ficheRoute;

	public PlanningGlobal()
	{
		this.societes   = new ArrayList<>();
		this.lots       = new ArrayList<>();
		this.ficheRoute = new ArrayList<>();
	}

	// ── Chargement ───────────────────────────────────────────────────────

	public void chargerDepuisExcel(String cheminXlsx, String cheminSocietes,
	                               int semaine, String cheminXlsxHeures) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminXlsx);
		this.societes = ExcelReader.lireSocietes(cheminSocietes, this.lots);
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsxHeures, this.societes, semaine);
	}

	public void chargerDepuisJson(String cheminLotsJson, String cheminSocietesJson) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminLotsJson);
		this.societes = ExcelReader.lireSocietes(cheminSocietesJson, this.lots);
	}

	public void importerNouveauxLots(String cheminXlsx) throws IOException
	{
		ArrayList<Lot> nouveaux = ExcelReader.lireLots(cheminXlsx);
		for (Lot l : this.lots)
			for (Lot ln : nouveaux)
				if (!l.equals(ln)) this.lots.add(ln);
	}

	public void mettreAJourHeuresSocietes(String cheminXlsx, int semaine) throws IOException
	{
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsx, this.societes, semaine);
	}

	public void nouveau()
	{
		this.lots      = new ArrayList<>();
		this.ficheRoute = new ArrayList<>();
	}

	// ── Modification lots — champs de BASE (DialogEditLot) ───────────────

	/**
	 * Modifie les champs administratifs d'un lot.
	 * Appelé depuis Controleur.modifierLot() (DialogEditLot).
	 * Ne touche PAS aux champs logistiques (formatCarton, collisage, etc.)
	 */
	public void modifierLot(Lot lot,
	                        String typologie, String affaire,
	                        int nbPieces, double cadence, int valeurVente,
	                        String statut, String statutEchant,
	                        String semaine, int priorite,
	                        String lotACharge, String emplacement,
	                        boolean sousDouane, String dateReception,
	                        String datePaiement, String commentaire)
	{
		int heuresAvant = (int) Math.ceil(lot.getHeures());

		lot.setTypologie    (typologie    != null ? typologie    : "");
		lot.setAffaire      (affaire      != null ? affaire      : "");
		lot.setNbPieces     (nbPieces);
		lot.setCadence      (cadence);
		lot.recalculerHeures();
		lot.setValeurVente  (valeurVente);
		lot.setStatut       (statut       != null ? statut       : "");
		lot.setStatutEchant (statutEchant != null ? statutEchant : "");
		lot.setSemaine      (semaine      != null ? semaine      : "");
		lot.setPriorite     (priorite);
		lot.setLotACharge   (lotACharge   != null ? lotACharge   : "");
		lot.setEmplacement  (emplacement  != null ? emplacement  : "");
		lot.setEstSousDouane(sousDouane);
		lot.setDateReception(dateReception != null ? dateReception : "");
		lot.setDatePaiement (datePaiement  != null ? datePaiement  : "");
		lot.setCommentaire  (commentaire   != null ? commentaire   : "");

		int heuresApres = (int) Math.ceil(lot.getHeures());
		int delta = heuresAvant - heuresApres;
		if (delta != 0)
		{
			Societe soc = getSocieteDuLot(lot);
			if (soc != null) soc.setTotalHeuresCE(soc.getTotalHeuresCE() + delta);
		}
	}

	// ── Modification lots — champs COMPLETS (CarteLot) ───────────────────

	/**
	 * Modifie TOUS les champs d'un lot : administratifs + logistiques.
	 * Appelé depuis Controleur.modifierLotComplet() (CarteLot).
	 *
	 * Champs logistiques : formatCarton, collisage, nbPers, distribution,
	 *                      cadenceReel, poucentrecupCartonFour, methode
	 */
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
		int heuresAvant = (int) Math.ceil(lot.getHeures());

		// Champs de base
		lot.setTypologie    (typologie    != null ? typologie    : "");
		lot.setAffaire      (affaire      != null ? affaire      : "");
		lot.setSemaine      (semaine      != null ? semaine      : "");
		lot.setEmplacement  (emplacement  != null ? emplacement  : "");
		lot.setDateReception(dateReception != null ? dateReception : "");
		lot.setDatePaiement (datePaiement  != null ? datePaiement  : "");
		lot.setNbPieces     (nbPieces);
		lot.setValeurVente  (valeurVente);
		lot.setCadence      (cadence);
		lot.recalculerHeures();
		lot.setLotACharge   (lotACharge   != null ? lotACharge   : "");
		lot.setStatut       (statut       != null ? statut       : "");
		lot.setStatutEchant (statutEchant != null ? statutEchant : "");
		lot.setEstSousDouane(sousDouane);
		lot.setCommentaire  (commentaire   != null ? commentaire  : "");

		// Champs logistiques
		lot.setFormatCarton(formatCarton != null ? formatCarton : "");
		lot.setCollisage   (collisage);
		lot.setNbPers      (nbPers);
		lot.setDistribution(distribution != null ? distribution : "");
		if (cadenceReel > 0) lot.setCadenceReel(cadenceReel);
		if (poucentrecupCartonFour >= 0 && poucentrecupCartonFour <= 100)
			lot.setPoucentrecupCartonFour(poucentrecupCartonFour);
		lot.setMethode(methode != null ? methode : "");
		lot.recalculNbPalette();

		// Répercussion delta heures sur la société
		int heuresApres = (int) Math.ceil(lot.getHeures());
		int delta = heuresAvant - heuresApres;
		if (delta != 0)
		{
			Societe soc = getSocieteDuLot(lot);
			if (soc != null) soc.setTotalHeuresCE(soc.getTotalHeuresCE() + delta);
		}
	}

	// ─────────────────────────────────────────────────────────────────────
	// (reste inchangé : modifierPhase, marquerLotTermine, modifierSociete,
	//  modifierAce, commencerLot, annulerLot, getSocieteDuLot, getAceDuLot,
	//  affecterLot, desaffecterLot, ajouterLot×2, supprimerLot,
	//  genererFicheRoute, getSocietes, getLots, setSocietes, setLots,
	//  setestHeureSup, getTouteAces, modifierLotMethodeDistribution)
	// ─────────────────────────────────────────────────────────────────────

	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{
		Phase phase = lot.getPhase();
		phase.setPreTri    (preTri);
		phase.setSurPiste  (surPiste);
		phase.setSortieEtiq(sortieEtiq);
		phase.setTri       (tri);
		phase.setFinit     (finit);
		if (finit)
		{
			String formatted = LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
			lot.setdateFin(formatted);
		}
		else { lot.setdateFin(""); }
	}

	public void marquerLotTermine(Lot lot)
	{
		modifierPhase(lot, true, true, true, true, true);
		lot.getSuivieProd().setNbPieceEtiq  (lot.getNbPieces());
		lot.getSuivieProd().setNbPieceRepart(lot.getNbPieces());
	}

	public void modifierSociete(Societe soc, String nom, String ce,
	                            int totalHeuresCE, int effectif)
	{
		soc.setNom          (nom);
		soc.setCe           (ce);
		soc.setTotalHeuresCE(totalHeuresCE);
		soc.setEffectifTotal(effectif);
	}

	public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
	{
		ace.setNom           (nom);
		ace.setNbPers        (nbPers);
		ace.setEffectifActuel(effectif);
	}

	public void commencerLot(Lot l)
	{
		String formatted = LocalDateTime.now()
			.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
		l.setDateDebut(formatted);
		l.calculDateFinThéorique();
	}

	public void annulerLot(Lot l)
	{
		l.setDateDebut("");
		l.setdateFin("");
		l.setdateFinT("");
		l.getPhase().setPreTri(false);
		l.getPhase().setSurPiste(false);
		l.getPhase().setSortieEtiq(false);
		l.getPhase().setTri(false);
		l.getPhase().setFinit(false);
	}

	public Societe getSocieteDuLot(Lot lot)
	{
		for (Societe s : this.societes)
			for (Lot l : s.getLots())
				if (l == lot) return s;
		return null;
	}

	public Ace getAceDuLot(Lot lot)
	{
		for (Societe s : this.societes)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l == lot) return a;
		return null;
	}

	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		Societe ancSociete = getSocieteDuLot(lot);
		Ace     ancAce     = getAceDuLot(lot);
		if (ancSociete == societe && ancAce == ace) return true;
		if (ancSociete != null)
		{
			if (ancAce != null) ancSociete.enleverLotACE(ancAce, lot);
			ancSociete.enleverLot(lot);
		}
		societe.ajouterLot(lot, ace);
		return true;
	}

	public void desaffecterLot(Lot lot)
	{
		Societe soc = getSocieteDuLot(lot);
		Ace     ace = getAceDuLot(lot);
		if (soc != null)
		{
			if (ace != null) soc.enleverLotACE(ace, lot);
			soc.enleverLot(lot);
		}
	}

	public void ajouterLot(int numCDE, String typologie, String affaire,
	                       int nbPieces, double cadence, int valeurVente,
	                       String statut, String statutEchant,
	                       String semaine, int priorite,
	                       String lotACharge, String emplacement,
	                       boolean sousDouane, String dateReception,
	                       String datePaiement, String commentaire)
	{
		Lot lot = new Lot(numCDE, nbPieces, cadence,
		                  nbPieces > 0 && cadence > 0 ? nbPieces / cadence : 0.0,
		                  valeurVente, statut, statutEchant);
		lot.setTypologie    (typologie    != null ? typologie    : "");
		lot.setAffaire      (affaire      != null ? affaire      : "");
		lot.setSemaine      (semaine      != null ? semaine      : "");
		lot.setPriorite     (priorite);
		lot.setLotACharge   (lotACharge   != null ? lotACharge   : "");
		lot.setEmplacement  (emplacement  != null ? emplacement  : "");
		lot.setEstSousDouane(sousDouane);
		lot.setDateReception(dateReception != null ? dateReception : "");
		lot.setDatePaiement (datePaiement  != null ? datePaiement  : "");
		lot.setCommentaire  (commentaire   != null ? commentaire   : "");
		lots.add(lot);
	}

	public void ajouterLot(Lot l) { this.lots.add(l); }

	public void supprimerLot(Lot lot) { lots.remove(lot); }

	public FicheRoute genererFicheRoute(Societe societe)
	{
		for (FicheRoute fr : ficheRoute)
			if (fr.getSociete() == societe) return fr;
		FicheRoute fr2 = new FicheRoute(societe);
		this.ficheRoute.add(fr2);
		return fr2;
	}

	public void modifierLotMethodeDistribution(Lot lot, String methode, String lotACharge)
	{
		lot.setMethode   (methode    != null ? methode    : "");
		lot.setLotACharge(lotACharge != null ? lotACharge : "");
	}

	public ArrayList<Societe> getSocietes()    { return societes; }
	public ArrayList<Lot>     getLots()        { return lots;     }
	public ArrayList<Ace>     getTouteAces()
	{
		ArrayList<Ace> tout = new ArrayList<>();
		for (Societe s : societes) for (Ace a : s.getAces()) tout.add(a);
		return tout;
	}
	public void setSocietes(ArrayList<Societe> societes) { this.societes = societes; }
	public void setLots    (ArrayList<Lot>     lots)     { this.lots     = lots;     }
	public void setestHeureSup()
	{
		estHeureSup = !estHeureSup;
		for (Lot l : this.lots) l.calculDateFinThéorique();
	}
}