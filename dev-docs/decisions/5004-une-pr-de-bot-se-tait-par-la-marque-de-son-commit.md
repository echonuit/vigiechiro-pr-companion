---
type: adr
title: "Une PR de bot se tait par la marque de son commit, pas par son jeton"
status: stable
article: A5
chantier: "#6213 (la CI rend un verdict qu'elle n'a pas produit), lot #5004"
decided_at: 2026-10-14
verification: humaine
verification_note: "la marque vit dans deux lignes de workflow qu'aucun garde ne lit, et son effet ne se voit que sur la forge, au passage d'une release : `gh run list --branch flatpak/maj-<version>` doit y rendre zéro run"
verified:
  - by: human:nedseb
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
relations:
  amende: ["0041-un-check-requis-gouverne-la-branche", "4571-une-fusion-sans-verdict-se-refuse"]
---

# Une PR de bot se tait par la marque de son commit, pas par son jeton

## Contexte

Deux ateliers ouvrent une PR avec le `GITHUB_TOKEN` puis la fusionnent dans le même pas, parce que
`main` impose le passage par une PR : `capture-vues.yml` pour la galerie d'aperçus, et `flatpak.yml`
pour le manifeste, appelé par le train de release.

Les [ADR 0041](0041-un-check-requis-gouverne-la-branche.md) et
[4571](4571-une-fusion-sans-verdict-se-refuse.md) posent qu'**aucun workflow n'est déclenché par un
événement produit avec ce jeton**, et en tirent qu'un check requis resterait muet sur ces PR.

La forge a cessé de se conduire ainsi fin août, sans qu'aucun commit du dépôt ne le dise. Relevé sur
les branches du job flatpak (#5004) :

| Versions | Runs par PR | Conclusion |
|---|---|---|
| 2.186.0 à 2.188.0 | 0 | - |
| 2.189.0 | 5 | `action_required` |
| 2.190.0 à 2.199.0 | 6 à 9 | `failure`, sans aucun job ; `action_required` sur la 2.198.0 |

Les PR d'aperçus, ouvertes par le même acteur et fusionnées de la même façon, n'ont **aucun** run :
leur commit de branche porte la marque de saut de CI, celui des PR flatpak ne la portait pas.

Le dommage tenait à la course. Sur la 2.199.0, les neuf runs sont nés à 06:27:03, la PR a été
fusionnée et sa branche supprimée à 06:27:04, et tous sont morts rouges à 06:27:05 sans avoir rien
vérifié. Le 14 octobre, ils ont été pris pour un échec de publication, alors que le Flatpak avait été
construit, démarré et publié.

## Décision

1. **Une PR qu'un atelier ouvre et fusionne dans le même pas porte la marque de saut de CI sur le
   commit de sa branche**, et pas seulement sur le sujet du squash. GitHub lit la marque sur le
   commit de tête de la PR : celle du squash ne protège que la poussée sur `main`.
2. **Ce que la PR transporte s'éprouve dans l'atelier, avant son ouverture** : le Flatpak y est
   construit et démarré, les aperçus y sont rendus, et le check `titre` y est publié à la main. On
   n'attend pas les workflows de la PR avant de fusionner.
3. **Ce qui fait taire une PR de bot s'écrit « la marque », jamais « le jeton »**, dans les ateliers
   comme dans la documentation.

Deux autres voies ont été écartées. **Attendre les checks avant de fusionner** rendrait une
vérification réelle, mais elle porterait sur un changement de manifeste que rien dans ces checks ne
construit, au prix de la CI complète et d'un train retardé d'autant. **Supprimer les runs rouges
après la fusion** effaçait le symptôme et gardait la course : le porteur l'a refusé.

## Conséquences

**Sur les ADR 0041 et 4571, la conclusion tient et la cause change.** Un check requis resterait muet
sur ces PR, puisque la marque les fait taire, et il les bloquerait. Mais ce silence est désormais un
choix du dépôt, réversible, et non une règle de la forge : retirer la marque ferait rapporter les
checks, au prix de la CI complète sur chaque PR de bot. Ce levier n'existait pas dans leur
raisonnement ; c'est à #5722 de le peser, et cette ADR ne le tranche pas.

**Les poussées directes de bot restent indécises.** Le commit du CHANGELOG porte lui aussi la marque :
la mesure du 4 octobre sur #5004 n'a trouvé aucune poussée de bot sans elle, si bien que la marque et
la règle du jeton y sont confondues. Rien ici n'en décide.

**L'événement `release` n'est pas concerné.** Que la Release dé-brouillonnée au jeton ne déclenche
rien ([ADR 2744](2744-la-publication-part-a-heure-fixe.md),
[4103](4103-un-canal-de-distribution-ne-depend-pas-d-un-geste.md)) n'a pas été démenti.

**Un atelier neuf qui ouvre et fusionne une PR doit poser la marque de lui-même** : aucun garde ne le
refuse.

## Vérification

Humaine. La marque tient en une ligne dans chacun des deux ateliers, et rien ne la lit. Son effet se
constate sur la forge au passage d'une release :

```bash
gh run list -R echonuit/vigiechiro-pr-companion --branch flatpak/maj-<version> --json databaseId --jq length
```

Éprouvé le 14 octobre 2026 par une release lancée à la main sur le commit du correctif (#6252) : la
v2.199.1 a ouvert la PR #6254, fusionnée cinq secondes plus tard, avec **zéro** run.
