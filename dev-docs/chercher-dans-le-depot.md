# Chercher dans le dépôt

Sept outils, et ils ne répondent pas aux mêmes questions. Se tromper d'outil coûte rarement une
erreur visible : cela coûte une réponse **fausse et plausible**, ce qui est pire.

| La question porte sur… | L'outil | Ce qu'il rend |
|---|---|---|
| un concept, ses voisins, ce qui lui ressemble | `graphify` | un sous-graphe déjà réduit |
| le **pourquoi** d'une règle, ce que la prose en dit | `scripts/graphify/couche_semantique.py cherche` | les énoncés de la couche, avec leur justification et leur page |
| **qui appelle CETTE méthode Java**, résolu | `scripts/qualite/appelants.py` | ses porteurs et leurs appelants, types résolus |
| **qui tient CE contrat Java**, résolu | `scripts/qualite/implemente.py` | ses porteurs, noms qualifiés |
| **qui lit CE champ Java**, hors de sa classe | `scripts/qualite/lecteurs.py` | ses déclarations, et qui les lit |
| une **forme de code** (appel, constructeur, structure) | `semgrep` | les occurrences, avec leur position |
| un **texte** (message, libellé, ligne de journal) | `grep` / `rg` | les lignes qui contiennent le motif |

Les trois du milieu lisent le même modèle Spoon, bâti une fois à la compilation, et refusent de
répondre si leur index manque. Ce qu'ils partagent surtout est ce qu'ils **ne** disent pas : aucun des
trois ne rend une liste de code mort, et les trois taux qui suivent disent pourquoi.

## Les appelants d'une méthode Java se RÉSOLVENT

Le dépôt déclare **1 424 noms de méthode dans plusieurs classes**, sur 10 360 noms distincts :
`preparer` dans 188, `start` dans 150, `nettoyer` dans 77. Ces deux comptes suivent la population et
valent au 2026-09-29 ; le rapport, lui, tient. Devant eux, `grep` et `semgrep` rendent une liste de fichiers à ouvrir, et le
graphe rend des arêtes en partie **inférées**. Aucun des trois ne dit laquelle des 188 est appelée.

```bash
python3 scripts/qualite/appelants.py preparer
```

Il lit l'index que Spoon produit à la compilation, que la CI rebâtit à chaque demande de fusion. Sur
`preparer`, la question passe de **243 fichiers à lire** à **neuf porteurs** appelés d'ailleurs, dont
le premier voit ses dix appelants nommés. Il **refuse** si l'index manque, plutôt que de rendre
« aucun appelant » pour toute méthode, ce qui se lirait exactement comme du code mort.

**Ce qu'il ne dit pas**, et le contresens à ne pas commettre : « aucun appelant hors de son fichier »
n'est pas du code mort. C'est l'état normal d'une aide privée, et cela vaut **69 %** du corpus, dont
5 014 cas que JUnit appelle par réflexion.

## Les porteurs d'un contrat, et les lecteurs d'un champ

Deux questions de la même famille, deux outils bâtis sur le même patron.

```bash
python3 scripts/qualite/implemente.py DaoGenerique   # 30 porteurs, qualifiés
python3 scripts/qualite/lecteurs.py service          # 168 déclarations, dont 24 lues d'ailleurs
```

Le gain n'est pas le compte, c'est la **résolution**. Un `grep` sur `implements Contrat` rate la classe
qui l'obtient par sa mère, et rate les **25 contrats imbriqués** du dépôt. Un `grep` sur un nom de
champ ne distingue pas le champ de la variable locale, du paramètre ni de la méthode homonymes :
sur `service`, il rend **3 398 lignes** à trier, et **995 des 3 730** noms de champ du dépôt sont
déclarés dans plusieurs classes.

**Ce qu'ils ne disent pas**, et c'est la même mise en garde qu'au-dessus, avec des taux plus raides :
**27 des 123 contrats** n'ont aucun porteur, et **8 196 des 8 934 champs** n'ont aucun lecteur hors de
leur classe, soit 92 %. Ni les uns ni les autres ne sont morts : un contrat posé pour un point
d'extension, un état privé lu par les méthodes de sa propre classe sont exactement cela.

`lecteurs.py` ne retient que les **lectures**, jamais les écritures. Les confondre ferait passer un
champ qu'un constructeur écrit et que personne ne lit pour « utilisé ailleurs », soit le faux négatif
que l'index existe pour éviter ; les 4 347 écritures du corpus ne sont donc pas indexées.

## Le graphe d'abord

C'est la règle du dépôt, et elle est dans `AGENTS.md` : quand `graphify-out/graph.json` existe,
`graphify query "<question>"` passe avant tout le reste. Il oriente ; les deux autres outils
précisent ensuite.

### Ce qu'il lit, et ce qu'il ne lit pas

Le graphe a deux couches, et elles ne couvrent pas la même chose.

