# ADR 0043 — La mesure fait foi en CI, pas sur le poste (amende 0037)

- **Statut** : Accepté — 2026-07-20
- **Chantier** : #1641, #1873, #1579, #2129

## Contexte

Le harnais de capture mesure du texte. Or **les polices d'un poste de développement et celles d'un runner de CI ne mesurent pas le texte à l'identique**. Sur ce dépôt, l'écart observé va jusqu'à **6 px** par libellé.

Ce n'est pas une nuance de confort : c'est l'ordre de grandeur des défauts eux-mêmes. Les 18 troncatures relevées par la CI manquaient toutes de **2 à 15 px**.

Trois faits l'ont établi, et chacun a d'abord été pris pour autre chose.

1. Un inventaire local annonçait **12 contrôles** à corriger. Le même inventaire en CI en a montré **18**, dont un bandeau entier que le poste ne voyait pas.
2. Quatre corrections calées au pixel près sur les mesures locales sont **revenues rouges** en CI après y avoir été vertes.
3. [ADR 0037](0037-une-barre-d-actions-plie-elle-ne-tronque-pas.md) affirme, en conséquence observée : « en convertissant `entete-actions`, le **bandeau d'identité** de la fiche site a cessé de tronquer ses cellules (« N° DE CA… » redevenu « N° DE CARRÉ ») ». **C'était vrai sur la machine de son auteur, et faux en CI**, où ces sept cellules coupaient encore libellés et valeurs. Le constat a été corrigé depuis, en resserrant leur marge intérieure.

## Décision

**1. Une observation visuelle locale est une hypothèse, pas un constat.** Elle vaut pour orienter, jamais pour conclure. Toute affirmation de la forme « cet écran ne tronque plus » se vérifie sur un rendu de CI avant d'être écrite — dans une ADR, un bilan, ou le corps d'une issue.

**2. Une correction de dimension prend une marge, elle ne vise pas le chiffre mesuré.** Environ **10 px** au-delà du manque relevé. Une correction exacte est une correction qui repassera au rouge ailleurs.

**3. Pour inventorier, un passage en mode rapport plutôt qu'une série d'allers-retours.** Le verrou s'arrête au premier écran fautif, donc chaque itération contre la CI coûte une dizaine de minutes pour un seul défaut. Rendre le contrôle **non bloquant** le temps d'un unique passage donne la liste complète, mesurée là où elle fait foi. C'est ainsi que les 18 troncatures ont été obtenues d'un coup.

## Conséquences

- La boucle de travail n'est plus « je corrige, je regarde, c'est bon » mais « je corrige, la CI mesure, je conclus ». C'est plus lent, et c'est la seule façon d'être juste.
- Les ADR et bilans qui décrivent un rendu doivent citer **d'où vient** la mesure. [ADR 0037](0037-une-barre-d-actions-plie-elle-ne-tronque-pas.md) ne le faisait pas, d'où son affirmation erronée.
- Le verrou de [ADR 0042](0042-un-apercu-qui-ment-est-refuse.md) tourne **aussi** en local, où il reste utile : ce qu'il y trouve est réel. Ce qu'il n'y trouve pas ne prouve rien.

## Ce qui a été écarté

**Relever la tolérance du verrou pour absorber l'écart de police** (par exemple à 8 px). Cela rendrait la chaîne stable partout — et aveugle aux défauts de 2 à 6 px, qui sont la majorité de ceux trouvés. On préfère un verrou exigeant mesuré au bon endroit à un verrou permissif mesuré partout.

**Figer la police du harnais** (embarquer une fonte et l'imposer aux captures). Rendrait la mesure reproductible, mais ferait mentir les aperçus d'une autre façon : ils ne montreraient plus le rendu que l'utilisateur obtient sur son système. La piste reste ouverte si les allers-retours de CI deviennent coûteux.
