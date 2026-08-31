# Cycle de vie d'un chantier

Un **chantier** est un lot de travail d'ampleur **EPIC** : une évolution qui ne tient pas dans une
seule PR et se découpe en plusieurs (ex. l'EPIC « Réglages auto-découverts → feature = plugin »). Là
où [Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md) décrit une **PR** et
[CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)
le **flux de contribution**, cette page décrit le niveau au-dessus : comment on **ouvre** et on
**clôt** un chantier entier.

Le principe : un chantier ne se termine pas au dernier `feat:` mergé. Une fois le cœur livré, une
**clôture en 14 passes** (numérotées **0 à 12**, plus la **6b** qui prolonge la 6) garantit que l'évolution est intégrée, cohérente
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
3. **Découper en issues** reliées à un **EPIC**, une issue « parapluie » dont les lots sont ses
   **sous-issues** depuis #4829. Chaque sous-issue porte son palier, ses dépendances, et **son
   critère de fin**, dans son corps.

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

**Et une quatrième, qui ne va pas au même endroit : le critère de fin.** Les trois phrases ci-dessus
se déposent en commentaire ; le critère, lui, s'écrit dans le **corps** de l'issue, qui est un lot de
son chantier. La compétence `ouvrir-une-issue` porte le geste et dit pourquoi le corps et non le fil.

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

### Une trouvaille se consigne en ISSUE, au moment où on la fait

Le paragraphe ci-dessus dit de **lire** la trouvaille. Celui-ci dit ce qu'on en fait ensuite, et le
geste est immédiat : **on ouvre l'issue tout de suite**, avant de revenir au travail en cours.

Pas à la clôture, et pas en commentaire. Un commentaire n'a pas d'état, ne se filtre pas, ne remonte
dans aucune liste, et sort de la vue dès que sa pull request est fusionnée. Une trouvaille qui n'existe
que là est perdue à la fusion.

**Ce qui se perd n'est pas la trouvaille, c'est son détail.** On se souvient trois jours plus tard
qu'« il y avait un souci avec le tri des échecs ». On ne se souvient pas que le job avait été coupé à
92 minutes sur un plafond de 90, que le tableau annonçait 618 classes sur 758, ni que le script rendait
`0`. Ces trois nombres sont ce qui rend l'issue traitable, et ce sont exactement eux qui ne se
retrouvent pas. Une issue de trois lignes écrite sur le coup vaut mieux qu'un paragraphe exact écrit de
mémoire.

Elle se rattache alors à l'un des deux endroits, et le choix ne se discute pas longtemps :

- à l'**EPIC du chantier en cours**, si elle tombe dans son périmètre ;
- sinon à l'**EPIC des suites** ([#4562](https://github.com/echonuit/vigiechiro-pr-companion/issues/4562)),
  qui est un **sas** : rien ne s'y traite directement, et la passe 9 le vide.

En cas de doute, l'EPIC des suites. Un rattachement trop large se corrige en passe 9, qui est faite
pour cela ; une trouvaille jamais écrite ne se corrige pas.

**Le rattachement est une sous-issue**, pas une ligne ajoutée au corps de l'EPIC :

```bash
gh issue create --parent <EPIC> --title "..."   # à l'ouverture de la trouvaille
gh issue edit <n> --parent <EPIC>               # pour la déplacer
```

Une liste tenue à la main dans un corps d'EPIC se périme sans bruit, et elle ne se compte pas. Le
lien natif se lit par `gh issue view <EPIC> --json parent,subIssues,subIssuesSummary` : chaque issue
porte son chantier, et la taille d'un EPIC est un nombre plutôt qu'un décompte à l'œil.

**La seule dispense, et elle est étroite** : une trouvaille **corrigée dans la pull request en cours**
n'a pas besoin d'issue. Le corps de la PR la porte, et rien ne peut se perdre entre la trouvaille et
son remède puisqu'ils voyagent ensemble. C'est le cas de l'auto-test absent découvert en traitant
#4544, corrigé dans la même PR et jamais consigné ailleurs.

Elle cesse de s'appliquer dès que le remède **sort** de cette PR, pour quelque raison que ce soit :
périmètre, taille, ou simplement le fait qu'on le remette à plus tard. À cet instant précis, l'issue
s'ouvre. « Je le corrige tout de suite » est la formule qui fait disparaître les trouvailles quand
elle se révèle fausse une heure après.

!!! warning "Un compte rendu n'est pas une consignation"
    Signaler la trouvaille dans le corps d'une pull request, dans un commentaire ou dans un rapport de
    fin de tâche **ne compte pas**. Le signalement y est réel et il est pourtant perdu, parce qu'un
    rapport long enterre ses propres trouvailles : qui le lit en diagonale retient la conclusion, pas
    les trois lignes du milieu.

    Vécu sur #4522, qui en a produit trois. Une seule est devenue une issue sur le coup, #4544, et
    c'est la seule qui n'a jamais risqué de disparaître. Les deux autres, #4553 et #4554, n'ont été
    ouvertes qu'après réclamation explicite, alors qu'elles avaient été **décrites** dès leur
    découverte. Le signalement ne manquait pas ; il n'avait simplement aucun support qui survive.

**Ce n'est pas une charge nouvelle.** C'est le même travail que la passe 9 demandait déjà, fait au
moment où le contexte est frais plutôt que reconstitué. Ce qui change est le coût : quelques minutes
pendant qu'on a le message d'erreur sous les yeux, contre une demi-heure d'archéologie trois jours plus
tard, pour un résultat moins juste.

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

### Un garde ne dit ce qu'il vérifie qu'après l'avoir vu rougir sur SA mutation

PIT répond à « une ligne est-elle couverte ». Il ne répond pas à « **ce garde attrape-t-il le défaut
qu'il nomme** ». C'est une autre question, et elle se pose pour tout dispositif qu'on écrit pour
empêcher un défaut précis de revenir : garde d'architecture, cliquet, test de parcours, inventaire.

**La règle : avant de faire confiance à un garde, on casse à la main exactement ce qu'il prétend
attraper, et on le regarde rougir.** Pas une mutation voisine - celle-là.

Trois dispositifs de la seule journée du 16/08/2026 ont échoué à ce contrôle, et aucun n'aurait été
démasqué par une relecture :

- le cliquet d'annonces de [#3645](decisions/3645-un-detecteur-textuel-s-exclut-de-son-corpus.md)
  parcourait tous les fichiers de test **y compris le sien**, dont la documentation nommait ses cinq
  débiteurs : il les certifiait gardés. Mesuré, il n'en nommait **aucun** ;
- le test de fraîcheur de [#3840](decisions/3840-le-signal-et-le-retour-se-partagent-la-fraicheur.md)
  restait vert en retirant le mécanisme qu'il prétendait vérifier : une écriture voisine annonçait, et
  son rechargement asynchrone relisait la base **après** le geste silencieux ;
- le garde d'élision de [#3798](decisions/3798-un-fil-elide-des-segments-il-ne-rogne-pas-des-libelles.md)
  a dû être **re-vérifié rouge après réécriture** du composant : la façon de lire avait changé en même
  temps que la chose lue.

Trois formes d'un même défaut : un vert qui existerait à l'identique sur un dépôt cassé
([ADR 3624](decisions/3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md)), un fait tenu
par **un autre dispositif que celui qu'on croit**, et un garde dont on a changé les deux côtés à la
fois.

Ce qui rend la mutation valable :

- **elle doit laisser le test s'exécuter.** Renommer une méthode casse la compilation : le test ne
  tourne plus, et un test qui ne tourne pas ne prouve rien. Simuler un cas de plus, neutraliser un
  corps de méthode, retirer une classe CSS : oui ;
- **elle porte sur le sujet, pas sur le détecteur.** Casser le détecteur vérifie sa **non-vacuité**,
  ce qui est un second contrôle utile - mais distinct, et à faire aussi ;
- **le message d'échec se lit.** Il doit nommer le coupable du jour, pas renvoyer un `expected: true`.
  C'est lui qu'on lira dans six mois, pas le test ;
- **après toute réécriture du garde ou du sujet, on refait la mutation.** Un dispositif vert n'est pas
  un dispositif vérifié.

Quand la mutation est impossible à monter - le défaut n'est pas atteignable en test - c'est une
information, pas un échec : elle dit que le garde promet plus qu'il ne tient, et il faut l'écrire dans
son en-tête plutôt que d'emprunter la solidité du voisin
([ADR 3540](decisions/3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md)).

## À la clôture d'une issue : ce qu'on laisse derrière soi

Une issue se ferme sur deux textes qu'on relira dans six mois **sans le fil** : son **corps**, et celui
de sa **pull request**.

### Le corps porte la vérité, les commentaires portent le journal

Une issue vit : la prémisse se révèle fausse, une mesure contredit l'intuition, le remède change. Rien
de cela ne doit rester **uniquement** en commentaire.

- **le corps** dit l'**état courant de la vérité** : ce qu'on fait, pourquoi, ce qui a été décidé ;
- **les commentaires** portent le **journal** : mesures, pistes essayées, et la **trace** des
  trouvailles incidentes.

Une trouvaille incidente s'écrit donc à deux endroits, et le commentaire n'est pas le second : elle a
**déjà** son issue, ouverte au moment où on l'a faite (voir « Une trouvaille se consigne en ISSUE »
ci-dessus). Le commentaire en garde la trace dans le fil ; c'est l'issue qui la porte.

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

## À la clôture : les 14 passes

Elles s'exécutent **dans l'ordre** : la relecture des ADR remet l'existant en tête avant qu'on touche
à quoi que ce soit, l'audit d'intégration peut révéler du travail à faire avant de documenter, la
cohérence CLI peut révéler une commande à ajouter (qui sera alors documentée et testée par les passes
suivantes), l'harmonisation peut **casser un écran sans casser un test** (d'où la revue visuelle
**juste après** elle), la revue visuelle peut faire émerger de nouveaux chantiers, les ADR s'écrivent
quand toutes les décisions sont prises, et le bilan vient en dernier parce qu'il y renvoie.

