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
import java.util.Set;

/**
 * Modèle métier central du planning.
 *
 * Contient les listes de sociétés, lots et fiches de route, ainsi que
 * les méthodes de chargement et de mise à jour des données.
 */
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

		Set<Integer> dejaPresentsCDE = new java.util.HashSet<>();
		for (Lot l : this.lots)
			dejaPresentsCDE.add(l.getNumCDE());

		for (Lot ln : nouveaux)
		{
			if (!dejaPresentsCDE.contains(ln.getNumCDE()))
			{
				this.lots.add(ln);
				dejaPresentsCDE.add(ln.getNumCDE()); // évite les doublons dans le fichier lui-même
			}
		}
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

	/**
	 * Met à jour les 5 drapeaux d'avancement de production d'un lot.
	 *
	 * Effet de bord sur {@code finit} :
	 *   • Si {@code finit = true}  → {@code lot.dateFin} = date/heure courante
	 *   • Si {@code finit = false} → {@code lot.dateFin} est effacée
	 *
	 * @param lot        le lot à mettre à jour
	 * @param preTri     pré-tri effectué
	 * @param surPiste   lot sur piste
	 * @param sortieEtiq sortie étiquetage effectuée
	 * @param tri        tri final effectué
	 * @param finit      lot entièrement terminé
	 */
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

	/**
	 * Clôture un lot en passant son statut à "MC" (Mission Clôturée).
	 *
	 * Effets :
	 *   • {@code lot.statut} = "MC"
	 *   • La date de fin réelle ({@code lot.dateFin}) est renseignée si
	 *     {@code phase.finit} est déjà à true ; sinon elle reste inchangée.
	 *
	 * ⚠️  Cette méthode ne désaffecte PAS le lot de sa société.
	 * Pour retirer un lot de la production, appeler également {@code desaffecterLot()}.
	 *
	 * @param lot le lot à clôturer
	 */
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

	/**
	 * Démarre la production d'un lot :
	 *   • {@code lot.statut} = "TC" (Travail Commencé)
	 *   • {@code lot.dateDebut} = date/heure courante (format "dd/MM/yyyy HH:mm:ss")
	 *   • {@code lot.dateFinTheorique} est recalculée depuis la nouvelle dateDebut
	 *
	 * @param lot le lot à démarrer
	 */
	public void commencerLot(Lot l)
	{
		String formatted = LocalDateTime.now()
			.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
		l.setDateDebut(formatted);
		l.calculDateFinThéorique();
	}

	/**
	 * Remet un lot en attente (annule son démarrage) :
	 *   • {@code lot.statut} = "OU" (Ouvert / en attente)
	 *   • {@code lot.dateDebut} est effacée
	 *   • {@code lot.dateFinTheorique} est effacée
	 *
	 * @param lot le lot à remettre en attente
	 */
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

	/**
	 * Retourne la société à laquelle est actuellement affecté le lot.
	 * Parcourt toutes les sociétés et leurs listes de lots.
	 *
	 * @param lot le lot recherché
	 * @return la société du lot, ou {@code null} si le lot n'est affecté nulle part
	 */
	public Societe getSocieteDuLot(Lot lot)
	{
		if (lot == null) return null;
		for (Societe s : this.societes)
			for (Lot l : s.getLots())
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return s;
		return null;
	}

	/**
	 * Retourne l'ACE auquel est affecté le lot.
	 * Parcourt toutes les sociétés → ACE → lots.
	 *
	 * @param lot le lot recherché
	 * @return l'ACE du lot, ou {@code null} si le lot n'a pas d'ACE
	 */
	public Ace getAceDuLot(Lot lot)
	{
		if (lot == null) return null;
		for (Societe s : this.societes)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return a;
		return null;
	}

	/**
	 * Affecte un lot à une société et à un ACE.
	 *
	 * Si le lot était déjà affecté à une autre société, il en est retiré
	 * (les heures sont restituées) avant d'être affecté à la nouvelle.
	 * Si le lot est déjà affecté à la même société/ACE, rien ne se passe.
	 *
	 * Effets sur les heures :
	 *   • {@code ancienneSociete.totalHeuresCE} += lot.heures (restitution)
	 *   • {@code nouvelleSociete.totalHeuresCE} -= lot.heures (décompte)
	 *
	 * @param lot     le lot à affecter
	 * @param societe la société destinataire
	 * @param ace     l'ACE responsable (peut être null)
	 * @return {@code true} si l'affectation a réussi, {@code false} sinon
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

	/**
	 * Retire un lot de sa société et de son ACE.
	 * Restitue les heures du lot à la société.
	 *
	 * Si le lot n'est affecté à aucune société, sans effet.
	 *
	 * @param lot le lot à désaffecter
	 */
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
	                       boolean sousDouane, boolean machine,
	                       String dateReception,
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
		lot.setEstMachine   (machine);
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