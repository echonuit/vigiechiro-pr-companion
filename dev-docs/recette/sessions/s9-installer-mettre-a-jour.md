# S9 · Installer et mettre à jour (winget)

> **Écran propriétaire** : aucun en propre - la session se joue **avant** l'application, dans le
> système.
> **Features** : aucune ; elle porte sur la **distribution** (#2213, lot 5 de l'EPIC #2104).
> · **Statut : à jouer.**
> Retour à la [méthode](../index.md).

## Objectif

Vérifier ce qu'aucun test ne peut atteindre : que le paquet winget **s'installe sans droits
d'administration**, que l'application **démarre** ensuite, et surtout que `winget upgrade`
**remplace** la version en place au lieu de s'installer à côté d'elle.

⚠️ Ce dernier point est le **motif d'origine** du chantier winget. L'`UpgradeCode` a été figé et le
scope fixé à `user` **avant** la première soumission (ADR 0045) précisément pour que la mise à jour
remplace ; sans quoi « la première mise à jour livrée aux utilisateurs casse ». Cette propriété n'a
**jamais été observée** : elle demande deux versions servies et une vraie machine Windows.

La CI en couvre déjà une partie, et il ne faut pas la rejouer ici : `winget.yml` installe le MSI puis
le **lance 45 s** sur un runner Windows, journal relu. Ce que la machine réelle ajoute, c'est
**l'enchaînement de deux versions** et ce que l'utilisateur voit dans son système.

## Environnement

- **Windows 10 ou 11**, à jour, avec `winget` disponible (`winget --version` répond).
- Une session **utilisateur standard**, **sans droits d'administration** : c'est le public visé, un
  observateur sur un poste institutionnel qu'il n'administre pas. Jouer la session en administrateur
  ne prouverait pas ce qu'on cherche.
- L'application **non installée** au départ (`winget list Echonuit.VigieChiroCompanion` ne rend rien).

!!! tip "La fixture existe grâce au chantier lui-même"
    winget conserve **deux** versions du paquet (`2.34.2` et une plus récente). On peut donc installer
    délibérément l'ancienne, puis monter - ce qui était impossible tant qu'une seule version était
    servie, et qui rendait cette session inécrivable.

    ```powershell
    winget install Echonuit.VigieChiroCompanion --version 2.34.2
    ```

    Si la 2.34.2 finit par sortir de winget (`max-versions-to-keep: 5`), prendre les **deux plus
    anciennes** versions servies : `winget show Echonuit.VigieChiroCompanion --versions` les liste.

## Le script (une case = un fait observable)

**Étape 1 · L'installation ne demande rien à l'administrateur**

1. `winget install Echonuit.VigieChiroCompanion --version 2.34.2`

- **S9-01** · *hors-portée: winget sur une vraie machine Windows : le banc filme une scène JavaFX, pas un gestionnaire de paquets* · L'installation se termine sans **aucune** invite UAC, en session standard.
- **S9-02** · *hors-portée: le menu Démarrer et « Applications installées » de Windows, hors du banc* · L'application apparaît dans le menu Démarrer sous **« VigieChiro Companion »** - le nom
  affiché, pas un identifiant technique.
- **S9-03** · *hors-portée: winget sur une vraie machine Windows : le banc filme une scène JavaFX, pas un gestionnaire de paquets* · `winget list Echonuit.VigieChiroCompanion` rend la version **2.34.2**.

**Étape 2 · Elle démarre pour de vrai**

1. Lancer l'application depuis le menu Démarrer.

- **S9-04** · *hors-portée: l'enchaînement de DEUX versions servies. L'écran existe, mais ce que le cas prouve est sa cause - une montée de version que le banc ne peut pas provoquer : filmer l'écran seul serait le clip convaincant et creux de l'ADR 4142* · La fenêtre s'ouvre et l'écran d'accueil s'affiche, sans message d'erreur.
- **S9-05** · *hors-portée: le dossier personnel d'un utilisateur Windows après désinstallation, hors du banc* · L'application est installée **dans le dossier personnel** (`%LOCALAPPDATA%`), pas dans
  `Program Files` : le scope `user` est bien celui qui a été servi.
- **S9-14** · *geste: annonce-d-une-mise-a-jour* · *carton: une nouvelle version est publiée, et l'application l'apprend* · Le bandeau d'annonce en haut de la fenêtre nomme les deux versions **et** dit de
  **fermer l'application avant d'installer** (#3457). Sans cette phrase, l'installation lancée
  fenêtre ouverte ne se termine pas, et rien n'en donne la raison.

    ⚠️ Le numéro sort de la suite volontairement : les cases 06 à 13 sont citées ailleurs (#3621
    tranche sur **S9-07**), et les renuméroter rendrait ces renvois faux en silence.

**Étape 3 · La mise à jour REMPLACE, elle ne double pas**

C'est la case pour laquelle cette session existe.

1. Fermer l'application - ce que **S9-14** vérifie que le produit sait désormais demander.
2. `winget upgrade Echonuit.VigieChiroCompanion`

- **S9-06** · *hors-portée: winget sur une vraie machine Windows : le banc filme une scène JavaFX, pas un gestionnaire de paquets* · La commande propose la montée et l'exécute, toujours **sans invite UAC**.
- **S9-07** · *hors-portée: le menu Démarrer et « Applications installées » de Windows, hors du banc* · ⚠️ **Paramètres → Applications installées** ne contient **qu'une seule** entrée
  « VigieChiro Companion ». Deux entrées = l'`UpgradeCode` n'a pas joué, et c'est un **défaut
  bloquant** : chaque version deviendrait un produit Windows distinct.
- **S9-08** · *hors-portée: le menu Démarrer et « Applications installées » de Windows, hors du banc* · Le menu Démarrer ne porte **qu'un seul** raccourci.
- **S9-09** · *hors-portée: winget sur une vraie machine Windows : le banc filme une scène JavaFX, pas un gestionnaire de paquets* · `winget list Echonuit.VigieChiroCompanion` rend la **nouvelle** version, et une seule
  ligne.

**Étape 4 · Les données de l'utilisateur ont survécu**

1. Rouvrir l'application.

- **S9-10** · *hors-portée: l'enchaînement de DEUX versions servies. L'écran existe, mais ce que le cas prouve est sa cause - une montée de version que le banc ne peut pas provoquer : filmer l'écran seul serait le clip convaincant et creux de l'ADR 4142* · Le dossier de travail, les sites et les nuits d'avant la montée sont **toujours là** :
  la mise à jour a remplacé le programme, pas les données.
- **S9-11** · *hors-portée: l'enchaînement de DEUX versions servies. L'écran existe, mais ce que le cas prouve est sa cause - une montée de version que le banc ne peut pas provoquer : filmer l'écran seul serait le clip convaincant et creux de l'ADR 4142* · **☰ → « À propos »** affiche le **nouveau** numéro de version.

**Étape 5 · La désinstallation ne détruit pas le travail**

1. `winget uninstall Echonuit.VigieChiroCompanion`

- **S9-12** · *hors-portée: winget sur une vraie machine Windows : le banc filme une scène JavaFX, pas un gestionnaire de paquets* · L'application disparaît du menu Démarrer et de « Applications installées ».
- **S9-13** · *hors-portée: le dossier personnel d'un utilisateur Windows après désinstallation, hors du banc* · Le **dossier de travail de l'utilisateur** (base et journaux, dans le dossier personnel)
  est **intact** : désinstaller le programme n'efface pas les nuits.

## Hors périmètre de cette session

Les **archives portables** et l'**AppImage**, livrées par #2107, ne sont couvertes par aucune case ici
et par aucun test ailleurs. Ce n'est pas un oubli de rédaction : c'est un constat, posé à la clôture de
#2213, et qui appartient à un autre chantier.
