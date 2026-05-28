# Guide complet des commentaires — Architecture et classes

## Convention de commentaires Java

### En-tête de classe
```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  NOMCLASSE — Description courte (1-2 lignes)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Description du rôle de cette classe (3-5 lignes)
 *
 * UTILISATION :
 * ─────────────
 * Qui l'utilise, quand, comment
 *
 * ARCHITECTURE :
 * ──────────────
 * Comment s'intègre-t-elle au système
 *
 * EXEMPLE :
 * ─────────
 * code.example.here()
 *
 * ═══════════════════════════════════════════════════════════════════
 */
```

### Méthodes publiques
```java
/**
 * Description claire en une phrase.
 *
 * FLUX :
 *   1. Vérifie la condition
 *   2. Fait l'action
 *   3. Retourne le résultat
 *
 * @param parametre Description du paramètre
 * @return Description du retour
 * @throws Exception Si condition X arrive
 *
 * ⚠️  ATTENTION : Point d'attention particulier
 */
```

### Champs privés
```java
// Commentaire court en fin de ligne ← pour les simples champs
private String nom; // Nom du lot

// Ou sur plusieurs lignes si complexe :
/** Description du champ si le contexte est flou */
private ArrayList<Lot> lots;

// Pour les constantes magiques :
private static final int TIMEOUT_SECONDES = 30; // ← pourquoi 30 ?
```

---

## Description détaillée de chaque classe

### Package `app`

#### 1. **CheminApp.java** — Résolution des chemins

```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  CheminApp — Résolution des chemins relatifs
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Localiser les fichiers app/data/, app/config/, etc.
 * indépendamment du répertoire courant.
 *
 * PROBLÈME RÉSOLU :
 * ──────────────────
 * Sans ça, le serveur lancé depuis N'IMPORTE QUEL dossier ne trouve
 * plus app/data/. C'est un cauchemar en production.
 * Exemple : on lance le JAR depuis /home/user/ mais app/data/ est
 * à /home/user/planning/app/data/ → crash.
 *
 * STRATÉGIE :
 * ───────────
 * 1. Vérifie si "app/data/" existe dans le working directory
 * 2. Sinon, cherche depuis le dossier du JAR
 * 3. Sinon, fallback sur working directory (ancien comportement)
 *
 * UTILISATION :
 * ─────────────
 * String chemin = CheminApp.resoudre("app/data/lots.json");
 * String base = CheminApp.getBaseDir(); // racine du projet
 *
 * ATTENTION :
 * ───────────
 * ⚠️  Appelé TRÈS TÔT au démarrage → éviter dépendances circulaires
 * ⚠️  Ne pas appeler depuis StaticInitializer ou constructeurs statiques
 *       avec dépendances vers CheminApp lui-même
 *
 * ═══════════════════════════════════════════════════════════════════
 */
private static final String BASE_DIR = calculerBaseDir();

public static String resoudre(String cheminRelatif)
{
    // Transforme "app/data/lots.json" en chemin absolu
    // Exemple : "C:\Users\erwan\...\gestionNOZ\app\data\lots.json"
    return Paths.get(BASE_DIR, cheminRelatif).toString();
}

public static String getBaseDir()
{
    // Retourne le répertoire racine du projet (là où se trouve app/)
    return BASE_DIR;
}

// CALCULER LA RACINE — stratégies cascadantes
private static String calculerBaseDir()
{
    // Stratégie 1 : working directory contient déjà app/data/ ?
    // ╔══════════════════════════════════════════════════════════╗
    // ║ Cas NORMAL si run_SERVEUR.bat fait "cd /d %~dp0"       ║
    // ║ Après le cd, le working dir = dossier du JAR            ║
    // ║ → "app/data" devrait exister                             ║
    // ╚══════════════════════════════════════════════════════════╝
    String wd = System.getProperty("user.dir");
    if (new File(wd, "app/data").exists())
    {
        System.out.println("[CheminApp] Racine trouvée : " + wd);
        return wd;
    }

    // Stratégie 2 : chercher depuis le dossier du JAR
    // ╔══════════════════════════════════════════════════════════╗
    // ║ Récupère l'URL du JAR/classes compilées                 ║
    // ║ Remonte au dossier parent si on vient d'une classe .jar ║
    // ║ Cherche "app/data" à partir de là                       ║
    // ╚══════════════════════════════════════════════════════════╝
    try {
        URL location = CheminApp.class.getProtectionDomain()
                                      .getCodeSource()
                                      .getLocation();
        File jarDir = new File(location.toURI()).getParentFile();
        while (jarDir != null) {
            if (new File(jarDir, "app/data").exists()) {
                System.out.println("[CheminApp] Racine trouvée via JAR : " + jarDir);
                return jarDir.getAbsolutePath();
            }
            jarDir = jarDir.getParentFile();
        }
    } catch (Exception e) {
        System.err.println("[CheminApp] ⚠️  Erreur lors localisation JAR : " + e.getMessage());
    }

    // Stratégie 3 : fallback
    // ╔══════════════════════════════════════════════════════════╗
    // ║ Retour au working directory par défaut                  ║
    // ║ → va probablement échouer, mais au moins on est cohérent║
    // ╚══════════════════════════════════════════════════════════╝
    System.out.println("[CheminApp] ⚠️  Fallback sur working directory : " + wd);
    return wd;
}
```

