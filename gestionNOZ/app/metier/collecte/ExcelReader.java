package app.metier.collecte;

import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.ficheroute.Phase;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import app.metier.PlanningGlobal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Lit des données depuis Excel via Apache POI ou depuis le JSON existant.
 */
public class ExcelReader
{
	private static final DataFormatter FORMATTER = new DataFormatter();
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	public static ArrayList<Societe> lireSocietes(String chemin, ArrayList<Lot> lots) throws IOException
	{
		if (chemin.toLowerCase().endsWith(".json"))
			return lireSocietesJson(chemin, lots);
		return null;
	}

	public static ArrayList<Lot> lireLots(String chemin) throws IOException
	{
		if (chemin.toLowerCase().endsWith(".json"))
			return lireLotsJson(chemin);
		return lireLotsExcel(chemin);
	}

	private static ArrayList<Lot> lireLotsExcel(String exportPath) throws IOException
	{
		try (FileInputStream fis = new FileInputStream(exportPath);
			 Workbook wb = WorkbookFactory.create(fis))
		{
			FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
			Sheet sh = wb.getSheetAt(0);
			ArrayList<Lot> lots = new ArrayList<>();

			for (int i = 1; i <= sh.getLastRowNum(); i++)
			{
				Row row = sh.getRow(i);
				if (row == null) continue;

				String cdeText = strSafe(getCell(row, 3), evaluator);
				if (cdeText.isEmpty()) continue;

				int cde;
				try { cde = (int) Math.round(Double.parseDouble(cdeText.replace(",", ".").trim())); }
				catch (NumberFormatException e) { continue; }

				int nbPieces = (int) Math.round(numSafe(getCell(row, 7), evaluator));
				double cadence = numSafe(getCell(row, 8), evaluator);
				double heuresFichier = numSafe(getCell(row, 16), evaluator);
				double heures;
				if (heuresFichier > 0)
					heures = Math.round(heuresFichier * 100.0) / 100.0;
				else if (cadence > 0)
					heures = Math.round((nbPieces / cadence) * 100.0) / 100.0;
				else
					heures = 0.0;

				Lot lot = new Lot(
					cde,
					nbPieces,
					Math.round(cadence * 100.0) / 100.0,
					heures,
					(int) Math.round(numSafe(getCell(row, 6), evaluator)),
					strSafe(getCell(row, 9), evaluator),
					strSafe(getCell(row, 10), evaluator)
				);

				lot.setSemaine(strSafe(getCell(row, 0), evaluator));
				lot.setPriorite((int) Math.round(numSafe(getCell(row, 2), evaluator)));
				lot.setTypologie(strSafe(getCell(row, 4), evaluator));
				lot.setAffaire(strSafe(getCell(row, 5), evaluator));
				lot.setLotACharge(strSafe(getCell(row, 11), evaluator));
				lot.setEstSousDouane("oui".equalsIgnoreCase(strSafe(getCell(row, 12), evaluator)));
				lot.setDateReception(dateSafe(getCell(row, 13), evaluator));
				lot.setDatePaiement(strSafe(getCell(row, 14), evaluator));
				lot.setCommentaire(strSafe(getCell(row, 15), evaluator));

				lots.add(lot);
			}

			System.out.println("  → " + lots.size() + " lots lus depuis export.XLSX");
			return lots;
		}
	}

	private static final Map<String, Integer> SOCIETE_ROW = Map.of(
		"PROD", 7,
		"PA",   14,
		"EUP",  21,
		"CFP",  28
	);

	public static void ajouterHeuresDepuisExcel(String exportPath, ArrayList<Societe> societes, int semaine)
	throws IOException
	{
		try (FileInputStream fis = new FileInputStream(exportPath);
			Workbook wb = WorkbookFactory.create(fis))
		{
			FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
			Sheet sh = wb.getSheet("TDB V2");
			if (sh == null)
				throw new IOException("Feuille Excel introuvable : TDB V2");

			int colStart = 5 + (semaine - 1) * 7;

			for (Societe soc : societes)
			{
				Integer rowIdx = SOCIETE_ROW.get(soc.getNom());
				if (rowIdx == null) continue;

				Row row = sh.getRow(rowIdx - 1);
				if (row == null) continue;

				int effectif = (int) Math.round(numSafe(getCell(row, colStart),     evaluator));
				int hBesoin  = (int) Math.round(numSafe(getCell(row, colStart + 1), evaluator));
				int hAff     = (int) Math.round(numSafe(getCell(row, colStart + 3), evaluator));

				soc.setEffectifTotal(effectif);
				for (Ace ace : soc.getAces())
				{
					int part = effectif / soc.getAces().size();
					ace.setEffectifActuel(part);
				}
				for (Lot lot : soc.getLots())
				{
					hAff -= (int) Math.ceil(lot.getHeures());
				}
				soc.setTotalHeuresCE(hAff);
			}
		}
	}

