import app.metier.collecte.DonneesSauvegarder;
import app.metier.collecte.JsonSerialiser;
import app.securite.ChiffrementAES;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtraireTest {
    public static void main(String[] args) throws Exception {
        String cheminSoc = "app/data/courutilisation/societes.json";
        String raw = Files.readString(Path.of(cheminSoc));
        ChiffrementAES aes = ChiffrementAES.chargerOuCreer("secret.key");
        String json = aes.dechiffrer(raw.trim());
        System.out.println("json length = " + json.length());
        System.out.println(json.substring(0, Math.min(json.length(), 400)));
        var objs = JsonSerialiser.extraireObjets(json);
        System.out.println("objs size = " + objs.size());
        for (int i = 0; i < objs.size(); i++) {
            System.out.println("obj " + i + " length=" + objs.get(i).length());
        }
    }
}
