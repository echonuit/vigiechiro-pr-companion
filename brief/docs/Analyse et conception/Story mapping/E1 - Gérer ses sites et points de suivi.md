# E1 - 🌐 Gérer ses sites et points de suivi

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P1 - Déclarer un site de suivi](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)

**Portée** : permettre à l'utilisateur de déclarer ses sites de suivi (créés en amont sur le portail VigieChiro) dans l'application, avec leurs points d'écoute, pour pouvoir y rattacher ensuite les nuits importées. Inclut la création à la volée depuis l'import.

**Persona principal** : Marie (mais utilisé par tous : Karim avec ses 3 chantiers parallèles, Samuel avec ses 36+ sites).

**Pré-requis** : E0.S1 (schéma BD initialisé), E0.S2 (DAO sites/points opérationnels).

## E1.S1 - Saisir un site de suivi avec son n° de carré { #e1s1 }

**En tant que** [Marie](../Personas/Marie.md) (utilisatrice débutante)

**Je veux** déclarer un nouveau site de suivi en saisissant son n° de carré et au moins un point d'écoute

**Afin de** pouvoir y rattacher mes futures nuits d'enregistrement lors de l'import

**Critères d'acceptation** :

- [ ] Le formulaire valide que le n° de carré fait exactement 6 chiffres ([R1](../Modèle%20conceptuel/Règles%20métier.md#r1) du [modèle conceptuel](../Modèle%20conceptuel/index.md)).
- [ ] Le formulaire alerte si l'utilisateur saisit 5 chiffres pour les départements 1-9 (leading zero manquant) avec un message explicite (ex. « Pour le département 4, le n° de carré doit commencer par 0 »).
- [ ] Le nom convivial est facultatif mais recommandé (texte indicatif « ex. Étang de la Tuilière »).
- [ ] Le champ **protocole** est un menu déroulant à deux valeurs : `PointFixeStandard` (par défaut, déclenche les alertes [R3](../Modèle%20conceptuel/Règles%20métier.md#r3) / [R4](../Modèle%20conceptuel/Règles%20métier.md#r4) en cas de passage hors fenêtre) et `PointFixeRecherche` (R3 / R4 muettes, pour les campagnes recherche à dates personnalisées). Les autres protocoles VigieChiro (Pédestre, Routier, etc.) ne sont pas supportés au MVP.
- [ ] Au moins un point d'écoute est exigé pour valider le site.
- [ ] Le code de point doit faire 1 lettre majuscule + 1 chiffre ([R2](../Modèle%20conceptuel/Règles%20métier.md#r2)), validé à la saisie.
- [ ] Le site créé apparaît immédiatement dans la vue des sites après validation.
- [ ] Le site est persisté en base et survit au redémarrage de l'application.

**Parcours rattaché** : [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)<br>
**Maquettes cibles** : [M-Sites](../Maquettes/M-Sites.md) (vue listant les sites + bouton « Ajouter »), [M-Site-detail](../Maquettes/M-Site-detail.md) (formulaire de création/édition)<br>
**Dépendances** : [E0.S1](E0%20-%20Fondations%20de%20persistance.md#e0s1), [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2)<br>

---

## E1.S2 - Ajouter, modifier ou retirer des points d'écoute sur un site existant { #e1s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir gérer la liste des points d'écoute d'un site déjà déclaré (en ajouter, en renommer, en supprimer)

**Afin de** maintenir mes sites à jour si j'étends mon protocole ou si je corrige une erreur de saisie initiale

**Critères d'acceptation** :

- [ ] Depuis la fiche détail d'un site, l'utilisateur voit la liste de ses points avec un bouton « + Ajouter un point ».
- [ ] Chaque point existant a une action « Modifier » et une action « Supprimer ».
- [ ] La modification valide les mêmes règles que la création ([R2](../Modèle%20conceptuel/Règles%20métier.md#r2) : 1 lettre + 1 chiffre).
- [ ] La suppression d'un point est **bloquée** si des passages y sont rattachés, avec un message explicite (« Ce point est utilisé par 3 passages. Supprimez d'abord les passages ou modifiez le rattachement. »).
- [ ] La modification du code d'un point (ex. `A1` → `A2`) **n'affecte pas** les fichiers déjà renommés des passages existants - une note d'avertissement informe l'utilisateur (« Le renommage d'un point ne re-renomme pas les fichiers des passages déjà importés »).
- [ ] Test d'intégration sur la suppression bloquée par contrainte d'intégrité.

**Parcours rattaché** : [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)<br>
**Maquettes cibles** : [M-Site-detail](../Maquettes/M-Site-detail.md) (avec section « Points d'écoute » et boutons d'action)<br>
**Dépendances** : [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2), [E1.S1](#e1s1)<br>

---

## E1.S3 - Saisir les coordonnées GPS et un descriptif d'un point d'écoute { #e1s3 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** pouvoir associer des coordonnées GPS et un descriptif à chaque point d'écoute

**Afin de** retrouver facilement le point sur le terrain et de permettre les contrôles automatiques (cohérence horaires, calcul astronomique pour P6)

**Critères d'acceptation** :

- [ ] Le formulaire de création/édition d'un point inclut deux champs optionnels : `Latitude` (décimal, ex. 43.5298) et `Longitude` (décimal, ex. 5.4474).
- [ ] Un champ texte libre `Descriptif` (multi-ligne, optionnel) permet d'ajouter des notes (« près du chêne, à 30 m du chemin »).
- [ ] Si les coordonnées sont saisies, elles sont validées comme étant dans des plages plausibles (latitude entre -90 et 90, longitude entre -180 et 180).
- [ ] Les coordonnées et le descriptif sont persistés.
- [ ] La présence des coordonnées GPS est un **prérequis débloquant** pour la story de cohérence horaires (E6, parcours [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md)).

**Parcours rattaché** : [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)<br>
**Maquettes cibles** : [M-Site-detail](../Maquettes/M-Site-detail.md) (champs supplémentaires dans le formulaire de point)<br>
**Dépendances** : [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2), [E1.S1](#e1s1)<br>

---

## E1.S4 - Vue des sites déclarés avec compteurs de passages { #e1s4 }

**En tant que** [Karim](../Personas/Karim.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** une vue d'ensemble de tous mes sites déclarés avec, pour chacun, un compteur du nombre de passages enregistrés

**Afin de** me repérer rapidement dans mon volume et identifier d'un coup d'œil les sites qui n'ont pas encore été utilisés cette saison

**Critères d'acceptation** :

- [ ] La vue principale liste tous les sites déclarés (carte, ligne ou tableau, à arbitrer).
- [ ] Pour chaque site, on voit : le n° de carré, le nom convivial, le nombre de points d'écoute, le nombre de passages cette saison, la date du dernier passage importé.
- [ ] Un clic sur un site ouvre sa fiche détail (M-Site-detail).
- [ ] Un bouton mis en avant « + Nouveau site » est toujours visible.
- [ ] Si aucun site n'est déclaré, la vue affiche un état vide explicite (« Vous n'avez encore aucun site. Commencez par en déclarer un. ») avec une seule action mise en avant.
- [ ] La vue se met à jour automatiquement après création/modification d'un site.

**Parcours rattaché** : [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md), point d'entrée vers [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)<br>
**Maquettes cibles** : [M-Sites](../Maquettes/M-Sites.md)<br>
**Dépendances** : [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2), [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3) (pour les compteurs de passages), [E1.S1](#e1s1)<br>

---

## E1.S5 - Créer un nouveau site directement depuis la modale d'import { #e1s5 }

!!! warning "Non livré (cible)"
    Le sélecteur de site à l'import ne propose que les sites **existants** ; il n'y a pas de bouton « + Créer un nouveau site » à la volée. La création de site vit dans l'écran « Mes sites », hors du parcours d'import.

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir créer un nouveau site sans quitter le flux d'import si je réalise que je n'ai pas encore déclaré le site auquel ma nuit appartient

**Afin de** ne pas être obligée d'annuler l'import, déclarer le site, puis recommencer l'import

**Critères d'acceptation** :

- [ ] Dans la modale d'import ([P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 3), le combobox « Site » propose en plus une option « **+ Créer un nouveau site** » en tête de liste.
- [ ] Le clic sur cette option ouvre le formulaire de [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) (E1.S1) en superposition sans fermer la modale d'import.
- [ ] Après validation du nouveau site, la modale d'import se rafraîchit avec ce site présélectionné (et le combobox des points est mis à jour).
- [ ] Si l'utilisateur annule la création du site, il revient à la modale d'import dans son état précédent (aucun fichier n'a été touché, [R9](../Modèle%20conceptuel/Règles%20métier.md#r9)).
- [ ] Cas particulier des fichiers déjà préfixés (extraction du quadruplet) : si le carré ne correspond à aucun site déclaré, la même option « + Créer un nouveau site » est proposée, avec le n° de carré pré-rempli depuis le préfixe.

**Parcours rattaché** : [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md) (variante), [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (avec l'option « + Créer un nouveau site » dans le combobox)<br>
**Dépendances** : [E1.S1](#e1s1), E2 (la modale d'import existe)<br>
