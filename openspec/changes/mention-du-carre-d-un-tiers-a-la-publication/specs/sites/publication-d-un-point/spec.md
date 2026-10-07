## ADDED Requirements

### Requirement: La publication d'un point dit le carré d'un tiers avant le geste

Quand le carré est marqué comme appartenant à un autre observateur, l'écran SHALL afficher la mention
« Ce carré est celui d'un tiers : votre point s'ajoutera aux siens. » à deux endroits : sous les actions
d'une carte de point qui offre le lien « Publier sur Vigie-Chiro », et sous la case « Publier ce point
sur Vigie-Chiro après l'enregistrement » de la modale de création.

La mention MUST NOT empêcher de publier, griser le lien ou la case, ni demander une confirmation. Elle
MUST NOT nommer le propriétaire. Elle MUST NOT s'afficher sur un carré sans marque, qu'il soit à
l'utilisateur, jamais relié à la plateforme, ou de propriétaire inconnu. Elle MUST NOT s'afficher là où
le geste n'est pas offert : carte d'un point déjà publié ou venu de la plateforme, modale en édition.
Absente, elle MUST NOT réserver de place.

*Vérifié par* : `PublicationSurLeCarreDUnTiersTest`, qui lit la mention sur trois carrés (tiers, à soi,
jamais relié) et compare l'empêchement de chacun avec et sans la marque ;
`PublicationSurLeCarreDUnTiersViewTest`, qui la lit sur la carte et dans la modale, constate le lien
ouvert et la case cochable, et refuse un texte coupé. Ignorer la marque, l'inverser ou en faire un
empêchement fait rougir les deux.

#### Scenario: Le lien d'une carte, sur le carré d'un tiers

- **WHEN** l'utilisateur connecté ouvre la fiche d'un carré marqué « tiers » qui porte un point à publier
- **THEN** la carte de ce point affiche la mention sous ses actions, et le lien « Publier sur Vigie-Chiro » reste ouvert

#### Scenario: La case de la modale, sur le carré d'un tiers

- **WHEN** il ouvre la création d'un point sur ce carré et saisit une position
- **THEN** la mention se lit sous la case, et la case se coche

#### Scenario: Un carré à soi

- **WHEN** il ouvre la fiche ou la modale d'un carré relié et sans marque
- **THEN** aucune mention ne s'affiche, et le geste est offert comme avant

#### Scenario: Un carré jamais relié

- **WHEN** il ouvre la fiche d'un carré que rien ne relie à la plateforme
- **THEN** aucune mention ne s'affiche, et le lien garde le motif de gris qu'il avait déjà