---

#### 2. **IControleur.java** — Interface du contrôleur

```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  IControleur — Interface abstraite de contrôle
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Définir le contrat que TOUS les contrôleurs doivent respecter :
 *   • Controleur (mode solo)      : appels directs à PlanningGlobal
 *   • ControleurClient (réseau)   : appels HTTP au ServeurHTTP
 *
 * L'IHM (FenetrePrincipale, dialogues) utilise UNIQUEMENT cette
 * interface → elle ne sait pas si c'est solo ou réseau !
 *
 * AVANTAGES DU PATTERN :
 * ──────────────────────
 * ✓ L'IHM reste indépendante du transport (local vs réseau)
 * ✓ Tests faciles : créer un MockControleur implémentant IControleur
 * ✓ Ajouter une nouvelle source (WebSocket, gRPC) : une nouvelle classe
 *   implémentant IControleur, sans toucher à l'IHM
 *
 * DEUX SIGNATURES DE modifierLot :
 * ────────────────────────────────
 * POURQUOI ? Raison historique + compatibilité :
 *
 *   modifierLot() "classique"      ← DialogEditLot (vieille IHM)
 *     Champs : administratifs de base
 *     Exemple : typologie, affaire, nombrePieces, etc.
 *
 *   modifierLotComplet() "new"     ← CarteLot (nouvelle interface)
 *     Champs : administratifs + LOGISTIQUES
 *     Exemple : formatCarton, collisage, distribution, etc.
 *
 * ⚠️  IMPORTANT : DialogEditLot appelle modifierLot()
 *                  CarteLot appelle modifierLotComplet()
 *                  LES DEUX DOIVENT EXISTER pour pas casser le code
 *
 * ═══════════════════════════════════════════════════════════════════
 */

public interface IControleur
{
    // ───────────────────────────────────────────────────────────────
    // SECTION 1 : ACCÈS AUX DONNÉES
    // ───────────────────────────────────────────────────────────────

    /**
     * Retourne la liste des sociétés actuellement chargées.
     *
     * Comportement :
     *   Solo    → accès direct à PlanningGlobal.getSocietes()
     *   Réseau  → effectue GET /societes auprès du serveur
     *
     * ⚠️  NE PAS MODIFIER le retour directement !
     *     Les modifications doivent passer par ajouterSociete() ou modifierSociete()
     *
     * @return ArrayList<Societe> — liste potentiellement vide
     */
    ArrayList<Societe> getSocietes();

    /**
     * Retourne la liste des lots actuellement chargés.
     *
     * Comportement :
     *   Solo    → accès direct à PlanningGlobal.getLots()
     *   Réseau  → effectue GET /lots auprès du serveur
     *
     * ⚠️  NE PAS MODIFIER le retour directement !
     *
     * @return ArrayList<Lot> — liste potentiellement vide
     */
    ArrayList<Lot> getLots();

    // ───────────────────────────────────────────────────────────────
    // SECTION 2 : GESTION DES LOTS
    // ───────────────────────────────────────────────────────────────

    /**
     * Crée un nouveau lot avec les paramètres spécifiés.
     *
     * Flux :
     *   1. Validation des paramètres
     *   2. Création objet Lot
     *   3. Ajout à la liste
     *   4. Sauvegarde (auto)
     *
     * @param numCDE        Numéro de commande affiché à l'utilisateur
     * @param nbPieces      Nombre total de pièces
     * @param cadence       Pièces/heure (nominal)
     * @param valeurVente   Montant en €
     * @param statut        "OU", "TC", "MC"
     * @param statut        "En cours", "Livré"
     * @param semaine       "S17"
     * @param priorite      1-5 (1 = très prioritaire)
     * @param emplacement   Numéro de zone (ex: "A12")
     *
     * ⚠️  Certains paramètres peuvent être null → défauts appliqués
     */
    void ajouterLot(int numCDE, String typologie, String affaire,
                    int nbPieces, double cadence, int valeurVente,
                    String statut, String statutEchant,
                    String semaine, int priorite,
                    String lotACharge, String emplacement,
                    boolean sousDouane, boolean machine,
                    String dateReception,
                    String datePaiement, String commentaire);

    /**
     * Supprime un lot de la liste.
     *
     * Conséquences :
     *   - Le lot disparaît du PlanningGlobal
     *   - Tous les ACE/Sociétés qui l'avaient reçoivent un callback
     *   - Les heures "libérées" du lot reviennent à chaque ACE
     *
     * @param lot L'objet à supprimer (doit exister dans les lots courants)
     *
     * ⚠️  Non-existent lots sont silencieusement ignorés (pas d'exception)
     */
    void supprimerLot(Lot lot);

    /**
     * Modifie les champs administratifs d'un lot (signature "classique").
     *
     * Champs modifiés :
     *   • Administratifs : typologie, affaire, semaine, etc.
     *   • Charges : nbPieces, cadence, heures (recalculées)
     *
     * Champs NON modifiés :
     *   • Logistiques : formatCarton, collisage, distribution, nbPers
     *     → ceux-ci sont du ressort de CarteLot via modifierLotComplet()
     *
     * UTILISATEURS :
     *   DialogEditLot          → appelle cette méthode
     *   PanelAffectation       → appelle cette méthode
     *   DialogAjoutLot         → appelle cette méthode
     *
     * @param lot          Objet à modifier (doit exister)
     * @param typographie  "électronique", "carton", etc.
     * @param affaire      Code client/projet
     * @param nbPieces     Quantité totale
     * @param cadence      Pièces/heure
     * @param valeurVente  Prix de vente TTC
     * @param statut       "OU", "TC", "MC"
     * @param statut       Statut échantillonnage
     * @param semaine      "S17", "S18", etc.
     * @param priorite     1-5 (plus haut = plus urgent)
     * @param lotACharge   Numéro lot parent (regroupement)
     * @param emplacement  Zone de stockage
     * @param sousDouane   true si lot en zone douanière
     * @param machine      true si process utilisé une machine
     * @param dateReception Date réception matière première
     * @param datePaiement  Date paiement effectif
     * @param commentaire   Notes supplémentaires
     *
     * EFFET SECONDAIRE :
     *   ✓ Recalcul des heures ACE associés au lot
     *   ✓ Sauvegarde automatique (fichier JSON)
     */
    void modifierLot(Lot lot,
                     String typographie, String affaire,
                     int nbPieces, double cadence, int valeurVente,
                     String statut, String statutEchant,
                     String semaine, int priorite,
                     String lotACharge, String emplacement,
                     boolean sousDouane, boolean machine,
                     String dateReception,
                     String datePaiement, String commentaire);

    /**
     * Modifie TOUS les champs d'un lot (signature "complète").
     *
     * Champs modifiés : ADMINISTRATIFS + LOGISTIQUES
     *   • formatCarton, collisage, distribution, nbPers, cadenceReel
     *   • poucentrecupCartonFour, methode
     *   • + tous les champs de modifierLot()
     *
     * UTILISATEUR UNIQUE :
     *   CarteLot.actionPerformed() → appelée lors du double-clic
     *
     * ⚠️  SIGNATURE TRÈS LONGUE → mauvais design ? Oui, mais c'était
     *     comme ça et on veut pas casser CarteLot.
     *
     * Les paramètres nouveaux (logistiques) sont à la fin :
     *   @param formatCarton             Ex: "1/2", "box"
     *   @param collisage                Ex: 6 (6 pièces par carton)
     *   @param nbPers                   Nombre de personnes assignées
     *   @param distribution             Ex: "PI", "PM", "PREPA"
     *   @param cadenceReel              Si différent de cadence nominal
     *   @param poucentrecupCartonFour   Pourcentage récupération cartons
     */
    void modifierLotComplet(Lot lot,
                           String typographie, String affaire, String semaine, String emplacement,
                           String dateReception, String datePaiement,
                           int nbPieces, double prixUnitaire, int valeurVente,
                           double cadence, double heures, String lotACharge,
                           String statut, String statutEchant,
                           boolean sousDouane, boolean machine, String commentaire,
                           String formatCarton, int collisage, int nbPers,
                           String distribution, double cadenceReel,
                           int poucentrecupCartonFour);

    // ───────────────────────────────────────────────────────────────
    // SECTION 3 : GESTION DES SOCIÉTÉS
    // ───────────────────────────────────────────────────────────────

    /**
     * Crée une nouvelle société.
     * ...
     */
    void ajouterSociete(/* paramètres */);

    // ... (autres méthodes)
}
```

