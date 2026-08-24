---
type: adr
title: "Une mesure dit ce qu'elle n'a pas pu lire"
status: stable
article: A3
chantier: "#3627 puis #3634, suites de la sauvegarde (#3624)"
decided_at: 2026-08-12
verification: certaine
enforced_by:
  - "ServiceSauvegardeTest#dossier_illisible_est_un_refus_et_non_un_incident"
verified:
  - by: machine:ci
    at: 2026-08-12
relations:
  prolonge: ["2213"]
---

# Une mesure dit ce qu'elle n'a pas pu lire

## Contexte

Le garde d'espace de la sauvegarde (#3572) mesure ce qu'elle emportera avant de copier. Il s'appuyait
sur `ArborescenceFichiers.octets`, qui parcourt l'arborescence et compte **zéro** pour un fichier dont
la taille ne se lit pas.

⚠️ **Le constat de départ de #3627 était partiellement faux, et le sonder l'a montré.** Il annonçait
qu'un droit refusé comptait pour zéro. Mesuré :

```
tout lisible      : 6000 octets (attendu 6000)
dossier interdit  : LEVE UncheckedIOException : java.nio.file.AccessDeniedException
```

Un **dossier** illisible ne se tait pas : `Files.walk` lève. Le zéro silencieux ne survient que sur une
**course** - un fichier qui disparaît entre le parcours et la lecture de sa taille - donc un cas étroit
et transitoire. #3627 a corrigé le plus petit des deux défauts, et refermé l'issue sur l'autre.

Car `UncheckedIOException` n'est **pas** une `IOException` : elle traversait le
`catch (IOException | SQLException)` de `sauvegarderComplet`, et `VerdictCli` la rangeait dans sa
branche par défaut. L'utilisateur lisait « Échec », code **1**, état incertain, alors que le garde
s'exécute **avant la première copie** et que rien n'avait été touché.

## Décision

**Une mesure rend ce qu'elle a lu et ce qu'elle n'a pas pu ouvrir**, et ne lève pas pour autant.
`peser` rend une `Pesee(octets, illisibles)`. Chaque appelant en fait ce que son usage demande :

| Appelant | Ce qu'il fait des illisibles | Pourquoi |
|---|---|---|
| `InventaireSauvegardes` | les **ignore** (`octets`) | il **affiche** une taille ; refuser d'afficher parce qu'un dossier a résisté serait absurde |
| le garde de sauvegarde | **refuse**, en les nommant | il **décide**, et une mesure partielle ne permet pas de conclure qu'il y a la place |

C'est le partage que l'[ADR 3574](3574-un-effacement-dit-son-contrat-dans-son-nom.md) a fait pour
l'effacement : un geste, deux besoins, et c'est **l'appelant** qui choisit, pas l'utilitaire qui devine.

### Le refus précède le calcul de la place

Annoncer « il manque N Go » sur une mesure incomplète enverrait l'utilisateur libérer de l'espace pour
un problème de **droits**. Le refus tombe donc avant la comparaison, avec sa propre cause et son propre
geste : rendre le dossier lisible, ou retirer la nuit concernée.

### Un parcours explicite, et non `Files.walk`

`Files.walk` lève sur le premier dossier qu'il ne peut pas lister et **interrompt le flux** : on
n'apprend ni ce que pèse le reste, ni combien de dossiers ont résisté. Le parcours est donc une file
explicite, qui note le dossier fermé et continue - même choix que `effacerAuMieux`, qui ne s'arrête pas
au premier récalcitrant.

⚠️ Le test « est-ce un dossier ? » porte `NOFOLLOW_LINKS`, et lui seul. C'est ce que fait `Files.walk`
par défaut, et sans lui **un lien vers un dossier ancêtre ferait tourner ce parcours sans fin**.

## Conséquences

- Un dossier illisible devient un **refus** (code 2) au lieu d'un incident (code 1). « Je n'ai pas pu
  regarder, et je n'ai rien abîmé » n'est pas « quelque chose a mal tourné, va vérifier tes données ».
- `InventaireSauvegardes` cesse de casser sur un dossier illisible : il affiche ce qu'il sait. C'était
  un second défaut latent du même parcours, que personne n'avait rencontré.
- Le port `TailleFichier` existe pour que le cas de course soit **éprouvable** : `Files.size` ne lève
  pas sur commande, un dossier en `chmod 000` fait échouer le parcours et non la pesée, et un lien mort
  est écarté par `isRegularFile` avant qu'on l'atteigne. Sans lui le test serait conditionnel, et un
  test qui s'abstient rend le même vert que celui qui s'exécute.
- ⚠️ Une décision **de ne pas faire** : la mesure ne relit pas ce qu'elle a manqué, et n'essaie pas de
  distinguer un droit refusé d'un support démonté. La cause système est rapportée telle quelle ; c'est
  elle qui dit à l'utilisateur quoi débloquer.
- Une mesure partielle qui se présenterait comme complète est ce que l'[ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)
  appelle un dispositif qui peut ne rien vérifier sans le dire.
