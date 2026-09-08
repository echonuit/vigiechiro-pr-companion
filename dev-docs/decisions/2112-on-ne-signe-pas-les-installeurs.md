---
type: adr
title: "On ne signe pas les installeurs, on s'arrête à la notification"
status: stable
article: A11
chantier: "#2112 (EPIC #2104)"
decided_at: 2026-09-08
verification: humaine
loupe:
  - "scripts/adr/loupe-2112-signature-presentee-comme-a-venir.py"
verification_note: "un renoncement ne laisse aucun code derrière lui, donc aucune demande ne peut le faire rougir : la décision ne peut être trahie que dans la prose, et c'est la prose qu'on relève. Loupe : `scripts/adr/loupe-2112-signature-presentee-comme-a-venir.py`"
verified:
  - by: human:nedseb
    at: 2026-09-08
---

# On ne signe pas les installeurs, on s'arrête à la notification

## Le contexte

Les installeurs du produit ne sont pas signés. SmartScreen sous Windows et Gatekeeper sous macOS
affichent donc un avertissement à la première ouverture, et la documentation le dit déjà.

Le lot 4 du chantier #2104 a livré la notification de mise à jour : l'application annonce qu'une
version existe et renvoie à la page des Releases. Le lot 7 voulait aller jusqu'au bout et installer
la mise à jour depuis l'application. C'est là que l'obstacle cesse d'être technique.

Une application qui télécharge puis exécute elle-même un installeur non signé place l'utilisateur
devant un avertissement système au moment précis où on lui demande de faire confiance. Lever
l'obstacle demande un certificat de signature Windows, environ 300 euros par an, et un compte Apple
Developer, 99 dollars par an. C'est une décision de financement.

## La décision

**Nous ne finançons pas la signature de code. Le produit s'arrête à la notification.**

La chaîne de mise à jour reste celle-ci : l'application signale la version disponible, l'utilisateur
va la chercher, et son système peut l'avertir. Sur Windows et sur Linux, winget et Flatpak évitent
ce détour à qui les utilise déjà.

C'est une décision de ne pas faire, et c'est pour cela qu'elle est écrite. Sans elle, la question se
repose à chaque chantier de livraison, et chaque fois sans le chiffre qui la tranche.

## Ce qui la rouvrirait

Elle est révisable, et les deux montants ci-dessus sont dans ce texte pour cela : le jour où on la
repose, on la repose sur un prix, pas sur un souvenir. Trois faits la rouvriraient légitimement.

Un utilisateur qui renonce à installer le produit à cause de l'avertissement, constaté et non
supposé. Un canal natif qui exigerait la signature pour continuer à servir le paquet. Une baisse du
coût, ou un financement qui n'existe pas aujourd'hui.

Aucun de ces trois n'est vrai en septembre 2026.

## Les conséquences

L'issue #161 demandait « détection d'une nouvelle version » et « téléchargement + installation
guidée **ou** notification + lien ». Le second critère admet la notification : #161 est donc tenue,
pas abandonnée, et se clôt sur le lot 4.

L'attestation de provenance ne remplace pas la signature et ne prétend pas le faire. Elle prouve
d'où vient le fichier à qui veut vérifier ; la signature parlerait au système d'exploitation. Ce
sont deux lecteurs différents, et renoncer à l'un ne dévalue pas l'autre.

La limite reste écrite là où l'utilisateur la rencontre, dans la FAQ et la prise en main. Ce qui
change, c'est qu'aucune page ne l'annonce plus comme provisoire.

## Comment on saura qu'elle est tenue

Une décision de ne pas faire ne laisse aucun code derrière elle. Aucun test ne peut rougir, et
nommer ici un garde prétendrait qu'il refuse quand il ne ferait que relever : la vérification est
donc humaine, avec une loupe.

`scripts/adr/loupe-2112-signature-presentee-comme-a-venir.py` relève les endroits de `docs/` et
`dev-docs/` où la signature est présentée comme une suite à venir, ou renvoyée à l'issue #2112 comme
à une question ouverte. Elle a été vue rouge sur les quatre passages qui existaient le jour de la
décision, avant qu'ils soient corrigés.

Elle lit au paragraphe et non à la ligne, parce que la promesse qui a motivé cette ADR tenait sur
deux lignes : un relevé ligne à ligne l'aurait manquée en rendant vert.
