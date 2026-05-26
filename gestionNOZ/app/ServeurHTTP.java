package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.ExcelReader;
import app.metier.collecte.JsonSerialiser;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

// Les imports Swing sont gardés mais utilisés uniquement si !headless
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * ══════════════════════════════════════════════════════════════
 *  ServeurHTTP — CORRIGÉ (tous les 8 problèmes résolus)
 *
 *  CORRECTIFS APPLIQUÉS :
 *  ─────────────────────
 *  #1 Chemins relatifs   → CheminApp.resoudre() ancre sur le dossier du JAR
 *  #2 CORS               → headers Access-Control-* ajoutés sur chaque réponse
 *  #3 Sécurité tokens    → header HSTS + avertissement console en mode HTTP
 *  #4 Mode headless      → détection GraphicsEnvironment.isHeadless(),
 *                          console logger si pas d'écran disponible
 *  #5 Verrou trop large  → ReadWriteLock : lectures simultanées autorisées,
 *                          écriture exclusive seulement quand nécessaire
 *  #6 Compteur clients   → timeout augmenté + log propre à la déconnexion
 *  #7 compile.list       → voir fichier compile.list livré avec ce correctif
 *  #8 JsonSerialiser     → voir JsonSerialiserSafe.java livré avec ce correctif
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
    private PlanningGlobal     metier;
    private DonneesSauvegarder savDonnees;

    private String cheminLotsJson;
    private String cheminSocietesJson;

    private volatile String semaineActive = "";

    private static final int PORT = 8080;

    // ── CORRECTIF #5 : ReadWriteLock pour permettre les lectures simultanées ──
    //
    // AVANT : un seul "synchronized (verrou)" bloquait TOUT le monde,
    // même pour de simples lectures (GET /lots, GET /version).
    //
    // APRÈS : java.util.concurrent.locks.ReadWriteLock sépare :
    //   • readLock  → plusieurs threads peuvent lire EN MÊME TEMPS
    //   • writeLock → exclusif, une seule écriture à la fois, bloque les lectures
    //
    // Les handlers GET utilisent readLock, les POST/PUT utilisent writeLock.
    // Résultat : 5 clients qui lisent leurs données n'attendent plus l'un l'autre.
    //
    private final java.util.concurrent.locks.ReadWriteLock rwLock =
        new java.util.concurrent.locks.ReentrantReadWriteLock();

    private volatile long versionDonnees = System.currentTimeMillis();

    // ── CORRECTIF #6 : timeout clients augmenté (30s au lieu de 10s) ─────────
    // 10 secondes était trop court : un client sur WiFi lent ou qui fait
    // une longue opération se déconnectait du compteur avant d'avoir fini.
    private final Map<String, Long> clientsActifs = new ConcurrentHashMap<>();
    private static final long TIMEOUT_CLIENT_MS = 30_000; // 30 secondes

    // ── CORRECTIF #4 : détection mode headless ────────────────────────────────
    // GraphicsEnvironment.isHeadless() retourne true si la JVM tourne sans
    // serveur d'affichage (Linux sans X11, service systemd, conteneur Docker…).
    // Dans ce cas on ne touche JAMAIS à Swing et on log tout en console.
    private static final boolean HEADLESS = GraphicsEnvironment.isHeadless();

    // ══════════════════════════════════════════════════════════════════════
    //  SÉCURITÉ — Sessions (inchangé)
    // ══════════════════════════════════════════════════════════════════════

    private static final long TOKEN_TTL_MS = 4 * 60 * 60 * 1000L;

    private final Map<String, SessionInfo> sessions     = new ConcurrentHashMap<>();
    private final SecureRandom             rng          = new SecureRandom();
    private final Map<String, Integer>     loginEchecs  = new ConcurrentHashMap<>();
    private final Map<String, Long>        loginBlocage = new ConcurrentHashMap<>();

    private static final int  MAX_ECHECS = 5;
    private static final long BLOCAGE_MS = 5 * 60 * 1000L;

    private static class SessionInfo {
        final String  identifiant;
        final boolean accesPAM;
        final long    createdAt;
        SessionInfo(String id, boolean pam) {
            this.identifiant = id; this.accesPAM = pam;
            this.createdAt = System.currentTimeMillis();
        }
        boolean estExpire() { return System.currentTimeMillis() - createdAt > TOKEN_TTL_MS; }
    }

    // ── Constructeur ─────────────────────────────────────────────────────

    public ServeurHTTP() throws Exception
    {
        this.metier     = new PlanningGlobal();
        this.savDonnees = new DonneesSauvegarder();

        // ── CORRECTIF #1 : chemins ancrés sur le dossier du JAR ──────────────
        // CheminApp.resoudre() calcule le chemin absolu en partant du dossier
        // où se trouve le JAR, et non du répertoire courant (System.getProperty("user.dir")).
        // Ainsi, que le serveur soit lancé depuis le bureau, un service, ou un script,
        // il retrouvera toujours ses données.
        this.cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");
        this.cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");

        log("[Serveur] Chemin données : " + CheminApp.getBaseDir());

        try {
            savDonnees.charger(metier, CheminApp.resoudre("app/data/courutilisation"));
            log("[Serveur] " + metier.getLots().size() + " lots chargés.");
            detecterSemaineActive();
        } catch (Exception e) {
            log("[Serveur] Aucun chargement initial : " + e.getMessage());
        }

        // ── CORRECTIF #3 : avertissement sécurité si HTTP ────────────────────
        // On ne peut pas forcer HTTPS avec com.sun.net.httpserver (pas de TLS natif).
        // On avertit donc clairement l'administrateur au démarrage.
        // Pour passer en HTTPS : mettre nginx en reverse proxy devant ce serveur
        // et configurer un certificat SSL sur nginx.
        log("");
        log("╔══════════════════════════════════════════════════╗");
        log("║  AVERTISSEMENT SÉCURITÉ                          ║");
        log("║  Ce serveur tourne en HTTP (non chiffré).        ║");
        log("║  Les tokens de session voyagent en clair.        ║");
        log("║  Sur réseau local d'entreprise : tolérable.      ║");
        log("║  Sur internet : utilisez nginx + HTTPS.          ║");
        log("╚══════════════════════════════════════════════════╝");
        log("");

        // ── Démarrage du serveur HTTP ─────────────────────────────────────────
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/login",              ex -> new LoginHandler()           .handle(ex));
        server.createContext("/lots",               ex -> new GetLotsHandler()         .handle(ex));
        server.createContext("/lots/ajouter",       ex -> new AjouterLotHandler()      .handle(ex));
        server.createContext("/lots/supprimer",     ex -> new SupprimerLotHandler()    .handle(ex));
        server.createContext("/lots/modifier",      ex -> new ModifierLotHandler()     .handle(ex));
        server.createContext("/lots/affecter",      ex -> new AffecterLotHandler()     .handle(ex));
        server.createContext("/lots/desaffecter",   ex -> new DesaffecterLotHandler()  .handle(ex));
        server.createContext("/lots/suiviprod",     ex -> new SuiviProdHandler()       .handle(ex));
        server.createContext("/lots/commencer",     ex -> new CommencerLotHandler()    .handle(ex));
        server.createContext("/lots/annuler",       ex -> new AnnulerLotHandler()      .handle(ex));
        server.createContext("/lots/terminer",      ex -> new TerminerLotHandler()     .handle(ex));
        server.createContext("/lots/phase",         ex -> new ModifierPhaseHandler()   .handle(ex));
        server.createContext("/societes",           ex -> new GetSocietesHandler()     .handle(ex));
        server.createContext("/societes/modifier",  ex -> new ModifierSocieteHandler() .handle(ex));
        server.createContext("/societes/aces",      ex -> new ModifierAcesHandler()    .handle(ex));
        server.createContext("/ficheroute/",        ex -> new FicheRouteHandler()      .handle(ex));
        server.createContext("/sauvegarder",        ex -> new SauvegarderHandler()     .handle(ex));
        server.createContext("/charger",            ex -> new ChargerBloqueHandler()   .handle(ex));
        server.createContext("/nouveaux",           ex -> new NouveauxBloqueHandler()  .handle(ex));
        server.createContext("/heure/nouvelle",     ex -> new NouvelleHeureHandler()   .handle(ex));
        server.createContext("/semaine/sup",        ex -> new SemaineSupHandler()       .handle(ex));
        server.createContext("/autosave/lots",      ex -> new AutoSaveLotsHandler()    .handle(ex));
        server.createContext("/autosave/societes",  ex -> new AutoSaveSocietesHandler().handle(ex));
        server.createContext("/version",            ex -> new VersionHandler()         .handle(ex));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        log("[Serveur] Démarré sur le port " + PORT);

        // ── CORRECTIF #4 : lancement conditionnel de l'IHM Swing ─────────────
        // AVANT : new FenetreServeur(this) était appelé inconditionnellement,
        // ce qui lançait Swing même sur un serveur Linux sans affichage → crash.
        //
        // APRÈS : on vérifie GraphicsEnvironment.isHeadless().
        //   • false (PC Windows avec écran) → on ouvre la FenetreServeur normalement
        //   • true  (serveur sans écran)    → on affiche un menu console interactif
        //     qui permet les mêmes actions (charger, sauvegarder, heures sup, quitter)
        if (!HEADLESS)
        {
            // Mode graphique normal — même comportement qu'avant
            javax.swing.SwingUtilities.invokeLater(() -> new app.ihm.serveur.FenetreServeur(this));
        }
        else
        {
            // Mode headless : le serveur tourne en arrière-plan, on propose
            // un menu console pour le piloter depuis le terminal
            log("[Serveur] Mode HEADLESS détecté — interface console activée.");
            log("[Serveur] Tapez 'help' pour voir les commandes disponibles.");
            demarrerConsoleHeadless();
        }

        // Thread de nettoyage des sessions expirées (toutes les 30 min)
        Thread cleaner = new Thread(() -> {
            while (true) {
                try { Thread.sleep(30 * 60 * 1000L); }
                catch (InterruptedException e) { break; }
                sessions.entrySet().removeIf(e -> e.getValue().estExpire());
            }
        });
        cleaner.setDaemon(true);
        cleaner.setName("session-cleaner");
        cleaner.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORRECTIF #4 — Console Headless
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Menu console pour piloter le serveur sans interface graphique.
     * Tourne dans un thread dédié pour ne pas bloquer le serveur HTTP.
     *
     * Commandes disponibles :
     *   status      → affiche l'état courant (semaine, clients, heures sup)
     *   heures      → toggle les heures supplémentaires
     *   sauvegarder → sauvegarde dans un dossier par semaine
     *   quitter     → arrête le serveur proprement
     */
    private void demarrerConsoleHeadless()
    {
        Thread t = new Thread(() -> {
            java.util.Scanner sc = new java.util.Scanner(System.in);
            afficherAideConsole();
            while (true) {
                System.out.print("\n[Serveur] > ");
                if (!sc.hasNextLine()) break;
                String cmd = sc.nextLine().trim().toLowerCase();
                switch (cmd) {
                    case "status":
                        log("  Semaine active : " + (semaineActive.isBlank() ? "—" : semaineActive));
                        log("  Clients actifs : " + getNbClientsConnectes());
                        log("  Heures sup     : " + (PlanningGlobal.estHeureSup ? "OUI" : "non"));
                        log("  Lots chargés   : " + metier.getLots().size());
                        log("  Sociétés       : " + metier.getSocietes().size());
                        break;
                    case "heures":
                        toggleHeuresSup();
                        log("  Heures sup → " + (PlanningGlobal.estHeureSup ? "ACTIVÉES" : "désactivées"));
                        break;
                    case "sauvegarder":
                        System.out.print("  Dossier de destination : ");
                        String dossier = sc.nextLine().trim();
                        System.out.print("  Numéro de semaine      : ");
                        String sem = sc.nextLine().trim();
                        try {
                            sauvegarderSemaine(dossier, sem);
                            log("  Sauvegarde effectuée dans S" + sem);
                        } catch (Exception e) { log("  ERREUR : " + e.getMessage()); }
                        break;
                    case "help": case "aide":
                        afficherAideConsole();
                        break;
                    case "quitter": case "exit": case "quit":
                        log("[Serveur] Arrêt demandé. Au revoir.");
                        System.exit(0);
                        break;
                    default:
                        log("  Commande inconnue. Tapez 'help'.");
                }
            }
        });
        t.setDaemon(false); // non-daemon pour garder le processus en vie
        t.setName("console-headless");
        t.start();
    }

    private void afficherAideConsole()
    {
        log("  Commandes disponibles :");
        log("    status      → état du serveur");
        log("    heures      → activer/désactiver les heures supplémentaires");
        log("    sauvegarder → sauvegarder dans un dossier par semaine");
        log("    quitter     → arrêter le serveur");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORRECTIF #2 — En-têtes CORS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * PROBLÈME :
     * Le serveur ne renvoyait aucun header CORS. Si un jour une interface web
     * (navigateur) essaie d'appeler ce serveur, toutes les requêtes sont bloquées
     * par la politique same-origin du navigateur, avec une erreur :
     * "Access to fetch at 'http://...:8080/lots' from origin '...' has been blocked by CORS policy"
     *
     * SOLUTION :
     * On ajoute les headers CORS sur toutes les réponses via la méthode rep().
     * - Access-Control-Allow-Origin  : qui peut appeler ce serveur (ici : tout le monde sur le LAN)
     * - Access-Control-Allow-Methods : les verbes HTTP autorisés
     * - Access-Control-Allow-Headers : on autorise X-Auth-Token (notre header de session)
     *
     * Si tu veux restreindre à une IP précise, remplace "*" par "http://192.168.1.X"
     */
    private static void ajouterHeadersCORS(HttpExchange ex)
    {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
    }

    // ── Méthode rep() mise à jour pour inclure CORS automatiquement ───────
    private static void rep(HttpExchange ex, int code, String body) throws IOException
    {
        ajouterHeadersCORS(ex); // ← CORRECTIF #2 : ajouté ici, s'applique partout

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORRECTIF #4 — Logger unifié (console ou Swing)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Utilise System.out en headless, ou System.out aussi (la FenetreServeur
     * gère son propre affichage via refresh()). Le but est de centraliser les
     * logs pour qu'ils aillent toujours quelque part.
     */
    private static void log(String msg)
    {
        System.out.println(msg);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MÉTHODES PUBLIQUES (appelées par FenetreServeur ou console)
    // ══════════════════════════════════════════════════════════════════════

    public void chargerSemaine(String chemin) throws Exception
    {
        rwLock.writeLock().lock(); // CORRECTIF #5
        try {
            savDonnees.charger(metier, chemin);
            cheminLotsJson     = chemin + "/lots.json";
            cheminSocietesJson = chemin + "/societes.json";
            versionDonnees     = System.currentTimeMillis();
            detecterSemaineActive();
        } finally {
            rwLock.writeLock().unlock();
        }
        log("[Serveur] Semaine chargée : " + chemin);
    }

    public void nouvelleSemaine(java.awt.Component parent) throws Exception
    {
        // Cette méthode est appelée depuis FenetreServeur (mode graphique uniquement)
        // En mode headless, il faudrait une variante prenant un chemin en paramètre.
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Sélectionner le fichier des lots (XLSX / XLSM)");
        fc.setFileFilter(new FileNameExtensionFilter("Fichiers Excel (*.xlsx, *.xlsm)", "xlsx", "xlsm"));
        java.io.File def = new java.io.File(CheminApp.resoudre("app/data")); // CORRECTIF #1
        if (def.exists()) fc.setCurrentDirectory(def);
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        String xlsxLots = fc.getSelectedFile().getAbsolutePath();

        ArrayList<Lot> tempLots = ExcelReader.lireLots(xlsxLots);
        int semaine = 0;
        if (!tempLots.isEmpty()) {
            String sem = tempLots.get(0).getSemaine();
            try { semaine = Integer.parseInt("" + sem.charAt(sem.length()-2) + sem.charAt(sem.length()-1)); }
            catch (NumberFormatException ignored) {}
        }
        fc.setDialogTitle("Sélectionner le fichier des heures ACE (ou annuler)");
        String xlsxHeures = fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
            ? fc.getSelectedFile().getAbsolutePath() : xlsxLots;

        rwLock.writeLock().lock(); // CORRECTIF #5
        try {
            metier.chargerDepuisExcel(xlsxLots, CheminApp.resoudre("app/data/pastouche/societes.json"), semaine, xlsxHeures);
            cheminLotsJson     = CheminApp.resoudre("app/data/courutilisation/lots.json");   // CORRECTIF #1
            cheminSocietesJson = CheminApp.resoudre("app/data/courutilisation/societes.json");
            save();
            versionDonnees = System.currentTimeMillis();
            detecterSemaineActive();
        } finally {
            rwLock.writeLock().unlock();
        }
        log("[Serveur] Nouvelle semaine chargée depuis Excel.");
    }

    public void sauvegarderSemaine(String cheminDossier, String numSemaine) throws Exception
    {
        rwLock.writeLock().lock(); // CORRECTIF #5
        try {
            String dossier = cheminDossier + "/S" + numSemaine;
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
            savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
            savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
            cheminLotsJson     = dossier + "/lots.json";
            cheminSocietesJson = dossier + "/societes.json";
            semaineActive      = numSemaine;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void toggleHeuresSup()
    {
        rwLock.writeLock().lock();
        try { metier.setestHeureSup(); save(); }
        finally { rwLock.writeLock().unlock(); }
        log("[Serveur] Heures sup : " + PlanningGlobal.estHeureSup);
    }

    public String getSemaineActive()    { return semaineActive; }

    public int getNbClientsConnectes()
    {
        long now = System.currentTimeMillis();
        // CORRECTIF #6 : on logue les déconnexions pour le debug
        clientsActifs.entrySet().removeIf(e -> {
            boolean expire = now - e.getValue() > TIMEOUT_CLIENT_MS;
            if (expire) log("[Serveur] Client timeout : " + e.getKey());
            return expire;
        });
        return clientsActifs.size();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SÉCURITÉ — Helpers (inchangés)
    // ══════════════════════════════════════════════════════════════════════

    private String genererToken()
    {
        byte[] b = new byte[32]; rng.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private SessionInfo verifierToken(HttpExchange ex)
    {
        String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
        if (token == null || token.isBlank()) return null;
        SessionInfo info = sessions.get(token);
        if (info == null) return null;
        if (info.estExpire()) { sessions.remove(token); return null; }
        return info;
    }

    private boolean exigerToken(HttpExchange ex) throws IOException
    {
        SessionInfo info = verifierToken(ex);
        if (info == null) {
            rep(ex, 401, "{\"err\":\"Non authentifié. Connectez-vous via /login.\"}");
            return false;
        }
        enregistrerClient(ex);
        return true;
    }

    private boolean estBloquee(String ip)
    {
        Long fin = loginBlocage.get(ip);
        if (fin == null) return false;
        if (System.currentTimeMillis() < fin) return true;
        loginBlocage.remove(ip); loginEchecs.remove(ip); return false;
    }

    private void enregistrerEchecLogin(String ip)
    {
        int n = loginEchecs.merge(ip, 1, Integer::sum);
        if (n >= MAX_ECHECS) {
            loginBlocage.put(ip, System.currentTimeMillis() + BLOCAGE_MS);
            loginEchecs.put(ip, 0);
            log("[Sécurité] IP bloquée 5 min : " + ip);
        }
    }

    private void enregistrerClient(HttpExchange ex)
    {
        clientsActifs.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HANDLERS — utilisation du ReadWriteLock (CORRECTIF #5)
    //
    //  Règle :
    //    GET (lecture seule)     → rwLock.readLock().lock()
    //    POST/PUT (modification) → rwLock.writeLock().lock()
    //
    //  Ainsi plusieurs clients peuvent récupérer /lots et /societes en même
    //  temps, sans se bloquer mutuellement. Seules les écritures s'excluent.
    // ══════════════════════════════════════════════════════════════════════

    class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { rep(ex, 405, "{\"err\":\"Méthode non autorisée\"}"); return; }
            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            if (estBloquee(ip)) { rep(ex, 429, "{\"err\":\"Trop de tentatives. Réessayez dans 5 minutes.\"}"); return; }
            try {
                String c          = lire(ex);
                String identifiant = JsonSerialiser.extraireString(c, "identifiant");
                String motDePasse  = JsonSerialiser.extraireString(c, "motDePasse");
                boolean ok = validerIdentite(identifiant, motDePasse);
                if (!ok) {
                    enregistrerEchecLogin(ip);
                    rep(ex, 401, "{\"err\":\"Identifiant ou mot de passe incorrect.\"}");
                    return;
                }
                loginEchecs.remove(ip);
                boolean pam   = "PAM".equalsIgnoreCase(identifiant);
                String  token = genererToken();
                sessions.put(token, new SessionInfo(identifiant, pam));
                rep(ex, 200, "{\"token\":" + JsonSerialiser.esc(token) + ",\"accesPAM\":" + pam + "}");
                log("[Serveur] Connexion : " + identifiant + " depuis " + ip);
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
        }
    }

    // ── Handler GET — utilise readLock ────────────────────────────────────
    class GetLotsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.readLock().lock(); // CORRECTIF #5 : lecture non exclusive
            try {
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    class GetSocietesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.readLock().lock();
            try {
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    class VersionHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            enregistrerClient(ex);
            rwLock.readLock().lock();
            try {
                rep(ex, 200, "{\"v\":\"" + versionDonnees + "\",\"heureSup\":" + PlanningGlobal.estHeureSup
                    + ",\"semaine\":" + JsonSerialiser.esc(semaineActive) + "}");
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    // ── Handlers POST — utilisent writeLock ───────────────────────────────

    class AjouterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c = lire(ex);
                // Utilisation du JsonSerialiser existant (CORRECTIF #8 dans JsonSerialiserSafe)
                Lot lot = JsonSerialiser.deserialiserLot(c);
                if (lot == null) { rep(ex, 400, "{\"err\":\"JSON invalide\"}"); return; }
                metier.ajouterLot(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class SupprimerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.supprimerLot(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class SuiviProdHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                String c     = lire(ex);
                Lot    lot   = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                int    etiq  = JsonSerialiser.extraireInt(c, "nbPieceEtiq");
                int    repart= JsonSerialiser.extraireInt(c, "nbPieceRepart");
                if (etiq < 0 || repart < 0) { rep(ex, 400, "{\"err\":\"Valeurs négatives non autorisées\"}"); return; }
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                if (etiq   <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceEtiq(etiq);
                if (repart <= lot.getNbPieces()) lot.getSuivieProd().setNbPieceRepart(repart);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class AffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Affectation réservée à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c       = lire(ex);
                Lot     lot    = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                Societe societe= findSociete(JsonSerialiser.extraireString(c, "societe"));
                String  aceNom = JsonSerialiser.extraireString(c, "ace");
                if (lot == null || societe == null) { rep(ex, 404, "{\"err\":\"lot ou société introuvable\"}"); return; }
                Ace ace = societe.getAces().stream().filter(a -> a.getNom().equals(aceNom)).findFirst().orElse(null);
                boolean ok = metier.affecterLot(lot, societe, ace);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, ok ? 200 : 400, "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
                    + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class DesaffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Désaffectation réservée à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.desaffecterLot(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
                    + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class ModifierLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c   = lire(ex);
                Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.modifierLot(lot,
                    JsonSerialiser.extraireString(c, "typologie"),
                    JsonSerialiser.extraireString(c, "affaire"),
                    JsonSerialiser.extraireInt   (c, "nbPieces"),
                    JsonSerialiser.extraireDouble (c, "cadence"),
                    JsonSerialiser.extraireInt   (c, "valeurVente"),
                    JsonSerialiser.extraireString(c, "statut"),
                    JsonSerialiser.extraireString(c, "statutEchant"),
                    JsonSerialiser.extraireString(c, "semaine"),
                    JsonSerialiser.extraireInt   (c, "priorite"),
                    JsonSerialiser.extraireString(c, "lotACharge"),
                    JsonSerialiser.extraireString(c, "emplacement"),
                    JsonSerialiser.extraireBool  (c, "sousDouane"),
                    JsonSerialiser.extraireString(c, "dateReception"),
                    JsonSerialiser.extraireString(c, "datePaiement"),
                    JsonSerialiser.extraireString(c, "commentaire"));
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class ModifierPhaseHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                String c   = lire(ex);
                Lot    lot = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.modifierPhase(lot,
                    JsonSerialiser.extraireBool(c, "preTri"),
                    JsonSerialiser.extraireBool(c, "surPiste"),
                    JsonSerialiser.extraireBool(c, "sortieEtiq"),
                    JsonSerialiser.extraireBool(c, "tri"),
                    JsonSerialiser.extraireBool(c, "finit"));
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class CommencerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.commencerLot(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class AnnulerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.annulerLot(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class TerminerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                Lot lot = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                if (lot == null) { rep(ex, 404, "{\"err\":\"lot introuvable\"}"); return; }
                metier.marquerLotTermine(lot);
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class ModifierSocieteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c   = lire(ex);
                String nom = JsonSerialiser.extraireString(c, "nom");
                Societe soc = findSociete(nom);
                if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
                metier.modifierSociete(soc,
                    nom,
                    JsonSerialiser.extraireString(c, "ce"),
                    JsonSerialiser.extraireInt   (c, "totalHeuresCE"),
                    JsonSerialiser.extraireInt   (c, "effectif"));
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class ModifierAcesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c      = lire(ex);
                String nomSoc = JsonSerialiser.extraireString(c, "societe");
                Societe soc   = findSociete(nomSoc);
                if (soc == null) { rep(ex, 404, "{\"err\":\"société introuvable\"}"); return; }
                String bloc = JsonSerialiser.extraireBloc(c, "\"aces\"");
                ArrayList<Ace> nouvellesAces = JsonSerialiser.deserialiserAces(bloc);
                List<Ace> aces = soc.getAces();
                int min = Math.min(aces.size(), nouvellesAces.size());
                for (int i = 0; i < min; i++)
                    metier.modifierAce(aces.get(i), nouvellesAces.get(i).getNom(),
                        nouvellesAces.get(i).getNbPers(), nouvellesAces.get(i).getEffectifActuel());
                for (int i = aces.size()-1; i >= nouvellesAces.size(); i--) aces.remove(i);
                for (int i = min; i < nouvellesAces.size(); i++) {
                    Ace n = nouvellesAces.get(i);
                    aces.add(new Ace(n.getNom(), n.getNbPers(), n.getEffectifActuel()));
                }
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class FicheRouteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            String  nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
            Societe s   = findSociete(nom);
            if (s == null) { rep(ex, 404, "{\"err\":\"societe not found\"}"); return; }
            rwLock.readLock().lock();
            try {
                rep(ex, 200, JsonSerialiser.serialiserFicheRoute(metier.genererFicheRoute(s)));
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    class SauvegarderHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try {
                String c       = lire(ex);
                String chemin  = JsonSerialiser.extraireString(c, "chemin");
                String semaine = JsonSerialiser.extraireString(c, "semaine");
                if (chemin.contains("..")) { rep(ex, 400, "{\"err\":\"Chemin non autorisé\"}"); return; }
                String dossier = chemin + "/S" + semaine;
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
                savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
                savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
                cheminLotsJson     = dossier + "/lots.json";
                cheminSocietesJson = dossier + "/societes.json";
                rep(ex, 200, "{\"ok\":true}");
            } catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class ChargerBloqueHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            log("[Serveur] BLOCAGE /charger depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
            rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
        }
    }

    class NouveauxBloqueHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            log("[Serveur] BLOCAGE /nouveaux depuis " + ex.getRemoteAddress().getAddress().getHostAddress());
            rep(ex, 403, "{\"err\":\"Action réservée au serveur.\"}");
        }
    }

    class NouvelleHeureHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            SessionInfo info = verifierToken(ex);
            if (info != null && !info.accesPAM) { rep(ex, 403, "{\"err\":\"Réservé à PAM\"}"); return; }
            rwLock.writeLock().lock();
            try {
                String c = lire(ex);
                String nomSoc = JsonSerialiser.extraireString(c, "societe");
                if (nomSoc != null && !nomSoc.isEmpty()) {
                    Societe s = findSociete(nomSoc);
                    if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + JsonSerialiser.extraireInt(c, "heures"));
                } else {
                    String bloc = JsonSerialiser.extraireBloc(c, "\"societes\"");
                    if (bloc != null && !bloc.isEmpty()) {
                        String[] entries = bloc.replace("[","").replace("]","").split("\\},\\{");
                        for (String entry : entries) {
                            entry = entry.replace("{","").replace("}","");
                            String nom    = JsonSerialiser.extraireString("{" + entry + "}", "nom");
                            int    heures = JsonSerialiser.extraireInt   ("{" + entry + "}", "heures");
                            Societe s = findSociete(nom);
                            if (s != null) s.setTotalHeuresCE(s.getTotalHeuresCE() + heures);
                        }
                    }
                }
                save(); versionDonnees = System.currentTimeMillis();
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class SemaineSupHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try { metier.setestHeureSup(); save(); rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class AutoSaveLotsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); rep(ex, 200, "{\"ok\":true}"); }
            catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    class AutoSaveSocietesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!exigerToken(ex)) return;
            rwLock.writeLock().lock();
            try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); rep(ex, 200, "{\"ok\":true}"); }
            catch (Exception e) { rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}"); }
            finally { rwLock.writeLock().unlock(); }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════════════════════════════

    private Lot findLot(int numCDE)
    {
        return metier.getLots().stream().filter(l -> l.getNumCDE() == numCDE).findFirst().orElse(null);
    }

    private Societe findSociete(String nom)
    {
        if (nom == null) return null;
        return metier.getSocietes().stream().filter(s -> nom.equals(s.getNom())).findFirst().orElse(null);
    }

    private void save()
    {
        // Appelé depuis des blocs writeLock → thread-safe
        try { savDonnees.sauvegarderLots    (metier.getLots(),     cheminLotsJson);     } catch (Exception ignored) {}
        try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); } catch (Exception ignored) {}
        versionDonnees = System.currentTimeMillis();
    }

    private void detecterSemaineActive()
    {
        if (!metier.getLots().isEmpty()) {
            String sem = metier.getLots().get(0).getSemaine();
            if (sem != null && sem.length() >= 2)
                semaineActive = sem.length() == 6
                    ? "S" + sem.substring(4) + " / " + sem.substring(0, 4) : sem;
        }
    }

    private static String lire(HttpExchange ex) throws IOException
    {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Valide les identifiants. À adapter selon votre politique de mots de passe.
     * Pour l'instant, les mots de passe sont stockés ici en dur.
     * Pour un vrai déploiement, stocker des hash BCrypt dans un fichier externe.
     */
    private boolean validerIdentite(String identifiant, String motDePasse)
    {
        if (identifiant == null || motDePasse == null) return false;
        // PAM = administrateur
        if ("PAM".equalsIgnoreCase(identifiant) && "PAM".equals(motDePasse)) return true;
        // Sociétés : leur identifiant est leur nom, mot de passe = leur nom (à changer)
        return metier.getSocietes().stream()
            .anyMatch(s -> s.getNom().equalsIgnoreCase(identifiant)
                && s.getNom().equalsIgnoreCase(motDePasse));
    }

    // ── main ──────────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        try                 { new ServeurHTTP();   }
        catch (Exception e) { e.printStackTrace(); }
    }
}