---
type: adr
title: "Un arbre repris d'un outil amont se déclare, il ne se réécrit pas"
status: stable
article: A5
chantier: "#4339 (OpenSpec), étendue par #4377 (Mocodo), chantier #4334"
decided_at: 2026-08-24
verification: certaine
relations:
  amendee_par: ["4516-une-commande-nomme-un-geste"]
enforced_by:
  - "scripts/adr/2843-tiret-cadratin.py"
  - "scripts/adr/4368-apostrophe-en-libelle.py"
verified:
  - by: machine:ci
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# Un arbre repris d'un outil amont se déclare, il ne se réécrit pas

!!! warning "Ce qui fait foi aujourd'hui"
    **Amendée le 2026-08-26** par [ADR 4516](4516-une-commande-nomme-un-geste.md) : les six commandes `opsx` sont sorties de la
    table des exemptions en disparaissant. Seuls les trois diagrammes Mocodo y restent.

## Contexte

Le dépôt tient deux règles typographiques à tolérance zéro : aucun tiret cadratin
([ADR 2843](2843-typographie-cliquet-plutot-que-nettoyage.md)), aucune apostrophe courbe
([ADR 4368](4368-l-apostrophe-d-un-libelle-est-droite.md)).

Deux fois dans le même chantier, du texte est entré sans avoir été écrit ici.

**Les six compétences OpenSpec** (#4339) sont reprises verbatim de l'outil, en anglais, et portent
**dix-sept cadratins**. Le cliquet a rougi, à juste titre : elles vivent dans `.agents/skills`, une
zone à tolérance zéro depuis #4337.

**Les trois SVG rendus par Mocodo** (#4377) portent **dix-sept apostrophes courbes**. Vérifié aux
octets : leur source `.mcd` porte `date d'import`, l'apostrophe droite, et le rendu porte
`date d'import`, la courbe. **L'outil substitue.**

## Décision

**Ce qui vient d'un outil amont ne se réécrit pas. L'exemption se déclare, nominativement, avec sa
raison.**

Réécrire ferait diverger la copie de sa source, et la première mise à jour de l'outil rendrait la
correction. Dans le cas de Mocodo, la source est déjà conforme : la corriger n'aurait rien à
corriger, et le rendu serait défait à la régénération suivante.

**L'exemption nomme les fichiers un par un, jamais un préfixe.** Une septième compétence amont, un
quatrième diagramme, doit faire **rougir** le garde plutôt qu'être exempté en silence. C'est le coût
volontaire de cette décision : elle demande un geste à chaque ajout, et ce geste est ce qui la rend
visible.

**Deux dispositifs, pas un.** La zone exclut, pour que la tolérance zéro ne rougisse pas. Et le
régime de couverture inscrit le fichier avec son motif, sans quoi l'exemption serait muette. Le
cliquet des cadratins porte les deux ([ADR 2843](2843-typographie-cliquet-plutot-que-nettoyage.md)) ;
sans le second, un fichier propre mais non gardé rechute sans bruit.

## Comment on sait que c'est un outil amont, et pas une excuse

**On le mesure.** Pour Mocodo, en comparant la source au rendu, octet par octet. Pour OpenSpec, en
constatant que le texte est en anglais et arrive avec le paquet.

Une exemption qui ne repose que sur « c'est de l'amont » est une exemption qu'on s'accorde. Celle-ci
demande la mesure qui le prouve, et l'en-tête du garde la porte.

## Alternatives écartées

**Réécrire, et accepter la divergence.** Le corpus amont serait alors un fork silencieux : la mise à
jour suivante rendrait les corrections ou produirait un conflit que personne n'attendait. Le dépôt a
déjà tranché pour la prose reçue à l'exécution
([ADR 2802](2802-un-texte-qu-on-n-a-pas-ecrit-se-borne-a-son-entree.md)) ; le raisonnement vaut pour
un arbre de fichiers.

**Exempter par préfixe** (`.agents/skills/openspec-*`, `*.svg`). Moins de gestes, et c'est le défaut :
un fichier entrerait dans la zone franche sans que personne ne le décide. L'ADR 3575 l'a déjà mesuré
sur une liste de noms, qui ne protège qu'elle-même.

**Ne pas reprendre l'outil.** Recevable, et c'est ce que la question ouverte de #4339 posait pour
OpenSpec. Ce n'en est pas une pour Mocodo, dont les diagrammes sont le modèle conceptuel du produit.

## Conséquences

Le dépôt porte aujourd'hui **cinq fichiers exemptés** au titre de cette décision, plus les trois
compétences OpenSpec de la zone des compétences. Chacun nomme sa raison.

Cette décision se relira le jour où un troisième outil entrera. Le geste sera le même : mesurer que
la substitution vient de l'outil, nommer les fichiers, écrire le motif.

## Ce que la suite a montré

La règle vaut toujours pour ce qui reste amont, et elle a rencontré sa limite au chantier #4511. Six des neuf fichiers qu'elle exemptait devaient porter le cycle de ce dépôt, et le canal prévu pour l'y injecter sans les toucher ne pouvait pas le faire.

L'[ADR 4515](4515-adopter-un-arbre-amont-quand-il-doit-parler-notre-cycle.md) dit à quelle condition un arbre amont s'adopte plutôt que de rester exempté, et ce que l'adoption coûte. Les six compétences OpenSpec sont sorties de la table des exemptions ; les six commandes `opsx` et les trois diagrammes Mocodo y restent.
