# Méthode de travail

Ce document s'adresse à toute personne, et à tout agent, qui contribue à ce dépôt. Il vaut pour
Claude Code, Codex, Copilot, Cursor, Gemini CLI et les autres : le fonds est ici, les fichiers
propres à chaque outil n'en sont que des adaptateurs, et ils renvoient tous ici.

Les adaptateurs en place : [CLAUDE.md](CLAUDE.md) pour Claude Code,
[.github/copilot-instructions.md](.github/copilot-instructions.md) pour GitHub Copilot. En servir un
autre, c'est ajouter un fichier qui renvoie à celui-ci, pas recopier la méthode.

Application **JavaFX 26 / MVVM / package-by-feature**, produit open source pour les naturalistes du
protocole Vigie-Chiro. Née de la SAÉ 2.01, mais ce dépôt n'est plus « le dépôt de la SAÉ » : les
décisions se cadrent pour l'**utilisateur final**, pas pour un contexte pédagogique.

## Références (ne pas dupliquer, renvoyer)

- **Contribution** (environnement, architecture, commits, CI) : [CONTRIBUTING.md](CONTRIBUTING.md)
- **Doc développeur approfondie** : <https://companion-dev.echonuit.fr/>
- **Ajouter une fonctionnalité** : [dev-docs/ajouter-une-fonctionnalite.md](dev-docs/ajouter-une-fonctionnalite.md)
- **Tests et qualité** : [TESTING.md](TESTING.md)

## Garde-fous non négociables

- **JDK 25 standard** (pas un JDK packagé FX) ; tests headless FX 26 (`-Dglass.platform=Headless`, lancer avec `env -u DISPLAY`).
- Toujours **brancher + PR** ; **jamais** de push direct sur `main`.
- ⚠️ **Tout travail de branche se fait dans un git worktree dédié.** Ce dépôt-ci
  (`vigiechiro-pr-companion`) ne sert qu'à **récupérer la dernière version de `main` et la tester** :
  on n'y crée pas de branche, on n'y édite pas, on n'y committe pas.

  ```bash
  git fetch -q origin
  git worktree add ../vigiechiro-wt/<branche> -b <branche> origin/main
  cd ../vigiechiro-wt/<branche>      # tout le travail se fait ICI
  ```

  Les worktrees vivent **à côté** du dépôt (`SAE201/vigiechiro-wt/<branche>`), jamais dans `/tmp` :
  sinon ils n'apparaissent pas dans l'arbre VSCode. `git worktree remove` après fusion.

  **Pourquoi c'est non négociable** : plusieurs sessions travaillent en parallèle sur ce dépôt, et un
  arbre de travail partagé produit des dégâts **silencieux**. Tous vécus le 2026-08-12 sur #3616 : une
  session a committé le travail en cours d'une autre **sous son propre message** ; un `git checkout`
  concurrent a déplacé HEAD entre deux commandes, si bien qu'un commit destiné à une branche a atterri
  **sur `main`** ; une version **neutralisée** - injectée exprès pour prouver qu'un test rougit - a été
  committée telle quelle ; et `target/` en contention a rendu des erreurs de compilation sans rapport
  (« could not create parent directories », « cannot access » sur des classes qui existent). Un
  worktree a son propre `target/`, ce qui supprime aussi cette contention.

  **Signes qu'on a dérivé** : `git status` propre alors qu'on vient d'éditer ; un commit dont le
  message ne correspond pas au contenu ; `git branch --show-current` qui rend `main` sans qu'on l'ait
  demandé. Dans ce cas, **mesurer avant de réparer** (`git log --oneline origin/main..main`) plutôt
  que de supposer où le travail est passé.

