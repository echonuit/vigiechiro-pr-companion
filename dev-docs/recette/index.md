# Recette (campagne d'acceptation)

La **recette** est la revue d'acceptation du logiciel **écran par écran et feature par feature**, menée
avant une livraison : on vérifie que tout est **conforme à la doc publiée** et que les parcours sont
**fluides** pour un utilisateur naturaliste. C'est un [chantier](../cycle-de-chantier.md) à part entière :
elle s'ouvre sur un EPIC, se déroule en sessions, et se **clôt par les passes du cycle**. Sa
particularité : chaque remarque finit en **issue**, et chaque correction bloquante revient avec **son
test**, si bien que la campagne **laisse derrière elle un filet** qui la rejoue.

!!! info "Où vit la campagne : l'EPIC est le tableau de bord"
    Le déroulé **vivant** (sessions cochées, constats, comptes-rendus, task-list des issues) vit dans
    l'**EPIC de recette** sur GitHub (label `recette`). Les comptes-rendus de session y sont postés
    **en commentaires** : *GitHub est la mémoire de la campagne ; cette page en est la méthode.*
    Campagne courante :
    [#1363](https://github.com/echonuit/vigiechiro-pr-companion/issues/1363).

## Le déroulé d'une campagne

L'ordre des sessions suit le **parcours métier réel** (importer, puis vérifier, puis déposer, puis
valider). Règle d'unicité : un écran est déroulé **en entier** dans **une seule** session propriétaire ;
ailleurs il n'est qu'écran de transit (seule la fluidité de la transition est notée). La base de recette
**vit d'une session à l'autre**.

