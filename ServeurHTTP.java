package app;

import app.metier.PlanningGlobal;
import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.JsonSerialiser;
import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * ══════════════════════════════════════════════════════════════
 *  SERVEUR CENTRAL
 *
 *  Lance-le UNE SEULE FOIS sur le PC "serveur" du réseau local.
 *  Tous les clients (autres PC) se connectent à son adresse IP.
 *
 *  Lancement :
 *    run_SERVEUR.bat   (double-clic)
 *    — ou —
 *    java -cp "bin;app/jar/..." app.ServeurHTTP
 *
 *  Le serveur écoute sur le PORT 8080.
 *  Les clients se connectent avec l'IP de ce PC :
 *    ex: http://192.168.1.15:8080
 *
 * ──────────────────────────────────────────────────────────────
 *  ROUTES HTTP DISPONIBLES :
 *
 *  GET  /lots              → liste complète des lots (JSON)
 *  GET  /societes          → liste complète des sociétés (JSON)
 *  GET  /ficheroute/{nom}  → fiche de route d'une société (JSON)
 *  POST /lots/ajouter      → ajouter un lot      (corps = JSON du lot)
 *  POST /lots/supprimer    → supprimer un lot     (corps = {"numCDE":123})
 *  POST /lots/modifier     → modifier un lot      (corps = JSON complet)
 *  POST /lots/affecter     → affecter lot→soc+ace (corps = JSON)
 *  POST /lots/desaffecter  → désaffecter un lot   (corps = {"numCDE":123})
 *  POST /lots/suiviprod    → mettre à jour suivi  (corps = JSON)
 *  POST /societes/modifier → modifier une société (corps = JSON)
 *  POST /aces/modifier     → modifier un ACE      (corps = JSON)
 *  POST /sauvegarder       → sauvegarder données  (corps = JSON)
 *  POST /charger           → charger sauvegarde   (corps = {"chemin":"..."})
 *  POST /nouveaux          → réinitialiser        (corps vide)
 *  POST /nouvelleheure     → nouvelles heures     (corps = {"semaine":17})
 * ══════════════════════════════════════════════════════════════
 */
public class ServeurHTTP
{
    // ── Données métier (UNE seule instance partagée entre tous les clients) ──
    private PlanningGlobal     metier;
    private DonneesSauvegarder savDonnees;
    private String             cheminLotsJson;
    private String             cheminSocietesJson;

    private static final int PORT = 8080;

