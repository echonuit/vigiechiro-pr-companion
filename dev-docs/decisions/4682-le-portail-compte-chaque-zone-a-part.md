---
type: adr
title: "Le portail compte chaque zone à part, et la production est à zéro"
status: stable
article: A9
chantier: "#4682 (chantier #4685, suites de #4656)"
decided_at: 2026-08-28
verification: probable
enforced_by:
  - "scripts/adr/4617-code-mort-et-zone-de-test.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
relations:
  complète: ["4617"]
---

# Le portail compte chaque zone à part, et la production est à zéro

## Contexte

L'[ADR 4617](4617-le-portail-voit-les-tests-et-le-code-mort.md) a étendu le portail aux deux zones
et posé **un cliquet unique** sur leur total. L'[ADR 4587](4587-le-plancher-des-renvois-de-test-est-distinct.md),
écrite le lendemain pour les planchers de renvois, refuse cette forme :

> Deux planchers, un par arbre, et surtout pas un seul sur les deux. Un plancher unique laisserait
> une perte d'un côté se compenser par un gain de l'autre : total stable, verdict vert.

Les deux se sont croisées sans se voir. Trouvé en passe 0 de la clôture du chantier #4656.

**Le défaut est actif.** Ce chantier venait de ramener la production à zéro violation, les quarante
restantes étant toutes en zone de test. Une méthode morte réapparue en production pouvait donc être
payée par un `NcssCount` retiré d'un test : total inchangé, garde vert, régression invisible dans la
zone qui compte le plus.

Le témoin le montre : à total constant, la production gagne une violation et le garde d'avant ne
bronche pas.

## Décision

**Deux cliquets, un par zone, et le garde rend le pire des deux verdicts.**

L'ADR 4617 garde le sien pour la zone de test ; celle-ci porte celui de la production. Les deux
populations sont disjointes, comme les planchers de renvois des ADR 4395 et 4587, et pour la même
raison : réunies sous un compteur, elles s'annulent.

**Le seuil de production est à zéro**, et c'est un **refus** plutôt qu'un cliquet. L'article A9
distingue les deux : un cliquet se resserre à mesure que la dette baisse, un refus n'a plus rien à
resserrer. Toute violation qui apparaît en production fait donc rougir, sans marge.

Le champ reste `ratchet: 0` parce que le mécanisme du dépôt n'en a pas d'autre, mais il se lit comme
un refus, et le dire ici évite qu'on cherche à le « resserrer ».

## Conséquences

**Ce qu'on gagne.** L'acquis du chantier #4656 est gardé : la production ne peut plus regagner une
violation en silence.

**Ce qu'on perd.** Un signalement de moins sur le total. Un contributeur qui retire une violation en
zone de test ne voit plus le compte global descendre d'autant, puisque les deux comptes vivent à
part. C'est le prix de la disjonction, et il est le même que celui payé par l'ADR 4587.

**Ce qui reste à surveiller.** Un seuil à zéro rend le portail sensible : une seule règle ajoutée au
jeu, si elle mord en production, bloque la chaîne. C'est voulu, et l'ADR 4617 rappelle que l'ajout
d'une règle se mesure avant d'être décidé.
