# Cycle de vie d'un chantier

Un **chantier** est un lot de travail d'ampleur **EPIC** : une évolution qui ne tient pas dans une
seule PR et se découpe en plusieurs (ex. l'EPIC « Réglages auto-découverts → feature = plugin »). Là
où [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) décrit une **PR** et
[CONTRIBUTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)
le **flux de contribution**, cette page décrit le niveau au-dessus : comment on **ouvre** et on
**clôt** un chantier entier.

Le principe : un chantier ne se termine pas au dernier `feat:` mergé. Une fois le cœur livré, une
**clôture en 10 passes** garantit que l'évolution est intégrée, cohérente entre les deux surfaces
(IHM et CLI), documentée, testée, harmonisée, **regardée**, et que la suite est cadrée.

!!! note "Où est la règle courte ?"
    La version concise pour les contributeurs vit dans la section « Cycle de vie d'un chantier » de
    [CONTRIBUTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md).
    Cette page en est la version approfondie : la **raison d'être** et le **mode opératoire** de
    chaque passe.

## À l'ouverture : l'analyse de départ

Avant d'écrire du code :

1. **Cartographier l'existant.** Repérer les **patterns déjà en place** qui répondent (au moins en
   partie) au besoin, pour les **réutiliser** plutôt que réinventer. La plupart des extensions du
   socle se calquent sur un pattern existant (`Multibinder<ActiviteAccueil>`, contrats `Ouvrir*`,
   patron DAO, `Capture*`…). Voir [Patterns et principes](patterns.md).
2. **Rédiger un plan** : découpage, contraintes d'architecture ([Architecture](architecture.md) et les
   règles ArchUnit de [Tests et qualité](tests-et-qualite.md)), risques, ordre des paliers.
3. **Découper en issues** reliées à un **EPIC** (une issue « parapluie » avec la task-list des
   sous-issues). Chaque sous-issue porte son palier et ses dépendances.

## À la clôture : les 10 passes

Elles s'exécutent **dans l'ordre** : l'audit d'intégration peut révéler du travail à faire avant de
documenter, la cohérence CLI peut révéler une commande à ajouter (qui sera alors documentée et
testée par les passes suivantes), l'harmonisation peut **casser un écran sans casser un test** (d'où la
revue visuelle **juste après** elle), la revue visuelle peut faire émerger de nouveaux chantiers, et le
bilan vient en dernier.

### 1. Audit d'intégration

`main` a **évolué pendant** le chantier (autres PR mergées, nouvelles features, nouvelles
conventions). Cette passe vérifie que rien n'a été laissé de côté avant de finaliser :

- **rebaser** la ou les branches restantes sur `main` et résoudre les divergences ;
- chercher les **nouveaux points d'accroche** apparus entre-temps qu'il faudrait câbler (une nouvelle
  feature devrait-elle contribuer au mécanisme qu'on vient d'introduire ?) ;
- traquer les **régressions** et les **conventions apparues** depuis l'ouverture (ex. un nouveau
  contrat socle, un nouveau seuil qualité).

!!! tip "Signal concret"
    Un `git log --oneline main` depuis le SHA d'ouverture du chantier (souvent tracé dans l'EPIC) met
    en évidence ce qui a bougé et mérite un regard.

### 2. Passe de cohérence CLI ↔ UI

