package app.metier.ficheroute;

import app.metier.lot.Lot;
import app.metier.personelle.Societe;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  FicheRoute — Récapitulatif financier et de production d'une société
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Calcule et regroupe les indicateurs de synthèse pour UNE société :
 *   • Valeur de vente totale (somme des VVS de tous ses lots)
 *   • Nombre de pièces total à produire
 *   • Prix unitaire moyen sur l'ensemble des lots
 *
 * UTILISATION :
 * ─────────────
 * Instancié à la demande via PlanningGlobal.genererFicheRoute(societe).
 * Affiché dans PanelFicheRoute (IHM) et exporté via GET /ficheroute/{nom}.
 *
 * ⚠️  CHAMP effectif NON CALCULÉ :
 * ────────────────────────────────
 * Le champ {@code effectif} est déclaré mais n'est pas alimenté dans le constructeur.
 * Il reste à 0 par défaut. Si l'effectif doit apparaître dans la fiche, alimenter
 * via {@code societe.getEffectifTotal()} ici ou dans le setter.
 *
 * CALCULS EFFECTUÉS À LA CONSTRUCTION :
 * ──────────────────────────────────────
 *   1. sommeVVS       = Σ lot.getValeurVente()   sur tous les lots de la société
 *   2. sommePieces    = Σ lot.getNbPieces()       sur tous les lots de la société
 *   3. prixUnitaireMoy= moyenne de lot.getPrixUnitaire() (lots à 0 pièce exclus)
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
public class FicheRoute
{
	/** Société pour laquelle cette fiche est générée. */
	private Societe societe;

	/** Somme des valeurs de vente de tous les lots de la société (en euros). */
	private int sommeVVS;

	/** Nombre total de pièces à produire pour l'ensemble des lots. */
	private int sommePieces;

	/** Prix unitaire moyen calculé sur les lots ayant au moins une pièce. */
	private double prixUntaireMoy;

	/**
	 * Effectif total de la société.
	 * ⚠️  Non calculé automatiquement — reste à 0 sauf appel explicite à setEffectif().
	 * À alimenter avec societe.getEffectifTotal() si nécessaire.
	 */
	private int effectif;

	/**
	 * Construit la fiche de route pour la société donnée.
	 * Les trois indicateurs (VVS, pièces, prix unitaire moyen) sont calculés
	 * immédiatement lors de la construction.
	 *
	 * @param societe la société dont on veut la synthèse
	 */
	public FicheRoute(Societe societe)
	{
		this.societe = societe;
		this.sommeVVS = 0;
		this.sommePieces = 0;
		this.prixUntaireMoy = 0;
		this.effectif = 0;

		calculSommeVVS();
		calculSommePieces();
		if (!societe.getLots().isEmpty()) calculPrixUnit();
	}

	// ── Calculs internes ─────────────────────────────────────────────────

	/**
	 * Additionne la valeur de vente (VVS) de chaque lot de la société.
	 * Résultat stocké dans {@code sommeVVS}.
	 *
	 * @return la somme calculée (identique à {@code sommeVVS} après appel)
	 */
	private int calculSommeVVS()
	{
		for (Lot l : this.societe.getLots())
		{
			this.sommeVVS += l.getValeurVente();
		}
		return sommeVVS;
	}

	/**
	 * Additionne le nombre de pièces de chaque lot de la société.
	 * Résultat stocké dans {@code sommePieces}.
	 *
	 * @return la somme calculée (identique à {@code sommePieces} après appel)
	 */
	private int calculSommePieces()
	{
		for (Lot l : this.societe.getLots())
		{
			this.sommePieces += l.getNbPieces();
		}
		return sommePieces;
	}

	/**
	 * Calcule le prix unitaire moyen sur les lots ayant au moins une pièce.
	 * Les lots avec {@code nbPieces == 0} sont exclus pour éviter les divisions par zéro
	 * et les distorsions de la moyenne.
	 * Résultat stocké dans {@code prixUntaireMoy}.
	 *
	 * @return le prix unitaire moyen, ou 0 si aucun lot éligible
	 */
	private double calculPrixUnit()
	{
		int cpt = 0;
		double moyPrixUnit = 0.0;
		for (Lot l : this.societe.getLots())
		{
			if (l.getNbPieces() == 0) continue;
			moyPrixUnit += l.getPrixUnitaire();
			cpt++;
		}
		return this.prixUntaireMoy = (cpt == 0) ? 0 : moyPrixUnit / cpt;
	}

	// ── Getters / Setters ─────────────────────────────────────────────────

	/** @return la société associée à cette fiche */
	public Societe getSociete()           { return societe;        }

	/** @return la somme des valeurs de vente (€) de tous les lots */
	public int     getSommeVVS()          { return sommeVVS;       }

	/** @return le nombre total de pièces de tous les lots */
	public int     getSommePieces()       { return sommePieces;    }

	/** @return le prix unitaire moyen sur les lots à pièces non nulles */
	public double  getPrixUntaireMoy()    { return prixUntaireMoy; }

	/**
	 * @return l'effectif de la société.
	 * ⚠️  Vaut 0 si non alimenté manuellement via {@code setEffectif()}.
	 */
	public int     getEffectif()          { return effectif;       }

	/**
	 * Remplace la société et force le recalcul de tous les indicateurs.
	 * À appeler si la fiche doit être mise à jour après un changement de lots.
	 *
	 * @param societe la nouvelle société
	 */
	public void setSociete(Societe societe)
	{
		this.societe = societe;
		// Réinitialiser et recalculer
		this.sommeVVS       = 0;
		this.sommePieces    = 0;
		this.prixUntaireMoy = 0;
		calculSommeVVS();
		calculSommePieces();
		if (!societe.getLots().isEmpty()) calculPrixUnit();
	}

	public void setSommeVVS(int sommeVVS)                { this.sommeVVS       = sommeVVS;       }
	public void setSommePieces(int sommePieces)          { this.sommePieces    = sommePieces;    }
	public void setPrixUntaireMoy(double prixUntaireMoy) { this.prixUntaireMoy = prixUntaireMoy; }

	/**
	 * Permet d'alimenter manuellement l'effectif si nécessaire.
	 * Exemple : {@code fiche.setEffectif(societe.getEffectifTotal())}
	 *
	 * @param effectif effectif total de la société
	 */
	public void setEffectif(int effectif)                { this.effectif       = effectif;       }
}