# Manuel de Développeur - Planning Global Futura

## Table des matières

1. [Vue d'ensemble du projet](#vue-densemble)
2. [Architecture générale](#architecture)
3. [Structure du code](#structure)
4. [Composants principaux](#composants)
5. [Flux de données](#flux)
6. [Guide de développement](#guide)
7. [Compilation et exécution](#compilation)
8. [Mode de débogage](#debug)

---

## Vue d'ensemble

**Planning Global Futura** est une application Java de gestion de planning et de fiches de route pour la gestion logistique. Elle supporte :
- **Mode Solo** : une seule instance locale
- **Mode Serveur/Client** : un serveur central + plusieurs clients connectés via réseau
- **Persistance** : données stockées en JSON (chiffrement AES-256 en mode réseau)
- **Interface Swing** : interface graphique desktop pour Windows/Linux
- **Communication HTTP** : REST API sur port 8082 (serveur)

---

## Architecture

### 1. Architecture générale

```
┌─────────────────────────────────────────────────────────────┐
│                   Planning Global Futura                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────┐          ┌────────────────────┐  │
│  │   Mode Solo           │          │ Mode Serveur/Client│  │
│  ├───────────────────────┤          ├────────────────────┤  │
│  │ Controleur (IHM)      │          │ ServeurHTTP        │  │
│  │ ↓                     │          │ (HTTP REST API)    │  │
│  │ PlanningGlobal        │          │ ↑↓                 │  │
│  │ (métier)              │          │ ControleurClient   │  │
│  │ ↓                     │          │ (clients réseau)   │  │
│  │ DonneesSauvegarder    │          │ ↑↓                 │  │
│  │ (JSON local)          │          │ Chiffrement AES    │  │
│  └───────────────────────┘          └────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2. Trois points d'entrée

| Mode | Classe | Fichier | Usage |
|------|--------|---------|-------|
| **Solo** | `Controleur` | Mode développement / bureau unique |
| **Serveur** | `ServeurHTTP` | PC central, gère les données |
| **Client** | `ControleurClient` | Postes utilisateurs, se connecte au serveur |

### 3. Flux de données en réseau

```
Client 1                    Serveur                    Client 2
   │                          │                          │
   ├─ GET /version ──────────>│                          │
   │<───── [version_json] ────┤                          │
   │                          │<─ GET /version ──────────┤
   │                          ├───── [version_json] ────>│
   │                          │                          │
   ├─ POST /lots ────────────>│                          │
   │ (lots modifiés)          │                          │
   │                    [sync]│                          │
   │                          │<─ GET /lots ─────────────┤
   │                          ├─ [lots] ────────────────>│
   │                          │                          │
```

---

## Structure du code

### 1. Arborescence

```
gestionNOZ/
├── app/                          ← Cœur de l'application
│   ├── CheminApp.java            ← Résolution des chemins
│   ├── Controleur.java           ← Contrôleur mode Solo
│   ├── ControleurClient.java     ← Contrôleur mode Réseau Client
│   ├── IControleur.java          ← Interface (dictionnaire de méthodes)
│   ├── ServeurHTTP.java          ← Serveur HTTP REST
│   │
│   ├── ihm/                      ← Interface utilisateur (Swing)
│   │   ├── FenetrePrincipale.java
│   │   ├── IhmUtils.java
│   │   ├── diagrame/             ← Diagrammes (Gantt, etc.)
│   │   ├── dialogue/             ← Boîtes de dialogue
│   │   ├── ficheroute/           ← Fiches de route
│   │   ├── gestionlot/           ← Gestion des lots
│   │   ├── login/                ← Écran de connexion
│   │   ├── map/                  ← Cartographie
│   │   └── serveur/              ← Écran de contrôle serveur
│   │
│   ├── metier/                   ← Logique métier
│   │   ├── PlanningGlobal.java   ← Gestion des lots et sociétés
│   │   ├── collecte/             ← Chargement et sérialisation
│   │   │   ├── ExcelReader.java  ← Lecteur de fichiers Excel
│   │   │   ├── JsonSerialiser.java
│   │   │   └── DonneesSauvegarder.java
│   │   ├── ficheroute/           ← Fiches de route (métier)
│   │   │   ├── FicheRoute.java
│   │   │   ├── Phase.java
│   │   │   └── SuivieProd.java
│   │   ├── lot/                  ← Gestion des lots
│   │   │   ├── Lot.java
│   │   │   ├── Methode.java
│   │   │   └── LigneColisage.java
│   │   └── personelle/           ← Personnes, sociétés
│   │       ├── Ace.java
│   │       └── Societe.java
│   │
│   ├── securite/                 ← Chiffrement et sécurité
│   │   └── ChiffrementAES.java   ← AES-256-CBC
│   │
│   ├── data/                     ← Données persistantes
│   │   ├── courutilisation/      ← Données en cours
│   │   │   ├── lots.json
│   │   │   ├── societes.json
│   │   │   ├── lots_tmp.json     ← Temporaire
│   │   │   └── societes_tmp.json
│   │   ├── enregistrementparsemaine/
│   │   │   └── S17/              ← Lots semaine 17
│   │   └── pastouche/            ← Données de référence
│   │       └── societes.json
│   │
│   └── jar/
│       └── poi-bin-5.2.3/        ← Apache POI (Excel)
│
├── bin/                          ← Classes compilées (.class)
├── output/                       ← Exécutables JAR packaging
├── tools/
│   └── MergeFatJar.java          ← Outil de génération JAR
│
├── compile.list                  ← Liste des fichiers à compiler
└── run_*.bat                     ← Scripts de lancement

```

---

## Composants principaux

### 1. **Controleur** (Mode Solo)

**Fichier** : [app/Controleur.java](app/Controleur.java)

**Rôle** : Implémente `IControleur` en mode application unique (pas de réseau)

**Responsabilités** :
- Charger les données depuis Excel ou JSON
- Gérer les interactions IHM
- Invoquer `PlanningGlobal` pour les opérations métier
- Persister les données avec `DonneesSauvegarder`

**Méthodes clés** :
- `lancerApp(String login, boolean utiliserExcel)` : démarre l'IHM
- `chargerDepuisExcelInteractif()` : sélection et chargement Excel
- `chargerFallbackJson()` : fallback sur JSON
- `getListeLots()`, `getListeSocietes()` : accès aux données
- `sauvegarderLots()`, `sauvegarderSocietes()` : persistance

**Flux d'initialisation** :
```
new Controleur()
  → new PlanningGlobal()
  → new DonneesSauvegarder()
  → invokeLater(() → new FenetreLogin(this))
  → utilisateur entre login
  → lancerApp(login, excel)
  → new FenetrePrincipale(this)
```

---

### 2. **ServeurHTTP** (Mode Serveur)

**Fichier** : [app/ServeurHTTP.java](app/ServeurHTTP.java)

**Rôle** : Serveur REST HTTP pour clients réseau (port 8082)

**Endpoints principaux** :
- `GET /version` : numéro de version actuelle (sync clients)
- `GET /lots` : liste des lots
- `GET /societes` : liste des sociétés
- `GET /cle` : clé AES de chiffrement (après authentification)
- `POST /lots` : mise à jour des lots
- `POST /societes` : mise à jour des sociétés
- `POST /login` : authentification, génération token
- `POST /logout` : déconnexion

**Sécurité** :
- Tokens de session (X-Auth-Token)
- Chiffrement AES-256-CBC des réponses et requêtes
- Authentification par identifiant + mot de passe ACE

**Gestion de la concurrence** :
- `ReadWriteLock` : plusieurs lectures simultanées, écritures exclusives
- Timeout clients : 30 secondes
- Verrous toujours libérés dans `finally{}`

**Modes d'exécution** :
- **Mode graphique** : lance `FenetreServeur` (Windows)
- **Mode headless** : console ASCII (Linux, serveurs)

---

### 3. **ControleurClient** (Mode Client Réseau)

**Fichier** : [app/ControleurClient.java](app/ControleurClient.java)

**Rôle** : Client qui se connecte au `ServeurHTTP`

**Caractéristiques** :
- Implémente `IControleur` (même interface que `Controleur`)
- Communication en HTTP/HTTPS
- Chiffrement AES automatique des échanges
- Polling toutes les 3 secondes (synchronisation)
- Récupération locale des données au démarrage

**Flux de connexion** :
```
new ControleurClient(ip, port, identifiant, mdp)
  → POST /login (sans chiffrement yet)
  → Récup : sessionToken + timeout
  → Chargement lots/sociétés (GET, sans chiffrement yet)
  → GET /cle → réception clé AES
  → À partir de là : tous les échanges chiffrés
  → Thread polling démarre (GET /version toutes les 3s)
```

**Modes spéciaux** :
- **Mode désynchronisé** (PAM) : préparer des semaines futures sans perturber les autres
- **Resynchronisation** : recharger l'état du serveur

---

### 4. **PlanningGlobal** (Métier)

**Fichier** : [app/metier/PlanningGlobal.java](app/metier/PlanningGlobal.java)

**Rôle** : Logique métier centrale

**Responsabilités** :
- Gestion des `Lot` (ajout, modification, suppression)
- Gestion des `Societe` (ajout, modification, suppression)
- Calcul des plannings globaux
- Validation des données

**Structures de données** :
- `ArrayList<Lot> listeLots` : tous les lots
- `ArrayList<Societe> listeSocietes` : toutes les sociétés
- `int semaine` : semaine active
- `int annee` : année active

**Méthodes essentielles** :
- `chargerDepuisExcel(String path, ...)` : remplir depuis Excel
- `chargerLots(String pathJson)`, `chargerSocietes(String pathJson)` : charger JSON
- `ajouterLot(Lot)`, `modifierLot(Lot)`, `supprimerLot(Lot)`
- `ajouterSociete(Societe)`, `modifierSociete(Societe)`, `supprimerSociete(Societe)`
- `calculerPlanning()`, `calculerCharges()`

---

### 5. **DonneesSauvegarder** (Persistance)

**Fichier** : [app/metier/collecte/DonneesSauvegarder.java](app/metier/collecte/DonneesSauvegarder.java)

**Rôle** : Lecture/écriture fichiers JSON sur disque

**Responsabilités** :
- Sérialiser `ArrayList<Lot>` ↔ JSON
- Sérialiser `ArrayList<Societe>` ↔ JSON
- Chiffrement/déchiffrement AES
- Gestion des fichiers temporaires

**Méthodes clés** :
- `sauvegarderLots(List<Lot>, String path)` : écrit en JSON
- `sauvegarderSocietes(List<Societe>, String path)` : écrit en JSON
- `chargerLots(String path)` : lit JSON → `ArrayList<Lot>`
- `chargerSocietes(String path)` : lit JSON → `ArrayList<Societe>`

---

### 6. **JsonSerialiser** (Sérialisation)

**Fichier** : [app/metier/collecte/JsonSerialiser.java](app/metier/collecte/JsonSerialiser.java)

**Rôle** : Construction de JSON et d'objets depuis JSON

**Méthodes essentielles** :
- `toJson(Lot/Societe/...)` : objet → chaîne JSON
- `fromJson(String, Class<T>)` : chaîne JSON → objet
- Gestion complète des échappements JSON (`\t`, `\r`, `\\`, etc.)

---

### 7. **ChiffrementAES** (Sécurité)

**Fichier** : [app/securite/ChiffrementAES.java](app/securite/ChiffrementAES.java)

**Rôle** : Chiffrement AES-256-CBC

**Méthodes** :
- `init(File key)` : charge/génère la clé secrète (secret.key)
- `chiffrer(String plaintext)` : plaintext → Base64(IV + ciphertext)
- `dechiffrer(String encrypted)` : Base64(IV + ciphertext) → plaintext

**Clé** :
- Générée aléatoirement au premier démarrage
- Stockée en `secret.key` (256 bits)
- Partage proposée via API `GET /cle` sur le serveur

---

### 8. **ExcelReader** (Import)

**Fichier** : [app/metier/collecte/ExcelReader.java](app/metier/collecte/ExcelReader.java)

**Rôle** : Lecture fichiers Excel/XLSX

**Méthodes** :
- `lireLots(String pathXlsx)` : → `ArrayList<Lot>`
- `lireSocietes(String pathXlsx)` : → `ArrayList<Societe>`
- `lireAces(String pathXlsx)` : → `ArrayList<Ace>`

**Utilise** : Apache POI (`poi-bin-5.2.3/`)

---

### 9. **IControleur** (Interface)

**Fichier** : [app/IControleur.java](app/IControleur.java)

**Rôle** : Contrat commun pour `Controleur` et `ControleurClient`

**Signature** : méthodes que l'IHM peut appeler indifféremment en Solo ou Réseau

```java
List<Lot> getListeLots();
List<Societe> getListeSocietes();
void sauvegarderLots(List<Lot>);
void sauvegarderSocietes(List<Societe>);
// ... etc
```

---

## Flux de données

### 1. Démarrage en mode Solo

```
Ligne commande
  ↓
Controleur.main()
  ↓
new Controleur()
  → new PlanningGlobal()
  → new DonneesSauvegarder()
  ↓
FenetreLogin (saisie identifiant)
  ↓
lancerApp(identifiant, useExcel=true)
  ↓
chargerDepuisExcelInteractif()
  → demanderFichierExcel()
  → ExcelReader.lireLots()
  → PlanningGlobal.chargerDepuisExcel()
  ↓
new FenetrePrincipale()
  → Affichage interface Swing
```

### 2. Démarrage en mode Serveur

```
run_SERVEUR.bat
  ↓
ServeurHTTP.main()
  ↓
new ServeurHTTP()
  → new PlanningGlobal()
  → new DonneesSauvegarder()
  → ChargementJSON
  → Création HttpServer port 8082
  → Enregistrement handlers (GET/POST)
  ↓
if (!headless) new FenetreServeur()
  → Affichage tableau de bord
else
  → Menu console ASCII
```

### 3. Démarrage en mode Client

```
run_CLIENT.bat
  ↓
ControleurClient.main()
  ↓
FenetreLogin (IP serveur + identifiant)
  ↓
new ControleurClient(ip, port, identifiant, mdp)
  ↓
Threads parallèles:
  (1) chargerDepuisServeur()
      → GET /lots + /societes (sans chiffrement)
  (2) recupererCle()
      → GET /cle (échange clé AES)
  (3) polling() toutes 3s
      → GET /version (détecte changements)
  ↓
new FenetrePrincipale()
  → Affichage interface Swing
```

### 4. Modification d'un lot (tous modes)

```
Utilisateur modifie lot dans IHM
  ↓
FenetrePrincipale.validerModificationLot()
  ↓
IControleur.modifierLot(lot)
  ↓
SI mode Solo:
  Controleur.modifierLot()
    → PlanningGlobal.modifierLot()
    → DonneesSauvegarder.sauvegarderLots()
    → Écriture lots.json
SINON mode Réseau:
  ControleurClient.modifierLot()
    → POST /lots (body: [lots] chiffré)
    → ServeurHTTP reçoit
    → Déchiffre + vérifie droits
    → PlanningGlobal.modifierLot()
    → DonneesSauvegarder.sauvegarderLots()
    → Retour 200 OK
    → Autres clients détectent via GET /version
      → Leur polling les force à GET /lots
  ↓
IHM se rafraîchit
```

---

## Guide de développement

### 1. Ajouter une nouvelle fonctionnalité

**Étape 1 : Métier** (`app/metier/`)
- Ajouter la méthode dans `PlanningGlobal.java`
- Exemple : `public void exporterPDF(String path)` { ... }

**Étape 2 : Contrôleur** (`app/Controleur.java` + `ControleurClient.java`)
- Implémenter le delégation vers PlanningGlobal
- Exemple : `exporterPDF()` { return metier.exporterPDF(...); }

**Étape 3 : Réseau** (`ServeurHTTP.java`)
- Ajouter l'endpoint HTTP si nécessaire
- Exemple : `POST /exportPDF` → invoke métier + retour Base64

**Étape 4 : IHM** (`app/ihm/`)
- Ajouter bouton, menu, ou dialogue
- Appeler `IControleur.exporterPDF()`
- L'IHM ne sait pas si c'est Solo ou Réseau !

### 2. Cycle de compilation et test

#### a. Compilation
```bash
cd c:\Users\erwan\Documents\GitHub\Stage_M2Serveur\gestionNOZ

# Compiler depuis compile.list
javac @compile.list

# Ou manuellement :
javac -cp "jar/poi-bin-5.2.3/lib/*;." -d bin app/**/*.java
```

#### b. Test Solo
```bash
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.Controleur
```

#### c. Test Serveur
```bash
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ServeurHTTP
# Ouvre FenetreServeur sur port 8082
```

#### d. Test Client
```bash
# Terminal 1 : serveur
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ServeurHTTP

# Terminal 2 : client
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ControleurClient
# Saisir IP=127.0.0.1, identifiant=PAM, mdp=admin
```

### 3. Débogage avec breakpoints

#### IDEs supportées :
- **IntelliJ IDEA** : Import projet → Run → Debug
- **Eclipse** : File → Import → Debug as → Java Application
- **VS Code** : Extension Debugger for Java

#### Debugger en Serveur Multi-clients
```
# Terminal 1 : Serveur en debug
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ServeurHTTP

# IDE : Run → Debug → Remote → localhost:5005

# Terminal 2/3/... : Clients normaux
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ControleurClient
```

---

## Compilation et exécution

### 1. Compiler

**Fichier** : `compile.list` contient tous les .java à compiler

```bash
cd gestionNOZ
javac @compile.list -d bin
```

**Ou manuellement** (si compile.list est vide) :
```bash
javac -cp "jar/poi-bin-5.2.3/lib/*:bin" \
      -d bin \
      app/*.java \
      app/ihm/**/*.java \
      app/metier/**/*.java \
      app/securite/*.java
```

### 2. Exécuter

#### Mode Solo
```bash
cd gestionNOZ
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.Controleur
```

#### Mode Serveur
```bash
cd gestionNOZ
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ServeurHTTP
```

#### Mode Client
```bash
cd gestionNOZ
java -cp "jar/poi-bin-5.2.3/lib/*:bin" app.ControleurClient
```

### 3. Scripts batch

**`run_Client.bat`** :
```batch
@echo off
cd /d %~dp0
java -cp "jar/poi-bin-5.2.3/lib/*;bin" app.ControleurClient
pause
```

**`run_SERVEUR.bat`** :
```batch
@echo off
cd /d %~dp0
java -cp "jar/poi-bin-5.2.3/lib/*;bin" app.ServeurHTTP
pause
```

### 4. Créer le JAR exécutable

**Outil** : `tools/MergeFatJar.java`

```bash
javac tools/MergeFatJar.java
java -cp tools MergeFatJar output/FuturaServer FuturaServer.jar \
  jar/poi-bin-5.2.3/lib/* bin/
java -jar output/FuturaServer.jar
```

---

## Mode de débogage

### 1. Logs console

**Pour activer** :
```java
// Dans n'importe quelle classe
System.out.println("[CLASSE] Message : " + variable);
System.err.println("[ERREUR] Details : " + exception);
```

**Convention** : 
```
[CLASSE] ... → facile à filtrer dans les logs
[ERREUR] ... → erreurs visibles
[DEBUG]  ... → info verbose
```

### 2. Debugger avec breakpoints (IntelliJ/Eclipse)

1. Ouvrir le projet en IDE
2. Menu Debug → Breakpoints → Line Breakpoint
3. Clic sur le numéro de ligne
4. Menu Debug → Debug Application
5. Poser des watches sur variables

### 3. Tokens de session

**Serveur** : chaque client reçoit un token unique après `/login`

```
POST /login {"identifiant":"PAM", "mdp":"admin"}
→ Réponse : {"token":"abc123...", "timeout":30}

Ensuite chaque requête a l'header:
X-Auth-Token: abc123...
```

**Debug** : afficher les tokens dans `ServeurHTTP.java`
```java
System.out.println("[AUTH] Token généré : " + token);
```

### 4. Vérifier le chiffrement AES

**Mode client** : impossible de lire les réponses HTTP brutes (chifffrées)

**Solution** : 
- Ajouter un interceptor de logs avant déchiffrement
- Dans `ControleurClient.get()`, avant `aes.dechiffrer()`
```java
if (encrypted != null) {
    System.out.println("[CRYPT] Response chiffré : " + encrypted.substring(0, 50) + "...");
}
```

### 5. Monitorer les threads clients

**Dans `ServeurHTTP`** :
```java
System.out.println("[THREADS] Actifs : " + Thread.activeCount());
System.out.println("[TIMEOUT] Clients : " + timeoutClients.keySet());
```

---

## FAQ Développement

### Q1 : Où stocker les configurations ?

**Réponse** : 
- Config au démarrage → `CheminApp.resoudre("app/config.json")`
- Propriétés Java → `System.getProperty("app.mode")`
- Variables d'environnement → `System.getenv("APP_DEBUG")`

### Q2 : Comment ajouter une nouvelle classe métier ?

**Réponse** :
1. Créer `app/metier/Classe.java`
2. Ajouter getters/setters
3. Implémenter `toJson()` dans `JsonSerialiser`
4. Ajouter champ `ArrayList<Classe>` dans `PlanningGlobal`
5. Ajouter méthodes `charger`, `ajouter`, `modifier`, `supprimer`

### Q3 : Comment gérer les erreurs réseau ?

**Réponse** :
- Timeout : `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))`
- Retry : boucle avec attente exponentielle
- Fallback : charger données locales en cas de déconnexion

### Q4 : Comment tester sans serveur central ?

**Réponse** : 
- Mode Solo (`Controleur`) : aucun réseau, données locales
- Mock HTTP : créer un `MockServeurHTTP` qui simule réponses

### Q5 : Le serveur se fige (hang) ?

**Réponse** :
- Verrous non libérés → toujours `finally { rwLock.xxxLock().unlock() }`
- Deadlock → vérifier l'ordre d'acquisition des verrous
- Threads bloqués → utiliser `jstack <pid>` pour voir stack traces

---

## Ressources utiles

- **Java Documentation** : https://docs.oracle.com/javase/
- **Apache POI** : https://poi.apache.org/ (lecture/écriture Excel)
- **Swing Tutorials** : https://docs.oracle.com/javase/tutorial/uiswing/
- **Java HTTP Client** : https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html
- **AES Chiffrement** : https://docs.oracle.com/javase/8/docs/api/javax/crypto/Cipher.html

---

**Dernière mise à jour** : 28/05/2026
**Version** : 1.0 - Manuel complet pour développeurs
