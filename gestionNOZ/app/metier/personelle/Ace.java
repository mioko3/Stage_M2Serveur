package app.metier.personelle;

import app.metier.lot.Lot;
import java.awt.Color;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 *  Ace — Chef d'Atelier de Conditionnement et Expédition
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Représente un ACE = responsable d'une unité de production au sein d'une Société.
 * • Chaque ACE gère un groupe de personnes (effectif)
 * • Reçoit des lots de production assignés
 * • A un budget horaire théorique (totalHeures) — informatif seulement
 *
 * ⚠️  IMPORTANT : LES HEURES DE L'ACE NE SONT PAS DÉCOMPTÉES
 * ────────────────────────────────────────────────────────────────
 * Seules les heures de la SOCIÉTÉ sont décomptées lors d'une affectation.
 * L'ACE sert à IDENTIFIER le responsable, pas à gérer un compteur d'heures.
 *
 * Exemple de flux d'affectation :
 *   1. Societe.ajouterLot(lot, ace)
 *      → Décrémente totalHeuresCE de la SOCIÉTÉ (- 100h)
 *      → Appelle ace.donnerLotACE(lot) pour la traçabilité
 *   2. ace.donnerLotACE(lot)
 *      → Ajoute lot à la liste ace.lots
 *      → Si lot.nbPers = 0 : le met à jour avec l'effectif de l'ACE
 *      → Ne touche PAS à totalHeures
 *
 * HIÉRARCHIE :
 * ────────────
 *   Societe
 *     └─ ArrayList<Ace>
 *         └─ Ace
 *             ├─ nom, nbPers, effectifActuel
 *             ├─ totalHeures (informatif, réparti depuis Societe)
 *             ├─ estMachine (true si a une machine)
 *             ├─ col (Color, généré from nom pour affichage)
 *             └─ ArrayList<Lot> (lots assignés à cet ACE)
 *
 * CHAMP MACHINE :
 * ───────────────
 * estMachine = true si cet ACE dispose d'une MACHINE (automatisation).
 * Impacte les calculs de cadence et timeline de production.
 *
 * COULEUR ASSOCIÉE :
 * ──────────────────
 * Chaque ACE a une couleur générée déterministe depuis son nom (trouverColor()).
 * Utilisée pour l'affichage visuel des lots dans les diagrammes Gantt.
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 */

public class Ace
{
	/** Nom du chef d'ACE (ex: "Alice", "Bob") */
	private String         nom;
	
	/** Nombre de personnes sous ce chef (taille de l'équipe) */
	private int            nbPers;
	
	/** Heures théoriques affectées à cet ACE (informatif, réparti de la Société) */
	private int            totalHeures;
	
	/** Effectif réel pouvant travailler (peut être < nbPers en cas absence) */
	private int            effectifActuel;
	
	/** true si cet ACE dispose d'une machine (automatisation) */
	private boolean        estMachine;
	
	/** Couleur associée à cet ACE (générée depuis le nom, pour affichage diagramme) */
	private Color          col;
	
	/** Lots assignés à cet ACE */
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
			if (lot.getNbPers() <= 0) lot.setNbPers(this.effectifActuel);
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
