# Paquet Flatpak

Manifeste de l'application pour [Flathub](https://flathub.org), le canal de distribution Linux du
produit (#2111).

Flathub exige que le manifeste vive dans **son propre dépôt** (`flathub/fr.echonuit.VigieChiroCompanion`),
créé à l'acceptation de la soumission. Cette copie est la **source** : elle évolue avec le produit, et
c'est elle qu'on recopie vers le dépôt Flathub. Sans elle, le manifeste dériverait en silence à la
première évolution du packaging.

## Ce que le manifeste fait, et pourquoi

**Il extrait le `.deb` publié** au lieu de construire depuis les sources. Les builds Flathub n'ont
**aucun réseau** : une résolution Maven y est impossible sans déclarer chaque dépendance transitive en
source vérifiée - un fichier généré, énorme, à régénérer à chaque montée de version. C'est le choix de
[Gluon Scene Builder](https://github.com/flathub/com.gluonhq.SceneBuilder), précédent JavaFX
directement comparable, pour la même raison.

Chez nous c'est même plus simple : le fat-jar embarque déjà JavaFX et ses natifs Linux, là où Scene
Builder doit télécharger le SDK JavaFX à côté.

**La mise à jour est automatique.** `x-checker-data` fait détecter les nouvelles versions par le robot
de Flathub, qui ouvre la PR de mise à jour tout seul. Publier une version ne demande donc aucun geste
ici.

## Les permissions, et pourquoi elles sont si étroites

| Permission | Ce qu'elle sert |
|---|---|
| `--filesystem=~/Documents/VigieChiro-Companion:create` | l'espace de travail, **et rien d'autre** |
| `--filesystem=/media`, `--filesystem=/run/media` | les **cartes SD** des enregistreurs, et l'audio **référencé** qui y vit |
| `--share=network` | API Vigie-Chiro, et consultation des versions publiées |
| `--socket=pulseaudio` | écoute des séquences |
| `--socket=wayland`, `--socket=fallback-x11`, `--share=ipc`, `--device=dri` | affichage |

`Workspace.parDefaut()` code le chemin de l'espace de travail **en dur** : on peut donc n'accorder que
lui, plutôt que `--filesystem=home` comme le font beaucoup d'applications. Vérifié dans le bac à
sable : `$HOME` n'y montre que **3 entrées** au lieu de 74, et `~/Documents` que
`VigieChiro-Companion`.

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
    --user --force-clean --install build-dir fr.echonuit.VigieChiroCompanion.yml

flatpak run fr.echonuit.VigieChiroCompanion
```

Ajouter `--disable-rofiles-fuse` si l'environnement n'a pas FUSE (conteneurs, certaines CI) : le build
échoue sinon sur `Failure spawning rofiles-fuse`.

Inspecter ce que le bac à sable voit réellement, ce qui est le seul moyen de vérifier une permission :

```bash
flatpak run --command=sh fr.echonuit.VigieChiroCompanion -c 'ls -A "$HOME"; ls /media/*/'
```

## Monter de version

Ne rien faire : le robot de Flathub s'en charge. En cas de reprise manuelle, mettre à jour `url` et
`sha256` de la source `vigiechiro.deb` - l'empreinte est **publiée avec la release** (fichier
`.deb.sha256`), il n'y a donc rien à recalculer.

## Soumettre à Flathub

Ouvrir une PR sur [`flathub/flathub`](https://github.com/flathub/flathub) avec ce manifeste. La revue
regarde surtout les **permissions** et les **métadonnées AppStream**. Prévoir de justifier
`--filesystem=/media` : c'est inhabituel, et c'est ce qui rend l'application utilisable.
