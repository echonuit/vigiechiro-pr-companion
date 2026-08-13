# ADR 3580 - Deux mécanismes de rafraîchissement, parce que ce sont deux questions

- **Statut** : Accepté - 2026-08-13
- **Chantier** : #3580 (lot 4 du chantier #3536)
- **Vérification** : certaine - `ServicePassageTest#poser_verdict_n_annonce_pas_de_mutation_structurelle`
- **Prolonge** : [ADR 3537](3537-un-signal-se-pose-a-l-ecriture.md)

## Contexte

L'[ADR 3537](3537-un-signal-se-pose-a-l-ecriture.md) a livré le signal de mutation : un port que
l'écriture appelle, un compteur observable que l'écran lit. Le lot 4 devait en faire profiter les
cinq écrans qui, faute de mieux, se rechargeaient **au retour de navigation**.

Son issue d'ouverture supposait donc une **substitution** : brancher les cinq écrans sur la
révision, puis retirer `RafraichirAuRetour`, devenu un vestige. C'est la lecture naturelle, et elle
est fausse.

La cartographie a renversé la prémisse. Le contrat du signal est **structurel** : n'émettent que les
écritures qui changent l'inventaire compté - sites, points, passages, observations. Or ce que les
cinq écrans rechargent au retour, ce sont des `update` :

| Ce qui a changé pendant qu'on était masqué | Écriture | Le signal émet-il ? |
|---|---|---|
| un verdict de qualification | `passageDao.update` | **non** |
| un dépôt sur la plateforme | `passageDao.update` | **non** |
| un statut de workflow | `passageDao.update` | **non** |

Retirer `RafraichirAuRetour` aurait cassé les cinq écrans sur **exactement ce pour quoi ils
rechargent**. Le vestige supposé était le seul mécanisme qui couvrait leur cas d'usage réel.

## Décision

**Les deux mécanismes coexistent, et cette coexistence est un choix, pas une transition
inachevée.** Ils répondent à deux questions différentes :

| | `RafraichirAuRetour` | `SuitLaRevision` |
|---|---|---|
| Répond à | « une sous-activité a travaillé pendant que j'étais masqué » | « la donnée a changé, sans que je bouge » |
| Déclencheur | revenir au premier plan | une mutation **structurelle** validée |
| Couvre | un verdict, un dépôt, un statut : des `update` | un import, une synchro, une restauration : des `insert` / `delete` |
| Ne couvre pas | ce qui survient **pendant** qu'on regarde | tout ce qui ne change pas les quatre compteurs |

Un écran d'inventaire déclare donc **les deux**. Les cinq du lot 4 le font.

## Les deux options écartées

**Élargir le signal à toute mutation métier.** Un seul mécanisme, et la question disparaît. Mais on
rend au signal le coût que l'ADR 3537 venait de lui retirer : chaque verdict réveillerait l'accueil
pour relire quatre `COUNT(*)` inchangés. Le contrat structurel n'est pas une limitation subie, c'est
ce qui rend le signal assez peu coûteux pour qu'on l'émette à chaque écriture.

**Un second signal, plus fin** - par entité, par passage. C'est le signal typé que l'ADR 3537 a déjà
écarté, pour une raison qui n'a pas bougé : un émetteur qui doit choisir le bon canal est une façon
de plus de se tromper **en silence**, et le silence est précisément le mode de panne qu'on combat.

## Conséquences

- ⚠️ **Un lecteur futur verra cinq contrôleurs déclarer les deux contrats et conclura à une
  migration inachevée.** C'est la raison d'être de cette ADR. Supprimer `RafraichirAuRetour` est un
  refactoring qui compile, qui laisse la suite verte sur les gardes du lot 4, et qui casse les cinq
  écrans sur les `update` - le défaut ne se verrait qu'en posant un verdict et en revenant.
- La **frontière est le type d'écriture**, pas l'écran. Un nouvel écran qui affiche un inventaire
  déclare les deux ; un écran qui n'affiche qu'un `update` n'a besoin que du retour.
- Le jour où l'on voudrait vraiment n'avoir qu'un mécanisme, la question à trancher n'est pas
  « lequel garder » mais « le signal doit-il couvrir les `update` », et elle se répond par une
  **mesure** du coût de réveil, pas par un raisonnement sur la propreté.
- La documentation utilisateur ne parle **ni de l'un ni de l'autre** : elle dit ce qui se met à jour
  tout seul, et où la liste s'arrête (`docs/ecrans/index.md`).
