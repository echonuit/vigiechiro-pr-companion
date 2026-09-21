## Purpose

Retrouver depuis un terminal les fiches des espèces et des participations, avec les sources utilisées dans l'interface graphique.

## ADDED Requirements

### Requirement: Lien de la fiche d'une espèce

La commande `lien-espece --code <code Tadarida>` SHALL écrire uniquement l'URL et un retour à la ligne sur stdout, avec le code de sortie 0. Elle respecte la priorité PNA puis la source universelle choisie dans les réglages, sans accès réseau. Vérification : `CliLiensTest` et `cli.bats`.

#### Scenario: Espèce du PNA
- **WHEN** le code possède une fiche PNA
- **THEN** la commande écrit son URL PNA

#### Scenario: Espèce avec nom latin hors PNA
- **WHEN** le code possède un nom latin et aucune fiche PNA
- **THEN** la commande écrit le lien de la source universelle choisie

### Requirement: Lien de la participation

La commande `lien-participation --passage <identifiant local>` SHALL écrire uniquement l'URL de la participation liée au passage et un retour à la ligne, avec le code 0. Vérification : `CliLiensTest` et `cli.bats`.

#### Scenario: Passage rattaché
- **WHEN** un lien local associe le passage à une participation Vigie-Chiro
- **THEN** la commande écrit l'URL de cette participation sans ouvrir de navigateur

### Requirement: Absence de fiche ou invocation invalide

Les commandes SHALL rendre le code 2 et une explication sur stderr, en laissant stdout vide, lorsqu'une fiche est indisponible ou qu'un argument requis manque. Vérification : `CliLiensTest`, `cli.bats` et `cli-surface.bats`.

#### Scenario: Taxon inconnu ou sans fiche
- **WHEN** le code est inconnu ou ne possède ni fiche PNA ni nom latin
- **THEN** la commande explique le refus

#### Scenario: Passage non rattaché
- **WHEN** le passage n'a aucun lien vers une participation
- **THEN** la commande explique qu'aucune participation n'est liée

#### Scenario: Argument manquant
- **WHEN** la commande est invoquée sans son option requise
- **THEN** l'aide d'usage indique l'argument manquant
