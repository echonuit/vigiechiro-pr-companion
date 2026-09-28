# S11 · La commande sur un poste Linux (`.deb`, Flatpak, AppImage)

> **Écran propriétaire** : aucun en propre - la session se joue **avant** l'application, dans le
> système et dans un terminal.
> **Features** : aucune ; elle porte sur l'**exposition de la ligne de commande** (#4071, suite de
> l'EPIC #2104).
> · **Statut : partielle sur Ubuntu 26.04 avec GNOME ; menu Debian à revoir et Flatpak 2.194.0 absent (#5548).**
> Retour à la [méthode](../index.md).

## Objectif

Vérifier ce que la CI **ne peut pas** atteindre : qu'un poste de bureau réel, après installation,
expose la commande **et** garde son entrée de menu.

La CI en couvre déjà beaucoup, et il ne faut pas le rejouer ici : le lanceur de l'app-image et celui
de l'archive portable sont ouverts à chaque PR, leur version leur est demandée, et les 111 E2E `bats`
traversent le lanceur livré. Ce que la machine réelle ajoute tient en deux choses : un **bureau** avec
un menu d'applications, et le **double-clic**, qui n'est pas un appel de programme.

Un défaut connu empêche la vérification en conteneur, et c'est pour cela que cette session existe :
`xdg-desktop-menu` échoue là où aucun menu n'est inscriptible, ce qui laisse le paquet en
`half-configured` (#4081). Sur un vrai bureau, ce chemin fonctionne - mais personne ne l'a observé
depuis que le postinst du dépôt a remplacé celui de jpackage.

## Environnement

- Une distribution avec un **environnement de bureau** (GNOME ou KDE), pas un serveur ni un conteneur.
- L'application **non installée** au départ (`dpkg -l vigiechirocompanion` ne rend rien,
  `flatpak list | grep -i vigie` non plus).
- Le `.deb`, le Flatpak et l'AppImage de la **même version publiée**.

## Étape 1 · Le paquet Debian

1. Installer : `sudo apt install ./vigiechirocompanion_<version>_amd64-x64.deb`.
2. Ouvrir le menu des applications et chercher « VigieChiro ».
3. Ouvrir un terminal **neuf** (le `PATH` d'un terminal déjà ouvert peut être périmé).

- **S11-01** · *hors-portée: un gestionnaire de paquets sur un bureau réel : le banc filme une scène JavaFX déjà lancée* · L'installation se termine **sans erreur** : `dpkg -l vigiechirocompanion` rend un statut
  `ii`, et non `iF`.
- **S11-02** · *hors-portée: une entrée de menu d'un environnement de bureau, que le banc ne peut ni inscrire ni ouvrir* · L'application apparaît dans le menu sous **« VigieChiro Companion »**, avec son icône.
- **S11-03** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `vigiechiro --version` répond depuis le terminal, sans donner de chemin.
- **S11-04** · *hors-portée: un double-clic sur une entrée de menu. Filmer la fenêtre qui s'ouvre ne prouverait rien de sa CAUSE, et serait le clip convaincant et creux de l'ADR 4142* · Un double-clic sur l'entrée de menu ouvre **la fenêtre**, et non un terminal.
- **S11-05** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `vigiechiro ihm` ouvre la fenêtre depuis le terminal.
- **S11-06** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `vigiechiro lister-sites` répond en **texte** dans le terminal, sans ouvrir de fenêtre.

## Étape 2 · Ce que la désinstallation emporte

1. `sudo apt remove vigiechirocompanion`.

- **S11-07** · *hors-portée: un gestionnaire de paquets sur un bureau réel : le banc filme une scène JavaFX déjà lancée* · `vigiechiro` n'existe plus dans le terminal (`command -v vigiechiro` ne rend rien), et
  aucun lien mort ne subsiste dans `/usr/bin`.
- **S11-08** · *hors-portée: une entrée de menu d'un environnement de bureau, que le banc ne peut ni inscrire ni ouvrir* · L'entrée a disparu du menu des applications.

## Étape 3 · Le Flatpak

1. Installer depuis le dépôt du projet, puis chercher l'application dans le menu.

- **S11-09** · *hors-portée: un double-clic sur une entrée de menu. Filmer la fenêtre qui s'ouvre ne prouverait rien de sa CAUSE, et serait le clip convaincant et creux de l'ADR 4142* · Le double-clic sur l'entrée de menu ouvre **la fenêtre**.
- **S11-10** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `flatpak run fr.echonuit.VigieChiroCompanion` **sans argument** ouvre la fenêtre, et
  n'affiche pas l'aide de la ligne de commande.
- **S11-11** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `flatpak run fr.echonuit.VigieChiroCompanion lister-sites` répond en texte.

## Étape 4 · L'AppImage

1. Rendre le fichier exécutable, puis le lancer par double-clic depuis le gestionnaire de fichiers.

- **S11-12** · *hors-portée: un double-clic sur une entrée de menu. Filmer la fenêtre qui s'ouvre ne prouverait rien de sa CAUSE, et serait le clip convaincant et creux de l'ADR 4142* · Le double-clic ouvre **la fenêtre**.
- **S11-13** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `./VigieChiroCompanion-<version>-linux-x86_64.AppImage --version` répond en texte dans
  un terminal.

## Étape 5 · Retrouver une fiche et diagnostiquer un incident

Sur le poste qui vient d'installer le paquet Debian, créer un workspace jetable avec
`RECETTE_CLI=$(mktemp -d)`. Utiliser `vigiechiro --workspace "$RECETTE_CLI"` pour chaque commande
ci-dessous ; supprimer ce dossier après la session. La première commande crée la base locale et
charge le référentiel livré avec l'application.

- **S11-14** · *hors-portée: la réponse est dans le terminal* · `vigiechiro --workspace "$RECETTE_CLI" lien-espece --code Pippip` rend le code 0 et écrit seulement l'URL PNA de la pipistrelle commune.
- **S11-15** · *hors-portée: la réponse est dans le terminal* · `vigiechiro --workspace "$RECETTE_CLI" lien-espece --code inconnu` rend le code 2, explique que le taxon est inconnu sur stderr et ne donne aucune URL sur stdout.
- **S11-16** · *hors-portée: la réponse est dans le terminal* · `vigiechiro --workspace "$RECETTE_CLI" lien-participation --passage 42` rend le code 2 et indique qu'aucune participation n'est liée au passage local.

Pour éprouver le diagnostic, créer **un second** workspace jetable avec
`RECETTE_INCIDENT=$(mktemp -d)`, puis y écrire une fausse base avec
`printf 'ceci n est pas une base SQLite' > "$RECETTE_INCIDENT/vigiechiro.db"`.
Ne jamais altérer le workspace de travail habituel.

- **S11-17** · *hors-portée: la réponse et le journal sont hors de l'application* · `vigiechiro --workspace "$RECETTE_INCIDENT" lister-sites` rend le code 1. Le message sur stderr désigne `"$RECETTE_INCIDENT/logs"` sans afficher de pile Java ; le fichier `vigiechiro-*.log` le plus récent y porte la trace de l'incident.

## Essai sur le poste Ubuntu, les 25 et 28 septembre 2026

Le 25 septembre, les sommes SHA-256 des paquets `.deb` et AppImage publiés en 2.194.0 ont été
vérifiées. Le poste était sous Ubuntu 26.04 avec GNOME. Un Flatpak 2.193.0 y était déjà installé :
il a été retiré temporairement, sans effacer ses données, puis restauré à la même version.

| Cases | Observation |
|---|---|
| S11-01, S11-03, S11-05, S11-06 | Le `.deb` 2.194.0 s'installe avec le statut `ii`. `/usr/bin/vigiechiro` pointe vers `/opt/vigiechirocompanion/bin/vigiechiro` ; `--version` donne 2.194.0, `ihm` ouvre la fenêtre et `lister-sites` répond « Aucun site enregistré. » dans un workspace jetable. |
| S11-02, S11-04, S11-08 | Le menu et son double-clic ont été observés une première fois alors que le Flatpak 2.193.0 portait le même nom. Cette observation ne prouve pas que l'entrée venait du `.deb`. Après retrait des deux installations, les fichiers d'entrée ont disparu du système, mais le menu GNOME n'a pas été vérifié. Ces trois cases restent à rejouer avec le `.deb` seul. |
| S11-07 | Après `apt remove`, la commande et son lien sous `/usr/bin` ont disparu. |
| S11-12, S11-13 | Le double-clic sur l'AppImage 2.194.0 ouvre la fenêtre ; `--version` répond en texte avec le numéro 2.194.0. |
| S11-14 à S11-17 | Sur le `.deb` 2.194.0, les codes sont 0, 2, 2 et 1. L'espèce rend seulement l'URL PNA ; les refus n'écrivent aucune URL ; l'incident désigne `logs/` sans pile sur stderr, et le fichier `vigiechiro-0.log` contient `SQLITE_NOTADB`. |
| S11-09 à S11-11 | Le Flatpak disponible sur le dépôt officiel reste en 2.193.0. Son entrée de menu ouvre la fenêtre ; son lancement sans argument ouvre aussi la fenêtre et `lister-sites` répond en texte. Ces observations ne valident pas les cases pour la version publiée 2.194.0. |

Le [job de publication Flatpak de la 2.194.0](https://github.com/echonuit/vigiechiro-pr-companion/actions/runs/35826167683/job/107069355439)
a échoué sur l'authentification du `git push` vers `echonuit/flatpak.git`. L'issue [#5548](https://github.com/echonuit/vigiechiro-pr-companion/issues/5548)
porte ce défaut. Rejouer les cases Flatpak sur une même version que le `.deb` et l'AppImage après
sa correction. Rejouer aussi S11-02, S11-04 et S11-08 avec le `.deb` comme seule installation.
Le Flatpak 2.193.0 et ses données utilisateur ont été restaurés ; le `.deb` a été retiré.

## Ce que cette session ne prouve pas

- **macOS** : la commande y est installée mais hors du `PATH` (#4088). Rien ici ne la concerne.
- **Windows** : couvert par S9 (winget) et S10 (le poste Windows).
- Le contenu des autres commandes : les E2E `bats` l'éprouvent sur le lanceur livré. Les cases
  S11-14 à S11-17 rejouent les nouveaux gestes de ce chantier sur un poste installé.
