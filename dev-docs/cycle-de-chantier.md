# Cycle de vie d'un chantier

Un **chantier** est un lot de travail d'ampleur **EPIC** : une évolution qui ne tient pas dans une
seule PR et se découpe en plusieurs (ex. l'EPIC « Réglages auto-découverts → feature = plugin »). Là
où [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) décrit une **PR** et
[CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)
le **flux de contribution**, cette page décrit le niveau au-dessus : comment on **ouvre** et on
**clôt** un chantier entier.

Le principe : un chantier ne se termine pas au dernier `feat:` mergé. Une fois le cœur livré, une
**clôture en 12 passes** (numérotées **0 à 11**) garantit que l'évolution est intégrée, cohérente
entre les deux surfaces (IHM et CLI), documentée, testée, harmonisée, **regardée**, et que la suite
est cadrée.

!!! info "Pourquoi la numérotation commence à 0 et pas à 1"
    Les passes **1 à 9 ne changent pas de numéro**, et c'est délibéré. Le dépôt compte **42 citations**
    de passes numérotées, dont **35 dans `dev-docs/decisions/`** - des documents que la règle déclare
    **immuables**. Décaler la série rendrait fausses 35 références qu'on s'interdit d'éditer, sans que
    rien ne rougisse. Les deux passes ajoutées se placent donc **aux extrémités** : la 0 en tête, la 10
    et la 11 en queue.

    Le « 0 » n'est pas un pis-aller : l'ouverture d'un chantier a **déjà** son étape 0, et c'est déjà
    *relire l'existant avant d'agir*. La même figure aux deux bouts.

!!! note "Où est la règle courte ?"
    La version concise pour les contributeurs vit dans la section « Cycle de vie d'un chantier » de
    [CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md).
    Cette page en est la version approfondie : la **raison d'être** et le **mode opératoire** de
    chaque passe.

## À l'ouverture : l'analyse de départ

Avant d'écrire du code :

0. **Trier et regrouper les issues existantes** : **avant** la cartographie et le plan. Voir
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
ressemblent pas : et le recoupement ne se découvre qu'au **conflit de fusion**, quand deux chantiers
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
   **ancienne se vérifie** au lieu de se croire (branche vivante ? PR ouverte ?) parce qu'une
   revendication oubliée fait passer une issue libre pour prise.
4. **Recadrer titre et corps** des issues déplacées. Un recadrage laissé en commentaire sous un corps
   périmé ne recadre rien : qui lit en diagonale retient la première version.

!!! warning "Pourquoi cette étape existe"
    Elle a été ajoutée après un cas réel. La passe 7 d'un chantier a compté 28 endroits écrivant leur
    sévérité dans du texte, et en a fait une issue. Une autre issue couvrait déjà **six des huit cas les
    plus profonds**, avec un remède plus juste : et son prérequis a fusionné pendant que le doublon
    s'écrivait. Le recoupement n'a été vu qu'en lisant un commit apparu sur `main`.

    **Un audit de clôture produit un comptage, pas une lecture.** Le comptage était exact ; il mélangeait
    deux problèmes de profondeur différente, et l'un des deux avait déjà une analyse ailleurs.

### Interroger le graphe du dépôt, pour ce que `grep` ne relie pas

Le tri « par concept, pas par mot-clé » exigé ci-dessus se heurte à un outil qui ne sait chercher que
des **chaînes**. Le dépôt se donne donc un **graphe de connaissances**, reconstruit régulièrement dans
`graphify-out/` (hors du suivi Git) : environ **21 800 nœuds** et **77 500 arêtes** couvrant le code,
les workflows, les tests `bats`, la documentation utilisateur, les dev-docs et le brief, **mêlés dans le
même graphe**.

```bash
graphify query "<question>" --budget 2500   # depuis la racine du dépôt
```

Ce qu'il apporte et que `grep` ne peut pas donner :

- les arêtes `calls` descendent au niveau **méthode** : « qui appelle réellement ceci ? » et,
  symétriquement, « ce code sert-il encore à quelqu'un ? » ;
- les arêtes `conceptually_related_to` répondent à « **qui d'autre fait X ?** » quand X est une **idée**
  et non un identifiant ;
- il **traverse les corpus** : quelles maquettes du brief décrivent ce composant, quelles pages
  documentent cet écran, quelles ADR citent ce workflow.

Trois moments l'appellent, et ce sont les trois où l'on croit déjà savoir : l'**ouverture** (étape 0 et
cartographie), l'**investigation d'un défaut** (chercher le jumeau), et l'**audit global de la passe 7**.

!!! tip "Ce qu'il a trouvé, et que les `grep` avaient manqué"
    - passe 7 du lot 4 de #3151, « qui d'autre dérive un département ? » : `grep` sur
      `departementDeCarre` et `RegionDuCarre` donnait **deux** écritures de la règle, le graphe en a
      montré une **troisième**, `CarreGeo.departement()`, qui ne cite aucun de ces noms. Ses arêtes ont
      révélé du même coup qu'elle n'avait **aucun appelant** ;
    - #3197 : `ServiceReset.executer` et `ServiceImport.ecraserEtImporter` écrivent eux aussi dans
      `sauvegardes/` - deux sources d'accumulation que l'issue ne nommait pas ;
    - #2739 : un **troisième** job (`publish`) dans `release.yml`, absent de l'en-tête du fichier, et
      les deux ADR qui référencent ce workflow. La forme du remède en a changé.

