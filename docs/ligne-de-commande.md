# Ligne de commande

VigieChiro Companion s'utilise à la souris, et **aussi** au clavier dans un terminal. Les mêmes
traitements y sont disponibles : importer une carte, qualifier, exporter, déposer. C'est ce qui permet
d'**enchaîner** ce qu'on ferait sinon nuit après nuit dans les écrans.

La commande s'appelle `vigiechiro`. Plusieurs pages de cette documentation la citent déjà, par exemple
`vigiechiro lister-sauvegardes` dans la [référence des écrans](ecrans/index.md).

!!! tip "À qui ça sert, concrètement"
    Si vous dépouillez une ou deux nuits par saison, l'interface suffit largement. Si vous en traitez
    vingt, si vous refaites toujours la même série de gestes, ou si vous voulez qu'un traitement tourne
    la nuit sans vous, la ligne de commande fait cela sans surveillance.

## Comment on l'appelle

Cela dépend de la façon dont vous avez installé l'application.

| Votre installation | Ce que vous tapez |
|---|---|
| Paquet Debian ou Ubuntu (`.deb`) | `vigiechiro lister-passages` |
| Windows (installeur `.msi` ou `winget`) | `vigiechiro lister-passages` |
| Flatpak | `flatpak run fr.echonuit.VigieChiroCompanion lister-passages` (paquet 2.187.0 ou plus récent) |
| AppImage | `./VigieChiroCompanion-<version>-linux-x86_64.AppImage lister-passages` |
| Archive portable (Linux, Windows) | `bin/vigiechiro lister-passages` depuis le dossier décompressé |
| macOS | `/Applications/VigieChiroCompanion.app/Contents/MacOS/vigiechiro lister-passages` |

!!! warning "Sous Flatpak, il faut un paquet 2.187.0 ou plus récent"
    Le paquet Flatpak est construit à partir d'une version publiée, et il ne monte pas de version à
    chaque publication. Si `flatpak run fr.echonuit.VigieChiroCompanion lister-passages` **ouvre la
    fenêtre** au lieu de répondre dans le terminal, c'est que votre paquet est antérieur : les
    commandes lui sont alors inconnues et il les ignore.

    ```bash
    flatpak update --user fr.echonuit.VigieChiroCompanion
    flatpak run fr.echonuit.VigieChiroCompanion --version
    ```

    La seconde ligne dit la version installée : à partir de 2.187.0, elle répond dans le terminal.

!!! note "macOS demande le chemin complet"
    Sur macOS, l'application vit dans un « paquet » `.app` que le terminal ne connaît pas par son nom.
    La commande fonctionne, mais il faut lui donner son chemin entier. Pour éviter de le retaper,
    posez un alias dans votre `~/.zshrc` :

    ```bash
    alias vigiechiro='/Applications/VigieChiroCompanion.app/Contents/MacOS/vigiechiro'
    ```

## Se repérer

`vigiechiro --help` liste toutes les commandes, et chaque commande décrit ses options :

```bash
vigiechiro --help
vigiechiro importer --help
```

Trois exemples pour voir la forme :

```bash
# Ce que la base contient déjà
vigiechiro lister-sites

# Importer une carte SD sur un point d'écoute connu
vigiechiro importer --point 12 --source /media/moi/CARTE_SD

# Sortir la liste des espèces dans un fichier
vigiechiro lister-especes --sortie especes.csv
```

## Ouvrir la fenêtre depuis le terminal

```bash
vigiechiro ihm
```

C'est le même geste que le double-clic. Sans ce mot, `vigiechiro` seul affiche l'aide : l'application
n'ouvre une fenêtre que si on la lui demande.

## Écrire un script qui sait s'il a réussi

Chaque commande rend un **code de sortie**, et c'est ce que votre script doit lire :

| Code | Ce qu'il signifie | Ce qu'il faut en faire |
|---|---|---|
| `0` | l'opération a abouti | continuer |
| `1` | échec d'exécution : disque, réseau, incident inattendu | **l'état est incertain**, vérifier avant de rejouer |
| `2` | refus, ou commande mal écrite | **rien n'a été fait**, l'état est intact |

La différence entre `1` et `2` est celle qui compte : un refus (`2`) laisse vos données exactement
comme elles étaient, alors qu'un échec (`1`) demande de regarder ce qui a été écrit avant de
recommencer.

