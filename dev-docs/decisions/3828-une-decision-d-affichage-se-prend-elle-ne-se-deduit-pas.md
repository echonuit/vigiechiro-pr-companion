# ADR 3828 - Une décision d'affichage se prend, elle ne se déduit pas

- **Statut** : Accepté - 2026-08-16
- **Chantier** : #3828, lot 4 des suites #3802
- **Vérification** : certaine - `EtatTraitementVigieChiroInstantTest#le_fuseau_du_lecteur_compte`

## Contexte

Trois défauts d'affichage, découverts séparément, se sont révélés être **le même** : une décision que
personne n'avait prise, et que la machine ou l'habitude avait prise à sa place.

| Ce que l'utilisateur voyait | Qui avait décidé |
|---|---|
| une aide colorisée sous Windows, nue sous Linux (#3738) | l'heuristique de picocli |
| `analyse EN COURS (le Fri, 3 Jul 2026 19:00:00 GMT)` (#3678) | le serveur VigieChiro |
| `21h00` dans un écran, `21:00` dans un autre, `21:00` sans « à » dans quatre (#3821) | l'habitude |

⚠️ Aucun des trois n'était un bug au sens ordinaire : rien ne plantait, aucun test ne rougissait. Ils
se ressemblent par ce qui **manquait** - un endroit où quelqu'un ait tranché.

## Décision

**Ce que l'utilisateur lit est décidé par le produit, à un endroit nommé.** Trois conséquences, une par
défaut.

### 1. La couleur est choisie, et l'utilisateur a le dernier mot dans les DEUX sens

`CouleurCli` décide, sur des entrées **fournies**. `NO_COLOR` éteint, `FORCE_COLOR` allume, et
`NO_COLOR` l'emporte : un refus explicite prime sur une demande explicite.

⚠️ La règle antérieure disait « `NO_COLOR` donne le dernier mot à l'utilisateur ». Ce n'était pas faux,
c'était **incomplet dans le sens rassurant** - il n'y avait de dernier mot que pour éteindre.

### 2. Un instant venu d'ailleurs se traduit avant d'être montré

`EtatTraitementVigieChiro.depuis` lit les **deux** formes que la plateforme rend, et les dit en français
dans le fuseau du lecteur.

⚠️ L'UTC n'est pas un détail de présentation : « le 3 juillet à 19 h » n'est pas la même heure pour
l'observateur que pour le serveur, et c'est l'observateur qui décide s'il attend ou revient demain.

⚠️ **Ce qu'on ne sait pas lire reste affiché tel quel.** Perdre l'information vaudrait moins que
l'afficher mal : un lecteur peut interpréter une chaîne étrange, jamais une absence.

### 3. Deux formes d'horodatage, nommées par leur USAGE

`Horodatage.dansUnePhrase` et `dansUnTableau`. Regardées **en contexte** plutôt qu'en liste, les
divergences n'étaient pas une négligence : le « à » lit bien dans une phrase et mal dans une colonne,
et les quatre sites sans « à » étaient précisément des tableaux. Le besoin était réel ; il manquait de
le **nommer**.

⚠️ **C'est le nom qui empêche de se tromper, pas la discipline.** `LISIBLE`, `QUAND`, `FORMAT_NUIT` -
les noms qu'on trouvait sur ces constantes - n'apprennent rien à celui qui choisit.

## Ce que la décision NE couvre pas

Deux autres familles de formateurs, aux exigences **opposées**, restent où elles sont :

- **noms de fichiers** (`yyyyMMdd_HHmmss`) : stables, jamais localisés, triés lexicalement ;
- **lecture de fichiers tiers** (ThLog, `Locale.ROOT`) : fidèles au **producteur**, pas à nous.

Les rapatrier serait un défaut, pas un remède. C'est écrit dans le doc-comment d'`Horodatage` pour que
la prochaine campagne d'unification ne les avale pas.

## Conséquences

- **Le nommage a corrigé un site tout seul** : `nuitLisible` alimentait une **cellule de tableau** en
  écrivant « à ». Personne ne l'avait vu ; il a suffi d'avoir à choisir entre deux noms.
- ⚠️ **Et il m'a repris sur un autre.** J'avais classé le refus du verrou en « phrase » ; son test a
  rougi en rappelant que **#3640 l'avait délibérément aligné** sur la table de choix d'une sauvegarde,
  « deux écrans plus loin ». Une décision documentée que j'allais renverser sans la voir. Elle vit
  désormais dans le doc-comment de `dansUnTableau`.
- **Trois aperçus ont changé**, et exactement les bons : les trois de la modale de reconstruction ont
  maigri de 189 à 506 octets - la disparition du « à ». Celui du choix de sauvegarde n'a **pas** bougé,
  car ce site écrivait déjà sans.
- ⚠️ **Le fuseau est un paramètre, jamais `systemDefault()` en dur** dans ce qui se teste :
  `fuseau-alternatif` rejoue la suite sous `America/Cayenne` (ADR 3450), et figer « 21:00 » y ferait
  rougir un test qui ne constate aucun défaut.

## Alternatives écartées

- **Une seule forme d'horodatage** : mauvaise quelque part par construction - le « à » alourdit une
  colonne, son absence appauvrit une phrase.
- **Unifier les 35 formateurs** : aurait cassé le tri des noms de fichiers et la lecture des ThLog.
- **S'en tenir à `NO_COLOR`** : laisse sans recours l'utilisateur qui lit dans un pager ou un journal
  de CI qui rend l'ANSI - trois situations ordinaires où la couleur est possible et refusée.
