# Glossaire du domaine

Le **vocabulaire** employé dans le code, les revues et les issues. Cette page fixe *les mots* ; le
[Modèle de données & domaine](modele-de-donnees.md) fixe *les entités et le schéma* ; le
**[glossaire utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/glossaire/)** fixe
les définitions vues par l'utilisateur final. Une divergence de mot entre ces trois surfaces est un
**défaut de vocabulaire** à corriger.

## L'unité de travail : nuit, passage, participation

Un même objet porte trois noms selon le point de vue. Les distinguer lève l'ambiguïté historique du
mot « lot ».

| Terme | Point de vue | Définition |
|---|---|---|
| **nuit** | capture (utilisateur) | Une nuit d'enregistrement sur un point d'écoute. Terme naturel, dominant dans l'IHM. |
| **passage** | protocole / modèle | L'entité `Passage` : un point d'écoute, une année, un **numéro de passage**. C'est la **racine d'agrégat** du [workflow à états](patterns.md#machine-a-etats-moteurworkflowpassage) `Importé → … → Déposé`. |
| **participation** | plateforme | La nuit **déposée** sur Vigie-Chiro. Un passage donne lieu à **au plus une** participation. |

**1 nuit ↔ 1 passage** : chaque nuit importée devient un passage distinct. « nuit » et « passage »
désignent donc le même objet ; on garde les deux mots car ils portent des nuances différentes (la
capture physique d'un côté, l'entité protocolaire numérotée de l'autre).

## « lot » : terme déprécié

!!! warning "N'employez plus « lot » pour désigner un passage"
    Historiquement, « lot » désignait *l'ensemble préparé des fichiers d'un passage au moment du
    dépôt*. Ce n'est **pas** un regroupement de plusieurs passages : le dépôt se fait **passage par
    passage** (un passage donne lieu à au plus une participation). Le mot induit en erreur, car il
    évoque un batch. **Vocabulaire retenu : on parle du _dépôt_ d'un passage.**

    - Côté IHM et doc utilisateur : « lot » est **purgé** (« préparer le **dépôt** »…).
    - Côté code : le package `fr.univ_amu.iut.lot` **conserve son nom pour l'instant** ; son
      renommage est tracé dans une issue de refactor dédiée du chantier
      [#1524](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1524).

Le mot « lot » subsiste par ailleurs au sens générique de *jeu de fichiers* (l'entrée du classifieur
Tadarida, une écoute filtrée sur plusieurs sites). Là aussi on préfère « **jeu** » ou
« **sélection** », pour ne pas le confondre avec le dépôt.

## Verdict de vérification

Le **verdict** est le jugement de qualité porté sur un passage après écoute d'un échantillon de
séquences (parcours P3, règles R13 consultative et R14 bloquante).

### État livré

| Verdict | Sens | Dépôt |
|---|---|---|
| `À vérifier` | pas encore jugé (valeur sentinelle) | — |
| `OK` | exploitable | autorisé |
| `Douteux` | à considérer avec réserve | autorisé |
| `À jeter` | à écarter | **bloqué (R14)** |

L'enum vit dans [`commun/model/Verdict`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/Verdict.java).

### Cible du chantier #1524 (décidée, pas encore implémentée)

!!! note "Évolution de vocabulaire figée, bascule à venir"
    Le chantier [#1524](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1524) fait
    passer d'un **verdict global unique** à **un verdict par fichier son** plus **un verdict final du
    passage dérivé**. Tant que la bascule (enum + migration Flyway) n'est pas livrée, l'**état livré**
    ci-dessus fait foi ; ne pas anticiper ces mots dans l'IHM ou la doc utilisateur.

    - **Verdict par fichier son** : `Bon` / `Mauvais` / `Inexploitable` (alimente la barre de
      progression tricolore).
    - **Verdict final du passage**, proposé après écoute complète de l'échantillon : `OK` /
      `Utilisable` / `Inexploitable`. État initial : `Non vérifié`.
    - Correspondance ancien → cible : `OK` → `OK`, `Douteux` → `Utilisable`, `À jeter` →
      `Inexploitable`, `À vérifier` → `Non vérifié`.
    - **Garde de dépôt** : `Inexploitable` reprend le rôle bloquant de `À jeter` (un passage
      `Inexploitable` ne peut pas être déposé sans **requalification**).

## Autres termes

Les termes du **domaine vus par l'utilisateur** (carré, point d'écoute, préfixe, séquence, sonogramme,
spectrogramme, statut, Tadarida, passage archivé, réactivation…) sont définis dans le
**[glossaire utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/glossaire/)**.
Cette page-ci ne reprend que les termes dont le **sens interne** mérite une précision pour qui lit ou
modifie le code.
