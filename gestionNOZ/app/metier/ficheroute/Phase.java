package app.metier.ficheroute;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  Phase — Avancement des étapes de production d'un lot
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Suivi booléen de l'état d'avancement d'un lot au travers de ses étapes
 * physiques de production. Chaque booléen est {@code true} dès que l'étape
 * correspondante est démarrée (ou terminée selon le contexte).
 *
 * ORDRE DES ÉTAPES :
 * ──────────────────
 * Les étapes suivent généralement cet enchaînement logique :
 *
 *   1. preTri      — Tri préalable des articles / conditionnement avant piste
 *   2. surPiste    — Lot physiquement positionné sur la piste de production
 *   3. sortieEtiq  — Articles étiquetés et sortis de la piste
 *   4. tri         — Tri final (contrôle qualité, séparation)
 *   5. finit       — Lot entièrement terminé
 *                    ⚠️ Quand {@code finit = true}, PlanningGlobal enregistre
 *                       automatiquement la date/heure de fin dans {@code Lot.dateFin}.
 *
 * RÈGLE :
 * ───────
 * Les étapes ne sont PAS mutuellement exclusives — plusieurs peuvent être
 * {@code true} simultanément (avancement partiel, chevauchements réels).
 * La seule étape qui déclenche un effet de bord est {@code finit}.
 *
 * PERSISTANCE :
 * ─────────────
 * Sérialisée dans le JSON du lot avec le préfixe "ph_" :
 *   "ph_preTri", "ph_surPiste", "ph_sortieEtiq", "ph_tri", "ph_finit"
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */
public class Phase
{
	/**
	 * Étape 1 — Pré-tri effectué.
	 * Les articles ont été triés et préparés avant d'entrer en piste.
	 */
	private boolean preTri;

	/**
	 * Étape 2 — Lot sur piste.
	 * Le lot est physiquement positionné sur la piste de conditionnement.
	 */
	private boolean surPiste;

	/**
	 * Étape 3 — Sortie étiquetage.
	 * Les articles ont été étiquetés et peuvent sortir de la piste.
	 */
	private boolean sortieEtiq;

	/**
	 * Étape 4 — Tri final.
	 * Contrôle qualité et tri de séparation après étiquetage.
	 */
	private boolean tri;

	/**
	 * Étape 5 — Lot terminé.
	 * ⚠️  Effet de bord : quand ce flag passe à {@code true},
	 * {@code PlanningGlobal.modifierPhase()} enregistre la date/heure courante
	 * dans {@code Lot.dateFin}. Quand il repasse à {@code false}, {@code dateFin}
	 * est effacée.
	 */
	private boolean finit;

	// ── Getters ───────────────────────────────────────────────────────────

	/** @return {@code true} si le pré-tri a été effectué */
	public boolean isPreTri()      { return preTri;     }

	/** @return {@code true} si le lot est sur piste */
	public boolean isSurPiste()    { return surPiste;   }

	/** @return {@code true} si l'étiquetage est sorti */
	public boolean isSortieEtiq()  { return sortieEtiq; }

	/** @return {@code true} si le tri final est effectué */
	public boolean isTri()         { return tri;        }

	/**
	 * @return {@code true} si le lot est entièrement terminé.
	 * ⚠️  Ce flag déclenche l'enregistrement de {@code Lot.dateFin}.
	 */
	public boolean isFinit()       { return finit;      }

	// ── Setters ───────────────────────────────────────────────────────────

	/** @param v {@code true} pour marquer le pré-tri comme effectué */
	public void setPreTri(boolean v)     { this.preTri     = v; }

	/** @param v {@code true} pour marquer le lot comme sur piste */
	public void setSurPiste(boolean v)   { this.surPiste   = v; }

	/** @param v {@code true} pour marquer la sortie étiquetage comme effectuée */
	public void setSortieEtiq(boolean v) { this.sortieEtiq = v; }

	/** @param v {@code true} pour marquer le tri final comme effectué */
	public void setTri(boolean v)        { this.tri        = v; }

	/**
	 * Marque ou démarque le lot comme terminé.
	 * ⚠️  Ne pas appeler directement depuis l'IHM — passer par
	 * {@code PlanningGlobal.modifierPhase()} pour que {@code Lot.dateFin}
	 * soit mis à jour correctement.
	 *
	 * @param v {@code true} pour clôturer le lot
	 */
	public void setFinit(boolean v)      { this.finit      = v; }
}