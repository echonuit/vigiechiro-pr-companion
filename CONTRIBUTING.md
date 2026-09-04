# Contribuer à VigieChiro Companion

Merci de votre intérêt ! Ce document explique comment proposer une évolution : mettre en place
l'environnement, respecter l'architecture, et soumettre une Pull Request.

Le détail des tests est dans [TESTING.md](TESTING.md) ; la politique de sécurité et de données dans
[SECURITY.md](SECURITY.md).

> 📖 **Documentation développeur** (architecture, navigation, persistance, injection, captures,
> CI/CD…, avec diagrammes) : **<https://companion-dev.echonuit.fr/>**.
> Ce fichier reste le point d'entrée « contribution » ; la doc dev en est la version approfondie.

---

## 1. Mettre en place l'environnement

Tout passe par le **Maven Wrapper** `./mvnw` (aucune installation de Maven). Le JDK doit être un
**JDK 25 standard** (Temurin / `25.0.2-open`), **pas** un JDK packagé avec JavaFX : JavaFX 26 vient
des dépendances Maven, et la *Headless Platform* est purement logicielle (cf. [TESTING.md](TESTING.md)).

```bash
git clone https://github.com/echonuit/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
./mvnw verify        # premier appel : télécharge Maven + dépendances, puis tout est en cache
```

Au **premier `./mvnw`**, le plugin `git-build-hook` configure silencieusement
`core.hooksPath=.githooks`, ce qui active le hook **pre-commit** ([.githooks/pre-commit](.githooks/pre-commit)).
Ce hook **formate les `.java` stagés** avec **Spotless** (Palantir Java Format) avant chaque commit.

