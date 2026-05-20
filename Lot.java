package app.metier.lot;

import app.metier.PlanningGlobal;
import app.metier.ficheroute.Phase;
import app.metier.ficheroute.SuivieProd;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Représente un lot de production issu du fichier export.XLSX.
 *
 * Heures = NbPieces / Cadence (valeur directe du fichier, pas étiq+répart séparés).
 * Pas de champ Societe : l'affectation est gérée exclusivement par Societe.ajouterLot().
 *
 * Identifiant interne : id (UUID généré à la création, sauvegardé en JSON).
 * numCDE reste pour l'affichage mais n'est PAS unique → ne jamais l'utiliser comme clé.
 */
public class Lot
{
	public static final String[] F_CARTON = new String[]{"","1/16","1/8","1/4","1/2","box"};
	public static final String[] DISTRI   = new String[]{"","PI","PM","PREPA","MI","PREPA + MI","PREPA + PART"};

	private static ArrayList<String> tabId;
	// ── Identité ──────────────────────────────────────────────────────────
	private String  id;      // clé technique unique (UUID), persistée en JSON
	private int     numCDE;  // numéro affiché à l'utilisateur — pas unique
	private String  typologie;
	private String  affaire;
	private int     nbPieces;
	private double  cadence,cadenceReel;
	private double  heures;
	private double  heuresAce; // les temps que ça prend (heure / nbpersonne)
	private int     valeurVente;
	private double  prixUnitaire;
	private String  semaine;
	private int     priorite;

	// ── Statuts ───────────────────────────────────────────────────────────
	private String  statut;         // statut interne (OU, TC, MC)
	private String  statutEchant;   // Statut échantillonnage (affiché dans l'IHM)

	// ── Informations logistiques ──────────────────────────────────────────
	private String  lotACharge;
	private boolean estSousDouane;
	private String  dateReception, datePaiement, commentaire, emplacement;

	// -- fiche de route --
	private SuivieProd suivieProd;
	private Phase      phase;
	private Methode    methode;
	private int    nbPalettes, nbColisPrevue, nbColisRecup, collisage,pcsUtiliser;
	private String  distribution, poucentrecupCartonFour;
	private String formatCarton, dateDebut, dateFin, dateFinTheorique; // "dd/MM/yyyy HH:mm:ss"
	private boolean estMachine;
	private int     nbPers;

	// ── Lignes de colisage multiples (cas rares) ──────────────────────────
	private ArrayList<LigneColisage> lignesColisage = new ArrayList<>();

	public Lot(int numCDE, int nbPieces, double cadence, double heures,
			   int valeurVente, String statut, String statutEchant)
	{
		String uuid = UUID.randomUUID().toString();
		this.id          = verifUUID(uuid);
		this.numCDE      = numCDE;
		this.nbPieces    = nbPieces;
		this.cadence     = cadence;
		this.cadenceReel = cadence;
		this.heures      = heures;
		this.valeurVente = valeurVente;
		this.prixUnitaire = calculerPU();
		this.statut      = statut      != null ? statut      : "";
		this.statutEchant = statutEchant != null ? statutEchant : "";
		this.typologie    = "";
		this.affaire      = "";
		this.semaine      = "";
		this.priorite     = 0;
		this.lotACharge   = "";
		this.estSousDouane= false;
		this.dateReception= "";
		this.datePaiement = "";
		this.commentaire  = "";
		this.emplacement  = "";
		this.formatCarton = "";
		this.distribution = "";
		this.dateDebut    = "";
		this.dateFin      = "";
		this.dateFinTheorique = "";
		this.pcsUtiliser  = this.nbPieces;
		this.suivieProd   = new SuivieProd();
		this.suivieProd.setLot(this);
		this.phase        = new Phase();
		this.estMachine   = false;
	}

	private String verifUUID(String uuid)
	{
		if (tabId == null) tabId = new ArrayList<>();
		for (String s : tabId)
		{
			if (uuid.equals(s))
			{
				String uuid2 = UUID.randomUUID().toString();
				return verifUUID(uuid2);
			}
		}
		return uuid;
	}

	// ── Recalcul ───────────────────────────────────────────────
	public void recalculerHeures()
	{
		this.heures = (this.cadenceReel > 0) ? this.nbPieces / this.cadenceReel : 0.0;
	}

	public void calculHeuresPiste(int eff)
	{
		this.heuresAce = (this.cadenceReel > 0) ? this.nbPieces / (this.cadenceReel * eff) : 0.0;
		calculDateFinThéorique();
	}

