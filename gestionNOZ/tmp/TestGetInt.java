import java.nio.file.Files;
import java.nio.file.Path;
import app.securite.ChiffrementAES;

public class TestGetInt {
    public static void main(String[] args) throws Exception {
        String chemin = "app/data/courutilisation/lots.json";
        String raw = Files.readString(Path.of(chemin));
        ChiffrementAES aes = ChiffrementAES.chargerOuCreer("secret.key");
        String json = aes.dechiffrer(raw.trim());
        var objs = app.metier.collecte.JsonSerialiser.extraireObjets(json);
        String obj = objs.get(2);
        System.out.println("nbPers raw text: " + obj.substring(obj.indexOf("\"nbPers\""), Math.min(obj.indexOf("\n", obj.indexOf("\"nbPers\"")), obj.length())));
        long start = System.nanoTime();
        int value = app.metier.collecte.JsonSerialiser.getInt(obj, "nbPers");
        long duration = System.nanoTime() - start;
        System.out.println("nbPers=" + value + " duration=" + duration + "ns");

        System.out.println("valeur sp_nbHeureEtiqRestant=" + app.metier.collecte.JsonSerialiser.getInt(obj, "sp_nbHeureEtiqRestant"));
    }
}
