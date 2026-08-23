# Comparer deux tournages

Depuis [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258), chaque version porte
les clips de ses deux bancs sur son tag. Savoir ce qui a bougé entre la dernière version et le
tournage courant demandait d'ouvrir cinquante lecteurs et de s'en souvenir. Le flux **comparer deux
tournages** fait le tri ; le regard fait le reste.

## Lancer une comparaison

Le flux `comparer-tournages.yml` (`workflow_dispatch`) prend quatre entrées :

| Entrée | Ce qu'elle attend |
|---|---|
| `avant` | ce à quoi on compare : un tag de version (`v2.188.0`) ou `clips-recette` |
| `apres` | ce qu'on regarde, mêmes valeurs |
| `banc` | `bash` ou `java`, pour choisir le préfixe sur les tags de version |
| `tolerance` | la tolérance de couleur, en pourcentage. 5 par défaut |

L'usage courant est `avant` = la dernière version, `apres` = `clips-recette`, ce qui montre **ce que le
travail non publié change à l'écran**.

Le résultat s'écrit dans le **résumé du job** et les images partent dans un artefact, gardé quatorze
jours.

!!! note "Pourquoi rien n'est committé"

    La comparaison « dernière version contre tournante » change dès que l'un des deux bouge. Une page
    committée serait périmée au prochain tournage manuel, et une page périmée sur un sujet visuel est
    pire qu'une page absente : on la croit.

## Ce qu'on obtient, par cas

Trois signaux, du plus fiable au moins fiable.

**La présence.** Le cas est dans les deux tournages, ou **apparu**, ou **disparu**. C'est le signal le
plus sûr et le moins cher, et c'est souvent celui qui compte.

**L'image finale.** Les deux dernières images accolées (`<cas>.avant-apres.png`), leur carte des
différences (`<cas>.ou.png`, rouge là où ça bouge), et la part de pixels changés.

**La durée.** Un scénario qui s'allonge a presque toujours changé.

## Comment lire le chiffre

**Il trie, il ne prouve pas.** Mesuré sur deux tournages du **même commit** :

| changement | part mesurée | rapport au plancher |
|---|---|---|
| rien (deux tournages identiques) | ≤ 0,01 % | le plancher |
| un chiffre (12 × 18 px) | 0,021 % | ×2 |
| un mot (60 × 18 px) | 0,101 % | ×10 |
| un libellé (220 × 18 px) | 0,364 % | ×36 |
| un encart (400 × 120 px) | 4,212 % | ×420 |

Un mot changé vaut dix fois le plancher : le chiffre le sort du lot. Un caractère changé n'en vaut que
deux : le chiffre ne le distingue plus, et c'est la **carte des différences** qui le localise.

## Pourquoi une tolérance de couleur

Sans elle, le plancher n'est pas de 0,01 % mais de **16 %**.

Deux tournages du même commit rendent le même écran avec un **anticrénelage** légèrement différent. La
carte des différences le montre sans ambiguïté : le rouge est sur le texte et sur les bordures, jamais
sur les aplats. Un décalage de mise en page a été soupçonné, puis **écarté par la mesure** - décaler
l'image aggrave l'écart au lieu de le réduire.

Une tolérance de 5 % absorbe cet anticrénelage sans rendre l'instrument aveugle, comme le tableau
ci-dessus le montre.

!!! warning "La tolérance se mesure, elle ne se fige pas"

    Les 5 % valent pour une machine et sept cas. Sur une autre, le plancher peut différer.
    `compare-tournages.sh --plancher <A> <B>` le remesure sur place, en comparant deux tournages qu'on
    sait identiques.

## Ce que la méthode ne voit pas

⚠️ **L'image finale compare la destination, pas le chemin.**

La dernière image est le seul instant où deux tournages sont comparables sans dépendre de leur cadence :
au milieu, l'image n°40 de l'un et l'image n°40 de l'autre montrent deux moments différents. Mais un cas
dont l'objet est une **transition** - « la modale s'ouvre sans saut » - garderait une fin identique
alors que son milieu aurait bougé.

La durée est le seul garde-fou bon marché contre cela, et il est grossier. C'est une limite assumée, pas
un oubli.

## Une mesure impossible n'est pas « rien n'a changé »

⚠️ Deux dossiers vides font **échouer** la comparaison au lieu de rendre « aucun cas ne bouge », et une
mesure qui échoue se compte à part dans le résumé.

⚠️ Et un **outil absent** est une panne d'installation, pas une mesure : le script refuse de commencer
et nomme ce qui manque, au lieu de rendre cinquante « ? » qui se liraient comme cinquante cas stables.
Les deux ne se réparent pas au même endroit, donc ils ne doivent pas se lire pareil.

Ce n'est pas de la prudence de principe : le premier jet de cet outil rendait « ? » sur les sept cas
d'un vrai tournage, et son index annonçait tranquillement « aucun cas ne bouge ». La cause était que
`identify` écrit `1.152e+06` pour une toile de 1280 × 900, et le test d'entier qui suivait refusait la
mesure. Un instrument cassé qui se présente en succès est pire que pas d'instrument
([ADR 2748](../decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)).

Le premier lancement réel l'a montré deux fois. Le workflow n'installait pas ImageMagick : `compare`
était introuvable, la part de pixels valait « ? », et les cinquante cas d'un vrai tournage se rangeaient
en « mesure impossible » - **le job restant vert**. Le compteur de mesures impossibles a dit la panne ;
sans lui, le résumé aurait annoncé « aucun cas ne bouge », et la chaîne aurait eu l'air éprouvée.
