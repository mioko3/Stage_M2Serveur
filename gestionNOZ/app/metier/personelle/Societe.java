package app.metier.personelle;

import app.metier.lot.Lot;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  Societe — Représentation d'un atelier de conditionnement
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Modèle représentant une SOCIÉTÉ DE CONDITIONNEMENT.
 * Une société regroupe :
 *   • Plusieurs chefs d'ACE (Atelier Conditionnement Expédition)
 *   • Chaque ACE a ses effectifs et ses lots assignés
 *   • La société a un "budget heures" global (totalHeuresCE) décrémenté progressivement
 *
 * HIÉRARCHIE :
 * ────────────
 *   Societe
 *     ├─ nom, ce (CE = Collectif Entreprise)
 *     ├─ ArrayList<Ace> — liste des ACE de cette société
 *     │   └─ Ace
 *     │       ├─ nom, nbPers, effectifActuel
 *     │       └─ ArrayList<Lot> — lots assignés à cet ACE
 *     └─ ArrayList<Lot> — tous les lots (doublon, pour accès rapide)
 *
 * RÈGLE HEURES CRITIQUE :
 * ────────────────────────
 * • totalHeuresCE = heures DISPONIBLES de la société (décrémentées progressivement)
 * • L'ACE N'A PAS de compteur d'heures propre (valeur informative via setTotalHeures())
 * • Affectation lot → société = décrémente totalHeuresCE
 * • Affectation lot → ACE = n'affecte PAS l'ACE (juste ref)
 *
 * Exemple :
 *   totalHeuresCE initial = 1000 heures
 *   Affectation lot(100h) à ACE1 → totalHeuresCE = 900
 *   Affectation lot(50h) à ACE2  → totalHeuresCE = 850
 *   → L'ACE1 et ACE2 ont chacun un totalHeures informatif, pas de compte propre
 *
 * CALCUL DE RÉPARTITION :
 * ──────────────────────
 * Les heures disponibles totalHeuresCE sont réparties PROPORTIONNELLEMENT
 * entre les ACE selon leur effectif :
 *   Heures_ACE = totalHeuresCE * (effectif_ACE / effectif_total)
 *
 * Exemple : totalHeuresCE=1000, ACE1(5 pers), ACE2(3 pers), ACE3(2 pers)
 *   ACE1 = 1000 * 5/10 = 500 h
 *   ACE2 = 1000 * 3/10 = 300 h
 *   ACE3 = 1000 * 2/10 = 200 h
 *
 * AFFECTATION DE LOTS :
 * ────────────────────
 * ajouterLot(lot, ace)          : ajoute lot à la société + l'ACE
 *                                décremente totalHeuresCE
 * ajouterLotSansHeures(lot, ace): ajoute lot SANS décrémenter heures
 *                                (fallback pour dépannage)
 * enleverLot(lot)               : retire lot, restitue heures
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */

public class Societe
{
	/** Nom de la société (ex: "Arcile", "Prat", "Carton Bretagne") */
	private String         nom;
	
	/** Code CE (Collectif Entreprise) utilisé pour les cotisations */
	private String         ce;
	
	/** Liste des ACE (chefs d'ateliers) de cette société */
	private ArrayList<Ace> aces;
	
	/** Effectif total calculé = somme des effectifs de tous les ACE */
	private int            effectifTotal;
	
	/** Heures disponibles de la société (décrémentées à chaque affectation lot) */
	private int            totalHeuresCE;
	
	/** Tous les lots assignés à cette société (rapide accès) */
	private ArrayList<Lot> lots;

	public Societe(String nom, String ce, ArrayList<Ace> aces, int totalHeuresCE)
	{
		this.nom           = nom;
		this.ce            = ce;
		this.aces          = aces;
		this.effectifTotal = 0;
		calculHAces();
		this.totalHeuresCE = totalHeuresCE;
		this.lots          = new ArrayList<>();
	}

	// ── Affectation ───────────────────────────────────────────────────────

	/**
	 * Affecte un lot à cette société.
	 * Décrémente les heures disponibles de la société.
	 * L'ACE n'est PAS impacté en heures (peut être null).
	 */
	public void ajouterLot(Lot lot , Ace ace)
	{
		if (!lots.contains(lot))
		{
			lots.add(lot);
			totalHeuresCE -= (int) Math.ceil(lot.getHeures());
			if (ace != null)
				ace.donnerLotACE(lot);
		}
	}

	public void ajouterLotSansHeures(Lot lot, Ace ace)
	{
		if (!lots.contains(lot))
		{
			lots.add(lot);
			if (ace != null)
				ace.donnerLotACE(lot);
		}
	}

	/**
	 * Retire un lot de cette société.
	 * Restitue les heures à la société.
	 */
	public void enleverLot(Lot lot)
	{
		if (lots.remove(lot))
			totalHeuresCE += (int) Math.ceil(lot.getHeures());
	}

	public void enleverLotACE(Ace ace, Lot lot)
	{
		if (lots.contains(lot))
			ace.enleverLotACE(lot);
	}

	private void calculHAces()
	{
		int totalEffectif = 0;

		// 1. Calcul de l'effectif total
		for (Ace ace : this.aces)
		{
			totalEffectif += ace.getEffectifActuel();
		}

		// 2. Répartition proportionnelle des heures
		for (Ace ace : this.aces)
		{
			if (totalEffectif > 0)
			{
				int heures = (int) Math.round(
					(double) totalHeuresCE * ace.getEffectifActuel() / totalEffectif
				);
				ace.setTotalHeures(heures);
			}
			else
			{
				ace.setTotalHeures(0);
			}
		}
	}

	// ── Getters / Setters ─────────────────────────────────────────────────
	public String         getNom()           { return nom;          }
	public String         getCe()            { return ce;           }
	public ArrayList<Ace> getAces()          { return aces;         }
	public int            getEffectifTotal() { return effectifTotal;}
	public int            getTotalHeuresCE() { return totalHeuresCE;}
	public ArrayList<Lot> getLots()          { return lots;         }
	public Ace            getAce(String nomAce)
	{ return aces.stream().filter(a -> a.getNom().equals(nomAce)).findFirst().orElse(null); }

	public void setNom(String v)             { this.nom          = v; }
	public void setCe(String v)              { this.ce           = v; }
	public void setAces(ArrayList<Ace> v)    { this.aces         = v; }
	public void setEffectifTotal(int v)      { this.effectifTotal= v; }
	public void setTotalHeuresCE(int v)      { this.totalHeuresCE= v; }
}
