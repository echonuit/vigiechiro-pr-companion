## Purpose

Dire, avant une remise à zéro, d'où l'audio de chaque nuit pourra revenir : du disque, du serveur, ou de nulle
part, pour qu'aucun son ne soit perdu sur la foi d'un verdict trop large.

## ADDED Requirements

### Requirement: Le serveur n'est nommé que pour ce qu'il a reçu

Quand le disque ne porte pas toutes les séquences d'une nuit, le bilan SHALL nommer le serveur comme source
seulement si la nuit est rattachée à une participation et que **toutes** les unités de son plan de dépôt sont
des séquences WAV **déposées**. Sinon il SHALL annoncer la nuit perdue, et, pour un plan en séquences dont
certaines ne sont pas déposées, SHALL en donner le compte.

*Vérifié par* : `ServiceRecuperabiliteTest`, sur une vraie base : un plan WAV entièrement déposé, puis le même
avec une séquence refusée.

#### Scenario: Dépôt WAV entièrement en ligne

- **WHEN** le disque est vide et que toutes les séquences du plan sont déposées en WAV
- **THEN** la source annoncée est le serveur

#### Scenario: Une séquence refusée

- **WHEN** le disque est vide et qu'une séquence sur deux du plan est refusée
- **THEN** la nuit est annoncée perdue, et le motif dit qu'une séquence sur deux n'est pas sur le serveur
