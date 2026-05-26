import app.metier.collecte.DonneesSauvegarder;
import app.securite.ChiffrementAES;
import java.nio.file.Files;
import java.nio.file.Path;

public class PrintLot2 {
    public static void main(String[] args) throws Exception {
        String chemin = "app/data/courutilisation/lots.json";
        String raw = Files.readString(Path.of(chemin));
        ChiffrementAES aes = ChiffrementAES.chargerOuCreer("secret.key");
        String json = aes.dechiffrer(raw.trim());
        System.out.println("length=" + json.length());
        var objs = app.metier.collecte.JsonSerialiser.extraireObjets(json);
        System.out.println("count=" + objs.size());
        if (objs.size() > 2) {
            String obj = objs.get(2);
            System.out.println(obj);
        } else {
            System.out.println("not enough objects");
        }
    }
}