!!! warning "Deux limites, et la seconde décide de comment on lit sa sortie"
    **Il ne modélise que NOTRE code.** Aucun nœud pour le JDK ni pour les bibliothèques : demander qui
    appelle `Files.readAllLines` rend zéro, et un zéro se lit comme une absence. Sur ces questions-là
    `grep` est le bon outil et le graphe est muet - mesuré sur #3222, six lectures d'API JDK.

    **Sa traversée est bruitée**, il photographie un commit (`built_at_commit`) donc il **vieillit**, et
    14 % de ses arêtes sont **inférées**. Sa sortie est une **hypothèse à confirmer** par une mesure
    exacte - lecture du fichier, `grep` ciblé -, **jamais un inventaire**. C'est exactement le régime que
    la passe 6 impose déjà au `grep` : *un zéro se confirme à la main*. Le graphe **oriente** la
    recherche, il ne remplace pas la lecture.

### Au commencement de chaque issue : rappeler ce qu'on fait et pourquoi maintenant

Un chantier s'enchaîne vite : issue, PR, CI, fusion, issue suivante. À ce rythme, le **pourquoi** se
perd : celui qui suit le fil (ou le relit trois semaines plus tard) voit une succession de correctifs
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

**Un signalement se relâche.** Quand on s'arrête (reporté, bloqué, abandonné) on **retire l'assignee et
on le dit**. Une revendication oubliée depuis trois semaines est **pire que rien** : elle fait passer une
issue libre pour prise, et personne ne la reprendra. Au triage (étape 0), une revendication ancienne se
**vérifie** (branche vivante ? PR ouverte ?) au lieu de se croire.

!!! warning "Ce que le signalement ne couvre pas"
    Il répond à « **cette issue est-elle prise ?** ». Il ne répond **pas** à « **cette issue est-elle la
    même que celle-là, sous d'autres mots ?** » : et c'est cette seconde question qui a produit le
    doublon le plus coûteux du dépôt : deux issues sur le même sujet, écrites depuis deux angles, ne se
    ressemblent pas, et aucune n'était assignée.

    Le signalement est un **filet**, pas une garantie : il repose sur la discipline, et la discipline
    lâche exactement quand ça va vite, c'est-à-dire quand les collisions arrivent. Il complète l'étape 0,
    il ne la remplace pas.

## Pendant l'issue : rouge, vert, refactor, autant de fois qu'il le faut

Le cycle décrit longuement comment on **clôt**. Ce qui se passe entre le commentaire de prise et la
pull request tenait, jusqu'à #3505, en une consigne implicite et fausse :
[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) numérotait « Tester » en **étape 8 sur 8**.
La page qui apprend à écrire une fonctionnalité enseignait l'ordre inverse de celui qu'on veut.

### C'est une BOUCLE, pas une étape

Rouge, vert, refactor n'est pas une checklist qu'on parcourt une fois par issue. C'est un **tour**, et
une issue en compte **autant que de petits pas** : quelques minutes chacun, souvent plusieurs dizaines
avant que le comportement soit complet.

> Une issue = N tours. Pas un tour avec N assertions dedans.

**Un petit pas est le plus petit comportement observable** qu'on puisse rendre rouge puis vert : pas
« la fonctionnalité », pas « la classe », pas « la méthode ». *Le refus quand la date manque*. *La
conversion d'une borne quand le point est connu*. *Le cas où la liste est vide*. On écrit le test de
ce pas-là, on le voit rouge, on écrit **le minimum** qui le rend vert, on regarde ce qu'il y a à
retravailler, et on recommence avec le pas suivant.

Trois raisons, et la troisième est celle qui décide :

- **le diagnostic est gratuit.** Quand un pas casse, la cause est dans les trois lignes qu'on vient
  d'écrire. Sur un grand pas, elle est quelque part dans une heure de travail ;
- **le refactor devient possible.** On ne retravaille sereinement qu'un code couvert : chaque tour
  élargit le filet sous les pas suivants. Un refactoring tenté après coup, sur du code écrit d'un bloc,
  se fait sans filet - c'est-à-dire qu'il ne se fait pas ;
- **le pas suivant se choisit en connaissance de cause.** Le vert précédent apprend quelque chose sur le
  domaine, et il arrive qu'il démente le plan. Sur #3483, le tour qui figeait les états capturés a rendu
  visible la monotonie de la fraction, qui n'était dans aucun plan.

!!! tip "Le signal que le pas était trop grand"
    **Le rouge dure.** Si l'on reste plus de quelques minutes en rouge, ou s'il faut écrire plusieurs
    classes pour revenir au vert, le pas était trop gros : on **revient au dernier vert** et on le coupe
    en deux. Rester longtemps en rouge fait perdre ce que la boucle apporte - on retombe dans « j'écris
    tout, je teste après », avec un test écrit avant en guise d'alibi.

    Le corollaire vaut aussi dans l'autre sens : un pas qui passe au vert **du premier coup, sans rien
    écrire**, dit que le test ne testait rien de neuf.

### Le rouge d'abord, parce qu'il ne coûte rien à ce moment-là

Le dépôt tient déjà la moitié de cette règle, sous un autre nom : **un garde-fou de non-régression se
vérifie en le voyant rouge** (passe 6, [ADR 2748](decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)).
Le rouge du TDD est cette même exigence, déplacée **avant** le code, là où elle est gratuite.