- Avant tout commit : `git config user.email` = `sebastien.nedjar@univ-amu.fr`.
- Avant de committer : **tests ciblés** + `./mvnw spotless:apply` + `./mvnw -Pquality-gate pmd:check` (PMD strict, dont GodClass). Le **gate complet** `-Pquality-gate verify` seulement si nécessaire (la CI reste autoritaire).
- **Conventional Commits en français**, petits commits par préoccupation ; petites PR séquentielles.
- **`Closes #N` dans le corps de la PR**, pour que l'issue se ferme à la fusion. Le mot-clé reste anglais : « Ferme #N » ne ferme rien et ne le dit pas. Une PR qui renvoie à une issue sans la clore écrit « Rattaché à #N ».
- **Pas de tiret cadratin** ; noms de classes en français sans accents ; doc-comments `///` (JEP 467).
- Jamais `@SuppressWarnings` / `//NOPMD` pour taire un warning qualité : **refactorer** (Extract Class/Method).

## Ce qui s'écrit se relit

L'article A31 le rend obligatoire : toute prose qu'un humain lira hors de l'échange qui l'a produite,
javadoc, documentation, ADR, libellés d'interface et de ligne de commande, messages de commit, passe
la grille de la compétence `humaniseur` avant d'être commise.

Les sept tics de la section « Le registre » de [CONTRIBUTING.md](CONTRIBUTING.md) en sont le
sous-ensemble **opposable** : la grille sert à relire, les sept servent à refuser une relecture.

## Les compétences

Le procédural vit dans `.agents/skills/<nom>/SKILL.md`, au format ouvert **Agent Skills**. Chacune
porte une **loi d'airain**, une fonction de garde, et les échecs réels qui l'ont produite. Elles ne
remplacent pas ce document : il se lit au début, une compétence s'ouvre au moment du geste.

| Compétence | Quand l'ouvrir |
|---|---|
| `triage` | avant d'ouvrir un chantier, pour décider s'il y a lieu |
| `ouvrir-une-issue` | ce qu'il faut avoir mesuré, vérifié et compris avant la première ligne |
| `worktree` | avant tout travail de branche, et avant de construire pendant que l'appli tourne |
| `tdd` | la boucle rouge, vert, refactor, et son échelle |
| `deboguer` | sur un défaut : cause racine avant correctif, le premier test reproduit |
| `mutation` | dès qu'un comportement est complet, et pour tout garde qu'on écrit |
| `recette-filmee` | le banc, le seuil, l'auto-test, le témoin |
| `revue-visuelle` | la planche avant/après |
| `ecrire-une-adr` | format, statut, chaînage, et l'article auquel elle se rattache |
| `clore-une-issue` | ce qu'on laisse derrière soi |
| `clore-un-chantier` | les douze passes de clôture |
| `humaniseur` | relire une prose française qui porte des marques d'écriture par LLM |
| `audit-croise` | confronter un arbitrage à un second lecteur, sans lui faire dire une mesure |
| `openspec-propose`, `-apply-change`, `-update-change`, `-sync-specs`, `-archive-change`, `-explore` | le cycle d'un changement dans la spécification vivante |

**Le fonds est dans `.agents/skills/`, hors de tout dossier de marque.** `.claude/skills/` n'en est
qu'une copie, pour que Claude Code les découvre. Servir un autre agent, c'est ajouter une copie : on
ne touche pas au fonds.

On copie plutôt qu'on ne lie : sous Windows, sans `core.symlinks`, git écrit un fichier texte
contenant le chemin, et la découverte casse en silence.

## La spécification vivante

`openspec/` est le cadre où se pose **ce qu'on est en train de changer**, entre ce que le produit
fait (`dev-docs/`) et ce qu'on a décidé (`dev-docs/decisions/`). Un changement porte ses delta
specs, et l'archivage les fusionne dans les specs principales.

**Ce que le cadre contient aujourd'hui : sa configuration, et rien d'autre.** Il n'y a pas encore de
`openspec/specs/`, aucun changement proposé ni archivé. Le cadre est posé pour le premier chantier
qui en aura besoin, et cette page dira ce qu'il porte quand il portera quelque chose. Un dossier
vide présenté comme un point de départ enverrait un repreneur nulle part.

Le cycle, quand il servira : `openspec-propose` ouvre le changement, `openspec-apply-change` le
réalise, `openspec-archive-change` le clôt en mettant à jour la spécification. `openspec-explore`
sert à instruire avant de proposer. La langue des artefacts est déclarée dans `openspec/config.yaml`,
et c'est le français.

## Cycle de vie d'un chantier

