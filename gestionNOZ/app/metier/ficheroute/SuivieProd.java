package app.metier.ficheroute;

import app.metier.lot.Lot;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  SuivieProd — Suivi de l'avancement de production d'un lot
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Mesure l'avancement réel d'un lot en deux axes indépendants :
 *   • Étiquetage  : combien de pièces ont été étiquetées ?
 *   • Répartition : combien de pièces ont été réparties/distribuées ?
 *
 * À chaque mise à jour, les pourcentages d'avancement et les heures restantes
 * sont recalculés automatiquement.
 *
 * FORMULES :
 * ──────────
 *   avancementEtiqPct   = nbPieceEtiq   / lot.getNbPieces() × 100
 *   avancementPartsPct  = nbPieceRepart / lot.getNbPieces() × 100
 *
 *   nbHeureEtiqRestant   = heuresAce × (1 − nbPieceEtiq   / nbPieces)
 *   nbHeureRepartRestant = heuresAce × (1 − nbPieceRepart / nbPieces)
 *
 * DÉCLENCHEMENT DU RECALCUL :
 * ────────────────────────────
 * {@code miseAJJourAvancement()} est appelé automatiquement par :
 *   • {@code setNbPieceEtiq()}
 *   • {@code setNbPieceRepart()}
 *   • {@code setLot()}          (initialisation du lien)
 *   • {@code Lot.calculHeuresPiste()} (quand les heures par personne changent)
 *
 * PERSISTANCE :
 * ─────────────
 * Sérialisée dans le JSON du lot avec le préfixe "sp_" :
 *   "sp_nbPieceEtiq", "sp_nbPieceRepart",
 *   "sp_nbHeureEtiqRestant", "sp_nbHeureRepartRestant"
 * Les pourcentages sont recalculés à la désérialisation, non stockés.
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
public class SuivieProd
{
	/** Nombre de pièces déjà étiquetées (0 → lot.getNbPieces()). */
	private int    nbPieceEtiq;

	/** Nombre de pièces déjà réparties / distribuées (0 → lot.getNbPieces()). */
	private int    nbPieceRepart;

	/**
	 * Heures d'étiquetage restantes estimées.
	 * Calculé = heuresAce × (1 − nbPieceEtiq / nbPieces).
	 * Capé à 999 999 lors de la désérialisation pour éviter les valeurs aberrantes.
	 */
	private double nbHeureEtiqRestant;

	/**
	 * Heures de répartition restantes estimées.
	 * Calculé = heuresAce × (1 − nbPieceRepart / nbPieces).
	 * Capé à 999 999 lors de la désérialisation pour éviter les valeurs aberrantes.
	 */
	private double nbHeureRepartRestant;

	/** Pourcentage d'avancement étiquetage, formaté "xx.x" (sans le symbole %). */
	private String avancementEtiqPct;

	/** Pourcentage d'avancement répartition, formaté "xx.x" (sans le symbole %). */
	private String avancementPartsPct;

	/**
	 * Référence au lot parent.
	 * Nécessaire pour accéder à {@code nbPieces} et {@code heuresAce} lors des recalculs.
	 * Initialisé via {@code setLot()} — jamais null après initialisation correcte.
	 */
	private Lot lot;

	/**
	 * Constructeur — initialise tout à zéro.
	 * Appeler {@code setLot()} immédiatement après pour activer les recalculs.
	 */
	public SuivieProd()
	{
		this.nbPieceEtiq          = 0;
		this.nbPieceRepart        = 0;
		this.nbHeureEtiqRestant   = 0;
		this.nbHeureRepartRestant = 0;
		this.avancementEtiqPct    = "0";
		this.avancementPartsPct   = "0";
	}

	// ── Getters ───────────────────────────────────────────────────────────

	/** @return nombre de pièces étiquetées */
	public int    getNbPieceEtiq()           { return nbPieceEtiq;          }

	/** @return nombre de pièces réparties */
	public int    getNbPieceRepart()         { return nbPieceRepart;        }

	/** @return heures d'étiquetage restantes estimées */
	public double getNbHeureEtiqRestant()    { return nbHeureEtiqRestant;   }