| Couche | Ce qu'elle lit | Où |
|---|---|---|
| de structure | les classes, les méthodes, et pour une page son fichier et ses titres | tout le dépôt |
| sémantique | les concepts, les décisions et les règles que la **prose** porte, avec leur raison | `brief/`, `dev-docs/`, `docs/`, les `.md` de la racine hors `CHANGELOG.md` |

Hors de ce périmètre, une page n'est connue que par ses titres : c'est le cas de `.github/`,
d'`openspec/`, des compétences et de `recette/`. Une question conceptuelle n'y lira aucune prose, et
le graphe ne le dira pas. Le périmètre, son coût et ses parades sont dans l'[ADR 5790].

Ces pages sont pourtant reliées au code : toute page que la structure porte reçoit une arête vers
chaque classe, chaque workflow et chaque profil qu'elle cite. « Quelle page parle de cette classe ? »
y trouve donc sa réponse, même là où « que dit cette page ? » n'en a pas. Deux exclusions, nommées
dans l'[ADR 5904] : `CHANGELOG.md`, et `.claude/skills`, copie de `.agents/skills`.

Cela ne veut pas dire que personne ne lit ces pages. La prose de `.github/` est lue par l'instrument
textuel de la passe 3 de la clôture, que porte la compétence `recoller-la-doc-au-code` depuis #5791 :
le graphe n'en connaît que les titres, et cet instrument en est le lecteur.

!!! warning "Une question en langage courant se pose aux énoncés"

    `graphify query` part des libellés qui ressemblent aux mots de la question, et un symbole de
    code homonyme capte le départ. « Pourquoi le dépôt se fait en WAV par défaut plutôt qu'en
    ZIP » n'y rend que du code : `WAV` et `ZIP` sont aussi deux constantes Java, alors que la
    couche porte plus de trente énoncés sur le sujet.

    Pour une question sur un pourquoi ou sur une règle, la commande du dépôt cherche les énoncés
    par leurs mots, justification comprise :

    ```bash
    python3 scripts/graphify/couche_semantique.py cherche "pourquoi le dépôt se fait en WAV"
    ```

    Elle compare des mots, pas des sens : quand elle rend zéro, la prose n'en dit rien, ou le dit
    autrement. Mesuré le 5 octobre 2026 sur sept questions : six rendent la page attendue parmi
    leurs cinq premiers énoncés, la septième au huitième rang. La raison est dans l'[ADR 5939].

!!! warning "Une page récente répond avec sa version d'avant"

    Une page modifiée depuis son extraction garde ses nœuds d'avant, sans rien signaler : le
    graphe répond, et il répond l'ancienne prose. Sur une page qui vient de changer, lire la page.

    `python3 scripts/graphify/couche_semantique.py a-reextraire` rend ces pages, une par ligne
    avec sa raison. Il refuse plutôt que de rendre une liste vide quand il n'a rien à comparer :
    le graphe est ignoré par git et ne vit que dans la copie principale, donc depuis un worktree
    il se désigne par `--graphe`.

### Refaire la couche d'une page

La couche sémantique ne se refait pas par une commande : quelqu'un relit la page. Le script borde
ce travail en quatre gestes, et son en-tête en donne le détail.

```bash
python3 scripts/graphify/couche_semantique.py a-reextraire                    # quelles pages
python3 scripts/graphify/couche_semantique.py decoupe --dossier DIR PAGE      # ce qu'il faut lire
python3 scripts/graphify/couche_semantique.py audite --dossier DIR            # le rendu tient-il
"$(cat graphify-out/.graphify_python)" scripts/graphify/couche_semantique.py fusionne --dossier DIR
```

Entre `decoupe` et `audite`, le lecteur écrit `rendu_NN.json` dans `DIR`, en suivant
`scripts/graphify/consigne-des-agents.md`. La fiche que `decoupe` pose dit ce que la page portait :
ses nœuds, ses arêtes, ses hyperarêtes, et les deux empreintes entre lesquelles `git diff` montre
ce qui a changé.

`fusionne` écrit le graphe de l'arbre d'où il est lancé, et refuse un graphe désigné ailleurs : il
se lance donc depuis la copie principale, avec l'interprète de graphify. Les trois autres tournent
avec n'importe quel `python3`.

Ce que le rendu doit déclarer quand il lâche un nœud ou une hyperarête, et pourquoi la fusion le
refuse sinon, est dans l'[ADR 5813] et l'[ADR 5904].

### Ce qui le tient à jour

Personne n'écrit ce graphe à la main : quand `VIGIECHIRO_GRAPHIFY=1` est posé, le crochet
`post-commit` le refait par `scripts/graphify/rebuild.py`, qui conserve la couche sémantique.

