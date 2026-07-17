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
    [#1363](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1363).

## Le déroulé d'une campagne

L'ordre des sessions suit le **parcours métier réel** (importer, puis vérifier, puis déposer, puis
valider). Règle d'unicité : un écran est déroulé **en entier** dans **une seule** session propriétaire ;
ailleurs il n'est qu'écran de transit (seule la fluidité de la transition est notée). La base de recette
**vit d'une session à l'autre**.

| # | Session (~1 h) | Écrans propriétaires |
|---|---|---|
| S1 | Premier contact (base vierge, connexion, déclarer un site) | accueil, Connexion, Mes sites, carré, Point |
| S2 | Importer une nuit (carte SD vers le pivot) | importation (+ cas dégradés), Rattachement, passage, diagnostic |
| S3 | Vérifier (pré-check + écoute + verdict) | qualification (+ raccourcis clavier) |
| S4 | Déposer et suivre (lot, dépôt réel, suivi) | lot |
| S5 | Valider (sur une nuit déposée) | Sons & validation, fiche espèce |
| S6 | Exploiter et piloter (vues agrégées) | Carte & passages, Espèces & observations, Audit, recherche |
| S7 | Réglages, interrupteurs OFF, états dégradés | reglages (5 onglets) + chrome |

Chaque session se joue en trois temps : une **passe statique** en solo (préparation, postée en
commentaire), la **session interactive** (pilotage écran par écran), puis un **triage à froid**.

## Les scripts de session

Chaque session a son **script figé**, versionné sous `recette/sessions/`. Un script est à la fois la
**checklist rejouable** (une case = un fait observable, groupée par étape) et le **relevé** de la dernière
passe (verdict par axe, issues produites, renvois, notes de méthode). Passer d'une campagne à l'autre,
c'est re-dérouler la checklist ; ses annotations disent ce qui avait été trouvé la fois d'avant.

Gabarit d'un script : en-tête (écrans propriétaires · features · statut) → objectif → environnement →
[raccourcis] → **le script** (points numérotés `Sxx-NN`, groupés par étape) → **verdict par axe** →
issues produites → renvois et décisions → notes de méthode.

- [S1 · Premier contact](sessions/s1-premier-contact.md) : accueil, connexion, sites, points.
- [S2 · Importer une nuit](sessions/s2-importer.md) : importation, passage, diagnostic (+ cas dégradés).
- [S3 · Vérifier](sessions/s3-verifier.md) : qualification, raccourcis, écoute.
- [S4 · Déposer et suivre](sessions/s4-deposer-suivre.md) : lot, dépôt réel, suivi *(à jouer)*.

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
- **Les fixtures** (cartes SD de recette, workspace) ont vocation à être **générées** depuis une spec de
  quelques kilo-octets plutôt que stockées en binaire : versionnables, rejouables à l'identique.
  *(Générateur à venir ; les cartes SD faites main servent de référence.)*

!!! danger "Jamais de secret dans le dépôt"
    Le `connexion.json` d'un workspace de recette contient un **token** Vigie-Chiro : il n'est **jamais**
    versionné. Un rejeu qui doit écrire sur la plateforme reçoit son jeton par **variable
    d'environnement ou secret CI**, pas depuis un fichier committé.

## Où ça vit

- `dev-docs/recette/index.md` : **cette page**, la méthode.
- `dev-docs/recette/sessions/` : les **scripts de session** figés (S1-S4 ; S5-S7 à venir).
- L'**EPIC** (label `recette`) : le déroulé vivant, les comptes-rendus, la task-list des issues.
- *(À venir : `recette/fixtures/spec/` pour les specs du générateur de cartes SD.)*
