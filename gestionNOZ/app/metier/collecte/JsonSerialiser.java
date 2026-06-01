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
 * RÔLE :
 * ──────
 * Convertir entre objets Java et chaînes JSON (pas de dépendance externe comme Gson/Jackson).
 * Implémentation MANUELLE pour contrôle total et légèreté.
 *
 * FORMATS JSON :
 * ──────────────
 * • Lots : [ { lot1 }, { lot2 }, ... ]
 * • Sociétés : [ { societe1 }, { societe2 }, ... ]
 * • ACE : { "nom": "Alice", "nbPers": 5, "lots": [numCDE1, numCDE2] }
 *
 * EXEMPLE JSON Lot complet :
 * ──────────────────────────
 * {
 *   "id": "550e8400-e29b-41d4-a716-446655440000",
 *   "numCDE": 12345,
 *   "typographie": "électronique",
 *   "affaire": "CLI001",
 *   "nbPieces": 1000,
 *   "cadence": 100,
 *   "heures": 10,
 *   "statut": "OU",
 *   "semaine": "S17",
 *   "emplacement": "A12",
 *   "commentaire": "Urgent client",
 *   ...
 * }
 *
 * GESTION D'ÉCHAPPEMENT JSON :
 * ────────────────────────────
 * JSON a des caractères spéciaux qui doivent être échappés :
 *   \" → quote
 *   \\ → backslash
 *   \/ → slash
 *   \b → backspace
 *   \f → form feed
 *   \n → newline
 *   \r → carriage return
 *   \t → tab
 *   \\uXXXX → caractère Unicode
 *
 * Exemple :
 *   Texte brut : "Lot avec \"guillemets\" et \\backslash"
 *   JSON : "Lot avec \\\"guillemets\\\" et \\\\backslash"
 *
 * Méthode esc() gère tous ces cas.
 * ⚠️  Erreur courante : oublier d'échapper → JSON invalide
 *
 * DEUX SENS :
 * ───────────
 * SÉRIALISATION (Objet → JSON) :
 *   serialiserLots(ArrayList<Lot>)
 *   serialiserLotSeul(Lot)
 *   serialiserSocietes(ArrayList<Societe>)
 *   Utilisé par : DonneesSauvegarder.sauvegarder*()
 *
 * DÉSÉRIALISATION (JSON → Objet) :
 *   deserialiserLots(String)
 *   deserialiserSocietes(String, lots)
 *   Utilisé par : ExcelReader.lireLotsJson()
 *
 * CHAMPS COMPLEXES :
 * ──────────────────
 * • ArrayList<Lot> dans Societe → sérialisée comme [numCDE1, numCDE2, ...]
 * • ArrayList<Ace> dans Societe → sérialisée comme [ { ace1 }, { ace2 } ]
 * • SuivieProd et Phase → sérialises avec flags (sp_nbPieceEtiq, phase_preTri, etc.)
 * • Methode → stockée en tant que nom (String)
 *
 * LIMITES :
 * ─────────
 * ⚠️  Pas de gestion de références circulaires (infinite loop si présentes)
 * ⚠️  Pas de versioning JSON (si structure change → migration manuelle)
 * ⚠️  Performance : O(n) pour chaque objet (pas optimal pour très gros volumes)
 * ⚠️  Validation : aucune (JSON mal formé peut crasher la désérialisation)
 *
 * POURQUOI PAS GSON/JACKSON ?
 * ────────────────────────────
 * • Contrôle total du format
 * • Pas de dépendances externes (simplification build)
 * • Léger et rapide pour nos besoins
 * • Évite la complexité de @SerializedName, @Expose, etc.
 *
 * ARCHITECTURE :
 * ──────────────
 * Classe 100% statique.
 * Appels typiques :
 *   String json = JsonSerialiser.serialiserLots(lots);
 *   ArrayList<Lot> restored = JsonSerialiser.deserialiserLots(json);
 *
 * HELPERS :
 * ─────────
 * • esc(String)        : échappe pour JSON
 * • unesc(String)      : déséchappe depuis JSON
 * • d2(double)         : formate double à 2 décimales
 * • getString()        : extrait valeur depuis JSON object
 * • ...
 *
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
			+ "\"lignesColisage\":"          + serialiserLignesColisage(lot.getLignesColisage()) + ","
			+ "\"sp_nbPieceEtiq\":"          + (sp != null ? sp.getNbPieceEtiq()          : 0) + ","
			+ "\"sp_nbPieceRepart\":"        + (sp != null ? sp.getNbPieceRepart()        : 0) + ","
			+ "\"sp_nbHeureEtiqRestant\":"   + (sp != null ? sp.getNbHeureEtiqRestant()   : 0) + ","
			+ "\"sp_nbHeureRepartRestant\":" + (sp != null ? sp.getNbHeureRepartRestant() : 0) + ","
			+ "\"phase_preTri\":"               + (ph != null && ph.isPreTri())                + ","
			+ "\"phase_surPiste\":"             + (ph != null && ph.isSurPiste())              + ","
			+ "\"phase_sortieEtiq\":"           + (ph != null && ph.isSortieEtiq())            + ","
			+ "\"phase_tri\":"                  + (ph != null && ph.isTri())                   + ","
			+ "\"phase_finit\":"                + (ph != null && ph.isFinit())
			+ "}";
	}

	public static String serialiserSocietes(ArrayList<Societe> societes)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < societes.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(serialiserSociete(societes.get(i)));
		}
		return sb.append("]").toString();
	}

	private static String serialiserSociete(Societe soc)
	{
		// IDs des lots affectés à la société
		StringBuilder lotsIds = new StringBuilder("[");
		for (int i = 0; i < soc.getLots().size(); i++) {
			if (i > 0) lotsIds.append(",");
			lotsIds.append(soc.getLots().get(i).getNumCDE());
		}
		lotsIds.append("]");

		// ACEs
		StringBuilder acesJson = new StringBuilder("[");
		for (int i = 0; i < soc.getAces().size(); i++) {
			if (i > 0) acesJson.append(",");
			acesJson.append(serialiserAce(soc.getAces().get(i)));
		}
		acesJson.append("]");

		return "{"
			+ "\"nom\":"           + esc(soc.getNom())         + ","
			+ "\"ce\":"            + esc(soc.getCe())          + ","
			+ "\"totalHeuresCE\":" + soc.getTotalHeuresCE()    + ","
			+ "\"effectifTotal\":" + soc.getEffectifTotal()    + ","
			+ "\"lotsIds\":"       + lotsIds                   + ","
			+ "\"aces\":"          + acesJson
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
			+ "\"nom\":"         + esc(ace.getNom())             + ","
			+ "\"nbPers\":"      + ace.getNbPers()               + ","
			+ "\"totalHeures\":" + ace.getTotalHeures()          + ","
			+ "\"effectif\":"    + ace.getEffectifActuel()       + ","
			+ "\"lotsIds\":"     + lotsIds
			+ "}";
	}

	public static String serialiserLignesColisage(ArrayList<LigneColisage> lignes)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < lignes.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(lignes.get(i).toJson());
		}
		return sb.append("]").toString();
	}

	public static String serialiserFicheRoute(FicheRoute fdr)
	{
		String nomSoc = fdr.getSociete() != null ? fdr.getSociete().getNom() : "";
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
			String obj = objets.get(idx);
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
		String lignesColisageStr = extraireBloc(obj, "lignesColisage");
		if (lignesColisageStr != null) {
			for (String ligneJson : extraireObjets(lignesColisageStr)) {
				LigneColisage ligne = LigneColisage.fromJson(ligneJson);
				lot.ajouterLigneColisage(ligne, ligne.getPcs());
			}
		}

		SuivieProd sp = new SuivieProd();
		sp.setLot(lot);
		sp.setNbPieceEtiq         (getInt(obj, "sp_nbPieceEtiq"));
		sp.setNbPieceRepart       (getInt(obj, "sp_nbPieceRepart"));
		sp.setNbHeureEtiqRestant  (Math.min(getInt(obj, "sp_nbHeureEtiqRestant"),   999999));
		sp.setNbHeureRepartRestant(Math.min(getInt(obj, "sp_nbHeureRepartRestant"), 999999));
		lot.setSuivieProd(sp);

		Phase ph = new Phase();
		ph.setPreTri    (getBool(obj, "ph_preTri")      || getBool(obj, "phase_preTri"));
		ph.setSurPiste  (getBool(obj, "ph_surPiste")    || getBool(obj, "phase_surPiste"));
		ph.setSortieEtiq(getBool(obj, "ph_sortieEtiq")  || getBool(obj, "phase_sortieEtiq"));
		ph.setTri       (getBool(obj, "ph_tri")         || getBool(obj, "phase_tri"));
		ph.setFinit     (getBool(obj, "ph_finit")       || getBool(obj, "phase_finit"));
		lot.setPhase(ph);

		return lot;
	}

	public static ArrayList<Societe> deserialiserSocietes(String json, ArrayList<Lot> lots)
	{
		ArrayList<Societe> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		ArrayList<String> socObjets = extraireObjets(json);
		for (int socIndex = 0; socIndex < socObjets.size(); socIndex++) {
			String obj = socObjets.get(socIndex);

			try {
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

				Societe soc = new Societe(
					getString(obj, "nom"),
					getString(obj, "ce"),
					aces,
					getInt   (obj, "totalHeuresCE")
				);
				soc.setEffectifTotal(getInt(obj, "effectifTotal"));

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

	// ── Alias pour ServeurHTTP et ControleurClient ────────────────────────
	public static int     extraireInt   (String json, String cle) { return getInt   (json, cle); }
	public static String  extraireString(String json, String cle) { return getString (json, cle); }
	public static boolean extraireBool  (String json, String cle) { return getBool   (json, cle); }
	public static double  extraireDouble(String json, String cle) { return getDouble (json, cle); }

	public static String getString(String obj, String cle)
	{
		String pattern = "\"" + cle + "\"";
		int pos = obj.indexOf(pattern);
		if (pos < 0) return "";

		// Chercher le ':' puis le '"' ouvrant de la valeur
		pos += pattern.length();
		while (pos < obj.length() && obj.charAt(pos) != '"' && obj.charAt(pos) != ':') pos++;
		if (pos >= obj.length() || obj.charAt(pos) == ':') {
			// sauter le ':' et les espaces pour trouver le '"' ouvrant
			while (pos < obj.length() && obj.charAt(pos) != '"') pos++;
		}
		if (pos >= obj.length()) return "";
		// Vérifier que ce n'est pas "null"
		int checkNull = pos;
		while (checkNull < obj.length() && obj.charAt(checkNull) != '"' && obj.charAt(checkNull) != 'n') checkNull++;
		if (checkNull < obj.length() && obj.charAt(checkNull) == 'n'
				&& obj.startsWith("null", checkNull)) return "";

		pos++; // saute le guillemet ouvrant

		StringBuilder sb = new StringBuilder();
		while (pos < obj.length())
		{
			char c = obj.charAt(pos);

			if (c == '\\' && pos + 1 < obj.length())
			{
				// ── CORRECTIF : décodage complet des séquences d'échappement ──
				char nx = obj.charAt(pos + 1);
				switch (nx)
				{
					case '"':  sb.append('"');  pos += 2; continue;
					case '\\': sb.append('\\'); pos += 2; continue;
					case '/':  sb.append('/');  pos += 2; continue; // ajout
					case 'n':  sb.append('\n'); pos += 2; continue;
					case 'r':  sb.append('\r'); pos += 2; continue; // ajout
					case 't':  sb.append('\t'); pos += 2; continue; // ajout
					case 'b':  sb.append('\b'); pos += 2; continue; // ajout
					case 'f':  sb.append('\f'); pos += 2; continue; // ajout
					case 'u':
						if (pos + 5 < obj.length())
						{
							try {
								int code = Integer.parseInt(obj.substring(pos + 2, pos + 6), 16);
								sb.append((char) code);
								pos += 6;
							} catch (NumberFormatException e) {
								sb.append('\\'); sb.append('u'); pos += 2;
							}
						} else {
							sb.append('\\'); pos++;
						}
						continue;
					default:
						// Séquence inconnue : on recopie les deux caractères
						sb.append('\\'); sb.append(nx); pos += 2; continue;
				}
			}

			if (c == '"') break; // guillemet fermant non échappé → fin de la valeur
			sb.append(c);
			pos++;
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
			|| obj.charAt(end) == '.' || obj.charAt(end) == 'E'
			|| obj.charAt(end) == 'e')) end++;
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
			|| obj.charAt(end) == '-' || obj.charAt(end) == '+' || obj.charAt(end) == 'E'
			|| obj.charAt(end) == 'e')) end++;
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

	/**
	 * Échappe une String pour l'insérer dans du JSON.
	 *
	 * CORRECTIF #8 — caractères manquants :
	 * L'original n'échappait que  \  "  \n
	 * Cette version échappe aussi \r  \t  et les caractères de contrôle < 0x20
	 *
	 * Exemple qui produisait du JSON invalide avant :
	 *   commentaire contenant un \t (tabulation) → JSON cassé, le client plantait
	 *   à la désérialisation suivante
	 */
	public static String esc(String s)
	{
		if (s == null) return "\"\"";
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':  sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n");  break;
				case '\r': sb.append("\\r");  break; // ajout
				case '\t': sb.append("\\t");  break; // ajout
				case '\b': sb.append("\\b");  break; // ajout
				case '\f': sb.append("\\f");  break; // ajout
				default:
					if (c < 0x20) { sb.append(String.format("\\u%04x", (int) c)); }
					else          { sb.append(c); }
			}
		}
		sb.append('"');
		return sb.toString();
	}

	private static double d2(double v)
	{
		if (Double.isInfinite(v) || Double.isNaN(v) || v > 1_000_000_000) return 0.0;
		return Math.round(v * 100.0) / 100.0;
	}
}