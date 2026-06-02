package app.metier.lot;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  LigneColisage — Détail d'une ligne de conditionnement carton pour un lot
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Représente UNE ligne dans le tableau de colisage d'un lot.
 * Un lot peut avoir plusieurs lignes de colisage quand les articles sont
 * conditionnés dans des formats de carton différents (cas rares).
 *
 * EXEMPLE :
 * ─────────
 *   Lot de 1 000 pièces :
 *     Ligne 1 : format "1/4", collisage 16 → 63 colis, 4 palettes
 *     Ligne 2 : format "1/2", collisage  8 → 125 colis, 16 palettes
 *
 * CALCULS DE recalculer(int pcs) :
 * ─────────────────────────────────
 *   nbColis    = ceil(pcs / collisage)
 *   parPalette = nombre de cartons par palette selon le format :
 *     "1/16" → 64   "1/8" → 32   "1/4" → 16   "1/2" → 8   "box" → 1
 *   nbPalettes = ceil(nbColis / parPalette)
 *
 * ⚠️  Si {@code collisage <= 0} ou {@code formatCarton} est null, {@code recalculer()}
 * ne fait rien (protection contre les divisions par zéro).
 *
 * LIEN AVEC Lot :
 * ───────────────
 * • {@code Lot.ajouterLigneColisage(ligne, pcs)} appelle {@code recalculer(pcs)}
 *   et décrémente {@code Lot.pcsUtiliser}.
 * • {@code Lot.supprimerLigneColisage(index)} restitue les pcs à {@code Lot.pcsUtiliser}.
 * • {@code Lot.recalculerLignesColisage()} rappelle {@code recalculer()} sur toutes
 *   les lignes quand {@code nbPieces} du lot change.
 *
 * PERSISTANCE :
 * ─────────────
 * Sérialisée dans le JSON du lot dans le tableau "lignesColisage" :
 *   { "formatCarton": "1/4", "collisage": 16, "nbColis": 63 }
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
public class LigneColisage
{
	/**
	 * Nombre de pièces affectées à cette ligne.
	 * Initialisé par {@code recalculer(pcs)} — égal au paramètre {@code pcs} passé.
	 */
	private int    pcs;

	/**
	 * Format du carton utilisé pour cette ligne.
	 * Valeurs autorisées (constantes {@code Lot.F_CARTON}) :
	 *   "", "1/16", "1/8", "1/4", "1/2", "box"
	 */
	private String formatCarton;

	/**
	 * Nombre de pièces par carton (ex : 16 pour un format 1/4).
	 * Utilisé comme diviseur dans le calcul de {@code nbColis}.
	 */
	private int    collisage;

	/**
	 * Nombre de cartons calculé = ceil(pcs / collisage).
	 * Mis à jour par {@code recalculer()}.
	 */
	private int    nbColis;

	/**
	 * Nombre de palettes calculé = ceil(nbColis / parPalette).
	 * {@code parPalette} dépend du format : 1/16→64, 1/8→32, 1/4→16, 1/2→8, box→1.
	 * Mis à jour par {@code recalculer()}.
	 */
	private int    nbPalettes;

	/**
	 * Construit une ligne de colisage avec son format et son collisage.
	 * Les champs calculés ({@code nbColis}, {@code nbPalettes}) restent à 0
	 * jusqu'au premier appel à {@code recalculer(pcs)}.
	 *
	 * @param formatCarton format du carton ("1/4", "1/2", etc.)
	 * @param collisage    nombre de pièces par carton
	 */
	public LigneColisage(String formatCarton, int collisage)
	{
		this.formatCarton = formatCarton;
		this.collisage    = collisage;
	}

