---
type: adr
title: "Un témoin se prouve par mutation, et la mutation est mécanique"
status: stable
article: A2
chantier: "#4490 (passe 9 de la clôture de #4462)"
decided_at: 2026-08-26
verification: certaine
enforced_by:
  - "scripts/adr/verifie_temoins_non_decoratifs.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Un témoin se prouve par mutation, et la mutation est mécanique

## Contexte

`verifie_scripts.py` clôt sur une phrase : « les N scripts chargés **détectent** leur violation témoin ». Elle affirme davantage que ce que la suite vérifie. Un témoin peut exister, s'exécuter, passer, et ne rien tenir.

Celui du cliquet de longueur d'ADR n'affirmait que `isinstance(suspects(), list)`. Un garde qui aurait cessé de détecter le passait. Le défaut a vécu jusqu'à ce qu'une passe de clôture aille le chercher à la main, et c'est le faux vert que le dépôt refuse partout ailleurs, installé dans le dispositif même qui le refuse.

L'article A2 dit déjà le geste : un garde est vu rouge sur sa propre mutation, et la mutation se refait après toute réécriture. Ce qui manquait n'est pas la règle, c'est qu'elle reposait sur la discipline, et la discipline lâche exactement quand ça va vite.

## Décision

La mutation devient mécanique. Pour chaque garde chargée par `verifie_scripts.py`, `verifie_temoins_non_decoratifs.py` neutralise ses fonctions de détection, relance la suite, et exige qu'elle rougisse. Tolérance zéro.

**La liste se dérive, elle ne s'écrit pas.** Les gardes viennent des appels `_charge("...")` de la suite. Un glob vieillit, et un garde neuf passerait au travers : c'est précisément le défaut que ce script existe pour attraper, et il serait cocasse de l'y poser.

**Le sens de la panne est le bon.** Si la neutralisation cessait de fonctionner, le garde continuerait de détecter, la suite resterait verte, et le script crierait « témoin décoratif » à tort. Un faux positif est bruyant ; c'est le silence qu'il fallait éviter.

## Conséquences

- Le coût est mesuré : `verifie_scripts.py` s'exécute en 0,05 s, et 23 gardes se mutent en 1,2 s. Rien ne justifie de refaire ce balayage à la main.
- Un témoin nouveau est éprouvé dès sa PR, et non à la clôture suivante.
- L'auto-test du garde prouve le mécanisme dans les **deux** sens : qu'un témoin solide fait bien rougir la suite sous mutation, et que sans mutation la suite est verte. Sans le second, un script qui répondrait toujours « tout va bien » passerait le premier.

## La cécité déclarée

La mutation ne remplace que les **fonctions de module non préfixées**. Un témoin qui n'éprouverait qu'une constante, une expression régulière ou une classe y survit sans être décoratif pour autant. Le garde ne prononce donc rien sur ceux-là, et il nomme ses exemptions une par une plutôt que de les taire :

| Script | Ce que son témoin éprouve réellement |
|---|---|
| `resserre_cliquets.py` | une expression régulière, et la **présence** d'une fonction plutôt que son effet |

Cette liste est la dette de la décision, pas son angle mort : elle est écrite, donc elle se relit.

## Alternatives écartées

- **Interdire les témoins structurels.** Certains sont légitimes : tenir un corpus, ou la présence d'une passe, se dit structurellement. C'est de les confondre avec une preuve de détection qui trompe.
- **Un glob sur `scripts/adr/*.py`.** Il aurait couvert des scripts sans témoin, que la suite ne charge pas, et rendu du bruit là où le garde doit rendre une liste courte et vraie.
- **Refaire le balayage à chaque clôture.** C'est ce qui a été fait une fois, et ce qui a trouvé le défaut. Ce n'est pas ce qui l'aurait empêché.
