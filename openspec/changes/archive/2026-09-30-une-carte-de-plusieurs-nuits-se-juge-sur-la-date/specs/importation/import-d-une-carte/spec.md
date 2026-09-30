## MODIFIED Requirements

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