	public static java.util.Map<String, Integer> lireHeuresSocietes(
		String cheminXlsx, int semaine) throws IOException
	{
		java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
 
		try (FileInputStream fis = new FileInputStream(cheminXlsx);
			 Workbook wb = WorkbookFactory.create(fis))
		{
			FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
 
			// Chercher la feuille "TDB V2" (même convention que ajouterHeuresDepuisExcel)
			Sheet sh = wb.getSheet("TDB V2");
			if (sh == null)
			{
				// Fallback : première feuille
				sh = wb.getSheetAt(0);
			}
 
			// Même logique que SOCIETE_ROW dans ajouterHeuresDepuisExcel
			java.util.Map<String, Integer> societeRow = new java.util.LinkedHashMap<>();
			societeRow.put("PROD", 7);
			societeRow.put("PA",   14);
			societeRow.put("EUP",  21);
			societeRow.put("CFP",  28);
 
			int colStart = 5 + (semaine - 1) * 7;
 
			for (java.util.Map.Entry<String, Integer> entry : societeRow.entrySet())
			{
				String nom    = entry.getKey();
				int    rowIdx = entry.getValue();
 
				Row row = sh.getRow(rowIdx - 1);
				if (row == null) continue;
 
				// hAff = heures affectées (même colonne que dans ajouterHeuresDepuisExcel)
				int hAff = (int) Math.round(numSafe(getCell(row, colStart + 3), evaluator));
				if (hAff > 0)
					result.put(nom, hAff);
			}
		}
 
		return result;
	}

	private static Cell getCell(Row row, int index)
	{
		return row == null ? null : row.getCell(index);
	}

	private static String strSafe(Cell cell, FormulaEvaluator evaluator)
	{
		if (cell == null) return "";
		return FORMATTER.formatCellValue(cell, evaluator).trim();
	}

	private static double numSafe(Cell cell, FormulaEvaluator evaluator)
	{
		if (cell != null && (cell.getCellType() == CellType.NUMERIC
				|| cell.getCellType() == CellType.FORMULA))
		{
			try { return cell.getNumericCellValue(); }
			catch (Exception ignored) {}
		}
		String value = strSafe(cell, evaluator);
		if (value.isEmpty()) return 0.0;
		value = value.replace(" ", "").replace("\u00A0", "").replace("'", "").replace("%", "");
		if (value.contains(",") && value.contains("."))
			value = value.replace(",", "");
		else
			value = value.replace(",", ".");
		try { return Double.parseDouble(value); }
		catch (NumberFormatException e) { return 0.0; }
	}

	private static String dateSafe(Cell cell, FormulaEvaluator evaluator)
	{
		if (cell == null) return "";
		if ((cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA)
			&& DateUtil.isCellDateFormatted(cell))
		{
			Date date = cell.getDateCellValue();
			return DATE_FORMAT.format(date);
		}
		String value = FORMATTER.formatCellValue(cell, evaluator).trim();
		return value.isEmpty() ? "" : value;
	}

	// ── Lecture JSON ──────────────────────────────────────────────────────

