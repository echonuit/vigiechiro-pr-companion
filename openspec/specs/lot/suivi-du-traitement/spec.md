# lot/suivi-du-traitement Specification

## Purpose
Savoir où en est l'analyse Tadarida d'une nuit déposée, et recevoir ses observations dès qu'un relevé la
dit terminée, sans changer d'écran ni de commande.

## Requirements

### Requirement: Un relevé qui rend l'analyse terminée importe les observations

Quand un relevé demandé par « Actualiser » rend une analyse terminée, et que la nuit n'a pas encore
d'observations, l'écran de lot SHALL importer les observations de la nuit dans le même geste, sans
confirmation. La carte « Traitement Vigie-Chiro » SHALL dire ce qui a été importé, sous l'état du
traitement, et le bouton « Actualiser » SHALL rester en attente jusqu'à la fin de l'import.

*Vérifié par* : un cas de `TraitementViewModelTest` (le relevé terminé appelle l'import, et son compte
rendu est restitué), et un test d'interface sur l'écran de lot monté avec un suivi et un import bouchons,
qui clique « Actualiser » et lit la carte.

#### Scenario: L'analyse vient de se terminer

- **WHEN** l'observateur clique « Actualiser » et que la plateforme répond « terminée » pour une nuit sans
  observation
- **THEN** les observations sont en base, la carte affiche « Analyse terminée » et, dessous, le compte
  rendu de l'import

#### Scenario: L'analyse est encore en cours

- **WHEN** l'observateur clique « Actualiser » et que la plateforme répond « planifiée » ou « en cours »
- **THEN** rien n'est importé, et la carte ne montre aucune ligne d'import

### Requirement: Une nuit qui a ses observations n'est pas réimportée

Quand un relevé rend une analyse terminée pour une nuit qui a déjà des observations, l'écran de lot SHALL
ne pas appeler l'import. La carte SHALL dire que les observations sont déjà là, et où les remplacer.

*Vérifié par* : un cas de `TraitementViewModelTest` qui constate qu'aucun import n'est appelé, et lit la
phrase de la carte.

#### Scenario: Observations déjà importées

- **WHEN** l'observateur clique « Actualiser » sur une nuit terminée dont les observations sont en base
- **THEN** aucun import ne part, et la carte dit que les observations sont déjà importées

### Requirement: Un import qui échoue se dit sans masquer l'état de l'analyse

Si l'import échoue après un relevé terminé, la carte SHALL garder l'état « Analyse terminée » et SHALL
dire, dessous, que l'import a échoué, avec son motif et le geste qui reste : cliquer de nouveau
« Actualiser », ou passer par « Sons & validation ».

*Vérifié par* : un cas de `TraitementViewModelTest` avec un import qui lève, qui lit les deux textes.

#### Scenario: La plateforme refuse l'import

- **WHEN** le relevé rend « terminée » et que l'import lève un refus avec un motif
- **THEN** la carte affiche toujours « Analyse terminée », et une ligne d'échec qui cite le motif

### Requirement: Rien ne s'importe sans un relevé demandé

L'ouverture de l'écran de lot SHALL NOT déclencher d'import, y compris quand le dernier état connu est
« terminée ». L'application SHALL NOT sonder la plateforme.

*Vérifié par* : un cas de `TraitementViewModelTest` qui charge un dernier relevé « terminée » et constate
qu'aucun import n'est appelé. Le scénario filmé S4-47 (`ScenarioConnecteLancementTest`) tient
par ailleurs que la carte ne montre que l'état planifié après un lancement.

#### Scenario: Réouverture sur une analyse terminée

- **WHEN** l'écran s'ouvre sur un passage dont le dernier relevé enregistré est « terminée »
- **THEN** la carte affiche cet état depuis le cache, et aucun import ne part

### Requirement: La ligne de commande importe sur demande

`etat-traitement-vigiechiro --importer` SHALL importer les observations quand le relevé rend une analyse
terminée et que la nuit n'en a pas, et SHALL dire ce qui a été importé. Sans `--importer`, la commande
SHALL ne rien écrire dans le dossier de travail et SHALL rester dispensée du verrou d'exclusivité. Avec
`--importer`, elle SHALL prendre ce verrou. Les codes de retour de l'état SHALL rester ceux d'aujourd'hui ;
un import demandé qui échoue SHALL rendre `2`.

*Vérifié par* : `EtatTraitementVigieChiroTest`, cinq cas (sans l'option rien n'est importé ; avec
l'option sur une analyse terminée l'import part ; sur une nuit déjà importée il ne part pas ; un import
qui échoue rend `2`), et un cas de `CliVerrouWorkspaceTest` qui tient le verrou dans les deux sens.

