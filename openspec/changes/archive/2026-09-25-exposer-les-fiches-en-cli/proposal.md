## Why

Le besoin et le rattachement au chantier sont décrits dans #1874 et #5505.

## What Changes

- `lien-espece --code <code Tadarida>` écrit le lien de la fiche de l'espèce.
- `lien-participation --passage <identifiant local>` écrit le lien de la participation associée.
- Une ressource inconnue ou sans fiche entraîne un refus explicite, avec le code de sortie 2.

## Capabilities

### New Capabilities

- `commun/consulter-les-fiches` : retrouver les liens des fiches depuis la CLI, avec les mêmes sources que l'interface.

### Modified Capabilities

Aucune.

## Impact

Sous-commandes picocli, aide et documentation CLI, tests Java et bats. Les services existants de construction des liens restent la source des URL. Aucune requête réseau ni ouverture de navigateur.