---

#### 3. **Controleur.java** — Mode Solo

```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  Controleur — Contrôleur du mode Solo (application locale)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Gère l'application en mode SANS RÉSEAU :
 *   • Données stockées en local (JSON dans app/data/)
 *   • Pas de serveur central
 *   • Une seule instance lancée à la fois
 *
 * IMPLÉMENTE : IControleur
 * UTILISE : PlanningGlobal (métier), DonneesSauvegarder (persistance)
 *
 * FLUX DE DÉMARRAGE :
 * ───────────────────
 *   1. main() → new Controleur()
 *   2. Constructeur crée PlanningGlobal() et DonneesSauvegarder()
 *   3. Affiche FenetreLogin (saisie identifiant/mot de passe)
 *   4. Au click "Connexion" → lancerApp(identifiant, useExcel)
 *   5. Charge données depuis Excel ou JSON
 *   6. Affiche FenetrePrincipale
 *
 * DATAFLOW : utilisateur modifie → FenetrePrincipale → Controleur
 *            → PlanningGlobal → DonneesSauvegarder → JSON sur disque
 *
 * ARCHITECTURE : implémente le pattern Strategy (IControleur)
 *   L'IHM ne sait pas qu'elle utilise Controleur, elle appelle
 *   simplement les méthodes de l'interface IControleur.
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public class Controleur implements IControleur
{
    /**
     * Écran principal : gère tous les panneaux (lots, sociétés, diagramme, etc.)
     * Créé après le login et le chargement des données.
     */
    private FenetrePrincipale fenetre;

    /**
     * Métier : gestion des lots, sociétés, calculs de planning.
     * Instance créée au démarrage, partagée entre FenetrePrincipale
     * et tous les dialogues.
     */
    private PlanningGlobal metier;

    /**
     * Persistance : lecture/écriture fichiers JSON sur disque.
     * Appelé à chaque sauvegarde (après modification d'un lot/société).
     */
    private DonneesSauvegarder savDonnees;

    /** Chemin vers le fichier "lots.json" courant */
    private String cheminLotsJson;

    /** Chemin vers le fichier "societes.json" courant */
    private String cheminSocietesJson;

    /** Chemins constants (sur disque) */
    private static final String LOTS_JSON     = "app/data/courutilisation/lots.json";
    private static final String SOCIETES_JSON = "app/data/courutilisation/societes.json";
    private static final String SOCIETES_REF  = "app/data/pastouche/societes.json";

    /**
     * Constructeur — initialise les composants et affiche l'écran de connexion.
     *
     * Étapes :
     *   1. Crée le métier (PlanningGlobal)
     *   2. Crée la persistance (DonneesSauvegarder)
     *   3. Définit les chemins des fichiers JSON
     *   4. Lance FenetreLogin sur le thread Swing (invokeLater)
     *
     * ⚠️  L'écran de login apparaît de façon ASYNCHRONE (invokeLater)
     *     → le constructeur retourne immédiatement.
     */
    public Controleur()
    {
        this.metier             = new PlanningGlobal();
        this.savDonnees         = new DonneesSauvegarder();
        this.cheminLotsJson     = LOTS_JSON;
        this.cheminSocietesJson = SOCIETES_JSON;
        SwingUtilities.invokeLater(() -> new FenetreLogin(this));
    }

    /**
     * Lance l'application une fois l'utilisateur connecté.
     *
     * Flux :
     *   1. Si useExcel=true : demande fichier XLSX et charge
     *      Sinon : charge depuis JSON
     *   2. Crée FenetrePrincipale (affichage)
     *   3. Lance sur le thread Swing (invokeLater)
     *
     * @param login       Identifiant saisi (ex: "PAM", "Societe A")
     * @param utiliserExcel true = charger depuis XLSX, false = depuis JSON
     *
     * ⚠️  Cette méthode est appelée par FenetreLogin.validerConnexion()
     */
    public void lancerApp(String login, boolean utiliserExcel)
    {
        SwingUtilities.invokeLater(() -> {
            if (utiliserExcel)
                chargerDepuisExcelInteractif();  // demande fichier, charge
            else
                chargerFallbackJson();           // charge depuis JSON
            this.fenetre = new FenetrePrincipale(this);  // affiche l'IHM
        });
    }

    /**
     * Charge les données depuis un fichier Excel (sélection interactive).
     *
     * Flux :
     *   1. Affiche JFileChooser pour sélectionner "lots.xlsx"
     *   2. Affiche une deuxième demande pour "heures_ace.xlsx"
     *   3. ExcelReader.lireLots() → liste Lot
     *   4. Extraction numéro semaine depuis premier lot
     *   5. ExcelReader.lireSocietes() + chargerDeduitHeures()
     *   6. Métier.chargerDepuisExcel()
     *   7. Sauvegarde en JSON (pour utilisation ultérieure)
     *
     * ⚠️  Si l'utilisateur annule (clique Cancel) : methode retourne sans faire
     *     rien, et reste l'ancienne donnée (fallback JSON).
     *
     * ⚠️  Exceptions IO non catchées → crash de l'application.
     *     À améliorer : capturer et afficher message d'erreur.
     */
    private void chargerDepuisExcelInteractif()
    {
        // Première demande : fichier des lots
        String xlsx = demanderFichierExcel("Sélectionner le fichier des lots (XLSX / XLSM)");
        if (xlsx == null) return;  // Annulé

        try {
            // Lecture lots → extraction semaine
            ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsx);
            int semaine = 0;  // par défaut
            if (!tempLots.isEmpty()) {
                String sem = tempLots.get(0).getSemaine();
                try {
                    // Regex pour extraire "S17" → 17
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})$")
                        .matcher(sem == null ? "" : sem.trim());
                    if (m.find()) semaine = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {}
            }

            // Deuxième demande : fichier des heures ACE
            String xlsxHeures = demanderFichierExcel("Sélectionner le fichier des heures ACE");
            if (xlsxHeures == null) xlsxHeures = xlsx;  // fallback : même fichier

            // Charge les données
            metier.chargerDepuisExcel(xlsx, SOCIETES_REF, semaine, xlsxHeures);

            // Sauvegarde en JSON pour la prochaine utilisation
            sauvegarderDonnees();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erreur lecture Excel : " + e.getMessage());
            chargerFallbackJson();  // fallback
        }
    }

    /**
     * Charge les données depuis JSON (fallback si Excel échoue).
     *
     * Fichiers chargés :
     *   • app/data/courutilisation/lots.json
     *   • app/data/courutilisation/societes.json
     *
     * ⚠️  Si JSON n'existe pas encore (première utilisation) → listes vides
     *     et FenetrePrincipale s'ouvre sur une application vierge.
     */
    private void chargerFallbackJson()
    {
        try {
            savDonnees.charger(metier, "app/data/courutilisation");
            System.out.println("[Controleur] Données chargées depuis JSON");
        } catch (IOException e) {
            System.err.println("[Controleur] Erreur chargement JSON : " + e.getMessage());
            // Listes restent vides, ok pour utilisation initiale
        }
    }

    /**
     * Sauvegarde lots et sociétés dans les fichiers JSON.
     * Appelé après chaque modification via IControleur.
     */
    private void sauvegarderDonnees()
    {
        try {
            savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
            savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(),
                                           cheminSocietesJson);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erreur sauvegarde : " + e.getMessage());
        }
    }

    /**
     * Affiche un dialogue de sélection de fichier XLSX.
     * @return chemin absolu du fichier sélectionné, ou null si annulé
     */
    private String demanderFichierExcel(String titre)
    {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Excel (XLSX/XLSM)", "xlsx", "xlsm", "xls"));
        int ret = fc.showOpenDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) return null;
        return fc.getSelectedFile().getAbsolutePath();
    }

    // ───────────────────────────────────────────────────────────────
    // IMPLÉMENTATION IControleur
    // ───────────────────────────────────────────────────────────────

    @Override
    public ArrayList<Societe> getSocietes() {
        return metier.getSocietes();
    }

    @Override
    public ArrayList<Lot> getLots() {
        return metier.getLots();
    }

    @Override
    public void ajouterLot(Lot lot) {
        metier.ajouterLot(lot);
        sauvegarderDonnees();
    }

    @Override
    public void modifierLot(Lot lot, String typographie, String affaire,
                           int nbPieces, double cadence, int valeurVente,
                           String statut, String statutEchant, String semaine, int priorite,
                           String lotACharge, String emplacement, boolean sousDouane,
                           boolean machine, String dateReception, String datePaiement,
                           String commentaire)
    {
        metier.modifierLot(lot, typographie, affaire, nbPieces, cadence, valeurVente,
                          statut, statutEchant, semaine, priorite, lotACharge, emplacement,
                          sousDouane, machine, dateReception, datePaiement, commentaire);
        sauvegarderDonnees();  // sauvegarde auto après chaque modif
    }

    // ... (autres méthodes de l'interface)
}
```

