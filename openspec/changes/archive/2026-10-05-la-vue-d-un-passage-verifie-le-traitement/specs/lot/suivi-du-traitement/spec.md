## ADDED Requirements

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
