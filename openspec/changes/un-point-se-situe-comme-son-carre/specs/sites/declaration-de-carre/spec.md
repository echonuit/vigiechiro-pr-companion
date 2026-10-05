## MODIFIED Requirements

### Requirement: Les formats de position acceptés, et ceux qui sont refusés

L'écran SHALL accepter une position en **degrés décimaux**, en **degrés-minutes-secondes** et en
**degrés-minutes décimales**, dans l'ordre **latitude puis longitude**. Un décimal suivi de son point
cardinal (`43.401 N`) SHALL se lire comme le décimal signé correspondant.

L'écran MUST refuser un texte qu'il ne sait pas lire, et son refus MUST dire quoi coller à la place.
Il ne devine ni l'ordre des deux nombres, ni une position à partir d'une URL de carte. Une paire
écrite avec la virgule décimale française MUST être refusée, et son refus MUST dire d'écrire le point
décimal : la virgule y est aussi le séparateur des deux nombres.

La même règle lit la position du site et celle d'un point d'écoute.

**Vérifié par** : `PositionColleeTest`, six cas sur l'analyseur pur -
`degres_decimaux_latitude_puis_longitude`, `degres_minutes_secondes_valent_leur_equivalent_decimal`,
`sud_et_ouest_comptent_negativement`, `ouest_s_ecrit_aussi_en_francais`,
`url_de_carte_refuse_avec_son_propre_motif`, `texte_illisible_refuse_en_disant_quoi_coller`. Trois autres
cas tiennent les degrés-minutes décimales, le cardinal après un décimal, et la virgule décimale refusée
avec son motif.

#### Scenario: Position en degrés décimaux

- **WHEN** l'observateur colle « 43.296482, 5.369780 »
- **THEN** la position est lue comme latitude 43.296482 et longitude 5.369780

#### Scenario: Position en degrés-minutes-secondes

- **WHEN** l'observateur colle une position écrite en degrés, minutes et secondes avec ses points
  cardinaux
- **THEN** elle est lue comme la même position que son équivalent décimal

#### Scenario: Position en degrés-minutes décimales

- **WHEN** l'observateur colle « 43°24.06'N 5°26.85'E », la forme des récepteurs GPS de terrain
- **THEN** elle est lue comme la même position que son équivalent décimal

#### Scenario: Un décimal suivi de son cardinal

- **WHEN** l'observateur colle « 43.401 N, 1.574 W »
- **THEN** la position est lue comme latitude 43.401 et longitude -1.574

#### Scenario: Une virgule décimale est refusée, et le refus dit quoi écrire

- **WHEN** l'observateur colle « 43,401, 5,447 »
- **THEN** aucune position n'est lue
- **AND** le motif affiché dit d'écrire le point décimal

#### Scenario: Une URL de carte est refusée, et le refus dit quoi coller

- **WHEN** l'observateur colle une URL Google Maps ou OpenStreetMap
- **THEN** aucune position n'est lue
- **AND** le motif affiché nomme le format attendu, deux nombres séparés par une virgule

#### Scenario: Un texte illisible ne remplit rien

- **WHEN** l'observateur colle un texte qui ne porte pas deux nombres lisibles
- **THEN** aucune position n'est lue, et le champ du numéro de carré n'est pas touché
