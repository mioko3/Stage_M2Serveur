package app;

import app.ihm.FenetrePrincipale;
import app.metier.collecte.JsonSerialiser;
import app.metier.ficheroute.FicheRoute;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONTRÔLEUR CLIENT
 *
 *  Implémente IControleur.
 *  Chaque méthode envoie une requête HTTP au ServeurHTTP
 *  au lieu d'agir localement. L'IHM Swing ne voit aucune différence.
 *
 *  Lancement :
 *    run_CLIENT.bat   (double-clic, demande l'IP du serveur)
 *    — ou —
 *    java -cp "bin;app/jar/..." app.ControleurClient 192.168.1.15
 * ══════════════════════════════════════════════════════════════
 */
public class ControleurClient implements IControleur
{
    private final String     urlServeur;
    private final HttpClient http;

    // Cache local — mis à jour après chaque réponse du serveur
    private ArrayList<Lot>     lots;
    private ArrayList<Societe> societes;

    // ══════════════════════════════════════════════════════════════════════
    //  CONSTRUCTEUR
    // ══════════════════════════════════════════════════════════════════════

    public ControleurClient(String ipServeur)
    {
        this.urlServeur = "http://" + ipServeur + ":8080";
        this.http       = HttpClient.newHttpClient();
        this.lots       = new ArrayList<>();
        this.societes   = new ArrayList<>();

        System.out.println("[Client] Connexion → " + urlServeur);
        chargerDepuisServeur();
        new FenetrePrincipale(this);
    }

    private void chargerDepuisServeur()
    {
        try {
            this.lots     = JsonSerialiser.deserialiserLots(get("/lots"));
            this.societes = JsonSerialiser.deserialiserSocietes(get("/societes"), this.lots);
            System.out.println("[Client] " + lots.size() + " lots, "
                    + societes.size() + " sociétés chargés.");
        } catch (Exception e) {
            System.err.println("[Client] Impossible de contacter le serveur : " + e.getMessage());
            System.err.println("  → Vérifiez que run_SERVEUR.bat tourne sur le PC serveur.");
            System.err.println("  → Vérifiez l'IP : " + urlServeur);
        }
    }

    /**
     * Recharge les données depuis le serveur.
     * Retourne true si les données ont changé (utile pour le timer de sync).
     * Appelé par le bouton ⟳ et automatiquement toutes les 5 secondes.
     */
    public boolean rechargerDepuisServeur()
    {
        try {
            String jsonLots = get("/lots");
            String jsonSoc  = get("/societes");
            ArrayList<Lot>     nouveauxLots      = JsonSerialiser.deserialiserLots(jsonLots);
            ArrayList<Societe> nouvellesSocietes  = JsonSerialiser.deserialiserSocietes(jsonSoc, nouveauxLots);
            // Détection de changement : compare les tailles + la sérialisation du premier lot
            boolean changed =
                nouveauxLots.size() != this.lots.size()
                || nouvellesSocietes.size() != this.societes.size()
                || !JsonSerialiser.serialiserLots(nouveauxLots)
                                  .equals(JsonSerialiser.serialiserLots(this.lots));
            this.lots     = nouveauxLots;
            this.societes = nouvellesSocietes;
            return changed;
        } catch (Exception e) {
            System.err.println("[Client] Erreur sync : " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACCÈS AUX DONNÉES
    // ══════════════════════════════════════════════════════════════════════

    @Override public ArrayList<Lot>     getLots()               { return lots;     }
    @Override public ArrayList<Societe> getSocietes()           { return societes; }
    @Override public String             getCheminLotsJson()     { return "";       }
    @Override public String             getCheminSocietesJson() { return "";       }

    // ══════════════════════════════════════════════════════════════════════
    //  GESTION DES LOTS
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void ajouterLot(Lot lot)
    {
        try {
            this.lots = JsonSerialiser.deserialiserLots(
                post("/lots/ajouter", JsonSerialiser.serialiserLotSeul(lot)));
        } catch (Exception e) { err("ajouterLot", e); }
    }

    @Override
    public void supprimerLot(Lot lot)
    {
        try {
            this.lots = JsonSerialiser.deserialiserLots(
                post("/lots/supprimer", "{\"numCDE\":" + lot.getNumCDE() + "}"));
        } catch (Exception e) { err("supprimerLot", e); }
    }

    @Override
    public void sauvegarderLots() { /* géré automatiquement par le serveur */ }

    @Override
    public void modifierLot(Lot lot, String typologie, String affaire,
                             int nbPieces, double cadence, int valeurVente,
                             String statut, String statutEchant, String semaine,
                             int priorite, String lotACharge, String emplacement,
                             boolean sousDouane, String dateReception,
                             String datePaiement, String commentaire)
    {
        try {
            String corps = "{"
                + "\"numCDE\":"        + lot.getNumCDE()    + ","
                + "\"typologie\":"     + e(typologie)       + ","
                + "\"affaire\":"       + e(affaire)         + ","
                + "\"nbPieces\":"      + nbPieces           + ","
                + "\"cadence\":"       + cadence            + ","
                + "\"valeurVente\":"   + valeurVente        + ","
                + "\"statut\":"        + e(statut)          + ","
                + "\"statutEchant\":"  + e(statutEchant)    + ","
                + "\"semaine\":"       + e(semaine)         + ","
                + "\"priorite\":"      + priorite           + ","
                + "\"lotACharge\":"    + e(lotACharge)      + ","
                + "\"emplacement\":"   + e(emplacement)     + ","
                + "\"estSousDouane\":" + sousDouane         + ","
                + "\"dateReception\":" + e(dateReception)   + ","
                + "\"datePaiement\":"  + e(datePaiement)    + ","
                + "\"commentaire\":"   + e(commentaire)     + "}";
            this.lots = JsonSerialiser.deserialiserLots(post("/lots/modifier", corps));
        } catch (Exception e) { err("modifierLot", e); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AFFECTATION
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean affecterLot(Lot lot, Societe societe, Ace ace)
    {
        try {
            String corps = "{"
                + "\"numCDE\":"    + lot.getNumCDE()              + ","
                + "\"nomSociete\":" + e(societe.getNom())         + ","
                + "\"nomAce\":"    + e(ace != null ? ace.getNom() : "") + "}";
            String rep = post("/lots/affecter", corps);
            mettreAJourDepuisReponseDual(rep);
            return true;
        } catch (Exception e) { err("affecterLot", e); return false; }
    }

    @Override
    public void desaffecterLot(Lot lot)
    {
        try {
            String rep = post("/lots/desaffecter", "{\"numCDE\":" + lot.getNumCDE() + "}");
            mettreAJourDepuisReponseDual(rep);
        } catch (Exception e) { err("desaffecterLot", e); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MODIFICATION SOCIÉTÉ / ACE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void modifierSociete(Societe soc, String nom, String ce, int totalHeuresCE, int effectif)
    {
        try {
            String corps = "{"
                + "\"nomActuel\":"    + e(soc.getNom()) + ","
                + "\"nom\":"          + e(nom)          + ","
                + "\"ce\":"           + e(ce)           + ","
                + "\"totalHeuresCE\":" + totalHeuresCE  + ","
                + "\"effectif\":"     + effectif        + "}";
            this.societes = JsonSerialiser.deserialiserSocietes(
                post("/societes/modifier", corps), this.lots);
        } catch (Exception e) { err("modifierSociete", e); }
    }

    @Override
    public void modifierAce(Ace ace, String nom, int nbPers, int effectif)
    {
        try {
            Societe soc = getSocieteDeAce(ace);
            String corps = "{"
                + "\"nomSociete\":"   + e(soc != null ? soc.getNom() : "") + ","
                + "\"nomActuelAce\":" + e(ace.getNom()) + ","
                + "\"nom\":"          + e(nom)          + ","
                + "\"nbPers\":"       + nbPers          + ","
                + "\"effectif\":"     + effectif        + "}";
            this.societes = JsonSerialiser.deserialiserSocietes(
                post("/aces/modifier", corps), this.lots);
        } catch (Exception e) { err("modifierAce", e); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUIVI PRODUCTION
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
    {
        try {
            String corps = "{"
                + "\"numCDE\":"       + lot.getNumCDE() + ","
                + "\"nbPieceEtiq\":"  + nbPieceEtiq    + ","
                + "\"nbPieceRepart\":" + nbPieceRepart  + "}";
            this.lots = JsonSerialiser.deserialiserLots(post("/lots/suiviprod", corps));
        } catch (Exception e) { err("mettreAJourSuiviProd", e); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RECHERCHE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Societe getSocieteDuLot(Lot lot)
    {
        for (Societe s : societes)
            for (Lot l : s.getLots())
                if (l.getNumCDE() == lot.getNumCDE()) return s;
        return null;
    }

    @Override
    public Ace getAceDuLot(Lot lot)
    {
        for (Societe s : societes)
            for (Ace a : s.getAces())
                for (Lot l : a.getLots())
                    if (l.getNumCDE() == lot.getNumCDE()) return a;
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FICHE DE ROUTE
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public FicheRoute genererFicheRoute(Societe societe)
    {
        // La fiche de route utilise directement les lots de la société
        // qui sont déjà dans le cache local — pas besoin d'un aller-retour réseau.
        // On retourne simplement la fiche recalculée localement.
        return new app.metier.ficheroute.FicheRoute(societe);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SAUVEGARDE / CHARGEMENT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void sauvegarderDonnees(String cheminDossier, String semaine)
    {
        try {
            // Le chemin est relatif au PC serveur (pas au client)
            post("/sauvegarder", "{\"chemin\":" + e(cheminDossier) + ",\"semaine\":" + e(semaine) + "}");
            System.out.println("[Client] Sauvegarde envoyée au serveur.");
        } catch (Exception ex) { err("sauvegarderDonnees", ex); }
    }

    @Override
    public void chargerDonnees(String chemin) throws IOException
    {
        try {
            String rep = post("/charger", "{\"chemin\":" + e(chemin) + "}");
            mettreAJourDepuisReponseDual(rep);
        } catch (Exception ex) { throw new IOException(ex.getMessage()); }
    }

    @Override
    public void nouveaux()
    {
        try {
            post("/nouveaux", "{}");
            chargerDepuisServeur();
        } catch (Exception e) { err("nouveaux", e); }
    }

    @Override
    public void NouvelleHeurePourSociete(int semaine)
    {
        try {
            String rep = post("/nouvelleheure", "{\"semaine\":" + semaine + "}");
            this.societes = JsonSerialiser.deserialiserSocietes(rep, this.lots);
        } catch (Exception e) { err("NouvelleHeurePourSociete", e); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITAIRES PRIVÉS
    // ══════════════════════════════════════════════════════════════════════

    /** Décompose une réponse {"lots":[...],"societes":[...]} et met à jour le cache. */
    private void mettreAJourDepuisReponseDual(String rep)
    {
        String jsonLots = JsonSerialiser.extraireBloc(rep, "\"lots\"");
        String jsonSoc  = JsonSerialiser.extraireBloc(rep, "\"societes\"");
        if (jsonLots != null) this.lots     = JsonSerialiser.deserialiserLots(jsonLots);
        if (jsonSoc  != null) this.societes = JsonSerialiser.deserialiserSocietes(jsonSoc, this.lots);
    }

    private Societe getSocieteDeAce(Ace ace)
    {
        for (Societe s : societes)
            for (Ace a : s.getAces())
                if (a.getNom().equals(ace.getNom())) return s;
        return null;
    }

    private String get(String route) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(urlServeur + route)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400)
            throw new Exception("HTTP " + resp.statusCode() + " : " + resp.body());
        return resp.body();
    }

    private String post(String route, String json) throws Exception
    {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(urlServeur + route))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400)
            throw new Exception("HTTP " + resp.statusCode() + " : " + resp.body());
        return resp.body();
    }

    private static String e(String s) { return JsonSerialiser.esc(s); }

    private void err(String m, Exception e)
    { System.err.println("[Client] " + m + " : " + e.getMessage()); }

    // ══════════════════════════════════════════════════════════════════════
    //  MAIN
    // ══════════════════════════════════════════════════════════════════════

    public static void main(String[] args)
    {
        String ip = args.length > 0 ? args[0] : "localhost";
        System.out.println("[Client] Connexion au serveur : " + ip);
        new ControleurClient(ip);
    }
}
