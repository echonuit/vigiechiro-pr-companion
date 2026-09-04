---
type: adr
title: "Un dispositif rapporte ce qu'il a vu avant de conclure"
status: stable
article: A12
chantier: "#2213 (winget), lot 5 (#2110) de l'EPIC #2104 ; PR #3594, #3597, #3598, #3601, #3603"
decided_at: 2026-08-11
verification: certaine
enforced_by:
  - ".github/scripts/verifie_secret_winget.py"
verified:
  - by: machine:ci
    at: 2026-08-11
relations:
  fait évoluer: ["3501"]
  prolonge: ["2748"]
---

# Un dispositif rapporte ce qu'il a vu avant de conclure

## Contexte

Le canal winget était outillé depuis juillet 2026 et n'avait **jamais rien distribué**. Sa mise en
service a demandé trois exécutions et a traversé cinq défauts. Deux seulement concernaient winget.

Le défaut central mérite d'être raconté, parce qu'il s'est produit **dans l'outil de diagnostic
lui-même**. La garde d'accès interrogeait l'API ainsi :

```bash
vu=$(GH_TOKEN="$1" gh api ".../contents/$CHEMIN" --jq '.[].name' 2>/dev/null) || vu=""
if [ -z "$vu" ]; then
  echo "❌ WINGET_TOKEN s'authentifie, mais ne voit pas le paquet dans winget-pkgs."
```

Ce `2>/dev/null` jette la réponse. Un **404**, un **401**, un **dépassement de quota** et un **hoquet
réseau** y deviennent le même silence - et de ce silence unique, la garde tirait un diagnostic unique
**et affirmatif** : le jeton n'a pas les droits.

C'était faux. Le message réel, une fois la sonde réparée, était :

```
The 'Microsoft Open Source' enterprise forbids access via a personal access tokens (classic)
if the token's lifetime is greater than 8 days.  (HTTP 403)
```

Ni les droits, ni la validité, ni la nature du jeton : sa **durée de vie**. Aucune des quatre
hypothèses formulées ne l'envisageait, et le mainteneur a démenti deux fois, à raison, l'accusation
portée contre son jeton.

**Le coût n'est pas le temps perdu, c'est la confiance mal placée.** Un dispositif qui conclut sans
preuve produit une affirmation qui *ressemble* à une mesure. On la croit, on cherche là où elle
désigne, et on ne revient pas interroger l'instrument - c'est le dernier endroit qu'on soupçonne.

La doctrine existait pourtant déjà dans ce dépôt, écrite pour #2748, dans `veille-contrat-api.sh` :

> Il refuse de conclure quand c'est **lui** qui est cassé. Trois refus explicites, plutôt qu'un
> « 0 jour » rassurant. **Une mesure vide n'est pas un zéro.**

Le chantier n'a donc pas découvert un principe : il a **refabriqué le défaut que ce principe
interdisait**, dans une garde neuve, écrite par quelqu'un qui venait de lire la doctrine.

## Décision

**Un dispositif qui ne peut pas conclure ne conclut pas : il rapporte ce qu'il a vu.** Et quand un
geste est délibéré, son échec est rouge, jamais vert.

Trois conséquences, qui sont les trois remèdes du chantier :

1. **La réponse de l'outil interrogé est rapportée, jamais avalée.** L'énumération des causes possibles
   accompagne le message brut au lieu de le remplacer : un 403 de politique, un 404 de droits, un 401
   de jeton mort et une limite de débit sont **quatre** situations, et le verdict doit laisser le
   lecteur les distinguer.
2. **Un geste manuel ne réussit pas en silence.** `winget.yml` sortait en vert quand son secret
   manquait. C'était juste sous un déclencheur automatique - rougir à chaque publication aurait été du
   bruit. Ça ne l'est plus sous `workflow_dispatch` **seul** : on lance un dispatch parce qu'on veut
   soumettre, et un vert qui n'a rien soumis annonce une publication qui n'a pas eu lieu.
3. **Un contrôle réseau porte sa preuve par une sonde injectable.** Il serait sinon le seul du dépôt à
   ne rien prouver. `WINGET_SONDE` permet à l'auto-test de jouer hors ligne les issues qui comptent,
   dont la moins évidente : le jeton qui **s'authentifie mais ne voit pas** la ressource.

