package app.metier.personelle;

import app.metier.lot.Lot;
import java.awt.Color;
import java.util.ArrayList;

/**
 * Chef d'ACE (Atelier de Conditionnement et d'Expédition).
 *
 * IMPORTANT : les heures de l'ACE ne sont PAS décomptées lors d'une affectation.
 *             Seules les heures de la SOCIÉTÉ sont décomptées (totalHeuresCE).
 *             L'ACE sert uniquement à identifier le responsable d'un lot.
 */
public class Ace
{
	private String         nom;
	private int            nbPers;
	private int            totalHeures;      // ← Ajouté : heures théoriques de l'ACE
	private int            effectifActuel;
	private boolean        estMachine;      // ← Ajouté : indique si l'ACE a une machine
	private Color          col;
	private ArrayList<Lot> lots;

	public Ace(String nom, int nbPers, int effectifActuel)
	{
		this.nom            = nom;
		this.nbPers         = nbPers;
		this.totalHeures    = 0;  // Par défaut, sera mis à jour si nécessaire
		this.effectifActuel = effectifActuel;
		this.estMachine     = false;  // Par défaut, pas de machine
		this.lots           = new ArrayList<>();
		trouverColor();
	}

	// ← Constructeur alternatif avec totalHeures
	public Ace(String nom, int nbPers, int totalHeures, int effectifActuel)
	{
		this.nom            = nom;
		this.nbPers         = nbPers;
		this.totalHeures    = totalHeures;
		this.effectifActuel = effectifActuel;
		this.estMachine     = false;  // Par défaut, pas de machine
		this.lots           = new ArrayList<>();
		trouverColor();
	}

	private void trouverColor()
	{
		Color colr;
		int a = this.nom.length() * 20;
		int b = (int)this.nom.charAt(0) * 2;
		int c = 0;
		for (int cpt=0;cpt<this.nom.length();cpt++) 
			c = (int)this.nom.charAt(cpt);
		colr = new Color(a,b,c*(3/2));
		this.col = colr;
	}


	/**
	 * Associe un lot à un ACE (sans toucher aux heures).
	 * Pré-condition : le lot doit déjà être affecté à cette société.
	 */
	public void donnerLotACE(Lot lot)
	{
		if (!this.lots.contains(lot))
		{
			this.lots.add(lot);
			lot.setNbPers(this.effectifActuel);
		}
	}

	/** Dissocie un lot d'un ACE (sans toucher aux heures). */
	public void enleverLotACE(Lot lot)
	{
		this.lots.remove(lot);
	}

	public String         getNom()            { return nom;            }
	public int            getNbPers()         { return nbPers;         }
	public int            getTotalHeures()    { return totalHeures;    }
	public int            getEffectifActuel() { return effectifActuel; }
	public boolean        estMachine()        { return estMachine;     }
	public Color          getColor()          { return col;            }
	public ArrayList<Lot> getLots()           { return lots;           }

	public void setNom(String v)              { this.nom         = v; trouverColor(); }
	public void setNbPers(int v)              { this.nbPers      = v; }
	public void setTotalHeures(int v)         { this.totalHeures = v; }
	public void setColor(Color c)             { this.col         = c; }
	public void setEstMachine(boolean v){ this.estMachine = v;}

	public void setEffectifActuel(int v) 
	{ 
		this.effectifActuel = v; 
		for (Lot lot : this.lots)
		{
			lot.setHeuresAce(lot.getHeures() / this.effectifActuel);
		}
	}
}
