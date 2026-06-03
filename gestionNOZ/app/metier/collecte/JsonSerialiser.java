package app.metier.collecte;

import app.metier.ficheroute.FicheRoute;
import app.metier.ficheroute.Phase;
import app.metier.ficheroute.SuivieProd;
import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  JsonSerialiser — Sérialisation bidirectionnelle Java ↔ JSON
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 *  CORRECTIFS APPLIQUÉS :
 *  ──────────────────────
 *  1. deserialiserLot() — ordre de désérialisation corrigé :
 *       • nbPers lu AVANT calculHeuresPiste()
 *       • Phase restaurée AVANT SuivieProd (setLot() déclenche miseAJJourAvancement)
 *       • calculHeuresPiste(nbPers) appelé si heuresAce absente ou nulle
 *
 *  2. "estMachine" lu et écrit dans les deux sens.
 *
 *  3. Clés phases : "ph_preTri", "ph_surPiste", etc. (cohérent avec DonneesSauvegarder)
 *     → rétrocompatibilité : si "ph_preTri" absent, on tente "phase_preTri" (anciens fichiers)
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
public class JsonSerialiser
{
	// ══════════════════════════════════════════════════════════════════════
	//  SÉRIALISATION (Objet → JSON)
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

		// Lignes de colisage
		StringBuilder lignes = new StringBuilder("[");
		if (lot.getLignesColisage() != null) {
			for (int i = 0; i < lot.getLignesColisage().size(); i++) {
				if (i > 0) lignes.append(",");
				LigneColisage lc = lot.getLignesColisage().get(i);
				lignes.append("{")
					.append("\"formatCarton\":").append(esc(lc.getFormatCarton())).append(",")
					.append("\"collisage\":").append(lc.getCollisage()).append(",")
					.append("\"pcs\":").append(lc.getPcs()).append(",")
					.append("\"nbColis\":").append(lc.getNbColis())
					.append("}");
			}
		}
		lignes.append("]");

