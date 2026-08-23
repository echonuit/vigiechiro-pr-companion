# ADR 4296 - On compare les deux bouts du clip, parce que le début ne bruite pas

- **Statut** : Accepté - 2026-08-23
- **Chantier** : #4296, EPIC #4295
- **Prolonge** : [ADR 4274](4274-on-compare-la-derniere-image-pas-le-chemin.md)
- **Vérification** : certaine - `.github/assets/compare-tournages.sh`

Son `--auto-test` porte les deux cas qui l'éprouvent : **« la dernière image est bien la fin : 0 % »**
et **« et la première est bien le début : 100 % »**, sur un clip qui vire du blanc au noir comparé à un
clip noir de bout en bout. Inverser les deux extractions, ou n'en garder qu'une, les fait rougir.

## Contexte

L'[ADR 4274](4274-on-compare-la-derniere-image-pas-le-chemin.md) ne retenait que l'image finale, et
écrivait sa contrepartie : on compare la destination, pas le chemin.

⚠️ Cette limite mordait précisément là où l'[ADR 4166](4166-un-etat-ne-se-juge-que-contre-son-contraire.md)
demande le plus. Celle-ci exige qu'un cas qui fait juger un état montre **cet état et son contraire dans
le même clip** : le geste offert puis empêché, le bouton grisé à côté d'un bouton actif. Pour ces cas,
ce qui fait le cas est le contraste entre deux moments, et l'image finale n'en gardait qu'un. **Plus un
cas respectait 4166, moins la comparaison le couvrait.**

## Ce qui a été mesuré

Sur la paire de tournages du même commit qui avait servi à l'ADR 4287, 51 cas :

| | plancher médian | pire cas |
|---|---|---|
| dernière image | 0,008 % | 0,809 % |
| **première image** | **0,000 %** | **0,000 %** |

La première image est donc **plus stable que la dernière**, sur les 51 cas sans exception. L'application
vient de se monter, aucune animation n'a commencé.

⚠️ **Stable ne suffisait pas.** Une mesure toujours nulle peut être parfaitement fidèle ou parfaitement
aveugle, et les deux se lisent pareil. Vérifié : les premières images de deux cas **différents**
diffèrent de 2,4 à 3 %. Elles distinguent donc bien les cas, au lieu de montrer le même écran d'accueil
à tout le monde.

## Décision

**On compare les deux bouts du clip** : sa première image et sa dernière, chacune contre son propre
plancher.

Le fichier de planchers porte donc quatre colonnes - le cas, le plancher du début, celui de la fin, et
le nombre de paires. Un fichier à trois colonnes est l'ancien format : le plancher qu'il porte est celui
de la fin, et celui du début reste **inconnu** plutôt que supposé nul.

Le classement se fait sur le **plus grand des deux rapports** : un cas qui a bougé à l'un ou l'autre
bout remonte.

## Conséquences

⚠️ **Les images du début ne sont produites que si le début a bougé.** Son plancher valant 0,000 % sur
les 51 cas, les produire systématiquement ferait cinquante montages strictement identiques - du bruit
qui noierait les deux ou trois qui comptent. Vérifié sur une comparaison réelle : 51 montages de fin,
**zéro** montage de début.

**Le chemin reste hors de portée**, et l'ADR 4274 garde donc sa contrepartie : un changement au milieu
d'un clip, entre ses deux bouts, ne laisse toujours aucune trace. Comparer deux bouts n'est pas
comparer une trajectoire.

## Ce qui a été écarté

**Traiter le plancher du début comme nul par construction.** Il a été mesuré à 0,000 % sur une seule
paire ; c'est un résultat, pas une propriété. Il est donc stocké et accumulé comme l'autre, et le nombre
de paires reste lisible (#4297).