## Ce que cette ADR fait évoluer dans l'ADR 3501

L'ADR 3501 exige que chaque `uses:` porte « **le tag lisible en commentaire** », commentaire déclaré
« obligatoire, pas décoratif ». Deux points bougent, et aucun ne relâche la règle.

**Un épinglage peut viser un commit sans tag.** `winget-releaser` ne porte qu'un tag `v2`, posé sur un
commit de **novembre 2024**, alors que l'action installe un `komac` de mars 2026. Rester sur le tag,
c'était garder vingt et un mois d'écart entre la colle et l'outil qu'elle pilote. Le commentaire dit
alors la **branche et la date** (`# main @ 2026-07-28`) plutôt qu'une version. L'obligation demeure :
le commentaire dit ce qu'on a épinglé.

**Un tag immobile rend la mesure par tags aveugle.** `verifie-fraicheur-actions.sh` comparait le tag
épinglé au tag amont : `v2` face à `v2`, aucun écart, verdict « à jour » sur **608 jours** de retard
réel, sur l'action qui soumet nos paquets Windows. C'est la forme du défaut de #3382 sous l'angle que
son correctif ne pouvait pas atteindre. S'ajoute donc une mesure d'**âge** du commit épinglé face au
HEAD amont, qui ne dépend d'aucun tag et mesure **notre** retard, pas le rythme de l'amont.

Les seuils sont **calibrés sur une mesure** et non choisis : le pire écart du dépôt était de 143 jours,
d'où 180 pour avertir et 365 pour rougir. La garde reste muette sur l'état sain du jour, et rouge sur
le cas qui lui avait échappé.

Accepter « pas de tag » sans distinction rouvrirait le silence que l'ADR 3501 fermait. Trois cas,
donc, séparés par le **commentaire** : une version annoncée que le SHA ne porte plus est un tag déplacé
en amont, donc **rouge** ; une branche annoncée est un épinglage assumé, seul l'âge juge ; un
commentaire **absent** ne permet pas de trancher, donc **rouge** plutôt que rassurant.

## Conséquences

- **`WINGET_TOKEN` est périssable à 8 jours**, le plus court du dépôt devant les 14 jours de
  `VIGIECHIRO_TOKEN`. Il ne s'entretient pas : il se refait **juste avant** chaque soumission, ce qui
  s'accorde avec un canal manuel et rare.
- **Un auto-test qui ne vérifie que des codes de sortie ne suffit pas.** Neutraliser la reconnaissance
  du 403 ne change pas le verdict - la garde rougit de toute façon par le chemin générique. Seul un
  test sur le **message** distingue « rouge pour la bonne raison » de « rouge en accusant autre chose ».
- **Une neutralisation qui ne modifie rien rend un vert qui ne prouve rien.** Rencontré deux fois
  pendant ce chantier, après #3293 : la première tentative n'avait pas appliqué sa substitution, et
  l'auto-test est resté vert. Vérifier que la ligne a **effectivement** changé fait partie de
  l'éprouvage.
- Le contrôle d'accès réel n'est **jamais** joué en CI : il ne tourne qu'au dispatch. C'est assumé, et
  c'est pourquoi son auto-test à sonde injectée est le seul endroit où on le voit à l'œuvre.

## Alternatives écartées

- **Laisser la garde sortir en vert sans secret**, au motif que `winget-releaser` rougirait de toute
  façon. C'est vrai et insuffisant : l'échec survenait **après** un runner Windows, un téléchargement
  de MSI et une installation complète, et il désignait le mauvais coupable.
- **Vérifier la validité du jeton par sa forme** (préfixe `ghp_`, longueur). Cela n'aurait rien
  attrapé : le jeton était de la bonne forme. Seul un appel réel distingue un jeton utilisable d'un
  jeton conforme.
- **Faire rougir la fraîcheur dès 180 jours.** Écarté sur mesure : `anchore/scan-action` était à 143
  jours sans que ce soit un défaut. Un rouge qui se déclenche sur l'état sain du jour s'apprend à
  s'ignorer, et ce dépôt en a déjà fait l'expérience.
