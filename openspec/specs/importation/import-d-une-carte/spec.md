## Purpose

Dire à l'observateur, à l'import d'une carte SD, ce qui concerne vraiment les nuits présentes sur la
carte : celles déjà importées ou récupérées de Vigie-Chiro, et la concordance du journal avec ses
enregistrements, sans jamais juger une nuit que seul le journal circulaire cite encore.

## Requirements

### Requirement: Les nuits jugées sont celles de la carte

Le système SHALL déterminer les nuits d'une carte d'après les horodatages de ses enregistrements (la
table des nuits), et SHALL n'utiliser le journal de l'enregistreur que pour son numéro de série. Une
nuit que le journal cite sans enregistrement présent sur la carte SHALL n'être jamais jugée.

*Vérifié par* : `InspectionImportViewModelTest#une_nuit_absente_de_la_carte_n_est_pas_signalee` et
`#une_nuit_presente_deja_importee_est_nommee`, sur une carte dont le journal commence par une nuit
effacée (19 août) suivie de trois nuits présentes (22 à 24 août). Le premier était rouge avant #5600.
`#le_rafraichissement_voit_une_nuit_importee_depuis_l_inspection` tient le même jugement quand la base
change entre l'inspection et l'import.

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
décochée SHALL ne jamais déclencher la question. Pour plusieurs nuits, le constat et la question SHALL
être au pluriel.

*Vérifié par* : `InspectionImportViewModelTest#une_nuit_absente_de_la_carte_n_est_pas_signalee`
(question vide sur la carte réutilisée), `#une_nuit_decochee_ne_declenche_pas_la_confirmation` et
`#deux_nuits_deja_importees_sont_nommees_avec_leur_date` ; le pluriel par
`AvertissementsInspectionTest#question_de_confirmation_pour_deux_nuits` (#5639), et l'état se voit
sur `apercu-import-doublon-multi-nuits.png`.

#### Scenario: La nuit déjà importée est décochée

- **WHEN** la seule nuit déjà importée de la carte est décochée, et que l'observateur lance l'import
- **THEN** aucune confirmation n'est demandée

#### Scenario: Plusieurs nuits cochées déjà importées

- **WHEN** deux nuits cochées ont déjà été importées
- **THEN** la confirmation nomme les deux, chacune avec sa date et son passage, et demande s'il faut
  les importer quand même comme nouveaux passages

### Requirement: Une nuit récupérée n'est reconnue que si on l'importe

Le contrôle du numéro de passage SHALL ne renvoyer vers la réactivation d'une nuit récupérée de
Vigie-Chiro que si cette nuit est parmi les nuits cochées de la carte.

*Vérifié par* : `ImportationViewModelTest#une_nuit_recuperee_absente_de_la_carte_n_est_pas_reconnue`
(rouge avant #5600) ; le cas #2580 existant, où la nuit récupérée est sur la carte, reste vert.

#### Scenario: Nuit récupérée absente de la carte

- **WHEN** une nuit récupérée de Vigie-Chiro porte la date de la première ligne du journal, mais
  n'a aucun enregistrement sur la carte
- **THEN** le contrôle ne la reconnaît pas et n'empêche pas l'import

### Requirement: Le journal est cohérent s'il raconte les nuits de la carte

Le système SHALL juger la date du journal incohérente avec les enregistrements seulement si une date
des enregistrements ne tombe dans la nuit (soir `J`, matin `J + 1`) d'**aucune** des nuits que le
journal raconte, et SHALL ne se replier sur la première ligne du journal que faute de cycle lisible.
Un journal qui raconte d'autres nuits en plus de celles de la carte SHALL être jugé cohérent. Sur une
carte dont les enregistrements s'étalent sur plus d'une nuit, le journal circulaire ne racontant
parfois que les premières, la date SHALL être jugée incohérente seulement si **aucune** nuit de la
carte n'est racontée, et SHALL n'être pas jugée sans cycle lisible. Quand la date est jugée
incohérente, le détail affiché SHALL nommer les nuits jugées, et non la seule première ligne du
journal.

*Vérifié par* : `AnalyseCoherenceTest#carte_reutilisee_a_une_nuit_n_est_pas_incoherente`, avec un vrai
journal circulaire lu par l'analyseur (rouge avant #5631), et son contrôle négatif
`#un_journal_etranger_reste_incoherent` ; pour une carte de plusieurs nuits,
`#carte_multi_nuits_et_journal_etranger_incoherente` (rouge avant #5669),
`#carte_multi_nuits_journal_circulaire_coherente` et `#carte_multi_nuits_derniere_nuit_racontee_coherente` ;
`GenerationCartesSDCliquetTest` sur les cartes `sd-carte-reutilisee` et `sd-journal-etranger-multi` ;
le détail par `AvertissementsInspectionTest#incoherence_date_nomme_les_nuits_racontees` (#5653).

#### Scenario: Carte réutilisée à une seule nuit

- **WHEN** le journal raconte la nuit du 19 août puis celle du 22, et que la carte ne porte que les
  enregistrements du 22
- **THEN** aucune incohérence de date n'est signalée

#### Scenario: Journal étranger à la carte

- **WHEN** le journal ne raconte que la nuit du 1er avril, et que la carte porte des enregistrements
  du 22 août
- **THEN** l'incohérence de date est signalée, et le détail nomme la nuit du 1er avril que le journal
  raconte

#### Scenario: Carte de plusieurs nuits dont le journal a perdu les dernières

- **WHEN** la carte porte les nuits du 3, du 4 et du 5 juillet, et que le journal ne raconte que celle
  du 3
- **THEN** aucune incohérence de date n'est signalée

#### Scenario: Carte de plusieurs nuits sous un journal étranger

- **WHEN** la carte porte les nuits du 3, du 4 et du 5 juillet, et que le journal ne raconte que les
  nuits du 19 et du 22 août
- **THEN** l'incohérence de date est signalée, et le détail nomme les nuits du 19 et du 22 août

### Requirement: La date d'une nuit se lit en français dans la table

Sur une carte de plusieurs nuits, la colonne « Nuit du » de la table des nuits SHALL afficher la date de
chaque nuit sous la forme « 03/07/2026 », celle que l'avertissement et la confirmation du même écran
emploient. Elle MUST NOT afficher la forme ISO. La colonne SHALL se trier dans l'ordre des dates.

*Vérifié par* : un test de la table qui lit le texte dessiné dans la cellule, et le scénario multi-nuits
qui retrouve une ligne par sa date affichée.

#### Scenario: Trois nuits de juillet

- **WHEN** l'inspection détecte les nuits des 3, 4 et 5 juillet 2026
- **THEN** la colonne « Nuit du » affiche « 03/07/2026 », « 04/07/2026 » et « 05/07/2026 »
