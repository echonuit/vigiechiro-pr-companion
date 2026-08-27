---
type: adr
title: "Un commentaire long en corps de méthode est un signal, pas un décor"
status: stable
article: A30
chantier: "#4472 (le commentaire en corps, chantier #4394)"
decided_at: 2026-08-25
verification: probable
enforced_by:
  - "scripts/adr/4472-commentaire-en-corps.py"
ratchet: 43
inv_key: cliquet-commentaire-corps
verified:
  - by: machine:ci
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# Un commentaire long en corps de méthode est un signal, pas un décor

## Contexte

Le cliquet de l'article A30 compte les lignes de prose des blocs `///`. Il ne voit donc **rien** des
commentaires `//` : le dépôt en porte **10 198 lignes réparties en 4 656 blocs**, dont la quasi-totalité
à l'intérieur d'un corps de méthode, et aucun dispositif ne les regardait.

Une javadoc et un commentaire de corps ne se lisent pourtant pas de la même façon. La javadoc
s'adresse à qui **appelle** : un paragraphe de pourquoi y est à sa place, et c'est même l'endroit que
l'article A30 lui assigne. Un bloc entre deux instructions s'adresse à qui **lit le corps**, et sa
longueur dit presque toujours l'une de trois choses :

- le code d'en dessous est trop obscur pour se passer d'explication - c'est lui qu'il faut réécrire ;
- une décision est restée là au lieu de monter dans une ADR ;
- un pan d'histoire n'a pas été retiré.

## Le défaut, mesuré avant d'être nommé

La mesure **rassure**, et c'est précisément pourquoi elle vaut d'être tenue.

| | valeur |
|---|---:|
| blocs de `//` en corps de méthode | 4 656 |
| lignes | 10 198 |
| médiane | **2** |
| 9ᵉ décile | 4 |
| plus long | 15 |

Le dépôt n'a pas ce défaut aujourd'hui. Mais un fait mesuré une fois n'est pas un fait gardé, et
c'est la règle du dépôt : *ce qui n'est pas compté grandit*.

## Décision

**Un bloc de `//` dans un corps de méthode a un budget de 8 lignes**, et chaque ligne au-delà compte
une. Le seuil est le double du 9ᵉ décile : il laisse passer le régime normal et ne signale que ce qui
en sort franchement.

Le cliquet est posé à **<!--inv:cliquet-commentaire-corps-->43<!--/inv-->**, l'état du jour.

**79 → 43** (#4583). Le chantier #4502 avait écarté ce ruban en invoquant cette ADR, et le motif
n'avait pas voix : son objet était d'aligner sur `vigiechiro-companion`, qui en compte 59.

Huit blocs sur vingt-sept ont été ouverts et contractés, aucun raccourci pour atteindre un chiffre.
Tous portaient de l'histoire que `git log` garde déjà, chacun citant l'issue qui l'explique ; ce qui
reste dit le contrat au présent. Les dix-neuf autres restent tels quels, et c'est ce que cette ADR
annonçait : un bloc long peut être justifié. Ils tiennent une formule, un seuil et sa mesure, une
limite déclarée, ou ce qu'une sonde ne doit surtout pas faire.

Les chiffres sont ceux de **ce** dépôt. Le seuil vient de la ligne d'origine et il est repris parce
que la distribution mesurée ici le confirme : même médiane, même 9ᵉ décile, et aucun bloc au-dessus
de quinze lignes là où elle en comptait un.

**Un compteur, une population.** Ce cliquet est distinct de celui de la javadoc, et les mêler
laisserait un raccourcissement de javadoc compenser un débordement en corps, pour un total stable et
un verdict vert - la règle 2 de l'ADR
[« Une dette qu'on migre au fil de l'eau se tient par un cliquet »](2867-une-dette-se-tient-par-un-cliquet.md).

**La borne est la profondeur d'accolades.** À la profondeur 1, entre les membres d'une classe, un
bloc de `//` documente une **section** et non du code : il ne compte pas. À la profondeur 0, avant la
classe, c'est un en-tête de fichier. Les trois cas sont tenus par des témoins.

## Conséquences

Le niveau est `probable` : un bloc long peut être justifié - une formule, un protocole, un
contre-exemple - et le script rend des **suspects** qu'un humain trie.

**Le comptage des accolades est naïf** : il ne comprend ni les chaînes ni les caractères, donc une
accolade dans un littéral fausse la profondeur. C'est assumé, parce que le verdict ne bascule que si
elle déplace un bloc de part et d'autre de la borne, et qu'un faux positif se trie à la lecture comme
les autres.

## Alternative écartée

**Étendre le cliquet A30 aux `//`.** Un seul compteur pour deux populations, ce que ce dépôt refuse
ailleurs pour la même raison. Et le seuil ne pourrait pas être le même : la javadoc de classe a le
sien à quinze lignes, ce qui ouvrirait ici un budget qu'aucune mesure ne justifie.
