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
 * Contrôleur MVC — mode standalone.
 * Implémente IControleur pour être interchangeable avec ControleurClient.
 *
 * Cycle de vie :
 *   1. main()           → new Controleur()
 *   2. Controleur()     → affiche FenetreLogin (aucun chargement de données)
 *   3. FenetreLogin     → appelle ctrl.lancerApp(login, utiliserExcel)
 *   4. lancerApp()      → charge les données (Excel ou JSON) puis ouvre FenetrePrincipale
 */
public class Controleur implements IControleur
{
	private FenetrePrincipale  fenetre;
	private PlanningGlobal     metier;
	private DonneesSauvegarder savDonnees;
	private String             cheminLotsJson;
	private String             cheminSocietesJson;

	// ── Chemins par défaut ────────────────────────────────────────────────
	private static final String LOTS_JSON     = "app/data/courutilisation/lots.json";
	private static final String SOCIETES_JSON = "app/data/courutilisation/societes.json";
	private static final String SOCIETES_REF  = "app/data/pastouche/societes.json";

	// ── Constructeur ──────────────────────────────────────────────────────

	public Controleur()
	{
		this.metier             = new PlanningGlobal();
		this.savDonnees         = new DonneesSauvegarder();
		this.cheminLotsJson     = LOTS_JSON;
		this.cheminSocietesJson = SOCIETES_JSON;

		SwingUtilities.invokeLater(() -> new FenetreLogin(this));
	}

	// ── Point d'entrée appelé par FenetreLogin ────────────────────────────

	public void lancerApp(String login, boolean utiliserExcel)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (utiliserExcel)
				chargerDepuisExcelInteractif();
			else
				chargerFallbackJson();

