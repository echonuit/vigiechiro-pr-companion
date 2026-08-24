---
type: adr
title: "Un point d'entrée, un mot déclaré ; la console reste à Windows"
status: stable
article: A19
chantier: "#4071, suite de #619 et de #2104"
decided_at: 2026-08-20
verification: certaine
enforced_by:
  - "DecisionsRespecteesTest#le_lanceur_de_ligne_de_commande_est_declare"
verified:
  - by: machine:ci
    at: 2026-08-20
---

# Un point d'entrée, un mot déclaré ; la console reste à Windows

## Contexte

66 classes portent `@Command`, des dizaines de `Cli*Test` les couvrent, un harnais `bats` les traverse
de bout en bout - et aucun emballage publié ne les exposait. Tous ne déclaraient qu'un point d'entrée,
l'interface graphique. Les arguments arrivaient pourtant jusqu'à `main` : `AppRun` passe `"$@"`, le
script Flatpak aussi, le lanceur jpackage également. Ils mouraient à la dernière ligne, dans le
`launch(args)` de JavaFX que `App` ne lit jamais.

Le cadrage initial du chantier a conclu qu'il fallait **deux lanceurs**, et que le lanceur unique
était à refuser. Trois mesures ont renversé la moitié de cette conclusion.

| Ce qu'on a mesuré | Ce que ça rend |
|---|---|
| `$(...)`, `2>`, `System.exit(2)` à travers le lanceur natif jpackage sous Linux | sortie capturée, erreur séparée, `$? = 2` |
| `jpackage --arguments ihm` | `[ArgOptions] arguments=ihm` dans le `.cfg`, passé à `main` **seulement** si personne n'a donné d'argument |
| un second lanceur sans clé `arguments` | il **hérite** de l'`ihm` du principal |

⚠️ La contrainte du sous-système graphique est **propre à Windows** : elle est inscrite dans l'en-tête
PE à l'édition de liens, aucun code Java ne la reprend à l'exécution. Sous Linux et macOS il n'y a pas
d'en-tête PE, donc pas de dilemme. C'est bien pour cette raison que le JDK livre `java` **et** `javaw`.

## Décision

### 1. Un point d'entrée, qui lit un mot ÉCRIT

L'aiguillage lui-même est gardé à part, par `LauncherTest` : cinq cas, dont celui de l'invocation sans
argument, tous vus rouges sur deux mutations avant d'être crus.

`Launcher` aiguille sur le premier argument : `ihm` ouvre la fenêtre, tout le reste part à `Cli`. Le
mot n'est à la charge de personne : `jpackage --arguments ihm` l'écrit dans le `.cfg`, le script
Flatpak porte le même défaut avec `"${@:-ihm}"`.

⚠️ **Une invocation sans aucun argument rend l'usage de la ligne de commande, elle n'ouvre pas la
fenêtre.** Traiter l'absence comme une demande d'interface serait une condition ambiante tenant lieu
de déclaration, la figure que ferme l'ADR 3828 - laquelle autorise en retour ce qui est décidé « sur
des entrées **fournies** », et un mot en est une.

### 2. Deux enveloppes sur un point d'entrée, une seule console

`--add-launcher vigiechiro` pose une seconde porte sur la **même** `main-class`. Elle ne porte
`win-console=true` que sous Windows ; le lanceur graphique ne gagne aucune console, sans quoi le menu
Démarrer en ouvrirait une noire à chaque lancement.

⚠️ Son fichier de propriétés porte `arguments=--help` **obligatoirement** : sans cette clé, le lanceur
hérite de l'`ihm` du principal et `vigiechiro` tapé seul dans une console ouvre la fenêtre.

### 3. Le nom exposé est en minuscules, et ce n'est pas le nom du produit

`vigiechiro`, celui que l'aide de picocli annonce déjà. Le nom du produit reste
`VigieChiroCompanion` : il porte l'identité (menu Démarrer, bundle macOS, winget) que les ADR 0045 et
0047 figent. L'enveloppe existe donc aussi sous Linux et macOS, où aucune console ne l'exige, pour la
seule raison qu'un `bin/VigieChiroCompanion` ne se tape pas sous Unix.

## Ce que la décision NE couvre pas

- **macOS.** Le lanceur vit dans `Contents/MacOS/` : exécutable par chemin complet, pas appelable
  depuis un terminal. Ce qui l'y mettra se décidera avec le cask Homebrew de #2110.
- **Les réglages par propriété JVM.** Trois refus conseillent un `-D` que le produit installé ne peut
  pas passer : c'est #4075, un sujet voisin et distinct.
- **L'écran Réglages.** Rien n'y change : les bornes de lecture et d'extraction restent délibérément
  hors de l'écran, comme leurs doc-comments l'ont décidé.

## Conséquences

- **`java -jar vigiechiro-*-shaded.jar` demande désormais `ihm`** pour ouvrir l'interface. C'est le
  seul changement de surface pour qui lançait le fat-jar à la main.
- **Le harnais `bats` peut viser le lanceur réel** au lieu de la classe, et faire passer les 66
  commandes par le chemin de l'utilisateur, runtime jlink compris - celui où #2299 avait laissé partir
  un paquet incapable de démarrer.
- ⚠️ **Un cas n'a pas d'équivalent sur ce chemin** : `cli_avec_option_jvm` abaisse une borne par `-D`
  pour éprouver un refus (#2732). Le lanceur n'accepte aucune option JVM ; le test **saute en le
  disant** plutôt que de rendre un vert qui n'aurait rien éprouvé (ADR 2748). C'est ce trou qui a fait
  ouvrir #4075.

## Alternatives écartées

- **Un lanceur qui devine son mode** (« zéro argument, donc fenêtre ») : ne résout pas la console
  Windows, et rouvre la famille de défaut que nomme l'ADR 3828.
- **Un exécutable console unique** : la console noire s'ouvre alors à chaque lancement graphique
  depuis le menu Démarrer.
- **`AttachConsole(ATTACH_PARENT_PROCESS)` par FFM** : rendrait la sortie visible depuis un exécutable
  graphique, mais ni `cmd` ni PowerShell n'attendent un processus graphique. Le prompt reviendrait
  avant la sortie et le code de retour serait perdu : rien ne serait scriptable, c'est-à-dire que le
  remède manquerait sa raison d'être.
- **Renommer le produit en minuscules** (`--name vigiechiro`) : toucherait l'identité figée par les
  ADR 0045 et 0047 pour un problème de nom de fichier.
