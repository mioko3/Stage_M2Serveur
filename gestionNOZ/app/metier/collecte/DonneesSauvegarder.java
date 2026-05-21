package app.metier.collecte;

import app.metier.PlanningGlobal;
import app.metier.ficheroute.Phase;
import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;

public class DonneesSauvegarder
{
	private static final String FICHIER_LOTS     = "lots.json";
	private static final String FICHIER_SOCIETES = "societes.json";

	// ── Sauvegarde ────────────────────────────────────────────────────────

	public void sauvegarderLots(ArrayList<Lot> lots, String cheminFichier)
	{
		String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(chemin), StandardCharsets.UTF_8)))
		{
			pw.println("[");
			for (int i = 0; i < lots.size(); i++)
			{
				Lot l = lots.get(i);
				Phase p = l.getPhase();
				pw.println("  {");
				pw.println("    \"id\": "                       + esc(l.getId())                                      + ",");
				pw.println("    \"numCDE\": "                   + l.getNumCDE()                                       + ",");
				pw.println("    \"semaine\": "                  + esc(l.getSemaine())                                 + ",");
				pw.println("    \"priorite\": "                 + l.getPriorite()                                     + ",");
				pw.println("    \"typologie\": "                + esc(l.getTypologie())                               + ",");
				pw.println("    \"affaire\": "                  + esc(l.getAffaire())                                 + ",");
				pw.println("    \"valeurVente\": "              + l.getValeurVente()                                  + ",");
				pw.println("    \"nbPieces\": "                 + l.getNbPieces()                                     + ",");
				pw.println("    \"cadence\": "                  + l.getCadence()                                      + ",");
				pw.println("    \"heures\": "                   + l.getHeures()                                       + ",");
				pw.println("    \"statut\": "                   + esc(l.getStatut())                                  + ",");
				pw.println("    \"statutEchant\": "             + esc(l.getStatutEchant())                            + ",");
				pw.println("    \"lotACharge\": "               + esc(l.getLotACharge())                              + ",");
				pw.println("    \"estSousDouane\": "            + l.isEstSousDouane()                                 + ",");
				pw.println("    \"dateReception\": "            + esc(l.getDateReception())                           + ",");
				pw.println("    \"datePaiement\": "             + esc(l.getDatePaiement())                            + ",");
				pw.println("    \"commentaire\": "              + esc(l.getCommentaire())                             + ",");
				pw.println("    \"emplacement\": "              + esc(l.getEmplacement())                             + ",");
				pw.println("    \"sp_nbPieceEtiq\": "           + l.getSuivieProd().getNbPieceEtiq()                  + ",");
				pw.println("    \"sp_nbPieceRepart\": "         + l.getSuivieProd().getNbPieceRepart()                + ",");
				pw.println("    \"sp_nbHeureEtiqRestant\": "    + l.getSuivieProd().getNbHeureEtiqRestant()           + ",");
				pw.println("    \"sp_nbHeureRepartRestant\": "  + l.getSuivieProd().getNbHeureRepartRestant()         + ",");
				pw.println("    \"methode\": "                  + esc(l.getMethode() == null ? "" : l.getMethode().getNom()) + ",");
				pw.println("    \"distribution\": "             + esc(l.getDistribution())                            + ",");
				pw.println("    \"formatCarton\": "             + esc(l.getFormatCarton())                            + ",");
				pw.println("    \"dateDebut\": "                + esc(l.getDateDebut())                               + ",");
				pw.println("    \"dateFin\": "                  + esc(l.getdateFin())                                 + ",");
				pw.println("    \"dateFinTheorique\": "         + esc(l.getdateFinT())                                 + ",");
				pw.println("    \"heuresAce\": "                + l.getHeuresAce()                                    + ",");
				pw.println("    \"collisage\": "                + l.getCollisage()                                    + ",");
				pw.println("    \"estMachine\": "               + l.estMachine()                                      + ",");
				pw.println("    \"nbPers\": "                   + l.getNbPers()                                       + ",");
				pw.println("    \"cadenceReel\": "              + l.getCadenceReel()                                  + ",");

				// ── Lignes de colisage multiples ──────────────────────────
				ArrayList<LigneColisage> lc = l.getLignesColisage();
				StringBuilder lignesJson = new StringBuilder("[");
				for (int k = 0; k < lc.size(); k++)
				{
					lignesJson.append(lc.get(k).toJson());
					if (k < lc.size() - 1) lignesJson.append(",");
				}
				lignesJson.append("]");
				pw.println("    \"lignesColisage\": "           + lignesJson                                          + ",");

				// Phase
				pw.println("    \"phase_preTri\": "             + (p != null && p.isPreTri())                         + ",");
				pw.println("    \"phase_surPiste\": "           + (p != null && p.isSurPiste())                       + ",");
				pw.println("    \"phase_sortieEtiq\": "         + (p != null && p.isSortieEtiq())                     + ",");
				pw.println("    \"phase_tri\": "                + (p != null && p.isTri())                            + ",");
				pw.println("    \"phase_finit\": "              + (p != null && p.isFinit()));
				pw.println();
				pw.print("  }");
				if (i < lots.size() - 1) pw.print(",");
				pw.println();
			}
			pw.println("]");
		}
		catch (IOException e)
		{
			throw new RuntimeException("Impossible de sauvegarder les lots : " + e.getMessage(), e);
		}
	}

	public void sauvegarderSocietes(ArrayList<Societe> societes, ArrayList<Lot> lots, String cheminFichier)
	{
		String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(chemin), StandardCharsets.UTF_8)))
		{
			pw.println("[");
			for (int i = 0; i < societes.size(); i++)
			{
				Societe s = societes.get(i);
				pw.println("  {");
				pw.println("    \"nom\": "          + esc(s.getNom())         + ",");
				pw.println("    \"ce\": "           + esc(s.getCe())          + ",");
				pw.println("    \"totalHeuresCE\": " + s.getTotalHeuresCE()   + ",");
				pw.println("    \"aces\": [");
				ArrayList<Ace> aces = s.getAces();
				for (int j = 0; j < aces.size(); j++)
				{
					Ace a = aces.get(j);
					StringBuilder lotsAce = new StringBuilder("[");
					for (int k = 0; k < a.getLots().size(); k++)
					{
						lotsAce.append("\"").append(a.getLots().get(k).getId()).append("\"");
						if (k < a.getLots().size() - 1) lotsAce.append(",");
					}
					lotsAce.append("]");
					pw.print("      { \"nom\": " + esc(a.getNom())
						+ ", \"nbPers\": "         + a.getNbPers()
						+ ", \"totalHeures\": "    + a.getTotalHeures()
						+ ", \"effectifActuel\": " + a.getEffectifActuel()
						+ ", \"estMachine\": "     + a.estMachine()
						+ ", \"lotsACE\": "        + lotsAce + " }");
					if (j < aces.size() - 1) pw.print(",");
					pw.println();
				}
				pw.println("    ],");
				pw.print("    \"lotsAffectes\": [");
				ArrayList<Lot> lotsAff = s.getLots();
				for (int j = 0; j < lotsAff.size(); j++)
				{
					pw.print("\"" + lotsAff.get(j).getId() + "\"");
					if (j < lotsAff.size() - 1) pw.print(",");
				}
				pw.println("]");
				pw.print("  }");
				if (i < societes.size() - 1) pw.print(",");
				pw.println();
			}
			pw.println("]");
		}
		catch (IOException e)
		{
			throw new RuntimeException("Impossible de sauvegarder les sociétés : " + e.getMessage(), e);
		}
	}

	public void sauvegarder(PlanningGlobal metier, String cheminDossier) throws IOException
	{
		File dossier = new File(cheminDossier);
		if (!dossier.exists() && !dossier.mkdirs())
			throw new IOException("Impossible de créer le dossier : " + cheminDossier);

		sauvegarderLots(metier.getLots(),
			Paths.get(cheminDossier, FICHIER_LOTS).toString());

		sauvegarderSocietes(metier.getSocietes(), metier.getLots(),
			Paths.get(cheminDossier, FICHIER_SOCIETES).toString());

		System.out.println("[Sauvegarde] → " + cheminDossier
			+ " (" + FICHIER_LOTS + " + " + FICHIER_SOCIETES + ")");
	}

	// ── Chargement ────────────────────────────────────────────────────────

	public void charger(PlanningGlobal metier, String cheminDossier) throws IOException
	{
		String cheminLots     = Paths.get(cheminDossier, FICHIER_LOTS).toString();
		String cheminSocietes = Paths.get(cheminDossier, FICHIER_SOCIETES).toString();
	
		if (!new File(cheminLots).exists())
			throw new IOException("Fichier introuvable : " + cheminLots);
		if (!new File(cheminSocietes).exists())
			throw new IOException("Fichier introuvable : " + cheminSocietes);
	
		metier.getSocietes().clear();
		metier.getLots().clear();
	
		metier.getLots()    .addAll(ExcelReader.lireLots    (cheminLots));
		metier.getSocietes().addAll(ExcelReader.lireSocietes(cheminSocietes));
	
		System.out.println("[Chargement] ← " + cheminDossier
			+ " (" + metier.getLots().size() + " lots, "
			+ metier.getSocietes().size() + " sociétés)");
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\", "\\\\")
		               .replace("\"", "\\\"")
		               .replace("\n", "\\n")
		               .replace("\r", "") + "\"";
	}
}