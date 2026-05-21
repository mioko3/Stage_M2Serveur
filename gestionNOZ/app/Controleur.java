package app;

import app.ihm.FenetrePrincipale;
import app.ihm.login.FenetreLogin;
import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Contrôleur mode SOLO — un seul PC.
 * Implémente IControleur pour être interchangeable avec ControleurClient.
 * Copie exacte de livraison/Controleur.java avec "implements IControleur" ajouté.
 */
public class Controleur implements IControleur
{
	private FenetrePrincipale  fenetre;
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;
	private String             cheminLotsJson;
	private String             cheminSocietesJson;

	private static final String LOTS_JSON     = "app/data/courutilisation/lots.json";
	private static final String SOCIETES_JSON = "app/data/courutilisation/societes.json";
	private static final String SOCIETES_REF  = "app/data/pastouche/societes.json";

	// ── Constructeur solo ─────────────────────────────────────────────────
	public Controleur()
	{
		this.metier             = new PlanningGlobal();
		this.savDonnees         = new DonneesSauvegarder();
		this.cheminLotsJson     = LOTS_JSON;
		this.cheminSocietesJson = SOCIETES_JSON;
		SwingUtilities.invokeLater(() -> new FenetreLogin(this));
	}

	// ── Appelé par FenetreLogin ───────────────────────────────────────────
	public void lancerApp(String login, boolean utiliserExcel)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (utiliserExcel) chargerDepuisExcelInteractif();
			else               chargerFallbackJson();
			this.fenetre = new FenetrePrincipale(this);
		});
	}

	// ── Chargement ───────────────────────────────────────────────────────
	private void chargerDepuisExcelInteractif()
	{
		String xlsx = demanderFichierExcel("Sélectionner le fichier des lots (XLSX / XLSM)");
		if (xlsx != null)
		{
			try
			{
				ArrayList<Lot> tempLots = app.metier.collecte.ExcelReader.lireLots(xlsx);
				int semaine = 0;
				if (!tempLots.isEmpty())
				{
					String sem = tempLots.get(0).getSemaine();
					try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
					catch (NumberFormatException ignored) {}
				}
				String xlsxHeures = demanderFichierExcel("Sélectionner le fichier des heures ACE");
				if (xlsxHeures == null) xlsxHeures = xlsx;
				metier.chargerDepuisExcel(xlsx, SOCIETES_REF, semaine, xlsxHeures);
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(null, "Erreur Excel :\n" + e.getMessage() + "\n\nRetour au JSON.", "Erreur", JOptionPane.WARNING_MESSAGE);
				chargerFallbackJson();
			}
		}
		else chargerFallbackJson();
	}

	private void chargerFallbackJson()
	{
		if (!new File(LOTS_JSON).exists()) { System.out.println("[Controleur] Démarrage à vide."); return; }
		try { metier.chargerDepuisJson(LOTS_JSON, SOCIETES_JSON); }
		catch (IOException e) { JOptionPane.showMessageDialog(null, "Impossible de charger :\n" + e.getMessage(), "Erreur", JOptionPane.WARNING_MESSAGE); }
	}

	private String demanderFichierExcel(String titre)
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(titre);
		fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		File def = new File("app/data");
		if (def.exists()) fc.setCurrentDirectory(def);
		return fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().getAbsolutePath() : null;
	}

	// ── IControleur : Données ─────────────────────────────────────────────
	@Override public ArrayList<Societe> getSocietes() { return metier.getSocietes(); }
	@Override public ArrayList<Lot>     getLots()     { return metier.getLots();     }

	// ── IControleur : Lots ────────────────────────────────────────────────
	@Override public void ajouterLot(Lot lot) { metier.ajouterLot(lot); autoSauvegarderLots(); }

	@Override
	public void ajouterLot(int numCDE, String typologie, String affaire,
	                       int nbPieces, double cadence, int valeurVente,
	                       String statut, String statutEchant, String semaine, int priorite,
	                       String lotACharge, String emplacement, boolean sousDouane,
	                       String dateReception, String datePaiement, String commentaire)
	{
		metier.ajouterLot(numCDE, typologie, affaire, nbPieces, cadence, valeurVente,
		                  statut, statutEchant, semaine, priorite, lotACharge, emplacement,
		                  sousDouane, dateReception, datePaiement, commentaire);
		autoSauvegarderLots();
	}

	@Override public void supprimerLot(Lot lot) { metier.supprimerLot(lot); autoSauvegarderLots(); }

	@Override
	public void exportNewLot()
	{
		String xlsx = demanderFichierExcel("Sélectionner le fichier des nouveaux lots");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Import lots", JOptionPane.INFORMATION_MESSAGE); return; }
		try { metier.importerNouveauxLots(xlsx); autoSauvegarderLots(); }
		catch (IOException e) { JOptionPane.showMessageDialog(null, "Erreur : " + e.getMessage(), "Import lots", JOptionPane.ERROR_MESSAGE); }
	}

	// ── IControleur : Affectation ─────────────────────────────────────────
	@Override public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{ boolean ok = metier.affecterLot(lot, societe, ace); if (ok) autoSauvegarderSocietes(); return ok; }

	@Override public void desaffecterLot(Lot lot)
	{ metier.desaffecterLot(lot); autoSauvegarderSocietes(); }

	// ── IControleur : Modification lots ──────────────────────────────────
	@Override
	public void modifierLot(Lot lot, String typologie, String affaire,
	                        int nbPieces, double cadence, int valeurVente,
	                        String statut, String statutEchant, String semaine, int priorite,
	                        String lotACharge, String emplacement, boolean sousDouane,
	                        String dateReception, String datePaiement, String commentaire)
	{
		metier.modifierLot(lot, typologie, affaire, nbPieces, cadence, valeurVente,
		                   statut, statutEchant, semaine, priorite, lotACharge, emplacement,
		                   sousDouane, dateReception, datePaiement, commentaire);
		autoSauvegarderLots();
	}

	public void modifierLotMethodeDistribution(Lot lot, String methode, String lotACharge)
	{ metier.modifierLotMethodeDistribution(lot, methode, lotACharge); autoSauvegarderLots(); }

	@Override public void modifierPhase(Lot lot, boolean preTri, boolean surPiste, boolean sortieEtiq, boolean tri, boolean finit)
	{ metier.modifierPhase(lot, preTri, surPiste, sortieEtiq, tri, finit); autoSauvegarderLots(); }

	@Override public void marquerLotTermine(Lot lot) { metier.marquerLotTermine(lot); autoSauvegarderLots(); }
	@Override public void commencerLot(Lot l) { metier.commencerLot(l); }
	@Override public void annulerLot  (Lot l) { metier.annulerLot(l);   }

	// ── IControleur : Sociétés ────────────────────────────────────────────
	@Override public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif)
	{ metier.modifierSociete(soc, nom, ce, totalHeuresCE, effectif); autoSauvegarderSocietes(); }

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces)
	{
		List<Ace> aces = soc.getAces();
		for (int i = nouvellesAces.size(); i < aces.size(); i++)
			if (!aces.get(i).getLots().isEmpty()) return false;
		int min = Math.min(aces.size(), nouvellesAces.size());
		for (int i = 0; i < min; i++)
			metier.modifierAce(aces.get(i), nouvellesAces.get(i).getNom(),
				nouvellesAces.get(i).getNbPers(), nouvellesAces.get(i).getEffectifActuel());
		for (int i = aces.size()-1; i >= nouvellesAces.size(); i--) aces.remove(i);
		for (int i = min; i < nouvellesAces.size(); i++) {
			Ace n = nouvellesAces.get(i);
			aces.add(new Ace(n.getNom(), n.getNbPers(), n.getEffectifActuel()));
		}
		autoSauvegarderSocietes();
		return true;
	}

	@Override
	public void nouvelleHeurePourSociete(int semaine)
	{
		String xlsx = demanderFichierExcel("Sélectionner le fichier des heures ACE");
		if (xlsx == null) { JOptionPane.showMessageDialog(null, "Aucun fichier.", "Nouvelle heure", JOptionPane.INFORMATION_MESSAGE); return; }
		try { metier.mettreAJourHeuresSocietes(xlsx, semaine); autoSauvegarderSocietes(); JOptionPane.showMessageDialog(null, "Heures ajoutées !", "OK", JOptionPane.INFORMATION_MESSAGE); }
		catch (IOException e) { JOptionPane.showMessageDialog(null, "Erreur :\n" + e.getMessage(), "Nouvelle heure", JOptionPane.ERROR_MESSAGE); }
	}

	@Override public void semaineSup() { metier.setestHeureSup(); }

	// ── IControleur : Suivi prod ──────────────────────────────────────────
	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		if (nbPieceEtiq <= lot.getNbPieces() && nbPieceRepart <= lot.getNbPieces()) {
			lot.getSuivieProd().setNbPieceEtiq  (nbPieceEtiq);
			lot.getSuivieProd().setNbPieceRepart(nbPieceRepart);
		}
		autoSauvegarderLots();
	}

	// ── IControleur : Recherche ───────────────────────────────────────────
	@Override public Societe        getSocieteDuLot(Lot lot) { return metier.getSocieteDuLot(lot); }
	@Override public Ace            getAceDuLot    (Lot lot) { return metier.getAceDuLot(lot);     }
	@Override public ArrayList<Ace> getTouteAces   ()        { return metier.getTouteAces();        }

	// ── IControleur : Fiche de route ─────────────────────────────────────
	@Override public FicheRoute genererFicheRoute(Societe societe) { return metier.genererFicheRoute(societe); }

	// ── IControleur : Sauvegarde / Chargement ────────────────────────────
	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try {
			String dossier = cheminDossier + "/S" + semaine;
			if (!Files.exists(Paths.get(dossier))) Files.createDirectories(Paths.get(dossier));
			Files.copy(Paths.get(cheminLotsJson),     Paths.get(dossier + "/lots.json"),     java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(Paths.get(cheminSocietesJson), Paths.get(dossier + "/societes.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			this.cheminLotsJson     = dossier + "/lots.json";
			this.cheminSocietesJson = dossier + "/societes.json";
		}
		catch (IOException e) { System.err.println("[Controleur] Erreur sauvegarde : " + e.getMessage()); }
	}

	@Override
	public void chargerDonnees(String chemin) throws IOException
	{
		savDonnees.charger(metier, chemin);
		this.cheminLotsJson     = chemin + "/lots.json";
		this.cheminSocietesJson = chemin + "/societes.json";
		if (fenetre != null) fenetre.rafraichirTout();
	}

	@Override
	public void nouveaux()
	{
		this.cheminLotsJson     = LOTS_JSON;
		this.cheminSocietesJson = SOCIETES_JSON;
		metier.nouveau();
		chargerDepuisExcelInteractif();
		autoSauvegarderLots();
		autoSauvegarderSocietes();
	}

	@Override public void autoSauvegarde() { autoSauvegarderLots(); autoSauvegarderSocietes(); }

	private void autoSauvegarderLots()
	{
		if (cheminLotsJson == null) return;
		try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); }
		catch (Exception e) { System.err.println("[AutoSave Lots] " + e.getMessage()); }
	}

	private void autoSauvegarderSocietes()
	{
		if (cheminSocietesJson == null) return;
		try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
		catch (Exception e) { System.err.println("[AutoSave Soc] " + e.getMessage()); }
	}

	// ── Main ──────────────────────────────────────────────────────────────
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(Controleur::new);
	}
}
