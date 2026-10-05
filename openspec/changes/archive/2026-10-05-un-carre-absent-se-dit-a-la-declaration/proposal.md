## Why

Samuel a déclaré dans Companion le carré 202013, absent de Vigie-Chiro en Point Fixe, et ne l'a appris qu'au dépôt, sa nuit déjà importée et transformée (2.193.0, #5607). C'est la deuxième fois au même endroit (#3458). La décision du 30 septembre est de continuer à renvoyer au portail, mais **au moment de la déclaration**.

Ce que le code fait aujourd'hui :

- la vérification de la modale répond, pour un carré absent, « vous pouvez le déclarer ici », en succès, sans parler du portail ;
- elle annonce « existe déjà, récupérez-le » pour un carré présent seulement sous un autre protocole, alors que le dépôt en Point Fixe échouera ;
- elle n'a lieu que si l'observateur clique « Vérifier sur Vigie-Chiro » : qui clique directement « Créer » n'apprend rien, et peut même créer un doublon local d'un carré déjà en Point Fixe ;
- `creer-site` refuse le doublon en Point Fixe, mais crée sans rien dire un carré absent ou sous un autre protocole.

Le même résultat de recherche est classé trois fois, par la vérification, par le rapatriement et par `creer-site`, et seul le rapatriement le classe juste.

## What Changes

- Une **classification commune** du résultat de recherche d'un carré : Point Fixe présent, autre protocole seulement, absent. Les trois appelants s'en servent ; chacun garde la phrase de son geste.
- Pour un carré absent ou présent seulement sous un autre protocole, la vérification de la modale et `creer-site` disent qu'**il faudra l'activer en Point Fixe sur le portail** (y créer un point), puis le récupérer ici, avant de pouvoir déposer. Le verdict « absent » passe de succès à avertissement. La phrase sur le portail est écrite une fois, et le rapatriement la reprend.
- **« Créer » vérifie ce qui ne l'a pas été** : si aucun verdict n'est affiché pour ce numéro, la modale interroge le portail avant d'enregistrer. Carré déjà en Point Fixe : elle ne crée pas, garde le verdict et propose « Récupérer ce carré », comme `creer-site` refuse. Carré absent, sous un autre protocole, ou portail injoignable : elle crée, et le bandeau de retour de « Mes sites » porte le verdict.
- `creer-site` écrit le geste du portail sur sa sortie d'erreur ; sa sortie standard reste l'identifiant du site, que les scripts lisent.

## Capabilities

### New Capabilities

(aucune)

### Modified Capabilities

- `sites/declaration-de-carre` : l'exigence « La vérification sur Vigie-Chiro reste un geste séparé » se précise pour « Créer » ; une exigence s'ajoute sur ce que le verdict d'existence dit du dépôt, pour les quatre cas, sur les deux surfaces.

## Impact

`sites/model` (la classification, `RechercheCarreExistant`, `RapatriementCarre`), `sites/viewmodel` et `sites/view` (la modale de site, le bandeau de « Mes sites »), `cli/commande/CreerSite`. Hors périmètre : créer le site sur la plateforme (écarté par la décision), l'édition d'un site existant, et le conseil du dépôt (`passage`), qui dit déjà juste et que la règle d'architecture tient à l'écart de `sites`.
