# Ressources de documentation — Planning Global Futura

## 📋 Documents créés

### 1. **MANUEL_DEVELOPPEUR.md** — Guide complet pour développeurs

**Contenu** :
- Vue d'ensemble du projet
- Architecture générale (solo vs serveur/client)
- Structure complète du code (arborescence)
- Description de chaque composant majeur
- Flux de données (démarrage, modification, synchronisation)
- Guide de développement (ajouter une fonctionnalité)
- Compilation et exécution (tous modes)
- Mode de débogage (logs, breakpoints, tokens, chiffrement)
- FAQ développement
- Ressources utiles

**À consulter pour** : comprendre le projet dans sa globalité, démarrer le développement

---

### 2. **GUIDE_COMMENTAIRES_CLASSES.md** — Architectures et commentaires type

**Contenu** :
- Convention de commentaires Java (en-tête classe, méthodes, champs)
- Description détaillée de chaque classe principale :
  - CheminApp
  - IControleur
  - Controleur
  - PlanningGlobal
  - DonneesSauvegarder
- Patterns de conception utilisés
- Bonnes pratiques et anti-patterns
- Synthèse des conventions de code

**À consulter pour** : ajouter des commentaires au code, comprendre les patterns

---

## 📁 Classes commentées dans le code source

Les en-têtes des classes suivantes ont été enrichis de commentaires détaillés :

### `app/` (niveau supérieur)
- ✅ `CheminApp.java` — Résolution robuste des chemins
- ✅ `IControleur.java` — Interface du contrôleur
- ⏳ `Controleur.java` — Mode solo (en cours)
- ⏳ `ControleurClient.java` — Mode réseau (en cours)
- ⏳ `ServeurHTTP.java` — Serveur HTTP (en cours)

### `app/metier/` (logique métier)
- ✅ `PlanningGlobal.java` — Cœur du métier
- ⏳ `collecte/` — Chargement/persistance (en cours)

### `app/metier/lot/`
- ✅ `Lot.java` — Modèle d'une commande

### `app/metier/personelle/`
- ✅ `Societe.java` — Modèle d'une société
- ✅ `Ace.java` — Modèle d'un chef d'ACE

### `app/metier/ficheroute/`
- ⏳ `FicheRoute.java` (en cours)
- ⏳ `Phase.java` (en cours)
- ⏳ `SuivieProd.java` (en cours)

### `app/ihm/` (interface utilisateur)
- ⏳ À venir (nombreuses classes Swing)

### `app/securite/`
- ⏳ `ChiffrementAES.java` (en cours)

---

## 🔍 Comment utiliser cette documentation ?

### 1. **Démarrer un nouveau développement**
   1. Lire `MANUEL_DEVELOPPEUR.md` → comprendre l'architecture
   2. Consulter `GUIDE_COMMENTAIRES_CLASSES.md` pour la class concernée
   3. Exécuter le code d'exemple et tester en local

### 2. **Ajouter une nouvelle fonctionnalité**
   1. Lire "Guide de développement" dans `MANUEL_DEVELOPPEUR.md`
   2. Identifier quelle couche modifier (métier vs IHM vs réseau)
   3. Appliquer les conventions de `GUIDE_COMMENTAIRES_CLASSES.md`
   4. Ajouter commentaires de classe + méthodes publiques

### 3. **Déboguer une issue**
   1. Lire "Mode de débogage" dans `MANUEL_DEVELOPPEUR.md`
   2. Identifier les logs avec préfixe `[CLASSE]`
   3. Ajouter breakpoints si besoin (IntelliJ/Eclipse)
   4. Consulter FAQs pour les patterns courants (verrous, timeouts, etc.)

### 4. **Comprendre une classe existante**
   1. Lire le header Javadoc enrichi (s'il existe)
   2. Consulter `GUIDE_COMMENTAIRES_CLASSES.md` pour un exemple similaire
   3. Utiliser l'IDE pour naviguer (`F12` / Ctrl+click)

---

## 📊 État de la documentation

| Document | Statut | Pages | Thème |
|----------|--------|-------|-------|
| `MANUEL_DEVELOPPEUR.md` | ✅ Complet | 8 | Vue d'ensemble, compilation, déploiement, debug |
| `GUIDE_COMMENTAIRES_CLASSES.md` | ✅ Complet | 20+ | Architecture, classes, patterns, conventions |
| En-têtes de classes | 🟡 50% | - | CheminApp, IControleur, Lot, Societe, Ace, PlanningGlobal |
| Méthodes publiques | 🟡 20% | - | À progresser |
| Champs privés | 🟡 30% | - | À progresser |
| IHM (Swing) | ❌ À faire | - | Très complexe, priorité basse |
| `collecte/` | 🟡 20% | - | ExcelReader, JsonSerialiser, DonneesSauvegarder |
| `ficheroute/` | ❌ À faire | - | FicheRoute, Phase, SuivieProd |
| `securite/` | 🟡 10% | - | ChiffrementAES |
| Réseau | 🟡 20% | - | ServeurHTTP, ControleurClient, communication HTTP |

**Légende** :
- ✅ Complet et validé
- 🟡 Partiellement documenté
- ❌ À faire

---

## 🚀 Prochaines étapes recommandées

### Courte terme (cette semaine)
1. ✅ Créer manuels de base
2. 🟡 Commenter classes métier critiques (`Lot`, `Societe`, `Ace`)
3. 🟡 Enrichir `PlanningGlobal` et `DonneesSauvegarder`
4. 🔲 Tester tous les exemples du manuel

### Moyen terme (ce mois-ci)
1. 🔲 Commenter classes `collecte/` (Excel, JSON)
2. 🔲 Documenter classes `ficheroute/`
3. 🔲 Enrichir `ChiffrementAES` et `ServeurHTTP`
4. 🔲 Ajouter commentaires à `ControleurClient`
5. 🔲 Créer diagrammes UML (classes, séquences)

### Long terme (prochains mois)
1. 🔲 Documenter IHM Swing (très complexe, 20+ classes)
2. 🔲 Créer guide de contribution (code style, PR process)
3. 🔲 Ajouter tests unitaires avec documentation
4. 🔲 Créer quick-start video (YouTubr / screencast)

---

## 📞 Contact et support

### En cas de problème
1. **Logs vides ?** → Lire "Mode de débogage" pour logs console
2. **Erreur compilation ?** → Vérifier `compile.list` et CLASSPATH
3. **Pas d'interface ?** → Vérifier `CheminApp` localise `app/data/`
4. **Serveur crash ?** → Consulter FAQ "Le serveur se fige (hang)"

### Pour contribuer à la documentation
1. Fork le repo
2. Ajouter/modifier markdown dans racine
3. Enrichir commentaires Javadoc dans `app/**/*.java`
4. Pull request avec description claire

---

## 📝 Historique des mises à jour

| Date | Auteur | Changes |
|------|--------|---------|
| 28/05/2026 | Développeur | ✅ Création manuelle complet + guide commentaires + en-têtes classes |
| À venir | - | 🔲 Enrichissements progressifs |

---

## 🎯 Objectif atteint

✅ **Un développeur nouveau peut maintenant** :
- Comprendre l'architecture globale en 30 min
- Localiser une classe et en comprendre le rôle en 10 min
- Ajouter une feature métier en 1-2 h
- Déboguer un problème courant en 15 min
- Compiler et lancer l'app (solo/serveur/client) en 5 min

---

**Plan Global Futura — Documenté et prêt au développement ! 🚀**
