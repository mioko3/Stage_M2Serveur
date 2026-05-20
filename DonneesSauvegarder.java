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
	private static final int VERSION = 2;

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

	private Lot parseLot(String obj)
	{
		Lot lot = new Lot(
			getInt   (obj, "numCDE"),
			getInt   (obj, "nbPieces"),
			getDouble(obj, "cadence"),
			getDouble(obj, "heures"),
			getInt   (obj, "valeurVente"),
			getString(obj, "statut"),
			getString(obj, "statutEchant")
		);
		String savedId = getString(obj, "id");
		if (savedId != null && !savedId.isEmpty()) lot.setId(savedId);

		lot.setTypologie    (getString(obj, "typologie"));
		lot.setAffaire      (getString(obj, "affaire"));
		lot.setSemaine      (getString(obj, "semaine"));
		lot.setPriorite     (getInt   (obj, "priorite"));
		lot.setLotACharge   (getString(obj, "lotACharge"));
		lot.setEstSousDouane(getBool  (obj, "estSousDouane"));
		lot.setDateReception(getString(obj, "dateReception"));
		lot.setDatePaiement (getString(obj, "datePaiement"));
		lot.setCommentaire  (getString(obj, "commentaire"));
		lot.setEmplacement  (getString(obj, "emplacement"));
		lot.getSuivieProd().setNbPieceEtiq         (getInt(obj, "sp_nbPieceEtiq"));
		lot.getSuivieProd().setNbPieceRepart        (getInt(obj, "sp_nbPieceRepart"));
		lot.getSuivieProd().setNbHeureEtiqRestant   (getInt(obj, "sp_nbHeureEtiqRestant"));
		lot.getSuivieProd().setNbHeureRepartRestant (getInt(obj, "sp_nbHeureRepartRestant"));
		lot.setMethode      (getString(obj, "methode"));
		lot.setDistribution (getString(obj, "distribution"));
		lot.setFormatCarton (getString(obj, "formatCarton"));
		lot.setHeuresAce    (getDouble(obj, "heuresAce"));
		lot.setCollisage    (getInt   (obj, "collisage"));
		lot.setEstMachine   (getBool  (obj, "estMachine"));

		// ── Lignes de colisage multiples ──────────────────────────────────
		String blocLignes = extraireBloc(obj, "\"lignesColisage\"");
		if (blocLignes != null)
		{
			for (String ligneObj : extraireObjets(blocLignes))
			{
				LigneColisage lc = LigneColisage.fromJson(ligneObj);
				lc.recalculer(lot.getNbPieces());
				lot.getLignesColisage().add(lc);
			}
		}

		// Phase
		Phase phase = new Phase();
		phase.setPreTri     (getBool(obj, "phase_preTri"));
		phase.setSurPiste   (getBool(obj, "phase_surPiste"));
		phase.setSortieEtiq (getBool(obj, "phase_sortieEtiq"));
		phase.setTri        (getBool(obj, "phase_tri"));
		phase.setFinit      (getBool(obj, "phase_finit"));
		lot.setPhase(phase);

		return lot;
	}

	private Lot trouverLot(ArrayList<Lot> lots, String id)
	{
		for (Lot l : lots) if (id.equals(l.getId())) return l;
		return null;
	}

	private ArrayList<String> parseIdList(String tableau)
	{
		ArrayList<String> liste = new ArrayList<>();
		String contenu = tableau.replace("[", "").replace("]", "").trim();
		if (contenu.isEmpty()) return liste;
		for (String s : contenu.split(","))
		{
			String id = s.trim().replace("\"", "");
			if (!id.isEmpty()) liste.add(id);
		}
		return liste;
	}

	private static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\", "\\\\")
		               .replace("\"", "\\\"")
		               .replace("\n", "\\n")
		               .replace("\r", "") + "\"";
	}

	private static String lireFichier(String chemin) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(chemin), StandardCharsets.UTF_8)))
		{ String l; while ((l = br.readLine()) != null) sb.append(l); }
		return sb.toString();
	}

	private static String extraireBloc(String json, String cle)
	{
		int pos = json.indexOf(cle); if (pos < 0) return null;
		int p = pos + cle.length();
		while (p < json.length() && json.charAt(p) != '[' && json.charAt(p) != '{') p++;
		if (p >= json.length()) return null;
		char open = json.charAt(p), close = open == '[' ? ']' : '}';
		int depth = 0, start = p;
		for (int i = p; i < json.length(); i++)
		{ char c = json.charAt(i); if (c==open) depth++; else if (c==close){depth--; if(depth==0) return json.substring(start,i+1);} }
		return null;
	}

	private static String extraireTableauPrimitif(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern); if (pos < 0) return null;
		pos = obj.indexOf('[', pos + pattern.length()); if (pos < 0) return null;
		int end = obj.indexOf(']', pos); if (end < 0) return null;
		return obj.substring(pos, end + 1);
	}

	private static ArrayList<String> extraireObjets(String tableau)
	{
		ArrayList<String> liste = new ArrayList<>();
		int depth = 0, start = -1;
		for (int i = 0; i < tableau.length(); i++)
		{ char c = tableau.charAt(i); if(c=='{'){if(depth==0)start=i;depth++;} else if(c=='}'){depth--;if(depth==0&&start>=0){liste.add(tableau.substring(start,i+1));start=-1;}} }
		return liste;
	}

	private static String getString(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern); if (pos < 0) return "";
		pos = obj.indexOf('"', pos + pattern.length() + 1); if (pos < 0) return "";
		StringBuilder sb = new StringBuilder(); pos++;
		while (pos < obj.length())
		{
			char c = obj.charAt(pos);
			if (c == '\\' && pos + 1 < obj.length())
			{
				char nx = obj.charAt(pos + 1);
				if (nx == '"')  { sb.append('"');  pos += 2; continue; }
				if (nx == 'n')  { sb.append('\n'); pos += 2; continue; }
				if (nx == '\\') { sb.append('\\'); pos += 2; continue; }
			}
			if (c == '"') break;
			sb.append(c); pos++;
		}
		return sb.toString();
	}

	private static int getInt(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern); if (pos < 0) return 0;
		pos += pattern.length(); while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos; while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0; }
	}

	private static double getDouble(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern); if (pos < 0) return 0.0;
		pos += pattern.length(); while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos; while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '.' || obj.charAt(end) == '-')) end++;
		try { return Double.parseDouble(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0.0; }
	}

	private static boolean getBool(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern); if (pos < 0) return false;
		pos += pattern.length(); while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		return obj.startsWith("true", pos);
	}
}