| # | Session (~1 h) | Écrans propriétaires |
|---|---|---|
| S1 | Premier contact (base vierge, connexion, déclarer un site) | accueil (dont le **bandeau de compteurs vivant**, #1376), Connexion, Mes sites, carré, Point |
| S2 | Importer une nuit (carte SD vers le pivot) | importation (+ cas dégradés), Rattachement, passage, diagnostic |
| S3 | Vérifier (pré-check + écoute + verdict) | qualification (+ raccourcis clavier) |
| S4 | Déposer et suivre (lot, dépôt réel, suivi) | lot |
| S5 | Valider (sur une nuit déposée) | [Sons & validation](sessions/s5-valider.md) (partielle : parcours expert #2790), fiche espèce |
| S6 | Exploiter et piloter (vues agrégées) | [Activité de la nuit](sessions/s6-exploiter-piloter.md) (écrite), puis Carte & passages, Espèces & observations, Audit, recherche |
| S7 | Réglages, interrupteurs OFF, états dégradés | [Réglages](sessions/s7-reglages.md) (tous les onglets, contribués par les features) + chrome |
| S8 | Récupérer une nuit déposée (P12 de bout en bout, carte SD réelle) | Connexion, Mes sites, Toutes mes nuits, passage |
| S9 | [Installer et mettre à jour](sessions/s9-installer-mettre-a-jour.md) (winget, machine Windows réelle) | aucun : la session se joue **avant** l'application |

Chaque session se joue en trois temps : une **passe statique** en solo (préparation, postée en
commentaire), la **session interactive** (pilotage écran par écran), puis un **triage à froid**.

## D'où viennent les cases : la passe 6 d'un chantier

Un script ne se remplit pas au moment de jouer la campagne : il se remplit **à la clôture de chaque
chantier**, en [passe 6](../cycle-de-chantier.md). La règle y est posée ainsi :

> Une capacité ajoutée par le chantier n'est pas finie tant qu'on ne sait pas comment la vérifier à la
> main, et que ce « comment » n'est pas écrit là où on le retrouvera.

Une case est terminée quand elle est **rejouable** par quelqu'un qui n'a pas fait le chantier, autant
de fois que nécessaire. Trois pièces, et il en manque une seule pour qu'elle redevienne une intention :
le **geste**, l'**observation attendue**, et la **fixture**. C'est pour cette dernière que les cartes SD
sont **générées** depuis une spec plutôt que stockées : une donnée fabriquée à la main pour une campagne
ne revient pas à la suivante. Quand le cas n'est porté par aucune fixture, **étendre la spec du
générateur fait partie de la passe 6**.

## Les scripts de session

Chaque session a son **script figé**, versionné sous `recette/sessions/`. Un script est à la fois la
**checklist rejouable** (une case = un fait observable, groupée par étape) et le **relevé** de la dernière
passe (verdict par axe, issues produites, renvois, notes de méthode). Passer d'une campagne à l'autre,
c'est re-dérouler la checklist ; ses annotations disent ce qui avait été trouvé la fois d'avant.

Gabarit d'un script : en-tête (écrans propriétaires · features · statut) → objectif → environnement →
[raccourcis] → **le script** (points numérotés `Sxx-NN`, groupés par étape) → **verdict par axe** →
issues produites → renvois et décisions → notes de méthode.

### Trois états, et « couvert » reste réservé à ce que la CI prouve (#3764)

Une case est dans l'une de **trois** situations. Les réduire à deux fabrique un vert creux :

| État | Qui tranche | Comment il se déclare |
|---|---|---|
| **Asserté** | la CI, et elle rougit quand le logiciel a tort | un test porte `@CasDeRecette("S1-02")` |
| **Perceptif** | un humain, en regardant | la case porte la marque `*perceptif*`, posée en passe 6 |
| **Non couvert** | personne | ni l'une, ni l'autre |

⚠️ Le deuxième état n'est **pas** une couverture, et c'est tout l'enjeu. Un scénario qui *joue* un cas
perceptif le cite comme n'importe quel test - c'est le seul lien vers le script - si bien que sans
distinction il gonflerait le compte des couverts d'un cas que **personne n'a regardé**. Un tel
scénario se déclare donc `@CasDeRecette("S1-26", jugement = HUMAIN)`.

**Les deux sources se tiennent l'une l'autre.** Le script dit ce qu'un cas *demande*, le code dit ce
qu'un test *prouve*, et `CorrespondanceRecetteTest` les confronte : il rougit sur un cas marqué
perceptif que du code prétend asserter, comme sur un scénario qui se déclare humain là où le script
n'a rien marqué. Sans ce recoupement, la marque dériverait comme la prose avait dérivé avant #3728 -
le script disait « perceptifs » en toutes lettres, et aucune machine ne le lisait.

- [S1 · Premier contact](sessions/s1-premier-contact.md) : accueil, connexion, sites, points.
- [S2 · Importer une nuit](sessions/s2-importer.md) : importation, passage, diagnostic (+ cas dégradés).
- [S3 · Vérifier](sessions/s3-verifier.md) : qualification, raccourcis, écoute *(à rejouer au delta : écran refondu #1524)*.
- [S4 · Déposer et suivre](sessions/s4-deposer-suivre.md) : lot, dépôt réel, suivi *(à jouer)*.
- [S7 · Réglages](sessions/s7-reglages.md) : les onglets contribués par les features, les interrupteurs de fonctionnalités, le chrome et les états dégradés *(à jouer)*.
- [Passe ciblée · constats en attente](sessions/passe-ciblee-constats-en-attente.md) : les huit
  constats qu'une image fixe ne peut pas juger, tranchés avant d'ouvrir les chantiers voisins
  *(jouée le 2026-08-07)*.
- [Passe de coutures · stabilisation](sessions/passe-verification-stabilisation.md) : les correctifs
  de la stabilisation tiennent-ils **ensemble** ? Un parcours qui traverse quatre écrans, là où chaque
  session n'en déroule qu'un. Condition d'entrée de la campagne 2 *(à jouer)*.

## La fiche d'évaluation : six axes

Chaque écran est noté sur **six axes**, verdict trivalué (**OK / remarque / bloquant**). Les axes **P**
et **D** s'instruisent en **passe statique** (ils ne demandent pas de piloter l'écran) ; la session
interactive ne sert qu'à **C, E, F, R**.

| Axe | Ce qu'on vérifie |
|---|---|
| **C** · Conformité | chaque affirmation de `docs/ecrans/<ecran>.md` est vraie à l'écran |
| **E** · États | vide / occupé / erreur / désactivé **expliqué** (`IndicateurBlocage` + tooltip) |
| **F** · Fluidité | état visible, annulation possible, erreur prévenue, **pas de mémorisation exigée** |
| **R** · Clavier | raccourcis documentés opérants, tab / focus cohérents |
| **P** · Parité CLI | capacités métier de l'écran couvertes par une [commande](../cli.md) |
| **D** · Doc & captures | fiche à jour, captures du manifeste = état **réellement livré** |

!!! note "Une vérification = un fait vérifiable"
    En séance, chaque point est **un seul fait** (`S4-01`, `S4-02`…), jamais un contrôle groupé, et les
    questions se posent **une par une**. C'est ce qui rend un constat traçable jusqu'à son test.

## Ce qui est bloquant

Un constat est **bloquant livraison** si :

- **(a)** il contredit la **doc publiée** ;
- **(b)** il fait courir un risque de **perte ou corruption** de données ;
- **(c)** il crée une **impasse** de parcours sans échappatoire ;
- **(d)** une **désactivation est muette** sur le chemin nominal ;
- **(e)** une **friction ergonomique forte** touche le parcours nominal (geste contre-intuitif, libellé
  trompeur), au jugé pendant le triage.

Tout le reste part en **« v2 produit »** par défaut.

## Le triage de fin de session (trois bacs)

1. **Bloquant livraison** (critères a-e) : **issue de finalisation** immédiate, label `recette`, ajoutée
   à la task-list de l'EPIC. Voix première personne, closing keywords **anglais**.
2. **v2 produit** : issue au **milestone « v2 produit »**, non reliée à l'EPIC. Le parapluie ergonomique
   est #786 ; tout constat ergonomique lui est d'abord confronté.
3. **Non retenu / déjà tracé** : simple **renvoi** dans le compte-rendu de session.

## La boucle : un constat devient un test

C'est le cœur de la recette côté dév, et sa différence avec une simple checklist. Un constat bloquant ne
se contente pas d'être corrigé : il **revient avec son test**, pour ne plus jamais régresser en silence.
Selon l'axe, la couche de cristallisation diffère :

| Axe | Où le constat se cristallise |
|---|---|
| **C**, **E** | test d'intégration de vue (`*VueIntegrationTest`) ou [parcours E2E](../tests-et-qualite.md) (`Parcours*E2ETest`) |
| **R** | test TestFX qui rejoue raccourcis et focus |
| **P** | test de commande CLI en golden : voir [CLI](../cli.md) |
| **D** | [harnais de captures](../captures.md) (`ApercuFx` / `Capture*`) + approbation |
| **F** | **irréductiblement humain** pour la part *perçue* ; les invariants objectivables (annulable, désactivation expliquée) rejoignent C / E |

Le mécanisme s'appuie sur des patrons déjà en place : le **cliquet** (`CliquetFixturePassageTest`), qui
empêche une dette de fixtures de repousser, et *[« la doc est tenue par un test »](../tests-et-qualite.md)*,
qui empêche la doc de dériver. La recette est donc une **fabrique de tests** : la campagne se termine, le
filet reste.

!!! example "En pratique : trois constats, trois filets à trois étages"
    La campagne courante l'a déjà fait plusieurs fois, et chaque fois le test atterrit dans la **couche
    qui convient** :

    - **Un raccourci clavier muet** (axe R). En S3, « Espace » n'ouvrait pas la lecture sur l'écran de
      qualification, et *rien ne le testait* (#1504). La correction est revenue avec son test **TestFX** :
      `QualificationViewTest` vérifie désormais qu'« Espace est capté pour la lecture avant le nœud
      focalisé », donc n'active plus par mégarde un bouton de verdict.
    - **Une régression silencieuse de données** (critère bloquant b). Toujours en S3, une nuit **déposée**
      pouvait **régresser** vers « Vérifié » depuis l'écran de vérification (#1514) : un verdict figé qui
      se défige, c'est un risque d'incohérence. Le filet est posé au **bon étage**, côté service :
      `ServiceQualificationTest` garantit qu'« une nuit déposée refuse tout nouveau verdict ».
    - **Un constat qui devient un patron** (axe R, transverse). « Échap » ne fermait aucune modale
      (#1505). Plutôt qu'un correctif écran par écran, la correction a **extrait un patron commun**
      (`Modales.fermerParEchap`) gardé par `ModalesTest` : un constat de recette a nourri
      l'**harmonisation**.

## Rejouer une campagne de façon déterministe

Pour **revalider** aux jalons suivants sans tout re-piloter à la main, on rejoue le **fond fonctionnel**
*headless* :

- **La CLI est le moteur de rejeu.** `fr.univ_amu.iut.cli` expose le métier en commandes scriptables
  (`importer`, `qualifier`, `deposer`, `auditer`, `reactiver`…). Un scénario qui enchaîne des commandes
  contre une fixture et compare la sortie à un **golden** rejoue un parcours entier sans IHM. Voir
  [CLI](../cli.md).
- **Les parcours E2E** (`Parcours*E2ETest`) sont les **scripts de départ** des sessions : ils pilotent
  les vrais ViewModels et services sur base jetable.
- **Les fixtures** (cartes SD de recette, workspace) sont **générées** depuis une spec de quelques
  kilo-octets plutôt que stockées en binaire : versionnables, rejouables à l'identique. Les 9 cartes de
  recette (+ `sd-nominale.zip`) se reconstruisent à l'identique via le générateur déterministe : voir
  [Fixtures (générateur de cartes SD)](fixtures.md).

!!! danger "Jamais de secret dans le dépôt"
    Le `connexion.json` d'un workspace de recette contient un **token** Vigie-Chiro : il n'est **jamais**
    versionné. Un rejeu qui doit écrire sur la plateforme reçoit son jeton par **variable
    d'environnement ou secret CI**, pas depuis un fichier committé.

## Où ça vit

- `dev-docs/recette/index.md` : **cette page**, la méthode.
- `dev-docs/recette/sessions/` : les **scripts de session** figés. S1 et S2 ont été jouées ; S3 est à
  rejouer au delta ; S4, S7 et S8 sont écrites mais **à jouer** ; S5 et S6 restent **partielles**.
  Depuis #3517, **les huit sessions existent** : aucune n'est plus à écrire. S'y ajoutent les **passes
  ciblées** listées plus haut.

    ⚠️ **Cet inventaire est la seule source.** Il a été recopié ailleurs - dans le cycle de chantier et
    dans `CONTRIBUTING.md` - et les trois copies ont divergé en **quelques heures** : S7 a été écrite
    (#3517) le jour même où les deux autres affirmaient qu'elle n'existait pas. Les renvois pointent
    désormais ici plutôt que de répéter la liste.
- `recette/fixtures/spec/` : les **specs** (YAML) des cartes SD, matérialisées par le générateur
  déterministe ; voir [Fixtures (générateur de cartes SD)](fixtures.md).
- L'**EPIC** (label `recette`) : le déroulé vivant, les comptes-rendus, la task-list des issues.
