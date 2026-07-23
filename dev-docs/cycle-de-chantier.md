# Cycle de vie d'un chantier

Un **chantier** est un lot de travail d'ampleur **EPIC** : une évolution qui ne tient pas dans une
seule PR et se découpe en plusieurs (ex. l'EPIC « Réglages auto-découverts → feature = plugin »). Là
où [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) décrit une **PR** et
[CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)
le **flux de contribution**, cette page décrit le niveau au-dessus : comment on **ouvre** et on
**clôt** un chantier entier.

Le principe : un chantier ne se termine pas au dernier `feat:` mergé. Une fois le cœur livré, une
**clôture en 10 passes** garantit que l'évolution est intégrée, cohérente entre les deux surfaces
(IHM et CLI), documentée, testée, harmonisée, **regardée**, et que la suite est cadrée.

!!! note "Où est la règle courte ?"
    La version concise pour les contributeurs vit dans la section « Cycle de vie d'un chantier » de
    [CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md).
    Cette page en est la version approfondie : la **raison d'être** et le **mode opératoire** de
    chaque passe.

## À l'ouverture : l'analyse de départ

Avant d'écrire du code :

0. **Trier et regrouper les issues existantes** — **avant** la cartographie et le plan. Voir
   ci-dessous : c'est l'étape qui décide de quoi le chantier est fait.
1. **Cartographier l'existant.** Repérer les **patterns déjà en place** qui répondent (au moins en
   partie) au besoin, pour les **réutiliser** plutôt que réinventer. La plupart des extensions du
   socle se calquent sur un pattern existant (`Multibinder<ActiviteAccueil>`, contrats `Ouvrir*`,
   patron DAO, `Capture*`…). Voir [Patterns et principes](patterns.md).
2. **Rédiger un plan** : découpage, contraintes d'architecture ([Architecture](architecture.md) et les
   règles ArchUnit de [Tests et qualité](tests-et-qualite.md)), risques, ordre des paliers.
3. **Découper en issues** reliées à un **EPIC** (une issue « parapluie » avec la task-list des
   sous-issues). Chaque sous-issue porte son palier et ses dépendances.

### Étape 0 : le triage, avant tout le reste

**Rien ne garantit qu'une issue soit rattachée au bon chantier.** Elles naissent une par une, souvent
en passe 9 d'une clôture, avec le vocabulaire du chantier qui les a trouvées plutôt que celui du
problème qu'elles décrivent. Deux issues sur le même sujet, écrites depuis deux angles, ne se
ressemblent pas — et le recoupement ne se découvre qu'au **conflit de fusion**, quand deux chantiers
ont déjà construit deux chemins.

Avant de cartographier quoi que ce soit, donc :

1. **Balayer les issues ouvertes**, pas seulement celles qu'on croit concernées. Le tri se fait par
   **concept**, pas par mot-clé : « la sévérité s'écrit dans le texte » et « des avertissements vivent
   hors du système de restitution » sont le même sujet sous deux noms.
2. **Chercher les EPIC vivants** qui pourraient déjà couvrir le besoin, et **les issues fermées**
   qui l'ont différé : une issue qui dit « différé de #N » signale un parent, éventuellement clos, dont
   la moitié restante n'a plus de toit.
3. **Décider des rattachements** : une issue appartient au chantier qui traite sa **cause**, pas celui
   qui a remarqué son symptôme. Quand deux chantiers se recoupent, **découper le périmètre
   explicitement** et l'écrire dans les deux, plutôt que de laisser la fusion arbitrer.
   **Vérifier ce qui est déjà pris** : `gh issue list --assignee "*"` donne la liste, et le commentaire
   de prise dit le chantier, la branche et le remède envisagé (voir ci-dessous). Une revendication
   **ancienne se vérifie** au lieu de se croire — branche vivante ? PR ouverte ? — parce qu'une
   revendication oubliée fait passer une issue libre pour prise.
4. **Recadrer titre et corps** des issues déplacées. Un recadrage laissé en commentaire sous un corps
   périmé ne recadre rien : qui lit en diagonale retient la première version.