Ce crochet relit le code et les titres des pages que le commit touche, et dit les titres qu'il
retire ([ADR 5941]). Sa prose, elle, attend un lecteur. Les pages que le commit ne touche pas ne
suivent qu'à la mise à jour, qui relit la structure de toutes les pages :

```bash
"$(cat graphify-out/.graphify_python)" scripts/graphify/rebuild.py --mets-a-jour
```

Elle garde la partition du graphe : chaque nœud reste dans sa communauté, un nœud neuf va chez
ses voisins, et les libellés ne bougent pas d'une lecture à l'autre. `--repartitionne` la refait
en entier quand elle a vieilli ([ADR 5940]).

Elle se lance depuis la copie principale, et avec l'interprète de graphify, que ce fichier nomme. Le
`python3` du poste peut trouver la commande `graphify` sans importer son module : la mise à jour
refuse alors en le disant, avant d'avoir touché au graphe.

`graphify update .` seul ne suffit pas : il laisse telle quelle la structure d'une page qui porte une
couche, et ses titres datent alors du jour où elle l'a reçue. La raison et la mesure sont dans
l'[ADR 5877].

## `semgrep` pour les questions de forme

`semgrep` lit l'arbre syntaxique, pas les lignes. Il répond juste là où `grep` ne peut que deviner :

```bash
semgrep --lang java --metrics=off --pattern 'CritereListe.$M(...)' src/main
semgrep --lang java --metrics=off --pattern 'new Scene(...)' src/main
```

Le second exemple n'est pas gratuit : la règle du dépôt est de passer par `Habillage.scene(...)`, et
la question « qui construit une `Scene` à la main ? » n'a pas de réponse textuelle fiable - le nom
`Scene` apparaît dans les imports, les commentaires et les types de paramètres.

!!! warning "Une réponse à zéro n'est pas une preuve d'absence"

    Le moteur libre de `semgrep` ne traite **pas** les annotations Java comme motif autonome :
    `--pattern '@CasDeRecette(...)'` rend **zéro occurrence** sur un dépôt qui en compte plusieurs
    dizaines, et **zéro erreur**. Rien ne distingue « je n'ai rien trouvé » de « je n'ai pas su
    chercher ».

    Avant de conclure d'un zéro, éprouver le motif sur un cas dont on **sait** qu'il existe. C'est
    la même discipline que pour un test : un dispositif qui ne peut pas échouer ne prouve rien.

Les motifs qui marchent en Java sur le moteur libre : appels de méthode, constructeurs, expressions.
Ceux qui demandent le moteur propriétaire : annotations seules, motifs inter-fichiers, flux de
données.

## `grep` pour le texte, avec ses pièges

`grep` reste le bon outil pour un message d'erreur ou un libellé. Trois pièges l'ont fait mentir dans
ce dépôt, et aucun n'a produit d'erreur visible :

- **Un sous-motif se compte lui-même.** `grep -c "couvert"` compte aussi « non couvert ». Ancrer, ou
  filtrer le contraire ;
- **Une puce markdown se replie.** Les fichiers de `dev-docs/recette/sessions/` continuent une puce
  sur la ligne suivante, indentée de deux espaces. `grep` n'en rend que le premier morceau, et la
  phrase tronquée peut dire le contraire de la phrase entière ;
- **Les octets NUL rendent `grep` muet.** Un fichier que `grep` juge binaire ne rend rien du tout, et
  une mesure vide se lit comme un zéro. `tr -d '\0'` en amont.

## Compter

Compter en **lançant**, pas en cherchant. Le dépôt a ses propres compteurs, et ils sont plus justes
que n'importe quelle expression : `CorrespondanceRecetteTest` imprime le compte des cas de recette et
nomme chacun, les auto-tests des bancs impriment leur nombre de cas et de rouges attendus, la CLI a
son inventaire.

Un chiffre transporté d'un contexte à l'autre garde sa forme et perd son objet : « 33 » a déjà été
relu comme « 33 clips » alors qu'il comptait les cas d'un auto-test.

[ADR 5790]: decisions/5790-le-graphe-lit-la-prose-d-un-perimetre-declare.md
[ADR 5813]: decisions/5813-une-hyperarete-porte-un-identifiant-et-une-mise-a-jour-declare-ce-qu-elle-lache.md
[ADR 5904]: decisions/5904-une-hyperarete-se-declare-et-les-ponts-parcourent-les-pages-du-graphe.md
[ADR 5939]: decisions/5939-une-question-de-prose-se-pose-aux-enonces-de-la-couche.md
[ADR 5940]: decisions/5940-la-partition-du-graphe-se-garde-et-ne-se-refait-que-sur-demande.md
[ADR 5941]: decisions/5941-le-crochet-relit-la-structure-des-pages-en-une-extraction-avec-le-code.md
[ADR 5877]: decisions/5877-la-mise-a-jour-du-graphe-relit-la-structure-de-toutes-les-pages.md
