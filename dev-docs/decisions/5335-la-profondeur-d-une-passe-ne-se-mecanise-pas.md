---
type: adr
title: "La profondeur d'une passe de clôture ne se mécanise pas, parce qu'une trace est un auto-rapport"
status: stable
article: A11
chantier: "#5335 (cinq passes sur quatorze cochées sans être tenues à la clôture de #5277), lot de l'EPIC #5584"
decided_at: 2026-10-01
verification: humaine
verification_note: "la trace d'une clôture rapporte ce que son auteur dit avoir fait, et un dispositif qui la lit ne peut exiger que la forme du rapport. Une forme coûte moins que le travail qu'elle atteste, donc l'exiger produit de la conformité. Trois candidats ont été joués contre le seul témoin disponible et aucun ne le sépare. La profondeur se tient par la relecture d'un tiers, et rien ne la refuse"
relations:
  complete: ["4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente"]
verified:
  - by: human:nedseb
    at: 2026-10-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-01
---

# La profondeur d'une passe de clôture ne se mécanise pas, parce qu'une trace est un auto-rapport

## Contexte

L'[ADR 4659](4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md) tient la
**forme** : une clôture sans trace ne se distingue pas d'une clôture absente, et un garde la compte.
Rien ne tient la **profondeur**, et une case cochée à la légère ressemble exactement à une case cochée
après travail.

La clôture de #5277 l'a montré en deux tours, tous deux sur l'EPIC. Cinq passes sur quatorze avaient
été cochées sans être tenues ; trois ont produit du travail réel au second tour, dont deux faussetés
en passe 3 et un écart de portée tu en passe 11. **Aucune n'aurait été reprise sans deux questions
posées de l'extérieur.**

## Décision

**Aucun dispositif ne jugera la profondeur d'une passe, et la limite se déclare.** Ce que le dépôt a
déjà fait pour la qualité d'un critère de fin, que l'[ADR 4992](4992-le-critere-de-fin-se-rappelle-et-se-mesure-il-ne-se-refuse-pas.md)
déclare ne pas juger.

### L'argument, qui porte sur la famille et pas sur un candidat

**Une trace est un auto-rapport.** Un dispositif qui la lit ne peut exiger que la **forme** du
rapport, jamais la vérité de ce qu'il rapporte. Et une forme coûte moins que le travail qu'elle est
censée attester : qui coche à la légère peut cocher à la légère **dans la forme exigée**, au prix
d'une phrase. Exiger une forme produit donc de la conformité, et transforme la trace en gabarit.

La corroboration est dans le corpus. `2. Cohérence CLI et UI : sans objet, 0 fichier de production`
est **bien formée** : elle cite un compte. Nul ne peut dire si ce compte a été mesuré ou écrit de
mémoire, et aucun motif ne le pourra. Le partage utile n'est pas *muette contre nommante*, c'est
**mesurée contre affirmée**, et une trace ne porte pas cette information.

**Cet obstacle est définitif, et non une contrainte d'outillage.** L'information n'est pas dans
l'objet lu : aucun instrument, présent ou futur, ne trouvera dans un rapport ce que le rapport ne
contient pas. C'est ce qui distingue cette limite d'une limite d'API, qui peut tomber le jour où
quelqu'un dispose d'un autre instrument.

### Ce qui a été essayé, pour que personne ne le réessaie

Un candidat devait être **rouge** sur la première trace de #5277 et **vert** sur la seconde.

| Candidat | tour 1 | tour 2 | sépare ? |
|---|:---:|:---:|:---:|
| la case cite un chemin, un renvoi ou du code | 4/7 muettes | 2/7 muettes | non |
| idem, ou un chiffre | 0/7 | 0/7 | non |
| la case dit « vérifié », pas seulement « sans objet » | 5/7 | 2/7 | non |

Le premier et le troisième refuseraient aussi le bon tour ; le second est vacant, un « sans objet »
contenant presque toujours un chiffre. Les deux traces portent **14 cases cochées chacune** et les
**14 libellés ont changé** : le signal est dans la prose, c'est le lire mécaniquement qui échoue.

**Comparer la trace au delta est exclu par construction.** Les deux traces décrivent le **même**
delta, donc un dispositif qui lit le delta rend le même verdict sur les deux, quelle qu'en soit la
finesse. Il ne peut les séparer qu'en lisant la trace, c'est-à-dire en redevenant le premier candidat.

## Conséquences

Les deux dispositifs en place tiennent la forme et continuent de la tenir :
`verifie_cloture_consignee.py` compte les EPIC clos sans trace, `loupe-4992-lots-sans-critere.py`
relève les lots sans critère. **Aucun des deux ne juge, et cette ADR ne leur en demande pas plus.**

Ce qui tient la profondeur est nommé : **la relecture d'un tiers**. C'est ce qui a produit le second
tour de #5277, et c'est le seul mécanisme attesté. Une clôture gagne donc à être relue par quelqu'un
qui ne l'a pas faite, et ce n'est pas une obligation que l'on peut refuser puisque rien ne la vérifie.

**Ce que cette ADR n'autorise pas.** Elle ne dit pas qu'une case se coche sans travail. La règle de
`clore-un-chantier` reste entière : une passe non tenue se coche **en le disant**. Ce qui change est
qu'on cesse d'attendre d'un garde qu'il le vérifie.
