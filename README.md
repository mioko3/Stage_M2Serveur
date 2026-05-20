# Manuel d'utilisation — Planning Global Futura

> Ce document explique comment utiliser l'application **Planning Global Futura** pas à pas.

---

## Sommaire

1. [Lancer l'application](#1-lancer-lapplication)
2. [Connexion](#2-connexion)
3. [Comprendre l'écran principal](#3-comprendre-lécran-principal)
4. [Importer des lots depuis Excel](#4-importer-des-lots-depuis-excel)
5. [Onglet Affectation — attribuer un lot à une société](#5-onglet-affectation--attribuer-un-lot-à-une-société)
6. [Onglet Fiches de Route — suivi de production](#6-onglet-fiches-de-route--suivi-de-production)
7. [Onglet Liste des lots — consulter et modifier les lots](#7-onglet-liste-des-lots--consulter-et-modifier-les-lots)
8. [Onglet Sociétés & heures — gérer les sociétés](#8-onglet-sociétés--heures--gérer-les-sociétés)
9. [Onglet Carte entrepôt — visualiser les emplacements](#9-onglet-carte-entrepôt--visualiser-les-emplacements)
10. [Sauvegarder et charger les données](#10-sauvegarder-et-charger-les-données)
11. [Remettre à zéro (nouvelle semaine)](#11-remettre-à-zéro-nouvelle-semaine)
12. [Questions fréquentes](#12-questions-fréquentes)
13. [Idées d'améliorations futures](#13-idées-daméliorations-futures)

---

## 1. Lancer l'application

1. Ouvrez le dossier du projet.
2. **Double-cliquez sur le fichier `run.bat`** (sous Windows).
3. L'application compile et s'ouvre automatiquement. Aucune installation n'est nécessaire.

> ⚠️ Si une fenêtre noire (invite de commandes) s'ouvre, ne pas la fermer — elle est nécessaire au fonctionnement de l'application.

> ⚠️ Java doit être installé sur votre ordinateur. Si rien ne se passe, vérifiez en tapant `java -version` dans l'invite de commandes.

---

## 2. Connexion

Au lancement, une **fenêtre de connexion** apparaît avant l'application.

1. Saisissez l'identifiant : **`PAM`**
2. Choisissez la source des données :
   - **Utiliser les fichiers courants (JSON)** : charge les données de la dernière session (recommandé en temps normal)
   - **Charger depuis un export Excel** : importe les lots depuis un nouveau fichier Excel — deux fichiers vous seront demandés (lots + heures ACE)
3. Cliquez sur **SE CONNECTER**.

> 💡 Si vous choisissez Excel et annulez la sélection de fichier, l'application bascule automatiquement sur les données JSON.

---

## 3. Comprendre l'écran principal

L'écran se divise en deux zones :

- **En haut** : une barre d'information affiche en temps réel le nombre de lots, les heures restantes sur les lots non affectés, le nombre de sociétés, les lots affectés et les heures disponibles au total.
- **Au centre** : cinq onglets permettent d'accéder aux différentes fonctions.

| Onglet | À quoi ça sert |
|---|---|
| ⊕ Affectation | Attribuer des lots à des sociétés et des ACE |
| 📋 Fiches de Route | Suivi de production détaillé par société ou par ACE |
| ☰ Liste des lots | Consulter, filtrer, modifier ou supprimer des lots |
| ▤ Sociétés & heures | Voir et modifier les sociétés et leurs heures disponibles |
| 🗺 Carte entrepôt | Visualiser les emplacements des lots dans l'entrepôt |

En haut à gauche, le menu **Fichier** permet de sauvegarder, charger ou réinitialiser les données.

Le bouton **⟳** dans l'en-tête force un rafraîchissement complet de tous les panneaux.

---

## 4. Importer des lots depuis Excel

### Au démarrage (recommandé pour une nouvelle semaine)

Choisissez **"Charger depuis un export Excel"** dans la fenêtre de connexion. L'application vous demandera successivement :
1. Le fichier des **lots** (fichier planning XLSX/XLSM)
2. Le fichier des **heures ACE** (peut être le même fichier)

### En cours de session — importer de nouveaux lots

Dans l'onglet **Affectation**, cliquez sur le bouton **importer nouveau lots** en bas du panneau central. Cela ajoute les nouveaux lots sans effacer les lots déjà présents.

> ⚠️ Les lots importés depuis Excel n'ont pas d'emplacement ni de société affectés par défaut.

---

## 5. Onglet Affectation — attribuer un lot à une société

Cet onglet est le cœur de l'application. Il est divisé en trois colonnes :

- **Gauche** : liste des lots disponibles (non affectés, non bloqués, hors douane)
- **Centre** : détail du lot sélectionné + formulaire d'affectation
- **Droite** : liste des lots déjà affectés

### Comment affecter un lot

1. Cliquez sur un lot dans la **colonne de gauche** pour le sélectionner.
2. Ses informations s'affichent dans la **colonne centrale**.
3. Choisissez une **société** dans la première liste déroulante (les heures disponibles sont affichées).
4. Choisissez un **ACE** (responsable d'équipe) dans la deuxième liste déroulante.
5. Cliquez sur **▶ Affecter →**.

> ⚠️ Si la société n'a pas assez d'heures disponibles, l'affectation est bloquée et un message d'erreur s'affiche.

### Comment retirer une affectation

1. Cliquez sur un lot dans la **colonne de droite**.
2. Cliquez sur **◀ Retirer**.
3. Le lot revient dans la colonne de gauche et les heures sont restituées à la société.

### Créer un lot manuellement

Cliquez sur **+ Nouveau lot** pour ouvrir un formulaire. Les champs obligatoires sont marqués d'un `*`. Les heures sont calculées automatiquement à partir du nombre de pièces et de la cadence.

### Modifier un lot

Sélectionnez un lot et cliquez sur **✏ Modifier ce lot**. Vous pouvez modifier tous les champs, y compris l'emplacement (zone + numéro de rangée).

> 🔍 Utilisez les **barres de recherche** au-dessus de chaque tableau pour filtrer par numéro de commande, typologie ou affaire.

---

## 6. Onglet Fiches de Route — suivi de production

Cet onglet présente une vue détaillée par **société** ou par **ACE**, avec des cartes de lot interactives.

### Sous-onglet "Par Société"

1. Sélectionnez une société dans la liste déroulante.
2. Des **tuiles de statistiques** s'affichent en haut (VVS total, nb pièces, PU moyen, avancement étiquetage et valeur).
3. Un **récapitulatif par ACE** est visible sous les tuiles.
4. En dessous, les **cartes de lot** sont regroupées par ACE.

### Sous-onglet "Par ACE"

Sélectionnez un ACE pour voir uniquement ses lots avec ses statistiques propres.

### Que peut-on faire sur une carte de lot ?

Chaque carte permet de :

- **Commencer / Annuler un lot** : enregistre l'heure de début ou remet le lot à zéro
- **Cocher les phases** : Pré Tri → Sur Piste → Sortie Étiq → Tri → Fini *(les phases ne sont cochables qu'après avoir cliqué sur "Commencer")*
- **Saisir l'avancement** : nombre de pièces étiquetées et réparties
- **Renseigner la logistique** : distribution, lot à charge, format carton, collisage, méthode
- **Ajouter des lignes de colisage supplémentaires** (formats de cartons différents)
- **Saisir un commentaire** directement sur la carte

> 💡 Le bouton **🖸 Aperçu / Export** génère un résumé textuel de la fiche de route pour la société sélectionnée.

> 💡 Le bouton **👁 Voir la Méthode** ouvre le PDF de méthode associé aux lots (si disponible dans `app/data/pastouche/methodes/`).

### Couleurs des cartes

| Couleur | Signification |
|---|---|
| 🟢 Vert clair | Lot terminé (phase "Fini" cochée) |
| 🟣 Violet clair | Lot sous douane |
| 🔴 Rouge clair | Lot prioritaire (priorité ≥ 8) |
| 🟡 Jaune clair | Lot commencé (en cours) |
| ⬜ Blanc | Lot normal |

---

## 7. Onglet Liste des lots — consulter et modifier les lots

Cet onglet affiche **tous les lots** enregistrés dans l'application.

### Filtrer et rechercher

- **Filtre Statut** : affiche uniquement les lots d'un statut donné (`VA - Validé`, `BL - Bloqué`, `EP - Envoi au CP`).
- **Case "Inclure les lots sous douane"** : les lots sous douane sont masqués par défaut.
- **Champ Recherche** : filtrage instantané par numéro de commande, typologie ou affaire.

### Modifier un lot

Double-cliquez sur une ligne (ou cliquez sur **✏ Modifier**) pour ouvrir la fenêtre d'édition complète.

### Supprimer un lot

1. Sélectionnez un lot **non affecté**.
2. Cliquez sur **🗑 Supprimer** et confirmez.

> ⚠️ Pour supprimer un lot affecté, retirez d'abord son affectation dans l'onglet **Affectation**.

---

## 8. Onglet Sociétés & heures — gérer les sociétés

Tableau récapitulatif de toutes les sociétés :

| Colonne | Signification |
|---|---|
| Société | Nom de la société |
| CE | Responsable CE |
| H initiales | Heures attribuées au départ |
| H restantes | Heures encore disponibles (vert / orange / rouge) |
| % consommé | Pourcentage des heures utilisées |
| Lots | Nombre de lots affectés |
| ACE | Nombre d'équipes (ACE) |

### Modifier une société

Double-cliquez sur une ligne (ou cliquez sur **✏ Modifier**). Vous pouvez changer le nom, le CE, les heures disponibles et gérer les ACE (ajout, suppression, modification des effectifs).

> ⚠️ Vous ne pouvez pas supprimer un ACE qui a des lots affectés.

### Mettre à jour les heures depuis Excel

Cliquez sur **Nouvelle heure**, saisissez le numéro de semaine (1 à 53), puis sélectionnez le fichier Excel des heures. Les heures des sociétés sont recalculées automatiquement.

---

## 9. Onglet Carte entrepôt — visualiser les emplacements

Cet onglet affiche un **plan interactif de l'entrepôt** avec les lots positionnés par emplacement.

### Zones disponibles

| Zone | Description |
|---|---|
| A, B, C, D | Zones à rangées numérotées (ex : B21, C12) |
| LTS | Long Term Storage |
| HD | Hors Douane |

### Comment l'utiliser

- **Survolez** une cellule pour voir un résumé des lots présents (tooltip).
- **Cliquez** sur une cellule pour la sélectionner : la liste des lots s'affiche dans le panneau de droite.
- Cliquez sur un lot dans la liste pour voir **toutes ses informations détaillées** dans la zone en bas à droite.

### Code couleur des cellules

| Couleur | Signification |
|---|---|
| 🟢 Vert | Lot(s) validé(s) (VA) |
| 🔴 Rouge | Lot(s) bloqué(s) (BL) |
| 🟡 Orange | Lot(s) en attente (EP) |
| 🟣 Violet | Lot(s) sous douane |
| ⬜ Gris clair | Emplacement vide |
| 🔵 Bleu | Emplacement sélectionné |

> 💡 Un emplacement contenant plusieurs lots affiche plusieurs couleurs côte à côte.

---

## 10. Sauvegarder et charger les données

### Sauvegarde automatique

Toute modification (affectation, édition d'un lot, suivi de production…) est **enregistrée automatiquement** dans `app/data/courutilisation/`. Vos données ne sont jamais perdues en cas de fermeture inattendue.

### Sauvegarde manuelle (archivage par semaine)

1. **Fichier → 💾 Sauvegarder** (ou **Ctrl+S**)
2. Choisissez un dossier de destination.
3. Entrez le numéro de semaine (ex : `19`).
4. Un sous-dossier `S19/` est créé avec une copie des fichiers JSON.

### Charger une sauvegarde précédente

1. **Fichier → 📂 Charger une sauvegarde…** (ou **Ctrl+O**)
2. Naviguez jusqu'au dossier de la semaine souhaitée (ex : `S19/`).
3. Sélectionnez ce dossier et cliquez sur **Ouvrir**.

---

## 11. Remettre à zéro (nouvelle semaine)

> ⚠️ **Pensez à sauvegarder la semaine en cours avant de faire cette action !**

1. **Fichier → 🆕 Nouveaux fichiers JSON…** (ou **Ctrl+N**)
2. Confirmez en cliquant sur **Oui**.
3. Toutes les données sont effacées, l'application repart de zéro.

---

## 12. Questions fréquentes

**L'application ne démarre pas.**
→ Vérifiez que Java est installé. Faites un clic droit sur `run.bat` → *Ouvrir avec* → *Invite de commandes* pour voir le message d'erreur précis.

**Je ne vois pas mes lots après avoir chargé un fichier Excel.**
→ Vérifiez que le fichier est au format `.xlsx` ou `.xlsm`. Les données sont lues depuis la première feuille du classeur.

**Je ne peux pas supprimer un lot.**
→ Un lot affecté ne peut pas être supprimé directement. Retirez d'abord son affectation dans l'onglet **Affectation**, puis supprimez-le depuis **Liste des lots**.

**Les heures restantes d'une société semblent incorrectes.**
→ Allez dans **Sociétés & heures**, sélectionnez la société et cliquez sur **✏ Modifier** pour corriger le total d'heures initiales.

**Un lot n'apparaît pas dans la colonne "disponibles" de l'onglet Affectation.**
→ Les lots sous douane, bloqués (`BL`) ou déjà affectés sont masqués dans cette colonne. Utilisez l'onglet **Liste des lots** pour les consulter.

**La carte entrepôt ne montre pas un lot que j'ai placé.**
→ Cliquez sur le bouton **⟳** en haut de l'écran pour forcer le rafraîchissement, ou naviguez vers un autre onglet puis revenez sur la carte.

**J'ai fermé l'application sans sauvegarder manuellement, est-ce que j'ai perdu mes données ?**
→ Non. La sauvegarde automatique enregistre chaque modification dans `app/data/courutilisation/`. Vos données sont conservées.

---

## 13. Idées d'améliorations futures

### Historique des actions (Undo / Redo)
Implémenter le **pattern Command** pour annuler les dernières affectations ou modifications. Fortement valorisé pour démontrer la maîtrise des design patterns.

### Export PDF / Excel de la fiche de route
Générer un export imprimable de la fiche de route avec Apache PDFBox (déjà inclus dans les dépendances) ou Apache POI pour Excel.

### Détection de conflit de capacité
Ajouter un avertissement progressif quand une société dépasse 80 % de capacité, plutôt qu'un blocage uniquement à 0 heure disponible.

### Vue "planning semaine" visuelle
Créer une vue de type calendrier (lignes = sociétés, colonnes = semaines) pour visualiser la charge de travail dans le temps.

### Filtres avancés dans les tableaux
Filtrer par semaine, par plage d'heures, par présence ou absence d'emplacement, etc.

---

*Manuel rédigé pour les utilisateurs de l'application Planning Global Futura — PAM S07/2026.*