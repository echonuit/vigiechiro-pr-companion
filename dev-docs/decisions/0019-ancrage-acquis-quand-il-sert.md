# ADR 0019 — L'ancrage s'acquiert quand il sert, pas à un moment décrété

- **Statut** : Accepté — 2026-07-18
- **Chantier** : #1838 (amende [ADR 0016](0016-synchro-rapatrie-des-squelettes-hydrates-a-la-demande.md))
- **Vérification** : humaine — l'acquisition de l'ancrage au moment où il sert est une règle temporelle du déroulé, pas un invariant statique

## Contexte

[ADR 0016](0016-synchro-rapatrie-des-squelettes-hydrates-a-la-demande.md) a séparé trois coutures : structure, observations, audio + ancrage. Elle rattachait l'ancrage à la **réactivation** (#1571), au motif que c'est là que l'audio revient. Le rapprochement était naturel : les deux passent par le même ré-import des `donnees`.

Mais l'ancrage et l'audio ne servent pas au même geste. L'audio sert à **écouter** ; l'ancrage sert à **publier** — il est la cible du `PATCH /donnees/{id}/observations/{indice}` (contrat #1203). Les rattacher revenait à exiger la réactivation d'une nuit pour publier ses corrections, alors que publier ne demande aucun fichier.

Le coût de ce raccourci s'est payé sur les nuits importées par **CSV** (#1565), qui ne portent pas d'ancrage. Leurs corrections étaient toutes écartées ; l'application conseillait « réimportez depuis VigieChiro », et l'action de publication était même **grisée par avance** (#1596). Un utilisateur ayant validé toute une nuit se retrouvait devant un menu inerte lui demandant de réactiver — c'est-à-dire de retrouver l'audio — pour un geste qui n'en avait pas besoin. Symétriquement, l'import proposé par l'écran de validation avait dérivé vers la pagination `donnees` (lente) plutôt que le CSV, précisément pour ramener cet ancrage : une heuristique de préchargement payée par tous, pour un besoin qui ne concerne que ceux qui publient.

## Décision

L'ancrage s'acquiert **au moment où il sert** : la publication des corrections le rapatrie elle-même quand il manque et que la nuit est **rattachée** à une participation, puis publie. Le ré-import se fait avec `remplacer = true`, qui **préserve les validations** de l'observateur : publier ne doit jamais coûter ses corrections à l'utilisateur.

Aucun geste n'acquiert d'ancrage « au cas où ». La réactivation continue de le faire (elle rapatrie déjà les `donnees` pour l'audio, l'ancrage vient avec) ; ce n'est plus le **seul** endroit, et surtout plus un passage obligé.

Corollaire sur les gardes : une IHM ne grise un geste que sur une **impossibilité durable**, pas sur un état que le geste lui-même sait changer. Le grisage de la publication porte donc sur « aucun ancrage **et** nuit non rattachée » — le rattachement, seul verrou que la publication ne peut pas lever. Et un aperçu qui conclut « rien à publier » doit tenir compte de ce que l'envoi va acquérir, sans quoi il refuse exactement le cas qu'on vient de rendre possible.

## Conséquences

- Une nuit importée par CSV devient publiable **sans réactivation** : le chemin le plus court entre « j'ai fini ma revue » et « c'est parti sur la plateforme » ne passe plus par la recherche des fichiers audio.
- Le **premier import** de l'écran de validation (et son équivalent CLI) redevient le **CSV**, avec repli sur les `donnees` : il n'a plus à précharger pour le compte de la publication. Le coût de la pagination est reporté sur le geste qui en a l'usage, et payé une seule fois.
- Les **fils de discussion** du validateur MNHN (#1417) suivent le même sort que l'ancrage, et pour la même raison. Le CSV ne les porte pas ; ils vivent dans les `donnees` et s'attachent d'ailleurs **par ancrage**. Un commentaire de validateur ne sert que si l'on compte modifier quelque chose sur la plateforme, et la publication rapatrie les `donnees` **complètes** : les fils **arrivent avec l'ancrage**. Ils ne sont pas perdus, ils cessent d'être préchargés.
- **Un ré-import, lui, reste sur la voie complète.** La clôture du chantier a montré que la voie rapide y serait destructrice : le CSV ne porte pas l'**avis du validateur**, que le remplacement écraserait donc par du vide (`PreservationValidations` prend délibérément l'avis du **nouvel** import, pour qu'un changement d'avis du MNHN s'affiche), et la suppression des observations emporte les fils **en cascade**. « Réimporter » veut dire « va chercher ce qui a changé côté serveur » : la lenteur y est le prix de ce qu'on vient chercher. Au premier import, il n'y a rien à perdre - c'est là, et là seulement, que la voie rapide sert.
- La publication peut désormais durer : elle passe par une **modale de progression annulable** ([ADR 0010](0010-dialogues-bloquants-sont-des-ports.md)) et non plus par un voile à libellé fixe. En CLI, l'avancement va sur la sortie d'erreur, la sortie standard restant réservée au bilan ([ADR 0014](0014-parite-cli-ihm.md)).
- La confirmation ne peut plus annoncer un total exact : une observation à ancrer peut aussi manquer de certitude. Elle annonce ce qui est prêt et ce qui sera d'abord ancré, plutôt qu'un compte que le bilan démentirait.
- Ce qui reste « sans ancrage » après un envoi n'est plus un oubli de réimport mais une nuit sans participation : le remède affiché change de nature.

## Alternatives écartées

- **Compter sur l'import de l'écran de validation** pour ancrer, comme il le faisait. C'est ce qui a motivé l'issue : une nuit ne devenait publiable qu'après un geste sans rapport apparent, que rien n'imposait de faire et dont l'utilisateur n'avait aucun moyen de deviner le lien. Accessoirement, tout le monde payait la pagination `donnees` (des dizaines de pages) pour un besoin que seuls les publiants ont - la rapidité du CSV, gagnée en #1565, était perdue pour tous.
- **Laisser la publication écarter les non ancrées et documenter le remède.** C'est ce que faisait le code ; le remède était « réimportez » (le geste que la publication sait faire) ou « réactivez » (un geste hors sujet). Faire porter à l'utilisateur une étape que le programme peut mener seul, sur la foi d'un message, n'est pas un contrat, c'est un piège.
- **Acquérir l'ancrage à la première correction saisie.** Trop tôt : corriger n'engage à rien, beaucoup de revues ne seront jamais publiées. On aurait déplacé le pré-chargement, pas supprimé.