L'application expose **deux surfaces** sur le même domaine : l'IHM JavaFX et la **CLI** picocli
(scriptable, headless, cf. [CLI](cli.md)). Quand un chantier ajoute ou modifie une **capacité
métier** (une opération, une option, un format d'export, une règle de gestion), la CLI doit exposer
l'**équivalent** pour que les deux surfaces restent au même niveau : un traitement disponible d'un
seul côté crée une asymétrie et une dette invisibles.

Cette passe :

- identifie les **capacités métier** introduites ou changées par le chantier (pas les détails de
  présentation : une pastille de statut, une mise en page n'ont pas d'équivalent CLI) ;
- vérifie que la CLI offre l'opération correspondante (commande ou option de `fr.univ_amu.iut.cli`)
  avec le **même comportement** : mêmes règles, mêmes formats, mêmes garde-fous ;
- en cas d'écart : **aligner tout de suite** si c'est petit (la commande ajoutée sera alors
  documentée et testée par les passes suivantes), sinon **créer une issue** (passe 9) pour ne pas
  perdre le contexte ;
- si le chantier est **purement présentationnel**, le noter explicitement « sans objet côté CLI ».

!!! tip "Signal concret"
    Un **service de domaine** nouvellement appelé par un ViewModel mais par aucune commande de
    `fr.univ_amu.iut.cli.commande` signale une capacité présente d'un seul côté. La CLI et l'IHM
    partagent les mêmes services : la parité se joue au niveau des services exposés, pas du code d'IHM.

### 3. Passe de doc développeur

Mettre à jour le **site dev** (`dev-docs/`, publié sous `…/dev/`) pour que l'architecture décrite
colle au code livré : [Architecture](architecture.md), [Patterns et principes](patterns.md),
[Injection (Guice)](injection.md), [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) si le
chantier a introduit un **nouveau pattern d'extension** que les futures features devront suivre.

Toute **décision structurante** prise pendant le chantier - un choix d'architecture ou de domaine qu'un
développeur futur pourrait raisonnablement remettre en cause faute d'en connaître les raisons - donne une
**[ADR](decisions/index.md)** : une par décision, immuable, expliquant le pourquoi.

### 4. Passe de doc utilisateur

Documenter le chantier pour les **utilisateurs** dans le site produit (`docs/`), avec **autant de
captures que nécessaire**. Les aperçus sont régénérés en CI : ajouter/mettre à jour les classes
`Capture*` et le manifeste, cf. [Captures d'écran](captures.md). Une fonctionnalité visible sans
capture est une fonctionnalité à moitié livrée.

### 5. Passe de brief projet

Le **brief projet** (dépôt [`IUTInfoAix-S201/brief`](https://github.com/IUTInfoAix-S201/brief)) est le
document de **conception** vivant du produit : le besoin, les **parcours utilisateurs** (P1-P10), les
maquettes, le modèle conceptuel. Ce n'est **pas** un sujet pédagogique - son lecteur est un
**contributeur** du produit, pas un étudiant. Quand un chantier change un de ces **éléments de
conception** (un parcours, une maquette, le modèle de données, une contrainte produit), répercuter
l'évolution dans le brief pour qu'il reste aligné avec le produit réellement livré. Un brief qui décrit
une version périmée du produit égare son lecteur. C'est **rarement** « sans objet » pour un chantier
qui touche au comportement ou à la conception du produit.

### 6. Passe de tests

Vérifier que **chaque usage** introduit est couvert :

- **tests d'intégration** TestFX (headless) sur les vues et leurs bindings ;
- **tests E2E** (`fr.univ_amu.iut.e2e.*`) sur les parcours complets IHM → ViewModel → service →
  base.

Pièges et conventions dans [Tests et qualité](tests-et-qualite.md). Les frontières d'architecture
sont couvertes automatiquement par `ArchitectureTest`.

**Chercher ce qui manque, pas relire ce qu'on a écrit.** C'est le point qui a le plus souvent failli :
on relit ses propres tests, on les trouve verts, et on conclut que c'est couvert - alors qu'un pan
entier n'a jamais été regardé. L'inventaire se fait donc **depuis le diff du chantier**
(`git diff origin/main...`, toutes les PR confondues), pas depuis sa mémoire. Pour **chaque capacité**
ajoutée ou changée - un service, un geste d'IHM, une commande CLI, une migration, un port, un état
persisté - on note **quel test la couvre et à quel niveau**. « Aucun » est une réponse valable, mais
elle doit être **dite** : un trou assumé devient une issue (passe 9), un trou tacite devient une
régression. Trois familles concentrent les angles morts :

- **les chemins non nominaux** : le refus, l'erreur, l'annulation, l'état vide, la donnée absente, la
  **feature désactivée** ([ADR 0003](decisions/0003-feature-plugin-desactivable-ports-optionnels.md)).
  Le cas nominal est presque toujours testé ; c'est l'autre branche qui manque ;
- **les surfaces jumelles** : un geste couvert côté IHM mais pas côté **CLI** (ou l'inverse) n'est
  couvert qu'à moitié - c'est le prolongement de la passe 2
  ([ADR 0014](decisions/0014-parite-cli-ihm.md)) ;
- **le cas réel** : un test synthétique vert ne prouve pas que le vrai jeu de données passe. Quand
  c'est possible, rejouer le geste sur une **vraie nuit** avant de conclure.

**Des E2E qui traversent, quitte à fusionner des scénarios.** Un E2E ne vaut pas par le nombre
d'assertions mais par ce qu'il **traverse**. Plusieurs parcours courts, qui bouchonnent chacun l'étape
voisine, prouvent chacun une tranche et **personne ne prouve la chaîne** : les défauts se logent
précisément dans les **coutures** entre deux étapes. Quand deux scénarios partagent leur amont, les
**fusionner** en un seul qui va plus loin donne une couverture plus **fidèle**. Le critère de fusion
est simple : deux étapes se fusionnent quand le défaut probable est **entre** elles. À l'inverse,
fusionner sans couture à exercer ne produit qu'un test-fleuve illisible - la longueur n'est pas le but.

**Ce qui ne peut pas être automatisé va en recette.** Certaines vérifications ne se scriptent pas :
finesse du rendu, fluidité perçue, geste qui exige un vrai serveur, une vraie carte SD ou du matériel.
Elles ne disparaissent pas pour autant : elles s'écrivent dans la [recette](recette/index.md), sur le
script de la **session propriétaire** de l'écran (`recette/sessions/`), sous forme de **cases
numérotées `Sxx-NN`** - une case = **un fait observable**, jamais un contrôle groupé. Sans ce report,
« pas automatisable » devient silencieusement « pas vérifié ».

### 7. Passe d'harmonisation

**Prendre du recul sur l'application entière**, pas seulement sur les fichiers que le chantier a
touchés. Il s'agit de regarder comment ce qui vient d'être livré **s'intègre dans le tout**. La passe
se fait en **deux temps**.

**Premier temps : l'audit global.** Avant de refactorer quoi que ce soit, cartographier l'intégration
du résultat du chantier dans le reste de l'application. Deux questions, posées sur **tout le code** et
pas sur le seul périmètre du chantier :

- **Qu'est-ce qui ressemble** à ce qu'on vient d'écrire ? Un geste, un composant, un contrat, une
  formulation d'IHM, un parcours.
- **Qu'est-ce qui bénéficierait** du résultat du chantier ? Un écran qui gagnerait le nouveau
  composant, un service qui pourrait s'appuyer sur la nouvelle abstraction, un appelant resté sur
  l'ancienne façon de faire.

Cet audit doit être **exhaustif et scrupuleux** : l'enjeu est de **comprendre ce qui sous-tend la
demande initiale** (le concept réel, au-delà de la formulation du ticket) et d'en repérer **tous les
axes** possibles. On ne s'arrête pas au premier doublon évident.

**Second temps : le refactoring de conceptualisation.** Retravailler l'application pour que sa
structure **exprime mieux ce concept** et la rende à la fois plus **lisible** et plus
**compréhensible**. La **réduction de la duplication** (code répété entre features → **contrat/pattern
partagé** dans `commun`) et l'**abstraction** (classe devenue trop grosse → **Extract Class**, le PMD
`GodClass` du portail qualité est le garde-fou, cf. [Tests et qualité](tests-et-qualite.md)) sont des
**outils** au service de cette clarté, **pas** le but : un code plus court mais moins compréhensible
n'est pas une harmonisation. C'est le moment de transformer trois copies d'un même geste en un
mécanisme d'extension.

**Discuter, ne pas trancher seul.** Un refactoring de conceptualisation engage l'application entière.
Dès qu'un choix, un doute ou une conséquence n'est pas évident, **en discuter avec l'utilisateur** :
soumettre les options, expliciter les compromis, laisser trancher la direction. C'est un des rares
moments où l'on **remonte** de l'implémentation vers la conception ; on ne s'y engage pas à l'aveugle.

### 8. Passe de revue visuelle

**Inspecter visuellement toutes les conséquences visibles du chantier.** Pas seulement les écrans
nouveaux ou modifiés : **chaque état** qu'un écran touché peut prendre (donnée présente ou absente,
GPS renseigné ou non, liste vide ou pleine, calcul disponible ou indisponible, thème clair ou
sombre...). On **régénère** les captures concernées, on les **ouvre une par une**, on les **regarde**.

**Les captures sont une documentation vivante de l'état réel de l'application.** Il est donc **crucial
qu'elles reflètent toutes les fonctionnalités visuelles du chantier**. Une conséquence visible qui n'a
**pas** de capture n'est **pas documentée** : elle dérivera en silence, et le prochain qui lira la doc
verra un produit qui n'existe plus. Cette passe **ajoute donc autant de captures que nécessaire** : un
état neuf apparu avec le chantier, une variante qu'aucune capture ne montrait, un écran entier créé.
Une capture ajoutée devient une **validation visuelle rejouable** (seed déterministe, entrée au
manifeste des captures, insertion dans la doc) : elle est régénérée à chaque build et re-contrôlée à
chaque chantier suivant.

Cette passe existe parce qu'un constat s'est répété **cinq fois** sur les chantiers #1405 et #1431 :

> **Un geste testé n'est pas un écran regardé.**

Cinq défauts d'IHM y ont été trouvés en **ouvrant une capture**, et **aucun** par un test - alors que les
gestes concernés étaient couverts :

- un libellé tronqué (« Code du poi… ») ; puis **le même** sur un autre écran, préexistant ;
- une consigne rognée par le bouton voisin (« Copier le m… ») ;
- un emoji qui ne se rend pas (glyphe absent, cf. #700) ;
- et une **capture de documentation qui avait dérivé du produit** : elle affichait un protocole
  « Point fixe » qui **n'existe pas**, et cachait une confirmation destructive entière.

Aucun de ces défauts ne fait rougir quoi que ce soit. Un test vérifie qu'un bouton **fait** ce qu'il
doit ; il ne vérifie pas qu'on peut **lire** ce qu'il dit.

**Pourquoi ici, et pas plus tôt.** La passe précédente (harmonisation) touche volontiers au CSS partagé
ou aux composants du socle : c'est **elle** qui est la plus à même de casser un écran sans casser un
test. On regarde donc **après** elle. Et comme les aperçus sont **régénérés automatiquement sur `main`**,
un défaut corrigé ici rafraîchit la documentation tout seul.

**Ce qu'on cherche.** D'abord la **couverture** : chaque conséquence visible du chantier a-t-elle une
capture ? Un état montré nulle part est un angle mort ; on **crée la capture manquante** avant d'aller
plus loin. Puis, sur chaque capture (par ordre de fréquence constatée) :

1. du **texte coupé** - libellé, consigne, bouton (une ellipse `…` est un aveu) ;
2. un **glyphe absent** (emoji, symbole) ;
3. un **écart entre la capture et le produit** : si une capture est *reconstruite* quelque part au lieu
   d'être *rendue*, elle **mentira** tôt ou tard (cf. #1468) ;
4. une **régression de style** après une factorisation CSS ;
5. un **écran de la doc qui ne ressemble plus** à ce que le chantier a livré.

Ce qui se corrige tout de suite se corrige ; le reste part en issue à la passe suivante.

### 9. Passe d'identification des nouveaux chantiers

Un chantier en révèle d'autres (dette assumée, palier différé, idée née en chemin). Les **cadrer** et
**créer les issues** correspondantes (reliées à un nouvel EPIC si elles forment un ensemble), pour ne
pas perdre le contexte encore frais.

### 10. Phase de bilan

Une **synthèse** courte : ce qui a été livré, la **dette restante**, les **décisions** prises et leur
pourquoi. Elle se dépose dans le corps de l'EPIC (au moment de le clore) et, si elle change une
règle du dépôt, se répercute dans `CLAUDE.md` / `CONTRIBUTING.md`. Le bilan **renvoie** aux
[ADR](decisions/index.md) écrites en passe 3 plutôt que de redérouler le raisonnement des décisions.

## Modèle de clôture (à coller dans l'EPIC)

```markdown
## Clôture de chantier
- [ ] 1. Audit d'intégration (rebase sur `main`, points d'accroche, régressions)
- [ ] 2. Cohérence CLI ↔ UI (capacités métier exposées des deux côtés, ou « sans objet »)
- [ ] 3. Doc développeur (dev-docs) à jour + ADR pour toute décision structurante (dev-docs/decisions/)
- [ ] 4. Doc utilisateur (docs/) + captures
- [ ] 5. Brief projet (IUTInfoAix-S201/brief) répercuté si un élément de conception change
- [ ] 6. Tests : inventaire des usages **depuis le diff** (chemins non nominaux, parité CLI ↔ IHM), E2E qui **traversent les coutures**, non-automatisable reporté en **recette**
- [ ] 7. Harmonisation : **audit global** (ce qui ressemble / bénéficierait, exhaustif) puis **refactoring de conceptualisation** (lisibilité ; duplication et abstraction = outils) ; **choix, doutes, conséquences discutés avec l'utilisateur**
- [ ] 8. Revue visuelle : **toute conséquence visible** couverte par une capture (captures **ajoutées** si besoin), régénérées et ouvertes une par une
- [ ] 9. Nouveaux chantiers identifiés + issues créées
- [ ] 10. Bilan (livré / dette / décisions)
```