!!! warning "Pourquoi cette étape existe"
    Elle a été ajoutée après un cas réel. La passe 7 d'un chantier a compté 28 endroits écrivant leur
    sévérité dans du texte, et en a fait une issue. Une autre issue couvrait déjà **six des huit cas les
    plus profonds**, avec un remède plus juste — et son prérequis a fusionné pendant que le doublon
    s'écrivait. Le recoupement n'a été vu qu'en lisant un commit apparu sur `main`.

    **Un audit de clôture produit un comptage, pas une lecture.** Le comptage était exact ; il mélangeait
    deux problèmes de profondeur différente, et l'un des deux avait déjà une analyse ailleurs.

### Au commencement de chaque issue : rappeler ce qu'on fait et pourquoi maintenant

Un chantier s'enchaîne vite : issue, PR, CI, fusion, issue suivante. À ce rythme, le **pourquoi** se
perd — celui qui suit le fil (ou le relit trois semaines plus tard) voit une succession de correctifs
sans savoir ce qu'ils construisent.

**Avant d'ouvrir la première ligne de code d'une issue**, énoncer trois choses :

- **ce qu'il y a à faire**, en une phrase, dans les termes du problème et non de la solution ;
- **pourquoi maintenant** : ce qui la rend traitable (un prérequis fusionné, une mesure qui vient de
  tomber) ou urgente (elle bloque autre chose) ;
- **dans quelle continuité** elle s'inscrit : de quel chantier elle vient, quelle issue elle suit, ce
  qu'elle rend possible ensuite.

Le troisième point est celui qu'on saute, et c'est le seul qui ne se retrouve pas après coup. Une issue
sans continuité écrite devient un correctif isolé dont personne ne sait s'il a été fini.

### Et se signaler : dire qu'on la prend, et ce qu'on va faire

Ces trois phrases ne servent pas qu'à soi. **Elles se déposent en commentaire sur l'issue**, et l'issue
est **assignée** à qui la prend.

Les deux ensemble, pas l'un ou l'autre, parce qu'ils ne servent pas à la même chose :

- **l'assignee est le signal machine.** `gh issue list --assignee "*"` répond « voici tout ce qui est
  pris » en une commande. Un commentaire, lui, oblige à ouvrir chaque issue pour savoir ;
- **le commentaire porte ce que l'assignee ne dit pas** : de quel **chantier** l'issue relève, sur quelle
  **branche** le travail se fait, et surtout **quel remède est envisagé**.

Ce dernier point est le vrai gain, et il dépasse la simple réservation. Deux personnes peuvent voir le
même défaut et imaginer deux corrections dont l'une est meilleure ; si chacune est annoncée, le
désaccord se règle **avant** le code. Sinon il se règle au moment de choisir laquelle des deux branches
on jette.

**Un signalement se relâche.** Quand on s'arrête — reporté, bloqué, abandonné — on **retire l'assignee et
on le dit**. Une revendication oubliée depuis trois semaines est **pire que rien** : elle fait passer une
issue libre pour prise, et personne ne la reprendra. Au triage (étape 0), une revendication ancienne se
**vérifie** — branche vivante ? PR ouverte ? — au lieu de se croire.

!!! warning "Ce que le signalement ne couvre pas"
    Il répond à « **cette issue est-elle prise ?** ». Il ne répond **pas** à « **cette issue est-elle la
    même que celle-là, sous d'autres mots ?** » — et c'est cette seconde question qui a produit le
    doublon le plus coûteux du dépôt : deux issues sur le même sujet, écrites depuis deux angles, ne se
    ressemblent pas, et aucune n'était assignée.

    Le signalement est un **filet**, pas une garantie : il repose sur la discipline, et la discipline
    lâche exactement quand ça va vite, c'est-à-dire quand les collisions arrivent. Il complète l'étape 0,
    il ne la remplace pas.

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

