---
type: adr
title: "Le code de sortie distingue le manque du déplacement"
status: stable
article: A13
chantier: "#3500, lot 1 du chantier #3518 ; défaut trouvé à la passe 2 de la clôture de #2720"
decided_at: 2026-08-09
verification: certaine
enforced_by:
  - "CliSauvegardeTest#restaurer_une_sauvegarde_amputee"
verified:
  - by: machine:ci
    at: 2026-08-09
---

# Le code de sortie distingue le manque du déplacement

## Contexte

`BilanRestauration` porte deux accesseurs, et les deux surfaces n'en lisaient pas le même nombre.
L'IHM lit `enClair()` **et** `appelleUnRegard()` : elle passe en `AVERTISSEMENT`, titre « Sauvegarde
restaurée, à un détail près ». La CLI lisait `enClair()` seul, imprimait le texte, puis rendait `0`
sans condition.

La CLI est la surface **scriptable**. Un script qui restaure et teste `$?` recevait `0` sur une
restauration amputée : le fait était bien imprimé, mais dans du texte qu'aucun script ne lit.

⚠️ C'est très exactement le défaut que le lot 1 de #2720 existait pour tuer - « une sauvegarde ne peut
plus mentir » - réapparu sur l'autre surface. Devant un humain, elle ne ment plus. Devant un script,
elle rendait `0`.

Le dépôt avait déjà tranché deux fois. `verifier-maj` a payé la distinction : `0` à jour, `10` mise à
jour disponible, `1` vérification impossible. Et `audit-coherence` porte l'avertissement écrit noir
sur blanc qu'un filtre d'affichage ne doit pas déteindre sur le code de sortie, « sans quoi
`--gravite INFO` sur un workspace abîmé rendrait `0` ».

## Le point qui a demandé un arbitrage

Le remède évident - câbler le code sur `appelleUnRegard()` - était faux, et la mesure l'a montré.
Cet accesseur est un **ou** de trois termes de gravité inégale :

| Terme | Ce qu'il dit | Code |
|---|---|---|
| `!manifestePresent` | on ne sait pas ce qu'on a remplacé | **10** |
| `!absentesDeLaSauvegarde.isEmpty()` | une nuit connue de la base manquait à la sauvegarde | **10** |
| `placements.anyMatch(deplacee)` | les dossiers ont atterri ailleurs | **0** |

Le troisième est le cas **normal** d'une restauration sur une autre machine, c'est-à-dire l'usage
principal de `--complet`. Les deux tests `bats` de restauration complète le montrent : ils restaurent
de la machine A vers la machine B, et l'un vérifie que le compte rendu contient « n'ont pas retrouvé
leur emplacement d'origine ».

## Décision

**`restaurer --complet` rend `10` quand la restauration laisse un manque, et `0` quand tout a été
replacé, fût-ce ailleurs.**

`BilanRestauration.laisseUnManque()` porte cette frontière, plus étroite qu'`appelleUnRegard()`. Le
compte rendu **dit** ce qui manque, et nomme le code : un `10` sans phrase renvoie l'utilisateur à la
documentation.

Ni `1` ni `2` : la restauration a **réussi**. Confondre « ça n'a pas marché » avec « ça a marché,
regarde quand même » est ce que `verifier-maj` a payé avant elle.

## Ce que cette décision coûte

**Les deux surfaces ne lisent plus le même signal, et c'est assumé.** L'IHM garde `appelleUnRegard()`
pour son niveau `AVERTISSEMENT` : devant quelqu'un qui a le compte rendu sous les yeux, signaler que
des dossiers ont changé de place est utile, et le coût d'un avertissement de trop est nul. Un script,
lui, n'a que le code. Un `10` permanent sur toute restauration entre machines s'apprend à s'ignorer -
et emporterait avec lui celui qui compte.

La divergence est donc **un choix de destinataire**, pas un oubli de parité. La règle générale reste
celle de la parité des surfaces ; ici c'est la même information, rendue à deux publics qui n'en font
pas le même usage.

## Conséquences

- `10` rejoint le tableau des codes de sortie de `dev-docs/cli.md`, aux côtés de `verifier-maj`. Il
  n'est **pas** universel : seules les commandes qui le documentent le rendent.
- Deux tests figent la frontière **dans les deux sens** : une sauvegarde amputée rend `10`, une
  restauration sur une autre machine rend `0`. Chacun tombe quand on mute l'autre côté.
- Les deux tests `bats` existants de restauration complète restent à `0`, sans modification : c'est la
  vérification que le nouveau code ne déborde pas sur le cas courant.