---

### Package `app.metier`

#### **PlanningGlobal.java** — Métier principal

```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  PlanningGlobal — Cœur du métier
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Gère TOUTES les données métier :
 *   • ArrayList<Lot> : tous les lots de production
 *   • ArrayList<Societe> : toutes les sociétés de conditionnement
 *   • ArrayList<FicheRoute> : fiches de route des lots
 *
 * RESPONSABILITÉS :
 * ──────────────────
 * ✓ Charger les données (Excel, JSON)
 * ✓ Ajouter/modifier/supprimer lots et sociétés
 * ✓ Calculer plannings, charges, heures
 * ✓ Gérer les affectations (lot → société → ACE)
 *
 * ARCHITECTURE :
 * ──────────────
 * Utilisée par :
 *   • Controleur (solo) → accès direct
 *   • ServeurHTTP → accès direct (multithreads protégé par ReadWriteLock)
 *   • ControleurClient → accès indirect via requêtes HTTP
 *
 * NE FAIT PAS :
 *   ✗ Affichage (IHM) → c'est le rôle des pannels Swing
 *   ✗ Persistance → délégué à DonneesSauvegarder
 *   ✗ Réseau → délégué à ServeurHTTP/ControleurClient
 *   ✗ Chiffrement → délégué à ChiffrementAES
 *
 * STRUCTURE INTERNE :
 * ───────────────────
 *   [PlanningGlobal]
 *       ├─ ArrayList<Lot>
 *       ├─ ArrayList<Societe>
 *       │   └─ ArrayList<Ace> (dans chaque Societe)
 *       │       └─ ArrayList<Lot> (dans chaque Ace)
 *       └─ ArrayList<FicheRoute>
 *
 * EXEMPLE D'UTILISATION :
 * ──────────────────────
 *   PlanningGlobal metier = new PlanningGlobal();
 *   metier.chargerDepuisExcel("lots.xlsx", "societes.json", 17, "heures.xlsx");
 *
 *   Lot nouveau = new Lot(1, 1000, 100, 10, 5000, "OU", "non");
 *   metier.ajouterLot(nouveau);
 *
 *   metier.modifierLot(nouveau, "electronique", "CLI001", ...);
 *   // la sauvegarde se fait APRÈS à l'extérieur via DonneesSauvegarder
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public class PlanningGlobal
{
    /**
     * Flag global pour indication horaire saisies (heure sup ou pas).
     * Utilisé par certains calculs de heures ACE.
     * ⚠️  Plutôt mauvais design → à refactoriser (passer en paramètre)
     */
    public static boolean estHeureSup;

    /** Liste des sociétés de production chargées */
    private ArrayList<Societe> societes;

    /** Liste des lots à produire */
    private ArrayList<Lot> lots;

    /** Liste des fiches de route (suivi de production) */
    private ArrayList<FicheRoute> ficheRoute;

    /**
     * Constructeur — initialise les listes vides.
     * Les données sont chargées ensuite via chargerDepuisExcel()
     * ou chargerDepuisJson().
     */
    public PlanningGlobal()
    {
        this.societes    = new ArrayList<>();
        this.lots        = new ArrayList<>();
        this.ficheRoute  = new ArrayList<>();
    }

    /**
     * Charge les données depuis un fichier Excel.
     *
     * Flux :
     *   1. ExcelReader.lireLots(xlsx)           → liste Lot
     *   2. ExcelReader.lireSocietes(societes.json, lots)
     *   3. ExcelReader.ajouterHeuresDepuisExcel(xlsxHeures, societes, semaine)
     *   4. Affectation lots → sociétés → ACE
     *
     * @param cheminXlsx       Fichier export.XLSX (lots)
     * @param cheminSocietes   Fichier societes.json (référence)
     * @param semaine          Numéro semaine (1-53)
     * @param cheminXlsxHeures Fichier heures ACE.xlsx
     *
     * @throws IOException Si fichiers manquants ou corrompus
     *
     * ⚠️  Cette opération peut être LENTE (parsing Excel)
     *     Ne pas l'appeler depuis le thread Swing !
     */
    public void chargerDepuisExcel(String cheminXlsx, String cheminSocietes,
                                  int semaine, String cheminXlsxHeures) throws IOException
    {
        this.lots     = ExcelReader.lireLots(cheminXlsx);
        this.societes = ExcelReader.lireSocietes(cheminSocietes, this.lots);
        ExcelReader.ajouterHeuresDepuisExcel(cheminXlsxHeures, this.societes, semaine);
    }

    /**
     * Charge les données depuis fichiers JSON.
     * Plus rapide que Excel, utilisé après les premières charges.
     *
     * @param cheminLotsJson    Fichier lots.json
     * @param cheminSocietesJson Fichier societes.json
     * @throws IOException Si fichiers manquants
     */
    public void chargerDepuisJson(String cheminLotsJson, String cheminSocietesJson)
                                 throws IOException
    {
        // Remarque bizarre : utilise ExcelReader pour JSON ?
        // À investiguer... peut-être que ExcelReader est "lecteur universel"
        this.lots     = ExcelReader.lireLots(cheminLotsJson);
        this.societes = ExcelReader.lireSocietes(cheminSocietesJson, this.lots);
    }

    /**
     * Modifie les champs administratifs d'un lot.
     * Utilisé par DialogEditLot et PanelAffectation.
     *
     * Recalcule automatiquement les heures après modification des pièces/cadence.
     * Alerte si les heures affectées aux ACE changeaient de façon importante.
     *
     * @param lot              Objet à modifier
     * @param typographie      "électronique", etc.
     * @param affaire          Code client/projet
     * @param nbPieces         Nouvelle quantité
     * @param cadence          Nouvelle vitesse (pièces/heure)
     * @param valeurVente      Nouveau prix
     * @param statut           "OU", "TC", "MC"
     * @param statutEchant     "Non", "Oui", etc.
     * @param semaine          "S17", etc.
     * @param priorite         1-5
     * @param lotACharge       Regroupement
     * @param emplacement      Zone stockage
     * @param sousDouane       true/false
     * @param dateReception    Date
     * @param datePaiement     Date
     * @param commentaire      Notes libres
     *
     * CALCULS AUTOMATIQUES APRÈS :
     *   ✓ recalculerHeures()    → heures = nbPieces / cadence
     *   ✓ Mise à jour métier ACE si heures changent significantly
     *   ✓ Log des changements   → console [DEBUG]
     */
    public void modifierLot(Lot lot,
                           String typographie, String affaire,
                           int nbPieces, double cadence, int valeurVente,
                           String statut, String statutEchant,
                           String semaine, int priorite,
                           String lotACharge, String emplacement,
                           boolean sousDouane, String dateReception,
                           String datePaiement, String commentaire)
    {
        // Sauvegarde heures AVANT pour comparer après
        int heuresAvant = (int) Math.ceil(lot.getHeures());

        // Mise à jour tous les champs
        lot.setTypologie    (typographie != null ? typographie : "");
        lot.setAffaire      (affaire != null ? affaire : "");
        lot.setNbPieces     (nbPieces);
        lot.setCadence      (cadence);
        lot.recalculerHeures();  // ← IMPORTANT : recalcule heures après modif pièces/cadence
        lot.setValeurVente  (valeurVente);
        // ... etc

        // Calcule delta heures pour logs
        int heuresApres = (int) Math.ceil(lot.getHeures());
        int delta = heuresAvant - heuresApres;
        if (delta != 0) {
            System.out.println("[PlanningGlobal] ⚠️  Lot " + lot.getNumCDE() +
                             " heures avant:" + heuresAvant + " après:" + heuresApres +
                             " delta:" + delta);
        }
    }

    // ... (autres méthodes : ajouterLot, supprimerLot, etc.)
}
```