	/**
	 * Calcule {@code nbColis} et {@code nbPalettes} à partir du nombre de pièces.
	 *
	 * Formules :
	 *   nbColis    = ceil(nbPieces / collisage)
	 *   parPalette = selon format : 1/16→64, 1/8→32, 1/4→16, 1/2→8, box→1, default→1
	 *   nbPalettes = ceil(nbColis / parPalette)
	 *
	 * ⚠️  Sans effet si {@code collisage <= 0} ou {@code formatCarton} est null.
	 *
	 * @param nbPieces nombre de pièces à conditionner sur cette ligne
	 */
	public void recalculer(int nbPieces)
	{
		if (collisage <= 0 || formatCarton == null) return;

		this.nbColis = (int) Math.ceil((double) nbPieces / collisage);

		// Nombre de cartons empilables par palette selon le format
		int parPalette;
		switch (formatCarton)
		{
			case "1/16": parPalette = 64; break;
			case "1/8":  parPalette = 32; break;
			case "1/4":  parPalette = 16; break;
			case "1/2":  parPalette =  8; break;
			case "box":  parPalette =  1; break;
			default:     parPalette =  1;
		}

		this.nbPalettes = (int) Math.ceil((double) nbColis / parPalette);
		this.pcs = nbPieces;
	}

	// ── Getters ───────────────────────────────────────────────────────────

	/** @return nombre de pièces affectées à cette ligne */
	public int    getPcs()          { return this.pcs;      }

	/** @return format du carton ("1/4", "1/2", etc.) */
	public String getFormatCarton() { return formatCarton;  }

	/** @return nombre de pièces par carton */
	public int    getCollisage()    { return collisage;     }

	/** @return nombre de cartons calculé (ceil(pcs / collisage)) */
	public int    getNbColis()      { return nbColis;       }

	/** @return nombre de palettes calculé */
	public int    getNbPalettes()   { return nbPalettes;    }

	// ── Setters ───────────────────────────────────────────────────────────

	/**
	 * Modifie le format du carton.
	 * ⚠️  Ne recalcule PAS automatiquement — appeler {@code recalculer(pcs)} si nécessaire.
	 *
	 * @param v nouveau format ("1/4", "1/2", etc.)
	 */
	public void setFormatCarton(String v) { this.formatCarton = v; }

	/**
	 * Modifie le nombre de pièces par carton.
	 * ⚠️  Ne recalcule PAS automatiquement — appeler {@code recalculer(pcs)} si nécessaire.
	 *
	 * @param v nouveau collisage
	 */
	public void setCollisage(int v)       { this.collisage    = v; }

	// ── Sérialisation JSON ────────────────────────────────────────────────

	/**
	 * Sérialise cette ligne en JSON compact.
	 *
	 * @return chaîne JSON de la forme :
	 *         {@code {"format":"1/4","collisage":16,"pcs":1000,"nbColis":63,"nbPalettes":4}}
	 */
	public String toJson()
	{
		return String.format(
			"{\"format\":\"%s\",\"collisage\":%d,\"pcs\":%d,\"nbColis\":%d,\"nbPalettes\":%d}",
			formatCarton, collisage, pcs, nbColis, nbPalettes);
	}

	/**
	 * Désérialise une ligne de colisage depuis un fragment JSON.
	 * Si {@code pcs} est absent ou nul dans le JSON, le recalcule depuis
	 * {@code nbColis × collisage} (rétrocompatibilité).
	 *
	 * @param obj fragment JSON de la ligne
	 * @return instance de {@code LigneColisage} recalculée
	 */
	public static LigneColisage fromJson(String obj)
	{
		String fmt = getString(obj, "format");
		int    col = getInt(obj, "collisage");
		int    pcs = getInt(obj, "pcs");
		if (pcs <= 0) pcs = getInt(obj, "nbColis") * col; // fallback rétrocompat
		LigneColisage ligne = new LigneColisage(fmt, col);
		ligne.recalculer(pcs);
		return ligne;
	}

	// ── Helpers JSON internes ─────────────────────────────────────────────

	/** Extrait une valeur string depuis un fragment JSON sans dépendance externe. */
	private static String getString(String obj, String cle)
	{
		String p = "\"" + cle + "\"";
		int pos = obj.indexOf(p);
		if (pos < 0) return "";
		pos = obj.indexOf('"', pos + p.length() + 1);
		if (pos < 0) return "";
		int end = obj.indexOf('"', pos + 1);
		if (end < 0) return "";
		return obj.substring(pos + 1, end);
	}

	/** Extrait une valeur entière depuis un fragment JSON sans dépendance externe. */
	private static int getInt(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return 0;
		pos += p.length();
		int end = pos;
		while (end < obj.length()
			&& (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
		try { return Integer.parseInt(obj.substring(pos, end)); }
		catch (NumberFormatException e) { return 0; }
	}
}