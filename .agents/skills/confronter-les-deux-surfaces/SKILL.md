---
name: confronter-les-deux-surfaces
description: Use at closure pass 2, once the integration audit is done, to check that a business capability introduced by the chantier exists on both the JavaFX screen and the picocli command line. Two inventories are compared, never two examples.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Confronter les deux surfaces

## Loi d'airain

```
ON CONFRONTE DEUX INVENTAIRES, JAMAIS DEUX EXEMPLES
```

Vérifier qu'**un** geste a son équivalent en ligne de commande produit une parité de façade : vraie
sur le cas qu'on a regardé, fausse dès qu'on resserre le filtre comme on le fait en vrai.

## Annoncer

« J'utilise la compétence confronter-les-deux-surfaces sur les capacités de <le chantier>. »

## Ce que la passe compare

L'application expose **deux surfaces** sur le même domaine : l'IHM JavaFX et la ligne de commande
picocli, scriptable et sans écran. Une capacité livrée d'un seul côté crée une asymétrie et une dette
que rien ne signale.

Ce qui se compare est une **capacité métier** : une opération, une option, un format d'export, une
règle de gestion. Pas un détail de présentation : une pastille de statut ni une mise en page n'ont
d'équivalent en ligne de commande.

## Fonction de garde

```
1. INVENTORIER les capacites metier introduites ou changees par le chantier.
2. DRESSER     DEUX listes : tout ce que l ecran offre, tout ce que la commande
               accepte. Pas un exemple de chaque cote.
3. LIRE        les options au nom VOISIN avant de conclure a une absence.
4. ALIGNER     tout de suite si l ecart est petit ; sinon OUVRIR l issue sur-le-champ,
               rattachee par --parent au chantier qui traite sa cause.
5. MOTIVER     un « sans objet cote CLI » par une mesure, jamais par une impression.
```

## La mesure qui a établi la loi d'airain

À la clôture de l'EPIC #2790, la passe avait constaté que l'export ZIP existait des deux côtés et que
le critère « Lieu » manquait en ligne de commande. Elle s'était arrêtée là.

Une question d'usage posée après coup, « et à plus de 90 % de confiance ? », a montré que la puce
« Proba » manquait aussi. La dette annoncée était à moitié décrite, et il a fallu une seconde issue
pour la compléter (#2971).

Un écart trouvé n'est donc pas un inventaire fait. La passe s'arrête quand les deux listes ont été
parcourues en entier, pas quand elle a trouvé quelque chose.

## Le nom voisin, qui fait conclure trop vite

Une option peut exister sous un nom qui ressemble, et désigner autre chose.

`--certitude` porte la certitude déclarée par l'observateur ; la probabilité rendue par Tadarida est
une autre donnée. Les deux se ressemblent à l'oral et n'ont rien à voir dans les données. Conclure à
l'absence sans avoir lu l'aide de l'option produit une dette imaginaire, et conclure à la présence
produit une parité qui n'existe pas.

## Le signal concret

Un **service de domaine** nouvellement appelé par un ViewModel mais par aucune commande de
`fr.univ_amu.iut.cli.commande` signale une capacité présente d'un seul côté.

Les deux surfaces partagent les mêmes services : la parité se joue au niveau des services exposés,
pas du code d'IHM.

## « Sans objet » est une conclusion, et elle se motive

Un chantier purement présentationnel, ou qui ne touche pas au domaine, n'a rien à exposer en ligne de
commande. Cela se note explicitement plutôt que de laisser la case vide, et cela s'appuie sur une
mesure : zéro fichier de `src/` dans le delta du chantier en est une.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai vérifié, le geste existe des deux côtés » | Un geste n'est pas un inventaire. Ce sont deux listes qui se comparent |
| « J'ai trouvé l'écart » | Un écart trouvé n'est pas un inventaire fait. #2790 s'est arrêtée au premier |
| « L'option n'existe pas en ligne de commande » | Existe-t-elle sous un nom voisin ? `--certitude` n'est pas la probabilité |
| « C'est de la présentation, sans objet » | Une conclusion se motive par une mesure, pas par une impression |
| « L'écart est petit, je le noterai » | Petit, on l'aligne ; sinon l'issue s'ouvre maintenant, avec son parent |
