# S11 · La commande sur un poste Linux (`.deb`, Flatpak, AppImage)

> **Écran propriétaire** : aucun en propre - la session se joue **avant** l'application, dans le
> système et dans un terminal.
> **Features** : aucune ; elle porte sur l'**exposition de la ligne de commande** (#4071, suite de
> l'EPIC #2104).
> · **Statut : à jouer.**
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

## Ce que cette session ne prouve pas

- **macOS** : la commande y est installée mais hors du `PATH` (#4088). Rien ici ne la concerne.
- **Windows** : couvert par S9 (winget) et S10 (le poste Windows).
- **Le contenu** des commandes : ce sont les E2E `bats` qui l'éprouvent, sur le lanceur livré. Cette
  session vérifie qu'on peut les **atteindre**, pas ce qu'elles font.
