---
type: adr
title: "Une convention typographique se tient par un cliquet, pas par un nettoyage"
status: stable
article: A9
chantier: "#2843, suite de la clôture du chantier #2348 et de #2813"
decided_at: 2026-07-29
verification: probable
enforced_by:
  - "scripts/adr/2843-tiret-cadratin.py"
ratchet: 1
inv_key: cliquet-cadratin
verified:
  - by: machine:suspects
    at: 2026-07-29
---

# Une convention typographique se tient par un cliquet, pas par un nettoyage

## Contexte

La règle « pas de tiret cadratin » est écrite **deux fois** dans le dépôt :

- `CONTRIBUTING.md` : « pas de tiret cadratin dans la doc et les commentaires : tiret simple ou deux-points » ;
- `dev-docs/ajouter-une-fonctionnalite.md`, mot pour mot.

Et elle n'était appliquée par rien. Elle a été enfreinte pendant la clôture du chantier #2348, dans un message de commit et un corps de PR. L'écart n'a été vu que parce que quelqu'un a relu, et il aurait tout aussi bien pu ne pas l'être.

Une convention que seule la relecture applique n'est pas une convention, c'est un souhait.

La mesure, à l'ouverture : **1132 occurrences** dans les sources Java, réparties sur 481 fichiers ; 545 dans la documentation développeur, 80 dans la documentation utilisateur, 9 dans le brief.

## Décision

**1. Un cliquet, pas un nettoyage.** Un correctif d'un seul tenant toucherait la moitié du dépôt pour un gain nul le jour où il fusionne, et rendrait illisible tout `git blame` sur ces fichiers. Le dépôt cesse de dériver ; chaque fichier ouvert pour une autre raison peut être assaini au passage.

**2. Le périmètre est celui des sources Java**, `src/main/java` et `src/test/java`.

C'est un choix contre l'alternative apparemment plus généreuse d'un compteur unique couvrant aussi la documentation. Deux populations dans un seul nombre peuvent se **masquer** : un nettoyage de vingt lignes de documentation compenserait vingt régressions dans le code, le total resterait stable, et le verdict resterait vert. Le garde mentirait alors dans le sens rassurant, ce qui est pire que pas de garde du tout.

Une seule population, un seul nombre, aucun angle mort.

**4. Une zone nettoyée passe du cliquet à la tolérance zéro.** Cette ADR annonçait d'abord que la documentation aurait « son propre cliquet ». C'était mal formulé : un cliquet sur une zone déjà au plancher est inutilement faible, et il resterait masquable tant qu'une autre zone comptée avec elle serait, elle, loin du plancher. Une zone nettoyée n'a plus besoin d'une marge, elle a besoin d'un refus.

`docs/`, `brief/`, `dev-docs/` et **`src/main/java`** sont nettoyées depuis #2365 : la vérification y compte **zéro** cadratin de prose, et le script échoue à la première rechute. Le cliquet ne porte donc plus que sur `src/test/java`.

`src/test/java` est nettoyée à son tour, et son plancher vaut **1**, pas zéro. Un plancher nommé n'est pas un reste de travail, c'est pourquoi il est écrit ici :

- `SiteDetailViewModelTest` affirme `« — à vérifier »`, la valeur **composée** que l'écran affiche.

La classe de caractères par laquelle `DocumentationAJourTest` accepte les **deux formes** d'en-tête d'ADR faisait d'abord partie de ce plancher. Elle est devenue une **forme citée** à part entière quand les familles hors Java sont passées en tolérance zéro : les trois analyseurs de cette forme (`_commun.py`, `resserre_cliquets.py`, `DocumentationAJourTest`) citent une syntaxe héritée, ce qui est une citation au même titre qu'un libellé recopié.

Celle qui reste n'entrera pas dans une forme citée, et pour une raison de dosage : un motif épargnant tout littéral **commençant** par le glyphe serait trop permissif, et chaque élargissement du motif de citation est un risque de déflation silencieuse du compteur. Recomposer l'attente du test depuis `Formats.VALEUR_ABSENTE` changerait, de son côté, ce que ce test **épingle**, au détour d'une passe de typographie. Un cliquet de 1 refuse la deuxième occurrence exactement comme une tolérance zéro refuse la première.

La promotion se fait **dans la tranche qui amène la zone à zéro**, pas plus tard. Différer, c'est ouvrir une fenêtre où l'arbre est propre et où rien ne le garde : la première rechute y passerait sans bruit, et le cliquet, ayant encore sa marge, resterait vert.

Une zone peut porter une **extension** : un arbre de sources se garde exactement comme un arbre de documentation. Ce qui rend cette généralisation risquée, c'est qu'un motif mal apparié à son arbre (`*.md` sur `src/main/java`) rapporterait « 0 cadratin de prose » **à jamais**, avec la forme exacte du succès. Une zone qui ne balaie **aucun fichier** lève donc, et le garde-fou des scripts vérifie ce refus : rien d'autre ne distingue une zone propre d'une zone jamais regardée.

