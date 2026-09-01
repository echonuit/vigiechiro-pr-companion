---
type: adr
title: "Une mesure plausible se contrôle avant d'être crue"
status: stable
article: A5
chantier: "#4804 (clôture, passe 11)"
decided_at: 2026-09-01
verification: humaine
verification_note: "aucun garde ne peut juger si une mesure mesure ce qu'elle prétend ; la décision porte sur le geste qui précède, et ses sept cas sont écrits pour être reconnus"
enforced_by:
verified:
  - by: human:nedseb
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Une mesure plausible se contrôle avant d'être crue

## Contexte

Le chantier #4804 est un chantier de **comptage** : six bancs rougissaient par intermittence et aucun
n'avait de taux. Tout y repose donc sur des mesures, et il en a produit des dizaines.

**Sept d'entre elles étaient fausses**, et aucune ne se signalait comme telle.

## Le défaut

**Une mesure fausse rend un nombre, exactement comme une mesure juste.** Rien ne les distingue à la
lecture, et c'est ce qui les rend coûteuses : on bâtit un remède dessus.

| Ce que la mesure disait | Ce qu'elle valait | Ce qui l'a démasquée |
|---|---|---|
| 348 `ConnectException` par run | une coupure qu'un test provoque exprès | des comptes **identiques à l'unité** sur sept runs |
| 20 tentatives sur 20 « un garde a refusé » | un garde **vert** imprime aussi le mot `REFUSE` | lire la ligne d'erreur **finale**, pas le journal |
| une mutation « tuée » | elle ne s'était pas **appliquée** | un `assert motif in texte` avant l'écriture |
| 15 attentes réinventées | le `grep` lisait le **fichier**, pas la méthode | ouvrir deux des quinze |
| 9 attentes « muettes » | deux **rattrapaient** et disaient tout | lire leur `catch` |
| 18 puis 9 gardes non conformes | trois idiomes pour la même chose | ouvrir trois des dix-huit |
| un banc instable depuis #4145 | il détectait un **vrai défaut du produit** | faire parler la sonde sur les passages **verts** |

## Décision

**Une mesure se contrôle avant d'être crue**, et le contrôle prend l'une de ces formes :

- **le contrôle inverse**, quand la mesure est un taux : le même taux sur la population qui ne porte
  pas le motif, ce que l'ADR 4841 exige déjà ;
- **l'échantillon lu**, quand la mesure est un compte : on ouvre deux ou trois des cas comptés, et
  l'on regarde s'ils sont bien ce que le compte prétend ;
- **le cas négatif**, quand la mesure est un dispositif : on lui donne ce qu'il ne doit **pas** voir ;
- **le succès instrumenté**, quand la mesure ne parle qu'à l'échec : un rapport qui ne se lit qu'en
  rouge ne dit pas si le vert est vert pour la bonne raison.

**Un résultat qui surprend se remesure**, et un résultat qui **ne surprend pas** se contrôle quand
même : quatre des sept ci-dessus étaient parfaitement plausibles.

## Pourquoi aucun garde ne la tient

Aucun script ne peut juger si une mesure mesure ce qu'elle prétend : il faudrait qu'il connaisse
l'intention. C'est ce qui classe cette décision `humaine` plutôt que `certaine`, et c'est aussi
pourquoi ses sept cas sont écrits en toutes lettres. Ils ne servent pas d'illustration : ils servent
à **reconnaître la forme** quand elle se représente.

## Conséquences

- **Le contrôle coûte moins que le remède qu'il évite.** Ouvrir trois fichiers a démasqué deux
  comptes faux ; le remède bâti sur le compte de 15 aurait converti deux sommeils en attentes.
- **La dernière ligne du tableau est la plus chère.** Un banc a passé des mois pour instable parce que
  son signal était traité comme du bruit. Le contrôle qui l'a retourné coûtait un `println` sur le
  chemin vert.
- **L'ADR 4841 devient un cas particulier de celle-ci**, celui des taux, et garde sa forme mécanique.

## Alternatives écartées

- **Un garde.** Il faudrait qu'il sache ce que la mesure prétend mesurer. Écrire un dispositif qui ne
  peut pas juger serait précisément le défaut que l'ADR 2748 nomme.
- **Ne rien écrire, la vigilance suffisant.** Sept fois dans un seul chantier, dont trois après que
  j'aie été pris deux fois. La vigilance n'a pas suffi.
