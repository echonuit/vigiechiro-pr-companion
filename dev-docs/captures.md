# Captures d'écran (harnais)

Les **aperçus PNG** (`.github/assets/apercu-*.png`) illustrent la documentation utilisateur. Ils sont
**régénérés depuis le code** à chaque évolution des écrans, pour ne **jamais se désynchroniser** de
l'application réelle. Tout est rendu **hors-écran** (Headless Platform JavaFX) : aucun display requis.

!!! tip "Une capture, ça se regarde"
    Un aperçu n'est pas qu'un livrable de doc : c'est le **seul endroit où l'on voit** ce qu'un test ne
    dit pas (texte tronqué, glyphe absent, style cassé). D'où la **passe de revue visuelle** en clôture
    de chantier, cf. [Cycle de vie d'un chantier](cycle-de-chantier.md#8-passe-de-revue-visuelle).

## Rendre une scène hors-écran : `ApercuFx`

[`ApercuFx`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/ApercuFx.java)
est la brique de base : elle attache une `Scene` à un `Stage` transitoire (montré brièvement pour une
passe de layout/CSS complète, p. ex. peupler une `TableView` virtualisée), capture via
`Scene.snapshot()`, écrit le PNG, puis referme le stage. Déterministe.

Pour les écrans à **écoute audio**, dont l'`AudioView` charge son WAV de façon **asynchrone**, on
utilise `ApercuFx.capturerApresPreparation(...)` : le `Stage` est montré **avant** une préparation
asynchrone, puis on `snapshot` **sans recréer de Stage** (la Headless Platform JavaFX 26 refuse un
`new Stage()` après une boucle d'évènements imbriquée). Couplée à
[`AttenteAudio`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/AttenteAudio.java)
(attend la fin du chargement) et
[`SonDemo`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/SonDemo.java)
(WAV de synthèse), elle produit un **spectrogramme réel** dans la capture.

## Un outil de capture par écran : `Capture*`

Chaque feature a un `outils/Capture<Feature>.java` exécutable comme **`main` autonome** : il seede une
base SQLite **jetable**, charge le FXML via une `controllerFactory` Guice, peuple l'écran, puis le rend
par `ApercuFx`. Souvent en **deux états** (vide / peuplé) pour montrer les cas pertinents.

!!! danger "Déterminisme = règle d'or"
    Les PNG sont **versionnés** : un rendu non déterministe salirait le dépôt à chaque CI. Signaux de
    synthèse (cf. `SonDemo`), pas d'horodatage réel, attente explicite des chargements asynchrones.

## La régénération en CI

[`capture-screenshots.sh`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/assets/capture-screenshots.sh)
compile puis lance **chaque `Capture*` dans son propre JVM**, avec les drapeaux headless
(`-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true`). Le workflow
[`capture-vues.yml`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/capture-vues.yml)
l'exécute à chaque push sur `main` et **commite** les PNG mis à jour (via une PR auto-mergée, message
`[skip ci]`). Le workflow `docs.yml` **republie** ensuite le site (déclencheur `workflow_run`), pour
que les images en ligne suivent le code.

```mermaid
sequenceDiagram
    participant Dev
    participant Main as Branche main
    participant CV as capture-vues.yml
    participant Docs as docs.yml
    Dev->>Main: push (code d'un écran modifié)
    Main->>CV: déclenche
    CV->>CV: capture-screenshots.sh (Capture* en headless)
    CV->>Main: commit des apercu-*.png [skip ci]
    CV-->>Docs: workflow_run (terminé)
    Docs->>Docs: rebuild + republie le site
```

## Les garde-fous de présence

Deux scripts vérifient qu'aucune vue ne vit sans aperçu, et qu'aucune page ne pointe une image
absente (lancés en CI) :

| Garde | Vérifie |
|---|---|
| [`check-captures.sh`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/assets/check-captures.sh) | Chaque vue FXML `src/main/**/view/*.fxml` est **déclarée** au `captures.manifest`, et chaque capture déclarée existe. *(Aucune vue livrée sans capture.)* |
| [`check-doc-images.sh`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/assets/check-doc-images.sh) | Chaque capture **référencée par une page de doc** existe et est au manifeste. *(Aucune page ne pointe une image absente.)* |

Le [`captures.manifest`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/assets/captures.manifest)
associe chaque vue FXML à ses aperçus.

## Le garde-fou de fidélité : un aperçu qui ment est refusé

Les garde-fous ci-dessus vérifient qu'une capture **existe**. Celui-ci vérifie qu'elle **ne ment
pas**. Il vit dans `ApercuFx`, au moment du rendu, et **interrompt** la chaîne : un aperçu déformé
n'est pas écrit.

L'application monte ses vues dans un `ScrollPane` permanent — ce qui déborde **défile**. La capture
rend une scène de taille fixe et n'a pas ce recours : ce qui déborde se **déforme**, de deux façons
que le message d'erreur distingue.

| Dans le message | Ce qui se passe | Remèdes |
|---|---|---|
| `manque N px` | La scène est trop **courte** : un libellé `wrapText` se rabat sur une ligne et s'ellipse | Augmenter la hauteur de cette scène |
| `tronque, manque N px` | Le contrôle est trop **étroit** pour son texte | Figer par `minWidth="-Infinity"`, élargir la colonne, ou assumer par `abregeable` |

**`minWidth="-Infinity"`** est le remède le plus fréquent. La largeur *minimale* d'un `Labeled`
autorise la troncature : une `HBox` en déficit rogne donc les libellés d'action plutôt que les
sélecteurs et champs de recherche qui les entourent. Le figer inverse cette priorité — le déficit se
reporte sur les voisins souples, qui se resserrent sans rien perdre de lisible.

L'attribut se pose **dans le FXML**, sur le nœud qui est enfant direct du conteneur qui rogne (donc
sur l'enveloppe `StackPane` quand le bouton en porte une). C'est un idiome répandu dans le dépôt,
notamment dans les modales.

!!! note "Pourquoi pas une classe CSS ?"
    On ne peut pas. `-fx-min-width: -Infinity` **parse sans erreur** mais donne `-1.0`, c'est-à-dire
    `USE_COMPUTED_SIZE` — exactement le comportement qu'on cherche à éviter — au lieu de
    `USE_PREF_SIZE`. Mesuré. L'attribut FXML est le seul moyen d'exprimer cette contrainte.

**`abregeable`** est une classe CSS **marqueur**, sans règle de style — ne pas la supprimer comme
CSS morte. Elle déclare, *dans la vue*, quel libellé porte le déficit : le figer partout ne fait pas
rentrer le contenu d'une barre, cela le fait déborder. La règle est de désigner un sélecteur ou une
métadonnée (qui se relisent ailleurs) plutôt qu'un libellé d'action (qui ne se relit nulle part). La
tolérance s'hérite jusqu'aux libellés internes des contrôles composés (`ComboBox`, `MenuButton`).

Le sous-arbre d'`AudioView` est **hors du contrôle** : le composant vient d'un artefact séparé, ses
défauts se traitent en amont.

!!! warning "Le poste de développement sous-mesure"
    Les polices d'un poste et celles d'un runner de CI **ne mesurent pas le texte à l'identique** :
    l'écart va jusqu'à 6 px, soit l'ordre de grandeur des défauts eux-mêmes. Une chaîne verte en local
    peut être rouge en CI, et l'a été. Deux conséquences pratiques : une correction de dimension prend
    une **marge d'une dizaine de pixels** plutôt que le chiffre mesuré ; et pour inventorier, rendre le
    contrôle **non bloquant** le temps d'un seul passage de CI vaut mieux qu'une série d'allers-retours,
    puisqu'il s'arrête au premier écran fautif. Voir
    [ADR 0043](decisions/0043-la-mesure-fait-foi-en-ci.md) et
    [ADR 0042](decisions/0042-un-apercu-qui-ment-est-refuse.md).

## Ajouter une capture

La marche à suivre (nouvel écran) est dans
**[Ajouter une fonctionnalité §7](ajouter-une-fonctionnalite.md#7-ajouter-un-apercu-capture-decran)** :
écrire `CaptureMaFeature` sur le patron existant, l'ajouter à `capture-screenshots.sh`, et déclarer
l'aperçu au `captures.manifest`.

!!! note "Exposées au site via un hook"
    Les PNG vivent dans `.github/assets/` ; le hook
    [`scripts/mkdocs_hooks.py`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/scripts/mkdocs_hooks.py)
    les expose sous `assets/captures/` au build du site utilisateur.
