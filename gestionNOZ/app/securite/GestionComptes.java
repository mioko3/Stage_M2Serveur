package app.securite;

import app.CheminApp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  GestionComptes — Portage exact de data/config.json (Node.js)
 *
 *  Rôle :
 *   - Charger / sauvegarder data/config.json
 *   - Valider identifiant + mot de passe
 *   - Créer une demande de compte (statut "attente")
 *   - Approuver / refuser une demande (PAM uniquement)
 *   - Lister les demandes en attente
 *
 *  Format config.json :
 *  {
 *    "utilisateurs": [ { "identifiant":"PAM", "motDePasse":"pam2026", "accesPAM":true }, ... ],
 *    "demandesCompte": [ { "identifiant":"marie", "motDePasse":"xxx", "date":"...", "statut":"attente" }, ... ]
 *  }
 *
 *  Thread-safety : toutes les méthodes sont synchronized sur l'instance.
 * ══════════════════════════════════════════════════════════════
 */
public class GestionComptes
{
	// ── Chemin du fichier de config ───────────────────────────────────────
	private static final String CHEMIN_CONFIG = "app/data/config.json";

	// ── Structures en mémoire ─────────────────────────────────────────────
	private List<Utilisateur>    utilisateurs = new ArrayList<>();
	private List<DemandeCompte>  demandes     = new ArrayList<>();

