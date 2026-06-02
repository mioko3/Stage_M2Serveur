# Manuel du développeur — Planning Global Futura

> Document de référence technique pour tout développeur qui reprend, maintient ou étend le projet.  
> Version actuelle : architecture serveur/client HTTP sécurisé, chiffrement AES-256.

---

## Sommaire

1. [Vue d'ensemble du projet](#1-vue-densemble-du-projet)
2. [Structure des fichiers](#2-structure-des-fichiers)
3. [Architecture logicielle](#3-architecture-logicielle)
4. [Le serveur — ServeurHTTP.java](#4-le-serveur--serveurHTTPjava)
5. [Le client — ControleurClient.java](#5-le-client--controleurchientjava)
6. [L'interface IControleur](#6-linterface-icontroleur)
7. [Sécurité](#7-sécurité)
8. [Persistance des données](#8-persistance-des-données)
9. [Référence des routes HTTP](#9-référence-des-routes-http)
10. [Synchronisation et polling](#10-synchronisation-et-polling)
11. [IHM — Fenêtres et panneaux](#11-ihm--fenêtres-et-panneaux)
12. [Compilation et lancement](#12-compilation-et-lancement)
13. [Limites connues et pistes d'amélioration](#13-limites-connues-et-pistes-damélioration)

---

## 1. Vue d'ensemble du projet

**Planning Global Futura** est une application Java de gestion de planning de production. Elle fonctionne selon un modèle **serveur/client HTTP** :

- Un PC central fait tourner `ServeurHTTP` qui expose une API REST sur le port **8082**.
- Chaque poste utilisateur fait tourner `ControleurClient` qui communique avec le serveur via HTTP.
- L'ensemble des données est **chiffré en AES-256** sur le disque et en transit après l'échange de clé.
- Le serveur peut être **arrêté chaque soir et relancé chaque matin sans aucune perte de données** grâce à la sauvegarde automatique continue dans `app/data/courutilisation/`.

### Cycle de vie quotidien

```
Matin  → run_SERVEUR.bat → ServeurHTTP démarre → recharge app/data/courutilisation/
                                                   ↓
         run_CLIENT.bat (×N) → login → token → polling toutes les 1s

Journée → modifications → autosave immédiat sur chaque POST
        → polling détecte version++ → tous les clients rechargent

Soir   → fermeture ServeurHTTP → données déjà persistées, rien n'est perdu
```

---

## 2. Structure des fichiers

```
gestionNOZ/
├── compile.list                        ← ordre de compilation javac
├── run_SERVEUR.bat                     ← lance ServeurHTTP
├── run_CLIENT.bat                      ← lance ControleurClient
├── secret.key                          ← clé AES-256 (générée au 1er démarrage, NE PAS SUPPRIMER)
│
├── app/
│   ├── CheminApp.java                  ← résolution des chemins relatifs/absolus
│   ├── IControleur.java                ← interface contrat (patron Strategy)
│   ├── ServeurHTTP.java                ← serveur HTTP + tous les handlers REST
│   ├── ControleurClient.java           ← client réseau (implémente IControleur)
│   │
│   ├── metier/
│   │   ├── PlanningGlobal.java         ← modèle métier central (lots + sociétés)
│   │   ├── lot/
│   │   │   ├── Lot.java
│   │   │   ├── Methode.java
│   │   │   └── LigneColisage.java
│   │   ├── personelle/
│   │   │   ├── Societe.java
│   │   │   └── Ace.java
│   │   ├── ficheroute/
│   │   │   ├── FicheRoute.java
│   │   │   ├── Phase.java
│   │   │   └── SuivieProd.java
│   │   └── collecte/
│   │       ├── DonneesSauvegarder.java ← lecture/écriture JSON (avec chiffrement)
│   │       ├── JsonSerialiser.java     ← sérialisation/désérialisation JSON manuel
│   │       └── ExcelReader.java        ← import fichier Excel (nouvelle semaine)
│   │
│   ├── securite/
│   │   ├── ChiffrementAES.java         ← AES-256-CBC, IV aléatoire par message
│   │   └── GestionComptes.java         ← utilisateurs + demandes de compte (JSON)
│   │
│   └── ihm/
│       ├── IhmUtils.java
│       ├── FenetrePrincipale.java
│       ├── login/
│       │   ├── FenetreConnexionClient.java
│       │   └── FenetreCreationCompte.java
│       ├── serveur/
│       │   ├── FenetreServeur.java     ← interface de contrôle du serveur
│       │   └── PanelSemaineSuivante.java
│       ├── diagrame/
│       ├── dialogue/
│       ├── ficheroute/
│       ├── gestionlot/
│       └── map/
│
└── app/data/
    ├── courutilisation/
    │   ├── lots.json                   ← données courantes (chiffrées AES)
    │   └── societes.json
    ├── semaine_suivante/
    │   ├── lots.json
    │   └── societes.json
    ├── enregistrementparsemaine/
    │   ├── S17/
    │   ├── S18/
    │   └── ...
    └── pastouche/
        └── methodes/                   ← fichiers PDF des méthodes
```

---

## 3. Architecture logicielle

### Patron Strategy — IControleur

L'IHM ne connaît **jamais** la nature du contrôleur utilisé. Elle manipule uniquement `IControleur`.

```
FenetrePrincipale
       │
       ▼
  IControleur (interface)
   ┌───────────┴────────────┐
   │                        │
Controleur              ControleurClient
(mode solo,             (mode réseau,
 accès direct           HTTP vers
 à PlanningGlobal)      ServeurHTTP)
```

Cela permet d'ajouter un mode démo, un mode test, ou un second protocole réseau sans toucher à l'IHM.

### Flux de démarrage client

```
FenetreConnexionClient
  └─ saisit IP / identifiant / mot de passe
       └─ POST /login
            └─ reçoit { "token": "...", "accesPAM": true/false }
                 └─ new ControleurClient(ip, id, pam, token)
                      ├─ Thread fond : chargerDepuisServeur()  → GET /lots + /societes
                      ├─ Thread fond : recupererCle()          → GET /cle  → aes activé
                      ├─ SwingUtilities.invokeLater → new FenetrePrincipale(this)
                      └─ demarrerPolling()           → GET /version toutes les 1s
```

### Flux de démarrage serveur

```
ServeurHTTP()
  ├─ ChiffrementAES.chargerOuCreer("secret.key")
  ├─ DonneesSauvegarder.charger(metier, "app/data/courutilisation")
  ├─ HttpServer.create(port 8082)  → enregistrement de tous les handlers
  ├─ Thread session-cleaner (nettoyage tokens expirés toutes les heures)
  └─ SwingUtilities.invokeLater → new FenetreServeur(this)
     (ou menuConsole() si headless)
```

---

## 4. Le serveur — ServeurHTTP.java

### Constantes importantes

| Constante | Valeur | Rôle |
|---|---|---|
| `PORT` | `8082` | Port d'écoute HTTP |
| `TOKEN_TTL_MS` | `4h` | Durée de vie d'un token de session |
| `MAX_ECHECS` | `5` | Tentatives login avant blocage IP |
| `BLOCAGE_MS` | `5 min` | Durée du blocage après trop de tentatives |
| `TIMEOUT_CLIENT_MS` | `10s` | Délai d'inactivité d'un client |

### Gestion de la concurrence

Toutes les modifications du modèle métier (`PlanningGlobal`) passent par un **verrou** (`ReadWriteLock` ou `synchronized(verrou)`). Les lectures utilisent le `readLock`, les écritures le `writeLock`. L'exécuteur HTTP utilise un pool de **8 threads** (`Executors.newFixedThreadPool(8)`).

### Numéro de version

`versionDonnees` est un `volatile long` initialisé à `System.currentTimeMillis()`. Il est incrémenté (mis à jour) à chaque écriture. Le handler `/version` l'expose aux clients pour déclencher le polling.

### Autosauvegarde

Chaque handler qui modifie des données appelle en fin de traitement `savDonnees.sauvegarderLots(...)` et/ou `savDonnees.sauvegarderSocietes(...)` dans `app/data/courutilisation/`. C'est ce mécanisme qui garantit la **zéro perte de données** au redémarrage.

### Mode headless

Si `GraphicsEnvironment.isHeadless()` est vrai (serveur Linux sans écran), `FenetreServeur` n'est pas instanciée et `menuConsole()` prend le relais avec une interface texte en boucle.

---

## 5. Le client — ControleurClient.java

### Règle fondamentale

> **Cet objet est jetable.** À chaque déconnexion, créer une nouvelle instance. Ne jamais réutiliser un `ControleurClient` après déconnexion.

### Threading

| Thread | Rôle |
|---|---|
| Thread Swing (EDT) | IHM, interactions utilisateur, appels `IControleur` |
| Thread de fond (démarrage) | `chargerDepuisServeur()` + `recupererCle()` — bloquants |
| Thread `polling-serveur` | `GET /version` toutes les 1s, daemon |

Les champs `aes`, `versionLocale`, `desynchronise`, `pollingActif` sont `volatile` pour assurer la visibilité inter-threads sans synchronisation lourde.

### Stratégie optimiste

Pour chaque modification (ex. `ajouterLot`), le client :
1. Met à jour **immédiatement** sa copie locale.
2. Rafraîchit **immédiatement** la fenêtre (feedback instantané).
3. Envoie la requête HTTP en **arrière-plan** (thread séparé).
4. Remplace la copie locale par la réponse du serveur quand elle arrive.

### Mode désynchronisé (PAM uniquement)

Permet à PAM de préparer une semaine future pendant que les autres travaillent sur la semaine courante. En mode désynchronisé :
- `desynchronise = true`
- Le polling est suspendu (boucle active mais sans action).
- Les modifications sont sauvegardées localement via `savLocal`.
- À la resynchronisation, le serveur écrase les modifications locales. **C'est volontaire.**

### Gestion des erreurs réseau

- Timeout de connexion : 10s (`HttpClient.connectTimeout`).
- Après 3 échecs consécutifs de polling : affichage d'une alerte (une seule fois), `pollingActif = false`.
- Réponse HTTP 401 : token expiré → `gererDeconnexion()` → retour à `FenetreConnexionClient`.

---

## 6. L'interface IControleur

Toutes les méthodes publiques de l'application passent par cette interface. Voici les groupes fonctionnels :

### Accès aux données
```java
ArrayList<Societe> getSocietes()
ArrayList<Lot>     getLots()
boolean            isAccesPAM()
boolean            isPollingActif()
```

### Gestion des lots
```java
void ajouterLot(Lot lot)
void ajouterLot(int numCDE, String typo, ...)   // création depuis formulaire
void supprimerLot(Lot lot)
void modifierLot(Lot lot, ...)                   // champs de base (DialogEditLot)
void modifierLotComplet(Lot lot, ...)             // tous les champs (CarteLot)
void modifierPhase(Lot lot, ...)
void marquerLotTermine(Lot lot)
void commencerLot(Lot lot)
void annulerLot(Lot lot)
```

### Affectation
```java
boolean affecterLot(Lot lot, Societe soc, Ace ace)
void    desaffecterLot(Lot lot)
```

### Sociétés / ACE
```java
void    modifierSociete(Societe soc, ...)
boolean mettreAJourAces(Societe soc, List<Ace> nouvellesAces)
void    nouvelleHeurePourSociete(int semaine)
void    semaineSup()
```

### Suivi production
```java
void mettreAJourSuiviProd(Lot lot, int nbPieceEtiq, int nbPieceRepart)
```

### Persistance
```java
void sauvegarderDonnees(String cheminDossier, String semaine)
void chargerDonnees(String chemin) throws IOException
void nouveaux()       // import Excel (PAM uniquement)
void autoSauvegarde()
```

### Recherche / Fiche de route
```java
Societe        getSocieteDuLot(Lot lot)
Ace            getAceDuLot(Lot lot)
ArrayList<Ace> getTouteAces()
FicheRoute     genererFicheRoute(Societe societe)
```

> **Note sur `modifierLot` vs `modifierLotComplet`** : deux signatures coexistent pour maintenir la rétrocompatibilité. `modifierLot` est appelé par `DialogEditLot` (champs administratifs). `modifierLotComplet` est appelé par `CarteLot` et ajoute les champs logistiques (`formatCarton`, `collisage`, `distribution`, etc.).

---

## 7. Sécurité

### Authentification par token

1. `POST /login` valide identifiant + mot de passe via `GestionComptes`.
2. En cas de succès, le serveur génère un token aléatoire 256 bits (`SecureRandom`) et crée une `SessionInfo`.
3. Le token est envoyé dans l'en-tête `X-Auth-Token` de chaque requête suivante.
4. Toutes les routes sauf `/login` et `/creer-compte` vérifient ce token via `exigerToken(ex)`.
5. Les tokens expirent après **4 heures**. Un thread daemon les nettoie toutes les heures.

### Rate limiting

Après **5 tentatives de connexion échouées** depuis la même IP, cette IP est bloquée **5 minutes**. Les compteurs `loginEchecs` et `loginBlocage` sont des `ConcurrentHashMap`.

### Chiffrement AES-256-CBC

**Sur le disque :** `DonneesSauvegarder` chiffre les JSON avant écriture et les déchiffre à la lecture via `ChiffrementAES`. La clé est dans `secret.key` (Base64, AES-256 bits).

**En transit :** après l'échange de clé (`GET /cle`), tous les corps HTTP sont chiffrés. Le client chiffre les corps POST, le serveur chiffre les réponses.

**Format d'un message chiffré :**
```
Base64( IV[16 octets] || données_AES_CBC )
```
L'IV est aléatoire à chaque message (sécurité renforcée, pas de réutilisation d'IV).

**Migration transparente :** si les fichiers JSON existent en clair (avant l'activation du chiffrement), `DonneesSauvegarder.lire()` les détecte (commence par `[` ou `{`), les lit, puis les réécrit immédiatement chiffrés.

### Gestion des comptes

`GestionComptes` (singleton) gère un fichier `app/data/courutilisation/comptes.json` (non exposé) contenant :
- la liste des utilisateurs (identifiant, mot de passe, flag `accesPAM`)
- les demandes de compte en attente (`statut: "attente" | "approuve" | "refuse"`)

Les routes `/admin/demandes`, `/admin/demandes/approuver`, `/admin/demandes/refuser` sont réservées aux sessions avec `accesPAM = true`.

### Protection path traversal

Le handler `/sauvegarder` rejette tout chemin contenant `..` avant de créer les dossiers de destination.

---

## 8. Persistance des données

### Principe

```
Modification reçue par ServeurHTTP
  └─ mise à jour de PlanningGlobal (en mémoire)
       └─ savDonnees.sauvegarderLots(...)       → app/data/courutilisation/lots.json
       └─ savDonnees.sauvegarderSocietes(...)   → app/data/courutilisation/societes.json
       └─ versionDonnees = System.currentTimeMillis()
```

Au prochain démarrage du serveur, `charger()` relit ces deux fichiers et repart de l'état exact de la dernière modification.

### Format JSON

La sérialisation est **entièrement manuelle** (pas de bibliothèque externe). `JsonSerialiser` contient des méthodes `deserialiserLots(String json)`, `deserialiserSocietes(String json, lots)`, `serialiserLots(...)`, etc. Les helpers `extraireString`, `extraireBloc`, `extraireObjets` parsent le JSON caractère par caractère.

> ⚠️ En cas d'évolution du modèle métier, mettre à jour **simultanément** `construireJsonLots` (dans `DonneesSauvegarder`) et `deserialiserLots` (dans `JsonSerialiser`).

### Structure des dossiers de données

| Dossier | Contenu |
|---|---|
| `app/data/courutilisation/` | Semaine en cours — écrasé à chaque modification |
| `app/data/semaine_suivante/` | Préparation PAM de la semaine N+1 |
| `app/data/enregistrementparsemaine/S17/` | Archive semaine 17 (manuel via bouton Sauvegarder) |

### secret.key

Généré automatiquement au premier démarrage si absent. **Supprimer ce fichier rend illisibles tous les fichiers JSON chiffrés existants.** À sauvegarder séparément du projet (ne pas versionner dans Git).

---

## 9. Référence des routes HTTP

Toutes les routes sauf `/login` et `/creer-compte` exigent le header `X-Auth-Token`.

### Authentification

| Méthode | Route | Corps / Paramètre | Réponse | Notes |
|---|---|---|---|---|
| POST | `/login` | `{identifiant, motDePasse}` | `{token, accesPAM}` | Public |
| POST | `/creer-compte` | `{identifiant, motDePasse}` | `{ok}` | Public |
| GET | `/cle` | — | Base64 clé AES | Token requis |

### Administration (PAM uniquement)

| Méthode | Route | Réponse |
|---|---|---|
| GET | `/admin/demandes` | `{demandes:[...]}` |
| POST | `/admin/demandes/approuver` | `{ok}` |
| POST | `/admin/demandes/refuser` | `{ok}` |

### Lots

| Méthode | Route | Notes |
|---|---|---|
| GET | `/lots` | Retourne tous les lots (chiffrés) |
| POST | `/lots/ajouter` | |
| POST | `/lots/supprimer` | `{numCDE}` |
| POST | `/lots/modifier` | Champs de base |
| POST | `/lots/affecter` | `{numCDE, societe, ace}` — retourne lots + sociétés |
| POST | `/lots/desaffecter` | `{numCDE}` — retourne lots + sociétés |
| POST | `/lots/suiviprod` | `{numCDE, nbPieceEtiq, nbPieceRepart}` |
| POST | `/lots/commencer` | `{numCDE}` |
| POST | `/lots/annuler` | `{numCDE}` |
| POST | `/lots/terminer` | `{numCDE}` |
| POST | `/lots/phase` | `{numCDE, preTri, surPiste, ...}` |
| POST | `/lots/lignecolisage/ajouter` | |
| POST | `/lots/lignecolisage/supprimer` | |

### Sociétés / ACE

| Méthode | Route | Notes |
|---|---|---|
| GET | `/societes` | |
| POST | `/societes/modifier` | |
| POST | `/aces/modifier` | |
| POST | `/aces/mettreajour` | Remplace la liste des ACE d'une société |

### Semaine suivante

| Méthode | Route | Notes |
|---|---|---|
| GET | `/semaine-suivante` | |
| POST | `/semaine-suivante/sauvegarder` | |
| POST | `/semaine-suivante/basculer` | PAM uniquement — bascule S+1 → courante |

### Système

| Méthode | Route | Notes |
|---|---|---|
| GET | `/version` | `{v, heureSup}` — polling |
| POST | `/sauvegarder` | `{chemin, semaine}` — archive manuelle |
| POST | `/nouvelleheure` | PAM uniquement |
| POST | `/semainesup` | |
| POST | `/autosave/lots` | Déclenché par le client toutes les N secondes |
| POST | `/autosave/societes` | |
| GET | `/ficheroute/{nomSociete}` | |

### Routes bloquées (403)

`/charger` et `/nouveaux` retournent toujours 403 depuis un client. Ces actions sont réservées au serveur (boutons de `FenetreServeur`).

---

## 10. Synchronisation et polling

### Mécanisme

Chaque client lance au démarrage un thread daemon `polling-serveur` qui appelle `GET /version` toutes les **1000 ms**.

La réponse contient :
```json
{ "v": "1717500000000", "heureSup": false }
```

- Si `v != versionLocale` → `chargerDepuisServeur()` est appelé, `versionLocale` est mise à jour.
- Si `heureSup` a changé → `PlanningGlobal.estHeureSup` est mis à jour.

### Conséquence pratique

Une modification effectuée par un client est visible par tous les autres en **moins de 2 secondes** (1s de polling + temps de chargement).

### Gestion de la perte de connexion

Après **3 échecs consécutifs** de `GET /version`, le client :
1. Affiche une alerte modale (une seule fois).
2. Passe `pollingActif = false`.
3. L'IHM reflète visuellement l'état déconnecté (indicateur dans `FenetrePrincipale`).

Le polling continue de tourner pour détecter le retour du serveur.

---

## 11. IHM — Fenêtres et panneaux

### Côté serveur

| Classe | Rôle |
|---|---|
| `FenetreServeur` | Tableau de bord : IP, port, clients actifs, semaine, boutons d'action |
| `PanelSemaineSuivante` | Gestion de la semaine N+1 (PAM) |

### Côté client

| Classe | Rôle |
|---|---|
| `FenetreConnexionClient` | Saisie IP / identifiant / mot de passe, appel POST /login |
| `FenetreCreationCompte` | Demande de création de compte (POST /creer-compte) |
| `FenetrePrincipale` | Fenêtre principale, onglets, méthode `rafraichirTout()` |
| `PanelLots` | Tableau filtrable des lots |
| `PanelSocietes` | Vue des sociétés et de leurs ACE |
| `PanelAffectation` | Drag-and-drop affectation lot ↔ société/ACE |
| `PanelDiagrame` | Vue Gantt |
| `PanelFicheRoute` | Récapitulatif par société (`FicheRoute`) |
| `PanelMap` | Vue carte des emplacements |
| `CarteLot` | Carte détaillée d'un lot (champs logistiques) |
| `DialogAjoutLot` | Formulaire création lot |
| `DialogEditLot` | Formulaire modification lot (champs de base) |
| `DialogEditSociete` | Formulaire modification société + ACE |

### Convention de rafraîchissement

`FenetrePrincipale.rafraichirTout()` est la **seule méthode** à appeler pour mettre à jour l'affichage. Elle doit être appelée depuis le thread Swing (`SwingUtilities.invokeLater`). Les contrôleurs l'appellent après chaque modification locale (stratégie optimiste) et après réception de la réponse serveur.

---

## 12. Compilation et lancement

### Prérequis

- Java 11 minimum (utilise `java.net.http.HttpClient`).
- Aucune dépendance externe (JSON, HTTP, AES : tout est implémenté manuellement ou via le JDK).

### Compilation

L'ordre de compilation est défini dans `compile.list`. Compiler avec :

```bat
javac -encoding UTF-8 -cp . @compile.list -d out/
```

### Lancement serveur

```bat
java -cp out app.ServeurHTTP
```

Ou via `run_SERVEUR.bat` (double-clic).

### Lancement client

```bat
java -cp out app.ControleurClient
```

Ou via `run_CLIENT.bat` (double-clic).

### Lancement headless (Linux sans écran)

```bash
java -Djava.awt.headless=true -cp out app.ServeurHTTP
```

`FenetreServeur` n'est pas instanciée ; la console textuelle (`menuConsole()`) prend le relais.

### Premier démarrage

Au premier lancement du serveur :
- `secret.key` est généré automatiquement.
- Les dossiers `app/data/courutilisation/`, `app/data/semaine_suivante/`, `app/data/enregistrementparsemaine/` sont créés.
- Aucun lot ni société n'existe : importer via bouton **Nouvelle semaine** (fichier Excel).

---

## 13. Limites connues et pistes d'amélioration

### Conflits d'écriture simultanée

**Problème :** si deux clients modifient le même lot à la même seconde, la **dernière écriture gagne** et les modifications du premier sont silencieusement écrasées.

**Solution envisageable :** ajouter un champ `dateModification` (timestamp) dans chaque lot. Le serveur compare avant d'appliquer et renvoie un code 409 (Conflict) si la version locale du client est dépassée.

### Charge du polling

**Problème :** avec 100 clients pollant toutes les secondes, le serveur reçoit 100 requêtes/seconde sur `/version`.

**Solution envisageable :** remplacer le polling HTTP par des **Server-Sent Events (SSE)** ou des **WebSockets** (Java 11+ supporte SSE via `HttpServer`). Chaque client maintient une connexion longue durée ; le serveur pousse les changements à la demande.

### Parsing JSON manuel

**Problème :** `JsonSerialiser` est fragile face aux valeurs contenant des guillemets ou des caractères d'échappement inhabituels.

**Solution envisageable :** intégrer une bibliothèque légère comme `org.json` ou `Gson` (ajouter le JAR dans le classpath, modifier `compile.list`).

### Mot de passe en clair dans comptes.json

**Problème :** les mots de passe sont actuellement stockés en texte brut (même si le fichier est chiffré AES).

**Solution envisageable :** hacher les mots de passe avec `BCrypt` ou `PBKDF2` avant stockage. La vérification se fait alors par comparaison de hash.

### Absence de logs persistants

**Problème :** les logs serveur (`System.out.println`) ne sont visibles que dans la console courante et perdus à la fermeture.

**Solution envisageable :** rediriger vers un fichier `logs/serveur_YYYY-MM-DD.log` avec rotation journalière (via `java.util.logging` ou redirection de `PrintStream`).

---

*Manuel rédigé pour les développeurs du projet Planning Global Futura.*  
*Dernière mise à jour : juin 2026.*