# Manuel d'utilisation — Planning Global Futura (Mode Serveur)

> Ce document explique comment utiliser l'application **Planning Global Futura** en mode **serveur/client** : un ordinateur central (le serveur) tourne en permanence pendant les heures de travail, et les postes des équipes (les clients) s'y connectent via le réseau.

---

## Sommaire

1. [Architecture générale](#1-architecture-générale)
2. [Démarrer le serveur le matin](#2-démarrer-le-serveur-le-matin)
3. [Arrêter le serveur le soir](#3-arrêter-le-serveur-le-soir)
4. [Connexion depuis un poste client](#4-connexion-depuis-un-poste-client)
5. [Fenêtre de contrôle du serveur (FenetreServeur)](#5-fenêtre-de-contrôle-du-serveur-fenetreserveur)
6. [Persistance des données — rien n'est perdu](#7-persistance-des-données--rien-nest-perdu)
7. [Sauvegarder la semaine](#8-sauvegarder-la-semaine)
8. [Charger une semaine précédente](#9-charger-une-semaine-précédente)
9. [Démarrer une nouvelle semaine](#10-démarrer-une-nouvelle-semaine)
10. [Fonctionnement du serveur — points clés](#11-fonctionnement-du-serveur--points-clés)
11. [Questions fréquentes](#12-questions-fréquentes)

---

## 1. Architecture générale

L'application fonctionne en **deux programmes distincts** :

| Programme | Fichier de lancement | Rôle |
|---|---|---|
| **ServeurHTTP** | `run_SERVEUR.bat` | Tourne sur un PC central, gère les données, répond aux clients |
| **ControleurClient** | `run_CLIENT.bat` | Tourne sur chaque poste utilisateur, se connecte au serveur |

```
[PC Serveur]  ←──réseau local──→  [Poste PAM]
  run_SERVEUR.bat                  run_CLIENT.bat  (identifiant : PAM)

                                 [Poste Société A]
                                  run_CLIENT.bat  (identifiant : nom société)

                                 [Poste Société B]
                                  run_CLIENT.bat  (identifiant : nom société)
```

> ⚠️ **Un seul serveur** tourne à la fois. Tous les clients s'y connectent via l'adresse IP du PC serveur.

---

## 2. Démarrer le serveur le matin

1. Sur le **PC serveur**, ouvrez le dossier du projet.
2. **Double-cliquez sur `run_SERVEUR.bat`**.
3. Une fenêtre noire (console) s'ouvre — **ne pas la fermer**.
4. La fenêtre de contrôle du serveur (FenetreServeur) apparaît automatiquement.
5. Le serveur est prêt dès que vous voyez la ligne :
   ```
   [Serveur] Démarré sur le port 8080
   ```

> 💡 Le serveur **recharge automatiquement les données** de la dernière session au démarrage. Aucune manipulation n'est nécessaire — vous reprenez exactement là où vous vous étiez arrêté.

> ⚠️ Java doit être installé. Pour vérifier, ouvrez une invite de commandes et tapez `java -version`.

---

## 3. Arrêter le serveur le soir

Les données sont **sauvegardées automatiquement en permanence** — vous pouvez fermer le serveur à tout moment sans risque de perte.

**Méthode recommandée :**

1. Dans la fenêtre de contrôle du serveur, cliquez sur **Sauvegarder** pour archiver la semaine en cours (optionnel mais conseillé).
2. Fermez simplement la fenêtre FenetreServeur (croix en haut à droite), ou tapez `quitter` dans la console.

> ⚠️ Ne fermez pas la fenêtre noire (console) directement — passez toujours par la fenêtre de contrôle ou la commande `quitter`.

---

## 4. Connexion depuis un poste client

1. Sur le poste utilisateur, **double-cliquez sur `run_CLIENT.bat`** (ou lancez `ControleurClient.exe`).
2. La fenêtre de connexion réseau apparaît.
3. Renseignez :
   - **Identifiant** : `PAM` (accès administrateur complet) ou le **nom de votre société** (accès limité à vos lots)
   - **IP du serveur** : l'adresse IP du PC serveur (affichée dans la barre en bas de FenetreServeur, ou trouvable avec `ipconfig` sur le serveur)
4. Cliquez sur **SE CONNECTER**.

> 💡 L'application se synchronise automatiquement avec le serveur toutes les secondes. Si le serveur modifie des données, tous les clients se mettent à jour automatiquement.

> ⚠️ Si le serveur n'est pas démarré, vous obtiendrez le message *"Connexion refusée — serveur démarré ?"*. Démarrez d'abord le serveur.

---

## 5. Fenêtre de contrôle du serveur (FenetreServeur)

Cette fenêtre s'ouvre automatiquement sur le PC serveur. Elle affiche :

| Indicateur | Signification |
|---|---|
| **Semaine active** | Numéro de la semaine en cours de travail |
| **Clients connectés** | Nombre de postes clients actuellement actifs |
| **Heures sup** | Indique si le mode heures supplémentaires est activé |
| **IP : xxx.xxx.x.xx** | Adresse IP à communiquer aux clients pour se connecter |
| **● Serveur actif — Port 8080** | Confirmation que le serveur tourne correctement |

### Boutons disponibles

| Bouton | Action |
|---|---|
| **Charger une semaine** | Charge les données d'une semaine archivée (dossier `S17/`, `S18/`…) |
| **Nouvelle semaine** | Importe un nouveau fichier Excel de planning |
| **Sauvegarder** | Archive les données de la semaine dans un sous-dossier par numéro |
| **Heures sup** | Active ou désactive le mode heures supplémentaires |

---

## 6. Persistance des données — rien n'est perdu

### Sauvegarde automatique (en continu)

Chaque modification effectuée par n'importe quel client (affectation, suivi de production, édition d'un lot…) est **immédiatement enregistrée** dans `app/data/courutilisation/` sur le PC serveur.

**Conséquence pratique :** si le serveur est redémarré le lendemain matin, il recharge automatiquement ces fichiers et repart exactement dans l'état de la dernière modification. Aucune donnée ne peut être perdue par une fermeture normale ou inattendue.

### Chiffrement des données

Les fichiers JSON sur le disque sont **chiffrés en AES-256**. La clé est stockée dans `secret.key` (généré automatiquement au premier démarrage). Ne supprimez pas ce fichier.

---

## 7. Sauvegarder la semaine

La sauvegarde manuelle sert à **archiver une semaine terminée** pour pouvoir la retrouver plus tard.

**Depuis FenetreServeur :**
1. Cliquez sur **Sauvegarder**.
2. Choisissez un dossier de destination (par défaut : `app/data/enregistrementparsemaine/`).
3. Entrez le numéro de semaine (ex : `19`).
4. Un sous-dossier `S19/` est créé avec une copie des fichiers JSON.

**Depuis la console headless :**
```
[Serveur] > sauvegarder
  Dossier de destination : app/data/enregistrementparsemaine
  Numéro de semaine      : 19
  Sauvegarde effectuée dans S19
```

> 💡 Faites cette sauvegarde **chaque vendredi soir** ou en fin de semaine de travail.

---

## 8. Charger une semaine précédente

Pour consulter ou reprendre les données d'une semaine archivée :

**Depuis FenetreServeur :**
1. Cliquez sur **Charger une semaine**.
2. Naviguez jusqu'au dossier de la semaine souhaitée (ex : `S17/`).
3. Confirmez — **tous les clients connectés basculeront automatiquement** sur ces données dans les 3 secondes.

> ⚠️ Le chargement remplace les données en cours. Sauvegardez d'abord la semaine actuelle si besoin.

---

## 9. Démarrer une nouvelle semaine

> ⚠️ **Sauvegardez la semaine en cours avant cette opération.**

**Depuis FenetreServeur :**
1. Cliquez sur **Nouvelle semaine**.
2. Confirmez.
3. Sélectionnez le fichier Excel du planning (lots) puis le fichier des heures ACE.
4. Les données sont importées ; les clients se synchronisent automatiquement.

---

## 10. Fonctionnement du serveur — points clés

### Sécurité et sessions

- Chaque client reçoit un **token de session** valable **4 heures** après connexion. Passé ce délai, il est redirigé vers la fenêtre de connexion.
- Après **5 tentatives de connexion échouées**, une IP est bloquée pendant 5 minutes.
- Toutes les communications entre clients et serveur sont **chiffrées en AES-256**.

### Synchronisation automatique

- Les clients interrogent le serveur **toutes les secondes** via un mécanisme de polling sur `/version`.
- Si un client PAM modifie un lot, tous les autres clients voient le changement en moins d'une seconde.

### Port réseau

- Le serveur écoute sur le **port 8080**.
- Si un pare-feu Windows est actif sur le PC serveur, autorisez le port 8080 en entrée.

### Logs

Tous les événements (connexions, modifications, erreurs) sont affichés dans la console noire du serveur. En cas de problème, c'est là qu'il faut regarder.

---

## 11. Questions fréquentes

**Le serveur ne démarre pas.**
→ Vérifiez que Java est installé (`java -version` dans une invite de commandes). Lisez le message d'erreur dans la console noire.

**Un client ne peut pas se connecter.**
→ Vérifiez que le serveur est bien démarré et que l'IP saisie est correcte. Sur le PC serveur, ouvrez une invite de commandes et tapez `ipconfig` — utilisez la valeur "Adresse IPv4" de la carte réseau active. Vérifiez aussi que le port 8080 n'est pas bloqué par le pare-feu.

**Le client affiche "Session expirée".**
→ Les tokens durent 4 heures. Relancez le client et reconnectez-vous normalement.

**J'ai fermé le serveur accidentellement, est-ce que les données sont perdues ?**
→ Non. La sauvegarde automatique est écrite à chaque modification dans `app/data/courutilisation/`. Au prochain démarrage, le serveur rechargera exactement l'état de la dernière modification.

**Plusieurs clients voient des données différentes.**
→ Le polling corrige cela en moins d'une seconde. Si le problème persiste, vérifiez la connexion réseau du poste concerné. Le client peut aussi cliquer sur le bouton **⟳** pour forcer une resynchronisation.

**Je veux utiliser le serveur sur Linux sans interface graphique.**
→ Le mode headless est détecté automatiquement. Lancez le JAR normalement ; la console textuelle remplace FenetreServeur. Voir [section 6](#6-console-en-mode-headless-sans-écran).

**Le fichier `secret.key` est absent au démarrage.**
→ Il est généré automatiquement au premier démarrage. Si vous le supprimez, les fichiers JSON existants (chiffrés avec l'ancienne clé) ne seront plus lisibles. Ne jamais supprimer ce fichier.

---

*Manuel rédigé pour les opérateurs du serveur Planning Global Futura — mode réseau serveur/client.*