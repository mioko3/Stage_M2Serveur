package app.metier.ficheroute;

import app.metier.lot.Lot;

public class SuivieProd
{
	private int    nbPieceEtiq;
	private int    nbPieceRepart;
	private int    nbHeureEtiqRestant;
	private int    nbHeureRepartRestant;
	private String avancementEtiqPct;
	private String avancementPartsPct;
	private Lot lot;

	public SuivieProd()
	{
		this.nbPieceEtiq          = 0;
		this.nbPieceRepart        = 0;
		this.nbHeureEtiqRestant   = 0;
		this.nbHeureRepartRestant = 0;
		this.avancementEtiqPct    = "0";
		this.avancementPartsPct   = "0";
	}

	public int    getNbPieceEtiq          () { return nbPieceEtiq;          }
	public int    getNbPieceRepart        () { return nbPieceRepart;        }
	public int    getNbHeureEtiqRestant   () { return nbHeureEtiqRestant;   }
	public int    getNbHeureRepartRestant () { return nbHeureRepartRestant; }
	public String getAvancementEtiqPct    () { return avancementEtiqPct+" %";}
	public String getAvancementPartsPct   () { return avancementPartsPct+" %";}
	public Lot    getLot                  () { return lot;                  }

	public void setNbPieceEtiq          (int v  ) { this.nbPieceEtiq          = v; miseAJJourAvancement();}
	public void setNbPieceRepart        (int v  ) { this.nbPieceRepart        = v; miseAJJourAvancement();}
	public void setNbHeureEtiqRestant   (int v  ) { this.nbHeureEtiqRestant   = v;}
	public void setNbHeureRepartRestant (int v  ) { this.nbHeureRepartRestant = v;}
	public void setLot                  (Lot lot) { this.lot = lot; miseAJJourAvancement();}
	
	public void miseAJJourAvancement()
	{
		
		// Pourcentage pièces étiquetées (basé sur nbPieceEtiq / nbPieces total)
		if (this.lot != null && this.lot.getNbPieces() > 0)
		{
			this.avancementEtiqPct = String.format("%.1f", 100.0 * this.nbPieceEtiq / this.lot.getNbPieces());
			this.avancementPartsPct = String.format("%.1f", 100.0 * this.nbPieceRepart / this.lot.getNbPieces());
			if (this.lot.getHeures() > 0)
			{
				this.nbHeureEtiqRestant = (int) Math.round(this.lot.getHeures() * (1 - this.nbPieceEtiq / (double) this.lot.getNbPieces()));
				this.nbHeureRepartRestant = (int) Math.round(this.lot.getHeures() * (1 - this.nbPieceRepart / (double) this.lot.getNbPieces()));
			}
		}
		else
		{
			this.avancementEtiqPct = "0";
			this.avancementPartsPct = "0";
		}
	}
}
