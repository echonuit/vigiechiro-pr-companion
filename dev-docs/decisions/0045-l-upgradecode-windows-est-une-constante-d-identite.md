# ADR 0045 — L'UpgradeCode et le scope de l'installeur Windows sont des constantes d'identité

- **Statut** : Accepté — 2026-07-21
- **Chantier** : EPIC #2104 / lot 5 (#2110) / winget (#2213)
- **Vérification** : certaine — `DecisionsRespecteesTest#l_installeur_windows_porte_ses_constantes_d_identite`

## Contexte

Le lot 5 délègue la mise à jour aux gestionnaires de paquets. Le premier canal visé est **winget**
(Windows) : l'utilisateur ferait `winget install SebastienNedjar.VigieChiro`, puis `winget upgrade` à
chaque version. Encore faut-il que la nouvelle version **remplace** l'ancienne au lieu de s'installer à
côté.

Deux propriétés de l'installeur MSI gouvernent cela, et toutes deux étaient **laissées au hasard ou au
défaut** :

**1. L'UpgradeCode.** jpackage, sans l'option `--win-upgrade-uuid`, **tire un UUID aléatoire à chaque
build** ([JDK-8214564](https://bugs.openjdk.org/browse/JDK-8214564)). Deux conséquences en chaîne :

- Windows Installer identifie un produit par son UpgradeCode ; un UpgradeCode différent à chaque
  version fait de chacune un **produit distinct**, donc rien ne désinstalle l'ancienne ;
- l'élément WiX `<MajorUpgrade>`, qui retire la version précédente, **n'est ajouté que si**
  `--win-upgrade-uuid` est fourni.

Sans GUID figé, `winget upgrade` aurait donc empilé les versions côte à côte. Le défaut est **invisible
en construisant** (l'installeur se produit et fonctionne) et ne se révèle qu'à la **deuxième** version
installée sur une vraie machine - le pire moment pour le découvrir, puisqu'il touche l'utilisateur.

**2. Le scope.** Par défaut, l'installeur jpackage est **par machine** : il exige une élévation UAC et
des droits administrateur. Or le public visé est un observateur de terrain sur un poste
**institutionnel non-administrateur**.

## Décision

**Figer ces deux propriétés dans le profil `jpackage-windows`, comme des constantes d'identité.**

- `--win-upgrade-uuid 0328d083-bdf7-4e84-95bf-918249478c00` : GUID **généré une fois**, gelé dans le
  pom. **Ne jamais le régénérer** : le changer reviendrait à publier un produit neuf, et le premier
  `winget upgrade` des utilisateurs déjà installés échouerait à les mettre à jour.
- `--win-per-user-install` : installation dans `%LOCALAPPDATA%`, **sans UAC ni admin**.

S'ajoute `--win-menu` (+ groupe `VigieChiro`) : sans entrée au menu Démarrer, l'installeur déposerait
les fichiers sans offrir aucun moyen de lancer l'application.

**Ces choix se prennent AVANT la première soumission publique à winget.** Le scope et l'UpgradeCode
font partie de l'identité que winget mémorise pour reconnaître une mise à jour ; les modifier après
coup romprait la continuité pour tout utilisateur déjà servi.

## Conséquences

**Ce qu'on gagne.** `winget upgrade` remplacera proprement l'ancienne version ; l'installation ne
demande plus de droits administrateur ; l'application est lançable depuis le menu Démarrer.

**Ce qu'on assume.** Le GUID devient une valeur qu'on **ne touche plus jamais** : sa constance est sa
seule raison d'être. Il est commenté comme tel dans le pom, à côté de sa justification.

**Ce qu'on ne peut pas vérifier ici.** Ces options n'ont d'effet que sur une construction **Windows** ;
sous Linux, le profil `jpackage-windows` est inerte. La bonne insertion des arguments dans l'appel
jpackage a été vérifiée sur l'effective-pom (profil forcé actif) ; leur **effet réel** (remplacement de
version, installation sans admin) se vérifie sur une machine Windows, et fait partie des critères
d'acceptation de #2213.

**Ce qui suit.** La soumission du manifeste à `microsoft/winget-pkgs` (première fois à la main, revue
par un modérateur) puis son automatisation sur `release: published` (#2213). La signature de code, qui
ferait taire l'avertissement SmartScreen, reste une question distincte (#2112) : winget accepte un MSI
non signé.