	public void calculDateFinThéorique()
	{
		if (this.dateDebut == null || this.dateDebut.isEmpty()) return;
		if (this.heuresAce <= 0) { this.dateFinTheorique = ""; return; }

		try
		{
			java.time.format.DateTimeFormatter fmt =
				java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

			java.time.LocalDateTime curseur =
				java.time.LocalDateTime.parse(this.dateDebut, fmt);

			java.time.LocalTime DEBUT_JOURNEE = java.time.LocalTime.of(8, 15);

			java.time.LocalTime FIN_LUN_JEU;
			if (PlanningGlobal.estHeureSup)
				FIN_LUN_JEU = java.time.LocalTime.of(17, 15);
			else 
				FIN_LUN_JEU = java.time.LocalTime.of(16, 15);
			java.time.LocalTime FIN_VEN     = java.time.LocalTime.of(14, 30);

			double heuresRestantes = this.heuresAce;

			if (curseur.toLocalTime().isBefore(DEBUT_JOURNEE))
			{
				curseur = curseur.with(DEBUT_JOURNEE);
			}

			while (heuresRestantes > 0)
			{
				java.time.DayOfWeek jour = curseur.getDayOfWeek();

				// week-end
				if (jour == java.time.DayOfWeek.SATURDAY)
				{
					curseur = curseur.plusDays(2).with(DEBUT_JOURNEE);
					continue;
				}

				if (jour == java.time.DayOfWeek.SUNDAY)
				{
					curseur = curseur.plusDays(1).with(DEBUT_JOURNEE);
					continue;
				}

				boolean isFriday = (jour == java.time.DayOfWeek.FRIDAY);

				java.time.LocalTime finJour =
					isFriday ? FIN_VEN : FIN_LUN_JEU;

				// pause selon jour
				java.time.LocalTime pauseDebut =
					isFriday ? java.time.LocalTime.of(11, 0)
							: java.time.LocalTime.of(12, 0);

				java.time.LocalTime pauseFin =
					isFriday ? java.time.LocalTime.of(11, 45)
							: java.time.LocalTime.of(12, 45);

				java.time.LocalDateTime finJourDT =
					curseur.with(finJour);

				java.time.LocalDateTime pauseDebutDT =
					curseur.with(pauseDebut);

				java.time.LocalDateTime pauseFinDT =
					curseur.with(pauseFin);

				if (!curseur.isBefore(finJourDT))
				{
					curseur = curseur.plusDays(1).with(DEBUT_JOURNEE);
					continue;
				}

				while (heuresRestantes > 0 && curseur.isBefore(finJourDT))
				{
					// gestion pause
					if (!curseur.isBefore(pauseDebutDT) &&
						curseur.isBefore(pauseFinDT))
					{
						curseur = pauseFinDT;
						continue;
					}

					curseur = curseur.plusMinutes(1);
					heuresRestantes -= 1.0 / 60.0;
				}

				if (heuresRestantes > 0 && !curseur.isBefore(finJourDT))
				{
					curseur = curseur.plusDays(1).with(DEBUT_JOURNEE);
				}
			}

			this.dateFinTheorique = curseur.format(fmt);
		}
		catch (Exception e)
		{
			this.dateFinTheorique = "";
		}
	}

	private double calculerPU()
	{
		double pu = 0.0;
		if (this.nbPieces > 0)
		{
			pu = Math.round(((double) this.valeurVente / this.nbPieces)*100.0) / 100.0;
		}
		return pu;
	}

	public void recalculNbPalette()
	{
		if (collisage <= 0 || formatCarton == "") 
		{
			this.nbColisPrevue = 0;
			this.nbPalettes    = 0;
			return;
		}
		int nbcarton = (int) Math.ceil(this.pcsUtiliser / (double) this.collisage);
		this.nbColisPrevue = nbcarton;
		switch (formatCarton)
		{
			case "1/16": this.nbPalettes = (int) Math.ceil(nbcarton / 64); break;
			case "1/8":  this.nbPalettes = (int) Math.ceil(nbcarton / 32); break;
			case "1/4":  this.nbPalettes = (int) Math.ceil(nbcarton / 16); break;
			case "1/2":  this.nbPalettes = (int) Math.ceil(nbcarton / 8);  break;
			case "box":  this.nbPalettes = (int) Math.ceil(nbcarton);      break;
			default:     throw new AssertionError();
		}
	}

	// ── Lignes de colisage multiples ──────────────────────────────────────

	public ArrayList<LigneColisage> getLignesColisage() { return lignesColisage; }

	public void ajouterLigneColisage(LigneColisage ligne, int pcs)
	{
		this.pcsUtiliser = this.pcsUtiliser - pcs;
		ligne.recalculer(pcs);
		lignesColisage.add(ligne);
		recalculNbPalette();
	}

	public void supprimerLigneColisage(int index)
	{
		if (index >= 0 && index < lignesColisage.size())
		{
			this.pcsUtiliser += lignesColisage.get(index).getPcs();
			lignesColisage.remove(index);
			recalculNbPalette();
		}
	}

	public void recalculerLignesColisage()
	{
		for (LigneColisage l : lignesColisage)
			l.recalculer(this.nbPieces);
	}

