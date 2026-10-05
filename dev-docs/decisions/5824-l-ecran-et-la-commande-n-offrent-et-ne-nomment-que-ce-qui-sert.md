---
type: adr
title: "L'écran et la commande n'offrent et ne nomment que ce qui sert à la forme du dépôt"
status: stable
article: A23
heuristiques:
  - "nielsen-2"
  - "nielsen-8"
chantier: "#5824, lot 25 du chantier #5596, et #5835 trouvé à sa clôture"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/view/LotDepotConnecteViewTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/EtapesDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/CompteRenduChiffreDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/cli/commande/DeposerVigieChiroTest.java"
verification_note: "les quatre classes tiennent l étape absente et la numérotation en forme WAV, l écran inchangé en forme ZIP, le nom de l unité dans le compte rendu, et la parité avec la commande. Le cas hors connexion en forme WAV n est tenu qu au modèle de vue : aucun test d interface ni aucun aperçu ne le montre (#5838)"
relations:
  complete: ["5677-le-depot-part-en-wav-et-un-depot-entame-garde-son-mode"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# L'écran et la commande n'offrent et ne nomment que ce qui sert à la forme du dépôt
## Contexte

L'[ADR 5677](5677-le-depot-part-en-wav-et-un-depot-entame-garde-son-mode.md) a fait des séquences WAV
la forme par défaut du dépôt. L'écran de lot, construit autour des archives ZIP, ne lisait jamais
cette forme. Connecté en WAV, il affichait une étape « Générer les archives » sans objet, y écrivait
que le téléversement « produit ses archives et les supprime », comptait des « archives » dans le
compte rendu, conseillait de les régénérer pour réparer un refus, et justifiait le blocage d'une
relance par un audio « non conservé ». La commande disait les mêmes choses.

## Décision

**1. La forme se lit à un seul endroit**, `ServiceLot.formeDuDepot` : celle d'un dépôt entamé, sinon
celle du réglage. C'est la règle qui décide aussi de ce qui part.

**2. L'étape des archives n'est offerte que si elle sert** : en forme ZIP, ou quand l'application ne
peut pas téléverser elle-même. Connecté en WAV, elle disparaît, avec ce qui sert au dépôt manuel
d'archives. Le porteur l'a confirmé : qui veut déposer à la main choisit le ZIP.

**3. Les étapes se numérotent sans trou**, de 1 à 3 ou de 1 à 4.

**4. Le compte rendu nomme ce qui est parti** : séquences, archives, ou unités quand le plan mêle
les deux. « Régénérez les archives » ne se conseille que pour des archives (ADR 3854). L'écran et la
commande lisent la même règle, `UniteDeDepot`.

**5. La relance d'une nuit analysée reste bloquée** dans les deux formes, avec la raison qui vaut pour
la sienne.

## Ce qui a été écarté

**Garder l'étape en la disant « sans objet ».** Une étape affichée se lit comme une étape à faire.

**Une propriété de plus sur `LotViewModel`.** Elle lui faisait franchir le plafond `GodClass` : la vue
lit le fil d'étapes, qui compte trois puces ou quatre.

## Conséquences

Connecté en WAV, un téléversement en échec n'offre plus de repli manuel à l'écran. Et un changement
de défaut ne se livre plus sans avoir cherché ce qui supposait l'ancien : cette ADR existe parce que
l'ADR 5677 ne l'avait pas fait.
