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
- doc-comments **Markdown** `///` (JEP 467), pas de `/** */` HTML ;
- **noms de classes en français**, sans accents dans les identifiants (`Navigateur`, `Passage`,
  `EtapeNavigation`...) ;
- **pas de tiret cadratin** dans la doc et les commentaires : tiret simple ou deux-points.

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

## 4. Workflow de contribution

```bash
# 1. Forkez le dépôt, puis :
git clone https://github.com/<vous>/vigiechiro-pr-companion.git
cd vigiechiro-pr-companion
git checkout -b feat/<feature>

# 2. ... code + tests ...
./mvnw verify                      # 0 échec, 0 skip indésirable
git commit ...                     # le hook formate le code

# 3. Poussez et ouvrez une PR vers la branche par défaut
git push -u origin feat/<feature>
gh pr create --fill
```

- La PR cible la **branche par défaut** du dépôt (`gh pr create --fill` la sélectionne
  automatiquement). `@nedseb` est ajouté en reviewer automatiquement ([CODEOWNERS](.github/CODEOWNERS)).
- Privilégier des **PR petites et séquentielles** (par exemple : ViewModel + tests, puis vue
  principale, puis vue secondaire) plutôt qu'une feature entière en un seul gros diff.
- Merger quand la CI est verte.
- Avant tout commit, vérifiez l'identité git : `git config user.email`.

---

## 5. Cycle de vie d'un chantier

Un **chantier** est une évolution d'ampleur **EPIC**, répartie sur **plusieurs PR** (le §4 décrit
_une_ PR ; ici on décrit l'ensemble). Il **s'ouvre** par une analyse et **se clôt** par 10 passes.

**À l'ouverture**, dans cet ordre : **trier et regrouper les issues existantes** (balayage **par
concept** et non par mot-clé, recherche des EPIC vivants et des issues « différées de #N », décision des
rattachements, recadrage des titres et des corps déplacés) ; puis cartographier l'existant (réutiliser
les patterns en place plutôt que réinventer) ; puis rédiger un plan ; puis découper en **issues reliées
à un EPIC**.

Le triage vient **en premier** parce qu'une issue est rattachée au chantier qui a remarqué son
**symptôme**, pas à celui qui traite sa **cause** : deux issues sur le même sujet, écrites depuis deux
angles, ne se ressemblent pas, et le recoupement se découvre alors au **conflit de fusion**.

**Au commencement de chaque issue**, avant la première ligne de code : dire **ce qu'il y a à faire**,
**pourquoi maintenant**, et **dans quelle continuité** ça s'inscrit. Le troisième est celui qu'on saute,
et le seul qui ne se retrouve pas après coup.

Ces trois phrases se déposent **en commentaire sur l'issue**, avec le **chantier**, la **branche** et le
**remède envisagé** — et l'issue s'**assigne**. L'assignee est le signal qui se filtre
(`gh issue list --assignee "*"`) ; le commentaire porte ce qu'il ne dit pas. Annoncer le remède est le
vrai gain : deux personnes peuvent voir le même défaut et imaginer deux corrections dont l'une est
meilleure ; annoncées, le désaccord se règle **avant** le code.

**Un signalement se relâche** : quand on s'arrête, on retire l'assignee et on le dit. Une revendication
oubliée est pire que rien — elle fait passer une issue libre pour prise. Elle répond à « cette issue
est-elle prise ? », **pas** à « est-ce la même que celle-là sous d'autres mots ? » : cette seconde
question reste le travail du triage.