---

### Package `app.metier.collecte`

#### **DonneesSauvegarder.java** — Persistance JSON

```java
/**
 * ═══════════════════════════════════════════════════════════════════
 *  DonneesSauvegarder — Lecture/écriture fichiers JSON (avec chiffrement optionnel)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RÔLE :
 * ──────
 * Abstraire la persistance des données sur disque.
 * Gère les conversions Objet Java ↔ JSON.
 *
 * FICHIERS GÉRÉS :
 * ────────────────
 *   • app/data/courutilisation/lots.json        → ArrayList<Lot>
 *   • app/data/courutilisation/societes.json    → ArrayList<Societe>
 *   • app/data/courutilisation/lots_tmp.json    → sauvegarde temporaire
 *   • app/data/courutilisation/societes_tmp.json
 *
 * CHIFFREMENT OPTIONNEL :
 * ──────────────────────
 * Version originale : NO chiffrement (fichiers JSON en clair).
 * Version avec correctif #9 : chiffrement AES-256-CBC OPTIONNEL.
 *
 * Activé via setCrypte(ChiffrementAES aes) :
 *   savDonnees.setCrypte(new ChiffrementAES(...));
 *   savDonnees.sauvegarderLots(lots, chemin);  // → fichier chiffré
 *
 * Sans appel à setCrypte() : comportement original (rétrocompatibilité).
 *
 * FLUX SAUVEGARDE AVEC CHIFFREMENT :
 * ──────────────────────────────────
 *   1. construireJsonLots(lots)      → String JSON (clair)
 *   2. aes.chiffrer(json)            → Base64(IV + ciphertext)
 *   3. ecrire(chemin, encrypted)     → fichier sur disque
 *
 * FLUX CHARGEMENT AVEC CHIFFREMENT :
 * ──────────────────────────────────
 *   1. lire(chemin)                  → String Base64(IV + ciphertext)
 *   2. aes.dechiffrer(encrypted)     → String JSON (clair)
 *   3. parserLots(json)              → ArrayList<Lot>
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public class DonneesSauvegarder
{
    /** Nom fichier lots en JSON */
    private static final String FICHIER_LOTS = "lots.json";

    /** Nom fichier sociétés en JSON */
    private static final String FICHIER_SOCIETES = "societes.json";

    /**
     * Cipher optionnel (null = pas de chiffrement, fichiers en clair).
     * Activé par setCrypte(aes) avant les appels à sauvegarder/charger.
     */
    private ChiffrementAES aes = null;

    /**
     * Active le chiffrement AES pour toutes les opérations suivantes.
     *
     * Appel typique dans ServeurHTTP :
     *   ChiffrementAES aes = ChiffrementAES.chargerOuCreer(...);
     *   this.savDonnees.setCrypte(aes);
     *
     * Après : sauvegarderLots(), sauvegarderSocietes(), charger()
     *         vont chiffrer/déchiffrer automatiquement.
     *
     * @param aes Objet ChiffrementAES (non-null recommandé pour sécurité)
     */
    public void setCrypte(ChiffrementAES aes)
    {
        this.aes = aes;
    }

    /**
     * Sauvegarde la liste des lots dans un fichier JSON (optionnellement chiffré).
     *
     * Flux :
     *   1. Construit JSON string depuis ArrayList<Lot>
     *   2. Si aes != null : chiffre le JSON
     *   3. Écrit sur disque
     *
     * @param lots            Liste des lots à sauvegarder
     * @param cheminFichier   Chemin où écrire (ex: "app/data/.../lots.json")
     *
     * @throws IOException Si problème disque (pas d'espace, permissions, etc.)
     *
     * FICHIER RÉSULTANT :
     *   • Si aes=null    : JSON en clair, lisible en éditeur texte
     *   • Si aes!=null   : Base64(IV + ciphertext), impossible à lire sans clé
     *
     * ⚠️  Pas de gestion des fichiers temporaires (lots_tmp.json) ici.
     *     À améliorer : créer fichier temp d'abord, ensuite renommer.
     */
    public void sauvegarderLots(ArrayList<Lot> lots, String cheminFichier) throws IOException
    {
        String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
        String json = construireJsonLots(lots);   // conversion Lot → JSON string
        ecrire(chemin, json);                     // écriture avec chiffrement éventuel
    }

    /**
     * Sauvegarde la liste des sociétés dans un fichier JSON (optionnellement chiffré).
     *
     * @param societes        Liste des sociétés
     * @param lots            Liste des lots (contexte métier)
     * @param cheminFichier   Chemin cible
     *
     * @throws IOException Si problème disque
     */
    public void sauvegarderSocietes(ArrayList<Societe> societes, ArrayList<Lot> lots,
                                   String cheminFichier) throws IOException
    {
        String chemin = cheminFichier.endsWith(".json") ? cheminFichier : cheminFichier + ".json";
        String json = construireJsonSocietes(societes, lots);
        ecrire(chemin, json);
    }

    /**
     * Charge les données depuis fichiers JSON et les injecte dans PlanningGlobal.
     *
     * Fichiers attendus :
     *   • <dossier>/lots.json
     *   • <dossier>/societes.json
     *
     * @param metier          Objet PlanningGlobal à remplir
     * @param cheminDossier   Dossier contenant les fichiers JSON
     *
     * @throws IOException Si fichiers manquants ou corrompus
     */
    public void charger(PlanningGlobal metier, String cheminDossier) throws IOException
    {
        String cheminLots = cheminDossier + "/" + FICHIER_LOTS;
        String cheminSocietes = cheminDossier + "/" + FICHIER_SOCIETES;

        // Charge les objets
        ArrayList<Lot> lots = chargerLots(cheminLots);
        ArrayList<Societe> societes = chargerSocietes(cheminSocietes, lots);

        // Injecte dans le métier
        metier.setSocietes(societes);
        metier.setLots(lots);
    }

    /**
     * Écriture bas niveau : gère chiffrement et écriture sur disque.
     *
     * Si aes != null :
     *   1. Chiffre la chaîne JSON
     *   2. Écrit Base64(IV+cipher) sur disque
     * Sinon :
     *   1. Écrit directement la chaîne JSON
     *
     * @param chemin Chemin fichier
     * @param contenu Chaîne JSON à écrire
     *
     * @throws IOException Si problème disque
     */
    private void ecrire(String chemin, String contenu) throws IOException
    {
        String donneeFinale = contenu;
        if (aes != null) {
            donneeFinale = aes.chiffrer(contenu);  // String JSON → Base64(encrypté)
        }
        Files.write(Paths.get(chemin), donneeFinale.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Lecture bas niveau : gère déchiffrement et lecture depuis disque.
     *
     * Si aes != null :
     *   1. Lit Base64(IV+cipher) depuis disque
     *   2. Déchiffre → JSON en clair
     * Sinon :
     *   1. Lit directement JSON en clair
     *
     * @param chemin Chemin fichier
     * @return Chaîne JSON déchiffrée (ou clair si pas de chiffrement)
     *
     * @throws IOException Si problème disque
     */
    private String lire(String chemin) throws IOException
    {
        String donneeChiffree = new String(Files.readAllBytes(Paths.get(chemin)),
                                           StandardCharsets.UTF_8);
        if (aes != null) {
            return aes.dechiffrer(donneeChiffree);  // Base64(encrypté) → JSON clair
        }
        return donneeChiffree;
    }

    /**
     * Convertit une liste de Lot en chaîne JSON.
     * Format : [ { lot1 }, { lot2 }, ... ]
     *
     * ⚠️  Utilise JsonSerialiser pour la conversion
     *
     * @param lots Liste à convertir
     * @return String JSON
     */
    private String construireJsonLots(ArrayList<Lot> lots)
    {
        // Délégation à JsonSerialiser (détail d'implémentation)
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lots.size(); i++) {
            sb.append(JsonSerialiser.toJson(lots.get(i)));
            if (i < lots.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ... (construireJsonSocietes, chargerLots, chargerSocietes, etc.)
}
```