Chaque ADR **déclare comment elle est vérifiée** ([ADR 2465](decisions/2465-une-adr-declare-comment-elle-est-verifiee.md)) : une puce `- **Vérification** : certaine | probable | humaine — <référence>` dans son en-tête, au même titre que `Statut` et `Chantier`. Un garde-fou fait rougir la CI si elle manque, ou si le test/script nommé n'existe pas. `certaine` nomme un test ou script déterministe ; `probable` nomme un script de suspects et son **cliquet** ; `humaine` donne le motif, et peut adjoindre une **loupe**. Voir la section « Comment une ADR est vérifiée » de l'[index des décisions](decisions/index.md).

### 4. Passe de doc utilisateur

Documenter le chantier pour les **utilisateurs** dans le site produit (`docs/`), avec **autant de
captures que nécessaire**. Les aperçus sont régénérés en CI : ajouter/mettre à jour les classes
`Capture*` et le manifeste, cf. [Captures d'écran](captures.md). Une fonctionnalité visible sans
capture est une fonctionnalité à moitié livrée.

### 5. Passe de brief projet

Le **brief projet** est le document de **conception** vivant du produit : le besoin, les **parcours
utilisateurs** (P1-P10), les maquettes, le modèle conceptuel. Ce n'est **pas** un sujet pédagogique -
son lecteur est un **contributeur** du produit, pas un étudiant. Quand un chantier change un de ces
**éléments de conception** (un parcours, une maquette, le modèle de données, une contrainte produit),
répercuter l'évolution dans le brief pour qu'il reste aligné avec le produit réellement livré. Un brief
qui décrit une version périmée du produit égare son lecteur. C'est **rarement** « sans objet » pour un
chantier qui touche au comportement ou à la conception du produit.

Ses sources sont **dans ce dépôt**, sous [`brief/`](https://github.com/echonuit/vigiechiro-pr-companion/tree/main/brief),
aux côtés de `docs/` et `dev-docs/`. Cette passe se fait donc **dans la pull request du chantier**,
comme les deux passes de documentation qui précèdent : il n'y a plus de second dépôt ni de seconde
pull request. Le dépôt `echonuit/brief` ne porte plus que le site construit, publié automatiquement
sur [brief.echonuit.fr](https://brief.echonuit.fr/) ; le modifier n'a aucun effet.

Prévisualiser le rendu avant de livrer : `mkdocs serve -f mkdocs-brief.yml`.

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

**Un zéro se confirme à la main.** L'inventaire se mène volontiers à coups de `grep` - bonne amorce,
mais qui se trompe **dans les deux sens**. Un audit réel de la suite E2E en a produit **quatre** de
suite : chercher le nom d'un écran remonte un **homonyme** (« recherche » → le protocole
`PointFixeRecherche`) ; chercher par **clé de vue** rate les parcours qui pilotent les **services**
(`importation`, `lot`, `qualification` ressortaient à zéro alors qu'ils sont couverts) ; chercher une
commande CLI par son **nom de classe** la déclare non testée quand le test l'invoque en kebab-case - et
l'inverse quand le test instancie la classe. Il a fallu **croiser deux signaux** pour obtenir la vraie
réponse : sur 41 commandes, **zéro** sans test, là où les greps naïfs en annonçaient jusqu'à 20. Donc :
un « aucun test » sorti d'un grep n'est qu'une **hypothèse**, à confirmer en ouvrant les fichiers
**avant** d'en faire une issue. Une issue fausse coûte plus cher que le trou qu'elle prétend signaler.

**Un garde-fou de non-régression se vérifie en le voyant rouge.** Un test écrit pour empêcher un défaut
de revenir ne prouve rien tant qu'on ne l'a pas vu **échouer** avec le défaut en place. Deux gestes,
complémentaires : **PIT ciblé** sur les classes du chantier (`-Pmutation`, exhaustif là où il
s'applique) et la **mutation à la main** pour ce que PIT ne mute pas - attribut d'annotation, câblage,
FXML, sonde réseau. Le mode d'emploi et les quatre contre-exemples qui ont motivé la règle sont dans
[Tests et qualité](tests-et-qualite.md#un-garde-fou-de-non-regression-se-verifie-en-le-voyant-rouge).

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

**Regarder ne suffit pas : il faut regarder d'assez près pour que l'affirmation tienne.** Un aperçu
s'ouvre à sa taille naturelle, où un glyphe fait douze pixels. À cette échelle, un **pictogramme
monochrome fin est indistinguable du vide** : on conclut « absent » sur ce qui est simplement discret.
La clôture de #1933 en a fait les frais, en publiant trois « preuves » d'absence dont **une seule**
était exacte - recadrées et agrandies ×3, deux des glyphes se rendaient, et un troisième se rendait
en **forme méconnaissable**, ce que personne n'avait envisagé. Avant d'écrire qu'un élément manque,
qu'un texte est coupé ou qu'une couleur a bougé, **recadrer la zone et l'agrandir**. Trente secondes,
et l'affirmation devient un constat.

Corollaire pour la rédaction : un pictogramme littéral n'a pas deux issues mais **trois** - rendu,
absent, ou déformé. La troisième est la pire pour l'utilisateur, puisqu'elle se lit comme une faute de
frappe dans le libellé.

### 9. Passe d'identification des nouveaux chantiers

Un chantier en révèle d'autres (dette assumée, palier différé, idée née en chemin). Les **cadrer** et
**créer les issues** correspondantes (reliées à un nouvel EPIC si elles forment un ensemble), pour ne
pas perdre le contexte encore frais.

### 10. Phase de bilan

Une **synthèse** courte : ce qui a été livré, la **dette restante**, les **décisions** prises et leur
pourquoi. Elle se dépose dans le corps de l'EPIC (au moment de le clore) et, si elle change une
règle du dépôt, se répercute dans `CLAUDE.md` / `CONTRIBUTING.md`. Le bilan **renvoie** aux
[ADR](decisions/index.md) écrites en passe 3 plutôt que de redérouler le raisonnement des décisions.

**Et il se montre.** Un chantier d'IHM se juge sur ce qu'il change à l'écran, or le bilan est un texte :
il décrit des captures que son lecteur n'a pas sous les yeux. La passe 8 les a pourtant toutes ouvertes,
recadrées et regardées — ce travail reste dans la tête de qui l'a fait.

La passe 10 produit donc un **artefact visuel** : une page qui met les états **avant / après** côte à
côte, une ligne par conséquence visible du chantier, avec la phrase qui dit ce qu'on doit y voir. Elle
sert deux fois :

- **pour valider** — c'est le seul support sur lequel un relecteur peut dire « non, ça ne va pas » sans
  relire le code. Un bilan qui affirme « les huit boutons s'affichent en entier » demande qu'on le
  croie ; une capture le montre ;
- **pour dater** — elle fige à quoi ressemblait l'écran à la clôture, ce que le prochain chantier pourra
  comparer.

Elle est **soumise avant de clore l'EPIC**, pas après : son objet est d'obtenir un assentiment, pas de
documenter une décision déjà prise.

!!! tip "Ce qu'elle contient, au minimum"
    Une entrée par écran touché : la capture **avant**, la capture **après**, et une phrase qui nomme ce
    qui a changé. Les défauts trouvés en chemin y figurent aussi, **recadrés et agrandis** — un glyphe de
    douze pixels ne se juge pas à l'échelle 1 (cf. passe 8). Ce qui n'a **pas** été corrigé y a sa place :
    une troncature laissée en l'état, montrée et assumée, vaut mieux qu'une omission.

## Les suites d'une clôture se closent aussi

La passe 9 crée des issues ; la passe 10 les nomme « dette restante » et clôt l'EPIC. Ces issues, une
fois livrées, forment un **nouveau delta** - et rien ne les rattrape si l'on considère que le chantier
est fini.

Le dépôt l'a vécu **trois fois** : les suites de l'EPIC #1662 ont formé l'EPIC #1863, dont les suites
ont formé le delta clos par #1920 ; les suites de #1838 ont eu leur propre clôture (#1921). Le patron
est donc régulier, pas accidentel.

**Les suites d'un chantier se closent par les mêmes 10 passes**, appliquées à leur seul delta
(`git log <sha-de-la-clôture-précédente>..origin/main`, filtré sur les commits du chantier). C'est peu
coûteux - le périmètre est étroit - et c'est là qu'on trouve ce que le travail de suite a laissé
derrière lui : une capacité livrée d'un seul côté, un état visuel sans capture, une règle construite par
quatre PR qu'aucune ADR ne porte.

**Un bilan est une hypothèse, pas un verdict.** Sa section « dette restante » décrit ce qu'on croyait
comprendre au moment de l'écrire. Le bilan de #1864 affirmait d'un défaut d'horodatage qu'« une
troncature de fuseau est confirmée, mais elle n'explique pas tout - au moins deux facteurs, dont un qui
détruit la fin de nuit ». C'était faux : il n'y avait qu'un facteur, mais il **composait** à chaque
cycle. Ce qui a tranché n'est pas un raisonnement plus fin, c'est d'être allé **lire l'état réel** sur la
plateforme. Quand une suite est traitée, **relire ce que le bilan en disait** et le corriger s'il s'est
trompé : une analyse fausse laissée en place oriente le chantier suivant.

## Modèle de clôture (à coller dans l'EPIC)

```markdown
## Ouverture de chantier
- [ ] 0. Triage : issues ouvertes balayées **par concept**, EPIC vivants et issues « différées de #N » cherchés, rattachements décidés, titres/corps recadrés
- [ ] 1. Cartographie de l'existant (patterns réutilisables)
- [ ] 2. Plan (découpage, contraintes, risques, ordre des paliers)
- [ ] 3. Issues créées et reliées à l'EPIC

## Clôture de chantier
- [ ] 1. Audit d'intégration (rebase sur `main`, points d'accroche, régressions)
- [ ] 2. Cohérence CLI ↔ UI (capacités métier exposées des deux côtés, ou « sans objet »)
- [ ] 3. Doc développeur (dev-docs) à jour + ADR pour toute décision structurante (dev-docs/decisions/)
- [ ] 4. Doc utilisateur (docs/) + captures
- [ ] 5. Brief projet (`brief/`, dans la PR du chantier) répercuté si un élément de conception change
- [ ] 6. Tests : inventaire des usages **depuis le diff** (chemins non nominaux, parité CLI ↔ IHM), E2E qui **traversent les coutures**, non-automatisable reporté en **recette**
- [ ] 7. Harmonisation : **audit global** (ce qui ressemble / bénéficierait, exhaustif) puis **refactoring de conceptualisation** (lisibilité ; duplication et abstraction = outils) ; **choix, doutes, conséquences discutés avec l'utilisateur**
- [ ] 8. Revue visuelle : **toute conséquence visible** couverte par une capture (captures **ajoutées** si besoin), régénérées et ouvertes une par une
- [ ] 9. Nouveaux chantiers identifiés + issues créées
- [ ] 10. Bilan (livré / dette / décisions) **+ artefact visuel avant/après soumis avant de clore**
```

## Modèle de commencement d'issue (à **commenter sur l'issue**, avant la première ligne de code)

À déposer en commentaire, **et assigner l'issue** dans la foulée.

```markdown
**Pris par** : chantier <EPIC ou thème> · branche `<nom-de-branche>`
**Ce qu'il y a à faire** : <une phrase, dans les termes du problème>
**Pourquoi maintenant** : <ce qui la rend traitable ou urgente>
**Dans quelle continuité** : <le chantier d'où elle vient, l'issue qu'elle suit, ce qu'elle permet ensuite>
**Remède envisagé** : <la piste retenue, pour qu'un désaccord se voie avant le code>
```

Et quand on s'arrête sans avoir fini — reporté, bloqué, abandonné :

```markdown
**Reposée** : <ce qui a été fait, ce qui bloque, ce qu'il reste>. Assignee retiré.
```