Si VS Code vous propose **SonarQube for IDE** (l'extension est recommandée dans
[.vscode/extensions.json](.vscode/extensions.json)), **réglez-la avant de vous y fier** : laissée par
défaut, elle applique 542 règles Java et en contredit trois qui sont délibérément arbitrées dans
[pmd-ruleset.xml](pmd-ruleset.xml), ce qui produit une soixantaine de remontées sur du code conforme.
Le bloc à recopier tient en dix lignes ; il vit dans vos réglages **utilisateur** et non dans le dépôt,
pour une raison expliquée avec lui dans
[dev-docs/tests-et-qualite.md](dev-docs/tests-et-qualite.md#sonarqube-for-ide-facultatif-à-configurer).
C'est **PMD qui fait foi** : lui seul bloque la CI.

### L'outillage Python, dès que vous touchez à un garde

Les gardes du dépôt sont en Python, et leurs versions sont **épinglées** dans
[pyproject.toml](pyproject.toml), groupe `gardes`. Le `ruff` de votre `PATH` n'est probablement pas
celui-là.

```bash
python3 -m venv .venv
.venv/bin/python -m pip install --group gardes
.venv/bin/ruff --version        # doit rendre la version épinglée, pas celle du PATH
```

**Rien à ajouter à `.gitignore`** : depuis Python 3.11, `venv` écrit lui-même un `.venv/.gitignore`
contenant `*`. Le dossier ne paraît pas dans `git status`. VS Code active ce `.venv` tout seul, si
bien que le terminal de l'IDE porte les bonnes versions sans qu'on y pense.

**Pourquoi ça compte, et non « c'est plus propre ».** La demande #5105 est sortie rouge sur une règle
`ruff` que la version du poste ne connaissait pas : le contrôle local et celui de la CI ne lisaient
pas les mêmes règles, donc aucune attention ne pouvait rattraper l'écart. Les deux commandes qui
bloquent la CI, à jouer avant de pousser :

```bash
.venv/bin/ruff check         scripts .github/scripts
.venv/bin/ruff format --check scripts .github/scripts
```

### La ligne de commande OpenSpec, si vous touchez à la spécification vivante

Elle n'est pas nécessaire pour construire ni pour tester le produit. Elle l'est dès que vous ouvrez
une des six compétences `openspec-*` ou une des six commandes qui y mènent (`/instruire`,
`/proposer`, `/realiser`, `/reprendre`, `/fusionner`, `/archiver`), parce que chacune de leurs
étapes l'appelle.

Le devcontainer l'installe et la met sur le `PATH`. Hors devcontainer :

```bash
npm ci --prefix .github/openspec
export PATH="$PWD/.github/openspec/node_modules/.bin:$PATH"
openspec --version        # 1.10.0
```

**Le nom compte.** Les compétences déclarent `allowed-tools: Bash(openspec:*)`, un motif littéral
qui n'autorise que les commandes commençant par le mot `openspec` : `npx @fission-ai/openspec` est
refusé. C'est le lien `node_modules/.bin/openspec` qu'il faut exposer, sous ce nom exact.

**Une installation globale divergente est le piège du dispositif**, et c'est celui qui a ouvert
le chantier #4511 : l'outil ne vivait qu'à un endroit, hors du dépôt, dans une version que rien ne
rapprochait de ce que les fichiers décrivaient. Si `which openspec` ne pointe pas dans
`.github/openspec/node_modules/.bin`, vous n'utilisez pas la version du dépôt. Le garde
`scripts/methode/verifie-version-openspec.py` tient l'égalité côté dépôt ; il ne peut rien pour le
`PATH` de votre poste.

---

## 2. L'architecture : package-by-feature + MVVM

L'architecture est **package-by-feature + MVVM** (cf. [l'aperçu du README](README.md#architecture)).
Chaque feature vit dans `src/main/java/fr/univ_amu/iut/<feature>/` et se découpe en `model/`,
`viewmodel/`, `view/`, `di/`.

> 📖 En détail dans la doc dev :
> [Architecture](https://companion-dev.echonuit.fr/architecture/) ·
> [Navigation](https://companion-dev.echonuit.fr/navigation/) ·
> [Persistance](https://companion-dev.echonuit.fr/persistance/) ·
> [Injection](https://companion-dev.echonuit.fr/injection/) ·
> [Ajouter une fonctionnalité](https://companion-dev.echonuit.fr/ajouter-une-fonctionnalite/).

Points d'attention :

- **Emplacement des `.fxml` / `.css`** : **à côté** de leur controller, dans
  `src/main/java/.../view/` (et non dans `src/main/resources`). Le `pom.xml` copie les fichiers
  non-Java de `src/main/java` dans `target/classes` au même chemin de paquetage. Les ressources
  **partagées** (migrations `db/migration`, thème) restent, elles, dans `src/main/resources`.
- **Câblage Guice** : la feature publie ses services / ViewModels via son module dans `di/` ;
  les controllers FXML sont injectés via la `controllerFactory`.
- **Respect des frontières MVVM** : elles sont **vérifiées par ArchUnit** (model sans JavaFX,
  viewmodel sans `javafx.scene/fxml/stage`, view sans JDBC, pas de dépendance vers le `view` /
  `viewmodel` d'une autre feature). Voir la liste complète dans [TESTING.md](TESTING.md).
- **`sites` est la feature de référence** : c'est le modèle à reproduire (ViewModel pur lié à une
  vue FXML).

Toute évolution de comportement doit être accompagnée de **tests** (de bout en bout quand c'est une
feature : IHM → ViewModel → service → base).

---

## 3. Conventions de code et de commit

**Code** :

- formatage **Spotless / Palantir Java Format** (le hook s'en charge ; sinon `./mvnw spotless:apply`) ;
- doc-comments **Markdown** `///` (JEP 467), pas de `/** */` HTML, gardé par `ConventionsDEcritureTest` ;
- **noms de classes en français**, sans accents dans les identifiants (`Navigateur`, `Passage`,
  `EtapeNavigation`...), gardé par `ConventionsDEcritureTest` ;
- **pas de tiret cadratin** : tiret simple, deux-points ou virgule. La règle vaut **partout** (doc,
  commentaires, chaînes affichées, styles, scripts, ateliers), et un garde la fait respecter
  ([ADR 2843](dev-docs/decisions/2843-typographie-cliquet-plutot-que-nettoyage.md)). Le glyphe reste
  permis là où il **est** la donnée plutôt qu'une ponctuation : la valeur absente
  (`Formats.VALEUR_ABSENTE`), un libellé de l'application **cité** entre guillemets français, une
  classe de caractères d'analyseur. Le titre de PR, lui, n'est pas gardé : il alimente le CHANGELOG.

**Commits** : [Conventional Commits](https://www.conventionalcommits.org/fr/) **en français**, le
scope étant le nom de la feature ou du domaine :

```
feat(passage): écran pivot d'une nuit (statut + navigation)
fix(importation): import hors fil JavaFX gelait l'écran
test(qualification): test d'acceptation du verdict de qualité
docs(readme): renvois vers CONTRIBUTING/TESTING/SECURITY
chore(deps): bump assertj 3.27.7
```

- **petits commits** logiques (un par préoccupation), message axé sur le **pourquoi** ;
- toujours **créer** un nouveau commit plutôt qu'amender.

> [!IMPORTANT]
> **Pas d'espace avant les deux-points** : `feat(passage): …`, jamais `feat(passage) : …`. Ici le `:`
> est un **token de syntaxe**, pas une ponctuation de phrase : la règle typographique française ne s'y
> applique pas, comme elle ne s'applique pas au `:` d'un `switch` Java. Le sujet qui suit, lui, reste
> du français avec sa typographie.
>
> Ce n'est pas un détail de style. Un espace rend le sujet **illisible pour semantic-release**, qui
> cesse alors de publier **sans rien faire rougir**. Le dépôt l'a vécu : 58 commits releasables
> accumulés sans aucune version, CI verte tout du long (cf.
> [ADR 0040](dev-docs/decisions/0040-le-sujet-de-commit-est-une-syntaxe.md)).

**Le titre de votre PR compte plus que vos messages de commit.** Les PR sont fusionnées en **squash**,
et c'est le **titre de la PR** qui devient le sujet du commit sur `main` : c'est donc lui que
semantic-release lira, et lui que la CI vérifie (`.github/workflows/titre-pr.yml`). Il doit suivre la
même convention.

Le **type** du commit pilote la version publiée (cf. §7) : `feat:` → mineure, `fix:` → patch,
`BREAKING CHANGE:` → majeure. Les autres types (`docs`, `chore`, `test`, `refactor`...) ne déclenchent
pas de release.

---

### Le registre : direct, et rien de plus

La règle vaut pour tout ce qui s'écrit ici : commentaires, ADR, documentation, messages de commit.

Écrivez la chose, pas son emballage. Une phrase dit un fait ; si elle dit surtout comment le fait
est amené, elle s'enlève.

Sept tics reviennent :

| Tic | Exemple | À la place |
|---|---|---|
| l'antithèse | « c'est X, pas Y » quand personne n'a proposé Y | « c'est X » |
| l'annonce | « deux choses méritent d'être dites », « et c'est instructif » | dire les deux choses |
| le tricolon | trois membres dont le troisième n'ajoute rien | deux membres |
| l'aphorisme final | une formule qui referme un paragraphe | rien |
| l'objection inventée | « ce n'est pas tant X », « je ne dis pas que Y » | la phrase que ça défend |
| la fausse alternative | « on aurait pu faire Z, mais » quand Z n'a jamais été envisagé | la contrainte réelle |
| le gras réflexe | un mot sur trois lignes en gras | rien |

Un titre de section pour deux paragraphes est un titre de trop. Une explication qui commence par
« pourquoi » raconte souvent une décision : c'est une ADR, et on la **cite**.

Le gras marque ce qui se lit de travers sans lui. `dev-docs` et `docs` en portent 11 995 emplois
pour 36 377 lignes, soit un tiers de ligne : à ce taux il ne marque plus rien. Aucun contrôle
mécanique ne fera le tri, parce que la syntaxe voit le gras et non son utilité. Un gras **code**
quelque chose quand il désigne un libellé d'interface, un nom de colonne ou une contrainte forte
comme « hors fil JavaFX » ; il **emballe** quand il tombe sur un mot ordinaire. Seul le second se
retire, et c'est la relecture qui tient la règle. Une liste dont chaque entrée ouvre sur un
mini-titre en gras suivi d'un deux-points est le même défaut, un paragraphe qu'on a découpé : il y
en a 214.

Un commentaire décrit le comportement d'aujourd'hui. « Jusqu'ici », « auparavant », « naguère »,
« désormais » annoncent presque toujours le contraire, et des dizaines de lignes de javadoc en
portent encore.
L'historique a ses lieux : `git log`, le CHANGELOG, la section « Alternatives écartées » d'une ADR.

**Une seule apostrophe, l'ASCII** (`'`). Le dépôt en portait 220 courbes le 2026-08-24 ; elles sont
normalisées, et `scripts/adr/4368-apostrophe-droite.py` tient la règle à zéro. Trois exemptions,
toutes déclarées dans son en-tête : le `CHANGELOG.md`, qui est engendré, les SVG rendus par Mocodo,
qui substitue à la source droite, et le signe cité plutôt qu'employé. Le titre de PR est gardé lui
aussi, parce qu'il devient une ligne du CHANGELOG publié.

Le connecteur lourd en tête de phrase, « Cependant », « Par ailleurs », « En outre », est le tic
français le plus cité. Mesuré ici : **zéro** sur près de cinquante mille lignes de documentation. Il n'est donc pas
retenu comme règle, et cette mesure est ce qui le dit.

Ces règles viennent de deux inventaires : [« Signs of AI writing »](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing)
(WikiProject AI Cleanup, 35 motifs) et son équivalent français,
[« Aide:Identifier l'usage d'une IA générative »](https://fr.wikipedia.org/wiki/Aide:Identifier_l%27usage_d%27une_IA_g%C3%A9n%C3%A9rative).
Ce qui n'a pas été retenu l'a été après mesure, pas par goût, et
[dev-docs/registre-editorial.md](dev-docs/registre-editorial.md) le dit motif par motif.

Les sept tics ci-dessus sont ce que ce dépôt retient. La grille complète, cinquante-quatre motifs
avec leurs exemples et leurs faux positifs, est dans la compétence `humaniser`
(`.agents/skills/humaniser/SKILL.md`) : elle sert à relire, pas à trancher un commit. L'article A31
de la [constitution](CONSTITUTION.md) la rend obligatoire pour toute prose visible.

## 4. Workflow de contribution

```bash
# 1. Forkez le dépôt, puis :
git clone https://github.com/<vous>/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
git checkout -b feat/<feature>

# 2. ... code + tests ...
./mvnw verify                      # 0 échec, 0 skip indésirable
git commit ...                     # le hook formate le code

# 3. Poussez, puis ouvrez la PR vers la branche par défaut
git push -u origin feat/<feature>

# Un seul commit : --fill reprend son sujet, déjà conforme.
gh pr create --fill

# Plusieurs commits : --fill titre la PR avec le NOM DE BRANCHE, que le garde refuse.
# Le titre s'écrit alors à la main, et c'est là qu'il se vérifie.
TITRE="feat(passage): écran pivot d'une nuit"
./.github/scripts/verifie-titre-pr.sh "$TITRE" && gh pr create --title "$TITRE" --body-file corps.md
```

- **`gh pr create --fill` ne convient qu'à une branche d'un seul commit.** Au-delà, `gh` titre la
  PR avec le nom de branche, `feat/1234-mon-sujet`, que `titre-pr.yml` refuse. Mesuré le
  2026-08-26 sur les 30 dernières PR fusionnées : 20 tenaient en un commit, 10 en plusieurs.
- **Vérifiez le titre avant d'ouvrir la PR**, pas après. Le script ci-dessus est exactement celui
  que lance `titre-pr.yml` : le passer en local coûte une frappe, le découvrir en CI coûte une PR à
  ré-éditer et une vérification à relancer. Le défaut n'entre pas au commit mais à la frappe du
  titre, et les deux se mesurent : les quatre PR rouges du 2026-08-26 avaient toutes trois commits
  ou plus, donc un titre tapé à la main, quand leurs sujets de commit étaient tous conformes.
- La PR cible la **branche par défaut** du dépôt (`gh pr create --fill` la sélectionne
  automatiquement). `@nedseb` est ajouté en reviewer automatiquement ([CODEOWNERS](.github/CODEOWNERS)).
- Privilégier des **PR petites et séquentielles** (par exemple : ViewModel + tests, puis vue
  principale, puis vue secondaire) plutôt qu'une feature entière en un seul gros diff.
- Merger quand la CI est verte.
- Avant tout commit, vérifiez l'identité git : `git config user.email`.

---

## 5. Cycle de vie d'un chantier

Un **chantier** est une évolution d'ampleur **EPIC**, répartie sur **plusieurs PR** (le §4 décrit
_une_ PR ; ici on décrit l'ensemble). Il **s'ouvre** par une analyse et **se clôt** par 14 passes,
numérotées **0 à 11** (les passes 1 à 9 gardent leur numéro : des dizaines d'ADR **immuables** les citent).

**À l'ouverture**, dans cet ordre : **trier et regrouper les issues existantes** (balayage **par
concept** et non par mot-clé, recherche des EPIC vivants et des issues « différées de #N », décision des
rattachements, recadrage des titres et des corps déplacés) ; puis cartographier l'existant (réutiliser
les patterns en place plutôt que réinventer) ; puis rédiger un plan ; puis découper en **issues reliées
à un EPIC**.

**Le palier du sous-chantier.** Pour chaque lot, une question avant de l'accrocher : combien de PR ?
Une seule, et une issue rattachée à l'EPIC suffit. **Une PR par issue : dès la deuxième, c'est un
chantier** : le lot s'ouvre alors en **sous-chantier**, un EPIC enfant, ses issues s'y rattachent, et
la case du parent pointe vers lui. Un lot multi-PR accroché sous une case à cocher n'a ni périmètre
écrit, ni bilan, ni les quatorze passes, et se termine sans que personne sache s'il est fini. La forme
observée dans les EPIC existants n'est pas la règle : l'imiter reproduit le défaut.

Le triage vient **en premier** parce qu'une issue est rattachée au chantier qui a remarqué son
**symptôme**, pas à celui qui traite sa **cause** : deux issues sur le même sujet, écrites depuis deux
angles, ne se ressemblent pas, et le recoupement se découvre alors au **conflit de fusion**.

Le balayage **par concept** ne se fait pas au `grep`, qui ne sait chercher que des chaînes. Le dépôt se
donne un **graphe de connaissances** (`graphify-out/`, hors suivi Git : code, workflows, `bats`, docs et
brief mêlés) qu'on interroge par `graphify query "<question>" --budget 2500`. Il répond à « qui d'autre
fait X ? » quand X est une idée, et donne les appelants réels au niveau **méthode**. Il ne modélise
**que notre code** (rien du JDK ni des bibliothèques) et sa traversée est **bruitée** : sa sortie est une
**hypothèse à confirmer**, jamais un inventaire.

**Au commencement de chaque issue**, avant la première ligne de code : dire **ce qu'il y a à faire**,
**pourquoi maintenant**, et **dans quelle continuité** ça s'inscrit. Le troisième est celui qu'on saute,
et le seul qui ne se retrouve pas après coup.

Ces trois phrases se déposent **en commentaire sur l'issue**, avec le **chantier**, la **branche** et le
**remède envisagé**, et l'issue s'**assigne**. L'assignee est le signal qui se filtre
(`gh issue list --assignee "*"`) ; le commentaire porte ce qu'il ne dit pas. Annoncer le remède est le
vrai gain : deux personnes peuvent voir le même défaut et imaginer deux corrections dont l'une est
meilleure ; annoncées, le désaccord se règle **avant** le code.

**Un signalement se relâche** : quand on s'arrête, on retire l'assignee et on le dit. Une revendication
oubliée est pire que rien : elle fait passer une issue libre pour prise. Elle répond à « cette issue
est-elle prise ? », **pas** à « est-ce la même que celle-là sous d'autres mots ? » : cette seconde
question reste le travail du triage.

**Pendant l'issue : rouge, vert, refactor - une BOUCLE, pas une étape.** Le tour se répète à chaque
**petit pas**, et une issue en compte **autant que de pas** : quelques minutes chacun, souvent plusieurs
dizaines avant que le comportement soit complet. Un petit pas est le plus petit **fait observable**
qu'on puisse rendre rouge puis vert (« le refus quand la date manque »), pas la fonctionnalité.

Le test s'écrit **avant** le code, et sur un défaut le premier test **reproduit** le défaut. C'est la
règle « un garde-fou se vérifie en le voyant rouge » déplacée là où elle est gratuite : après coup, il
faut réintroduire le défaut à la main pour obtenir le même rouge. Un test qui échoue **pour une autre
raison que prévu** est une **trouvaille** : lire le message avant de corriger. **Si le rouge dure**
plus de quelques minutes, le pas était trop gros : revenir au dernier vert et le couper en deux.

**Une trouvaille s'ouvre en issue au moment où on la fait**, pas à la clôture et pas en commentaire.
Elle se rattache à l'**EPIC du chantier** si elle est dans son périmètre, sinon à l'**EPIC des
suites** ([#4562](https://github.com/echonuit/vigiechiro-pr-companion/issues/4562)) ; en cas de doute,
les suites, car la passe 9 est faite pour corriger un rattachement et ne peut rien pour une trouvaille
jamais écrite. **Le rattachement est une sous-issue** (`gh issue create --parent <EPIC>`), pas une
ligne ajoutée au corps de l'EPIC : une liste tenue à la main se périme sans bruit et ne se compte
pas. Ce qui se perd n'est pas la trouvaille mais son **détail** : le chiffre, le numéro de
run, le message littéral, c'est-à-dire ce qui la rend traitable. Un signalement en commentaire ou dans
un compte rendu ne compte pas : il est réel, et il sort de la vue à la fusion.

**Le REFACTOR est la troisième phase de CHAQUE tour**, pas une étape de fin d'issue ni la seule passe 7 :
le moment le moins cher pour retravailler un code est celui où l'on s'en souvient, et ce moment dure un
tour. Ce sont **deux échelles** et elles ne se croisent pas : le tour retravaille **le pas qu'on vient de
faire**, sous le test qu'on vient de rendre vert et seul ; la passe 7 retravaille **l'application**, et se
discute. Ce qui déborde du pas courant se note et revient en passe 7.

**Quand la boucle s'arrête**, le comportement étant complet : lancer **PIT** sur les classes pures que
l'issue a livrées et lire les **survivants** un par un. Attendre la passe 6 fait découvrir le trou
plusieurs PR après le code.

**Le corps de l'issue porte la vérité, les commentaires portent le journal.** Une prémisse démentie, une
mesure qui corrige la précédente, un remède qui change : tout commentaire qui change la lecture de
l'issue est **suivi d'une édition du corps**. Et avant de fusionner : le corps de la PR **et** celui de
l'issue se lisent-ils dans six mois **sans** le fil de discussion ?

**À la clôture** (dans l'ordre) :

0. **Relecture des ADR existantes**, contre `origin/main` : le chantier a-t-il **contredit** une décision
   en place, et si oui délibérément ? Un chantier a le droit de dépasser une ADR, pas de le faire en
   silence : deux règles opposées resteraient dans le dépôt, et le prochain lecteur appliquerait la
   première trouvée.
1. **Audit d'intégration** : vérifier que les évolutions de `main` survenues pendant le chantier n'ont
   rien laissé à rajouter (rebase, nouveaux points d'accroche à câbler, régressions).
2. **Cohérence CLI ↔ UI** : quand le chantier ajoute/change une **capacité métier**, la **CLI**
   (`fr.univ_amu.iut.cli`) doit exposer l'équivalent (même comportement) ; aligner si petit, sinon
   ouvrir une issue. « Sans objet » si le chantier est purement présentationnel.
3. **Doc développeur** (site `dev-docs/`) à jour. Chercher aussi **ce qui est devenu FAUX**, pas
   seulement ce qu'il y a à ajouter : une page qui décrit fidèlement un mécanisme **remplacé** ne rougit
   nulle part et se lit comme vraie. Partir des **fichiers touchés** et chercher qui les **cite** dans
   `docs/`, `dev-docs/` et `brief/`, plutôt que de partir de sa mémoire. Les **ADR s'écrivent en passe
   10**, pas ici.
4. **Doc utilisateur** (site `docs/`) + **captures** autant que nécessaire.
5. **Brief projet** (`brief/`, dans ce dépôt) : répercuter dans le **brief projet** (document de
   conception vivant : besoin, parcours utilisateurs, maquettes, MCD - **pas** un sujet pédagogique) les
   évolutions qui changent un de ces **éléments de conception**. Cela se fait **dans la PR du chantier**,
   comme les deux passes précédentes ; `echonuit/brief` ne porte plus que le site construit.
6. **Tests** : chaque usage couvert par des tests d'**intégration** (TestFX) et **E2E**. L'inventaire
   se fait **depuis le diff** du chantier, pas de mémoire : pour chaque capacité ajoutée, quel test la
   couvre et à quel niveau (les angles morts sont les **chemins non nominaux**, la **parité CLI ↔ IHM**
   et le **cas réel**). Un « aucun test » sorti d'un `grep` n'est qu'une **hypothèse** : l'inventaire
   par motif se trompe dans les **deux sens** (homonymes, tests qui pilotent le service sans porter la
   clé de vue, commande invoquée en kebab-case vs classe instanciée). **Confirmer chaque zéro à la
   main** avant d'en faire une issue. Un E2E vaut par ce qu'il **traverse** : **fusionner** deux scénarios quand le
   défaut probable est **entre** eux. **La recette se prépare, elle ne se remplit pas des restes** :
   **toute capacité ajoutée** a sa case `Sxx-NN` dans sa **session propriétaire**
   (`dev-docs/recette/sessions/`), parce qu'une capacité dont personne ne sait comment la vérifier à la
   main n'est pas finie. Une case est terminée quand elle est **rejouable** par quelqu'un qui n'était
   pas là, et cela tient en trois pièces : le **geste**, l'**observation attendue**, et la **fixture**.
   Si aucune fixture ne porte le cas, **étendre la spec du générateur** fait partie de la passe - une
   donnée bricolée à la main ne revient pas à la campagne suivante. Une case = **un fait observable**,
   jamais un contrôle groupé. Session **partielle ou jamais jouée** (état dans
   `dev-docs/recette/index.md`, seule source) : **le dire en issue**, sinon la capacité est réputée
   vérifiable par un script qui ne la couvre pas. Un **garde-fou de non-régression se vérifie en le voyant rouge** : **PIT ciblé**
   (`-Pmutation`, exhaustif sur une classe et rapide) pour le code Java, **mutation à la main** pour ce
   que PIT ne mute pas (attribut d'annotation, câblage, FXML, sonde réseau). Un test vert n'est qu'une
   **hypothèse** sur ce qu'il couvre. Et **un dispositif n'est pas toujours un test** : la même règle
   vaut pour un **job de CI** ou un **script**, là où le faux vert se voit le moins. Vécu : un job écrit
   pour rejouer la suite sous un autre fuseau passait celui-ci par `-D` sur la ligne Maven, que les forks
   surefire n'héritent pas - il aurait été vert en ne vérifiant rien. **Ce vert existerait-il si c'était
   cassé ?** PIT a normalement déjà tourné pendant l'issue ; cette passe **vérifie que ça a été fait**.
7. **Harmonisation** : prendre du recul sur **l'application entière**, en deux temps. D'abord un
   **audit global**, exhaustif et scrupuleux (qu'est-ce qui **ressemble** au résultat du chantier,
   qu'est-ce qui en **bénéficierait**), pour comprendre ce qui **sous-tend** la demande initiale. Puis
   un **refactoring de conceptualisation** (rendre l'application plus **lisible** et **compréhensible** ;
   réduire la duplication et abstraire (Extract Class, patterns partagés) sont des **outils**, pas le
   but). **Discuter les choix, doutes et conséquences avec l'utilisateur.**
8. **Revue visuelle** : inspecter **toutes les conséquences visibles** du chantier, chaque écran **et
   chaque état** qu'il peut prendre. Les captures sont une **documentation vivante** de l'état réel de
   l'application : **ajouter autant de captures que nécessaire** pour refléter toutes ses
   fonctionnalités visuelles (une capture ajoutée = validation rejouable). Les **régénérer**, les
   **ouvrir une par une**, les **regarder**. Un geste testé n'est pas un écran regardé : un texte
   coupé, un glyphe absent ou une régression de style ne font rougir aucun test. C'est la passe
   précédente (CSS, socle) qui est la plus à même de casser un écran sans casser un test, d'où cette
   relecture **juste après** elle.
9. **Suites consolidées** : l'EPIC des suites relu, regroupé, les rattachements tranchés, et ce que la clôture a elle-même révélé ajouté.
10. **Changement OpenSpec archivé** : les delta specs fusionnent d'abord dans `openspec/specs/`, toutes les
    capacités sont re-comparées, **puis** le dossier passe sous `openspec/changes/archive/`. L'ordre inverse
    laisse le changement archivé et la spécification jamais mise à jour, sans que rien ne le dise.
11. **ADR du chantier écrites** (`dev-docs/decisions/`), pour toute **décision structurante**. Le numéro
    **ne se choisit pas** : c'est celui de l'issue qui porte la décision (le lot, à défaut l'EPIC) ; le
    compteur séquentiel est clos à 0048, voir le [journal](dev-docs/decisions/index.md). Chaque ADR
    **déclare comment elle est vérifiée** (champ `verification: certaine | probable | humaine` de son en-tête OKF,
    [ADR 2465](dev-docs/decisions/2465-une-adr-declare-comment-elle-est-verifiee.md)) : un garde-fou fait
    rougir la CI si elle manque. **Ici et non en passe 3**, parce que les passes 4 à 9 **produisent** des
    décisions : sur le chantier #3151, les cinq ADR sont nées après la passe 3, aucune à l'endroit prévu.
    Une décision **de ne pas faire** est une décision, et c'est celle qu'on oublie : elle ne laisse pas
    de code derrière elle.
12. **Bilan** : ce qui a été livré, dette restante, décisions (qui **renvoient aux ADR** de la passe 11).
    **Et il se montre** : la passe 12 produit un **artefact visuel avant / après**, une ligne par
    conséquence visible, soumis **avant** de clore l'EPIC. Un chantier d'IHM se juge sur ce qu'il change
    à l'écran, et un bilan écrit demande qu'on le croie là où une capture le montre. Ce qui n'a **pas**
    été corrigé y figure aussi : une troncature montrée et assumée vaut mieux qu'une omission.

**Les suites se closent aussi.** Les issues consolidées en passe 9, une fois livrées, forment un
**nouveau delta** : il se clôt par les **mêmes 14 passes**, appliquées à lui seul. Et un bilan est une
**hypothèse** : quand une suite est traitée, relire ce que le bilan précédent en disait et le corriger
s'il s'est trompé.

> 📖 Raison d'être et mode opératoire de chaque passe, avec le **modèle de clôture** à coller dans
> l'EPIC : [doc dev · Cycle de vie d'un chantier](https://companion-dev.echonuit.fr/cycle-de-chantier/).

---

## 6. Intégration continue

Deux workflows se déclenchent à chaque push :

| Workflow | Rôle | Bloquant ? |
|---|---|---|
| [`maven.yml`](.github/workflows/maven.yml) | Build + tests headless **+ couverture** (`./mvnw verify -Djacoco.haltOnFailure=true`, seuils JaCoCo bloquants). | **Oui** |
| [`lint.yml`](.github/workflows/lint.yml) | Statique : **`spotless:check`** (formatage) + complétude des captures + **`test-compile pmd:pmd`**, qui produit le rapport sans juger, puis les **cliquets ADR** qui le jugent - dont celui de l'[ADR 4617](dev-docs/decisions/4617-le-portail-voit-les-tests-et-le-code-mort.md). | **Oui** |

Reproduire les contrôles **en local** (la CI les répartit sur les deux workflows) :

```bash
./mvnw -B test-compile pmd:pmd                  # le rapport PMD, qui ne juge pas (lint.yml)
python3 scripts/adr/4617-code-mort-et-zone-de-test.py   # le verdict, par zone (lint.yml)
./mvnw -B verify -Djacoco.haltOnFailure=true    # tests + couverture + dépendances (maven.yml)
./mvnw spotless:check                           # formatage (lint.yml)
```

Les autres workflows : `capture-vues.yml` (régénère les aperçus de la doc), `docs.yml`
(construit/publie les trois sites de documentation), `api-live.yml` (contrat de l'API Vigie-Chiro,
hebdomadaire et en lecture seule), `release.yml`, `flatpak.yml` et `winget.yml` (publication).

> 📖 Carte complète des workflows et du portail qualité :
> [doc dev · CI/CD et release](https://companion-dev.echonuit.fr/ci-cd-release/).

---

## 7. Publier une version

Les **releases sont automatiques**, pilotées par les Conventional Commits (cf. §3). Sur la branche
`main`, à chaque ensemble de commits mergé :

- `feat:` déclenche une version **mineure**, `fix:` un **patch**, un `BREAKING CHANGE` une **majeure** ;
- **semantic-release** ([.releaserc.json](.releaserc.json)) calcule la version, crée le tag `vX.Y.Z`,
  la Release GitHub et met à jour le [CHANGELOG.md](CHANGELOG.md) ;
- le workflow [`release.yml`](.github/workflows/release.yml) construit alors les **installeurs natifs**
  (Linux `.deb`, macOS `.dmg` Apple Silicon, Windows `.msi`) via le profil `-Pinstaller`, et les
  attache à la Release (rendue publique seulement une fois **tous** les installeurs téléversés).

Vous n'avez donc **rien à taguer ni à versionner à la main** : merger des commits conventionnels suffit.

> **Activation.** Le workflow est **dormant** tant que la variable de dépôt `ENABLE_RELEASE` n'est pas
> à `true`. Première version : `v1.0.0`.

Construire un installeur **en local** (pour tester le packaging) :

```bash
./mvnw -Pinstaller -Djpackage.type=deb -DskipTests verify   # produit target/dist/
```

---

## 8. Dépendances

Les mises à jour sont gérées par **Dependabot** ([.github/dependabot.yml](.github/dependabot.yml)),
mensuellement, pour `maven` et `github-actions`. **JavaFX (`org.openjfx:*`) est volontairement
exclu** de l'automatisation : ses bumps ont un impact fort (rendu, Headless Platform, plugin
communautaire) et se décident à la main.

---

## 9. En cas de doute

- Un comportement du dépôt vous surprend ? Consultez d'abord les commentaires des fichiers cités
  ci-dessus : ils documentent les décisions (souvent le « pourquoi »).
- Une question : [sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr).
