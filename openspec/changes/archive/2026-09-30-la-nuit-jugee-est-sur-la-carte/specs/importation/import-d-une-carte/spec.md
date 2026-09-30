## Purpose

Dire à l'observateur, à l'import d'une carte SD, ce qui concerne vraiment les nuits présentes sur la
carte : celles déjà importées ou récupérées de Vigie-Chiro, et la concordance du journal avec ses
enregistrements, sans jamais juger une nuit que seul le journal circulaire cite encore.

## ADDED Requirements

### Requirement: Les nuits jugées sont celles de la carte

Le système SHALL déterminer les nuits d'une carte d'après les horodatages de ses enregistrements (la
table des nuits), et SHALL n'utiliser le journal de l'enregistreur que pour son numéro de série. Une
nuit que le journal cite sans enregistrement présent sur la carte SHALL n'être jamais jugée.

*Vérifié par* : `InspectionImportViewModelTest`, sur une carte dont le journal commence par une nuit
effacée (19 août) suivie de trois nuits présentes (22 à 24 août). Écrit, rouge avant.

#### Scenario: Seule la nuit effacée a été importée

- **WHEN** le journal commence par une nuit déjà importée dont les enregistrements ne sont plus sur la
  carte, et qu'aucune nuit présente n'a été importée
- **THEN** l'inspection n'affiche aucun avertissement « déjà importée »

#### Scenario: Une nuit présente a été importée

- **WHEN** l'une des nuits présentes sur la carte a déjà été importée
- **THEN** l'inspection affiche l'avertissement et nomme le passage existant de cette nuit

### Requirement: La confirmation porte sur les nuits qu'on importe

Au lancement de l'import, le système SHALL demander confirmation seulement si une nuit **cochée** dans
la table a déjà été importée, et SHALL nommer, pour chacune, sa date et le passage existant. Une nuit
décochée SHALL ne jamais déclencher la question.

*Vérifié par* : `InspectionImportViewModelTest` (question vide sur la carte réutilisée, écrit, rouge
avant) et un cas où la seule nuit déjà importée est décochée. À écrire.

#### Scenario: La nuit déjà importée est décochée

- **WHEN** la seule nuit déjà importée de la carte est décochée, et que l'observateur lance l'import
- **THEN** aucune confirmation n'est demandée

#### Scenario: Plusieurs nuits cochées déjà importées

- **WHEN** deux nuits cochées ont déjà été importées
- **THEN** la confirmation nomme les deux, chacune avec sa date et son passage

### Requirement: Une nuit récupérée n'est reconnue que si on l'importe

Le contrôle du numéro de passage SHALL ne renvoyer vers la réactivation d'une nuit récupérée de
Vigie-Chiro que si cette nuit est parmi les nuits cochées de la carte.

*Vérifié par* : `ImportationViewModelTest` (nuit récupérée absente de la carte, écrit, rouge avant) ;
le cas #2580 existant, où la nuit récupérée est sur la carte, reste vert.

#### Scenario: Nuit récupérée absente de la carte

- **WHEN** une nuit récupérée de Vigie-Chiro porte la date de la première ligne du journal, mais
  n'a aucun enregistrement sur la carte
- **THEN** le contrôle ne la reconnaît pas et n'empêche pas l'import

### Requirement: Le journal est cohérent s'il raconte les nuits de la carte

Le système SHALL juger la date du journal incohérente avec les enregistrements seulement si **aucune**
nuit racontée par le journal ne correspond à une nuit des enregistrements. Un journal qui raconte
d'autres nuits en plus de celles de la carte SHALL être jugé cohérent.

*Vérifié par* : `AnalyseCoherenceTest`, avec un vrai journal circulaire lu par l'analyseur (écrit,
rouge avant) et son contrôle négatif, un journal étranger qui reste incohérent (écrit, vert avant et
après).

#### Scenario: Carte réutilisée à une seule nuit

- **WHEN** le journal raconte la nuit du 19 août puis celle du 22, et que la carte ne porte que les
  enregistrements du 22
- **THEN** aucune incohérence de date n'est signalée

#### Scenario: Journal étranger à la carte

- **WHEN** le journal ne raconte que la nuit du 1er avril, et que la carte porte des enregistrements
  du 22 août
- **THEN** l'incohérence de date est signalée
