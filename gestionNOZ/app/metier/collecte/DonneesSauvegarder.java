package app.metier.collecte;

import app.metier.PlanningGlobal;
import app.metier.ficheroute.Phase;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import app.securite.ChiffrementAES;
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

		// Si le chiffrement est actif, on déchiffre le contenu AVANT de le
		// passer à ExcelReader. ExcelReader lit lui-même les fichiers via
		// lireFichier() — on ne peut pas lui injecter le contenu directement.
		// Solution : si le fichier est chiffré, on écrit un fichier .tmp
		// déchiffré à côté, ExcelReader le lit, puis on supprime le .tmp.
		// Si le fichier est déjà en JSON brut (migration), on le passe directement.
		String cheminLotsEffectif     = preparerFichierPourLecture(cheminLots);
		String cheminSocietesEffectif = preparerFichierPourLecture(cheminSocietes);

		try
		{
			// ExcelReader reconstruit correctement les Lot (UUID, lignesColisage,
			// phases, etc.) et les Societe (ACEs, affectations par ID).
			metier.getLots()    .clear();
			metier.getSocietes().clear();
			ArrayList<Lot> lots = ExcelReader.lireLots(cheminLotsEffectif);
			ArrayList<Societe> societes = ExcelReader.lireSocietes(cheminSocietesEffectif, lots);
			metier.getLots()    .addAll(lots);
			metier.getSocietes().addAll(societes);

			System.out.println("[Chargement] " + metier.getLots().size()
				+ " lots, " + metier.getSocietes().size() + " sociétés depuis " + cheminDossier);
		}
		finally
		{
			// Supprimer les fichiers temporaires déchiffrés dans tous les cas
			if (!cheminLotsEffectif.equals(cheminLots))
				new java.io.File(cheminLotsEffectif).delete();
			if (!cheminSocietesEffectif.equals(cheminSocietes))
				new java.io.File(cheminSocietesEffectif).delete();
		}
	}

	/**
	 * Si le chiffrement est actif et que le fichier est chiffré (Base64),
	 * écrit un fichier temporaire .tmp contenant le JSON déchiffré et
	 * retourne son chemin. ExcelReader lira ce fichier temporaire.
	 *
	 * Si le fichier est déjà en JSON brut (migration ou pas de chiffrement),
	 * retourne le chemin original sans créer de temporaire.
	 * Dans ce cas, migre aussi le fichier vers le format chiffré.
	 */
	private String preparerFichierPourLecture(String chemin) throws IOException
	{
		if (!new java.io.File(chemin).exists())
			throw new IOException("Fichier introuvable : " + chemin);

		if (aes == null)
			return chemin; // pas de chiffrement → chemin original

		String contenu = Files.readString(Paths.get(chemin), StandardCharsets.UTF_8).trim();

		boolean estJsonBrut = contenu.startsWith("[") || contenu.startsWith("{");

		if (estJsonBrut)
		{
			// Fichier hérité non chiffré : migrer sur le disque
			System.out.println("[AES] Migration : chiffrement de " + chemin);
			try {
				Files.writeString(Paths.get(chemin), aes.chiffrer(contenu));
				System.out.println("[AES] Migration réussie : " + chemin);
			} catch (Exception e) {
				System.err.println("[AES] Migration échouée : " + e.getMessage());
			}
			// ExcelReader détecte le format via l'extension du fichier.
			// Le temporaire DOIT garder l'extension .json, sinon ExcelReader
			// l'ouvre comme Excel et plante avec "unsupported file type".
			// On insère "_tmp" avant l'extension : lots.json → lots_tmp.json
			String tmp = cheminSansExtension(chemin) + "_tmp.json";
			Files.writeString(Paths.get(tmp), contenu);
			return tmp;
		}

		// Fichier chiffré : déchiffrer dans un temporaire .json
		try {
			String jsonBrut = aes.dechiffrer(contenu);
			String tmp = cheminSansExtension(chemin) + "_tmp.json";
			Files.writeString(Paths.get(tmp), jsonBrut);
			return tmp;
		} catch (Exception e) {
			throw new IOException("Erreur de déchiffrement de " + chemin
				+ " (clé incorrecte ou fichier corrompu) : " + e.getMessage(), e);
		}
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
	 *
	 * GESTION DE LA MIGRATION JSON → CHIFFRÉ :
	 * ──────────────────────────────────────────
	 * Quand le chiffrement est activé pour la première fois, les fichiers
	 * lots.json et societes.json existent déjà en JSON brut (non chiffré).
	 * Si on essaie de les déchiffrer directement → erreur "Illegal base64
	 * character 5b" car '[' (= 0x5b) n'est pas un caractère Base64 valide.
	 *
	 * Solution : détecter si le contenu est du JSON brut AVANT de déchiffrer.
	 * Un fichier chiffré contient du Base64 pur (pas d'accolades ni crochets).
	 * Un fichier JSON commence toujours par '[' ou '{'.
	 *
	 * Si c'est du JSON brut et que le chiffrement est actif → on le lit tel quel
	 * ET on le réécrit chiffré immédiatement pour migrer le fichier.
	 */
	private String lire(String chemin) throws IOException
	{
		if (!new java.io.File(chemin).exists())
			throw new IOException("Fichier introuvable : " + chemin);

		String contenu = Files.readString(Paths.get(chemin), StandardCharsets.UTF_8).trim();

		if (aes != null)
		{
			// Détecter si le fichier est déjà chiffré (Base64) ou encore en JSON brut
			boolean estJsonBrut = contenu.startsWith("[") || contenu.startsWith("{");

			if (estJsonBrut)
			{
				// Fichier hérité non chiffré : on le lit et on le migre sur le disque
				System.out.println("[AES] Migration : chiffrement de " + chemin);
				try {
					String chiffré = aes.chiffrer(contenu);
					Files.writeString(Paths.get(chemin), chiffré);
					System.out.println("[AES] Migration réussie : " + chemin);
				} catch (Exception e) {
					System.err.println("[AES] Migration échouée pour " + chemin + " : " + e.getMessage());
					// On continue avec le JSON brut même si la migration a échoué
				}
				return contenu;
			}

			// Fichier déjà chiffré : déchiffrer normalement
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

	/**
	 * Retire l'extension d'un chemin de fichier.
	 * "app/data/lots.json" → "app/data/lots"
	 * Utilisé pour créer les fichiers temporaires avec l'extension .json
	 * à la place de l'extension originale.
	 */
	private static String cheminSansExtension(String chemin)
	{
		int dot = chemin.lastIndexOf('.');
		if (dot < 0) return chemin;
		return chemin.substring(0, dot);
	}

	private static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
	}
}