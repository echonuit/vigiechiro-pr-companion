# S10 · Le poste Windows, ce que seule une vraie machine dit

> **Écran propriétaire** : aucun en propre - la session porte sur des **comportements de plateforme**.
> **Features** : aucune ; elle porte sur le **contrat de système de fichiers** et la **console**
> (lot 3 du chantier #3518).
> · **Statut : à jouer.**
> Retour à la [méthode](../index.md).

## Objectif

Le lot 3 a fait tourner la suite entière sous Windows et macOS, chaque mardi. Restent deux choses
qu'une suite de tests **ne peut pas** juger, et qui ont pourtant motivé deux correctifs :

1. **Ce qu'un humain lit** quand le dossier de travail est déjà tenu par une autre instance ;
2. **Ce qu'une vraie console Windows rend** de la couleur de la CLI.

⚠️ Ces deux comportements n'ont **jamais été observés sur une machine Windows réelle**. Ils sont
éprouvés par des tests - c'est la raison d'être du lot - mais un test lit une chaîne, il ne lit pas un
écran.

## Ce que la CI couvre déjà, et qu'il ne faut pas rejouer

| Déjà couvert | Où |
|---|---|
| le verrou est **impératif** sous Windows, la lecture évite l'octet verrouillé | `VerrouWorkspaceTest`, matrice `contrat-fichiers` sur 3 OS |
| la borne du repli de lecture | `VerrouWorkspaceTest.ApresLOctetDuVerrou` (partout) |
| la couleur est éteinte quand la sortie est redirigée, allumée sur un pseudo-terminal | `cli.bats` (#3738) - **sous Linux seulement** |
| toute la suite passe sous Windows et macOS | passage programmé du mardi (#3526) |

Ce que la machine réelle ajoute : **un humain devant l'écran**, et une console Windows véritable -
`bats` ne tourne que sous Linux.

## Environnement

- **Windows 10 ou 11**, session utilisateur standard.
- L'application installée (S9 la pose), et le **fat-jar** disponible pour la partie CLI.
- Un dossier de travail contenant au moins une nuit, pour que la seconde instance ait quelque chose à
  refuser.

## Le script (une case = un fait observable)

### A. Le dossier de travail déjà tenu (#3693, #3714)

- [ ] **A1.** Lancer l'application, la laisser ouverte. Lancer une **seconde** instance sur le
  **même** dossier de travail.
  **Attendu** : la seconde refuse de démarrer, et son message **nomme l'occupant** - un identifiant
  et un poste, pas des parenthèses vides.
  ⚠️ C'est le motif exact de #3693 : sous Windows le verrou est impératif, et la fonctionnalité qui
  nomme l'occupant y était **inerte** - le message affichait « déjà utilisé () ».

- [ ] **A2.** Noter **le texte exact** du message, capture à l'appui.
  **Attendu** : il dit qui, depuis quand, et **quoi faire**. Un refus qui n'indique pas l'action
  suivante renvoie l'utilisateur à lui-même.

- [ ] **A3.** Fermer la première instance. Relancer la seconde.
  **Attendu** : elle démarre. Le verrou relâché ne laisse pas de trace qui bloquerait le prochain
  lancement.

- [ ] **A4.** Tuer la première instance **brutalement** (gestionnaire des tâches), puis relancer.
  **Attendu** : le démarrage réussit. ⚠️ Un verrou de fichier est relâché par le système à la mort du
  processus ; si ce n'était pas le cas, l'utilisateur serait **enfermé dehors** par un plantage, et
  c'est le pire des refus - celui qu'on ne sait pas défaire.

### B. La couleur dans une vraie console (#3738)

⚠️ Trois consoles, trois comportements possibles : `cmd.exe`, **PowerShell**, et **Windows Terminal**.
Le défaut d'origine venait précisément d'une heuristique qui décidait seule ; ne pas se contenter d'une
seule des trois.

- [ ] **B1.** Dans **Windows Terminal**, lancer `vigiechiro --help`.
  **Attendu** : l'aide est **lisible**. Colorisée ou non selon ce que rend la console, mais **jamais**
  de suites de caractères parasites du genre `←[1m`.

- [ ] **B2.** Même chose dans **PowerShell**, puis dans **`cmd.exe`**.
  **Attendu** : idem. ⚠️ `cmd.exe` est le plus susceptible d'afficher les échappements bruts : c'est
  la console la moins capable, et celle qu'un observateur institutionnel a le plus de chances d'ouvrir.

- [ ] **B3.** Rediriger : `vigiechiro --help > aide.txt`, puis ouvrir `aide.txt` dans le Bloc-notes.
  **Attendu** : **aucun** caractère parasite. Le fichier est du texte nu.

- [ ] **B4.** Poser `NO_COLOR=1` puis relancer B1.
  **Attendu** : aucune couleur, et l'aide reste lisible.

## Ce que la session ne couvre pas, et pourquoi

- **`ATOMIC_MOVE` sur une cible ouverte** (#3777) : le motif fondateur du lot, non éprouvé. Il demande
  un **dispositif**, pas une observation - on ne sait pas encore reproduire la condition à la main de
  façon fiable. À traiter par un test, pas par une case.
- **La protection du jeton par les ACL du profil** (#3778) : affirmée par la doc, vérifiée par
  personne. Une case de recette dirait « le fichier existe », pas « aucun autre compte ne peut le
  lire ». Cela demande un second compte, et cela relève d'un port testable plutôt que d'un regard.