	/** @return heures de répartition restantes estimées */
	public double getNbHeureRepartRestant()  { return nbHeureRepartRestant; }

	/** @return avancement étiquetage formaté "xx.x %" */
	public String getAvancementEtiqPct()     { return avancementEtiqPct  + " %"; }

	/** @return avancement répartition formaté "xx.x %" */
	public String getAvancementPartsPct()    { return avancementPartsPct + " %"; }

	/** @return le lot parent associé à ce suivi */
	public Lot    getLot()                   { return lot;                  }

	// ── Setters ───────────────────────────────────────────────────────────

	/**
	 * Met à jour le nombre de pièces étiquetées et recalcule l'avancement.
	 *
	 * @param v nombre de pièces étiquetées (doit être ≤ lot.getNbPieces())
	 */
	public void setNbPieceEtiq(int v)
	{
		this.nbPieceEtiq = v;
		miseAJJourAvancement();
	}

	/**
	 * Met à jour le nombre de pièces réparties et recalcule l'avancement.
	 *
	 * @param v nombre de pièces réparties (doit être ≤ lot.getNbPieces())
	 */
	public void setNbPieceRepart(int v)
	{
		this.nbPieceRepart = v;
		miseAJJourAvancement();
	}

	/**
	 * Définit les heures d'étiquetage restantes directement (depuis désérialisation).
	 * Ne déclenche PAS de recalcul — valeur brute restaurée depuis JSON.
	 *
	 * @param v heures restantes (capé à 999 999 côté JsonSerialiser)
	 */
	public void setNbHeureEtiqRestant(double v)    { this.nbHeureEtiqRestant   = v; }

	/**
	 * Définit les heures de répartition restantes directement (depuis désérialisation).
	 * Ne déclenche PAS de recalcul — valeur brute restaurée depuis JSON.
	 *
	 * @param v heures restantes (capé à 999 999 côté JsonSerialiser)
	 */
	public void setNbHeureRepartRestant(double v)  { this.nbHeureRepartRestant = v; }

	/**
	 * Associe ce suivi à son lot parent et déclenche un premier recalcul.
	 * Doit être appelé juste après la construction ou la désérialisation.
	 *
	 * @param lot le lot dont ce SuivieProd mesure l'avancement
	 */
	public void setLot(Lot lot)
	{
		this.lot = lot;
		miseAJJourAvancement();
	}

	// ── Recalcul ──────────────────────────────────────────────────────────

	/**
	 * Recalcule les pourcentages d'avancement et les heures restantes.
	 *
	 * Formules :
	 *   avancement (%) = nbPiece[x] / lot.getNbPieces() × 100
	 *   heuresRestantes = lot.getHeuresAce() × (1 − nbPiece[x] / lot.getNbPieces())
	 *
	 * Si {@code lot} est null ou {@code nbPieces == 0}, les indicateurs restent à "0".
	 */
	public void miseAJJourAvancement()
	{
		if (this.lot != null && this.lot.getNbPieces() > 0)
		{
			// Pourcentages d'avancement (format "xx.x")
			this.avancementEtiqPct  = String.format("%.1f",
				100.0 * this.nbPieceEtiq   / this.lot.getNbPieces());
			this.avancementPartsPct = String.format("%.1f",
				100.0 * this.nbPieceRepart / this.lot.getNbPieces());

			// Heures restantes (basées sur heuresAce = heures / nbPersonnes)
			if (this.lot.getHeures() > 0)
			{
				this.nbHeureEtiqRestant =
					arrondi2(this.lot.getHeuresAce()
						* (1 - this.nbPieceEtiq   / (double) this.lot.getNbPieces()));

				this.nbHeureRepartRestant =
					arrondi2(this.lot.getHeuresAce()
						* (1 - this.nbPieceRepart / (double) this.lot.getNbPieces()));
			}
		}
		else
		{
			this.avancementEtiqPct  = "0";
			this.avancementPartsPct = "0";
		}
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	/**
	 * Arrondit une valeur à 2 décimales.
	 *
	 * @param val valeur à arrondir
	 * @return valeur arrondie
	 */
	private double arrondi2(double val)
	{
		return Math.round(val * 100.0) / 100.0;
	}
}