### 0. Relecture des ADR existantes

La procédure vit dans la compétence [`ecrire-une-adr`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/ecrire-une-adr/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 1. Audit d'intégration

La procédure vit dans la compétence [`auditer-l-integration`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/auditer-l-integration/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 2. Passe de cohérence CLI ↔ UI

La procédure vit dans la compétence [`confronter-les-deux-surfaces`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/confronter-les-deux-surfaces/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 3. Passe de doc développeur

La procédure vit dans la compétence [`recoller-la-doc-au-code`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/recoller-la-doc-au-code/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 4. Passe de doc utilisateur

La procédure vit dans la compétence [`documenter-pour-l-utilisateur`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/documenter-pour-l-utilisateur/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 5. Passe de brief projet

La procédure vit dans la compétence [`aligner-le-brief-au-produit`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/aligner-le-brief-au-produit/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 6. Passe de tests

La procédure vit dans la compétence [`couvrir-les-usages-livres`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/couvrir-les-usages-livres/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 6b. Passe de préparation de recette

La procédure vit dans la compétence [`preparer-la-recette`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/preparer-la-recette/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 7. Passe d'harmonisation

La procédure vit dans la compétence [`harmoniser-l-application`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/harmoniser-l-application/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 8. Passe de revue visuelle

La procédure vit dans la compétence [`revoir-les-ecrans`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/revoir-les-ecrans/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 9. Passe de consolidation des suites

La procédure vit dans la compétence [`vider-le-sas`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/vider-le-sas/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 10. Archivage des changements OpenSpec

La procédure vit dans la compétence [`openspec-archive-change`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/openspec-archive-change/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 11. Écriture des ADR du chantier

La procédure vit dans la compétence [`ecrire-une-adr`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/ecrire-une-adr/SKILL.md), qui est ce qu'un agent lit au moment du geste.

### 12. Phase de bilan

La procédure vit dans la compétence [`rendre-le-bilan`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.agents/skills/rendre-le-bilan/SKILL.md), qui est ce qu'un agent lit au moment du geste.

## Les suites d'une clôture se closent aussi

La passe 9 consolide les issues ouvertes en chemin ; la passe 12 les nomme « dette restante » et clôt
l'EPIC. Ces issues, une fois livrées, forment un **nouveau delta** - et rien ne les rattrape si l'on
considère que le chantier est fini.

Le dépôt l'a vécu **trois fois** : les suites de l'EPIC #1662 ont formé l'EPIC #1863, dont les suites
ont formé le delta clos par #1920 ; les suites de #1838 ont eu leur propre clôture (#1921). Le patron
est donc régulier, pas accidentel.

**Les suites d'un chantier se closent par les mêmes 14 passes**, appliquées à leur seul delta
(`git log <sha-de-la-clôture-précédente>..origin/main`, **entier et non filtré** : le code des suites
doit se juger à côté de ce qui a été fusionné pendant qu'elles couraient). C'est peu coûteux - le
périmètre est étroit - et c'est là qu'on trouve ce que le travail de suite a laissé derrière lui : une
capacité livrée d'un seul côté, un état visuel sans capture, une règle construite par quatre PR
qu'aucune ADR ne porte.

Cette page a longtemps écrit ici « filtré sur les commits du chantier », et c'était faux : la clôture
de #4671 a trouvé son défaut le plus coûteux dans un plancher **posé par une autre session** pendant
qu'elle courait. Un delta filtré ne l'aurait pas montré.

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
- [ ] 2b. Spécification tranchée : **ce chantier change-t-il ce que le produit FAIT ?** Si oui, `/instruire` puis `/proposer`, et les tâches du changement **sont** les lots. Si non, la raison s'écrit en une phrase
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
- [ ] 10. Changement OpenSpec archivé : delta specs **fusionnées d'abord** dans `openspec/specs/`, toutes les capacités re-comparées, **puis** le dossier déplacé sous `archive/`
- [ ] 11. ADR du chantier écrites (balayer les passes 0 à 10 ; une décision **de ne pas faire** en est une)
- [ ] 12. Bilan (livré / dette / décisions) **+ artefact visuel avant/après soumis avant de clore**
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
