# ADR 0047 — L'identité de distribution est le projet Echonuit, distincte de l'auteur et de la plateforme

- **Statut** : Accepté — 2026-07-21
- **Chantier** : #2240 (issu de #2213 winget, #2111 Flathub)
- **Fait évoluer** : [ADR 0045](0045-l-upgradecode-windows-est-une-constante-d-identite.md), dont les exemples d'identifiant winget sont remplacés ; prolonge la logique de #2108.

## Contexte

Le produit est né d'une SAÉ pédagogique et s'en est détaché : c'est un logiciel libre pour les
naturalistes. Trois identités s'y superposaient, mal démêlées :

- **l'établissement** (`IUTInfoAix-S201`), présent dans l'app-id Flatpak
  (`io.github.iutinfoaix_s201.VigieChiro`) et le bundle macOS (`fr.univ-amu.vigiechiro`) ;
- **l'auteur** (Sébastien Nedjar), fixé comme `--vendor` par #2108, faute de mieux à l'époque : la
  décision tranchait *personne vs établissement*, **aucune identité projet n'existant encore** ;
- **la plateforme nationale Vigie-Chiro** (MNHN), à laquelle le produit se connecte, dont le nom était
  parfois écrit « VigieChiro » sans tiret et se confondait avec celui du produit.

Deux faits rendaient le moment décisif :

1. **Rien n'était encore soumis** (ni à winget-pkgs, ni à Flathub). Or un `PackageIdentifier` winget et
   un app-id Flatpak sont **permanents** une fois publiés : les changer après coup casse les
   installations existantes. C'était la dernière fenêtre pour les fixer sans coût.
2. Le produit ne s'appelait plus tout à fait ce qu'il faisait : « PR » (Passive Recorder) était devenu
   trop étroit.

## Décision

**Le produit s'appelle « VigieChiro Companion ».** Il perd le « PR » : il dépasse le seul enregistreur
passif.

**L'éditeur est le projet, "Echonuit".** Une identité propre, ni l'établissement ni l'auteur :

| Canal | Identifiant |
|---|---|
| app-id reverse-DNS (Flatpak + AppStream + bundle macOS) | `fr.echonuit.VigieChiroCompanion` |
| winget | `Echonuit.VigieChiroCompanion` |
| `--vendor` / développeur affiché | Echonuit |

**L'app-id s'ancre sur le domaine `echonuit.fr`, pas sur GitHub.** `fr.echonuit.*` se vérifie sur
Flathub par un jeton `https://echonuit.fr/.well-known/org.flathub.VerifiedApps.txt`, non par le compte
de forge. Conséquence voulue : **l'identité de paquet ne dépend pas du nom de l'organisation GitHub**,
qui peut donc être renommée ou déplacée plus tard sans retoucher ni resoumettre les identifiants.

**L'auteur reste crédité.** Le copyright appartient à la personne physique (Sébastien Nedjar, pied du
README) ; l'éditeur/développeur est le projet (Echonuit). Les deux ne se contredisent pas : l'un est le
titulaire des droits, l'autre la marque qui publie.

**La frontière produit / plateforme est mécanique.** « Vigie-Chiro » avec tiret = la plateforme ;
« VigieChiro Companion » = le produit ; « VigieChiro » nu n'apparaît que dans les **noms de fichiers du
build**. Le garde-fou `NomDeLApplicationTest` fait respecter cette règle dans le site utilisateur (son
motif passe de « PR Companion » à « Companion »).

## Conséquences

**Ce qu'on gagne.** Une identité cohérente et non personnelle, invisible dans l'IHM (l'utilisateur voit
le nom du produit, pas l'app-id), et **découplée de l'org GitHub** — le renommage d'org devient un
geste libre, sans effet sur winget ni Flathub.

**Ce qui est reporté (PR B).** Le `--name` de jpackage reste « VigieChiro » : les **noms d'artefacts**
(`VigieChiro-*.msi`…), les chemins d'installation, l'entrée « Ajout/Suppression de programmes » et
l'`Exec`/`Icon` de l'AppImage ne changent **pas** dans cette première étape. Les renommer touche
`release.yml`, les smoke-tests et les docs, et déplace l'entrée ARP — d'où une PR isolée. Tant qu'elle
n'est pas faite, l'entrée ARP (« VigieChiro ») ne correspondra pas au `PackageName` winget
(« VigieChiro Companion ») : à résorber **avant** la soumission winget.

**Ce qui reste à faire hors dépôt.** Créer l'org GitHub `echonuit` et configurer `echonuit.fr` (avant
la soumission Flathub). Les URLs de la fiche AppStream restent sur `IUTInfoAix-S201` jusqu'à ce que le
dépôt bouge.

**Ce qu'on n'a pas fait, et qu'on pourrait.** Le composant `audio-view` garde son groupId Maven
`fr.nedjar.vigiechiro` (le domaine personnel de l'auteur). Le migrer sous `fr.echonuit` pour une
cohérence totale est optionnel et remis à plus tard : une re-publication sur Maven Central sous un
groupId neuf, sans urgence.
