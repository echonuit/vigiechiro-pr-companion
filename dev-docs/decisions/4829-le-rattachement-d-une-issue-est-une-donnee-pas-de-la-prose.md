---
type: adr
title: "Le rattachement d'une issue à son chantier est une donnée, pas de la prose"
status: stable
article: A3
chantier: "#4829 (chantier #4643)"
decided_at: 2026-08-30
verification: certaine
enforced_by:
  - ".github/scripts/verifie_chantier_de_l_issue.py"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Le rattachement d'une issue à son chantier est une donnée, pas de la prose

## Contexte

Le dépôt écrit depuis longtemps qu'une issue appartient au chantier qui traite sa cause. La règle
vivait à quatre endroits en prose, et le lien lui-même n'existait nulle part ailleurs : une case
cochée à la main dans le corps d'un EPIC, et un numéro cité dans un bloc de prise.

Trois sessions l'ont enfreinte le même jour, le 27 août 2026, et il a fallu les reprendre une par
une. Le chantier #4643 a d'abord répondu en écrivant la règle une fois de plus, dans la compétence
qu'on lit au moment de prendre (#4644). Cela ne pouvait pas suffire : la règle était déjà écrite à
trois endroits quand elle a été manquée.

## Le défaut

**Une liste tenue à la main se périme sans bruit, et elle ne se compte pas.**

Mesuré sur le sas des suites #4562, dont les vingt-trois entrées ont été comparées à leur issue avant
d'être basculées :

| Ce qu'était l'entrée | Combien |
|---|---:|
| le titre porte la phrase, à l'identique ou mieux | 8 |
| la phrase ajoutait un rattachement, que le lien natif porte mieux | 5 |
| le titre tronque, mais le fait vit dans le corps de l'issue | 5 |
| la phrase ajoutait une provenance, déjà présente dans le corps | 3 |
| la phrase ajoutait un numéro de PR absent du corps | 1 |
| la phrase portait un fait qui n'existait nulle part ailleurs | 1 |

Vingt-deux entrées sur vingt-trois étaient de la duplication, et **rien ne signalait laquelle était
l'exception**. Celle-ci portait, sur une issue close, le résultat qui infirmait la piste que son
corps invitait encore à éprouver.

La dérive s'est vue ailleurs le même jour. #2104 listait neuf issues à la main et en portait huit en
sous-issues : la neuvième, #4071, était invisible à toute lecture machine, et sa case n'était pas
cochée alors qu'elle est close. Deux mensonges dans une ligne que personne ne relit.

## Décision

**Le rattachement se pose en sous-issue, et la case à cocher disparaît.**

```bash
gh issue create --parent <EPIC> --title "..."
gh issue edit <n> --parent <EPIC>
```

Le corps d'un EPIC cesse de porter un index de ses issues. Il garde ce que le lien ne porte pas :
la description d'un lot, ce qu'il livre et comment on saura qu'il est fini.

**Ce qui se compte se lit sans compter à la main** : `gh issue view <EPIC> --json subIssuesSummary`.
La taille du sas des suites est un signal que sa page revendique depuis son ouverture, et qui
n'était mesurable par personne.

## Conséquences

**La distinction entre index et description décide de ce qu'on retire.** Les listes de #4643, #4562,
#4852 et #4853 recopiaient des titres : elles sont parties. Celles de #2104 et #4859 décrivent des
lots, ce que la compétence `ouvrir-un-chantier` demande d'un corps d'EPIC : elles restent.

**Le mécanisme a été repris sans qu'on le demande.** Quatre trouvailles ont rejoint le sas en
`--parent` dans les heures qui ont suivi la bascule, déposées par d'autres sessions.

**Un état ne s'écrit pas dans le parent.** Le seul fait perdu de justesse vivait là parce que
quelqu'un avait consigné un résultat dans la case plutôt que dans l'issue. Le corps de l'issue porte
la vérité, ce que `clore-une-issue` dit déjà.

**`subIssuesSummary` retarde de quelques secondes** après un changement de parent, et affiche entre
temps un compte faux. Le champ `parent` d'une issue, lui, répond juste tout de suite : c'est celui
que le garde lit.

## Alternatives écartées

- **Écrire la règle une fois de plus.** C'est ce que #4644 a fait, et c'était nécessaire sans être
  suffisant : elle était déjà à trois endroits le jour où trois sessions l'ont manquée.
- **Un garde qui lise le bloc de prise.** C'était le dessin d'origine de #4649 : un motif calé sur
  `**Pris par** : chantier #NNNN`. Un motif de prose périme au premier synonyme, et l'ADR 4713 a
  mesuré ce que coûtent les listes et les motifs tenus à la main.
- **Un label par chantier.** Il faudrait le créer, le tenir, et rien ne relierait un label à l'EPIC
  qui porte le même nom. La forge offre la relation ; la fabriquer à côté serait la troisième copie.