    public ServeurHTTP() throws Exception
    {
        this.metier             = new PlanningGlobal();
        this.savDonnees         = new DonneesSauvegarder();
        this.cheminLotsJson     = "app/data/courutilisation/lots.json";
        this.cheminSocietesJson = "app/data/courutilisation/societes.json";

        // HttpServer est inclus dans le JDK — aucune librairie externe nécessaire
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/lots/ajouter",      ex -> new AjouterLotHandler()     .handle(ex));
        server.createContext("/lots/supprimer",    ex -> new SupprimerLotHandler()   .handle(ex));
        server.createContext("/lots/modifier",     ex -> new ModifierLotHandler()    .handle(ex));
        server.createContext("/lots/affecter",     ex -> new AffecterLotHandler()    .handle(ex));
        server.createContext("/lots/desaffecter",  ex -> new DesaffecterLotHandler() .handle(ex));
        server.createContext("/lots/suiviprod",    ex -> new SuiviProdHandler()      .handle(ex));
        server.createContext("/lots",              ex -> new GetLotsHandler()        .handle(ex));
        server.createContext("/societes/modifier", ex -> new ModifierSocieteHandler().handle(ex));
        server.createContext("/aces/modifier",     ex -> new ModifierAceHandler()    .handle(ex));
        server.createContext("/societes",          ex -> new GetSocietesHandler()    .handle(ex));
        server.createContext("/ficheroute/",       ex -> new FicheRouteHandler()     .handle(ex));
        server.createContext("/sauvegarder",       ex -> new SauvegarderHandler()    .handle(ex));
        server.createContext("/charger",           ex -> new ChargerHandler()        .handle(ex));
        server.createContext("/nouveaux",          ex -> new NouveauxHandler()       .handle(ex));
        server.createContext("/nouvelleheure",     ex -> new NouvelleHeureHandler()  .handle(ex));

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  SERVEUR PLANNING démarré — port " + PORT + "           ║");
        System.out.println("║  Trouvez votre IP avec : ipconfig (Windows)      ║");
        System.out.println("║  Les clients se connectent sur : IP:" + PORT + "       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("  Ctrl+C pour arrêter.");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITAIRES HTTP
    // ══════════════════════════════════════════════════════════════════════

    private static String lireCorps(HttpExchange ex) throws IOException
    {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void repondre(HttpExchange ex, int code, String corps) throws IOException
    {
        byte[] bytes = corps.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private Societe trouverSociete(String nom)
    {
        for (Societe s : metier.getSocietes())
            if (s.getNom().equals(nom)) return s;
        return null;
    }

    private Lot trouverLot(int numCDE)
    {
        for (Lot l : metier.getLots())
            if (l.getNumCDE() == numCDE) return l;
        return null;
    }

    private void autoSauvegarderLots()
    {
        try { savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson); }
        catch (Exception e) { System.err.println("[Serveur] AutoSave lots : " + e.getMessage()); }
    }

    private void autoSauvegarderSocietes()
    {
        try { savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson); }
        catch (Exception e) { System.err.println("[Serveur] AutoSave sociétés : " + e.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HANDLERS — un par route
    // ══════════════════════════════════════════════════════════════════════

    class GetLotsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try { repondre(ex, 200, JsonSerialiser.serialiserLots(metier.getLots())); }
            catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    class GetSocietesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try { repondre(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes())); }
            catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    class AjouterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                Lot lot = JsonSerialiser.deserialiserLot(lireCorps(ex));
                metier.ajouterLot(lot);
                autoSauvegarderLots();
                repondre(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
                System.out.println("[Serveur] Lot ajouté : #" + lot.getNumCDE());
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class SupprimerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                int numCDE = JsonSerialiser.extraireInt(lireCorps(ex), "numCDE");
                Lot lot = trouverLot(numCDE);
                if (lot == null) { repondre(ex, 404, err("Lot introuvable")); return; }
                metier.supprimerLot(lot);
                autoSauvegarderLots();
                repondre(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class ModifierLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps = lireCorps(ex);
                Lot lot = trouverLot(JsonSerialiser.extraireInt(corps, "numCDE"));
                if (lot == null) { repondre(ex, 404, err("Lot introuvable")); return; }
                metier.modifierLot(lot,
                    JsonSerialiser.extraireString(corps, "typologie"),
                    JsonSerialiser.extraireString(corps, "affaire"),
                    JsonSerialiser.extraireInt   (corps, "nbPieces"),
                    JsonSerialiser.extraireDouble(corps, "cadence"),
                    JsonSerialiser.extraireInt   (corps, "valeurVente"),
                    JsonSerialiser.extraireString(corps, "statut"),
                    JsonSerialiser.extraireString(corps, "statutEchant"),
                    JsonSerialiser.extraireString(corps, "semaine"),
                    JsonSerialiser.extraireInt   (corps, "priorite"),
                    JsonSerialiser.extraireString(corps, "lotACharge"),
                    JsonSerialiser.extraireString(corps, "emplacement"),
                    JsonSerialiser.extraireBool  (corps, "estSousDouane"),
                    JsonSerialiser.extraireString(corps, "dateReception"),
                    JsonSerialiser.extraireString(corps, "datePaiement"),
                    JsonSerialiser.extraireString(corps, "commentaire")
                );
                autoSauvegarderLots();
                repondre(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class AffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps   = lireCorps(ex);
                Lot     lot    = trouverLot(JsonSerialiser.extraireInt(corps, "numCDE"));
                Societe soc    = trouverSociete(JsonSerialiser.extraireString(corps, "nomSociete"));
                if (lot == null || soc == null) { repondre(ex, 404, err("Lot ou société introuvable")); return; }
                Ace ace = soc.getAce(JsonSerialiser.extraireString(corps, "nomAce"));
                if (ace == null) { repondre(ex, 404, err("ACE introuvable")); return; }
                boolean ok = metier.affecterLot(lot, soc, ace);
                if (!ok) { repondre(ex, 409, err("Heures insuffisantes")); return; }
                autoSauvegarderSocietes();
                repondre(ex, 200, "{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
                                + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class DesaffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                Lot lot = trouverLot(JsonSerialiser.extraireInt(lireCorps(ex), "numCDE"));
                if (lot == null) { repondre(ex, 404, err("Lot introuvable")); return; }
                metier.desaffecterLot(lot);
                autoSauvegarderSocietes();
                repondre(ex, 200, "{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
                                + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class SuiviProdHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps = lireCorps(ex);
                Lot lot = trouverLot(JsonSerialiser.extraireInt(corps, "numCDE"));
                if (lot == null) { repondre(ex, 404, err("Lot introuvable")); return; }
                lot.getSuivieProd().setNbPieceEtiq  (JsonSerialiser.extraireInt(corps, "nbPieceEtiq"));
                lot.getSuivieProd().setNbPieceRepart(JsonSerialiser.extraireInt(corps, "nbPieceRepart"));
                autoSauvegarderLots();
                repondre(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class ModifierSocieteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps = lireCorps(ex);
                Societe soc = trouverSociete(JsonSerialiser.extraireString(corps, "nomActuel"));
                if (soc == null) { repondre(ex, 404, err("Société introuvable")); return; }
                metier.modifierSociete(soc,
                    JsonSerialiser.extraireString(corps, "nom"),
                    JsonSerialiser.extraireString(corps, "ce"),
                    JsonSerialiser.extraireInt   (corps, "totalHeuresCE"),
                    JsonSerialiser.extraireInt   (corps, "effectif")
                );
                autoSauvegarderSocietes();
                repondre(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class ModifierAceHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps = lireCorps(ex);
                Societe soc = trouverSociete(JsonSerialiser.extraireString(corps, "nomSociete"));
                if (soc == null) { repondre(ex, 404, err("Société introuvable")); return; }
                Ace ace = soc.getAce(JsonSerialiser.extraireString(corps, "nomActuelAce"));
                if (ace == null) { repondre(ex, 404, err("ACE introuvable")); return; }
                metier.modifierAce(ace,
                    JsonSerialiser.extraireString(corps, "nom"),
                    JsonSerialiser.extraireInt   (corps, "nbPers"),
                    JsonSerialiser.extraireInt   (corps, "effectif")
                );
                autoSauvegarderSocietes();
                repondre(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class FicheRouteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String path = ex.getRequestURI().getPath();
                String nomEnc = path.substring("/ficheroute/".length());
                String nom = java.net.URLDecoder.decode(nomEnc, "UTF-8");
                Societe soc = trouverSociete(nom);
                if (soc == null) { repondre(ex, 404, err("Société introuvable")); return; }
                FicheRoute fdr = metier.genererFicheRoute(soc);
                repondre(ex, 200, JsonSerialiser.serialiserFicheRoute(fdr));
            } catch (Exception e) { repondre(ex, 400, err(e)); }
        }
    }

    class SauvegarderHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String corps   = lireCorps(ex);
                String chemin  = JsonSerialiser.extraireString(corps, "chemin");
                String semaine = JsonSerialiser.extraireString(corps, "semaine");
                String dossier = chemin + "/S" + semaine;
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dossier));
                savDonnees.sauvegarderLots    (metier.getLots(),     dossier + "/lots.json");
                savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), dossier + "/societes.json");
                cheminLotsJson     = dossier + "/lots.json";
                cheminSocietesJson = dossier + "/societes.json";
                repondre(ex, 200, "{\"ok\":true}");
                System.out.println("[Serveur] Données sauvegardées dans : " + dossier);
            } catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    class ChargerHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                String chemin = JsonSerialiser.extraireString(lireCorps(ex), "chemin");
                savDonnees.charger(metier, chemin);
                cheminLotsJson     = chemin + "/lots.json";
                cheminSocietesJson = chemin + "/societes.json";
                repondre(ex, 200, "{\"lots\":"     + JsonSerialiser.serialiserLots(metier.getLots())
                                + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
                System.out.println("[Serveur] Données chargées depuis : " + chemin);
            } catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    class NouveauxHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                metier             = new PlanningGlobal();
                cheminLotsJson     = "app/data/courutilisation/lots.json";
                cheminSocietesJson = "app/data/courutilisation/societes.json";
                autoSauvegarderLots();
                autoSauvegarderSocietes();
                repondre(ex, 200, "{\"ok\":true}");
            } catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    class NouvelleHeureHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            try {
                int semaine = JsonSerialiser.extraireInt(lireCorps(ex), "semaine");
                metier.nouvelleHeurePourSociete(semaine);
                autoSauvegarderSocietes();
                repondre(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            } catch (Exception e) { repondre(ex, 500, err(e)); }
        }
    }

    private static String err(Exception e) { return "{\"erreur\":\"" + e.getMessage().replace("\"","'") + "\"}"; }
    private static String err(String msg)  { return "{\"erreur\":\"" + msg + "\"}"; }

    public static void main(String[] args) throws Exception
    {
        new ServeurHTTP();
        // La JVM reste vivante grâce au pool de threads. Ctrl+C pour arrêter.
    }
}
