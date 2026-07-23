# E3 - 🎧 Vérifier la qualité d'enregistrement

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P3 - Vérifier l'enregistrement par échantillonnage](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md)

**Portée** : permettre à l'utilisateur de **valider qu'une nuit d'enregistrement est exploitable** avant de la déposer sur Vigie-Chiro. La vérification se fait en deux temps complémentaires : (1) un **pré-check synthétique** rapide sans écoute (couverture horaire, nombre de fichiers, cohérence du renommage), pratiqué par défaut par Samuel ; (2) un **sound check par échantillonnage** audio plus long, pratiqué par défaut par Marie. C'est un sound check global, distinct de la validation taxonomique espèce par espèce (qui est l'objet de E7).

**Persona principal** : tous (Marie en mono-site, Karim et Samuel en chaîne).

**Pré-requis** : E0.S4 (DAO sélections/séquences), E2.S6 (séquences d'écoute disponibles sur disque après transformation).

## E3.S0 - Pré-check synthétique de la nuit (sans écoute) { #e3s0 }

**En tant que** [Samuel](../Personas/Samuel.md) ou [Karim](../Personas/Karim.md) (et [Marie](../Personas/Marie.md) qui veut un premier feu vert immédiat)

**Je veux** voir d'un coup d'œil si la nuit a produit un volume cohérent de fichiers, sur la bonne plage horaire, avec un renommage conforme

**Afin de** décider sans écouter si la nuit peut être déposée directement sur Vigie-Chiro, ou si elle nécessite une investigation plus poussée

**Critères d'acceptation** :

- [x] À l'ouverture de la vue détail d'un passage `Importé` ou `Transformé`, un encart **« État de la nuit »** affiche trois indicateurs sous forme de feux (🟢 OK / 🟠 suspect / 🔴 anomalie).
- [ ] **Indicateur 1 - Couverture horaire** : compare la plage `premier WAV → dernier WAV` (extraite des horodatages des fichiers) à la plage théorique `coucher de soleil - 30 min → lever de soleil + 30 min` ([R3](../Modèle%20conceptuel/Règles%20métier.md#r3)). La plage astronomique est calculée localement à partir des coordonnées GPS du point ([C3](../Modèle%20conceptuel/C3%20-%20Point%20d%27écoute.md)) et de la date de session d'enregistrement. Feu 🟠 si l'écart dépasse 30 min d'un côté, 🔴 si une moitié de nuit complète manque.
- [x] **Indicateur 2 - Nombre de fichiers** : feu 🟢 si nombre d'enregistrements originaux ≥ 50, 🟠 si entre 1 et 49 (nuit anormalement creuse), 🔴 si 0.
- [x] **Indicateur 3 - Cohérence du renommage** : feu 🟢 si tous les WAV portent le préfixe `Car<carre>-<annee>-Pass<n>-<point>-` attendu ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6)) avec les bons champs ; 🔴 dès qu'un fichier diverge (extra-fichier non préfixé ou incohérence de champ).
- [x] Chaque indicateur, au survol ou au clic, affiche le détail du calcul (valeurs attendues vs effectives).
- [ ] Si les coordonnées GPS du point ne sont pas renseignées, l'indicateur de couverture horaire affiche un état neutre `?` avec un message explicite « Renseignez les coordonnées du point pour activer ce check ».
- [ ] Un lien explicite renvoie vers [P6 - Diagnostiquer le matériel](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md) pour creuser (courbes T°/H, événements anormaux du journal, comparaison batterie inter-passages).
- [x] Test d'intégration : sur un passage de référence, vérifier les trois indicateurs (cas nominal 🟢/🟢/🟢, cas dégradé 🟠 sur couverture, cas critique 🔴 sur renommage).

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 1<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (encart « État de la nuit » en haut de la vue de vérification)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3) (passage en BD), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6) (transformation terminée), librairie astronomique pour les horaires (re-use [E6.S3](E6%20-%20Diagnostiquer%20le%20matériel.md#e6s3) si possible)<br>

---

## E3.S1 - Générer une sélection d'écoute automatique à l'ouverture de l'onglet { #e3s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application me propose automatiquement une sélection de séquences d'écoute représentative de la nuit dès que j'ouvre l'onglet « Vérifier l'enregistrement »

**Afin de** ne pas avoir à choisir moi-même quelles séquences écouter et avoir un échantillon directement utilisable

**Critères d'acceptation** :

- [x] À l'ouverture de l'onglet « Vérifier l'enregistrement » d'un passage `Transformé`, l'application constitue automatiquement une sélection de **10 à 30 séquences** ([R12](../Modèle%20conceptuel/Règles%20métier.md#r12)).
- [x] La méthode par défaut est `RéparTemporel` : les séquences sont **réparties uniformément** sur la plage horaire de la nuit (du premier au dernier enregistrement).
- [x] La sélection est **persistée** en BD pour pouvoir être reprise plus tard (cf. [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4)).
- [x] Si une sélection existe déjà pour ce passage, elle est rechargée plutôt que régénérée.
- [ ] La taille par défaut (entre 10 et 30) est ajustée en fonction du volume total : 10 séquences pour < 50 enregistrements originaux, 30 pour > 500, interpolation linéaire entre.
- [x] Test d'intégration : génération sur un passage de 100 enregistrements → vérifier nombre, répartition temporelle, persistance.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 2<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (état initial à l'ouverture de l'onglet)<br>
**Dépendances** : [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6)<br>

---

## E3.S2 - Afficher la sélection en liste chronologique avec métadonnées { #e3s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** voir la sélection sous forme d'une liste chronologique où chaque ligne montre les informations clés de la séquence (horodatage, durée, indicateur de fréquence)

**Afin de** pouvoir choisir rapidement quelles séquences je veux écouter en priorité

**Critères d'acceptation** :

- [x] Les séquences de la sélection sont affichées sous forme de tableau ou de liste, **triées chronologiquement** (du plus ancien au plus récent).
- [ ] Pour chaque séquence on voit : horodatage de session d'enregistrement (heure du fichier source), durée affichée (5 s par défaut, plus court pour la dernière séquence d'un fichier), fréquence dominante indicative (en kHz, calculée à partir du WAV).
- [x] Un indicateur visuel distingue les séquences déjà écoutées des séquences pas encore écoutées (cf. [E3.S4](#e3s4)).
- [ ] Un bouton ▶ par ligne permet de déclencher la lecture d'une séquence (cf. [E3.S3](#e3s3)).
- [x] La liste reste lisible même avec 50 lignes (défilement vertical).

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 3<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (tableau central de la vue de vérification)<br>
**Dépendances** : [E3.S1](#e3s1)<br>

---

## E3.S3 - Intégrer le composant de vue audio (sonogramme + spectrogramme) { #e3s3 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir écouter chaque séquence sélectionnée avec les contrôles habituels et visualiser le sonogramme + spectrogramme pour confirmer visuellement la qualité

**Afin de** vérifier de mes propres oreilles ET de mes propres yeux que la nuit est exploitable

!!! info "Composant fourni"
    Le composant de vue audio (sonogramme + spectrogramme avec contrôles de lecture et zoom) est une **dépendance externe** (`audio-view`, JitPack). Le même composant est utilisé dans [E7.S3](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3) pour la validation Tadarida. Cette story ne porte que son **intégration** dans M-Qualification.

**Critères d'acceptation** :

- [x] Le panneau de détail ([E3.S2](#e3s2)) affiche le composant audio, alimenté par le chemin de la séquence courante.
- [ ] Le clic sur le bouton ▶ d'une séquence dans la liste de gauche déclenche la lecture immédiate dans le composant.
- [x] Comme les séquences sont **déjà ralenties ×10 sur disque** ([R10](../Modèle%20conceptuel/Règles%20métier.md#r10)), la lecture se fait à vitesse normale (pas de re-échantillonnage à la volée).
- [ ] Le curseur du composant est synchronisé entre le sonogramme, le spectrogramme et la barre de lecture.  *(non verifiable depuis le code)*
- [x] Une seule séquence joue à la fois : démarrer une nouvelle lecture stoppe la précédente.
- [x] La lecture d'une séquence en marque la séquence comme « écoutée » (cf. [E3.S4](#e3s4)).
- [ ] Si la séquence est introuvable sur disque (fichier supprimé, déplacé), le composant affiche un substitut explicite.  *(non verifiable depuis le code)*

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 4<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (vue audio combinée)<br>
**Dépendances** : [E3.S2](#e3s2), composant de vue audio partagé<br>

---

## E3.S4 - Marquer les séquences écoutées et suivre l'avancement { #e3s4 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application mémorise quelles séquences j'ai déjà écoutées et m'affiche un compteur de progression

**Afin de** savoir où j'en suis dans ma revue et ne pas écouter plusieurs fois la même séquence par inadvertance

**Critères d'acceptation** :

- [x] Chaque lecture d'une séquence (cf. [E3.S3](#e3s3)) marque automatiquement la séquence comme « écoutée » en BD ([E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4)).
- [ ] L'utilisateur peut aussi **manuellement** marquer/démarquer une séquence comme écoutée via une case à cocher ou une bascule dans la liste.
- [x] Un compteur visible affiche `N/M séquences écoutées` (ex. `12/30 séquences écoutées`).
- [x] L'état « écouté » est **persisté** : à la réouverture de l'onglet, on retrouve la progression.
- [x] L'utilisateur reste libre de saisir son verdict (cf. [E3.S5](#e3s5)) **sans avoir tout écouté** ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 4<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (compteur d'avancement, indicateur visuel par ligne)<br>
**Dépendances** : [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4), [E3.S2](#e3s2), [E3.S3](#e3s3)<br>

---

## E3.S5 - Saisir le verdict global du passage et un commentaire libre { #e3s5 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** consigner mon avis global sur la nuit (`OK`, `Utilisable`, `Inexploitable`) et pouvoir y ajouter un commentaire texte

**Afin de** trancher si la nuit est exploitable et de garder une trace de ce que j'ai constaté pour plus tard

**Critères d'acceptation** :

- [x] Un menu déroulant visible permet de choisir le verdict parmi `OK`, `Utilisable`, `Inexploitable`.
- [x] Un champ texte libre (multi-ligne) permet d'ajouter un commentaire optionnel (ex. « vent fort vers 02:00, sons à vérifier »).
- [x] La saisie du verdict est possible **dès le premier clic** sur le menu, sans contrainte d'avoir écouté un nombre minimum de séquences ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).
- [x] À la validation du verdict, le passage passe au statut `Vérifié` en BD ([E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)).
- [ ] Le verdict et le commentaire sont **persistés** : ils sont restaurés à la réouverture de l'onglet et peuvent être modifiés à tout moment.
- [x] Un verdict `Inexploitable` est mis en évidence visuelle (couleur d'alerte) et affiche un rappel : « Ce passage ne pourra pas être inclus dans un dépôt » ([R14](../Modèle%20conceptuel/Règles%20métier.md#r14)).
- [ ] L'utilisateur peut enchaîner sur la préparation du dépôt ([P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)) via un bouton mis en avant après saisie d'un verdict OK ou Utilisable.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 6 et 7<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (zone de saisie du verdict en bas de l'écran)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)<br>

---

## E3.S6 - Personnaliser la composition de la sélection d'écoute { #e3s6 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md) (qui veut s'assurer plus en profondeur de la qualité d'une nuit douteuse)

**Je veux** pouvoir modifier la sélection automatique : changer la méthode (aléatoire au lieu d'uniforme), augmenter la taille, ou ajouter manuellement une séquence à un moment précis

**Afin de** affiner ma vérification quand le résultat par défaut ne me semble pas suffisant

**Critères d'acceptation** :

- [ ] Un bouton « Modifier la sélection » ouvre un panneau avec : choix de la méthode (`RéparTemporel`, `Aléatoire`), choix de la taille (slider ou input numérique entre 10 et 100).
- [x] Le clic sur « Régénérer » re-constitue la sélection avec les nouveaux paramètres et la persiste.
- [ ] Un bouton « + Ajouter une séquence » ouvre un sélecteur permettant de pointer une séquence précise (par horodatage ou par recherche dans tous les fichiers du passage). La séquence ajoutée vient compléter la sélection sans déclencher de régénération complète.
- [ ] Les séquences déjà écoutées sont conservées dans le statut « écouté » même si elles ne sont plus dans la nouvelle sélection (historique préservé).
- [ ] La régénération de la sélection est tracée (date de dernière régénération + paramètres utilisés) pour audit.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 5<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (panneau « Modifier la sélection »)<br>
**Dépendances** : [E3.S1](#e3s1), [E3.S2](#e3s2)<br>

---

## E3.S7 - Saisir un verdict par fichier son écouté { #e3s7 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** noter la qualité de **chaque** séquence que j'écoute

**Afin que** le verdict global du passage se déduise de mes écoutes plutôt que d'un jugement d'ensemble

**Critères d'acceptation** :

- [x] Chaque séquence écoutée reçoit un verdict **Bon / Mauvais / Inexploitable** ; une séquence non jugée reste **neutre** (on ne juge que ce qu'on écoute).
- [x] Le verdict global du passage est **dérivé des seuls verdicts saisis** : aucune écoute → « À vérifier » ; **majorité stricte** d'inexploitables → « Inexploitable » ; toutes bonnes → « OK » ; sinon « Utilisable ».
- [x] Le verdict global dérivé reste **surchargeable** à la main ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).
- [x] Le lexique du verdict final (`Non vérifié / OK / Utilisable / Inexploitable`) est distinct du verdict par fichier ; badges, tri et filtres sauvegardés sont **préservés**.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), à l'écoute de chaque séquence<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (barre de verdicts par fichier)<br>
**Dépendances** : [E3.S4](#e3s4), [E3.S5](#e3s5)<br>