**À la clôture** (dans l'ordre) :

1. **Audit d'intégration** : vérifier que les évolutions de `main` survenues pendant le chantier n'ont
   rien laissé à rajouter (rebase, nouveaux points d'accroche à câbler, régressions).
2. **Cohérence CLI ↔ UI** : quand le chantier ajoute/change une **capacité métier**, la **CLI**
   (`fr.univ_amu.iut.cli`) doit exposer l'équivalent (même comportement) ; aligner si petit, sinon
   ouvrir une issue. « Sans objet » si le chantier est purement présentationnel.
3. **Doc développeur** (site `dev-docs/`) à jour **+ une ADR** (`dev-docs/decisions/`) pour toute **décision structurante** prise pendant le chantier. Son numéro **ne se choisit pas** : c'est celui de l'issue qui porte la décision (le lot, à défaut l'EPIC). Le compteur séquentiel est clos à 0048, voir le [journal](dev-docs/decisions/index.md). Chaque ADR **déclare comment elle est vérifiée** (puce `**Vérification** : certaine | probable | humaine`, [ADR 2465](dev-docs/decisions/2465-une-adr-declare-comment-elle-est-verifiee.md)) : un garde-fou fait rougir la CI si elle manque.
4. **Doc utilisateur** (site `docs/`) + **captures** autant que nécessaire.
5. **Brief projet** (`brief/`, dans ce dépôt) : répercuter dans le **brief projet** (document de
   conception vivant : besoin, parcours utilisateurs, maquettes, MCD - **pas** un sujet pédagogique) les
   évolutions qui changent un de ces **éléments de conception**. Cela se fait **dans la PR du chantier**,
   comme les deux passes précédentes ; `echonuit/brief` ne porte plus que le site construit.
6. **Tests** : chaque usage couvert par des tests d'**intégration** (TestFX) et **E2E**. L'inventaire
   se fait **depuis le diff** du chantier, pas de mémoire : pour chaque capacité ajoutée, quel test la
   couvre et à quel niveau (les angles morts sont les **chemins non nominaux**, la **parité CLI ↔ IHM**
   et le **cas réel**). ⚠️ Un « aucun test » sorti d'un `grep` n'est qu'une **hypothèse** : l'inventaire
   par motif se trompe dans les **deux sens** (homonymes, tests qui pilotent le service sans porter la
   clé de vue, commande invoquée en kebab-case vs classe instanciée). **Confirmer chaque zéro à la
   main** avant d'en faire une issue. Un E2E vaut par ce qu'il **traverse** : **fusionner** deux scénarios quand le
   défaut probable est **entre** eux. Ce qui n'est pas automatisable part en **recette**
   (`dev-docs/recette/sessions/`, une case = un fait observable), sinon « pas automatisable » devient
   « pas vérifié ». ⚠️ Un **garde-fou de non-régression se vérifie en le voyant rouge** : **PIT ciblé**
   (`-Pmutation`, exhaustif sur une classe et rapide) pour le code Java, **mutation à la main** pour ce
   que PIT ne mute pas (attribut d'annotation, câblage, FXML, sonde réseau). Un test vert n'est qu'une
   **hypothèse** sur ce qu'il couvre.
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
9. **Nouveaux chantiers** identifiés + **issues** créées.
10. **Bilan** : ce qui a été livré, dette restante, décisions (qui **renvoient aux ADR** de la passe 3).
    **Et il se montre** : la passe 10 produit un **artefact visuel avant / après**, une ligne par
    conséquence visible, soumis **avant** de clore l'EPIC. Un chantier d'IHM se juge sur ce qu'il change
    à l'écran, et un bilan écrit demande qu'on le croie là où une capture le montre. Ce qui n'a **pas**
    été corrigé y figure aussi : une troncature montrée et assumée vaut mieux qu'une omission.

**Les suites se closent aussi.** Les issues créées en passe 9, une fois livrées, forment un **nouveau
delta** : il se clôt par les **mêmes 10 passes**, appliquées à lui seul. Et un bilan est une
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
| [`lint.yml`](.github/workflows/lint.yml) | Statique : **`spotless:check`** (formatage) + complétude des captures + **`-Pquality-gate compile pmd:check`** (PMD bloquant). | **Oui** |

Reproduire les contrôles **en local** (la CI les répartit sur les deux workflows) :

```bash
./mvnw -Pquality-gate compile pmd:check         # PMD bloquant (lint.yml)
./mvnw -B verify -Djacoco.haltOnFailure=true    # tests + couverture bloquante (maven.yml)
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
