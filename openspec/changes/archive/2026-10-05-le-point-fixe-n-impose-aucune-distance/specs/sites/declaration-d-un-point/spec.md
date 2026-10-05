## ADDED Requirements

### Requirement: La distance entre deux points s'affiche sans se juger

Sur la fiche d'un site, la carte d'un point géolocalisé SHALL afficher sa distance au point géolocalisé
le plus proche du même site, quand il en existe un. Elle MUST NOT qualifier cette distance : ni
avertissement, ni icône de sévérité, ni mention d'une règle de protocole, quelle que soit sa valeur. Le
protocole Point Fixe n'impose aucune distance minimale entre deux points ; le seul voisinage que
l'application signale est celui de la création, à 40 m.

*Vérifié par* : un test du libellé (une distance de 100 m rend la phrase nue, sans le mot « protocole »),
un test d'interface sur une fiche dont deux points sont à cent mètres l'un de l'autre, qui lit la
distance et ne trouve aucune étiquette d'alerte. Remettre un seuil doit les faire rougir.

#### Scenario: Deux points à cent mètres

- **WHEN** l'observateur ouvre la fiche d'un site dont les points `A1` et `B2` sont à cent mètres l'un de
  l'autre
- **THEN** leurs cartes disent « à 100 m du point le plus proche », sans avertissement

#### Scenario: Un seul point géolocalisé

- **WHEN** le site n'a qu'un point géolocalisé
- **THEN** sa carte n'affiche aucune distance
