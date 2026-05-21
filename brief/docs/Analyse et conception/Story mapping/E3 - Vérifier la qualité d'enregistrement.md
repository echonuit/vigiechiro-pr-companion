# E3 - 🎧 Vérifier la qualité d'enregistrement

[← Retour au hub story mapping](index.md) · **Parcours principal** : [P3 - Vérifier l'enregistrement par échantillonnage](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md) · ✅ MUST

**Portée** : permettre à l'utilisateur de **valider qu'une nuit d'enregistrement est exploitable** avant de la déposer sur Vigie-Chiro. Constitution automatique d'une sélection d'écoute échantillonnée, lecteur audio pour les séquences ralenties ×10, et saisie d'un verdict global. C'est un **sound check** distinct de la validation taxonomique espèce par espèce (qui est l'objet de E7).

**Persona principal** : tous (Marie en mono-site, Karim et Samuel en chaîne).

**Pré-requis** : E0.S4 (DAO sélections/séquences), E2.S6 (séquences d'écoute disponibles sur disque après transformation).

## E3.S1 - Générer une sélection d'écoute automatique à l'ouverture de l'onglet { #e3s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application me propose automatiquement une sélection de séquences d'écoute représentative de la nuit dès que j'ouvre l'onglet « Vérifier l'enregistrement »

**Afin de** ne pas avoir à choisir moi-même quelles séquences écouter et avoir un échantillon directement utilisable

**Critères d'acceptation** :

- [ ] À l'ouverture de l'onglet « Vérifier l'enregistrement » d'un passage `Transformé`, l'application constitue automatiquement une sélection de **10 à 30 séquences** ([R12](../Modèle%20conceptuel/Règles%20métier.md#r12)).
- [ ] La méthode par défaut est `RéparTemporel` : les séquences sont **réparties uniformément** sur la plage horaire de la nuit (du premier au dernier enregistrement).
- [ ] La sélection est **persistée** en BD pour pouvoir être reprise plus tard (cf. [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4)).
- [ ] Si une sélection existe déjà pour ce passage, elle est rechargée plutôt que régénérée.
- [ ] La taille par défaut (entre 10 et 30) est ajustée en fonction du volume total : 10 séquences pour < 50 enregistrements originaux, 30 pour > 500, interpolation linéaire entre.
- [ ] Test d'intégration : génération sur un passage de 100 enregistrements → vérifier nombre, répartition temporelle, persistance.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 2<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (état initial à l'ouverture de l'onglet)<br>
**Dépendances** : [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6)<br>
**Complexité** : ★★ (simple — algorithme d'échantillonnage uniforme + persistance)<br>
**MoSCoW** : ✅ MUST

---

## E3.S2 - Afficher la sélection en liste chronologique avec métadonnées { #e3s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** voir la sélection sous forme d'une liste chronologique où chaque ligne montre les informations clés de la séquence (horodatage, durée, indicateur de fréquence)

**Afin de** pouvoir choisir rapidement quelles séquences je veux écouter en priorité

**Critères d'acceptation** :

- [ ] Les séquences de la sélection sont affichées sous forme de tableau ou de liste, **triées chronologiquement** (du plus ancien au plus récent).
- [ ] Pour chaque séquence on voit : horodatage de session d'enregistrement (heure du fichier source), durée affichée (5 s par défaut, plus court pour la dernière séquence d'un fichier), fréquence dominante indicative (en kHz, calculée à partir du WAV).
- [ ] Un indicateur visuel distingue les séquences déjà écoutées des séquences pas encore écoutées (cf. [E3.S4](#e3s4)).
- [ ] Un bouton ▶ par ligne permet de déclencher la lecture d'une séquence (cf. [E3.S3](#e3s3)).
- [ ] La liste reste lisible même avec 50 lignes (scroll vertical).

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 3<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (tableau central de la vue de vérification)<br>
**Dépendances** : [E3.S1](#e3s1)<br>
**Complexité** : ★★ (simple — TableView ou ListView JavaFX avec rendu personnalisé par cellule)<br>
**MoSCoW** : ✅ MUST

---

## E3.S3 - Intégrer le composant de vue audio (sonogramme + spectrogramme) { #e3s3 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir écouter chaque séquence sélectionnée avec les contrôles habituels et visualiser le sonogramme + spectrogramme pour confirmer visuellement la qualité

**Afin de** vérifier de mes propres oreilles ET de mes propres yeux que la nuit est exploitable

!!! info "Composant fourni"
    Le composant de vue audio (sonogramme + spectrogramme avec contrôles de lecture et zoom) est **fourni par l'équipe pédagogique**. Le même composant est utilisé dans [E7.S3](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s3) pour la validation Tadarida. Cette story se concentre sur l'**intégration** dans M-Qualification.

**Critères d'acceptation** :

- [ ] Le panneau de détail ([E3.S2](#e3s2)) affiche le composant audio fourni, alimenté par le chemin de la séquence courante.
- [ ] Le clic sur le bouton ▶ d'une séquence dans la liste de gauche déclenche la lecture immédiate dans le composant.
- [ ] Comme les séquences sont **déjà ralenties ×10 sur disque** ([R10](../Modèle%20conceptuel/Règles%20métier.md#r10)), la lecture se fait à vitesse normale (pas de re-échantillonnage à la volée).
- [ ] Le cursor du composant est synchronisé entre le sonogramme, le spectrogramme et la barre de lecture.
- [ ] Une seule séquence joue à la fois : démarrer une nouvelle lecture stoppe la précédente.
- [ ] La lecture d'une séquence en marque la séquence comme « écoutée » (cf. [E3.S4](#e3s4)).
- [ ] Si la séquence est introuvable sur disque (fichier supprimé, déplacé), le composant affiche un placeholder explicite.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 4<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (vue audio combinée)<br>
**Dépendances** : [E3.S2](#e3s2), composant audio fourni par l'équipe pédagogique<br>
**Complexité** : ★★ (simple — intégration du composant + gestion d'état lecture)<br>
**MoSCoW** : ✅ MUST

---

## E3.S4 - Marquer les séquences écoutées et suivre l'avancement { #e3s4 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application mémorise quelles séquences j'ai déjà écoutées et m'affiche un compteur de progression

**Afin de** savoir où j'en suis dans ma revue et ne pas écouter plusieurs fois la même séquence par inadvertance

**Critères d'acceptation** :

- [ ] Chaque lecture d'une séquence (cf. [E3.S3](#e3s3)) marque automatiquement la séquence comme « écoutée » en BD ([E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4)).
- [ ] L'utilisateur peut aussi **manuellement** marquer/démarquer une séquence comme écoutée via une checkbox ou un toggle dans la liste.
- [ ] Un compteur visible affiche `N/M séquences écoutées` (ex. `12/30 séquences écoutées`).
- [ ] L'état « écouté » est **persisté** : à la réouverture de l'onglet, on retrouve la progression.
- [ ] L'utilisateur reste libre de saisir son verdict (cf. [E3.S5](#e3s5)) **sans avoir tout écouté** ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 4<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (compteur d'avancement, indicateur visuel par ligne)<br>
**Dépendances** : [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4), [E3.S2](#e3s2), [E3.S3](#e3s3)<br>
**Complexité** : ★ (trivial — booléen persisté + indicateur UI)<br>
**MoSCoW** : ✅ MUST

---

## E3.S5 - Saisir le verdict global du passage et un commentaire libre { #e3s5 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** consigner mon avis global sur la nuit (`OK`, `Douteux`, `À jeter`) et pouvoir y ajouter un commentaire texte

**Afin de** trancher si la nuit est exploitable et de garder une trace de ce que j'ai constaté pour plus tard

**Critères d'acceptation** :

- [ ] Un menu déroulant visible permet de choisir le verdict parmi `OK`, `Douteux`, `À jeter`.
- [ ] Un champ texte libre (multi-ligne) permet d'ajouter un commentaire optionnel (ex. « vent fort vers 02:00, sons à vérifier »).
- [ ] La saisie du verdict est possible **dès le premier clic** sur le menu, sans contrainte d'avoir écouté un nombre minimum de séquences ([R13](../Modèle%20conceptuel/Règles%20métier.md#r13)).
- [ ] À la validation du verdict, le passage passe au statut `Vérifié` en BD ([E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)).
- [ ] Le verdict et le commentaire sont **persistés** : ils sont restaurés à la réouverture de l'onglet et peuvent être modifiés à tout moment.
- [ ] Un verdict `À jeter` est mis en évidence visuelle (couleur d'alerte) et affiche un rappel : « Ce passage ne pourra pas être inclus dans un lot prêt à déposer » ([R14](../Modèle%20conceptuel/Règles%20métier.md#r14)).
- [ ] L'utilisateur peut enchaîner sur la préparation du lot ([P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)) via un bouton mis en avant après saisie d'un verdict OK ou Douteux.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 6 et 7<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (zone de saisie du verdict en bas de l'écran)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)<br>
**Complexité** : ★ (trivial — menu déroulant + champ texte + persistance)<br>
**MoSCoW** : ✅ MUST

---

## E3.S6 - Personnaliser la composition de la sélection d'écoute { #e3s6 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md) (qui veut s'assurer plus en profondeur de la qualité d'une nuit douteuse)

**Je veux** pouvoir modifier la sélection automatique : changer la méthode (aléatoire au lieu d'uniforme), augmenter la taille, ou ajouter manuellement une séquence à un moment précis

**Afin de** affiner ma vérification quand le résultat par défaut ne me semble pas suffisant

**Critères d'acceptation** :

- [ ] Un bouton « Modifier la sélection » ouvre un panneau avec : choix de la méthode (`RéparTemporel`, `Aléatoire`), choix de la taille (slider ou input numérique entre 10 et 100).
- [ ] Le clic sur « Régénérer » re-constitue la sélection avec les nouveaux paramètres et la persiste.
- [ ] Un bouton « + Ajouter une séquence » ouvre un sélecteur permettant de pointer une séquence précise (par horodatage ou par recherche dans tous les fichiers du passage). La séquence ajoutée vient compléter la sélection sans déclencher de régénération complète.
- [ ] Les séquences déjà écoutées sont conservées dans le statut « écouté » même si elles ne sont plus dans la nouvelle sélection (historique préservé).
- [ ] La régénération de la sélection est tracée (date de dernière régénération + paramètres utilisés) pour audit.

**Parcours rattaché** : [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), étape 5<br>
**Maquettes cibles** : [M-Qualification](../Maquettes/M-Qualification.md) (panneau « Modifier la sélection »)<br>
**Dépendances** : [E3.S1](#e3s1), [E3.S2](#e3s2)<br>
**Complexité** : ★★★ (moyen — interface de personnalisation + algorithme aléatoire + ajout manuel par recherche)<br>
**MoSCoW** : 🟠 SHOULD (personnalisation est un confort, la sélection automatique par défaut suffit pour le MVP strict)