			this.fenetre = new FenetrePrincipale(this);
		});
	}

	// ── Chargement des données ────────────────────────────────────────────

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
					try
					{
						semaine = Integer.parseInt(
							"" + sem.charAt(sem.length() - 2)
							   + sem.charAt(sem.length() - 1));
					}
					catch (NumberFormatException ignored) {}
				}

				String xlsxHeures = demanderFichierExcel(
					"Sélectionner le fichier des heures ACE (XLSX / XLSM)");
				if (xlsxHeures == null) xlsxHeures = xlsx;

				metier.chargerDepuisExcel(xlsx, SOCIETES_REF, semaine, xlsxHeures);
			}
			catch (IOException e)
			{
				JOptionPane.showMessageDialog(null,
					"Erreur lors du chargement Excel :\n" + e.getMessage()
					+ "\n\nRetour aux données JSON.",
					"Erreur de chargement", JOptionPane.WARNING_MESSAGE);
				chargerFallbackJson();
			}
		}
		else
		{
			chargerFallbackJson();
		}
	}

	private void chargerFallbackJson()
	{
		File lotsFile = new File(LOTS_JSON);
		if (!lotsFile.exists())
		{
			System.out.println("[Controleur] Aucun JSON existant, démarrage à vide.");
			return;
		}

		try
		{
			metier.chargerDepuisJson(LOTS_JSON, SOCIETES_JSON);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null,
				"Impossible de charger les données JSON :\n" + e.getMessage(),
				"Erreur", JOptionPane.WARNING_MESSAGE);
		}
	}

	private String demanderFichierExcel(String titre)
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(titre);
		chooser.setFileFilter(
			new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
		File dossierDefaut = new File("app/data");
		if (dossierDefaut.exists() && dossierDefaut.isDirectory())
			chooser.setCurrentDirectory(dossierDefaut);
		int resultat = chooser.showOpenDialog(null);
		if (resultat == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile().getAbsolutePath();
		return null;
	}

	// ── IControleur : Données ─────────────────────────────────────────────

	@Override public ArrayList<Societe> getSocietes()          { return metier.getSocietes(); }
	@Override public ArrayList<Lot>     getLots()              { return metier.getLots();     }
	@Override public String             getCheminLotsJson()    { return cheminLotsJson;       }
	@Override public String             getCheminSocietesJson(){ return cheminSocietesJson;   }

	// ── IControleur : Gestion des lots ───────────────────────────────────

	@Override
	public void ajouterLot(Lot lot)
	{ metier.ajouterLot(lot); autoSauvegarderLots(); }

	@Override
	public void ajouterLot(int numCDE, String typologie, String affaire,
	                       int nbPieces, double cadence, int valeurVente,
	                       String statut, String statutEchant,
	                       String semaine, int priorite,
	                       String lotACharge, String emplacement,
	                       boolean sousDouane, String dateReception,
	                       String datePaiement, String commentaire)
	{
		metier.ajouterLot(numCDE, typologie, affaire, nbPieces, cadence, valeurVente,
		                  statut, statutEchant, semaine, priorite,
		                  lotACharge, emplacement, sousDouane, dateReception,
		                  datePaiement, commentaire);
		autoSauvegarderLots();
	}

	@Override
	public void supprimerLot(Lot lot)
	{ metier.supprimerLot(lot); autoSauvegarderLots(); }

	@Override
	public void sauvegarderLots()
	{ autoSauvegarderLots(); }

	@Override
	public void exportNewLot()
	{
		String xlsx = demanderFichierExcel("Sélectionner le fichier des nouveaux lots");
		if (xlsx == null)
		{
			JOptionPane.showMessageDialog(null,
				"Aucun fichier sélectionné. Opération annulée.",
				"Import lots", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		try
		{
			metier.importerNouveauxLots(xlsx);
			autoSauvegarderLots();
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null,
				"Erreur lors de l'import :\n" + e.getMessage(),
				"Import lots", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ── IControleur : Affectation ─────────────────────────────────────────

	@Override
	public boolean affecterLot(Lot lot, Societe societe, Ace ace)
	{
		boolean ok = metier.affecterLot(lot, societe, ace);
		if (ok) autoSauvegarderSocietes();
		return ok;
	}

	@Override
	public void desaffecterLot(Lot lot)
	{ metier.desaffecterLot(lot); autoSauvegarderSocietes(); }

	// ── IControleur : Modification lots ──────────────────────────────────

	@Override
	public void modifierLot(Lot lot, String typologie, String affaire,
	                        int nbPieces, double cadence, int valeurVente,
	                        String statut, String statutEchant,
	                        String semaine, int priorite,
	                        String lotACharge, String emplacement,
	                        boolean sousDouane, String dateReception,
	                        String datePaiement, String commentaire)
	{
		metier.modifierLot(lot, typologie, affaire, nbPieces, cadence, valeurVente,
		                   statut, statutEchant, semaine, priorite,
		                   lotACharge, emplacement, sousDouane, dateReception,
		                   datePaiement, commentaire);
		autoSauvegarderLots();
	}

	public void modifierLotMethodeDistribution(Lot lot, String typologie, String lotACharge)
	{ metier.modifierLotMethodeDistribution(lot, typologie, lotACharge); autoSauvegarderLots(); }

	@Override
	public void modifierPhase(Lot lot, boolean preTri, boolean surPiste,
	                          boolean sortieEtiq, boolean tri, boolean finit)
	{ metier.modifierPhase(lot, preTri, surPiste, sortieEtiq, tri, finit); autoSauvegarderLots(); }

	@Override
	public void marquerLotTermine(Lot lot)
	{ metier.marquerLotTermine(lot); autoSauvegarderLots(); }

	@Override
	public void commencerLot(Lot l) { this.metier.commencerLot(l); }

	@Override
	public void annulerLot(Lot l)   { this.metier.annulerLot(l);   }

	// ── IControleur : Modification sociétés ──────────────────────────────

	@Override
	public void modifierSociete(Societe soc, String nom, String ce,
	                            int totalHeuresCE, int effectif)
	{ metier.modifierSociete(soc, nom, ce, totalHeuresCE, effectif); autoSauvegarderSocietes(); }

	@Override
	public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
	{ metier.modifierAce(ace, nom, nbPers, effectif); autoSauvegarderSocietes(); }

	@Override
	public boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces)
	{
		List<Ace> aces = soc.getAces();
		for (int i = nouvellesAces.size(); i < aces.size(); i++)
			if (!aces.get(i).getLots().isEmpty()) return false;

		int min = Math.min(aces.size(), nouvellesAces.size());
		for (int i = 0; i < min; i++)
		{
			Ace ancien  = aces.get(i);
			Ace nouveau = nouvellesAces.get(i);
			metier.modifierAce(ancien, nouveau.getNom(),
				nouveau.getNbPers(), nouveau.getEffectifActuel());
		}
		for (int i = aces.size() - 1; i >= nouvellesAces.size(); i--)
			aces.remove(i);
		for (int i = min; i < nouvellesAces.size(); i++)
		{
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
		if (xlsx == null)
		{
			JOptionPane.showMessageDialog(null,
				"Aucun fichier sélectionné. Opération annulée.",
				"Nouvelle heure", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		try
		{
			metier.mettreAJourHeuresSocietes(xlsx, semaine);
			autoSauvegarderSocietes();
			JOptionPane.showMessageDialog(null,
				"Heures ajoutées avec succès !",
				"Nouvelle heure", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null,
				"Erreur lors du chargement du fichier Excel :\n" + e.getMessage(),
				"Nouvelle heure", JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void semaineSup()
	{ this.metier.setestHeureSup(); }

	// ── IControleur : Suivi production ───────────────────────────────────

	@Override
	public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
	{
		if (nbPieceEtiq <= lot.getNbPieces() && nbPieceRepart <= lot.getNbPieces())
		{
			lot.getSuivieProd().setNbPieceEtiq  (nbPieceEtiq);
			lot.getSuivieProd().setNbPieceRepart(nbPieceRepart);
		}
		autoSauvegarderLots();
	}

	// ── IControleur : Recherche ───────────────────────────────────────────

	@Override public Societe       getSocieteDuLot(Lot lot) { return metier.getSocieteDuLot(lot); }
	@Override public Ace           getAceDuLot    (Lot lot) { return metier.getAceDuLot(lot);     }
	@Override public ArrayList<Ace> getTouteAces  ()        { return metier.getTouteAces();        }

	// ── IControleur : Fiche de route ─────────────────────────────────────

	@Override
	public FicheRoute genererFicheRoute(Societe societe)
	{ return metier.genererFicheRoute(societe); }

	// ── IControleur : Sauvegarde / Chargement ────────────────────────────

	@Override
	public void sauvegarderDonnees(String cheminDossier, String semaine)
	{
		try
		{
			String dossierSemaine = cheminDossier + "/S" + semaine;
			if (!Files.exists(Paths.get(dossierSemaine)))
				Files.createDirectories(Paths.get(dossierSemaine));
			String destLots     = dossierSemaine + "/lots.json";
			String destSocietes = dossierSemaine + "/societes.json";
			Files.copy(Paths.get(cheminLotsJson),     Paths.get(destLots),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.copy(Paths.get(cheminSocietesJson), Paths.get(destSocietes),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			this.cheminLotsJson     = destLots;
			this.cheminSocietesJson = destSocietes;
			System.out.println("[Controleur] Données sauvegardées : " + dossierSemaine);
		}
		catch (IOException e)
		{
			System.err.println("[Controleur] Erreur sauvegarde : " + e.getMessage());
		}
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
		this.metier.nouveau();
		this.chargerDepuisExcelInteractif();
		autoSauvegarderLots();
		autoSauvegarderSocietes();
	}

	// ── IControleur : Auto-sauvegarde ────────────────────────────────────

	@Override
	public void autoSauvegarde()
	{
		autoSauvegarderLots();
		autoSauvegarderSocietes();
	}

	private void autoSauvegarderLots()
	{
		if (cheminLotsJson != null)
			try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); }
			catch (Exception e)
			{ System.err.println("[AutoSave Lots] Échec : " + e.getMessage()); }
	}

	private void autoSauvegarderSocietes()
	{
		if (cheminSocietesJson != null)
			try { savDonnees.sauvegarderSocietes(
					metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
			catch (Exception e)
			{ System.err.println("[AutoSave Sociétés] Échec : " + e.getMessage()); }
	}

	// ── Point d'entrée ────────────────────────────────────────────────────

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(Controleur::new);
	}
}