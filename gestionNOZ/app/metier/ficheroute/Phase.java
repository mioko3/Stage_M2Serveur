package app.metier.ficheroute;

/**
 * Représente l’avancement des phases de production pour un lot.
 *
 * Stocke l’état de chaque étape du processus (pré-tri, tri, étiquetage, etc.).
 */
public class Phase
{
	private boolean preTri;
	private boolean surPiste;
	private boolean sortieEtiq;
	private boolean tri;
	private boolean finit;

	//
	// getters & setters
	//
	public boolean isPreTri() {return preTri;}
	public boolean isSurPiste() {return surPiste;}
	public boolean isSortieEtiq() {return sortieEtiq;}
	public boolean isTri() {return tri;}
	public boolean isFinit() {return finit;}

	public void setPreTri(boolean v) {this.preTri = v;}
	public void setSurPiste(boolean v) {this.surPiste = v;}
	public void setSortieEtiq(boolean v) {this.sortieEtiq = v;}
	public void setTri(boolean v) {this.tri = v;}
	public void setFinit(boolean v) {this.finit = v;}
	
}
