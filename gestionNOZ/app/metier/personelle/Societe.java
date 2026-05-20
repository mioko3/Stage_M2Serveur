package app.metier.personelle;

import app.metier.lot.Lot;
import java.util.ArrayList;

/**
 * Société de conditionnement.
 *
 * Règle heures :
 *   - totalHeuresCE = heures DISPONIBLES de la société (décrémenté à chaque affectation)
 *   - L'ACE n'a PAS de compteur d'heures propre : il reçoit des lots mais ses heures
 *     ne sont pas décomptées séparément (on ne risque pas de tomber en négatif sur l'ACE).
 */
public class Societe
{
	private String         nom;
	private String         ce;
	private ArrayList<Ace> aces;
	private int            effectifTotal;
	private int            totalHeuresCE;   // heures disponibles (décrémentées)
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

	public void calculHAces()
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
