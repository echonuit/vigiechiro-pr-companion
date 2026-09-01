# coherence-horaire-d-une-nuit Specification

## Purpose

Dire à l'observateur si la nuit qu'il vient d'importer couvre la fenêtre d'enregistrement que le
protocole Vigie-Chiro Point Fixe exige.

L'interruption en cours de nuit **ne fait pas partie de cette capacité** : elle demande une donnée
qui n'est persistée nulle part, et l'exigence ci-dessous dit pourquoi. Ce paragraphe l'annonçait
comme livrée tant que le report n'avait pas été tranché.

## Requirements

### Requirement: La fenêtre exigée est un plancher

Le protocole demande de commencer **au moins** 30 minutes avant le coucher du soleil et de finir **au
moins** 30 minutes après son lever. Le système SHALL traiter cette fenêtre comme un minimum à
couvrir, et non comme une plage à respecter exactement : une plage plus large la couvre.

Le système SHALL n'employer aucun seuil de tolérance en minutes. La question posée est « la fenêtre
est-elle couverte », pas « de combien s'écarte-t-on d'une cible ».

*Vérifié par* : cas unitaires sur le calcul, un par borne, avec une plage plus large, une plage
exacte et une plage plus étroite.

#### Scenario: Une plage plus large que la fenêtre exigée

- **WHEN** l'enregistrement commence 45 minutes avant le coucher et finit 45 minutes après le lever
- **THEN** le système rend le niveau **information**, et ne signale aucun défaut de protocole

#### Scenario: Une plage exactement égale à la fenêtre exigée

- **WHEN** l'enregistrement commence 30 minutes avant le coucher et finit 30 minutes après le lever
- **THEN** le système ne signale aucun défaut de protocole

#### Scenario: Une plage qui commence trop tard

- **WHEN** l'enregistrement commence 30 minutes **après** le coucher du soleil
- **THEN** le système rend le niveau **avertissement**, et nomme la borne non couverte

#### Scenario: Une plage qui finit trop tôt

- **WHEN** l'enregistrement finit 10 minutes après le lever du soleil
- **THEN** le système rend le niveau **avertissement**, et nomme la borne non couverte

### Requirement: Ce que le système ne peut pas savoir, il ne l'affirme pas

Le système SHALL déduire l'interruption du **journal du capteur** seul, et SHALL NOT l'inférer d'un
intervalle sans enregistrement entre deux fichiers son : une nuit calme et une nuit interrompue s'y
ressemblent, et aucune mesure disponible ne les sépare.

Le système SHALL NOT présenter l'absence d'avertissement comme une preuve que la nuit est entière, le
journal du capteur étant circulaire et pouvant avoir perdu la trace d'une interruption.

**Le niveau d'interruption est reporté, et ce report est une décision.** La réalisation a montré que
la complétude d'une nuit, calculée à l'import par `CycleAcquisition`, n'est **persistée nulle part** :
le diagnostic s'ouvre plus tard, sur un passage en base, et n'a aucun moyen de l'apprendre. Livrer le
troisième niveau demanderait d'abord de persister cette donnée, ce qui est un travail à soi seul et
rejoint le sujet du lot #4990. En attendant, le système SHALL NOT feindre de savoir : il ne rend que
ce que les horaires disent.

*Vérifié par* : relecture humaine de la prose affichée, et un cas unitaire montrant qu'une longue
absence de fichiers sans mention au journal ne déclenche aucun avertissement.

#### Scenario: Un long silence sans trace au journal

- **WHEN** deux heures séparent deux enregistrements consécutifs, et le journal ne consigne aucune
  interruption
- **THEN** le système ne rend aucun avertissement d'interruption

### Requirement: Les deux plages sont montrées

Le système SHALL montrer la plage **exigée** par le protocole et la plage **effectivement
enregistrée**, afin que l'observateur voie ce qui est attendu et ce qu'il a obtenu plutôt qu'un seul
verdict.

*Vérifié par* : capture de l'écran de diagnostic dans son état d'avertissement, et cas de la
commande `diagnostiquer`.

#### Scenario: Les deux plages à l'écran

- **WHEN** l'observateur ouvre le diagnostic d'une nuit dont les coordonnées et les horaires sont
  connus
- **THEN** il lit la plage exigée et la plage enregistrée, quel que soit le niveau rendu

### Requirement: Les deux surfaces rendent le même verdict

L'écran de diagnostic et la commande `diagnostiquer` SHALL rendre le même niveau pour la même nuit,
conformément à la parité que l'ADR 0014 exige.

*Vérifié par* : un cas qui interroge les deux surfaces sur la même nuit et compare les niveaux.

#### Scenario: La même nuit, les deux surfaces

- **WHEN** la même nuit est diagnostiquée depuis l'écran puis depuis la ligne de commande
- **THEN** les deux rendent le même niveau

### Requirement: Sans données, le système se tait

Quand les coordonnées du point, la date ou les horaires manquent, ou quand le lieu connaît le jour ou
la nuit polaire, le système SHALL déclarer la vérification **indisponible** plutôt que de rendre un
verdict.

*Vérifié par* : cas unitaires, un par donnée manquante, plus un cas de latitude polaire.

#### Scenario: Aucune coordonnée sur le point

- **WHEN** le point d'écoute n'a pas de coordonnées
- **THEN** le système déclare la vérification indisponible, et n'affiche ni niveau ni plage
