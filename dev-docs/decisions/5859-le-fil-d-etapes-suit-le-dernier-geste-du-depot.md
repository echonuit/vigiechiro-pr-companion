---
type: adr
title: "Le fil d'étapes suit le dernier geste du dépôt : son nom, et s'il reste à faire"
status: stable
article: A23
heuristiques:
  - "nielsen-1"
  - "nielsen-4"
chantier: "#5859, lot 27 du chantier #5596, trouvé en montant l'artefact de sa clôture"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/viewmodel/DernierGesteDuDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/view/LotDepotConnecteViewTest.java"
verification_note: "le premier tient le nom et l état de la dernière étape pour chacun des trois gestes, en trois comme en quatre étapes ; le second confronte la dernière puce au texte du bouton, la lit courante avant le lancement et franchie une fois l analyse planifiée, et lit la confirmation de réinitialisation sous les deux formes. Cinq mutations tuées : nom figé, état figé, analyse demandée ignorée, écouteur retiré, confirmation figée. Rien ne tient que la liaison du contrôleur reste retenue par un champ"
relations:
  complete: ["5676-un-geste-a-un-seul-point-d-action-et-son-resultat-se-lit-pres-de-lui", "5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Le fil d'étapes suit le dernier geste du dépôt : son nom, et s'il reste à faire
## Contexte

L'[ADR 5676](5676-un-geste-a-un-seul-point-d-action-et-son-resultat-se-lit-pres-de-lui.md) a donné au
titre et au bouton de la dernière étape de l'écran de lot le nom du geste : « Lancer la participation »
dès qu'une participation est liée au passage, « Marquer déposé » sinon. Le fil d'étapes du haut de l'écran
lisait deux listes figées, et nommait le même geste « Marquer déposé » au-dessus de la carte. Trouvé en
mettant côte à côte les aperçus de la clôture de #5596.

Le renommer ne suffisait pas. Le fil rend toutes ses étapes franchies dès que la nuit est sur la
plateforme. Une puce « Lancer la participation » se serait donc affichée faite au-dessus d'un bouton
encore offert : une incohérence de nom remplacée par une affirmation fausse. La session de recette S4
portait déjà ce constat, S4-C03.

L'[ADR 5824](5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert.md), de son côté, a
fait dire à l'infobulle de « Réinitialiser le dépôt » ce que la forme du dépôt conserve. La question
que le bouton pose ensuite parlait encore d'archives ZIP dans tous les cas.

## Décision

**1. Le dernier geste a un type**, `DernierGesteDuDepot`, à trois états : marquer déposé, lancer la
participation, participation lancée. Il porte le nom du bouton, écrit une fois.

**2. Le fil lit la liaison du bouton.** Le geste se déduit de la participation liée et de ce que
l'écran sait de l'analyse, là où le bouton se désactive déjà. Le contrôleur, seul à connaître les trois
modèles de vue, l'applique au fil au moment de l'afficher : le calcul des étapes, lui, ne connaît
toujours que le statut du passage.

**3. Sur une nuit déposée, la dernière étape reste courante tant que la participation est à lancer.**
Elle n'est franchie qu'une fois l'analyse demandée ou faite. Un dépôt marqué à la main garde tout son
fil franchi : il n'a pas de suite dans l'application.

**4. Avant la fin du dépôt, le geste ne déplace rien.** Un dépôt partiel offre déjà son bouton de
lancement, mais son étape courante reste le téléversement.

**5. L'infobulle et la confirmation de « Réinitialiser le dépôt » lisent la même phrase**, et elle
ne nomme d'archives que s'il y en a.

## Ce qui a été écarté

**Renommer sans toucher à l'état.** C'était la demande d'origine. Le porteur a choisi le 5 octobre que
le fil suive aussi l'état, après avoir vu la puce verte au-dessus du bouton offert.

**Laisser le fil dire « Marquer déposé ».** La puce verte disait vrai, mais sous un autre nom que la
carte, et le lecteur devait deviner que les deux parlaient de la même étape.

**Faire porter le geste par `LotViewModel`.** C'était le premier jet, et une méthode de plus lui faisait
franchir le plafond `GodClass` (ADR 4682). Le fil calculé reste donc celui du statut, et le geste le
corrige à l'affichage, comme la vue lit déjà le nombre d'étapes pour savoir si celle des archives est
offerte.

## Conséquences

L'état de l'analyse n'est connu que par un relevé, ou par le dernier relevé gardé en local. Sur une nuit
dont la participation a été lancée ailleurs, le fil dit « à lancer » jusqu'au prochain relevé, exactement
comme le bouton.

Les aperçus du lancement composés à la main (#5838) montrent encore un bouton actif après un lancement
accepté : le fil y suit donc un état que le produit ne rend pas. C'est l'aperçu qui est à corriger.