	private static ArrayList<Societe> lireSocietesJson(String cheminJson, ArrayList<Lot> lots) throws IOException
	{
		String json = lireFichier(cheminJson);
		ArrayList<Societe> liste = new ArrayList<>();

		for (String obj : extraireObjets(json))
		{
			String nom    = getString(obj, "nom");
			String ce     = getString(obj, "ce");
			int    hTotal = getInt(obj, "totalHeuresCE");

			ArrayList<Ace> aces = new ArrayList<>();
			String blocAces = extraireBloc(obj, "\"aces\"");
			Societe soc = new Societe(nom, ce, aces, hTotal);
			if (blocAces != null)
			{
				for (String a : extraireObjets(blocAces))
				{
					Ace ace = new Ace(
						getString(a, "nom"),
						getInt(a, "nbPers"),
						getInt(a, "totalHeures"),
						getInt(a, "effectifActuel")
					);
					ace.setEstMachine(getBool(a, "estMachine"));
					aces.add(ace);
					String blocLotsAce = extraireBloc(a, "\"lotsACE\"");
					if (blocLotsAce != null)
					{
						for (String id : parseIdList(blocLotsAce))
						{
							Lot lot = trouverLotParId(id, lots);
							if (lot != null && !ace.getLots().contains(lot))
							{
								soc.ajouterLotSansHeures(lot, ace);
							}
							else if (lot == null)
							{
								System.err.println("[ERREUR] Lot non trouvé (ID: " + id + ") pour l'ACE " + ace.getNom() + " de " + soc.getNom());
							}
						}
					}
				}
			}
			String lotsAffectes = extraireBloc(obj, "\"lotsAffectes\"");
			if (lotsAffectes != null)
			{
				for (String id : parseIdList(lotsAffectes))
				{
					Lot lot = trouverLotParId(id, lots);
					if (lot != null && !soc.getLots().contains(lot))
						soc.ajouterLot(lot, null);
					else if (lot == null)
						System.err.println("[ERREUR] Lot non trouvé (ID: " + id + ") pour la société " + soc.getNom());
				}
			}
			liste.add(soc);
		}
		return liste;
	}

	private static Lot trouverLotParId(String id, ArrayList<Lot> lots)
	{
		for (Lot l : lots)
			if (l.getId().equals(id)) return l;
		return null;
	}

	private static ArrayList<Lot> lireLotsJson(String cheminJson) throws IOException
	{
		String json = lireFichier(cheminJson);
		ArrayList<Lot> liste = new ArrayList<>();

		for (String obj : extraireObjets(json))
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
			lot.setId           (getString(obj, "id"));
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
			lot.setDateDebut    (getString(obj, "dateDebut"));
			lot.setdateFin      (getString(obj, "dateFin"));
			lot.setdateFinT      (getString(obj, "dateFinTheorique"));
			lot.setHeuresAce    (getDouble(obj, "heuresAce"));
			lot.setCollisage    (getInt   (obj, "collisage"));
			lot.setEstMachine   (getBool  (obj, "estMachine"));
			lot.setNbPers       (getInt(obj, "nbPers"));
			lot.setCadenceReel  (getDouble(obj, "cadenceReel"));

			// ── Lignes de colisage multiples ──────────────────────────────
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

			liste.add(lot);
		}
		return liste;
	}

	// ── Helpers JSON ──────────────────────────────────────────────────────

	private static String lireFichier(String cheminJson) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(cheminJson), StandardCharsets.UTF_8)))
		{
			String ligne;
			while ((ligne = br.readLine()) != null) sb.append(ligne);
		}
		return sb.toString();
	}

	private static String extraireBloc(String json, String cle)
	{
		int pos = json.indexOf(cle);
		if (pos < 0) return null;
		int p = pos + cle.length();
		while (p < json.length() && json.charAt(p) != '[' && json.charAt(p) != '{') p++;
		if (p >= json.length()) return null;
		char open = json.charAt(p), close = open == '[' ? ']' : '}';
		int depth = 0, start = p;
		for (int i = p; i < json.length(); i++)
		{
			char c = json.charAt(i);
			if (c == open) depth++;
			else if (c == close) { depth--; if (depth == 0) return json.substring(start, i + 1); }
		}
		return null;
	}

	private static ArrayList<String> extraireObjets(String tableau)
	{
		ArrayList<String> liste = new ArrayList<>();
		int depth = 0, start = -1;
		for (int i = 0; i < tableau.length(); i++)
		{
			char c = tableau.charAt(i);
			if (c == '{') { if (depth == 0) start = i; depth++; }
			else if (c == '}') { depth--; if (depth == 0 && start >= 0) { liste.add(tableau.substring(start, i+1)); start = -1; } }
		}
		return liste;
	}

	private static ArrayList<String> parseIdList(String tableau)
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

	private static String getString(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return "";
		pos = obj.indexOf('"', pos + pattern.length() + 1);
		if (pos < 0) return "";
		int end = obj.indexOf('"', pos + 1);
		if (end < 0) return "";
		return obj.substring(pos + 1, end);
	}

	private static int getInt(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return 0;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); }
		catch (NumberFormatException e) { return 0; }
	}

	private static double getDouble(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return 0.0;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '.' || obj.charAt(end) == '-')) end++;
		try { return Double.parseDouble(obj.substring(pos, end)); }
		catch (NumberFormatException e) { return 0.0; }
	}

	private static boolean getBool(String obj, String cle)
	{
		String pattern = "\"" + cle + "\":";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return false;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		return obj.startsWith("true", pos);
	}
}