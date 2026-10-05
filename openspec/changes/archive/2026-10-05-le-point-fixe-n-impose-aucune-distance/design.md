## Context

`CartePoint.SEUIL_PROXIMITE_METRES` vaut 200 depuis #154. `CartePoint.tropProche()` le lit, et
`CartesPointsSite` en tire un style d'alerte, une icône et une phrase (#1379, #2221). Le lot 20 de #5596 a
posé à côté `PointVoisin.RAYON_METRES`, 40 m, pour la création.

## Decisions

**Retirer, et non requalifier.** Garder un seuil sous un autre nom (« points proches, vérifiez la
saisie ») laisserait une alerte sans règle derrière elle. Une faute de saisie GPS se voit sur la carte du
site, et le voisinage à 40 m la signale déjà à la création. Écarté aussi : aligner le seuil à 40 m sur la
carte, qui redirait après coup ce que la création a déjà dit et que l'observateur a choisi d'ignorer.

**Garder la distance.** Elle ne coûte rien, elle aide à relire un site, et elle ne juge pas.

**Le commentaire de `DistanceGeo` se réécrit.** Il justifiait l'écart de 15 m (« même endroit ») par sa
petitesse devant le seuil de 200 m. La valeur ne change pas ; sa justification s'appuie désormais sur le
rayon de 40 m.

## Risks / Trade-offs

Une coordonnée saisie de travers qui place un point à 60 m d'un autre ne déclenche plus rien sur la fiche.
C'est assumé : à 60 m, deux points d'un même carré sont une configuration que le protocole permet.
