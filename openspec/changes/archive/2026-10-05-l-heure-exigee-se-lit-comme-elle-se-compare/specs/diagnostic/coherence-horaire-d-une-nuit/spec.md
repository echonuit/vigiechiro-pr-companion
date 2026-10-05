## MODIFIED Requirements

### Requirement: Les deux plages sont montrées

Le système SHALL montrer la plage **exigée** par le protocole et la plage **effectivement
enregistrée**, afin que l'observateur voie ce qui est attendu et ce qu'il a obtenu plutôt qu'un seul
verdict. La plage exigée SHALL s'afficher à la minute du côté qui respecte le plancher : son début
arrondi à la minute **inférieure**, sa fin à la minute **supérieure**, de sorte qu'un enregistrement
programmé sur les heures affichées tienne toujours la fenêtre. La comparaison, elle, SHALL rester à la
seconde.

*Vérifié par* : capture de l'écran de diagnostic dans son état d'avertissement, et cas de la
commande `diagnostiquer` ; l'arrondi par `PariteCoherenceHoraireTest#programmer_la_fin_affichee_tient_la_fenetre`
(rouge avant #5601), qui rejoue l'analyse avec un arrêt à l'heure affichée par l'écran et par le
terminal.

#### Scenario: Les deux plages à l'écran

- **WHEN** l'observateur ouvre le diagnostic d'une nuit dont les coordonnées et les horaires sont
  connus
- **THEN** il lit la plage exigée et la plage enregistrée, quel que soit le niveau rendu

#### Scenario: Programmer l'heure affichée tient la fenêtre

- **WHEN** la fin exigée tombe à 07:31:40, et que l'observateur programme son enregistreur sur l'heure
  de fin affichée
- **THEN** l'écran et le terminal affichent 07:32, et un arrêt à 07:32 tient la fin exigée
