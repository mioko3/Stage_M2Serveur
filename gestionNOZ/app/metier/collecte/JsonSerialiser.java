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
 *
 *  Convertit les objets Java ↔ JSON (texte).
 *  Utilisé par le serveur pour répondre aux clients,
 *  et par le client pour envoyer/recevoir des données.
 *
 *  Aucune librairie externe — compatible avec votre projet
 *  sans Maven ni Gradle.
 * ══════════════════════════════════════════════════════════════
 */
public class JsonSerialiser
{
	// ══════════════════════════════════════════════════════════════════════
	//  SÉRIALISATION : Java → JSON
	// ══════════════════════════════════════════════════════════════════════

	/** Convertit la liste complète des lots en tableau JSON. */
	public static String serialiserLots(ArrayList<Lot> lots)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < lots.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(serialiserLotSeul(lots.get(i)));
		}
		return sb.append("]").toString();
	}

	/** Convertit un seul lot en objet JSON. */
	public static String serialiserLotSeul(Lot lot)
	{
		SuivieProd sp = lot.getSuivieProd();
		Phase      ph = lot.getPhase();
		return "{"
			+ "\"numCDE\":"                  + lot.getNumCDE()                              + ","
			+ "\"typologie\":"               + esc(lot.getTypologie())                      + ","
			+ "\"affaire\":"                 + esc(lot.getAffaire())                        + ","
			+ "\"nbPieces\":"                + lot.getNbPieces()                            + ","
			+ "\"cadence\":"                 + lot.getCadence()                             + ","
			+ "\"heures\":"                  + lot.getHeures()                              + ","
			+ "\"heuresAce\":"               + lot.getHeuresAce()                           + ","
			+ "\"valeurVente\":"             + lot.getValeurVente()                         + ","
			+ "\"prixUnitaire\":"            + lot.getPrixUnitaire()                        + ","
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
			+ "\"methode\":"                 + esc(lot.getMethode().getNom())               + ","
			+ "\"distribution\":"            + esc(lot.getDistribution())                   + ","
			+ "\"formatCarton\":"            + esc(lot.getFormatCarton())                   + ","
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

	/**
	 * Convertit la liste des sociétés en tableau JSON.
	 * Les sociétés contiennent uniquement les numCDE des lots (pas les objets complets)
	 * pour éviter la duplication avec le tableau /lots.
	 */
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
		// IDs des lots affectés à cette société
		StringBuilder lotsIds = new StringBuilder("[");
		for (int i = 0; i < soc.getLots().size(); i++) {
			if (i > 0) lotsIds.append(",");
			lotsIds.append(soc.getLots().get(i).getNumCDE());
		}
		lotsIds.append("]");

		// Liste des ACE
		StringBuilder aces = new StringBuilder("[");
		for (int i = 0; i < soc.getAces().size(); i++) {
			if (i > 0) aces.append(",");
			aces.append(serialiserAce(soc.getAces().get(i)));
		}
		aces.append("]");

		return "{"
			+ "\"nom\":"            + esc(soc.getNom())           + ","
			+ "\"ce\":"             + esc(soc.getCe())            + ","
			+ "\"effectifTotal\":"  + soc.getEffectifTotal()      + ","
			+ "\"totalHeuresCE\":"  + soc.getTotalHeuresCE()      + ","
			+ "\"lotsIds\":"        + lotsIds                     + ","
			+ "\"aces\":"           + aces
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
			+ "\"nom\":"            + esc(ace.getNom())           + ","
			+ "\"nbPers\":"         + ace.getNbPers()             + ","
			+ "\"totalHeures\":"    + ace.getTotalHeures()        + ","
			+ "\"effectif\":"       + ace.getEffectifActuel()     + ","
			+ "\"lotsIds\":"        + lotsIds
			+ "}";
	}

	/** Sérialise une FicheRoute pour GET /ficheroute/{nom}. */
	public static String serialiserFicheRoute(FicheRoute fdr)
	{
		String nomSoc = (fdr.getSociete() != null) ? fdr.getSociete().getNom() : "";
		return "{"
			+ "\"nomSociete\":"   + esc(nomSoc)                + ","
			+ "\"sommeVVS\":"     + fdr.getSommeVVS()          + ","
			+ "\"sommePieces\":"  + fdr.getSommePieces()       + ","
			+ "\"prixUnitMoy\":"  + fdr.getPrixUntaireMoy()    + ","
			+ "\"effectif\":"     + fdr.getEffectif()
			+ "}";
	}

	// ══════════════════════════════════════════════════════════════════════
	//  DÉSÉRIALISATION : JSON → Java
	// ══════════════════════════════════════════════════════════════════════

	/** Convertit un tableau JSON en liste de Lot. */
	public static ArrayList<Lot> deserialiserLots(String json)
	{
		ArrayList<Lot> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json)) {
			try { liste.add(deserialiserLot(obj)); }
			catch (Exception e) { System.err.println("[Json] Lot ignoré : " + e.getMessage()); }
		}
		return liste;
	}

	/** Convertit un objet JSON en Lot. */
	public static Lot deserialiserLot(String obj)
	{
		int    numCDE       = getInt   (obj, "numCDE");
		int    nbPieces     = getInt   (obj, "nbPieces");
		double cadence      = getDouble(obj, "cadence");
		double heures       = getDouble(obj, "heures");
		int    valeurVente  = getInt   (obj, "valeurVente");
		String statut       = getString(obj, "statut");
		String statutEchant = getString(obj, "statutEchant");

		Lot lot = new Lot(numCDE, nbPieces, cadence, heures, valeurVente, statut, statutEchant);
		lot.setTypologie    (getString(obj, "typologie"));
		lot.setAffaire      (getString(obj, "affaire"));
		lot.setPrixUnitaire (getDouble(obj, "prixUnitaire"));
		lot.setHeuresAce    (getDouble(obj, "heuresAce"));
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

		SuivieProd sp = new SuivieProd();
		sp.setLot(lot);
		sp.setNbPieceEtiq         (getInt(obj, "sp_nbPieceEtiq"));
		sp.setNbPieceRepart       (getInt(obj, "sp_nbPieceRepart"));
		sp.setNbHeureEtiqRestant  (getInt(obj, "sp_nbHeureEtiqRestant"));
		sp.setNbHeureRepartRestant(getInt(obj, "sp_nbHeureRepartRestant"));
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

	/**
	 * Convertit un tableau JSON de sociétés en objets Societe.
	 * Retrouve les Lot par numCDE depuis la liste fournie.
	 */
	public static ArrayList<Societe> deserialiserSocietes(String json, ArrayList<Lot> lots)
	{
		ArrayList<Societe> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json)) {
			try {
				// 1. Construire les ACE d'abord (requis par le constructeur Societe)
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

				// 2. Créer la Societe
				Societe soc = new Societe(
					getString(obj, "nom"),
					getString(obj, "ce"),
					aces,
					getInt   (obj, "totalHeuresCE")
				);
				soc.setEffectifTotal(getInt(obj, "effectifTotal"));

				// 3. Re-lier les lots de la société (ajout direct dans la liste, sans décompter
				//    les heures car elles sont déjà dans le JSON)
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

	// ══════════════════════════════════════════════════════════════════════
	//  UTILITAIRES D'EXTRACTION — parseur JSON minimal
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
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0; }
	}

	public static double getDouble(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return 0.0;
		pos += p.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		int end = pos;
		while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '.' || obj.charAt(end) == '-' || obj.charAt(end) == 'E' || obj.charAt(end) == 'e')) end++;
		try { return Double.parseDouble(obj.substring(pos, end)); } catch (NumberFormatException e) { return 0.0; }
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

	// Alias courts utilisés par ServeurHTTP et ControleurClient
	public static int     extraireInt   (String json, String cle) { return getInt   (json, cle); }
	public static String  extraireString(String json, String cle) { return getString (json, cle); }
	public static boolean extraireBool  (String json, String cle) { return getBool   (json, cle); }
	public static double  extraireDouble(String json, String cle) { return getDouble (json, cle); }

	/**
	 * Extrait un bloc JSON (objet ou tableau) associé à une clé.
	 * Ex: extraireBloc(json, "\"lots\"") → "[{...},{...}]"
	 */
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

	/** Extrait un tableau de primitifs : "lotsIds":[1,2,3] → "[1,2,3]" */
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

	/** Extrait tous les objets JSON {} d'un tableau [...]. */
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

	/** Parse un tableau JSON d'entiers [1,2,3]. */
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

	/** Échappe une chaîne pour inclusion dans du JSON. */
	public static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
	}

	 /**
	 * Désérialise une liste d'ACE depuis un tableau JSON.
	 * Format attendu : [{"nom":"A","nbPers":3,"effectif":2}, ...]
	 * (sans lotsIds — utilisé pour mettreAJourAces)
	 */
	public static ArrayList<Ace> deserialiserAces(String json)
	{
		ArrayList<Ace> liste = new ArrayList<>();
		if (json == null || json.isBlank()) return liste;
		for (String obj : extraireObjets(json))
		{
			try
			{
				Ace ace = new Ace(
					getString(obj, "nom"),
					getInt   (obj, "nbPers"),
					0,                          // totalHeures non transmis
					getInt   (obj, "effectif")
				);
				liste.add(ace);
			}
			catch (Exception e)
			{
				System.err.println("[Json] ACE ignorée : " + e.getMessage());
			}
		}
		return liste;
	}
 
	/**
	 * Désérialise une FicheRoute depuis JSON (réponse de GET /ficheroute/{nom}).
	 * Le JSON ne contient que les totaux — la FicheRoute est reconstruite
	 * côté client uniquement pour affichage.
	 */
	public static FicheRoute deserialiserFicheRoute(String json, Societe societe)
	{
		FicheRoute fr = new FicheRoute(societe);
		// La FicheRoute se construit depuis la société — le JSON sert
		// juste à confirmer les données. On retourne la fiche générée localement.
		return fr;
	}
}
