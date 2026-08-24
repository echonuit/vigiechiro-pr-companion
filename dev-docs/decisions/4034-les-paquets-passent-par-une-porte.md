---
type: adr
title: "Les paquets passent par une porte, et le cache de fichiers a un périmètre"
status: stable
article: A3
chantier: "#4034, clôture du chantier des films et de la CI (#4013)"
decided_at: 2026-08-20
verification: certaine
enforced_by:
  - ".github/scripts/verifie-apt.sh"
verified:
  - by: machine:ci
    at: 2026-08-20
---

# Les paquets passent par une porte, et le cache de fichiers a un périmètre

## Contexte

Trois étapes de trois workflows ont pendu le même jour, sur la même ligne : `apt-get update`. Le
miroir du runner rendait `Ign:` sur toutes ses sources, APT basculait sur l'archive amont, et
l'attente durait jusqu'au butoir du job - y compris **sur `main`**, où `paquet` a été annulé deux fois
de suite à quarante minutes alors qu'il passait en onze minutes le matin même.

⚠️ Le remède avait été appliqué à **une** des trois. Les deux autres ont continué de pendre le
lendemain. C'est le motif de ce chantier : une leçon apprise à un seul endroit.

## Décision

**1. Aucune étape n'appelle `apt-get` directement.** `installer-paquets.sh` borne les délais
(`Acquire::http::Timeout`), reprend les téléchargements coupés (`Acquire::Retries`), tente deux fois,
et n'installe pas ce qui est déjà présent sur le runner. Jamais de `-qq` : c'est lui qui a rendu la
première panne indéchiffrable.

Elle **borne**, elle ne ressuscite pas un miroir mort : un runner sans réseau échouera - en une minute
et en le disant, au lieu d'immobiliser une PR trois quarts d'heure.

**2. Les `.deb` se mettent en cache.** Borner évite de pendre ; cela ne fait pas descendre 91 Mo plus
vite. Le seul levier restant quand l'hébergeur ralentit est de ne pas retélécharger.

**3. Le cache de l'état INSTALLÉ (`awalsh128/cache-apt-pkgs-action`) a un périmètre étroit.** Il
restaure des fichiers **sans rejouer les scripts post-installation**. Il sert là où rien n'est rendu
- `bats`, `desktop-file-utils` - et nulle part ailleurs.

⚠️ **Ce qui compte n'est pas le paquet demandé, c'est ce qu'il TRAÎNE.** J'avais rangé `ffmpeg` parmi
les « paquets de fichiers » en regardant son nom ; sa fermeture de dépendances tire **dix paquets de
polices**. Le premier run qui a trouvé le cache a fait tomber cinq cas du banc de recette - tous ceux
qui écrivent du texte dans une vidéo, `drawtext` cherchant sa police par fontconfig.

`verifie-apt.sh` refuse donc `fonts-*`, `flatpak*` et `ffmpeg` au cache de fichiers.

## Conséquences

⚠️ **Un cache ne se prouve pas au run qui le REMPLIT.** Le run mesuré avant la fusion affichait
`Cache not found` : il enregistrait. Le suivant, qui a trouvé la clé, est tombé. Un dispositif dont le
défaut attend le second essai est exactement celui qu'une vérification unique laisse passer.

Le gain, une fois le cache en service et mesuré sur `main` :

```
→ cache APT : /home/runner/work/_temp/apt-vigiechiro (118 paquet(s) déjà là)
Need to get 0 B/91.2 MB of archives.
```

## Ce qui a été écarté

**`eclipse-score/apt-install`**, qui enveloppe `awalsh128/cache-apt-pkgs-action` en six commits sans
tag. Un saut de dépendance au lieu de deux, dans un dépôt qui épingle ses actions au SHA et surveille
leur fraîcheur.

**Recalculer le volume à télécharger dans la porte.** Elle l'a annoncé un temps - « 87 Mo » - en
sommant la taille des paquets. Juste comme poids, faux comme annonce dès que le cache servait : APT
disait « 0 B/91.2 MB » sur la ligne d'à côté. Dupliquer une mesure qui existe déjà, c'est s'exposer à
la voir vieillir ; celle-ci a vieilli en une journée.
