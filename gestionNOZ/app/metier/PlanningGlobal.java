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

/**
 * Couche métier pure — AUCUNE dépendance Swing.
 *
 * Toute interaction avec l'utilisateur (JFileChooser, JOptionPane, System.exit)
 * est gérée par le Contrôleur, pas ici.
 */
public class PlanningGlobal
{
	public static boolean estHeureSup;

	private ArrayList<Societe>    societes;
	private ArrayList<Lot>        lots;
	private ArrayList<FicheRoute> ficheRoute;

	// ── Constructeur ──────────────────────────────────────────────────────

	public PlanningGlobal()
	{
		this.societes   = new ArrayList<>();
		this.lots       = new ArrayList<>();
		this.ficheRoute = new ArrayList<>();
		ExcelReader.donnerPlanningGlobal(this);
	}

	// ── Chargement des données ────────────────────────────────────────────

	public void chargerDepuisExcel(String cheminXlsx, String cheminSocietes,
	                               int semaine, String cheminXlsxHeures) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminXlsx);
		this.societes = ExcelReader.lireSocietes(cheminSocietes);
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsxHeures, this.societes, semaine);
	}

	public void chargerDepuisJson(String cheminLotsJson, String cheminSocietesJson) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminLotsJson);
		this.societes = ExcelReader.lireSocietes(cheminSocietesJson);
	}

	public void importerNouveauxLots(String cheminXlsx) throws IOException
	{
		ArrayList<Lot> nouveaux = ExcelReader.lireLots(cheminXlsx);
		for (Lot ln : nouveaux)
		{
			boolean deja = false;
			for (Lot l : this.lots)
				if (l.getNumCDE() == ln.getNumCDE()) { deja = true; break; }
			if (!deja) this.lots.add(ln);
		}
	}

	public void mettreAJourHeuresSocietes(String cheminXlsx, int semaine) throws IOException
	{
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsx, this.societes, semaine);
	}

	public void nouveau()
	{
		this.lots       = new ArrayList<>();
		this.ficheRoute = new ArrayList<>();
	}

	// ── Modification lots ─────────────────────────────────────────────────

	public void modifierLot(Lot lot, String typologie, String affaire,
	                        int nbPieces, double cadence, int valeurVente,
	                        String statut, String statutEchant,
	                        String semaine, int priorite,
	                        String lotACharge, String emplacement,
	                        boolean sousDouane, String dateReception,
	                        String datePaiement, String commentaire)
	{
		int heuresAvant = (int) Math.ceil(lot.getHeures());

		lot.setTypologie    (typologie);
		lot.setAffaire      (affaire);
		lot.setNbPieces     (nbPieces);
		lot.setCadence      (cadence);
		lot.recalculerHeures();
		lot.setValeurVente  (valeurVente);
		lot.setStatut       (statut);
		lot.setStatutEchant (statutEchant);
		lot.setSemaine      (semaine);
		lot.setPriorite     (priorite);
		lot.setLotACharge   (lotACharge);
		lot.setEmplacement  (emplacement);
		lot.setEstSousDouane(sousDouane);
		lot.setDateReception(dateReception);
		lot.setDatePaiement (datePaiement);
		lot.setCommentaire  (commentaire);

		int heuresApres = (int) Math.ceil(lot.getHeures());
		int delta = heuresAvant - heuresApres;
		if (delta != 0)
		{
			Societe soc = getSocieteDuLot(lot);
			if (soc != null)
				soc.setTotalHeuresCE(soc.getTotalHeuresCE() + delta);
		}
	}

	public void modifierLotMethodeDistribution(Lot lot, String methode, String lotACharge)
	{
		lot.setMethode   (methode    != null ? methode    : "");
		lot.setLotACharge(lotACharge != null ? lotACharge : "");
	}

	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{
		Phase phase = lot.getPhase();
		phase.setPreTri     (preTri);
		phase.setSurPiste   (surPiste);
		phase.setSortieEtiq (sortieEtiq);
		phase.setTri        (tri);
		phase.setFinit      (finit);
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

	public void commencerLot(Lot l)
	{
		String formatted = LocalDateTime.now()
			.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
		l.setDateDebut(formatted);
	}

	public void annulerLot(Lot l)
	{
		l.setDateDebut("");
		l.setdateFin("");
		l.getPhase().setPreTri     (false);
		l.getPhase().setSurPiste   (false);
		l.getPhase().setSortieEtiq (false);
		l.getPhase().setTri        (false);
		l.getPhase().setFinit      (false);
	}

	// ── Modification sociétés / ACE ───────────────────────────────────────

	public void modifierSociete(Societe soc, String nom, String ce,
	                            int totalHeuresCE, int effectif)
	{
		soc.setNom           (nom);
		soc.setCe            (ce);
		soc.setTotalHeuresCE (totalHeuresCE);
		soc.setEffectifTotal (effectif);
	}

	public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
	{
		ace.setNom           (nom);
		ace.setNbPers        (nbPers);
		ace.setEffectifActuel(effectif);
	}

	// ── Recherche ─────────────────────────────────────────────────────────

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

	// ── Affectation ───────────────────────────────────────────────────────

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

	// ── Ajout / Suppression lots ──────────────────────────────────────────

	public void ajouterLot(Lot lot) { lots.add(lot); }

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

	public void supprimerLot(Lot lot) { lots.remove(lot); }

	// ── Fiche de route ────────────────────────────────────────────────────

	public FicheRoute genererFicheRoute(Societe societe)
	{
		for (FicheRoute fr : ficheRoute)
			if (fr.getSociete() == societe) return fr;
		FicheRoute fr2 = new FicheRoute(societe);
		this.ficheRoute.add(fr2);
		return fr2;
	}

	// ── Getters / Setters ─────────────────────────────────────────────────

	public ArrayList<Societe> getSocietes() { return societes; }
	public ArrayList<Lot>     getLots()     { return lots;     }

	public ArrayList<Ace> getTouteAces()
	{
		ArrayList<Ace> tout = new ArrayList<>();
		for (Societe s : societes)
			for (Ace a : s.getAces())
				tout.add(a);
		return tout;
	}

	public void setestHeureSup()
	{
		estHeureSup = !estHeureSup;
		for (Lot l : this.lots)
			l.calculDateFinThéorique();
	}
}