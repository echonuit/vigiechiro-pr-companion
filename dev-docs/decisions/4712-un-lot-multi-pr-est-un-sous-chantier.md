---
type: adr
title: "Un lot qui portera plus d'une PR s'ouvre en sous-chantier"
status: stable
article: A11
chantier: "#4712, sas des suites (#4562)"
decided_at: 2026-08-29
verification: humaine
verification_note: "un lot est de la prose dans le corps d'un EPIC, et le nombre de PR qu'il portera est un jugement : aucun compteur ne le lit. Deux dessins de garde mécanique ont été mesurés et écartés, et la loupe pose la question au lieu d'y répondre"
loupe:
  - "scripts/adr/loupe-4712-lots-multi-pr.py"
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Un lot qui portera plus d'une PR s'ouvre en sous-chantier

## Contexte

Le dépôt tient qu'un **chantier** est une évolution d'ampleur EPIC répartie sur **plusieurs PR**, et
qu'il se clôt par douze passes. Il décrit aussi l'ouverture : trier, cartographier, planifier, puis
« découper en issues reliées à un EPIC ».

Entre les deux, rien. Le mot **sous-chantier** n'apparaissait nulle part : ni dans `AGENTS.md`, ni
dans `CONTRIBUTING.md`, ni dans une ADR, ni dans une compétence.

Un lot d'EPIC qui portera plusieurs issues et plusieurs PR se retrouvait donc accroché sous une case
à cocher, avec ses issues rattachées au parent. Il n'a alors ni périmètre écrit, ni bilan, ni les
douze passes, et il se termine sans que personne sache s'il est fini.

**L'erreur se reproduit parce que la forme observée l'enseigne.** Un contributeur qui cherche comment
faire regarde les EPIC en place et les imite. La loupe le mesure au 2026-08-29 : l'EPIC #4511 porte
**sept lots et zéro sous-chantier**, l'EPIC #3848 quatre lots et zéro. Imiter reproduit le défaut, et
le porteur du produit a dû redemander la règle plusieurs fois.

## Décision

**Une PR par issue : dès la deuxième, c'est un chantier.** Au découpage, chaque lot se pose la
question du nombre de PR :

- **une seule** : une issue rattachée à l'EPIC suffit ;
- **deux ou plus**, ou plusieurs issues déjà identifiées : le lot **s'ouvre en sous-chantier**, un
  EPIC enfant, ses issues s'y rattachent, et la case du parent pointe vers lui.

Le seuil s'énonce par le principe et non par un nombre à comparer, parce que la forme précédente se
lisait à l'envers : « une ou deux : des issues suffisent » disait qu'un lot de deux PR n'était pas un
chantier, quand la décision était l'inverse. Elle a été suivie exactement, et il a fallu la corriger
au découpage du sous-chantier C de #5102 (issue #5155).

La règle s'écrit à l'endroit qui induisait l'erreur, c'est-à-dire dans la phrase de découpage
d'`AGENTS.md` et son équivalent de `CONTRIBUTING.md`, et non dans une page qu'on lit une fois.

## Pourquoi la vérification reste humaine

Deux dessins de garde mécanique ont été **mesurés et écartés**, et c'est la mesure qui a tranché.

**Compter les lots citant plus d'une issue** rend zéro sur les dix EPIC ouverts : la forme courante
est un lot en prose sans aucune référence, les issues se rattachant à l'EPIC par ailleurs. Le signal
n'existe pas.

**Compter les issues rattachées via la recherche de la forge** repose sur un outil qui n'honore pas
les phrases exactes : un EPIC sans aucune issue rattachée revenait avec un résultat, lui-même. Un
cliquet bâti là-dessus aurait rougi au hasard.

Reste que le nombre de PR qu'un lot portera est un **jugement**, pas une propriété du texte. Un garde
qui prétendrait le lire promettrait plus qu'il ne tient.

La loupe **pose donc la question** au lieu d'y répondre : elle met sous les yeux, pour chaque EPIC
ouvert, ses lots et ce qui lui pend. Elle lit la forge, et sans elle sort en 2 plutôt que de rendre
un rapport vide qui se lirait comme « aucun lot suspect ».

## Conséquences

- Un lot multi-PR gagne ce qu'une case à cocher ne porte pas : un périmètre, un bilan, une clôture.
- Le parent reste lisible : ses cases pointent vers des chantiers, pas vers des listes d'issues.
- Les EPIC déjà ouverts ne sont **pas rattrapés**. Rejouer le découpage d'un chantier en cours
  coûterait plus qu'il ne rendrait, et la loupe les montre pour ce qu'ils sont.
- Une clôture d'EPIC gagne une question de plus, et c'est celle qui manquait.
