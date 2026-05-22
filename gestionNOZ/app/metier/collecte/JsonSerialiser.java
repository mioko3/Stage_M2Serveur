package app.metier.collecte;

import app.metier.ficheroute.FicheRoute;
import app.metier.ficheroute.Phase;
import app.metier.ficheroute.SuivieProd;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.util.ArrayList;

/**
 * ══════════════════════════════════════════════════════════════
 *  SÉRIALISEUR JSON
 * ══════════════════════════════════════════════════════════════
 */
public class JsonSerialiser
{
	// ══════════════════════════════════════════════════════════════════════
	//  SÉRIALISATION
	// ══════════════════════════════════════════════════════════════════════

	public static String serialiserLots(ArrayList<Lot> lots)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < lots.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(serialiserLotSeul(lots.get(i)));
		}
		return sb.append("]").toString();
	}

	public static String serialiserLotSeul(Lot lot)
	{
		SuivieProd sp = lot.getSuivieProd();
		Phase      ph = lot.getPhase();
		String nomMethode = (lot.getMethode() != null) ? lot.getMethode().getNom() : "";

		return "{"
			+ "\"numCDE\":"                  + lot.getNumCDE()                              + ","
			+ "\"typologie\":"               + esc(lot.getTypologie())                      + ","
			+ "\"affaire\":"                 + esc(lot.getAffaire())                        + ","
			+ "\"nbPieces\":"                + lot.getNbPieces()                            + ","
			+ "\"cadence\":"                 + d2(lot.getCadence())                         + ","
			+ "\"heures\":"                  + d2(lot.getHeures())                          + ","
			+ "\"heuresAce\":"               + d2(lot.getHeuresAce())                       + ","
			+ "\"valeurVente\":"             + lot.getValeurVente()                         + ","
			+ "\"prixUnitaire\":"            + d2(lot.getPrixUnitaire())                    + ","
			+ "\"statut\":"                  + esc(lot.getStatut())                         + ","
			+ "\"statutEchant\":"            + esc(lot.getStatutEchant())                   + ","
			+ "\"semaine\":"                 + esc(lot.getSemaine())                        + ","
			+ "\"priorite\":"                + lot.getPriorite()                            + ","
			+ "\"lotACharge\":"              + esc(lot.getLotACharge())                     + ","
			+ "\"emplacement\":"             + esc(lot.getEmplacement())                    + ","
			+ "\"estSousDouane\":"           + lot.isEstSousDouane()                        + ","
			+ "\"dateReception\":"           + esc(lot.getDateReception())                  + ","
			+ "\"datePaiement\":"            + esc(lot.getDatePaiement())                   + ","
			+ "\"commentaire\":"             + esc(lot.getCommentaire())                    + ","
			+ "\"methode\":"                 + esc(nomMethode)                              + ","
			+ "\"distribution\":"            + esc(lot.getDistribution())                   + ","
			+ "\"formatCarton\":"            + esc(lot.getFormatCarton())                   + ","
			+ "\"dateDebut\":"               + esc(lot.getDateDebut())                      + ","
			+ "\"dateFin\":"                 + esc(lot.getdateFin())                        + ","
			+ "\"dateFinTheorique\":"        + esc(lot.getdateFinT())                       + ","
			+ "\"cadenceReel\":"             + d2(lot.getCadenceReel())                     + ","
			+ "\"collisage\":"               + lot.getCollisage()                           + ","
			+ "\"nbPers\":"                  + lot.getNbPers()                              + ","
			+ "\"poucentrecup\":"            + lot.getPoucentrecupCartonFour()              + ","
			+ "\"sp_nbPieceEtiq\":"          + (sp != null ? sp.getNbPieceEtiq()          : 0) + ","
			+ "\"sp_nbPieceRepart\":"        + (sp != null ? sp.getNbPieceRepart()        : 0) + ","
			+ "\"sp_nbHeureEtiqRestant\":"   + (sp != null ? sp.getNbHeureEtiqRestant()   : 0) + ","
			+ "\"sp_nbHeureRepartRestant\":" + (sp != null ? sp.getNbHeureRepartRestant() : 0) + ","
			+ "\"ph_preTri\":"               + (ph != null && ph.isPreTri())               + ","
			+ "\"ph_surPiste\":"             + (ph != null && ph.isSurPiste())             + ","
			+ "\"ph_sortieEtiq\":"           + (ph != null && ph.isSortieEtiq())           + ","
			+ "\"ph_tri\":"                  + (ph != null && ph.isTri())                  + ","
			+ "\"ph_finit\":"                + (ph != null && ph.isFinit())
			+ "}";
	}

	public static String serialiserSocietes(ArrayList<Societe> societes)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < societes.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(serialiserSocieteSeule(societes.get(i)));
		}
		return sb.append("]").toString();
	}

	private static String serialiserSocieteSeule(Societe soc)
	{
		StringBuilder lotsIds = new StringBuilder("[");
		for (int i = 0; i < soc.getLots().size(); i++) {
			if (i > 0) lotsIds.append(",");
			lotsIds.append(soc.getLots().get(i).getNumCDE());
		}
		lotsIds.append("]");

		StringBuilder aces = new StringBuilder("[");
		for (int i = 0; i < soc.getAces().size(); i++) {
			if (i > 0) aces.append(",");
			aces.append(serialiserAce(soc.getAces().get(i)));
		}
		aces.append("]");

		return "{"
			+ "\"nom\":"           + esc(soc.getNom())      + ","
			+ "\"ce\":"            + esc(soc.getCe())       + ","
			+ "\"effectifTotal\":" + soc.getEffectifTotal() + ","
			+ "\"totalHeuresCE\":" + soc.getTotalHeuresCE() + ","
			+ "\"lotsIds\":"       + lotsIds                + ","
			+ "\"aces\":"          + aces
			+ "}";
	}

	private static String serialiserAce(Ace ace)
	{
		StringBuilder lotsIds = new StringBuilder("[");
		for (int i = 0; i < ace.getLots().size(); i++) {
			if (i > 0) lotsIds.append(",");
			lotsIds.append(ace.getLots().get(i).getNumCDE());
		}
		lotsIds.append("]");

		return "{"
			+ "\"nom\":"         + esc(ace.getNom())       + ","
			+ "\"nbPers\":"      + ace.getNbPers()         + ","
			+ "\"totalHeures\":" + ace.getTotalHeures()    + ","
			+ "\"effectif\":"    + ace.getEffectifActuel() + ","
			+ "\"lotsIds\":"     + lotsIds
			+ "}";
	}

	public static String serialiserFicheRoute(FicheRoute fdr)
	{
		String nomSoc = (fdr.getSociete() != null) ? fdr.getSociete().getNom() : "";
		return "{"
			+ "\"nomSociete\":"  + esc(nomSoc)             + ","
			+ "\"sommeVVS\":"    + fdr.getSommeVVS()       + ","
			+ "\"sommePieces\":" + fdr.getSommePieces()    + ","
			+ "\"prixUnitMoy\":" + fdr.getPrixUntaireMoy() + ","
			+ "\"effectif\":"    + fdr.getEffectif()
			+ "}";
	}

	// ══════════════════════════════════════════════════════════════════════
	//  DÉSÉRIALISATION
	// ══════════════════════════════════════════════════════════════════════

	public static ArrayList<Lot> deserialiserLots(String json)
	{
		ArrayList<Lot> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		ArrayList<String> objets = extraireObjets(json);
		for (int idx = 0; idx < objets.size(); idx++) {
			try {
				liste.add(deserialiserLot(objets.get(idx)));
			}
			catch (Throwable e) {
				System.err.println("[Json] Lot #" + idx + " ignoré : " + e.getMessage());
			}
		}
		return liste;
	}

	public static Lot deserialiserLot(String obj)
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
		lot.setTypologie    (getString(obj, "typologie"));
		lot.setAffaire      (getString(obj, "affaire"));
		lot.setPrixUnitaire (getDouble(obj, "prixUnitaire"));
		double heuresAce = getDouble(obj, "heuresAce");
		lot.setHeuresAce(heuresAce > 1_000_000 ? 0.0 : heuresAce);
		lot.setSemaine      (getString(obj, "semaine"));
		lot.setPriorite     (getInt   (obj, "priorite"));
		lot.setLotACharge   (getString(obj, "lotACharge"));
		lot.setEmplacement  (getString(obj, "emplacement"));
		lot.setEstSousDouane(getBool  (obj, "estSousDouane"));
		lot.setDateReception(getString(obj, "dateReception"));
		lot.setDatePaiement (getString(obj, "datePaiement"));
		lot.setCommentaire  (getString(obj, "commentaire"));
		lot.setMethode      (getString(obj, "methode"));
		lot.setDistribution (getString(obj, "distribution"));
		lot.setFormatCarton (getString(obj, "formatCarton"));
		lot.setDateDebut    (getString(obj, "dateDebut"));
		lot.setdateFin      (getString(obj, "dateFin"));
		lot.setdateFinT     (getString(obj, "dateFinTheorique"));
		lot.setCadenceReel  (getDouble(obj, "cadenceReel"));
		lot.setCollisage    (getInt   (obj, "collisage"));
		lot.setNbPers       (getInt   (obj, "nbPers"));
		lot.setPoucentrecupCartonFour(getInt(obj, "poucentrecup"));

		SuivieProd sp = new SuivieProd();
		sp.setLot(lot);
		sp.setNbPieceEtiq         (getInt(obj, "sp_nbPieceEtiq"));
		sp.setNbPieceRepart       (getInt(obj, "sp_nbPieceRepart"));
		sp.setNbHeureEtiqRestant  (Math.min(getInt(obj, "sp_nbHeureEtiqRestant"),   999999));
		sp.setNbHeureRepartRestant(Math.min(getInt(obj, "sp_nbHeureRepartRestant"), 999999));
		lot.setSuivieProd(sp);

		Phase ph = new Phase();
		ph.setPreTri    (getBool(obj, "ph_preTri"));
		ph.setSurPiste  (getBool(obj, "ph_surPiste"));
		ph.setSortieEtiq(getBool(obj, "ph_sortieEtiq"));
		ph.setTri       (getBool(obj, "ph_tri"));
		ph.setFinit     (getBool(obj, "ph_finit"));
		lot.setPhase(ph);

		return lot;
	}

	public static ArrayList<Societe> deserialiserSocietes(String json, ArrayList<Lot> lots)
	{
		ArrayList<Societe> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json)) {
			try {
				// 1. ACE
				ArrayList<Ace> aces = new ArrayList<>();
				String blocsAces = extraireBloc(obj, "\"aces\"");
				if (blocsAces != null) {
					for (String objAce : extraireObjets(blocsAces)) {
						Ace ace = new Ace(
							getString(objAce, "nom"),
							getInt   (objAce, "nbPers"),
							getInt   (objAce, "totalHeures"),
							getInt   (objAce, "effectif")
						);
						String aceLotsStr = extraireTableauPrimitif(objAce, "lotsIds");
						if (aceLotsStr != null)
							for (int id : parseIntArray(aceLotsStr)) {
								Lot l = trouverLot(lots, id);
								if (l != null) ace.getLots().add(l);
							}
						aces.add(ace);
					}
				}

				// 2. Société
				Societe soc = new Societe(
					getString(obj, "nom"),
					getString(obj, "ce"),
					aces,
					getInt   (obj, "totalHeuresCE")
				);
				soc.setEffectifTotal(getInt(obj, "effectifTotal"));

				// 3. Lots affectés
				String lotsIdsStr = extraireTableauPrimitif(obj, "lotsIds");
				if (lotsIdsStr != null)
					for (int id : parseIntArray(lotsIdsStr)) {
						Lot l = trouverLot(lots, id);
						if (l != null) soc.getLots().add(l);
					}

				liste.add(soc);
			} catch (Exception e) {
				System.err.println("[Json] Société ignorée : " + e.getMessage());
			}
		}
		return liste;
	}

	public static ArrayList<Ace> deserialiserAces(String json)
	{
		ArrayList<Ace> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json))
			try {
				liste.add(new Ace(
					getString(obj, "nom"),
					getInt   (obj, "nbPers"),
					0,
					getInt   (obj, "effectif")
				));
			} catch (Exception e) { System.err.println("[Json] ACE ignorée : " + e.getMessage()); }
		return liste;
	}

	public static FicheRoute deserialiserFicheRoute(String json, Societe societe)
	{
		return new FicheRoute(societe);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES D'EXTRACTION
	// ══════════════════════════════════════════════════════════════════════

	public static String getString(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return "";
		pos = obj.indexOf('"', pos + pattern.length() + 1);
		if (pos < 0) return "";
		StringBuilder sb = new StringBuilder();
		pos++;
		while (pos < obj.length()) {
			char c = obj.charAt(pos);
			if (c == '\\' && pos + 1 < obj.length()) {
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

	public static int getInt(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return 0;
		pos += p.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-'
			|| obj.charAt(end) == '.' || obj.charAt(end) == 'E' || obj.charAt(end) == 'e')) end++;
		String val = obj.substring(pos, end);
		try {
			if (val.contains(".") || val.contains("E") || val.contains("e")) {
				double d = Double.parseDouble(val);
				if (d > Integer.MAX_VALUE || d < Integer.MIN_VALUE) return 0;
				return (int) d;
			}
			return Integer.parseInt(val);
		} catch (NumberFormatException e) { return 0; }
	}

	public static double getDouble(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return 0.0;
		pos += p.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '.'
			|| obj.charAt(end) == '-' || obj.charAt(end) == '+' || obj.charAt(end) == 'E' || obj.charAt(end) == 'e')) end++;
		try {
			double d = Double.parseDouble(obj.substring(pos, end));
			if (Double.isInfinite(d) || Double.isNaN(d)) return 0.0;
			return Math.round(d * 100.0) / 100.0;
		} catch (NumberFormatException e) { return 0.0; }
	}

	public static boolean getBool(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return false;
		pos += p.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		return obj.startsWith("true", pos);
	}

	// ── Alias pour ServeurHTTP et ControleurClient ────────────────────────
	public static int     extraireInt   (String json, String cle) { return getInt   (json, cle); }
	public static String  extraireString(String json, String cle) { return getString (json, cle); }
	public static boolean extraireBool  (String json, String cle) { return getBool   (json, cle); }
	public static double  extraireDouble(String json, String cle) { return getDouble (json, cle); }

	public static String extraireBloc(String json, String cle)
	{
		int pos = json.indexOf(cle);
		if (pos < 0) return null;
		int p = pos + cle.length();
		while (p < json.length() && json.charAt(p) != '[' && json.charAt(p) != '{') p++;
		if (p >= json.length()) return null;
		char open = json.charAt(p), close = open == '[' ? ']' : '}';
		int depth = 0, start = p;
		for (int i = p; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == open) depth++;
			else if (c == close) { depth--; if (depth == 0) return json.substring(start, i + 1); }
		}
		return null;
	}

	public static String extraireTableauPrimitif(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return null;
		pos = obj.indexOf('[', pos + pattern.length());
		if (pos < 0) return null;
		int end = obj.indexOf(']', pos);
		return end < 0 ? null : obj.substring(pos, end + 1);
	}

	public static ArrayList<String> extraireObjets(String tableau)
	{
		ArrayList<String> liste = new ArrayList<>();
		int depth = 0, start = -1;
		for (int i = 0; i < tableau.length(); i++) {
			char c = tableau.charAt(i);
			if (c == '{') { if (depth == 0) start = i; depth++; }
			else if (c == '}') {
				depth--;
				if (depth == 0 && start >= 0) { liste.add(tableau.substring(start, i + 1)); start = -1; }
			}
		}
		return liste;
	}

	private static ArrayList<Integer> parseIntArray(String tableau)
	{
		ArrayList<Integer> liste = new ArrayList<>();
		String contenu = tableau.replace("[","").replace("]","").trim();
		if (contenu.isEmpty()) return liste;
		for (String s : contenu.split(","))
			try { liste.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
		return liste;
	}

	private static Lot trouverLot(ArrayList<Lot> lots, int numCDE)
	{
		for (Lot l : lots) if (l.getNumCDE() == numCDE) return l;
		return null;
	}

	public static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
	}

	private static double d2(double v)
	{
		if (Double.isInfinite(v) || Double.isNaN(v) || v > 1_000_000_000) return 0.0;
		return Math.round(v * 100.0) / 100.0;
	}
}