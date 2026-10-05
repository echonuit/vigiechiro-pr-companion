## Context

La règle d'import vit déjà dans le socle (`commun/model/ImportApresReleve`), ainsi que le relevé
(`commun/model/SuiviTraitement`). Seules les phrases sont dans `lot/viewmodel/FormatsTraitement`, d'où
la vue d'un passage ne peut pas les lire : une feature ne dépend pas d'une autre.

## Goals / Non-Goals

**Goals** : le même geste et les mêmes mots sur les deux écrans, écrits une fois.

**Non-Goals** : une carte « Traitement » dans la vue du passage, un sondage de la plateforme, un
changement de l'écran de lot ou de la ligne de commande.

## Decisions

**Les phrases passent dans `commun/viewmodel`.** `FormatsTraitement` y est déplacé tel quel et devient
public. L'autre voie, que la vue du passage compose un `TraitementViewModel` de `lot`, ferait dépendre
`passage` de `lot`.

**Un objet de vérification, pas une croissance du ViewModel du passage.** `VerificationDuTraitement`
(`passage/viewmodel`) tient le suivi et l'import optionnels, joue le relevé hors du fil JavaFX, et rend un
`RetourOperation` prêt pour le bandeau. `PassageViewModel` ne reçoit que ce retour : il est déjà au
plafond de taille.

**Le résultat va au bandeau, pas dans une zone neuve.** C'est la réponse du porteur. Le bandeau porte
la phrase d'état, puis la phrase d'import quand il y en a une.

**La sévérité suit l'issue.** Importé : succès. En cours, planifié, déjà importé : information. Analyse
en échec côté plateforme, import en échec, relevé impossible : erreur.

**Le bouton grisé explique.** Il suit le patron de « Voir la participation » : une enveloppe porte
l'infobulle, qui survit à la désactivation.

## Risks / Trade-offs

Le bandeau ne garde pas l'état : à la réouverture de la vue, rien ne dit où en était l'analyse. C'est
voulu, la vue ne sonde pas ; l'écran de lot garde sa carte et son dernier état connu.
