---
type: adr
title: "La fenêtre à surveiller vaut la péremption de ce qu'on envoie"
status: stable
article: A17
chantier: "#4711, lot 1 de l'EPIC #4640"
decided_at: 2026-08-29
verification: humaine
verification_note: "aucun compteur ne dit d'où vient le corps d'une écriture : c'est une lecture de code. La règle se tient à la revue, et la loupe de l'ADR 0020 énumère déjà la surface d'écriture à confronter"
loupe:
  - "scripts/adr/loupe-0020-ecritures-plateforme.py"
relations:
  prolonge: ["4640-pour-ne-rien-effacer-il-faut-se-souvenir-de-ce-qu-on-avait-vu"]
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# La fenêtre à surveiller vaut la péremption de ce qu'on envoie

## Contexte

Deux écritures du dépôt tiennent une concurrence optimiste côté client, et elles se ressemblent au
point qu'on les corrigerait ensemble.

`PublicationPoint` relit les localités d'un site juste avant d'écrire, compare les `_etag`, et renonce
s'ils diffèrent. `SynchronisationParticipation#pousserVers` faisait la même chose pour une
participation, et c'était **faux**.

Le lot 1 du chantier #4640 a corrigé la seconde en lui donnant une **base** : ce que la plateforme
portait à notre dernière lecture. La question s'est alors posée pour la première : faut-il l'aligner ?

## Décision

**Non, et la raison n'est pas que l'une est plus importante que l'autre : c'est que la largeur de
fenêtre nécessaire dépend d'où vient le corps envoyé.**

| Écriture | D'où vient son corps | Fenêtre nécessaire |
|---|---|---|
| `PublicationPoint` | de la lecture qu'elle vient de faire, `avant.avecEnPlus(point)` | l'intervalle entre cette lecture et l'envoi, quelques millisecondes |
| `pousserVers` | du **passage local**, saisi par l'utilisateur plus tôt | depuis notre dernière lecture, soit des minutes ou des jours |

Une comparaison entre deux lectures du même appel suffit donc à la première et pas à la seconde. Ce
n'est pas un défaut chez l'une et une qualité chez l'autre : c'est la même règle appliquée à deux
provenances différentes.

**La règle, énoncée une fois :** une garde de concurrence doit couvrir l'intervalle pendant lequel ce
qu'on s'apprête à envoyer a pu se périmer. Envoyer ce qu'on vient de lire ne se périme pas ; envoyer
ce qu'un humain a saisi se périme dès qu'il a fini de le saisir.

## Ce que cette décision empêche

Un développeur futur qui lirait les deux gardes côte à côte verrait un patron corrigé et un patron
resté en arrière, et alignerait le second par souci de cohérence. Il ajouterait une table, un relevé
et une comparaison à un endroit qui n'en a pas besoin, et il croirait avoir fermé un défaut.

C'est le sens de cette ADR : **la ressemblance de forme entre deux gardes ne dit rien de leur
besoin.** Ce qui les sépare n'est pas visible dans la garde, il est visible dans la provenance du
corps, quelques lignes plus haut.

## Le corollaire, pour toute écriture future

Avant d'écrire une garde de concurrence, une question : **le corps que j'envoie, d'où vient-il ?**

- de la lecture qui précède immédiatement l'envoi, alors une relecture comparée suffit ;
- d'un état local antérieur, alors il faut une base persistée, et l'[ADR 4640] dit laquelle.

## Conséquences

- `PublicationPoint` reste en l'état, et son en-tête dit déjà que sa fenêtre ne se ferme pas. Ce n'est
  plus une dette : c'est une adéquation.
- Toute écriture nouvelle se range dans l'une des deux colonnes du tableau ci-dessus, et le dire fait
  partie de son contrat.
- La loupe de l'[ADR 0020] énumère la surface d'écriture vers la plateforme. C'est la liste à
  confronter à cette règle en revue.

[ADR 4640]: 4640-pour-ne-rien-effacer-il-faut-se-souvenir-de-ce-qu-on-avait-vu.md
[ADR 0020]: 0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md
