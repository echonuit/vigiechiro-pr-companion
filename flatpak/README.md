# Paquet Flatpak

Manifeste de l'application (#2111), source du paquet publié sur le dépôt Flatpak auto-hébergé du
projet ([`dev-docs/ci-cd-release.md`](../dev-docs/ci-cd-release.md#dépôt-flatpak-auto-hébergé-2111)).

## Ce que le manifeste fait, et pourquoi

**Il extrait le `.deb` publié** au lieu de construire depuis les sources. Les builds Flatpak n'ont
**aucun réseau** : une résolution Maven y est impossible sans déclarer chaque dépendance transitive en
source vérifiée - un fichier généré, énorme, à régénérer à chaque montée de version. C'est le choix de
Gluon Scene Builder, précédent JavaFX directement comparable, pour la même raison.

Chez nous c'est même plus simple : le fat-jar embarque déjà JavaFX et ses natifs Linux, là où Scene
Builder doit télécharger le SDK JavaFX à côté.

**La mise à jour est automatique.** Le bloc `x-checker-data` est lu par `flatpak-external-data-checker`
dans `flatpak.yml`, qui ouvre la PR de mise à jour une fois le nouveau paquet construit et démarré avec
succès. Publier une version ne demande donc aucun geste ici.

## Les permissions, et pourquoi elles sont si étroites

| Permission | Ce qu'elle sert |
|---|---|
| `--filesystem=~/Documents/VigieChiro-Companion:create` | l'espace de travail, **et rien d'autre** |
| `--filesystem=/media`, `--filesystem=/run/media` | les **cartes SD** des enregistreurs, et l'audio **référencé** qui y vit |
| `--share=network` | API Vigie-Chiro, et consultation des versions publiées |
| `--socket=pulseaudio` | écoute des séquences |
| `--socket=x11`, `--share=ipc`, `--device=dri` | affichage |

!!! `--socket=x11` et surtout **pas** `--socket=fallback-x11`, qui n'accorde X11 que si Wayland est
**absent**. JavaFX rend par GTK en X11 et ne parle pas Wayland : sur une session Wayland, c'est-à-dire
GNOME et KDE par défaut, le repli retirait X11 précisément là où il est indispensable, et l'application
mourait au démarrage sur `Unable to open DISPLAY`. Sur Wayland, l'hôte expose XWayland, et `x11` y donne
un affichage. Déclarer `x11` **et** `wayland` n'est pas une option : `flatpak-builder-lint` refuse cette
combinaison. `verifie-affichage-flatpak.sh` garde cette règle.

`Workspace.parDefaut()` code le chemin de l'espace de travail **en dur** : on peut donc n'accorder que
lui, plutôt que `--filesystem=home` comme le font beaucoup d'applications. Vérifié dans le bac à
sable : `$HOME` n'y montre que **trois entrées** (`Documents`, `.local`, `.var`), là où le dossier
personnel en compte des dizaines, et `~/Documents` ne montre que `VigieChiro-Companion`.

Le rapport se mesure en une commande (voir plus bas) et se démode ; c'est la liste des trois entrées
qui porte la démonstration, pas le chiffre d'en face. Au 2026-08-13 : 3 contre 76.

!!! Les deux chemins de montage sont nécessaires : `udisks2` monte sous `/media/<utilisateur>/` sur
Debian et Ubuntu, sous `/run/media/<utilisateur>/` sur Fedora et dérivées. Sans les deux, la moitié du
parc ne peut rien importer.

**Conséquence assumée** : la surcharge `-Dvigiechiro.workspace` ne fonctionne pas dans le bac à sable,
puisque seul le chemin par défaut est accordé. Déplacer son espace de travail demande d'accorder le
nouveau chemin (`flatpak override --user --filesystem=…`), ou d'utiliser le `.deb`.

**Seconde conséquence, depuis l'[ADR 0048](../dev-docs/decisions/0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md)** :
l'audio peut désormais être **référencé** là où il vit, sans copie. Il n'est alors écoutable que si son
emplacement compte parmi les chemins accordés. L'espace de travail et les points de montage le sont,
donc un disque externe ou une carte SD conviennent. Un partage réseau ouvert depuis le gestionnaire de
fichiers ne convient **pas** : GNOME et KDE le montent sous `/run/user/<utilisateur>/gvfs/`, hors de
tout chemin accordé. Une nuit référencée là se présente comme non écoutable, exactement comme un
support débranché - et le réveil décrit par l'ADR la rend écoutable dès qu'elle redevient joignable,
sans rien redemander à l'utilisateur.

Référencer un NAS sous Flatpak demande donc d'accorder ce que le gestionnaire de fichiers utilise
(`flatpak override --user --filesystem=xdg-run/gvfs`), ou de monter le partage soi-même
(`/etc/fstab`, `systemd.mount`) puis d'accorder son point de montage. Le `.deb` n'a aucune de ces
limites.

## Construire et essayer en local

```bash
flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo
flatpak install --user flathub org.flatpak.Builder

cd flatpak
flatpak run --filesystem="$PWD" --cwd="$PWD" org.flatpak.Builder \
    --user --force-clean --disable-rofiles-fuse --install \
    build-dir fr.echonuit.VigieChiroCompanion.yml

flatpak run fr.echonuit.VigieChiroCompanion
```

!!! **Ajouter `--disable-rofiles-fuse` est le cas NORMAL, pas l'exception.** Sans lui, la construction
échoue sur :

```
fusermount: file descriptor 4 is not a socket, can't send fuse fd
Error: Failure spawning rofiles-fuse, exit_status: 256
```

Ce n'est pas une histoire de FUSE manquant : la panne survient sur un poste de bureau ordinaire qui en
dispose. La cause est que **le constructeur est lui-même un flatpak**. `org.flatpak.Builder` tourne dans
son propre bac à sable, d'où il ne peut pas parler au `fusermount` de l'hôte. Or c'est exactement le mode
d'installation que la recette ci-dessus emploie.

Inspecter ce que le bac à sable voit réellement, ce qui est le seul moyen de vérifier une permission :

```bash
flatpak run --command=sh fr.echonuit.VigieChiroCompanion -c 'ls -A "$HOME"; ls /media/*/'
```

## Faire tourner le linter avant publication

`org.flatpak.Builder` embarque `flatpak-builder-lint`, le contrôle de référence pour un manifeste
Flatpak. Autant le voir rougir ici, avant de publier.

```bash
cd flatpak
flatpak run --filesystem="$PWD" --cwd="$PWD" \
    --command=flatpak-builder-lint org.flatpak.Builder \
    manifest fr.echonuit.VigieChiroCompanion.yml
```

**Sortie vide et code 0 = zéro défaut.** Une sortie vide n'est un zéro que si l'on a vu l'outil savoir
rougir : glisser un `--filesystem=home` dans une copie du manifeste doit lui faire rendre
`finish-args-home-filesystem-access`. Sans ce contrôle, on ne distingue pas « rien à signaler » de
« dispositif muet ».

## Monter de version

Ne rien faire : `flatpak.yml` s'en charge, à chaque `workflow_dispatch`, en lisant le bloc
`x-checker-data` du manifeste. En cas de reprise manuelle, mettre à jour `url` et `sha256` de la source
`vigiechiro.deb` - l'empreinte est **publiée avec la release** (fichier `.deb.sha256`), il n'y a donc
rien à recalculer.