Après coup, elle se paie. Sur #3483, la règle ArchUnit qui interdit à un outil de capture de lire
l'horloge a dû être vérifiée en **réintroduisant le défaut à la main**, en relançant, puis en
restaurant. Trois gestes, un risque d'oublier le dernier, et une confiance qui repose sur le fait
qu'on ait bien tout remis en place. Écrite d'abord, elle était rouge sans cérémonie.

### Sur un défaut, le premier test REPRODUIT

Une correction n'a pas de « comportement attendu » tant qu'on n'a pas compris le défaut. Le premier
geste n'est donc pas « écrire le test », c'est **écrire le test qui reproduit** : il échoue parce que
le produit est faux, et il passe au vert quand il ne l'est plus. Un test de caractérisation reste du
rouge d'abord.

Le corollaire vaut pour les gardes de **forme** ([ADR 3412](decisions/3412-un-alias-n-est-pas-une-police.md)) :
on ne sait ce qu'il faut interdire qu'une fois le défaut lu. Le garde s'écrit donc après l'analyse mais
**avant** le correctif, et se confronte aux **lignes fautives d'origine** - c'est ce qui a fait
abandonner un garde textuel sur les fuseaux, qui aurait manqué les deux moitiés du défaut.

**Et avant de corriger, chercher le jumeau.** Un défaut a rarement un seul site : la question n'est pas
« où est ce symptôme ? » mais « **qui d'autre fait la même chose ?** ». C'est le moment d'interroger le
[graphe du dépôt](#interroger-le-graphe-du-depot-pour-ce-que-grep-ne-relie-pas) plutôt que de se fier à
un `grep` sur le nom de la méthode fautive : sur #3197, il a désigné deux services qui écrivaient au
même endroit sans que l'issue les nomme. Le test qui reproduit doit couvrir **tous** les jumeaux
trouvés, sans quoi le correctif en laisse un derrière lui.

### Un rouge inattendu est une trouvaille

Un test qui échoue **pour une autre raison que celle qu'on attendait** vient de dire quelque chose. Le
réflexe est de corriger jusqu'au vert ; le bon geste est de **lire le message avant de corriger**.

Vécu sur #3483 : le test qui figeait les trois états capturés annonçait « ~10 s » là où on attendait
« ~17 s ». La cause n'était pas dans le correctif mais dans la **monotonie de la fraction** (#814) :
une fraction plus basse posée après une plus haute ne redescend pas, et l'estimation se calcule donc
sur l'ancienne. La capture était juste par accident de séquence, et rien ne le disait. Sans ce
rouge-là, la règle serait restée invisible jusqu'au jour où un outil aurait posé une fraction
décroissante.

### REFACTOR : à chaque tour, et à la bonne échelle

**Le refactoring appartient au cycle, pas seulement à la clôture.** Il est **tenté à chaque tour de
boucle**, c'est-à-dire des dizaines de fois par issue : pas nécessairement appliqué, mais
systématiquement **regardé**. C'est la troisième phase du tour, pas une étape de fin d'issue - la sauter
« parce qu'on y reviendra » est la façon habituelle de ne jamais y revenir. Le moment le moins cher pour
retravailler un code est celui où l'on se souvient encore pourquoi il est ainsi, et ce moment dure un
tour.

Ce qui se regarde à ce moment-là est **petit** : un nom qui ne dit pas ce que fait la méthode, une
duplication qui vient d'apparaître entre le pas précédent et celui-ci, une condition qui gagnerait à
être nommée. Ce qui déborde du pas courant n'appartient pas au tour : ça se note, et ça revient en
passe 7.

Il ne remplace pas la **passe 7** et n'entre pas en concurrence avec la règle des petites PR, parce que
ce sont **deux échelles** :

| | REFACTOR du cycle | Passe 7, harmonisation |
| --- | --- | --- |
| Portée | le code qu'on vient de toucher | l'application entière |
| Moment | à chaque barre verte | à la clôture du chantier |
| Filet | le test qu'on vient de rendre vert | la suite complète |
| Décision | seul | **discutée** avec la direction |

Sans cette frontière, l'une des deux règles cède à l'autre. Avec elle, elles ne se croisent jamais.

Ce que le cycle attrape et que la passe 7 attrape mal : sur #3442, le PMD `GodClass` a mordu
**pendant** l'issue et forcé l'extraction de `HorairesDistants`. Le résultat porte un **concept nommé**
- *une borne distante se lit dans le fuseau de son point* - parce qu'il a été écrit par quelqu'un qui
avait encore la raison en tête. Une passe 7 aurait produit la même extraction, au bon endroit, sous un
nom quelconque.

### Quand la boucle s'arrête : la mutation, tout de suite

La boucle tourne **tant que le comportement n'est pas complet**. Quand elle s'arrête - plus de petit
pas à faire, la fonctionnalité tient debout - vient la seule mesure qui dise ce que tous ces tours ont
vraiment couvert.

La **passe 6** exige PIT. À la clôture, c'est-à-dire souvent **plusieurs pull requests après** que le
code a été écrit : le trou découvert porte alors sur du code déjà livré, et le contexte est froid.

**PIT tourne donc dès qu'un comportement est complet**, sur les classes que l'issue vient de livrer. La
passe 6 devient alors une **vérification que ça a été fait**, pas la première exécution.

Le cadrage, sans lequel la mesure ne vaut rien :

- **cibler les classes pures.** Une façade de délégation ne rend que des survivants sans valeur ;
- **une phase est obligatoire** (`test-compile`), sinon la mesure meurt en silence (`MINION_DIED`) ;
- **le pourcentage ne dit rien.** Ce sont les **survivants**, lus un par un, qui parlent : un **vrai
  trou** (on écrit le test), un **défensif inatteignable** (on l'assume, sans test creux), un
  **artefact de ciblage** (on élargit `targetTests` et on remesure) ;
- **une couverture de mutation dit « aucun test ne couvre cette ligne », jamais « cette ligne est
  atteignable »** ([ADR 3451](decisions/3451-un-invariant-tenu-par-la-base-se-double-d-un-refus.md)).
  Confondre les deux fait écrire un correctif pour un défaut qui n'existe pas ; c'est arrivé.

Le mode d'emploi complet est dans [Tests et qualité](tests-et-qualite.md).

## À la clôture d'une issue : ce qu'on laisse derrière soi

Une issue se ferme sur deux textes qu'on relira dans six mois **sans le fil** : son **corps**, et celui
de sa **pull request**.

### Le corps porte la vérité, les commentaires portent le journal

Une issue vit : la prémisse se révèle fausse, une mesure contredit l'intuition, le remède change. Rien
de cela ne doit rester **uniquement** en commentaire.

- **le corps** dit l'**état courant de la vérité** : ce qu'on fait, pourquoi, ce qui a été décidé ;
- **les commentaires** portent le **journal** : mesures, trouvailles incidentes, pistes essayées.

D'où la règle : **tout commentaire qui change la lecture de l'issue est suivi d'une édition du corps.**
Le commentaire reste comme trace, le corps porte la conclusion.

C'est la généralisation d'une règle qui n'existait qu'au triage (étape 0) : « un recadrage laissé en
commentaire sous un corps périmé ne recadre rien ». Elle vaut pour toute la vie d'une issue, pas
seulement quand on la déplace d'un chantier à l'autre.

!!! warning "Deux dettes de ce type, laissées dans le dépôt"
    Sur #3451, une prémisse fausse - une mesure de mutation lue comme « ce code est atteignable » - a
    été corrigée **en commentaire**, le corps gardant la version fausse. Sur #3439, une mesure erronée
    a connu le même sort. Qui ouvre ces issues aujourd'hui lit d'abord l'erreur, et la correction
    ensuite, s'il descend jusque-là.

### Le test de lecture à froid, avant de fusionner

Avant de fusionner : **le corps de la pull request et celui de l'issue se lisent-ils correctement dans
six mois, sans la discussion ?** Ils doivent dire ce qui a été fait et pourquoi, pas retracer les
hésitations qui y ont mené.

Ce n'est pas de la cosmétique. Le **titre de la pull request devient le sujet du commit de squash**, et
son corps est ce qu'atteint quiconque remonte depuis `git log`. C'est la seule trace qui survive à la
fermeture de l'onglet.

## À la clôture : les 12 passes

Elles s'exécutent **dans l'ordre** : la relecture des ADR remet l'existant en tête avant qu'on touche
à quoi que ce soit, l'audit d'intégration peut révéler du travail à faire avant de documenter, la
cohérence CLI peut révéler une commande à ajouter (qui sera alors documentée et testée par les passes
suivantes), l'harmonisation peut **casser un écran sans casser un test** (d'où la revue visuelle
**juste après** elle), la revue visuelle peut faire émerger de nouveaux chantiers, les ADR s'écrivent
quand toutes les décisions sont prises, et le bilan vient en dernier parce qu'il y renvoie.

### 0. Relecture des ADR existantes

Un chantier de plusieurs semaines écrit du code sous des règles qu'il a cessé de voir. Cette passe les
remet en tête **avant** que la clôture ne commence, et pose une seule question de vérification :

> Le chantier a-t-il **contredit** une décision existante, et si oui, l'a-t-il fait exprès ?

Un chantier a parfaitement le droit de dépasser une ADR : #3442 a rendu faux ce que l'[ADR 3406](decisions/3406-une-nuit-porte-le-fuseau-de-son-site.md)
assumait sur l'outre-mer, et c'était le progrès attendu. Ce qui n'est pas permis, c'est que le
dépassement soit **silencieux** : une ADR qu'on contredit sans le dire laisse deux règles opposées dans
le dépôt, et le prochain lecteur appliquera celle qu'il trouvera en premier.

La lecture se fait contre **`origin/main`**, pas contre la branche du chantier : d'autres chantiers ont
pu écrire des ADR pendant celui-ci, et le rebase de la passe 1 arriverait trop tard pour les découvrir.

!!! tip "Signal concret"
    `git log --oneline <sha-d-ouverture>..origin/main -- dev-docs/decisions/` liste les décisions
    apparues **pendant** le chantier : ce sont les plus susceptibles d'avoir été ignorées, puisqu'elles
    n'existaient pas quand le plan a été écrit.

    Et la question se pose aussi **à l'envers** : parmi les ADR que le chantier respecte, certaines
    régissent-elles du code **hors du delta** qu'il faudrait aligner ? C'est ce qui a manqué à une
    clôture où un garde de surface CLI (`cli-surface.bats`) avait été tenu à jour, mais pas le garde de
    comportement (`cli.bats`) que la même décision régit.

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

!!! warning "Confronter des inventaires, pas des exemples"
    Vérifier qu'*un* geste a son équivalent CLI produit une **parité de façade** : vraie sur le cas
    qu'on a regardé, fausse dès qu'on resserre le filtre comme on le fait en vrai. Ce qui se compare,
    ce sont **deux listes** - tous les critères que l'écran offre face à toutes les options que la
    commande accepte.

    Vécu à la clôture de l'EPIC #2790 : la passe avait constaté que l'export ZIP existait des deux
    côtés et que le critère « Lieu » manquait en CLI. Elle s'était arrêtée là. Une question d'usage
    posée après coup - « et à plus de 90 % de confiance ? » - a montré que la puce « Proba » manquait
    aussi, et que la dette annoncée était donc à moitié décrite (#2971).

    Corollaire : quand l'option existe déjà sous un nom **voisin**, la lire avant de conclure. En CLI,
    `--certitude` désigne la certitude déclarée par l'observateur, pas la probabilité Tadarida ; deux
    notions qui se ressemblent à l'oral et n'ont rien à voir dans les données.

### 3. Passe de doc développeur

Mettre à jour le **site dev** (`dev-docs/`, publié sous `…/dev/`) pour que l'architecture décrite
colle au code livré : [Architecture](architecture.md), [Patterns et principes](patterns.md),
[Injection (Guice)](injection.md), [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) si le
chantier a introduit un **nouveau pattern d'extension** que les futures features devront suivre.

Les **ADR ne s'écrivent plus ici** : elles se rédigent en **passe 10**, quand toutes les décisions du
chantier sont prises. Cette passe-ci ne traite que les pages de description.

#### Chercher ce qui est devenu FAUX, pas ce qu'on a à ajouter

« Mettre à jour pour que la doc colle au code » se lit spontanément comme « qu'ai-je à **ajouter** ? ».
Le mode de panne est l'inverse : une page qui décrivait fidèlement un mécanisme **remplacé** ne signale
rien, ne rougit nulle part, et se lit comme vraie.

Mesuré : #3439 a remplacé les rectangles de masque des aperçus, écrits à la main, par des rectangles
**dérivés de la scène**. `dev-docs/captures.md` a continué pendant une semaine à décrire « seize »
fichiers énumérés dans un script, et à qualifier de « non élucidée » une instabilité que le même
chantier venait d'élucider. Rien ne l'a signalé ; c'est un lecteur qui l'a trouvé.

L'instrument est mécanique : **partir des fichiers que le chantier a touchés, et chercher qui les
cite**, plutôt que de partir de sa mémoire.

```bash
git diff --name-only origin/main... | while read -r f; do
  grep -rl "$(basename "$f")" docs dev-docs brief 2>/dev/null
done | sort -u
```

Le nom d'une classe, d'un script ou d'un fichier suffit à trouver ses mentions ; pour un **concept**
qui n'a pas de nom stable, la même question se pose au
[graphe du dépôt](#interroger-le-graphe-du-depot-pour-ce-que-grep-ne-relie-pas), qui relie code, doc et
brief. Ce qui décrit un mécanisme disparu se corrige **ou se supprime** : une page à moitié vraie égare
plus qu'une page absente.

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

Le **second signal** le moins cher à obtenir est le [graphe du dépôt](#interroger-le-graphe-du-depot-pour-ce-que-grep-ne-relie-pas) :
ses arêtes `calls` entrantes disent qui exerce réellement une méthode, y compris depuis un test qui ne
cite pas son nom. Il ne dispense pas d'ouvrir le fichier - sa sortie est elle aussi une hypothèse - mais
deux hypothèses obtenues **par des chemins différents** valent mieux que la même deux fois.

**Un garde-fou de non-régression se vérifie en le voyant rouge.** Un test écrit pour empêcher un défaut
de revenir ne prouve rien tant qu'on ne l'a pas vu **échouer** avec le défaut en place. Deux gestes,
complémentaires : **PIT ciblé** sur les classes du chantier (`-Pmutation`, exhaustif là où il
s'applique) et la **mutation à la main** pour ce que PIT ne mute pas - attribut d'annotation, câblage,
FXML, sonde réseau. Le mode d'emploi et les quatre contre-exemples qui ont motivé la règle sont dans
[Tests et qualité](tests-et-qualite.md#un-garde-fou-de-non-regression-se-verifie-en-le-voyant-rouge).

**Un dispositif n'est pas toujours un test.** La règle vaut à l'identique pour un **job d'intégration
continue**, un **script** de vérification, un **cliquet** : tout ce dont le vert prétend dire quelque
chose. C'est même là que le faux vert est le plus difficile à voir, parce que personne ne relit un job
qui passe.

Mesuré sur #3450 : le job `fuseau-alternatif`, écrit pour rejouer toute la suite sous `America/Cayenne`,
passait le fuseau par `-Duser.timezone` sur la **ligne Maven**. Or surefire fabrique ses propres JVM
(`forkCount=1C`), qui n'en héritent pas. Le job aurait été **vert en rejouant la suite dans le fuseau du
runner**, c'est-à-dire en ne vérifiant rien de ce qu'il annonçait. Il a fallu passer par
l'**environnement**, et lui adjoindre un test qui **vérifie depuis l'intérieur de la suite** que le
fuseau annoncé est bien celui qui tourne.

Le contrôle tient en une phrase, et il se pose avant de croire un vert : **ce vert existerait-il si
c'était cassé ?** ([ADR 2748](decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md))

!!! note "PIT a normalement déjà tourné"
    Depuis #3505, la mutation se lance **quand un comportement est complet**, pendant l'issue et non à
    la clôture (voir « Pendant l'issue » ci-dessus). Cette passe **vérifie que ça a été fait** et
    rattrape ce qui a été livré sans mesure. Trouver ici un trou que la mutation aurait signalé cinq
    pull requests plus tôt est le signal que la règle du cycle a été sautée.

!!! warning "Deux dispositifs qu'on saute quand on est pressé"
    Ce sont **toujours les deux mêmes** qui manquent à l'appel, parce qu'ils demandent une commande de
    plus et qu'un écran vert donne déjà l'impression du travail fini. La passe 6 n'est pas close tant
    qu'ils n'ont pas tourné, ou que leur inapplicabilité n'est pas **écrite**.

    **PIT sur les classes du chantier**, et sa lecture, pas son score :

    ```bash
    env -u DISPLAY ./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage \
      -DtargetClasses="fr.univ_amu.iut.<feature>.model.*" \
      -DtargetTests="fr.univ_amu.iut.<feature>.*"
    ```

    Le pourcentage ne dit rien : ce sont les **survivants** qui parlent, et il faut les ouvrir un par
    un. Trois familles s'y mêlent, à ne pas confondre : un **vrai trou** (un test manque, on l'écrit),
    un **défensif inatteignable** (on l'assume, sans test creux), et un **artefact de ciblage** (la
    classe est couverte par un test qu'on a exclu de `targetTests` : on élargit et on remesure).
    Cibler les classes **pures** ; une façade de délégation ne produit que des survivants sans valeur.

    **Les E2E `bats` de la CLI** (`src/test/bats/`), dès qu'une commande est ajoutée ou changée. Ils
    lancent le **vrai fat-jar dans un processus** : ils voient le packaging, l'analyse des arguments
    par picocli et les **codes de sortie réels**, que les tests Java in-process ne voient pas.

    ```bash
    ./mvnw -DskipTests package && bats src/test/bats
    ```

    Deux niveaux, tous deux requis : `cli-surface.bats` (la commande **existe** et refuse une
    invocation vide) et `cli.bats` (elle **fait ce qu'elle promet** : fichier écrit, refus expliqué,
    code de sortie juste).

**Des E2E qui traversent, quitte à fusionner des scénarios.** Un E2E ne vaut pas par le nombre
d'assertions mais par ce qu'il **traverse**. Plusieurs parcours courts, qui bouchonnent chacun l'étape
voisine, prouvent chacun une tranche et **personne ne prouve la chaîne** : les défauts se logent
précisément dans les **coutures** entre deux étapes. Quand deux scénarios partagent leur amont, les
**fusionner** en un seul qui va plus loin donne une couverture plus **fidèle**. Le critère de fusion
est simple : deux étapes se fusionnent quand le défaut probable est **entre** elles. À l'inverse,
fusionner sans couture à exercer ne produit qu'un test-fleuve illisible - la longueur n'est pas le but.

#### Préparer la recette : toute capacité ajoutée sait comment on la vérifie

La [recette](recette/index.md) n'est pas le déversoir de ce qui a résisté à l'automatisation. C'est
l'endroit où vit **le procédé de vérification** d'une capacité : le geste à faire, ce qu'on doit voir,
et de quoi le refaire.

> **Une capacité ajoutée par le chantier n'est pas finie tant qu'on ne sait pas comment la vérifier à
> la main, et que ce « comment » n'est pas écrit là où on le retrouvera.**

Le critère qui dit qu'une case est terminée est la **rejouabilité** : quelqu'un qui n'a pas fait le
chantier doit pouvoir la refaire, dans six mois, autant de fois que nécessaire, et obtenir la même
chose. Il tient en trois pièces, et il en manque **une seule** pour que la case redevienne une
intention :

| Pièce | Ce qu'elle répond | Ce qui arrive si elle manque |
| --- | --- | --- |
| le **geste** | qu'est-ce que je fais ? | la case se rejoue différemment à chaque campagne |
| l'**observation attendue** | qu'est-ce que je dois voir ? | on coche « ça marche » sans référence |
| la **fixture** | sur quelles données ? | la donnée se bricole, donc le résultat ne se compare pas |

Quatre gestes, donc, dans cette passe.

**1. Désigner la session propriétaire.** Un écran est déroulé **en entier** dans **une seule** session ;
ailleurs il n'est qu'écran de transit. Une case déposée dans la mauvaise session sera jouée deux fois ou
jamais. La table des sessions vit dans l'[index de la recette](recette/index.md).

⚠️ **Toutes les sessions ne sont pas au même état** : certaines sont partielles, d'autres écrites mais
jamais jouées. Quand le chantier touche un de ces écrans, cela se **dit** - une issue, pas un silence :
sinon la capacité est réputée vérifiable par un script qui ne la couvre pas.

L'état de chaque session se lit dans l'[index de la recette](recette/index.md), **et nulle part
ailleurs**. La première version de cette page recopiait la liste ici : les deux copies ont divergé en
**quelques heures**, S7 ayant été écrite (#3517) le jour même où ce paragraphe affirmait qu'elle
n'existait pas. Un inventaire ne se duplique pas, il se cite.

**2. Écrire les cases à leur place**, sous forme de points numérotés `Sxx-NN`, groupés par étape du
parcours. **Une case = un fait observable**, jamais un contrôle groupé. Elles s'écrivent **pour un
lecteur qui n'était pas là** : « le bandeau annonce la nuit du 22/04 et le nombre de fichiers retenus »
se rejoue, « vérifier que l'import marche » non.

**3. Fournir de quoi les jouer.** Si aucune des fixtures existantes ne porte le cas - une nuit sans GPS,
un journal qui contredit les WAV, un volume qui déborde -, **étendre la spec du générateur** fait partie
de cette passe. Les cartes SD de recette sont **générées** depuis quelques kilo-octets de spec
précisément pour revenir à l'identique (voir [Fixtures](recette/fixtures.md)) ; une donnée fabriquée à
la main pour l'occasion ne se retrouvera pas à la campagne suivante, et la case deviendra injouable
sans que personne ne l'ait décidé.

**4. Relire le statut de la session.** L'index de la recette porte un inventaire - qui a été joué, ce
qui est partiel, ce qui reste à écrire - et un inventaire vieillit tout seul. C'est le même geste que
la relecture de la doc périmée en passe 3.

!!! warning "Ce que cette passe empêche, et qui se déguise bien"
    L'ancienne formulation - *ce qui ne peut pas être automatisé va en recette* - laissait « pas
    automatisable » devenir silencieusement « **pas vérifié** ». La règle actuelle ferme aussi la
    variante suivante, plus difficile à voir : une case **écrite mais injouable**, faute de fixture ou
    faute de session où la déposer. Elle a l'apparence d'une vérification prévue, et elle se coche.

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

**C'est ici que le [graphe du dépôt](#interroger-le-graphe-du-depot-pour-ce-que-grep-ne-relie-pas) est
le plus utile**, parce que les deux questions de l'audit sont des questions de **concept** et qu'aucune
ne se pose en termes d'identifiants. « Qu'est-ce qui ressemble à ce qu'on vient d'écrire ? » est
exactement ce que rendent les arêtes `conceptually_related_to` ; « qu'est-ce qui en bénéficierait ? »
se lit dans les appelants de l'ancienne façon de faire. La règle a été apprise ici même : c'est à cette
passe que trois `grep` bien choisis annonçaient deux écritures d'une même règle de dérivation là où le
graphe en montrait trois. La troisième n'avait aucun appelant, ce qu'aucun `grep` n'aurait dit.

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

### 10. Écriture des ADR du chantier

Toute **décision structurante** prise pendant le chantier - un choix d'architecture ou de domaine qu'un
développeur futur pourrait raisonnablement remettre en cause faute d'en connaître les raisons - donne
une **[ADR](decisions/index.md)** : une par décision, immuable, expliquant le pourquoi. Le numéro de
l'ADR **est** celui de l'issue.

Chaque ADR **déclare comment elle est vérifiée** ([ADR 2465](decisions/2465-une-adr-declare-comment-elle-est-verifiee.md)) : une puce `- **Vérification** : certaine | probable | humaine, <référence>` dans son en-tête, au même titre que `Statut` et `Chantier`. Un garde-fou fait rougir la CI si elle manque, ou si le test/script nommé n'existe pas. `certaine` nomme un test ou script déterministe ; `probable` nomme un script de suspects et son **cliquet** ; `humaine` donne le motif, et peut adjoindre une **loupe**. Voir la section « Comment une ADR est vérifiée » de l'[index des décisions](decisions/index.md).

**Pourquoi ici et pas en passe 3.** Parce que les passes 4 à 9 **produisent** des décisions, et qu'on ne
peut pas écrire en début de clôture ce qu'on n'a pas encore décidé. Le constat est mesuré, pas
supposé : les cinq ADR du chantier #3151 - 3406, 3439, 3450, 3451, 3483 - portent **toutes** la mention
« suite de », c'est-à-dire qu'aucune n'est née à l'endroit où le cycle les demandait. L'ADR 3439 est
sortie de la **revue visuelle** (passe 8), l'ADR 3483 d'une trouvaille faite en retirant ce que 3439
masquait. Un cycle qui exige une chose impossible obtient qu'on l'ignore.

Cette passe **balaie donc les passes 0 à 9** et pose, pour chacune, la même question : *une décision
a-t-elle été prise ici, qu'un lecteur futur pourrait défaire faute d'en connaître la raison ?* Trois
sources reviennent :

- la **passe 0**, quand le chantier a délibérément **dépassé** une ADR existante : le dépassement
  s'écrit, sinon deux règles opposées cohabitent dans le dépôt ;
- la **passe 7**, où un refactoring de conceptualisation tranche presque toujours quelque chose ;
- la **passe 8**, où l'on découvre ce qu'aucun test ne dit.

⚠️ Une décision **de ne pas faire** est une décision. « On garde la dépendance aux tuiles
OpenStreetMap », « on n'ajoute aucune protection de branche », « la Polynésie reste hors de la table de
dérivation » : ce sont des ADR, et ce sont celles qu'on oublie, parce qu'elles ne laissent pas de code
derrière elles.

### 11. Phase de bilan

Une **synthèse** courte : ce qui a été livré, la **dette restante**, les **décisions** prises et leur
pourquoi. Elle se dépose dans le corps de l'EPIC (au moment de le clore) et, si elle change une
règle du dépôt, se répercute dans `CLAUDE.md` / `CONTRIBUTING.md`. Le bilan **renvoie** aux
[ADR](decisions/index.md) écrites en passe 10 plutôt que de redérouler le raisonnement des décisions -
c'est la raison pour laquelle il vient **après** elles et non l'inverse.

**Et il se montre.** Un chantier d'IHM se juge sur ce qu'il change à l'écran, or le bilan est un texte :
il décrit des captures que son lecteur n'a pas sous les yeux. La passe 8 les a pourtant toutes ouvertes,
recadrées et regardées : ce travail reste dans la tête de qui l'a fait.

La passe 11 produit donc un **artefact visuel** : une page qui met les états **avant / après** côte à
côte, une ligne par conséquence visible du chantier, avec la phrase qui dit ce qu'on doit y voir. Elle
sert deux fois :

- **pour valider** : c'est le seul support sur lequel un relecteur peut dire « non, ça ne va pas » sans
  relire le code. Un bilan qui affirme « les huit boutons s'affichent en entier » demande qu'on le
  croie ; une capture le montre ;
- **pour dater** : elle fige à quoi ressemblait l'écran à la clôture, ce que le prochain chantier pourra
  comparer.

Elle est **soumise avant de clore l'EPIC**, pas après : son objet est d'obtenir un assentiment, pas de
documenter une décision déjà prise.

!!! tip "Ce qu'elle contient, au minimum"
    Une entrée par écran touché : la capture **avant**, la capture **après**, et une phrase qui nomme ce
    qui a changé. Les défauts trouvés en chemin y figurent aussi, **recadrés et agrandis** : un glyphe de
    douze pixels ne se juge pas à l'échelle 1 (cf. passe 8). Ce qui n'a **pas** été corrigé y a sa place :
    une troncature laissée en l'état, montrée et assumée, vaut mieux qu'une omission.

## Les suites d'une clôture se closent aussi

La passe 9 crée des issues ; la passe 11 les nomme « dette restante » et clôt l'EPIC. Ces issues, une
fois livrées, forment un **nouveau delta** - et rien ne les rattrape si l'on considère que le chantier
est fini.

Le dépôt l'a vécu **trois fois** : les suites de l'EPIC #1662 ont formé l'EPIC #1863, dont les suites
ont formé le delta clos par #1920 ; les suites de #1838 ont eu leur propre clôture (#1921). Le patron
est donc régulier, pas accidentel.

**Les suites d'un chantier se closent par les mêmes 12 passes**, appliquées à leur seul delta
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
- [ ] 0. Relecture des ADR existantes (contre `origin/main`) : une décision a-t-elle été **contredite**, et si oui délibérément ?
- [ ] 1. Audit d'intégration (rebase sur `main`, points d'accroche, régressions)
- [ ] 2. Cohérence CLI ↔ UI (capacités métier exposées des deux côtés, ou « sans objet »)
- [ ] 3. Doc développeur (dev-docs) : ce qui manque **et ce qui est devenu FAUX** (partir des fichiers touchés, chercher qui les cite)
- [ ] 4. Doc utilisateur (docs/) + captures
- [ ] 5. Brief projet (`brief/`, dans la PR du chantier) répercuté si un élément de conception change
- [ ] 6. Tests : inventaire des usages **depuis le diff** (chemins non nominaux, parité CLI ↔ IHM), E2E qui **traversent les coutures**, **PIT ciblé** (survivants lus un par un) et **E2E `bats`** si la CLI bouge
- [ ] 6b. Recette **préparée** : chaque capacité ajoutée a sa case `Sxx-NN` dans sa **session propriétaire** (geste + observation attendue), la **fixture** existe ou la spec du générateur est étendue, session manquante ou jamais jouée **signalée en issue**
- [ ] 7. Harmonisation : **audit global** (ce qui ressemble / bénéficierait, exhaustif) puis **refactoring de conceptualisation** (lisibilité ; duplication et abstraction = outils) ; **choix, doutes, conséquences discutés avec l'utilisateur**
- [ ] 8. Revue visuelle : **toute conséquence visible** couverte par une capture (captures **ajoutées** si besoin), régénérées et ouvertes une par une
- [ ] 9. Nouveaux chantiers identifiés + issues créées
- [ ] 10. ADR du chantier écrites (balayer les passes 0 à 9 ; une décision **de ne pas faire** en est une)
- [ ] 11. Bilan (livré / dette / décisions) **+ artefact visuel avant/après soumis avant de clore**
```

## Modèle de cycle d'une issue (rouge, vert, refactor)

Le bloc **↻** se répète à **chaque petit pas**, jusqu'à ce que le comportement soit complet : une issue
en compte des dizaines. Ce qui l'encadre ne se fait qu'une fois.

```markdown
Avant la boucle
- [ ] Le comportement est découpé en **petits pas** : le prochain fait observable, pas la fonctionnalité
- [ ] Jumeaux cherchés (graphe du dépôt, pas seulement `grep`) : le défaut a-t-il d'autres sites ?

↻ À chaque pas, jusqu'à ce que le comportement soit complet
- [ ] ROUGE : le test échoue, et **pour la raison attendue** (sur un défaut : il le **reproduit**)
- [ ] VERT : le **minimum** qui fait passer
- [ ] REFACTOR **tenté** : ce que ce pas vient de toucher, à comportement constant
- [ ] Le rouge a duré ? Le pas était trop gros : revenir au dernier vert et le couper en deux

Quand la boucle s'arrête
- [ ] **PIT ciblé** sur les classes pures livrées, survivants lus un par un
- [ ] Corps de l'issue **édité** si une découverte a changé sa lecture (pas seulement commenté)
- [ ] Lecture à froid : corps de la PR et de l'issue compréhensibles dans six mois, sans le fil
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

Et quand on s'arrête sans avoir fini, reporté, bloqué, abandonné :

```markdown
**Reposée** : <ce qui a été fait, ce qui bloque, ce qu'il reste>. Assignee retiré.
```
