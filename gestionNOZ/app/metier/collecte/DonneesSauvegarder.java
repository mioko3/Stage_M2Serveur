package app.metier.collecte;

import app.securite.ChiffrementAES;
import app.metier.PlanningGlobal;
import app.metier.ficheroute.Phase;
import app.metier.lot.LigneColisage;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * ══════════════════════════════════════════════════════════════
 *  DonneesSauvegarder — version chiffrée (correctif fichiers JSON)
 * ══════════════════════════════════════════════════════════════
 *
 *  CHANGEMENTS PAR RAPPORT À L'ORIGINAL :
 *  ───────────────────────────────────────
 *  • ajout d'un ChiffrementAES optionnel via setCrypte(aes)
 *  • si setCrypte() a été appelé :
 *      - sauvegarderLots / sauvegarderSocietes chiffrent le JSON avant d'écrire
 *      - charger lit le fichier et le déchiffre avant de parser
 *  • si setCrypte() N'a PAS été appelé → comportement identique à l'original
 *    (rétrocompatibilité totale, le mode solo sans chiffrement continue de fonctionner)
 *
 *  COMMENT ACTIVER LE CHIFFREMENT dans ServeurHTTP :
 *  ──────────────────────────────────────────────────
 *      ChiffrementAES aes = ChiffrementAES.chargerOuCreer(
 *          CheminApp.resoudre("secret.key"));
 *      this.savDonnees.setCrypte(aes);   // ← une seule ligne à ajouter
 *
 *  Après ça, tous les appels existants à sauvegarderLots(), sauvegarderSocietes()
 *  et charger() fonctionnent exactement comme avant, mais les fichiers sur le
 *  disque sont chiffrés.
 * ══════════════════════════════════════════════════════════════
 */
public class DonneesSauvegarder
{
	private static final String FICHIER_LOTS     = "lots.json";
	private static final String FICHIER_SOCIETES = "societes.json";

	// null = pas de chiffrement (comportement original)
	private ChiffrementAES aes = null;

	/**
	 * Active le chiffrement AES sur toutes les lectures/écritures.
	 * Appeler cette méthode une seule fois après l'instanciation.
	 */
	public void setCrypte(ChiffrementAES aes)
	{
		this.aes = aes;
	}

	// ── Sauvegarde ────────────────────────────────────────────────────────

	public void sauvegarderLots(ArrayList<Lot> lots, String cheminFichier) throws IOException
	{
		String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
		String json   = construireJsonLots(lots);
		ecrire(chemin, json);
	}

	public void sauvegarderSocietes(ArrayList<Societe> societes, ArrayList<Lot> lots,
									String cheminFichier) throws IOException
	{
		String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
		String json   = construireJsonSocietes(societes, lots);
		ecrire(chemin, json);
	}

	// ── Chargement ────────────────────────────────────────────────────────

