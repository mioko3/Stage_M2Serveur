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

public class ServeurHTTP
{
    private PlanningGlobal metier;
    private DonneesSauvegarder savDonnees;

    private String cheminLotsJson;
    private String cheminSocietesJson;

    private static final int PORT = 8080;

    private final Object verrou = new Object();

    public ServeurHTTP() throws Exception
    {
        this.metier = new PlanningGlobal();
        this.savDonnees = new DonneesSauvegarder();

        this.cheminLotsJson = "app/data/courutilisation/lots.json";
        this.cheminSocietesJson = "app/data/courutilisation/societes.json";

        try
        {
            savDonnees.charger(metier, "app/data/courutilisation");
        }
        catch (Exception e)
        {
            System.out.println("[Serveur] Aucun chargement initial.");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // LOTS
        server.createContext("/lots", ex -> new GetLotsHandler().handle(ex));
        server.createContext("/lots/ajouter", ex -> new AjouterLotHandler().handle(ex));
        server.createContext("/lots/supprimer", ex -> new SupprimerLotHandler().handle(ex));
        server.createContext("/lots/modifier", ex -> new ModifierLotHandler().handle(ex));
        server.createContext("/lots/affecter", ex -> new AffecterLotHandler().handle(ex));
        server.createContext("/lots/desaffecter", ex -> new DesaffecterLotHandler().handle(ex));
        server.createContext("/lots/suiviprod", ex -> new SuiviProdHandler().handle(ex));
        server.createContext("/lots/commencer", ex -> new CommencerLotHandler().handle(ex));
        server.createContext("/lots/annuler", ex -> new AnnulerLotHandler().handle(ex));
        server.createContext("/lots/terminer", ex -> new TerminerLotHandler().handle(ex));
        server.createContext("/lots/phase", ex -> new ModifierPhaseHandler().handle(ex));

        // SOCIETES
        server.createContext("/societes", ex -> new GetSocietesHandler().handle(ex));
        server.createContext("/societes/modifier", ex -> new ModifierSocieteHandler().handle(ex));
        server.createContext("/aces/modifier", ex -> new ModifierAceHandler().handle(ex));

        // ROUTES SYSTEME
        server.createContext("/ficheroute/", ex -> new FicheRouteHandler().handle(ex));
        server.createContext("/sauvegarder", ex -> new SauvegarderHandler().handle(ex));
        server.createContext("/charger", ex -> new ChargerHandler().handle(ex));
        server.createContext("/nouveaux", ex -> new NouveauxHandler().handle(ex));
        server.createContext("/nouvelleheure", ex -> new NouvelleHeureHandler().handle(ex));

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("[Serveur] Démarré sur port " + PORT);
    }

    // ================= UTILS =================

    private String lire(HttpExchange ex) throws IOException
    {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void rep(HttpExchange ex, int code, String body) throws IOException
    {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private Lot findLot(int id)
    {
        for (Lot l : metier.getLots())
            if (l.getNumCDE() == id) return l;
        return null;
    }

    private Societe findSociete(String nom)
    {
        for (Societe s : metier.getSocietes())
            if (s.getNom().equals(nom)) return s;
        return null;
    }

    private void save()
    {
        try
        {
            savDonnees.sauvegarderLots(metier.getLots(), cheminLotsJson);
            savDonnees.sauvegarderSocietes(metier.getSocietes(), metier.getLots(), cheminSocietesJson);
        }
        catch (Exception e)
        {
            System.err.println("[Serveur] Save error: " + e.getMessage());
        }
    }

    // ================= HANDLERS =================

    class GetLotsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
        }
    }

    class GetSocietesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
        }
    }

    class AjouterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                Lot l = JsonSerialiser.deserialiserLot(lire(ex));
                metier.ajouterLot(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class SupprimerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                int id = JsonSerialiser.extraireInt(lire(ex), "numCDE");
                Lot l = findLot(id);
                if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }
                metier.supprimerLot(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class ModifierLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                String c = lire(ex);
                Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                if (l == null) { rep(ex, 404, "{\"err\":\"not found\"}"); return; }

                metier.modifierLot(l,
                        JsonSerialiser.extraireString(c, "typologie"),
                        JsonSerialiser.extraireString(c, "affaire"),
                        JsonSerialiser.extraireInt(c, "nbPieces"),
                        JsonSerialiser.extraireDouble(c, "cadence"),
                        JsonSerialiser.extraireInt(c, "valeurVente"),
                        JsonSerialiser.extraireString(c, "statut"),
                        JsonSerialiser.extraireString(c, "statutEchant"),
                        JsonSerialiser.extraireString(c, "semaine"),
                        JsonSerialiser.extraireInt(c, "priorite"),
                        JsonSerialiser.extraireString(c, "lotACharge"),
                        JsonSerialiser.extraireString(c, "emplacement"),
                        JsonSerialiser.extraireBool(c, "estSousDouane"),
                        JsonSerialiser.extraireString(c, "dateReception"),
                        JsonSerialiser.extraireString(c, "datePaiement"),
                        JsonSerialiser.extraireString(c, "commentaire")
                );

                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class AffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                String c = lire(ex);

                Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));
                Societe s = findSociete(JsonSerialiser.extraireString(c, "nomSociete"));
                Ace a = s.getAce(JsonSerialiser.extraireString(c, "nomAce"));

                boolean ok = metier.affecterLot(l, s, a);
                if (!ok) { rep(ex, 409, "{\"err\":\"heures\"}"); return; }

                save();
                rep(ex, 200,
                        "{\"lots\":" + JsonSerialiser.serialiserLots(metier.getLots())
                        + ",\"societes\":" + JsonSerialiser.serialiserSocietes(metier.getSocietes()) + "}");
            }
        }
    }

    class DesaffecterLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                metier.desaffecterLot(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class SuiviProdHandler implements HttpHandler
    {
        public void handle(HttpExchange ex) throws IOException
        {
            synchronized (verrou)
            {
                try
                {
                    String c = lire(ex);

                    int numCDE = JsonSerialiser.extraireInt(c, "numCDE");
                    int etiq   = JsonSerialiser.extraireInt(c, "nbPieceEtiq");
                    int repart = JsonSerialiser.extraireInt(c, "nbPieceRepart");

                    Lot l = findLot(numCDE);

                    if (l == null)
                    {
                        rep(ex, 404, "{\"err\":\"lot introuvable\"}");
                        return;
                    }

                    l.getSuivieProd().setNbPieceEtiq(etiq);
                    l.getSuivieProd().setNbPieceRepart(repart);

                    save();

                    rep(ex, 200,
                        JsonSerialiser.serialiserLots(metier.getLots()));
                }
                catch (Exception e)
                {
                    rep(ex, 400, "{\"err\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    class CommencerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                metier.commencerLot(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class AnnulerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                metier.annulerLot(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class TerminerLotHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                Lot l = findLot(JsonSerialiser.extraireInt(lire(ex), "numCDE"));
                metier.marquerLotTermine(l);
                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class ModifierPhaseHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                String c = lire(ex);
                Lot l = findLot(JsonSerialiser.extraireInt(c, "numCDE"));

                metier.modifierPhase(l,
                        JsonSerialiser.extraireBool(c, "preTri"),
                        JsonSerialiser.extraireBool(c, "surPiste"),
                        JsonSerialiser.extraireBool(c, "sortieEtiq"),
                        JsonSerialiser.extraireBool(c, "tri"),
                        JsonSerialiser.extraireBool(c, "finit"));

                save();
                rep(ex, 200, JsonSerialiser.serialiserLots(metier.getLots()));
            }
        }
    }

    class ModifierSocieteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                String c = lire(ex);
                Societe s = findSociete(JsonSerialiser.extraireString(c, "nomActuel"));

                metier.modifierSociete(s,
                        JsonSerialiser.extraireString(c, "nom"),
                        JsonSerialiser.extraireString(c, "ce"),
                        JsonSerialiser.extraireInt(c, "totalHeuresCE"),
                        JsonSerialiser.extraireInt(c, "effectif"));

                save();
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            }
        }
    }

    class ModifierAceHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                String c = lire(ex);

                Societe s = findSociete(JsonSerialiser.extraireString(c, "nomSociete"));
                Ace a = s.getAce(JsonSerialiser.extraireString(c, "nomActuelAce"));

                metier.modifierAce(a,
                        JsonSerialiser.extraireString(c, "nom"),
                        JsonSerialiser.extraireInt(c, "nbPers"),
                        JsonSerialiser.extraireInt(c, "effectif"));

                save();
                rep(ex, 200, JsonSerialiser.serialiserSocietes(metier.getSocietes()));
            }
        }
    }

    class FicheRouteHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String nom = ex.getRequestURI().getPath().replace("/ficheroute/", "");
            Societe s = findSociete(nom);

            FicheRoute f = metier.genererFicheRoute(s);
            rep(ex, 200, JsonSerialiser.serialiserFicheRoute(f));
        }
    }

    class SauvegarderHandler implements HttpHandler
    {
        public void handle(HttpExchange ex) throws IOException
        {
            synchronized (verrou)
            {
                try
                {
                    String c = lire(ex);

                    String chemin  = JsonSerialiser.extraireString(c, "chemin");
                    String semaine = JsonSerialiser.extraireString(c, "semaine");

                    String dossier = chemin + "/S" + semaine;

                    java.nio.file.Files.createDirectories(
                        java.nio.file.Paths.get(dossier)
                    );

                    savDonnees.sauvegarderLots(
                        metier.getLots(),
                        dossier + "/lots.json"
                    );

                    savDonnees.sauvegarderSocietes(
                        metier.getSocietes(),
                        metier.getLots(),
                        dossier + "/societes.json"
                    );

                    cheminLotsJson = dossier + "/lots.json";
                    cheminSocietesJson = dossier + "/societes.json";

                    rep(ex, 200, "{\"ok\":true}");
                }
                catch (Exception e)
                {
                    rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    class ChargerHandler implements HttpHandler
    {
        public void handle(HttpExchange ex) throws IOException
        {
            synchronized (verrou)
            {
                try
                {
                    String chemin = JsonSerialiser.extraireString(
                        lire(ex),
                        "chemin"
                    );

                    savDonnees.charger(metier, chemin);

                    cheminLotsJson     = chemin + "/lots.json";
                    cheminSocietesJson = chemin + "/societes.json";

                    rep(ex, 200,
                        "{\"lots\":" +
                            JsonSerialiser.serialiserLots(metier.getLots())
                        + ",\"societes\":" +
                            JsonSerialiser.serialiserSocietes(metier.getSocietes())
                        + "}"
                    );
                }
                catch (Exception e)
                {
                    rep(ex, 500, "{\"err\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    class NouveauxHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            synchronized (verrou)
            {
                metier = new PlanningGlobal();
                save();
                rep(ex, 200, "{\"ok\":true}");
            }
        }
    }

    class NouvelleHeureHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            rep(ex, 200, "{\"err\":\"a déplacer côté client ou Excel upload\"}");
        }
    }

    public static void main(String[] args) throws Exception
    {
        new ServeurHTTP();
    }
}