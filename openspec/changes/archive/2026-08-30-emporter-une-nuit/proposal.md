## Why

Une nuit commencée sur un poste ne peut pas être reprise sur un autre. Le « pourquoi » public, ses
deux scénarios de terrain et les deux mécanismes existants qui n'y répondent pas sont dans l'EPIC
[#3848](https://github.com/echonuit/vigiechiro-pr-companion/issues/3848).

Ce changement est son **lot 0**, l'instruction du périmètre : il rend un document et des décisions,
pas du code.

## What Changes

- **Une nuit s'emporte vers un autre poste sous forme de paquet**, et l'avis du relecteur revient
  **signé de son pseudo**, rangé à côté du nôtre plutôt que fondu dedans.
- Le paquet emporte **la sélection d'écoute et ses séquences**, pas la nuit entière. La sélection est
  **figée** : le relecteur juge le même échantillon que l'expéditeur, ce qui rend les deux verdicts
  comparables séquence par séquence. Les enregistrements bruts n'y sont pas non plus.
- Les **identifiants de plateforme ne voyagent pas** : le relecteur juge, l'expéditeur publie.
- `selection_sequence` gagne deux colonnes, `verdict_relecteur` et `relecteur_pseudo`, sur le patron
  de V26 qui a logé l'avis du validateur du MNHN à côté de celui de l'observateur.
- **L'avis du relecteur s'affiche, il ne vote pas.** Le verdict du passage continue de se dériver des
  seuls verdicts de l'expéditeur. Décider qu'un relecteur pèse sur ce verdict serait une décision de
  domaine, et elle n'est pas dans ce lot.
- L'écriture du paquet est précédée d'un **plan lisible** : volume estimé et ventilé par nature de
  fichier, essai à blanc par défaut.

Aucun comportement existant n'est retiré. Pas de **BREAKING**.

## Capabilities

### New Capabilities

- `passage/emport-d-une-nuit` : ce que l'application garantit quand une nuit part vers un autre poste
  et que son avis revient. Ce que le paquet contient, ce qu'il refuse d'emporter, comment l'avis du
  relecteur est attribué, et ce qu'il ne décide pas.

### Modified Capabilities

Aucune. `openspec/specs/` ne porte encore aucune capacité : le seul changement antérieur du dépôt,
`add-carre-par-coord`, n'est pas archivé, et c'est l'archivage qui alimente les specs principales.

## Impact

**Ce que le lot 0 produit** : cette note, le `design.md` qui porte ses cinq décisions, la delta spec
ci-dessus, et les issues du découpage. Aucun code.

**Ce que les lots suivants toucheront**, relevé pour que le périmètre soit lisible et non pour
engager leur conception :

| Zone | Ce qui bougera |
|---|---|
| `src/main/resources/db/migration/` | une migration additive, deux colonnes sur `selection_sequence` |
| `qualification/model/dao/SelectionDao.java` | unique point de persistance de ces tables, 214 lignes |
| `qualification/` | l'affichage de l'avis du relecteur à côté du nôtre |
| `passage/` | l'export du paquet, son import, et la trace de l'emport |

**Ce qui ne bouge pas.** `AgregationVerdict` et le verdict du passage : mesuré, **vingt-trois classes
lisent ce verdict**, du tableau multisite au solde de saison et à quatre commandes de la ligne de
commande. Le laisser intact est ce qui garde ce chantier borné.

**Dépendances.** Aucune. L'identité du relecteur est déjà persistée localement dans `connexion.json`,
avec son pseudo et son rôle, et se lit hors connexion.