	public void charger(PlanningGlobal metier, String cheminDossier) throws IOException
	{
		String cheminLots     = cheminDossier + "/" + FICHIER_LOTS;
		String cheminSocietes = cheminDossier + "/" + FICHIER_SOCIETES;

		String jsonLots     = lire(cheminLots);
		String jsonSocietes = lire(cheminSocietes);

		ArrayList<Lot>     lots     = JsonSerialiser.deserialiserLots(jsonLots);
		ArrayList<Societe> societes = JsonSerialiser.deserialiserSocietes(jsonSocietes, lots);

		metier.setLots(lots);
		metier.setSocietes(societes);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LECTURE / ÉCRITURE avec chiffrement transparent
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Écrit le contenu dans le fichier.
	 * Si le chiffrement est activé, le fichier contiendra du Base64 chiffré.
	 * Sinon, le fichier contient du JSON brut lisible (comportement original).
	 */
	private void ecrire(String chemin, String contenu) throws IOException
	{
		if (aes != null)
		{
			// Mode chiffré : on chiffre le JSON et on écrit le Base64 résultant
			try {
				String chiffré = aes.chiffrer(contenu);
				Files.writeString(Paths.get(chemin), chiffré);
			} catch (Exception e) {
				throw new IOException("Erreur de chiffrement lors de l'écriture de " + chemin + " : " + e.getMessage(), e);
			}
		}
		else
		{
			// Mode original : écriture JSON brut
			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
					new FileOutputStream(chemin), StandardCharsets.UTF_8)))
			{
				pw.print(contenu);
			}
		}
	}

	/**
	 * Lit le contenu d'un fichier.
	 * Si le chiffrement est activé, déchiffre le Base64 avant de retourner.
	 * Sinon, retourne le contenu brut (comportement original).
	 */
	private String lire(String chemin) throws IOException
	{
		if (!new java.io.File(chemin).exists())
			throw new IOException("Fichier introuvable : " + chemin);

		String contenu = Files.readString(Paths.get(chemin), StandardCharsets.UTF_8);

		if (aes != null)
		{
			try {
				return aes.dechiffrer(contenu);
			} catch (Exception e) {
				throw new IOException("Erreur de déchiffrement de " + chemin
					+ ". Le fichier est peut-être corrompu ou la clé a changé : " + e.getMessage(), e);
			}
		}

		return contenu;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTION JSON (identique à l'original)
	// ══════════════════════════════════════════════════════════════════════

	private String construireJsonLots(ArrayList<Lot> lots)
	{
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < lots.size(); i++)
		{
			Lot l = lots.get(i);
			Phase p = l.getPhase();
			sb.append("  {\n");
			sb.append("    \"id\": "                       ).append(esc(l.getId()))                                     .append(",\n");
			sb.append("    \"numCDE\": "                   ).append(l.getNumCDE())                                      .append(",\n");
			sb.append("    \"semaine\": "                  ).append(esc(l.getSemaine()))                                .append(",\n");
			sb.append("    \"priorite\": "                 ).append(l.getPriorite())                                    .append(",\n");
			sb.append("    \"typologie\": "                ).append(esc(l.getTypologie()))                              .append(",\n");
			sb.append("    \"affaire\": "                  ).append(esc(l.getAffaire()))                                .append(",\n");
			sb.append("    \"valeurVente\": "              ).append(l.getValeurVente())                                 .append(",\n");
			sb.append("    \"nbPieces\": "                 ).append(l.getNbPieces())                                    .append(",\n");
			sb.append("    \"cadence\": "                  ).append(l.getCadence())                                     .append(",\n");
			sb.append("    \"heures\": "                   ).append(l.getHeures())                                      .append(",\n");
			sb.append("    \"statut\": "                   ).append(esc(l.getStatut()))                                 .append(",\n");
			sb.append("    \"statutEchant\": "             ).append(esc(l.getStatutEchant()))                           .append(",\n");
			sb.append("    \"lotACharge\": "               ).append(esc(l.getLotACharge()))                             .append(",\n");
			sb.append("    \"estSousDouane\": "            ).append(l.isEstSousDouane())                                .append(",\n");
			sb.append("    \"dateReception\": "            ).append(esc(l.getDateReception()))                          .append(",\n");
			sb.append("    \"datePaiement\": "             ).append(esc(l.getDatePaiement()))                           .append(",\n");
			sb.append("    \"commentaire\": "              ).append(esc(l.getCommentaire()))                            .append(",\n");
			sb.append("    \"emplacement\": "              ).append(esc(l.getEmplacement()))                            .append(",\n");
			sb.append("    \"sp_nbPieceEtiq\": "           ).append(l.getSuivieProd().getNbPieceEtiq())                 .append(",\n");
			sb.append("    \"sp_nbPieceRepart\": "         ).append(l.getSuivieProd().getNbPieceRepart())               .append(",\n");
			sb.append("    \"sp_nbHeureEtiqRestant\": "    ).append(l.getSuivieProd().getNbHeureEtiqRestant())          .append(",\n");
			sb.append("    \"sp_nbHeureRepartRestant\": "  ).append(l.getSuivieProd().getNbHeureRepartRestant())        .append(",\n");
			sb.append("    \"methode\": "                  ).append(esc(l.getMethode() == null ? "" : l.getMethode().getNom())).append(",\n");
			sb.append("    \"distribution\": "             ).append(esc(l.getDistribution()))                           .append(",\n");
			sb.append("    \"formatCarton\": "             ).append(esc(l.getFormatCarton()))                           .append(",\n");
			sb.append("    \"dateDebut\": "                ).append(esc(l.getDateDebut()))                              .append(",\n");
			sb.append("    \"dateFin\": "                  ).append(esc(l.getdateFin()))                                .append(",\n");
			sb.append("    \"dateFinTheorique\": "         ).append(esc(l.getdateFinT()))                               .append(",\n");
			sb.append("    \"heuresAce\": "                ).append(l.getHeuresAce())                                   .append(",\n");
			sb.append("    \"collisage\": "                ).append(l.getCollisage())                                   .append(",\n");
			sb.append("    \"estMachine\": "               ).append(false)                                              .append(",\n");
			sb.append("    \"nbPers\": "                   ).append(l.getNbPers())                                      .append(",\n");
			sb.append("    \"cadenceReel\": "              ).append(l.getCadenceReel())                                 .append(",\n");
			sb.append("    \"lignesColisage\": []"         )                                                            .append(",\n");
			sb.append("    \"phase_preTri\": "             ).append(p != null && p.isPreTri())                          .append(",\n");
			sb.append("    \"phase_surPiste\": "           ).append(p != null && p.isSurPiste())                        .append(",\n");
			sb.append("    \"phase_sortieEtiq\": "         ).append(p != null && p.isSortieEtiq())                      .append(",\n");
			sb.append("    \"phase_tri\": "                ).append(p != null && p.isTri())                             .append(",\n");
			sb.append("    \"phase_finit\": "              ).append(p != null && p.isFinit())                           .append("\n");
			sb.append("  }");
			if (i < lots.size() - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append("]");
		return sb.toString();
	}

	private String construireJsonSocietes(ArrayList<Societe> societes, ArrayList<Lot> lots)
	{
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < societes.size(); i++)
		{
			Societe s = societes.get(i);
			sb.append("  {\n");
			sb.append("    \"nom\": "          ).append(esc(s.getNom()))        .append(",\n");
			sb.append("    \"ce\": "           ).append(esc(s.getCe()))         .append(",\n");
			sb.append("    \"totalHeuresCE\": ").append(s.getTotalHeuresCE())   .append(",\n");
			sb.append("    \"effectifTotal\": ").append(s.getEffectifTotal())   .append(",\n");

			// IDs des lots affectés à la société
			sb.append("    \"lotsIds\": [");
			for (int j = 0; j < s.getLots().size(); j++) {
				if (j > 0) sb.append(",");
				sb.append(s.getLots().get(j).getNumCDE());
			}
			sb.append("],\n");

			// ACEs
			sb.append("    \"aces\": [\n");
			for (int j = 0; j < s.getAces().size(); j++)
			{
				Ace a = s.getAces().get(j);
				sb.append("      {\n");
				sb.append("        \"nom\": "         ).append(esc(a.getNom()))           .append(",\n");
				sb.append("        \"nbPers\": "      ).append(a.getNbPers())             .append(",\n");
				sb.append("        \"totalHeures\": " ).append(a.getTotalHeures())        .append(",\n");
				sb.append("        \"effectif\": "    ).append(a.getEffectifActuel())     .append(",\n");
				sb.append("        \"lotsIds\": [");
				for (int k = 0; k < a.getLots().size(); k++) {
					if (k > 0) sb.append(",");
					sb.append(a.getLots().get(k).getNumCDE());
				}
				sb.append("]\n");
				sb.append("      }");
				if (j < s.getAces().size() - 1) sb.append(",");
				sb.append("\n");
			}
			sb.append("    ]\n");
			sb.append("  }");
			if (i < societes.size() - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append("]");
		return sb.toString();
	}

	private static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
	}
}