---

## Synthèse des patterns et bonne pratiques

### Patterns de conception utilisés

| Pattern | Où | Objectif |
|---------|-----|----------|
| **Strategy** | IControleur | Abstraire solo vs réseau |
| **Singleton-like** | CheminApp | Un point d'ancrage pour les chemins |
| **Factory** | ExcelReader | Créer Lot/Societe depuis Excel |
| **Adapter** | DonneesSauvegarder | Transformer JSON ↔ Objet |
| **Observer** | Polling HTTP | Clients synced avec serveur |
| **Thread Pool** | ServeurHTTP | Gérer plusieurs clients |

### Bonnes pratiques à respecter

✅ **À FAIRE** :
- Toujours mettre `finally { unlock() }` quand on prend un verrou
- Sauvegarder après chaque modification métier
- Utiliser `CheminApp.resoudre()` pour tous les chemins
- Loger avec préfixe `[CLASSE]` pour faciliter le filtrage
- Null-check sur les paramètres String (utiliser ternaire)
- Utiliser `@Override` sur les méthodes surchargées

❌ **À ÉVITER** :
- Appeler `File.getAbsolutePath()` sans CheminApp
- Modifier les listes retournées par getter (clone si besoin)
- Oublier `throws IOException` dans signatures
- Appeler `System.exit()` sans log préalable
- Utiliser `*` imports au lieu d'imports explicites
- Mélanger logique métier et présentation (IHM)

---

**Dernière mise à jour** : 28/05/2026  
**Auteur** : Développeur
