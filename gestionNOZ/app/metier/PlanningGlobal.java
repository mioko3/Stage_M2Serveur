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
 * Couche métier pure — aucune dépendance Swing.
 *
 * Toute interaction avec l'utilisateur (JFileChooser, JOptionPane, System.exit)
 * a été retirée. PlanningGlobal reçoit des chemins de fichiers en paramètre,
 * lance des exceptions en cas d'erreur, et laisse le Contrôleur décider
 * comment réagir (afficher un dialogue, logger, quitter…).
 */
public class PlanningGlobal
{
	public static boolean estHeureSup;

	private ArrayList<Societe>    societes;
	private ArrayList<Lot>        lots;
	private ArrayList<FicheRoute> ficheRoute;

	// ── Constructeur ──────────────────────────────────────────────────────

	/**
	 * Constructeur vide : le planning est initialisé à vide.
	 * C'est le Contrôleur qui appellera chargerDepuisExcel() ou chargerDepuisJson()
	 * selon le choix de l'utilisateur.
	 */
	public PlanningGlobal()
	{
		this.societes   = new ArrayList<>();
		this.lots       = new ArrayList<>();
		this.ficheRoute = new ArrayList<>();
		ExcelReader.donnerPlanningGlobal(this);
	}

	// ── Chargement des données ────────────────────────────────────────────

	/**
	 * Charge les lots depuis un fichier Excel (XLSX/XLSM).
	 * Lance une IOException si le fichier est illisible.
	 *
	 * @param cheminXlsx chemin absolu vers le fichier Excel
	 * @param cheminSocietes chemin vers le JSON des sociétés
	 * @param semaine numéro de semaine (pour les heures ACE)
	 * @param cheminXlsxHeures chemin vers le fichier Excel des heures (peut être le même)
	 */
	public void chargerDepuisExcel(String cheminXlsx, String cheminSocietes,
	                               int semaine, String cheminXlsxHeures) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminXlsx);
		this.societes = ExcelReader.lireSocietes(cheminSocietes);
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsxHeures, this.societes, semaine);
	}

	/**
	 * Charge les lots et sociétés depuis les fichiers JSON de secours.
	 *
	 * @param cheminLotsJson    chemin vers lots.json
	 * @param cheminSocietesJson chemin vers societes.json
	 */
	public void chargerDepuisJson(String cheminLotsJson, String cheminSocietesJson) throws IOException
	{
		this.lots     = ExcelReader.lireLots(cheminLotsJson);
		this.societes = ExcelReader.lireSocietes(cheminSocietesJson);
	}

	/**
	 * Importe de nouveaux lots depuis un fichier Excel et les ajoute
	 * à la liste existante (sans effacer les lots déjà présents).
	 *
	 * @param cheminXlsx chemin absolu vers le fichier Excel
	 */
	public void importerNouveauxLots(String cheminXlsx) throws IOException
	{
		ArrayList<Lot> nouveaux = ExcelReader.lireLots(cheminXlsx);
		for (Lot l : this.lots)
			for (Lot ln : nouveaux)
				if (!l.equals(ln)) this.lots.add(ln);
	}

	/**
	 * Met à jour les heures disponibles des sociétés depuis un fichier Excel.
	 *
	 * @param cheminXlsx chemin absolu vers le fichier Excel
	 * @param semaine    numéro de semaine à lire
	 */
	public void mettreAJourHeuresSocietes(String cheminXlsx, int semaine) throws IOException
	{
		ExcelReader.ajouterHeuresDepuisExcel(cheminXlsx, this.societes, semaine);
	}

	public void nouveau()
	{
		this.lots = new ArrayList<Lot>();
		this.ficheRoute = new ArrayList<>();
	}

	// ── Méthodes IHM (inchangées, pas de Swing) ───────────────────────────

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

	public void modifierLotMethodeDistribution(Lot lot, String typologie, String lotACharge)
	{
		lot.setMethode(typologie  != null ? typologie  : "");
		lot.setLotACharge(lotACharge != null ? lotACharge : "");
	}

	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{
		Phase phase = lot.getPhase();
		phase.setPreTri      (preTri);
		phase.setSurPiste    (surPiste);
		phase.setSortieEtiq  (sortieEtiq);
		phase.setTri         (tri);
		phase.setFinit       (finit);
		if (finit)
		{
			LocalDateTime now = LocalDateTime.now();
			DateTimeFormatter formatter =
				DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
			String formatted = now.format(formatter);
			lot.setdateFin(formatted);
		}
		else { lot.setdateFin("");}
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

	public void commencerLot(Lot l)
	{
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
		String formatted = now.format(formatter);
		l.setDateDebut(formatted);
	}
	public void annulerLot(Lot l)
	{
		l.setDateDebut("");
		l.setdateFin("");
		l.setdateFinT("");  // ← AJOUTER cette ligne si absente
		l.getPhase().setPreTri(false);
		l.getPhase().setSurPiste(false);
		l.getPhase().setSortieEtiq(false);
		l.getPhase().setTri(false);
		l.getPhase().setFinit(false);
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

	/**
	 * @return true si succès, false si la société n'a pas assez d'heures
	 * ou que la société et l'ACE sont déjà affectés au lot. Dans ce cas,
	 * aucune modification n'est appliquée.
	 */
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

	public void ajouterLot(Lot l)
	{
		this.lots.add(l);
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

	
	public ArrayList<Societe> getSocietes()       { return societes; }
	public ArrayList<Lot>     getLots()           { return lots;     }
	public ArrayList<Ace>     getTouteAces()
	{
		ArrayList<Ace> tout = new ArrayList<>();
		for (Societe s : societes)
			for (Ace a : s.getAces())
				tout.add(a);
		return tout;
	}

	public void setSocietes(ArrayList<Societe> societes) { this.societes = societes; }
	public void setLots    (ArrayList<Lot>     lots)     { this.lots	 = lots;     }
	public void setestHeureSup()
	{
		estHeureSup = !estHeureSup;
		for (Lot l : this.lots)
		{
			l.calculDateFinThéorique();
		}
	}
}