	// ── Singleton ─────────────────────────────────────────────────────────
	private static GestionComptes instance;
	public static synchronized GestionComptes getInstance()
	{
		if (instance == null) instance = new GestionComptes();
		return instance;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CLASSES INTERNES
	// ══════════════════════════════════════════════════════════════════════

	public static class Utilisateur
	{
		public final String  identifiant;
		public final String  motDePasse;
		public final boolean accesPAM;

		public Utilisateur(String id, String mdp, boolean pam)
		{
			this.identifiant = id;
			this.motDePasse  = mdp;
			this.accesPAM    = pam;
		}
	}

	public static class DemandeCompte
	{
		public final String identifiant;
		public final String motDePasse;
		public final String date;
		public String       statut; // "attente", "approuve", "refuse"

		public DemandeCompte(String id, String mdp, String date, String statut)
		{
			this.identifiant = id;
			this.motDePasse  = mdp;
			this.date        = date;
			this.statut      = statut;
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR — CHARGEMENT
	// ══════════════════════════════════════════════════════════════════════

	private GestionComptes()
	{
		charger();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CHARGEMENT / SAUVEGARDE
	// ══════════════════════════════════════════════════════════════════════

	public synchronized void charger()
	{
		String chemin = CheminApp.resoudre(CHEMIN_CONFIG);
		File f = new File(chemin);

		if (!f.exists())
		{
			// Créer config par défaut
			utilisateurs = new ArrayList<>();
			utilisateurs.add(new Utilisateur("PAM", "pam2026", true));
			demandes = new ArrayList<>();
			sauvegarder();
			System.out.println("[GestionComptes] Config créée par défaut.");
			return;
		}

		try
		{
			String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			utilisateurs = parseUtilisateurs(json);
			demandes     = parseDemandes(json);
			System.out.println("[GestionComptes] " + utilisateurs.size() + " utilisateurs, "
				+ demandes.stream().filter(d -> "attente".equals(d.statut)).count() + " demandes en attente.");
		}
		catch (Exception e)
		{
			System.err.println("[GestionComptes] Erreur chargement : " + e.getMessage());
			utilisateurs = new ArrayList<>();
			utilisateurs.add(new Utilisateur("PAM", "pam2026", true));
			demandes = new ArrayList<>();
		}
	}

	public synchronized void sauvegarder()
	{
		String chemin = CheminApp.resoudre(CHEMIN_CONFIG);
		try
		{
			Files.createDirectories(Paths.get(chemin).getParent());
			StringBuilder sb = new StringBuilder("{\n  \"utilisateurs\": [\n");
			for (int i = 0; i < utilisateurs.size(); i++)
			{
				Utilisateur u = utilisateurs.get(i);
				sb.append("    {\"identifiant\":").append(esc(u.identifiant))
				  .append(",\"motDePasse\":").append(esc(u.motDePasse))
				  .append(",\"accesPAM\":").append(u.accesPAM).append("}");
				if (i < utilisateurs.size() - 1) sb.append(",");
				sb.append("\n");
			}
			sb.append("  ],\n  \"demandesCompte\": [\n");
			for (int i = 0; i < demandes.size(); i++)
			{
				DemandeCompte d = demandes.get(i);
				sb.append("    {\"identifiant\":").append(esc(d.identifiant))
				  .append(",\"motDePasse\":").append(esc(d.motDePasse))
				  .append(",\"date\":").append(esc(d.date))
				  .append(",\"statut\":").append(esc(d.statut)).append("}");
				if (i < demandes.size() - 1) sb.append(",");
				sb.append("\n");
			}
			sb.append("  ]\n}");
			Files.write(Paths.get(chemin), sb.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			System.err.println("[GestionComptes] Erreur sauvegarde : " + e.getMessage());
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  API PUBLIQUE
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * Valide identifiant + mot de passe.
	 * Recharge le fichier avant de valider (pour prendre en compte les approbations).
	 * @return l'utilisateur si OK, null sinon
	 */
	public synchronized Utilisateur valider(String identifiant, String motDePasse)
	{
		charger(); // toujours lire depuis le disque
		if (identifiant == null || motDePasse == null) return null;

		// Vérifier si ce compte a une demande "attente" → bloquer
		boolean enAttente = demandes.stream()
			.anyMatch(d -> d.identifiant.equalsIgnoreCase(identifiant) && "attente".equals(d.statut));
		if (enAttente) return null;

		return utilisateurs.stream()
			.filter(u -> u.identifiant.equalsIgnoreCase(identifiant) && u.motDePasse.equals(motDePasse))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Vérifie si un compte est en attente de validation.
	 */
	public synchronized boolean estEnAttente(String identifiant)
	{
		charger();
		return demandes.stream()
			.anyMatch(d -> d.identifiant.equalsIgnoreCase(identifiant) && "attente".equals(d.statut));
	}

	/**
	 * Crée une demande de compte.
	 * @return message d'erreur, ou null si OK
	 */
	public synchronized String creerDemande(String identifiant, String motDePasse)
	{
		charger();
		if (identifiant == null || identifiant.trim().isEmpty()) return "Identifiant requis.";
		if (motDePasse == null || motDePasse.length() < 4)       return "Mot de passe trop court (4 caractères min).";
		if (identifiant.trim().length() < 2)                     return "Identifiant trop court (2 caractères min).";

		String id = identifiant.trim().toUpperCase();

		// Doublon parmi les comptes existants PAM
		boolean pamExist = utilisateurs.stream()
			.anyMatch(u -> u.identifiant.equalsIgnoreCase(id) && u.accesPAM);
		if (pamExist) return "Cet identifiant est réservé.";

		// Doublon compte normal existant
		boolean normalExist = utilisateurs.stream()
			.anyMatch(u -> u.identifiant.equalsIgnoreCase(id));
		if (normalExist) return "Cet identifiant existe déjà.";

		// Doublon demande en attente
		boolean dejaEnAttente = demandes.stream()
			.anyMatch(d -> d.identifiant.equalsIgnoreCase(id) && "attente".equals(d.statut));
		if (dejaEnAttente) return "Une demande est déjà en attente pour cet identifiant.";

		String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
		demandes.add(new DemandeCompte(id, motDePasse, date, "attente"));
		sauvegarder();
		System.out.println("[GestionComptes] Demande créée : " + id);
		return null; // succès
	}

	/**
	 * Approuve une demande → crée le compte normal.
	 * @return message d'erreur, ou null si OK
	 */
	public synchronized String approuver(String identifiant)
	{
		charger();
		DemandeCompte dem = demandes.stream()
			.filter(d -> d.identifiant.equalsIgnoreCase(identifiant) && "attente".equals(d.statut))
			.findFirst().orElse(null);
		if (dem == null) return "Demande introuvable.";

		// Créer le compte si pas déjà présent
		boolean dejaPresent = utilisateurs.stream()
			.anyMatch(u -> u.identifiant.equalsIgnoreCase(identifiant));
		if (!dejaPresent)
			utilisateurs.add(new Utilisateur(dem.identifiant, dem.motDePasse, false));

		dem.statut = "approuve";
		sauvegarder();
		System.out.println("[GestionComptes] Compte approuvé : " + identifiant);
		return null;
	}

	/**
	 * Refuse une demande.
	 * @return message d'erreur, ou null si OK
	 */
	public synchronized String refuser(String identifiant)
	{
		charger();
		DemandeCompte dem = demandes.stream()
			.filter(d -> d.identifiant.equalsIgnoreCase(identifiant) && "attente".equals(d.statut))
			.findFirst().orElse(null);
		if (dem == null) return "Demande introuvable.";

		dem.statut = "refuse";
		sauvegarder();
		System.out.println("[GestionComptes] Compte refusé : " + identifiant);
		return null;
	}

	/**
	 * Retourne la liste des demandes en attente.
	 */
	public synchronized List<DemandeCompte> getDemandesEnAttente()
	{
		charger();
		List<DemandeCompte> res = new ArrayList<>();
		for (DemandeCompte d : demandes)
			if ("attente".equals(d.statut)) res.add(d);
		return res;
	}

	/**
	 * Sérialise les demandes en attente en JSON pour l'API REST.
	 */
	public synchronized String serialiserDemandesJson()
	{
		List<DemandeCompte> att = getDemandesEnAttente();
		StringBuilder sb = new StringBuilder("{\"demandes\":[");
		for (int i = 0; i < att.size(); i++)
		{
			DemandeCompte d = att.get(i);
			if (i > 0) sb.append(",");
			sb.append("{\"identifiant\":").append(esc(d.identifiant))
			  .append(",\"date\":").append(esc(d.date)).append("}");
		}
		sb.append("]}");
		return sb.toString();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  PARSING JSON MANUEL (sans dépendance externe)
	// ══════════════════════════════════════════════════════════════════════

	private List<Utilisateur> parseUtilisateurs(String json)
	{
		List<Utilisateur> list = new ArrayList<>();
		String bloc = extraireBloc(json, "\"utilisateurs\"");
		if (bloc == null) return list;
		for (String obj : extraireObjets(bloc))
		{
			String id  = getString(obj, "identifiant");
			String mdp = getString(obj, "motDePasse");
			boolean pam = getBool(obj, "accesPAM");
			if (!id.isEmpty()) list.add(new Utilisateur(id, mdp, pam));
		}
		return list;
	}

	private List<DemandeCompte> parseDemandes(String json)
	{
		List<DemandeCompte> list = new ArrayList<>();
		String bloc = extraireBloc(json, "\"demandesCompte\"");
		if (bloc == null) return list;
		for (String obj : extraireObjets(bloc))
		{
			String id     = getString(obj, "identifiant");
			String mdp    = getString(obj, "motDePasse");
			String date   = getString(obj, "date");
			String statut = getString(obj, "statut");
			if (!id.isEmpty()) list.add(new DemandeCompte(id, mdp, date, statut));
		}
		return list;
	}

	// ── Helpers JSON minimalistes ─────────────────────────────────────────

	private static String getString(String obj, String cle)
	{
		String p = "\"" + cle + "\":\"";
		int pos = obj.indexOf(p);
		if (pos < 0) return "";
		pos += p.length();
		int end = pos;
		while (end < obj.length() && obj.charAt(end) != '"') end++;
		return obj.substring(pos, end);
	}

	private static boolean getBool(String obj, String cle)
	{
		String p = "\"" + cle + "\":";
		int pos = obj.indexOf(p);
		if (pos < 0) return false;
		pos += p.length();
		while (pos < obj.length() && obj.charAt(pos) == ' ') pos++;
		return obj.startsWith("true", pos);
	}

	private static String extraireBloc(String json, String cle)
	{
		int pos = json.indexOf(cle);
		if (pos < 0) return null;
		int p = pos + cle.length();
		while (p < json.length() && json.charAt(p) != '[' && json.charAt(p) != '{') p++;
		if (p >= json.length()) return null;
		char open = json.charAt(p), close = open == '[' ? ']' : '}';
		int depth = 0, start = p;
		for (int i = p; i < json.length(); i++)
		{
			char c = json.charAt(i);
			if (c == open) depth++;
			else if (c == close) { depth--; if (depth == 0) return json.substring(start, i + 1); }
		}
		return null;
	}

	private static List<String> extraireObjets(String tableau)
	{
		List<String> liste = new ArrayList<>();
		int depth = 0, start = -1;
		for (int i = 0; i < tableau.length(); i++)
		{
			char c = tableau.charAt(i);
			if (c == '{') { if (depth == 0) start = i; depth++; }
			else if (c == '}')
			{
				depth--;
				if (depth == 0 && start >= 0) { liste.add(tableau.substring(start, i + 1)); start = -1; }
			}
		}
		return liste;
	}

	private static String esc(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}