---
type: adr
title: "Un garde exécute la règle qu'il juge, il ne la relit pas"
status: stable
article: A1
chantier: "#4291"
decided_at: 2026-08-24
verification: certaine
enforced_by:
  - ".github/scripts/verifie-decisions-du-tournage-connecte.sh"
  - ".github/scripts/verifie-jeton-vivant.sh"
  - ".github/scripts/revoque-jeton.sh"
verified:
  - by: machine:ci
    at: 2026-08-24
relations:
  prolonge: ["4291"]
---

# Un garde exécute la règle qu'il juge, il ne la relit pas

## Contexte

Une règle de CI vit rarement dans un endroit commode. Trois formes se rencontrent dans ce dépôt :

- une valeur de YAML - `needs:`, `if:`, l'ordre des pas - qu'un garde lit par analyse ;
- un script de `.github/scripts/`, qui peut porter son propre `--auto-test` ;
- **du shell posé dans un `run:`**, c'est-à-dire du code exécutable rangé dans une chaîne de YAML.

La troisième forme se garde mal. Le réflexe est d'y chercher un motif : « le fichier contient-il bien
`clips-connectes` ? », « la ligne dit-elle `|| code=000` ? ». Ce réflexe juge le **texte** d'un
programme au lieu de son **comportement**, et le dépôt a payé pour savoir que les deux diffèrent.

## La mesure qui a tranché

`verifie-jeton-vivant.sh` (#4328) portait un auto-test à six cas, tous verts, qui éprouvaient sa
fonction de verdict. Lancé pour de bon contre un port mort, le script affichait :

```
✗ LA PLATEFORME NE RÉPOND PAS (HTTP 000000)
```

`curl -w '%{http_code}'` écrit **déjà** « 000 » quand la connexion échoue **et** sort non nul : le
`|| echo 000` en ajoutait un second. Aucune relecture ne l'avait vu - il avait traversé trois écritures
et deux revues - et aucun auto-test ne pouvait le voir, puisque tous éprouvaient le verdict et aucun
l'appel.

Le même défaut vivait à deux autres endroits, dont `api-live.yml`, où il n'existe **que** sous forme de
`run:`. Il y a été trouvé en extrayant ce `run:` du YAML et en le lançant.

## Décision

**Quand la règle à garder est du shell, le garde l'extrait et l'exécute.** Il ne cherche pas son motif.

Trois conséquences, et la troisième est celle qu'on oublie :

**1. L'appel se sépare du verdict.** Une fonction qui interroge, une fonction qui juge. La seconde
s'éprouve par une table de codes ; la première s'éprouve en la lançant contre un port mort de la boucle
locale - `http://127.0.0.1:1` -, ce qui ne demande aucun réseau et rougit en une milliseconde.

**2. Le verdict se prend sur le MESSAGE, pas sur le code de sortie.** En exécutant du shell dans un
bac à sable, on remplace ses dépendances par des leurres qui échouent. Toute exécution sort alors non
nulle, et un garde qui lirait le code passerait au vert en croyant avoir vu ce qu'il cherchait. C'est
le faux vert le plus facile à construire de tout ce dispositif.

**3. Ce qui ne peut pas être extrait le déclare.** Un `run:` peut dépendre de son contexte au point
qu'aucun bac à sable ne le reproduise. Le garde l'écrit dans son en-tête plutôt que d'emprunter la
solidité de ses voisins.

## Conséquences

`verifie-decisions-du-tournage-connecte.sh` tient les trois décisions de l'ADR 4291. Pour la première -
le refus de la source `clips-connectes` par la comparaison des tournages - il extrait la fonction
`reprendre()` du YAML, remplace `gh` par un leurre, la lance sur `clips-connectes` et **lit son
message**. Son contrôle de l'autre bord vérifie qu'une source ordinaire ne déclenche pas ce refus.

`verifie-jeton-vivant.sh` et `revoque-jeton.sh` portent chacun un cas d'auto-test qui exerce leur appel
contre un port mort.

`api-live.yml` reste la limite connue : son `run:` a été extrait et lancé à la main pour éprouver sa
correction, mais rien ne le rejouera. C'est écrit dans son commentaire, et suivi par #4385.

## Ce que cette décision ne dit pas

Elle ne dit pas qu'un garde par analyse est mauvais. L'ordre de deux pas, la présence d'une fonction
d'état dans un `if:`, la portée d'un `env:` sont des propriétés du **document**, et s'y lisent très
bien. La règle porte sur le cas où le document contient un **programme**.

Elle ne dit pas non plus qu'exécuter dispense de la mutation. C'est l'inverse : un garde qui exécute se
mesure comme les autres, en cassant ce qu'il prétend attraper et en le regardant rougir.
