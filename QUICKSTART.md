# ⚡ Quick Start — 5 minutes pour bien démarrer

## 🚀 En 5 minutes, lancez l'app

### Étape 1 : Vérifier les prérequis (1 min)
```bash
# Vérifier Java 11+
java -version
# Doit afficher : java version "11.0.x" ou plus

# Vérifier le projet existe
cd c:\Users\erwan\Documents\GitHub\Stage_M2Serveur\gestionNOZ
dir app  # Doit afficher le dossier "app"
```

### Étape 2 : Compiler (2 min)
```bash
cd gestionNOZ

# Option A : Utiliser le fichier compile.list (recommandé)
javac @compile.list -d bin

# Option B : Compiler manuellement
javac -cp "jar/poi-bin-5.2.3/lib/*:bin" -d bin app/**/*.java
```

### Étape 3 : Lancer (2 min) — choisir un mode

**Mode Solo (développement local)**
```bash
java -cp "jar/poi-bin-5.2.3/lib/*;bin" app.Controleur
# → Écran login apparaît
# → Connectez-vous avec un identifiant quelconque
# → Données charges localement
```

**Mode Serveur (sur un PC central)**
```bash
java -cp "jar/poi-bin-5.2.3/lib/*;bin" app.ServeurHTTP
# → Serveur écoute sur port 8082
# → FenetreServeur s'ouvre (tableau de bord)
```

**Mode Client (sur postes utilisateurs)**
```bash
java -cp "jar/poi-bin-5.2.3/lib/*;bin" app.ControleurClient
# → Écran login + IP serveur
# → Saisir IP = 127.0.0.1 (local test) ou 192.168.x.x (vrai serveur)
# → Identifiant = PAM ou nom société
# → Mot de passe = (voir base de données)
```

---

## 📚 Après les 5 minutes → Lire la suite

|  Temps |             Lecture             |        Résultat         |
|--------|---------------------------------|-------------------------|
| 15 min | `MANUEL_DEVELOPPEUR.md` sec 1-2 |     Comprendre archi    |
| 30 min | `MANUEL_DEVELOPPEUR.md` sec 3-4 |    Maîtriser structure  |
|   1h   | `GUIDE_COMMENTAIRES_CLASSES.md` | Classes clés expliquées |
|   2h   |         Tous les manuels        |     Expert complet      |

---

## 🔥 Astuces rapides

### "Où se trouvent les données ?"
→ `app/data/courutilisation/` (JSON local)

### "Comment ajouter un nouveau lot ?"
→ Lire `GUIDE_COMMENTAIRES_CLASSES.md` section `IControleur`

### "Le serveur n'écoute pas ?"
→ Vérifier port 8082 pas bloqué par firewall

### "Les fichiers ne se trouvent pas ?"
→ Vérifier working directory = dossier `gestionNOZ`

### "Comment déboguer ?"
→ Lire `MANUEL_DEVELOPPEUR.md` section "Mode de débogage"

---

## 📖 Les 3 documents essentiels

1. **`MANUEL_DEVELOPPEUR.md`** ← Commencer par là
2. **`GUIDE_COMMENTAIRES_CLASSES.md`** ← Consulter pour chaque classe
3. **`INDEX_DOCUMENTATION.md`** ← Mappe complète

---

**C'est parti ! 🎉 Bienvenue au projet Planning Global Futura**
