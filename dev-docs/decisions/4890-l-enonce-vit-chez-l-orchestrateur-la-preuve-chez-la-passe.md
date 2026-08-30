---
type: adr
title: "L'énoncé vit chez l'orchestrateur, la preuve chez la passe dont elle est la trouvaille"
status: stable
article: A3
chantier: "#4890 (chantier #4946, sous #4828)"
decided_at: 2026-08-31
verification: humaine
loupe: "aucun motif ne distingue une règle générale d'un cas qui l'étaye : la question se pose quand une compétence de passe reprend un exemple de l'orchestrateur"
verified:
  - by: humain
    at: 2026-08-31
---

# L'énoncé vit chez l'orchestrateur, la preuve chez la passe dont elle est la trouvaille

## Contexte

Le chantier #4828 a logé les quatorze passes de clôture dans autant de compétences. Chacune a repris,
de l'orchestrateur, le **cas vécu** qui étayait sa règle, parce que ce cas est une trouvaille de sa
passe.

Trois répétitions en sont nées, une par compétence écrite, et elles ont été relevées au fil :

| La preuve | L'énoncé qu'elle étayait | La passe dont elle est la trouvaille |
|---|---|---|
| 43 des 64 EPIC clos sans trace | la trace n'est pas une formalité de fin | 12 |
| les deux planchers périmés de #4671 | le delta se lit entier, jamais filtré | 1 |
| la page blanche en passe 9 | la passe consolide, elle ne découvre pas | 9 |

**Ce n'était pas une négligence.** L'orchestrateur pose une règle **générale**, valable pour les
quatorze passes, et l'étaye d'un cas qui vient nécessairement de **l'une** d'elles. Chaque compétence
neuve reproduisait donc la même forme.

## Le défaut d'un texte qui se répète

Deux copies divergent. La règle vaut pour quatorze passes, le cas pour une : la première mise à jour
qui touche l'une sans l'autre laisse deux versions du même fait, et le lecteur applique celle qu'il
trouve en premier.

Le dépôt l'a déjà mesuré ailleurs : deux copies de la liste des sessions de recette ont divergé en
**quelques heures**.

## Décision

**L'orchestrateur garde l'énoncé, la compétence de la passe garde la preuve, et un renvoi remplace la
redite.**

Ce qui décide n'est pas la longueur ni l'ancienneté du texte, mais **de quelle passe la trouvaille
est**. Une preuve qui ne vient d'aucune passe reste chez l'orchestrateur : c'est le cas de l'ordre du
rejeu et des suites d'une clôture, qui portent sur l'**enchaînement** et qu'aucune compétence ne
pourrait accueillir.

## Pourquoi trancher une fois plutôt qu'au fil

Corriger chaque répétition à mesure aurait demandé quatorze arbitrages, un par compétence écrite,
avec le risque d'en trancher deux dans des sens opposés.

Tranché une fois sur les quatorze, l'arbitrage tient à un critère unique et se relit comme tel.

## Conséquences

**Chaque preuve retirée se confronte à sa compétence avant la coupe.** C'est ce qui a été fait : les
trois ont été vérifiées présentes avant que l'orchestrateur ne les perde. Une suppression sur la foi
d'un « c'est déjà dans la compétence » non vérifié est une perte que rien ne signale.

**La compétence devient le seul endroit où le cas se lit**, et l'orchestrateur y renvoie. Un lecteur
de l'un n'a pas à ouvrir l'autre pour comprendre ce qu'il lit.

**La règle vaut pour la prochaine compétence de passe.** Elle reprendra un cas de l'orchestrateur si
elle en trouve un, et c'est alors à l'orchestrateur de le lâcher.

## Alternatives écartées

- **Laisser les deux copies.** C'est l'état d'où l'on vient. Trois répétitions en une journée, et une
  par compétence à venir.
- **Tout remonter à l'orchestrateur.** Il redeviendrait la page qu'on ne lit pas, ce que le chantier
  #4828 a passé une journée à défaire.
- **Tout descendre dans les compétences.** L'orchestrateur perdrait les règles générales, dont
  certaines ne sont d'aucune passe et n'auraient nulle part où aller.
