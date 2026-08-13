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
| `--socket=x11`, `--share=ipc`, `--device=dri` | affichage |

!!! `--socket=x11` et surtout **pas** `--socket=fallback-x11`, qui n'accorde X11 que si Wayland est
**absent**. JavaFX rend par GTK en X11 et ne parle pas Wayland : sur une session Wayland, c'est-à-dire
GNOME et KDE par défaut, le repli retirait X11 précisément là où il est indispensable, et l'application
mourait au démarrage sur `Unable to open DISPLAY`. Sur Wayland, l'hôte expose XWayland, et `x11` y donne
un affichage. Déclarer `x11` **et** `wayland` n'est pas une option : le linter de Flathub refuse cette
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
d'installation que la recette ci-dessus emploie, et celui que recommande la doc de Flathub.

Inspecter ce que le bac à sable voit réellement, ce qui est le seul moyen de vérifier une permission :

```bash
flatpak run --command=sh fr.echonuit.VigieChiroCompanion -c 'ls -A "$HOME"; ls /media/*/'
```

## Jouer les contrôles de Flathub avant eux

`org.flatpak.Builder` embarque `flatpak-builder-lint`, celui-là même que leur CI applique à toute
soumission. Autant le voir rougir ici.

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

Pour le contrôle du dépôt construit, il faut le miroitage des captures, sans quoi deux erreurs
apparaissent qui ne viennent pas du manifeste :

```bash
flatpak run --filesystem="$PWD" --cwd="$PWD" org.flatpak.Builder \
    --user --force-clean --disable-rofiles-fuse \
    --mirror-screenshots-url=https://dl.flathub.org/media \
    --repo=repo build-dir fr.echonuit.VigieChiroCompanion.yml

flatpak run --filesystem="$PWD" --cwd="$PWD" \
    --command=flatpak-builder-lint org.flatpak.Builder repo repo
```

!!! Ce contrôle-là peut rendre `appstream-external-screenshot-url` et
`appstream-remote-icon-not-mirrored` **même quand tout va bien** : l'`appstreamcli` embarqué écrit des
chemins relatifs avec `media_baseurl` sur la racine, tandis que le linter attend le préfixe absolu dans
chaque élément. C'est un décalage d'outillage, pas un défaut du manifeste ; l'infrastructure de Flathub,
elle, passe. Vérifié le 2026-08-13 sur une construction d'essai réussie.

## Monter de version

Ne rien faire : le robot de Flathub s'en charge. En cas de reprise manuelle, mettre à jour `url` et
`sha256` de la source `vigiechiro.deb` - l'empreinte est **publiée avec la release** (fichier
`.deb.sha256`), il n'y a donc rien à recalculer.

## Soumettre à Flathub

⚠️ **Lire d'abord les [exigences](https://docs.flathub.org/docs/for-app-authors/requirements) en
entier, y compris la politique sur l'IA générative.** Elle interdit qu'une PR de soumission soit
« generated, opened, or automated using AI tools or agents », interdit aussi les réponses rédigées par
un LLM dans le fil, et vise plus largement les applications dont le code ou la documentation sont
assistés par IA. Sanction annoncée : refus sans examen, et bannissement permanent en cas de récidive.
La [9760](https://github.com/flathub/flathub/pull/9760) a été fermée par un mainteneur sur ce motif,
après avoir coché la case « I have read and followed all the Submission requirements » sans que la page
ait été lue. **Ce point décide de la recevabilité avant toute question technique.**

Ensuite seulement, la mécanique :

- PR sur [`flathub/flathub`](https://github.com/flathub/flathub), base **`new-pr`**, titre littéral
  `Add fr.echonuit.VigieChiroCompanion` ;
- le manifeste **et** le metainfo **à la racine** : si tous les fichiers sont en sous-dossier, le robot
  ferme ;
- corps repris **mot pour mot** du gabarit, cases cochées. Un corps rédigé sur mesure sans checklist a
  fait fermer la [9432](https://github.com/flathub/flathub/pull/9432) en 33 minutes, sans qu'aucun
  humain ne la lise ;
- la **vidéo** est obligatoire : case cochée et URL sur la même ligne ou dans les deux suivantes. Y
  écrire « N/A » déclenche la fermeture au même titre qu'une case vide.

!!! Le robot revalide **à chaque passage**, toutes les deux heures, et le brouillon n'exempte de rien :
son contrôle anti-spam s'exécute avant la vérification du statut. On n'ouvre donc qu'une fois tout prêt.

La revue humaine, elle, regarde surtout les **permissions** et les **métadonnées AppStream**. Prévoir de
justifier `--filesystem=/media` : c'est inhabituel, et c'est ce qui rend l'application utilisable.