		return "{"
			+ "\"numCDE\":"                  + lot.getNumCDE()                              + ","
			+ "\"id\":"                      + esc(lot.getId())                             + ","
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
			+ "\"estMachine\":"              + lot.estMachine()                             + ","
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
			+ "\"lignesColisage\":"          + lignes                                       + ","
			+ "\"sp_nbPieceEtiq\":"          + (sp != null ? sp.getNbPieceEtiq()          : 0) + ","
			+ "\"sp_nbPieceRepart\":"        + (sp != null ? sp.getNbPieceRepart()        : 0) + ","
			+ "\"sp_nbHeureEtiqRestant\":"   + (sp != null ? sp.getNbHeureEtiqRestant()   : 0) + ","
			+ "\"sp_nbHeureRepartRestant\":" + (sp != null ? sp.getNbHeureRepartRestant() : 0) + ","
			// ── phases : clé "ph_" (cohérent avec DonneesSauvegarder) ──
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
			+ "\"societe\":"        + esc(nomSoc)                + ","
			+ "\"sommeVVS\":"       + fdr.getSommeVVS()          + ","
			+ "\"sommePieces\":"    + fdr.getSommePieces()       + ","
			+ "\"prixUnitaire\":"   + fdr.getPrixUntaireMoy()    + ","
			+ "\"effectif\":"       + fdr.getEffectif()
			+ "}";
	}

	// ══════════════════════════════════════════════════════════════════════
	//  DÉSÉRIALISATION (JSON → Objet)
	// ══════════════════════════════════════════════════════════════════════

	public static ArrayList<Lot> deserialiserLots(String json)
	{
		ArrayList<Lot> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json)) {
			try { liste.add(deserialiserLot(obj)); } catch (Exception ignored) {}
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

		String id = getString(obj, "id");
		if (!id.isBlank()) lot.setId(id);

		lot.setTypologie    (getString(obj, "typologie"));
		lot.setAffaire      (getString(obj, "affaire"));
		lot.setPrixUnitaire (getDouble(obj, "prixUnitaire"));
		lot.setSemaine      (getString(obj, "semaine"));
		lot.setPriorite     (getInt   (obj, "priorite"));
		lot.setLotACharge   (getString(obj, "lotACharge"));
		lot.setEmplacement  (getString(obj, "emplacement"));
		lot.setEstSousDouane(getBool  (obj, "estSousDouane"));
		lot.setEstMachine   (getBool  (obj, "estMachine"));
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
		lot.setPoucentrecupCartonFour(getInt(obj, "poucentrecup"));

		// ── CORRECTIF ORDRE : nbPers AVANT calculHeuresPiste ─────────────
		int nbPers = getInt(obj, "nbPers");
		lot.setNbPers(nbPers);

		// ── CORRECTIF CALCUL : recalculer heuresAce si absente/nulle ─────
		double heuresAce = getDouble(obj, "heuresAce");
		if (heuresAce > 0 && heuresAce < 1_000_000)
			lot.setHeuresAce(heuresAce);          // valeur sauvegardée : on la restaure
		else if (nbPers > 0)
			lot.calculHeuresPiste(nbPers);         // pas de valeur sauvegardée : recalcul
		else
			lot.recalculerHeures();               // fallback

		// ── Lignes de colisage ────────────────────────────────────────────
		String blocsLignes = extraireBloc(obj, "\"lignesColisage\"");
		if (blocsLignes != null) {
			for (String objLigne : extraireObjets(blocsLignes)) {
				try {
					LigneColisage lc = new LigneColisage(
						getString(objLigne, "formatCarton"),
						getInt   (objLigne, "collisage")
					);
					int pcs = getInt(objLigne, "pcs");
					if (pcs <= 0) pcs = getInt(objLigne, "nbColis") * lc.getCollisage();
					if (pcs > 0) lc.recalculer(pcs);
					lot.getLignesColisage().add(lc);
				} catch (Exception ignored) {}
			}
		}

		// ── CORRECTIF ORDRE : Phase AVANT SuivieProd ─────────────────────
		// setLot() dans SuivieProd déclenche miseAJJourAvancement()
		// → la phase doit déjà être correcte quand ça arrive
		Phase ph = new Phase();
		// Rétrocompatibilité : on accepte "ph_preTri" (nouveau) ET "phase_preTri" (ancien)
		ph.setPreTri    (getBoolCompat(obj, "ph_preTri",     "phase_preTri"));
		ph.setSurPiste  (getBoolCompat(obj, "ph_surPiste",   "phase_surPiste"));
		ph.setSortieEtiq(getBoolCompat(obj, "ph_sortieEtiq", "phase_sortieEtiq"));
		ph.setTri       (getBoolCompat(obj, "ph_tri",        "phase_tri"));
		ph.setFinit     (getBoolCompat(obj, "ph_finit",      "phase_finit"));
		lot.setPhase(ph);

		// ── SuivieProd en dernier — toutes les valeurs sont prêtes ───────
		SuivieProd sp = new SuivieProd();
		sp.setLot(lot);   // déclenche miseAJJourAvancement() avec phase + heuresAce corrects
		sp.setNbPieceEtiq         (getInt(obj, "sp_nbPieceEtiq"));
		sp.setNbPieceRepart       (getInt(obj, "sp_nbPieceRepart"));
		sp.setNbHeureEtiqRestant  (Math.min(getDouble(obj, "sp_nbHeureEtiqRestant"),   999999));
		sp.setNbHeureRepartRestant(Math.min(getDouble(obj, "sp_nbHeureRepartRestant"), 999999));
		lot.setSuivieProd(sp);

		return lot;
	}

	// ── Sociétés ──────────────────────────────────────────────────────────

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

				// 3. Lots de la société
				String lotsStr = extraireTableauPrimitif(obj, "lotsIds");
				if (lotsStr != null)
					for (int id : parseIntArray(lotsStr)) {
						Lot l = trouverLot(lots, id);
						if (l != null && !soc.getLots().contains(l)) soc.getLots().add(l);
					}

				liste.add(soc);
			} catch (Exception ignored) {}
		}
		return liste;
	}

	public static java.util.ArrayList<Ace> deserialiserAces(String json)
	{
		java.util.ArrayList<Ace> liste = new java.util.ArrayList<>();
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
	//  HELPERS D'EXTRACTION JSON
	// ══════════════════════════════════════════════════════════════════════

	public static String getString(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return "";
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) != ':') pos++;
		pos++;
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		if (pos >= obj.length()) return "";
		if (obj.charAt(pos) == '"')
		{
			pos++;
			StringBuilder sb = new StringBuilder();
			while (pos < obj.length())
			{
				char c = obj.charAt(pos);
				if (c == '\\' && pos + 1 < obj.length())
				{ sb.append(obj.charAt(pos + 1) == 'n' ? '\n' : obj.charAt(pos + 1)); pos += 2; }
				else if (c == '"') break;
				else { sb.append(c); pos++; }
			}
			return sb.toString();
		}
		return "";
	}

	public static int getInt(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return 0;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) != ':') pos++;
		pos++;
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0; }
	}

	public static double getDouble(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return 0.0;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) != ':') pos++;
		pos++;
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end))
				|| obj.charAt(end) == '-' || obj.charAt(end) == '.')) end++;
		try { return Double.parseDouble(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0.0; }
	}

	public static boolean getBool(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return false;
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) != ':') pos++;
		pos++;
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		return obj.startsWith("true", pos);
	}

	/**
	 * Lit un booléen en essayant d'abord la clé principale,
	 * puis la clé de repli (rétrocompatibilité anciens fichiers).
	 */
	private static boolean getBoolCompat(String obj, String cleNouvelle, String cleAncienne)
	{
		// Si la clé nouvelle est présente, on l'utilise
		if (obj.contains("\"" + cleNouvelle + "\"")) return getBool(obj, cleNouvelle);
		// Sinon on tente l'ancienne (fichiers sauvegardés avant le correctif)
		return getBool(obj, cleAncienne);
	}

	public static String extraireString(String obj, String cle) { return getString(obj, cle); }
	public static int    extraireInt   (String obj, String cle) { return getInt(obj, cle);    }
	public static double extraireDouble(String obj, String cle) { return getDouble(obj, cle); }
	public static boolean extraireBool (String obj, String cle) { return getBool(obj, cle);   }

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
		String contenu = tableau.replace("[", "").replace("]", "").trim();
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

	public static String serialiserLignesColisage(ArrayList<LigneColisage> lignes)
	{
		if (lignes == null) return "[]";
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < lignes.size(); i++) {
			if (i > 0) sb.append(",");
			LigneColisage lc = lignes.get(i);
			sb.append("{")
				.append("\"formatCarton\":").append(esc(lc.getFormatCarton())).append(",")
				.append("\"collisage\":").append(lc.getCollisage()).append(",")
				.append("\"pcs\":").append(lc.getPcs()).append(",")
				.append("\"nbColis\":").append(lc.getNbColis())
				.append("}");
		}
		return sb.append("]").toString();
	}

	public static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	private static double d2(double v)
	{
		if (Double.isInfinite(v) || Double.isNaN(v) || v > 1_000_000_000) return 0.0;
		return Math.round(v * 100.0) / 100.0;
	}
}