	// ── Getters ───────────────────────────────────────────────────────────
	public String  getId()             { return id;            }
	public int     getNumCDE()         { return numCDE;        }
	public String  getTypologie()      { return typologie;     }
	public String  getAffaire()        { return affaire;       }
	public int     getNbPieces()       { return nbPieces;      }
	public double  getCadence()        { return cadence;       }
	public double  getCadenceReel()    { return cadenceReel;   }
	public double  getHeures()         { return heures;        }
	public int     getValeurVente()    { return valeurVente;   }
	public double  getPrixUnitaire()   { return prixUnitaire;  }
	public String  getSemaine()        { return semaine;       }
	public int     getPriorite()       { return priorite;      }
	public String  getStatut()         { return statut;        }
	public String  getStatutEchant()   { return statutEchant;  }
	public String  getLotACharge()     { return lotACharge;    }
	public boolean isEstSousDouane()   { return estSousDouane; }
	public String  getDateReception()  { return dateReception; }
	public String  getDatePaiement()   { return datePaiement;  }
	public String  getCommentaire()    { return commentaire;   }
	public String  getEmplacement()    { return emplacement;   }
	public SuivieProd getSuivieProd   () { return suivieProd; }
	public Phase      getPhase        () { return phase;      }
	public Methode    getMethode      () { return methode;    }
	public String     getDistribution () { return distribution;}
	public String     getFormatCarton () { return formatCarton;}
	public double     getHeuresAce    () { return heuresAce;  }
	public int        getNbPalettes   () { return nbPalettes;  }
	public int        getNbColisPrevue() { return nbColisPrevue;}
	public int        getNbColisRecup () { return nbColisRecup; }
	public int        getCollisage    () { return collisage;   }
	public String     getPoucentrecupCartonFour() { return poucentrecupCartonFour; }
	public String     getDateDebut    () { return dateDebut;   }
	public String     getdateFin      () { return dateFin;     }
	public String     getdateFinT      () { return dateFinTheorique;}
	public boolean    estMachine      () { return estMachine;  }
	public int        getNbPers       () { return nbPers;      }

	// ── Setters ───────────────────────────────────────────────────────────
	public void setId(String v)            { this.id           = v; }
	public void setNumCDE(int v)           { this.numCDE       = v; }
	public void setTypologie(String v)     { this.typologie    = v; }
	public void setAffaire(String v)       { this.affaire      = v; }
	public void setNbPieces(int v)
	{
		this.nbPieces = v;
		this.recalculerHeures();
		recalculerLignesColisage();
	}
	public void setCadence(double v)       { this.cadence      = v; }
	public void setCadenceReel(double v)   { this.cadenceReel  = v; this.calculHeuresPiste(this.nbPers);this.recalculerHeures();}
	public void setHeures(double v)        { this.heures       = v; }
	public void setValeurVente(int v)      { this.valeurVente  = v; this.prixUnitaire = calculerPU(); }
	public void setPrixUnitaire(double v)  { this.prixUnitaire = v; }
	public void setSemaine(String v)       { this.semaine      = v; }
	public void setPriorite(int v)         { this.priorite     = v; }
	public void setStatut(String v)        { this.statut       = v; }
	public void setStatutEchant(String v)  { this.statutEchant = v; }
	public void setLotACharge(String v)    { this.lotACharge   = v; }
	public void setEstSousDouane(boolean v){ this.estSousDouane= v; }
	public void setDateReception(String v) { this.dateReception= v; }
	public void setDatePaiement(String v)  { this.datePaiement = v; }
	public void setCommentaire(String v)   { this.commentaire  = v; }
	public void setEmplacement(String v)   { this.emplacement  = v; }
	public void setSuivieProd(SuivieProd v) { this.suivieProd = v; this.suivieProd.setLot(this); }
	public void setPhase(Phase v)           { this.phase      = v; }
	public void setMethode(String methode)  { this.methode = Methode.getMetode(methode); }
	public void setDistribution(String v)   { this.distribution = v; }
	public void setFormatCarton(String v)   { this.formatCarton = v; recalculNbPalette(); }
	public void setHeuresAce(double v)      { this.heuresAce = v;    }
	public void setNbPalettes(int v)        { this.nbPalettes = v;   }
	public void setNbColisPrevue(int v)     { this.nbColisPrevue = v;}
	public void setNbColisRecup(int v)      { this.nbColisRecup = v; }
	public void setCollisage(int v)         { this.collisage = v; recalculNbPalette(); }
	public void setPoucentrecupCartonFour(String v) { this.poucentrecupCartonFour = v; }
	public void setDateDebut(String v)      { this.dateDebut = v;    }
	public void setdateFin(String v)        { this.dateFin = v;      }
	public void setdateFinT(String v)       { this.dateFinTheorique = v; }
	public void setEstMachine(boolean v)    { this.estMachine = v;   }
	public void setNbPers(int v)            { this.nbPers = v; this.calculHeuresPiste(v); }
}