Un **chantier** est un lot de travail d'ampleur **EPIC**, réparti sur plusieurs PR (ex. l'EPIC « Réglages auto-découverts »). Tout chantier suit ce cycle.

### À l'ouverture : analyse de départ

**0. Trier et regrouper les issues existantes, AVANT la cartographie et le plan.** Balayer les issues ouvertes **par concept**, pas par mot-clé (« la sévérité s'écrit dans le texte » et « des avertissements vivent hors du système de restitution » sont le même sujet sous deux noms) ; chercher les **EPIC vivants** et les issues « **différée de #N** » (elles signalent un parent, parfois clos, dont la moitié restante n'a plus de toit) ; **décider des rattachements** : une issue appartient au chantier qui traite sa **cause**, pas à celui qui a remarqué son **symptôme** ; **recadrer titre ET corps** des issues déplacées (un recadrage laissé en commentaire sous un corps périmé ne recadre rien). Sans cette étape, le recoupement entre deux chantiers ne se découvre qu'au **conflit de fusion**, quand deux chemins existent déjà. Vécu : un audit de passe 7 a produit une issue doublonnant six des huit cas d'une issue existante, mieux fondée, dont le prérequis a fusionné pendant que le doublon s'écrivait.

**Puis** : cartographier l'existant (repérer les **patterns réutilisables** avant d'écrire du neuf), rédiger un **plan**, puis **découper en issues** reliées à un **EPIC**.

### Au commencement de chaque issue : se signaler

Avant la première ligne de code, énoncer **ce qu'il y a à faire** (une phrase, dans les termes du
problème et non de la solution), **pourquoi maintenant** (ce qui la rend traitable ou urgente) et
**dans quelle continuité** elle s'inscrit. Déposer ces trois phrases **en commentaire sur l'issue**,
avec le chantier, la branche et le remède envisagé, puis **assigner l'issue**.

Les deux, pas l'un ou l'autre : l'assignee est le signal qui se filtre
(`gh issue list --assignee "*"`), le commentaire porte ce que l'assignee ne dit pas. Un signalement
se relâche : quand on s'arrête, retirer l'assignee et le dire.

La forme exacte du bloc, et la consigne d'attendre un accord avant de commencer, sont propres à
Claude Code et vivent dans [CLAUDE.md](CLAUDE.md).

### Pendant l'issue : rouge, vert, refactor, en boucle

Le tour se répète à **chaque petit pas** jusqu'à ce que le comportement soit complet : une issue en compte des dizaines. Le test **avant** le code ; sur un défaut, le premier test **reproduit** le défaut. Un rouge **inattendu** est une trouvaille : lire le message avant de corriger. Si le rouge dure, le pas était trop gros : revenir au dernier vert et couper en deux.

**Le REFACTOR est la troisième phase de chaque tour**, pas une étape de fin d'issue. Il porte sur le pas qu'on vient de faire ; ce qui déborde se note pour la passe 7.

Quand la boucle s'arrête : **PIT** sur les classes pures livrées, survivants lus un par un.

### En clôturant une issue : éditer, pas empiler

