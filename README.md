# Manuel d'utilisation — Planning Global Futura (Mode Serveur)

> Ce document explique comment utiliser l'application **Planning Global Futura** en mode **serveur/client** : un ordinateur central (le serveur) tourne pendant les heures de travail, et les postes des équipes (les clients) s'y connectent via le réseau local.

---

## Sommaire

1. [Architecture générale](#1-architecture-générale)
2. [Prérequis](#2-prérequis)
3. [Démarrer le serveur le matin](#3-démarrer-le-serveur-le-matin)
4. [Arrêter le serveur le soir](#4-arrêter-le-serveur-le-soir)
5. [Connexion depuis un poste client](#5-connexion-depuis-un-poste-client)
6. [Fenêtre de contrôle du serveur (FenetreServeur)](#6-fenêtre-de-contrôle-du-serveur-fenetreserveur)
7. [Persistance des données — rien n'est perdu](#7-persistance-des-données--rien-nest-perdu)
8. [Sauvegarder la semaine](#8-sauvegarder-la-semaine)
9. [Charger une semaine précédente](#9-charger-une-semaine-précédente)
10. [Démarrer une nouvelle semaine](#10-démarrer-une-nouvelle-semaine)
11. [Fonctionnement du serveur — points clés](#11-fonctionnement-du-serveur--points-clés)
12. [Questions fréquentes](#12-questions-fréquentes)

---

## 1. Architecture générale

L'application fonctionne en **deux programmes distincts** :

|     Programme      |      Fichier       |                           Rôle                                 |
|--------------------|--------------------|----------------------------------------------------------------|
|  **FuturaServer**  | `FuturaServer.exe` | Tourne sur un PC central, gère les données, répond aux clients |
|  **FuturaClient**  | `FuturaClient.exe` |  Tourne sur chaque poste utilisateur, se connecte au serveur   |

```
[PC Serveur]  ←──réseau local──→  [Poste PAM]
FuturaServer.exe                  FuturaClient.exe  (identifiant : PAM)

                                  [Poste Société A]
                                  FuturaClient.exe  (identifiant : nom société)

                                  [Poste Société B]
                                  FuturaClient.exe  (identifiant : nom société)
```

> ⚠️ **Un seul serveur** tourne à la fois. Tous les clients s'y connectent via l'adresse IP du PC serveur sur le **port 8082**.

---

## 2. Prérequis

- **Aucune installation requise** — Java est embarqué directement dans les `.exe`.
- Les postes clients doivent être sur le **même réseau local** que le serveur.
- Le **port 8082** doit être ouvert dans le pare-feu Windows du PC serveur.

---

## 3. Démarrer le serveur le matin

1. Sur le **PC serveur**, ouvrez le dossier `FuturaServer/`.
2. **Double-cliquez sur `FuturaServer.exe`**.
3. La fenêtre de contrôle du serveur (FenetreServeur) apparaît.
4. Le serveur est prêt dès que l'indicateur affiche :
   ```
   ● Serveur actif — Port 8082
   ```

> 💡 Le serveur **recharge automatiquement les données** de la dernière session au démarrage. Vous reprenez exactement là où vous vous étiez arrêté — aucune manipulation nécessaire.

> 🔐 Au **tout premier démarrage**, un fichier `secret.key` est généré automatiquement dans le dossier `FuturaServer/`. Ce fichier chiffre toutes les données. **Ne jamais le supprimer ni le déplacer.**

---

## 4. Arrêter le serveur le soir

1. Dans la **FenetreServeur**, cliquez sur le bouton **Quitter**.
2. Confirmez l'arrêt si une boîte de dialogue apparaît.
3. L'application se ferme.

> ✅ Toutes les données sont déjà sauvegardées en temps réel dans `app/data/courutilisation/`. La fermeture du serveur **ne cause aucune perte de données**.

> ⚠️ Évitez d'éteindre le PC serveur brutalement sans fermer l'application au préalable.

---

## 5. Connexion depuis un poste client

1. Sur le **poste client**, ouvrez le dossier `FuturaClient/`.
2. **Double-cliquez sur `FuturaClient.exe`**.
3. Dans la fenêtre de connexion :
   - **Adresse IP** : saisissez l'IP du PC serveur (visible dans FenetreServeur).
   - **Identifiant** : votre nom d'utilisateur ou nom de société.
   - **Mot de passe** : votre mot de passe.
4. Cliquez sur **SE CONNECTER**.

> 💡 L'application se synchronise automatiquement avec le serveur toutes les secondes. Si le serveur modifie des données, tous les clients se mettent à jour automatiquement.

> ⚠️ Si le serveur n'est pas démarré, vous obtiendrez *"Connexion refusée — serveur démarré ?"*. Démarrez d'abord le serveur.

> 🔑 Les sessions expirent après **4 heures**. Si le message "Session expirée" apparaît, relancez le client et reconnectez-vous normalement.

---

## 6. Fenêtre de contrôle du serveur (FenetreServeur)

Cette fenêtre s'ouvre automatiquement sur le PC serveur. Elle affiche :

|            Indicateur           |                    Signification                       |
|---------------------------------|--------------------------------------------------------|
|        **Semaine active**       |         Numéro de la semaine en cours de travail       |
|      **Clients connectés**      |        Nombre de postes clients actuellement actifs    |
|          **Heures sup**         |  Indique si le mode heures supplémentaires est activé  |
|       **IP : xxx.xxx.x.xx**     | Adresse IP à communiquer aux clients pour se connecter |
| **● Serveur actif — Port 8082** |     Confirmation que le serveur tourne correctement    |

### Boutons disponibles

|          Bouton         |                                 Action                              |
|-------------------------|---------------------------------------------------------------------|
| **Charger une semaine** | Charge les données d'une semaine archivée (dossier `S17/`, `S18/`…) |
|  **Nouvelle semaine**   |            Importe un nouveau fichier Excel de planning             |
|     **Sauvegarder**     |  Archive les données de la semaine dans un sous-dossier par numéro  |
|      **Heures sup**     |          Active ou désactive le mode heures supplémentaires         |
|        **Quitter**      |              Arrête proprement le serveur                           |

---

## 7. Persistance des données — rien n'est perdu

### Sauvegarde automatique (en continu)

Chaque modification effectuée par n'importe quel client (affectation, suivi de production, édition d'un lot…) est **immédiatement enregistrée** dans `app/data/courutilisation/` sur le PC serveur.

**Conséquence pratique :** si le serveur est redémarré le lendemain matin, il recharge automatiquement ces fichiers et repart exactement dans l'état de la dernière modification. **Aucune donnée ne peut être perdue** par une fermeture normale ou inattendue.

### Chiffrement des données

Les fichiers JSON sur le disque sont **chiffrés en AES-256**. La clé est stockée dans `secret.key` (généré automatiquement au premier démarrage).

> ⛔ **Ne jamais supprimer `secret.key`.** Sans ce fichier, les données chiffrées existantes sont irrécupérables.

### Structure des dossiers de données

```
FuturaServer/
├── FuturaServer.exe
├── secret.key                          ← Clé de chiffrement (NE PAS SUPPRIMER)
└── app/data/
    ├── courutilisation/                ← Session en cours (rechargée au démarrage)
    │   ├── lots.json                   ← Tous les lots (chiffré AES-256)
    │   └── societes.json               ← Sociétés et ACEs (chiffré AES-256)
    ├── enregistrementparsemaine/       ← Archives manuelles par semaine
    │   ├── S17/
    │   ├── S18/
    │   └── ...
    ├── config.json                     ← Comptes utilisateurs
    └── pastouche/methodes/             ← Fiches méthodes PDF
```

---

## 8. Sauvegarder la semaine

La sauvegarde manuelle sert à **archiver une semaine terminée** pour pouvoir la retrouver plus tard.

**Depuis FenetreServeur :**
1. Cliquez sur **Sauvegarder**.
2. Choisissez un dossier de destination (par défaut : `app/data/enregistrementparsemaine/`).
3. Entrez le numéro de semaine (ex : `19`).
4. Un sous-dossier `S19/` est créé avec une copie des fichiers JSON.

> 💡 Cette opération ne perturbe pas le travail en cours. Les clients restent connectés.

---

## 9. Charger une semaine précédente

**Depuis FenetreServeur :**
1. Cliquez sur **Charger une semaine**.
2. Naviguez vers le dossier archivé (ex : `app/data/enregistrementparsemaine/S17/`).
3. Confirmez. Le serveur recharge les données de cette semaine.

> ⚠️ Cette action **remplace les données en cours**. Faites d'abord une sauvegarde si nécessaire.

---

## 10. Démarrer une nouvelle semaine

**Depuis FenetreServeur :**
1. Cliquez sur **Nouvelle semaine**.
2. Sélectionnez le fichier Excel (`.xlsx`) exporté depuis le système ERP.
3. Le serveur importe les nouveaux lots et réinitialise le planning.

> 💡 Les sociétés et ACEs existants sont conservés. Seuls les lots sont mis à jour depuis le fichier Excel.

---

## 11. Fonctionnement du serveur — points clés

|          Aspect             |                      Détail                           |
|-----------------------------|-------------------------------------------------------|
|         **Port**            |                     8082 (TCP)                        |
|        **Protocole**        |                     HTTP/JSON                         |
|      **Authentification**   |                 Token valide 4 heures                 |
| **Synchronisation clients** |               Polling toutes les secondes             |
|       **Sauvegarde**        |              Immédiate à chaque modification          |
|       **Chiffrement**       |             AES-256 sur tous les fichiers JSON        |
|           **Java**          | Embarqué dans le `.exe` — aucune installation requise |

---

## 12. Questions fréquentes

**Le serveur ne démarre pas.**
→ Vérifiez que le dossier `FuturaServer/` est complet (notamment le dossier `app/data/`). Regardez les messages d'erreur dans la console qui s'ouvre brièvement.

**Un client ne peut pas se connecter.**
→ Vérifiez que le serveur est bien démarré et que l'IP saisie est correcte. L'IP est affichée directement dans FenetreServeur. Vérifiez aussi que le port 8082 n'est pas bloqué par le pare-feu du PC serveur.

**Le client affiche "Session expirée".**
→ Les sessions durent 4 heures. Fermez et relancez `FuturaClient.exe`, puis reconnectez-vous.

**J'ai fermé le serveur accidentellement, est-ce que les données sont perdues ?**
→ Non. La sauvegarde automatique est écrite à chaque modification dans `app/data/courutilisation/`. Au prochain démarrage, le serveur rechargera exactement l'état de la dernière modification.

**Plusieurs clients voient des données différentes.**
→ La synchronisation corrige cela en moins d'une seconde. Si le problème persiste, vérifiez la connexion réseau du poste concerné.

**Le fichier `secret.key` est absent.**
→ Il est généré automatiquement au premier démarrage. Si vous le supprimez, les fichiers JSON existants (chiffrés avec l'ancienne clé) ne seront plus lisibles. **Ne jamais supprimer ce fichier.**

**Je dois déplacer le serveur sur un autre PC.**
→ Copiez l'intégralité du dossier `FuturaServer/` (y compris `secret.key` et `app/data/`). Sans `secret.key`, les données chiffrées seront illisibles sur le nouveau PC.

---

*Manuel rédigé pour les opérateurs du serveur Planning Global Futura — mode réseau serveur/client.*