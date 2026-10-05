---
type: adr
title: "Le Point Fixe n'impose aucune distance entre deux points : la fiche d'un site la donne sans la juger"
status: stable
article: A23
heuristiques:
  - "nielsen-2"
  - "nielsen-9"
chantier: "#5839, lot 26 du chantier #5596, trouvé à la passe 7 de sa clôture"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/sites/view/LibelleProximiteTest.java"
  - "src/test/java/fr/univ_amu/iut/sites/view/ScenarioFicheSiteTest.java"
verification_note: "le premier exige la phrase de distance en entier, à dix, cent et huit cent cinquante mètres ; le second ouvre une fiche dont deux points sont à cent mètres et exige la même phrase sans icône. Remettre le texte d alerte fait rougir les deux, remettre la seule icône fait rougir le second. Rien ne tient l absence d un seuil ailleurs que sur la carte d un point"
relations:
  complete: ["5688-une-position-se-saisit-d-une-seule-facon"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Le Point Fixe n'impose aucune distance entre deux points : la fiche d'un site la donne sans la juger
## Contexte

La fiche d'un site affiche, sur la carte de chaque point, sa distance au point le plus proche. Depuis
#154, cette ligne passait en avertissement sous 200 m, puis elle a gagné une icône et une phrase :
« trop rapprochés pour le protocole, vérifiez la position ou la saisie GPS ». Le code appelait ce seuil
un « garde-fou de protocole », sans source.

L'[ADR 5688](5688-une-position-se-saisit-d-une-seule-facon.md) a posé à côté un second voisinage, 40 m à
la création d'un point, qui est le rayon dans lequel le portail rattache une position à un point. Un
point créé à 100 m d'un autre ne recevait donc aucun avertissement à la création, puis sa carte
s'affichait « trop rapprochés ».

Le porteur a demandé d'où venait le premier seuil. Lu dans le code du portail le 5 octobre 2026 :
`checkDistanceBetweenPoints(200)` n'existe que dans le service du protocole **Carré**, qui exige de 5 à
13 points par carré et que Companion ne traite pas. Le service du **Point Fixe** n'impose aucune distance
entre deux points : il ne connaît que le rayon de 40 m autour de ses centres de maille, espacés de 500 m.

## Décision

**1. La distance s'affiche, elle ne se juge pas.** La carte d'un point dit « à 100 m du point le plus
proche », dans le style de sa description, quelle que soit la valeur. Ni seuil, ni icône, ni mention du
protocole.

**2. Il ne reste qu'un voisinage, celui de la création.** 40 m, tenu par `PointVoisin` et par l'ADR
5688. C'est le seul moment où signaler un voisin sert : avant que le doublon existe.

**3. L'écart de 15 m qui dit « le même endroit » garde sa valeur.** `DistanceGeo` le justifiait par sa
petitesse devant le seuil retiré ; il se justifie par le rayon de 40 m.

## Ce qui a été écarté

**Requalifier l'alerte en « vérifiez la saisie ».** Une alerte sans règle derrière elle reste un
reproche. Une coordonnée fausse se voit sur la carte du site, et le voisinage de la création la signale
déjà quand elle tombe sur un point existant.

**Aligner la carte sur 40 m.** Elle redirait après coup ce que la création a dit, à un observateur qui a
choisi d'enregistrer quand même.

**Garder le seuil pour le jour où le protocole Carré serait traité.** Ce jour-là, la règle se posera par
protocole, avec sa source. La garder d'avance la fait appliquer au seul protocole qui ne l'a pas.

## Conséquences

Une coordonnée saisie de travers, qui place un point à 60 m d'un autre, ne déclenche plus rien sur la
fiche. C'est assumé : le protocole permet cette disposition.

Le seuil venait d'une lecture du portail que personne n'avait refaite en quatre mois. Une règle écrite
« de protocole » dans le code cite désormais le service du portail qui la porte, ou ne s'écrit pas.
