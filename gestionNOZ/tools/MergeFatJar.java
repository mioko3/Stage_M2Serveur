import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class MergeFatJar {
    public static void main(String[] args) throws Exception {
        String output    = args[0];
        String mainClass = args[1];

        Map<String, StringBuilder> services = new LinkedHashMap<>();
        Map<String, byte[]>        entries  = new LinkedHashMap<>();

        for (int i = 2; i < args.length; i++) {
            File f = new File(args[i]);
            if (!f.exists()) { System.out.println("SKIP (introuvable) : " + f); continue; }

            if (f.isDirectory()) {
                Files.walk(f.toPath()).filter(Files::isRegularFile).forEach(p -> {
                    String rel = f.toPath().relativize(p).toString().replace('\\', '/');
                    // Ignorer les manifestes venant du dossier bin (META-INF extrait des JARs POI)
                    if (rel.startsWith("META-INF/")) return;
                    try { entries.putIfAbsent(rel, Files.readAllBytes(p)); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
            } else {
                try (JarInputStream jis = new JarInputStream(new FileInputStream(f))) {
                    JarEntry entry;
                    while ((entry = jis.getNextJarEntry()) != null) {
                        String name = entry.getName();
                        if (entry.isDirectory())             continue; // ignorer les dossiers
                        if (name.startsWith("META-INF/MANIFEST")) continue;
                        if (name.equals("META-INF/"))        continue;
                        if (name.startsWith("META-INF/maven"))  continue; // inutile
                        if (name.startsWith("META-INF/license")) continue;
                        if (name.startsWith("META-INF/LICENSE")) continue;
                        if (name.startsWith("META-INF/NOTICE"))  continue;

                        byte[] data = jis.readAllBytes();

                        if (name.startsWith("META-INF/services/")) {
                            services.computeIfAbsent(name, k -> new StringBuilder())
                                    .append(new String(data)).append('\n');
                        } else {
                            entries.putIfAbsent(name, data);
                        }
                    }
                }
            }
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output), manifest)) {
            // Entries normales
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                try {
                    jos.putNextEntry(new ZipEntry(e.getKey()));
                    jos.write(e.getValue());
                    jos.closeEntry();
                } catch (ZipException ze) {
                    // doublon résiduel — ignorer silencieusement
                }
            }
            // Services fusionnés + dédoublonnés
            for (Map.Entry<String, StringBuilder> e : services.entrySet()) {
                String[] lines = e.getValue().toString().split("\\r?\\n");
                Set<String> seen = new LinkedHashSet<>();
                for (String l : lines) { String t = l.trim(); if (!t.isEmpty() && !t.startsWith("#")) seen.add(t); }
                if (seen.isEmpty()) continue;
                try {
                    jos.putNextEntry(new ZipEntry(e.getKey()));
                    jos.write(String.join("\n", seen).getBytes());
                    jos.closeEntry();
                } catch (ZipException ze) { /* doublon — ignorer */ }
            }
        }
        System.out.println("Fat-jar cree : " + output);
    }
}