La **documentation de la racine** (`README`, `CONTRIBUTING`, `SECURITY`…) se garde en balayage **non récursif** : les sous-arbres ont déjà leurs zones, et descendre depuis `.` ramasserait les fichiers non suivis, faisant rougir le garde chez le développeur sans rien signaler en CI. Cette zone manquait à la première clôture du chantier, et `CONTRIBUTING.md`, le fichier qui **énonce** la convention, portait encore deux cadratins de prose. Une règle sans garde n'est pas appliquée, pas même par qui l'écrit.

`CHANGELOG.md` en est **exclu**, et c'est la seule exclusion du dépôt qui porte sur un fichier entier. Il est produit par semantic-release depuis les sujets de commits déjà fusionnés : le corriger falsifierait le compte rendu de ce qui a réellement été livré, et la ligne réécrite reviendrait à la génération suivante. Ce qu'il faut tenir, ce sont les **titres de PR** à la source, pas leur report.

Les zones nettoyées vivent dans une **liste déclarative** du script (`ZONES_NETTOYEES`). Ajouter une tranche revient à ajouter **une ligne**, et c'est délibéré : chaque tranche du chantier touche ce même script, donc une insertion d'une ligne se résout sans réfléchir là où un bloc de code aurait sérialisé les tranches.

**5. Ce qui est cité n'est pas de la prose.** Un cadratin entre **guillemets français**, entre **chevrons de code**, seul dans une **cellule de tableau**, réduit à un **littéral Java** (`"—"`), ou posé dans la **classe de caractères littérale** par laquelle les trois analyseurs d'en-têtes d'ADR acceptent encore l'ancienne forme, est une citation : le glyphe de valeur absente que la documentation décrit, un libellé de l'application qu'une fiche d'écran reproduit fidèlement, le glyphe lui-même tel que `Formats` le définit, ou une syntaxe héritée qu'un analyseur doit continuer de lire. Une seule règle couvre les cinq, là où cinq listes d'exceptions auraient dérivé séparément.

Cette cinquième forme est écrite **en littéral**, et non « des crochets contenant un cadratin » : un motif si large avalerait les libellés de liens Markdown, où un tiret entre crochets est de la prose ordinaire. Le garde-fou des scripts porte les deux cas dans la même fixture, la classe épargnée et le lien compté, pour qu'un élargissement du motif fasse rougir au lieu de déflater.

La règle vaut pour **les deux mesures**, Java comprise. Elle n'a d'abord servi qu'aux zones Markdown, et le compteur Java restait brut : il butait donc sur un plancher de **50 occurrences légitimes** du glyphe, que rien ne distinguait d'un reste de travail. Un cliquet dont on ignore le plancher est un cliquet sur lequel on ne peut pas clore le chantier, puisque nul ne sait quel nombre signifie « fini ». La mesure dit maintenant la même chose des deux côtés : **notre prose**.

C'est aussi ce qui a révélé que **`docs/` n'est pas indépendante des chaînes Java** : `docs/ecrans/sites.md` cite le libellé `« GPS manquant : placer sur la carte »`, écrit tel quel dans `CartesPointsSite`. Corriger la documentation seule l'aurait fait diverger du produit. Le libellé et sa documentation ont donc changé ensemble, dans la tranche des chaînes Java.

Une citation vieillit, et le chantier l'a prouvé quatre fois. Cette phrase citait encore `« GPS manquant, placer sur la carte »` avec une virgule, quand la tranche des chaînes avait tranché pour un deux-points ; trois Javadoc décrivaient de même un format d'affichage que cette tranche avait changé sous elles. Aucun contrôle ne peut l'attraper : une citation périmée est du texte juste au sujet d'un produit qui a bougé. La seule parade connue est de **relire les citations quand on touche à ce qu'elles citent**, et le motif se cherche mécaniquement (un libellé **composé** entre guillemets, contenant un séparateur).

**3. La vérification reste « probable ».** Un tiret cadratin peut être cité légitimement : un commentaire qui explique la règle, une chaîne qui reproduit un texte externe. Aucun motif ne sait faire cette différence, c'est un humain qui tranche, extrait en main.

## Conséquences

Le script compte les **commentaires**, contrairement à la plupart de ses voisins qui les retirent avant de mesurer. Ce n'est pas un oubli : la règle vise explicitement « la doc et les commentaires », qui sont ici la matière et non le bruit.

Cette ADR contient elle-même des tirets cadratins, à commencer par son titre et sa ligne de vérification, dont le format est imposé par `_commun.py`. Elle n'est pas dans le périmètre, et il n'y a là aucune contradiction : la règle porte sur ce qu'on écrit dans le code, pas sur la mécanique qui la fait respecter.

Le cliquet ne se resserre pas tout seul. Quand la mesure passe sous la marge, le script le signale (`verdict=a-resserrer`) : un cliquet qu'on ne resserre jamais redevient un tapis sous lequel on pousse.

La marge se mesure sur `main` **à jour**. La première version de cette ADR déclarait 1067, mesuré quelques heures plus tôt ; une fusion intervenue entre-temps a ajouté une ligne, et la CI a fait rougir la PR qui introduisait le cliquet. L'incident est le bon signe, mais il vaut d'être connu : un cliquet posé sur une base périmée échoue à son premier passage.
