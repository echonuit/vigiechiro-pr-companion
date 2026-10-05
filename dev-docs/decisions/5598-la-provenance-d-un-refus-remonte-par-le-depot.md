---
type: adr
title: "La provenance d'un refus remonte par le dépôt, et un refus du stockage n'est pas un refus de droits"
status: stable
article: A13
heuristiques:
  - "nielsen-9"
chantier: "#5598, lot 2 du chantier #5596"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/CompteRenduChiffreDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/cli/commande/DeposerVigieChiroTest.java"
verification_note: "les deux classes tiennent le geste nommé pour chaque cause et la parité entre l écran et la commande. Elles ne disent pas que la relance réussit contre un vrai stockage : le geste « relancez, les autorisations sont redemandées » a été vérifié à la main (#5598)"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# La provenance d'un refus remonte par le dépôt, et un refus du stockage n'est pas un refus de droits
## Contexte

Un refus `403` venu du stockage S3 était classé comme un refus de **droits** : même statut, autre
serveur. L'écran de dépôt et `deposer-vigiechiro` conseillaient donc « Reconnectez-vous : elles
redeviendront reprenables ». Samuel a payé ce conseil d'une reconnexion puis d'un redémarrage, le
14 septembre, sans effet : sa session n'était pas en cause.

## Décision

**1. La provenance d'un refus est portée, pas devinée.** `deposerEnParts` rend son issue avec la
provenance de l'étape qui l'a produite, `API` ou `STOCKAGE`, et `CauseRefus.de(reponse, provenance)`
classe avec elle. Le statut seul ne suffit pas, et le texte de la réponse ne se relit pas (#3689).

**2. Chaque cause nomme son geste, et seulement le sien.** Un refus de droits : se reconnecter. Un
refus du stockage : se reconnecter n'y changera rien, relancer redemande de nouvelles autorisations
d'envoi, et le dépôt manuel reste possible. Un contenu refusé : voir
l'[ADR 5824](5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert.md). Quand les causes
sont mêlées, chacune dit sa part.

**3. L'écran et la commande donnent le même conseil** (ADR 0014), chacun dans ses mots.

## Ce qui a été écarté

**Un composant « origine » dans `ReponseApi.Refuse`.** Le record est déconstruit par position dans
quatorze sites, et trois combinateurs devraient le propager, pour une information que seul le dépôt
consomme.

**Deviner la provenance depuis l'hôte de l'URL ou le corps de la réponse.** C'est relire du texte :
la même panne s'écrit de trop de façons.

## Conséquences

Un refus rejouable (`429`, `5xx`, coupure) ne porte toujours aucune cause. Une cause nouvelle demande
son geste vérifié avant d'être nommée, pas l'inverse.
