---
type: adr
title: "Une comparaison reprend une exécution de tournage, avec les refus de la mesure moins un"
status: stable
article: A5
chantier: "#5930, lot 3 du chantier #5933"
decided_at: 2026-10-06
verification: certaine
enforced_by:
  - ".github/scripts/verifie_decisions_du_tournage_connecte.py"
verification_note: "la dixième décision du garde lance le bloc de reprise de la comparaison face au leurre qui sert déjà à la mesure, et exige les mêmes phrases de refus des deux ateliers. Sept cassures, chacune tuée par un seul cas : numéro cherché parmi les versions, comparaison tenue à un seul commit, tournage non conclu, deux artefacts, aucun artefact, artefact expiré, permission de lecture retirée. Rien ne tient qu un artefact expiré reste listé par la forge : ce cas vient du contrat de son API, et n a pas été observé"
relations:
  amende: ["5854-le-tournage-precedent-se-garde-avant-d-etre-ecrase"]
  prolonge: ["5885-un-plancher-appartient-a-l-instrument-qui-l-a-pris", "4331-un-garde-execute-la-regle-qu-il-juge"]
verified:
  - by: machine:ci
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# Une comparaison reprend une exécution de tournage, avec les refus de la mesure moins un

## Contexte

`comparer-tournages.yml` prenait ses deux côtés dans des versions publiées. Une branche ne pouvait donc
pas comparer ses clips à ceux de `main` avant fusion. Deux lots ont buté dessus le 5 octobre 2026 :
#5885 a comparé ses deux tournages dans l'atelier de mesure, faute de mieux, et #5929 a monté un
conteneur sur un poste pour savoir si vingt-deux gestes convertis changeaient une dernière image.

L'[ADR 5854](5854-le-tournage-precedent-se-garde-avant-d-etre-ecrase.md) avait envisagé une entrée
« numéro d'exécution » et l'avait écartée, pour deux raisons : c'est une seconde voie de reprise à
garder en parité avec la première, et un artefact expire. Elle notait déjà que le besoin revenait par
ce côté.

Les deux raisons sont justes. `mesurer-les-planchers.yml` reprend les clips d'une exécution depuis
l'[ADR 5885](5885-un-plancher-appartient-a-l-instrument-qui-l-a-pris.md), avec trois refus. Une
seconde reprise écrite à côté pouvait en perdre un sans que rien ne rougisse.

## Décision

**Chaque côté d'une comparaison est une version publiée ou un numéro d'exécution de
`tournage-recette.yml`.** Une valeur purement numérique est une exécution. Aucun des 200 derniers tags
du dépôt ne l'est, et un nombre n'est donc jamais cherché parmi les versions : inconnu, il se dit
illisible.

**Les refus sont ceux de la mesure, par les mêmes phrases.** L'exécution a conclu en succès. Elle porte
un artefact de clips, et un seul. Il n'est pas vide.

**La parité est tenue par le garde, pas par la ressemblance.** Les phrases de refus sont nommées une
fois dans `verifie_decisions_du_tournage_connecte.py`, et exigées des deux ateliers face au même
leurre. Retirer un refus d'un côté fait rougir la décision de ce côté.

**Un seul refus ne se reprend pas, celui du même commit.** Il est juste pour un plancher, qui est le
bruit entre deux tournages identiques. Il rendrait la comparaison inutile, dont l'objet est de mettre
deux commits côte à côte. Le garde l'éprouve dans ce sens : une comparaison tenue à un seul commit est
une de ses cassures.

**Un artefact expiré se dit.** Le refus nomme le commit à retourner. Et quand l'exécution ne liste plus
aucun artefact de clips, le message rappelle le délai de quatorze jours.

**Le résumé nomme les deux côtés** avant l'index : le numéro, le commit et la branche de chaque
exécution. Un index qui ne dit pas ce qu'il compare ne se relit pas.

## Ce qui a été écarté

**Un atelier à part pour les exécutions.** Il aurait dupliqué l'installation de l'instrument et l'appel
de l'outil, et l'instrument est précisément ce que deux ateliers doivent partager.

**Sortir la reprise du YAML dans un script commun aux deux ateliers.** La parité tiendrait par
construction. Mais c'est réécrire `mesurer-les-planchers.yml` et déplacer les cassures de deux
décisions du garde, pour un lot qui n'en avait pas besoin. Le portage du shell prendra les deux blocs
ensemble.

**Garder un tournage de `main` en référence durable**, à la façon de
`clips-plateforme-de-test-precedent`. Une comparaison avant fusion veut le `main` d'aujourd'hui, et un
tournage se relance en une commande.

**Refuser qu'une source se compare à elle-même.** Le cas existait avant ce lot pour les versions, et
il n'est pas de son périmètre.

## Conséquences

Pour comparer une branche à `main`, on tourne les deux et on donne les deux numéros. Les planchers lus
sont ceux du dépôt, avec l'instrument du flux : plus besoin de conteneur sur un poste.

Une paire mixte reste permise, une version d'un côté et une exécution de l'autre.

Éprouvé le 6 octobre 2026 sur une paire dont l'écart était connu : l'exécution 37312569488 (f9c9e1218)
contre la 37343768383 (3ef05487f, branche de #5929). Le clip
`ScenarioMenuDeLigneImportTest.le_menu_de_ligne_s_ouvre_pendant_l_import` y sort à 24,230 %, le chiffre
relevé la veille en conteneur, sur 95 cas comparés (exécution 37448502869).

`mesurer-les-planchers.yml` ne disait pas qu'un artefact a expiré : c'était le seul point hors
parité. Depuis #5979 il le dit par les mêmes phrases, exigées des deux ateliers, délai compris.

Deux exécutions de populations différentes, l'une ordinaire et l'autre de la plateforme de test, n'ont
aucun cas commun. La comparaison annonce alors tout apparu et tout disparu. Elle sortait en 0 ;
depuis #5934 elle sort en 1, son index écrit et versé au résumé, parce que rien n'a été comparé.

## Ce qu'on ne sait pas

Si la forge liste un artefact expiré avec `expired: true`, ou le retire aussitôt. Une exécution du
4 septembre 2026 n'en listait plus aucun le 6 octobre. Aucune exécution n'a été trouvée dans
l'intervalle, et le refus nommé repose donc sur le contrat de l'API. Cela vaut pour les deux ateliers.