#### Scenario: Sans l'option

- **WHEN** on lance `etat-traitement-vigiechiro --passage 42` sur une analyse terminée
- **THEN** la commande rend `0`, n'importe rien, et renvoie à `--importer`

#### Scenario: Avec l'option, analyse terminée

- **WHEN** on lance `etat-traitement-vigiechiro --passage 42 --importer` sur une analyse terminée sans
  observation
- **THEN** les observations sont importées, la sortie le dit, et la commande rend `0`

#### Scenario: Avec l'option, analyse en cours

- **WHEN** on lance la commande avec `--importer` sur une analyse en cours
- **THEN** rien n'est importé, et la commande rend `3` comme sans l'option

### Requirement: La vue d'un passage vérifie le traitement et importe les observations

La vue d'un passage SHALL offrir un bouton « Vérifier le traitement », à côté de « Voir la
participation ». Un clic SHALL relever l'état de l'analyse auprès de la plateforme et, quand il la dit
terminée pour une nuit sans observation, importer les observations dans le même geste, sans confirmation.
Le résultat SHALL s'afficher dans le bandeau de retour de la vue.

*Vérifié par* : `VerificationDuTraitementTest`, un cas par issue du relevé, et
`PassageActionsFicheViewTest#verifier_le_traitement_dit_ou_en_est_l_analyse`, qui clique le bouton sur
la vue montée avec un suivi bouchon et lit le bandeau.

#### Scenario: L'analyse est terminée et la nuit n'a pas ses observations

- **WHEN** l'observateur clique « Vérifier le traitement » et que la plateforme répond « terminée »
- **THEN** les observations sont importées, et le bandeau dit que l'analyse est terminée, puis ce qui a
  été importé

#### Scenario: L'analyse est encore en cours

- **WHEN** l'observateur clique « Vérifier le traitement » et que la plateforme répond « planifiée » ou
  « en cours »
- **THEN** rien n'est importé, et le bandeau dit où en est l'analyse

#### Scenario: La nuit a déjà ses observations

- **WHEN** le relevé rend « terminée » pour une nuit dont les observations sont en base
- **THEN** aucun import ne part, et le bandeau dit que les observations sont déjà importées

#### Scenario: Le relevé ou l'import échoue

- **WHEN** la plateforme ne peut pas être lue, ou que l'import lève un refus
- **THEN** le bandeau le dit avec sa cause, et l'état d'une analyse terminée reste dit quand seul
  l'import a échoué

### Requirement: Les deux écrans disent la même chose du même relevé

Pour un même relevé et une même issue d'import, la vue d'un passage et l'écran de lot SHALL afficher les
mêmes phrases. Ces phrases SHALL n'être écrites qu'à un endroit. Seul le nom du bouton à recliquer après
un import en échec SHALL différer, chaque écran nommant le sien.

*Vérifié par* : `MemesMotsQueLEcranDeLotTest`, qui confronte, pour chaque état du traitement et chaque
issue d'import, le texte du bandeau de la vue du passage aux textes de la carte « Traitement
Vigie-Chiro ». Il ne recopie aucune phrase.

#### Scenario: Une analyse en cours, lue des deux côtés

- **WHEN** le même relevé « en cours » est restitué à l'écran de lot et à la vue du passage
- **THEN** le bandeau de la vue contient mot pour mot la phrase d'état de la carte

#### Scenario: Un import en échec, lu des deux côtés

- **WHEN** le même relevé « terminée » voit son import échouer sur les deux écrans
- **THEN** la phrase est la même, l'écran de lot invitant à recliquer « Actualiser » et la vue du passage
  « Vérifier le traitement »

### Requirement: Sans participation ou hors connexion, le bouton reste et dit pourquoi

Quand le passage n'est lié à aucune participation, ou que l'application n'est pas connectée, le bouton
« Vérifier le traitement » SHALL rester visible, SHALL être grisé, et SHALL dire dans son infobulle ce qui
manque. L'ouverture de la vue SHALL NOT interroger la plateforme.

*Vérifié par* : `VerificationDuTraitementUITest`, un cas pour chacun des deux, qui lit l'état du bouton et
son infobulle, et un troisième qui constate qu'aucun relevé ne part sans clic.

#### Scenario: Application non connectée

- **WHEN** la vue s'ouvre sur un passage lié à une participation, hors connexion
- **THEN** le bouton est visible et grisé, et son infobulle dit que l'application n'est pas connectée

#### Scenario: Passage sans participation

- **WHEN** la vue s'ouvre sur un passage qui n'a pas été déposé par l'application
- **THEN** le bouton est visible et grisé, et son infobulle dit qu'aucune participation n'est liée
