---
type: adr
title: "La vue d'un passage vérifie le traitement, dans les mots de l'écran de lot et sans dépendre de lui"
status: stable
article: A22
heuristiques:
  - "nielsen-1"
  - "nielsen-4"
chantier: "#5862, lot 30 du chantier #5596 (retour de terrain 2.193.0)"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/passage/viewmodel/VerificationDuTraitementTest.java"
  - "src/test/java/fr/univ_amu/iut/passage/viewmodel/MemesMotsQueLEcranDeLotTest.java"
  - "src/test/java/fr/univ_amu/iut/passage/view/VerificationDuTraitementUITest.java"
  - "src/test/java/fr/univ_amu/iut/architecture/IsolationFeatureSourcesTest.java"
verification_note: "la première classe tient le geste issue par issue, importé, en cours, déjà importé, en échec ; la deuxième joue le même relevé sur les deux écrans et compare les phrases ; la troisième tient le bouton grisé et sa raison ; la quatrième refuse qu une feature en lise une autre"
relations:
  complete: ["5784-un-releve-qui-trouve-l-analyse-terminee-importe"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# La vue d'un passage vérifie le traitement, dans les mots de l'écran de lot et sans dépendre de lui

## Contexte

Depuis l'ADR 5784, « Actualiser » sur l'écran de lot relève l'état de l'analyse et, si elle est
terminée, importe les observations. La vue d'un passage n'en savait rien : elle ne portait que le lien
vers la participation sur le portail. Qui ouvrait une nuit déposée devait aller jusqu'à l'écran de lot
pour savoir si l'analyse était finie. Le porteur a demandé le 5 octobre 2026 que la vue du passage
l'offre.

Le geste existait donc, et ses phrases aussi. Elles vivaient dans `lot/viewmodel`, d'où la vue d'un
passage ne peut pas les lire : une feature ne dépend pas d'une autre (article A22).

## Décision

**1. La vue d'un passage porte « Vérifier le traitement »**, à côté de « Voir la participation ». Le
bouton relève l'état de l'analyse et, si elle est terminée et que la nuit n'a pas ses observations, les
importe. C'est la règle de l'ADR 5784, par le même chemin.

**2. Les phrases du traitement vivent dans le socle.** `FormatsTraitement` est passé de `lot/viewmodel`
à `commun/viewmodel`, sans changer d'un mot. Les deux écrans lisent le même texte, et aucun ne dépend de
l'autre. Une seule chose diffère : quand un import échoue, chaque écran nomme son propre bouton à
rejouer.

**3. Le résultat va au bandeau de retour**, pas dans une zone neuve. Il dit la phrase d'état, puis la
phrase d'import quand il y en a une. Sa sévérité suit l'issue : succès quand des observations sont
importées, information quand l'analyse est en cours, planifiée ou déjà importée, erreur quand l'analyse
ou l'import a échoué, ou que le relevé est impossible.

**4. Sans participation liée, ou hors connexion, le bouton reste visible, grisé, et dit pourquoi.** Une
enveloppe porte l'infobulle, qui survit à la désactivation, comme pour « Voir la participation ».

## Ce qui a été écarté

**Composer le modèle de vue de l'écran de lot dans la vue du passage.** C'était le plus court, et il
faisait dépendre `passage` de `lot`. Deux écrans qui disent la même chose n'ont pas à se connaître : ils
lisent la même source.

**Une carte « Traitement » dans la vue du passage**, avec le dernier état connu. Elle aurait demandé à
la vue de garder un état, donc de le rafraîchir, donc de sonder la plateforme, ce que l'ADR 5784 exclut.

**Faire grossir le modèle de vue du passage.** Il est au plafond de taille. Le geste vit dans un objet à
part, `VerificationDuTraitement`, qui joue le relevé hors du fil JavaFX et rend un retour prêt pour le
bandeau.

## Conséquences

Le bandeau ne garde pas l'état : à la réouverture de la vue, rien ne dit où en était l'analyse. C'est
voulu. L'écran de lot garde sa carte et son dernier état connu ; la vue du passage répond à une question
posée, et ne la pose pas d'elle-même.

Une phrase du traitement qui change se change à un seul endroit, et `MemesMotsQueLEcranDeLotTest` rougit
si l'un des deux écrans cesse de la lire.

La ligne de commande ne change pas : `etat-traitement-vigiechiro --importer` portait déjà le geste.