Le **corps** de l'issue porte l'état courant de la vérité ; les **commentaires** portent le journal. **Tout commentaire qui change la lecture de l'issue est suivi d'une édition du corps** - une prémisse démentie laissée en commentaire fait lire l'erreur d'abord (#3451 et #3439 en portent encore la trace).

Avant de fusionner : **le corps de la PR et celui de l'issue se lisent-ils dans six mois, sans le fil ?**

### À la clôture : 12 passes obligatoires, numérotées 0 à 11 (dans l'ordre)

⚠️ Les passes **1 à 9 gardent leur numéro** : 35 des 42 citations de passes du dépôt vivent dans des ADR **immuables**. Les deux passes ajoutées sont donc aux extrémités.

0. **Relecture des ADR existantes**, contre `origin/main` : quelles décisions régissent ce code, et le chantier en a-t-il contredit une ? Un chantier a le droit de dépasser une ADR, pas de le faire en silence. `git log --oneline <sha-d-ouverture>..origin/main -- dev-docs/decisions/` liste celles apparues pendant le chantier, les plus susceptibles d'avoir été ignorées.
1. **Audit d'intégration** : vérifier que les évolutions de `main` survenues pendant le chantier n'ont rien laissé à rajouter avant finalisation (rebase, nouveaux points d'accroche à câbler, régressions, conventions apparues entre-temps).
2. **Cohérence CLI ↔ UI** : quand le chantier ajoute/change une capacité métier, la CLI (`fr.univ_amu.iut.cli`) doit exposer l'équivalent (même comportement, mêmes règles/formats) pour rester au niveau de l'IHM ; aligner si petit (documenté/testé par les passes suivantes), sinon créer une issue. « Sans objet » si le chantier est purement présentationnel.
3. **Doc développeur** : mettre à jour le site dev (`dev-docs/` : architecture, patterns, injection, « ajouter une fonctionnalité »…). ⚠️ Chercher aussi ce qui est devenu FAUX, pas seulement ce qu'il y a à ajouter : une page qui décrit fidèlement un mécanisme remplacé ne rougit nulle part et se lit comme vraie (#3439 a livré les masques dérivés, `captures.md` a décrit « seize » fichiers pendant une semaine). Partir des fichiers touchés et chercher qui les cite dans `docs/`, `dev-docs/`, `brief/`. Les ADR s'écrivent en passe 10, pas ici.
4. **Doc utilisateur** : documenter le chantier pour les utilisateurs (site `docs/`), avec autant de captures que nécessaire.
5. **Brief projet** : répercuter dans le brief projet (sous `brief/` dans ce dépôt, document de conception vivant : besoin, parcours utilisateurs, maquettes, MCD, pas un sujet pédagogique, le lecteur est un contributeur du produit) toute évolution qui change un de ces éléments de conception. Rarement « sans objet ».
6. **Tests** : couvrir chaque usage par des tests d'intégration (TestFX) et des tests E2E. ⚠️ Chercher ce qui manque, pas relire ce qu'on a écrit : inventorier les capacités depuis le diff du chantier (`git diff origin/main...`), et pour chacune dire quel test la couvre et à quel niveau - « aucun » est recevable, mais doit devenir une issue (passe 9), jamais un trou tacite. Angles morts récurrents : chemins non nominaux (refus, erreur, annulation, état vide, feature désactivée), parité CLI ↔ IHM (couvert d'un seul côté = à moitié couvert), cas réel (un test synthétique vert ne prouve pas la vraie nuit). ⚠️ Un zéro se confirme à la main : l'inventaire par `grep` se trompe dans les deux sens (homonymes ; tests qui pilotent le service sans porter la clé de vue ; commande CLI invoquée en kebab-case vs classe instanciée). Croiser deux signaux : sur un audit réel, les greps naïfs annonçaient jusqu'à 20 commandes non testées, la vérification en a trouvé zéro sur 41. Un « aucun test » issu d'un grep est une hypothèse, pas un constat : l'ouvrir avant d'en faire une issue. Les E2E valent par ce qu'ils traversent : fusionner deux scénarios quand le défaut probable est entre eux (les coutures), sans tomber dans le test-fleuve. Ce qui n'est pas automatisable (rendu fin, fluidité, vrai serveur/carte SD/matériel) part en recette (`dev-docs/recette/sessions/`, cases `Sxx-NN`, une case = un fait observable) - sinon « pas automatisable » devient « pas vérifié ». ⚠️ Les deux dispositifs systématiquement sautés, que la passe 6 exige : PIT ciblé sur les classes pures du chantier (`-Pmutation` + `-DtargetClasses`), dont on lit les survivants un par un : vrai trou → test ; défensif inatteignable → assumé sans test creux ; artefact de ciblage → élargir `targetTests` et remesurer, le pourcentage seul ne disant rien ; et les E2E `bats` (`src/test/bats/`) dès qu'une commande CLI bouge, car ils lancent le vrai fat-jar et voient packaging, analyse d'arguments picocli et codes de sortie, invisibles aux tests Java in-process : `cli-surface.bats` (la commande existe et refuse une invocation vide) et `cli.bats` (elle fait ce qu'elle promet).
7. **Harmonisation** : prendre du recul sur l'application entière, en deux temps. D'abord un audit global, exhaustif et scrupuleux (qu'est-ce qui ressemble au résultat du chantier, qu'est-ce qui en bénéficierait), pour comprendre ce qui sous-tend la demande initiale. Puis un refactoring de conceptualisation (rendre l'application plus lisible et compréhensible ; réduire la duplication et abstraire sont des outils, pas le but). Discuter les choix, doutes et conséquences avec l'utilisateur.
8. **Revue visuelle** : inspecter toutes les conséquences visibles du chantier (chaque écran et chaque état). Les captures sont une documentation vivante de l'état réel de l'application : ajouter autant de captures que nécessaire pour refléter toutes les fonctionnalités visuelles (une capture ajoutée = validation rejouable). Régénérer, ouvrir une par une, regarder ; un geste testé n'est pas un écran regardé (texte coupé, glyphe absent, régression de style ne font rougir aucun test).
9. **Nouveaux chantiers** : identifier les suites et créer les issues nécessaires.
10. **ADR du chantier écrites** (`dev-docs/decisions/`), en balayant les passes 0 à 9. Ici et non en passe 3, parce que les passes 4 à 9 produisent des décisions : les cinq ADR du chantier #3151 portent toutes « suite de », aucune n'était née à l'endroit prévu. Le numéro est celui de l'issue. Chaque ADR déclare son article et son niveau de vérification dans son en-tête OKF : `article: A<n>` et `verification: certaine | probable | humaine`, gagé par `enforced_by:` pour les deux premiers et par `loupe:` pour le troisième. ⚠️ Une décision de ne pas faire est une décision, et c'est celle qu'on oublie : elle ne laisse pas de code derrière elle.
11. **Bilan** : synthèse du chantier (ce qui a été livré, dette restante, décisions prises) déposée dans l'EPIC ; les décisions renvoient aux ADR de la passe 10 plutôt que d'en redérouler le raisonnement. ⚠️ Et il se montre : produire un artefact visuel avant / après (une ligne par conséquence visible, la capture des deux états, la phrase qui dit ce qu'on doit y voir), soumis avant de clore l'EPIC : son objet est d'obtenir un assentiment, pas de documenter une décision déjà prise. La passe 8 a ouvert et recadré toutes ces captures ; sans cet artefact, ce travail reste dans la tête de qui l'a fait, et le bilan écrit demande qu'on le croie là où une capture le montre. Les défauts trouvés en chemin y figurent recadrés et agrandis (un glyphe de 12 px ne se juge pas à l'échelle 1), et ce qui n'a pas été corrigé y a sa place : une troncature montrée et assumée vaut mieux qu'une omission.

> Raison d'être et mode opératoire détaillés de chaque passe :
> [dev-docs/cycle-de-chantier.md](dev-docs/cycle-de-chantier.md) (publié sous
> <https://companion-dev.echonuit.fr/cycle-de-chantier/>).

## Chercher dans le dépôt

Trois outils, trois questions. `graphify` d'abord (voir ci-dessous), puis :

- **`semgrep`** pour une question de forme : « qui appelle X ? », « qui construit un Y à la
  main ? ». Il lit l'arbre syntaxique, pas les lignes.
  `semgrep --lang java --metrics=off --pattern 'Habillage.$M(...)' src/main`
- **`grep`** pour un texte : un message, un libellé, une ligne de journal.

⚠️ Le moteur libre de `semgrep` ne traite pas les **annotations Java** comme motif autonome :
`--pattern '@CasDeRecette(...)'` rend **zéro occurrence et zéro erreur** sur un dépôt qui en compte
des dizaines. Un zéro ne prouve donc rien tant que le motif n'a pas été éprouvé sur un cas connu.

⚠️ Et les pièges de `grep` qui ont déjà menti ici : un sous-motif se compte lui-même
(`grep -c "couvert"` compte « non couvert »), une puce de `dev-docs/recette/sessions/` se replie sur
la ligne suivante et n'est lue qu'à moitié, un fichier à octets NUL rend `grep` muet.

Le détail, avec les cas : `dev-docs/chercher-dans-le-depot.md`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