```bash
if vigiechiro deposer --passage 3; then
    echo "déposé"
else
    echo "rien déposé, code $?"
fi
```

### Une commande peut réussir et vous avertir quand même

Un code `0` dit que l'opération a abouti, pas que tout est parfait. Certaines commandes écrivent en
plus une remarque sur la **sortie d'erreur**, sans échouer pour autant.

C'est le cas quand vous donnez une position à un point d'écoute : l'application regarde dans quel
carré cette position tombe, et vous le dit si ce n'est pas celui que le site déclare.

```bash
POINT=$(vigiechiro ajouter-point --site 3 --code A1 --lat 44.4467 --lon 6.2981)
# stdout : 12
# stderr : Ce point tombe dans le carré 040110 de la grille STOC, alors que ce
#          site déclare le carré 130711. Vérifiez les coordonnées, ou le n° de
#          carré du site.
```

Le point **a bien été créé**, et son identifiant est sur la sortie standard : `POINT=$(...)` marche
comme avant. La remarque part ailleurs, exprès, pour ne pas se retrouver dans votre variable.

Une faute de frappe sur un numéro de carré ne se voit sinon qu'au dépôt, très loin en aval, après
avoir contaminé le nom de tous vos fichiers. Si vous voulez la voir passer, gardez la sortie
d'erreur :

```bash
vigiechiro ajouter-point --site 3 --code A1 --lat 44.4467 --lon 6.2981 2> alertes.txt
```

Ce contrôle **ne demande rien au réseau** : la grille des carrés est embarquée dans l'application. Il
fonctionne donc sans connexion et sans jeton, comme le reste de ces deux commandes.

## Quand un fichier est refusé parce qu'il est trop gros

Le compagnon refuse de lire une entrée démesurée : un journal de carte, une réponse du serveur, une
archive. Les limites sont larges - la plus basse vaut dix-sept mille fois le journal d'une nuit réelle -
mais un cas légitime peut les dépasser.

Le refus vous dit alors quoi taper :

```
Fichier de la carte refusé : « LogPR1925492.txt » fait 41,0 Mo, au-delà des 32,0 Mo admis.
Cette limite se relève en ligne de commande : vigiechiro --reglage import.journal.max-octets=<valeur>.
```

```bash
vigiechiro --reglage import.journal.max-octets=67108864 importer --point 12 --source /media/moi/CARTE
```

L'option se répète si plusieurs limites sont en cause, et une clé mal écrite vous liste celles qui
existent. Elle ne vaut que pour la commande qu'elle accompagne : rien n'est retenu d'une fois sur
l'autre.

!!! note "Ces limites ne sont pas dans les Réglages, et c'est voulu"
    Elles protègent la lecture de fichiers venus du dehors, et personne n'a à choisir une taille de
    réponse serveur pour travailler. Elles se relèvent au cas par cas, quand un fichier légitime le
    demande - y compris si vous avez rencontré le refus **dans la fenêtre** : c'est la même limite, et
    la ligne de commande est l'endroit où elle se relève.

## Le dossier de travail

Toutes les commandes travaillent sur le même dossier que l'application, `Documents/VigieChiro-Companion`.
L'option `--workspace` permet d'en viser un autre :

```bash
vigiechiro lister-passages --workspace /media/disque-externe/vigiechiro-2026
```

!!! warning "Sous Flatpak, il faut aussi **autoriser** le dossier"
    Un Flatpak ne voit que les dossiers qu'on lui accorde. Viser un autre dossier de travail demande
    donc les deux, l'autorisation **et** l'option :

    ```bash
    flatpak run --filesystem=/media/disque-externe/vigiechiro-2026 \
      fr.echonuit.VigieChiroCompanion \
      lister-passages --workspace /media/disque-externe/vigiechiro-2026
    ```

    Sans `--filesystem`, la commande échoue en disant qu'elle ne trouve pas le dossier, alors qu'il
    existe bien : c'est le bac à sable qui le lui cache.

!!! danger "Une seule application à la fois sur un dossier"
    L'application et la ligne de commande **ne peuvent pas** écrire en même temps dans le même dossier
    de travail. La seconde est refusée, et vous dit qui occupe la place. Fermez la fenêtre avant de
    lancer un script qui écrit, ou faites-le travailler sur un autre dossier.
