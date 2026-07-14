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

### 4. Passe de doc utilisateur

Documenter le chantier pour les **utilisateurs** dans le site produit (`docs/`), avec **autant de
captures que nécessaire**. Les aperçus sont régénérés en CI : ajouter/mettre à jour les classes
`Capture*` et le manifeste, cf. [Captures d'écran](captures.md). Une fonctionnalité visible sans
capture est une fonctionnalité à moitié livrée.

### 5. Passe de brief SAÉ

L'application est le **companion** de la SAÉ 2.01 : le **brief** (dépôt
[`IUTInfoAix-S201/brief`](https://github.com/IUTInfoAix-S201/brief), sujet distribué aux étudiants)
décrit ce qu'ils construisent et utilisent. Quand un chantier change **ce qui est attendu ou fourni**
(un parcours, une capture du sujet, une contrainte, une feature de référence), répercuter l'évolution
dans le brief pour que le sujet reste aligné avec le socle réellement livré. Un brief qui décrit une
version périmée de l'app induit les étudiants en erreur.

### 6. Passe de tests

Vérifier que **chaque usage** introduit est couvert :

- **tests d'intégration** TestFX (headless) sur les vues et leurs bindings ;
- **tests E2E** (`fr.univ_amu.iut.e2e.*`) sur les parcours complets IHM → ViewModel → service →
  base.

Pièges et conventions dans [Tests et qualité](tests-et-qualite.md). Les frontières d'architecture
sont couvertes automatiquement par `ArchitectureTest`.

### 7. Passe d'harmonisation

Prendre du recul sur l'ensemble du chantier et chercher les concepts à **abstraire** pour réduire
**complexité** et **duplication** : code répété entre features → **contrat/pattern partagé** dans
`commun` ; classe devenue trop grosse → **Extract Class** (le PMD `GodClass` du portail qualité est le
garde-fou, cf. [Tests et qualité](tests-et-qualite.md)). C'est le moment de transformer trois copies
d'un même geste en un mécanisme d'extension.

### 8. Passe de revue visuelle

**Régénérer les captures des écrans touchés, et les ouvrir.** Une par une. Les regarder.

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

**Ce qu'on cherche** (par ordre de fréquence constatée) :

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
règle du dépôt, se répercute dans `CLAUDE.md` / `CONTRIBUTING.md`.

## Modèle de clôture (à coller dans l'EPIC)

```markdown
## Clôture de chantier
- [ ] 1. Audit d'intégration (rebase sur `main`, points d'accroche, régressions)
- [ ] 2. Cohérence CLI ↔ UI (capacités métier exposées des deux côtés, ou « sans objet »)
- [ ] 3. Doc développeur (dev-docs) à jour
- [ ] 4. Doc utilisateur (docs/) + captures
- [ ] 5. Brief SAÉ (IUTInfoAix-S201/brief) répercuté si attendus/fournis changent
- [ ] 6. Tests d'intégration + E2E couvrant chaque usage
- [ ] 7. Harmonisation (abstractions, duplication, GodClass)
- [ ] 8. Revue visuelle : captures des écrans touchés **régénérées et ouvertes**
- [ ] 9. Nouveaux chantiers identifiés + issues créées
- [ ] 10. Bilan (livré / dette / décisions)
```
