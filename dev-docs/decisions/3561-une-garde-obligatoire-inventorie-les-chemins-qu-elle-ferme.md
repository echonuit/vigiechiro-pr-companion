---
type: adr
title: "Une garde obligatoire inventorie les chemins qu'elle ferme"
status: stable
article: A3
chantier: "#3561, lot 3 du chantier #3518"
decided_at: 2026-08-15
verification: certaine
enforced_by:
  - ".github/scripts/veille_plateformes.py"
verified:
  - by: machine:ci
    at: 2026-08-15
relations:
  amende: ["2744"]
  applique: ["0041"]
---

# Une garde obligatoire inventorie les chemins qu'elle ferme

## Contexte

Le lot 3 a branché le train de publication sur une preuve : `release` ne part plus sans un passage
complet réussi de la suite sous Windows et macOS, de moins de dix jours (#3526). Le motif est solide -
le produit est livré en MSI et DMG, et son premier passage sous Windows a rendu **onze échecs**, dont
un vrai défaut produit.

La garde a été posée sans inventorier ce qu'elle ferme. Deux décisions en ont fait les frais.

**L'ADR 0041 avait écrit la procédure**, et elle a été sautée :

> Avant de rendre un check obligatoire, inventorier **tous les chemins d'écriture** vers `main` - PR
> humaines, PR de bot, pushes directs d'automatismes - et se demander, pour chacun, comment le check y
> rapportera. Un chemin sans réponse est un **blocage permanent**.

Fait après coup, l'inventaire tient en deux lignes : le `schedule` du mercredi, et le
`workflow_dispatch`. La seconde était vide.

**L'ADR 2744 décidait le contraire**, en toutes lettres : « Pourquoi `workflow_dispatch` reste : un
correctif urgent n'attend pas le train. » Le job `preuve-des-plateformes` ne portait aucun `if`
distinguant les déclencheurs : il gardait les deux.

Ce qui a tranché n'est pas le retard - un passage complet lancé à la main coûte ~50 min. C'est qu'un
**test instable et sans rapport** aurait retenu un correctif de sécurité. Ce n'est pas une hypothèse :
la même clôture a mesuré dix tests dont le verdict dépend de l'ordre d'exécution (#3773), et l'un d'eux
a rendu **vert à 8 h 14 et rouge à 15 h 34 sur le même commit et la même image**.

## Décision

**Une garde obligatoire énumère les chemins qu'elle ferme, et laisse à chacun une réponse.** Quand un
chemin doit rester ouvert, l'exception est **explicite et tracée**, jamais implicite ni impossible.

Concrètement, pour le train :

```yaml
run-name: >-
  ${{ (github.event_name == 'workflow_dispatch' && inputs.raison_du_contournement != '')
      && format('⚠️ publication SANS preuve des plateformes - {0}', inputs.raison_du_contournement)
      || 'publication' }}
```

- une entrée `raison_du_contournement`, **vide par défaut** ;
- renseignée, elle saute la garde, part dans le **titre du run** - donc dans l'historique des
  exécutions, pas seulement dans le log d'un job - puis dans son résumé ;
- **refusée en dessous de 20 caractères** : un contournement dont la trace est « x » n'en laisse pas.

### Ce qui distingue une preuve d'un passage

La veille ne compte que les passages **complets**. Un passage peut être **ciblé** depuis #3754 - deux
classes au lieu de 4600 - et l'API des runs ne dit pas quelles entrées ont été passées à un
`workflow_dispatch`. Le workflow porte donc son périmètre dans son `run-name` : `[complet]` ou
`[ciblé]`.

Filtrer sur le **déclencheur** aurait été plus simple, et c'est ce qui a été écrit d'abord. C'est
précisément ce filtre qui fermait le chemin du correctif urgent : un passage manuel *complet* est une
preuve tout aussi bonne qu'un passage programmé ; c'est le passage *ciblé* qui n'en est pas une.

### Deux pièges d'expression GitHub, tenus pour acquis par personne

- Tester `inputs.raison_du_contournement == ''` **seul** aurait désarmé la garde sur le `schedule` :
  `inputs` y est **null**, et une expression GitHub compare deux types différents en les coulant en
  nombre - `null == ''` y est donc **vrai**. La garde se serait sautée toute seule le mercredi,
  c'est-à-dire exactement les jours où elle sert. La condition teste d'abord le déclencheur.
- `needs` sur un job **sauté** saute le dépendant par défaut. Sans `!cancelled()` et un test explicite
  sur `.result != 'failure'`, le contournement aurait **empêché** la publication au lieu de la
  permettre.

## Ce que cette ADR amende dans l'ADR 2744

L'ADR 2744 écrit : « `workflow_dispatch` reste : un correctif urgent n'attend pas le train ». La phrase
n'est plus vraie **sans condition**. Elle se lit désormais : *un correctif urgent n'attend pas le train,
et il ne part pas non plus sans qu'on ait écrit pourquoi il se passe de la preuve.*

Le reste de l'ADR 2744 est inchangé : cadence hebdomadaire, mercredi, `semantic-release` intact.

## Conséquences

- **Le train peut refuser**, et il le dit avec la raison - preuve absente, périmée, ou historique
  illisible. Trois refus distincts plutôt qu'un silence.
- **Un contournement est visible pour toujours** dans l'historique des runs, sans qu'il faille ouvrir
  un job. C'est ce qui rend l'exception supportable : elle ne s'oublie pas.
- **Le câblage n'a jamais tourné.** `release.yml` est dormant (`ENABLE_RELEASE != 'true'`). Le
  script est éprouvé sur les données réelles du dépôt et les expressions sont validées par
  `actionlint`, mais le graphe de jobs s'exécutera pour la première fois le jour de l'activation. Cela
  se dit, plutôt que de laisser croire que le dispositif a été vu à l'œuvre.
- **Le marqueur repose sur un nom.** Renommer `[complet]` d'un seul côté casse la détection ; la veille
  refuse alors en accusant **elle-même**, comme `ETAPE_CONTRAT` le fait pour la veille du contrat d'API.

## Alternatives écartées

- **Laisser la garde sur les deux chemins.** Cohérent - un correctif qui ne peut pas prouver les
  plateformes ne devrait pas partir. Mais c'est le blocage permanent que l'ADR 0041 dit d'éviter, et il
  suffisait d'un test instable pour retenir un correctif de sécurité une semaine.
- **Exempter `workflow_dispatch`.** Restaure l'ADR 2744 à la lettre, et rend la garde contournable par
  simple clic, sans trace ni justification. Le bouton serait devenu le chemin de moindre résistance
  pour éviter un rouge - le défaut que ce dépôt combat partout ailleurs.
- **Un secret ou une variable de dépôt comme interrupteur.** Invisible dans l'historique, et modifiable
  sans laisser de trace dans le run qu'il autorise.
