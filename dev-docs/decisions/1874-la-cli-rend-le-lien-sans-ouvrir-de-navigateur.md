---
type: adr
title: "La CLI rend le lien d'une fiche sans ouvrir de navigateur"
status: stable
article: A19
heuristiques: [nielsen-7]
chantier: "#5505, lot #1874"
decided_at: 2026-09-25
verification: humaine
loupe: "Relire LienEspece et LienParticipation contre CliLiensTest et cli.bats : la sortie est une URL seule, et aucun ouvreur de navigateur ni appel réseau n'entre dans les commandes."
verified:
  - by: humain
    at: 2026-09-25
relations:
  precise: ["0014"]
---

# La CLI rend le lien d'une fiche sans ouvrir de navigateur

## Contexte

L'interface ouvre la fiche d'une espèce ou d'une participation dans le navigateur. Un script doit
pouvoir retrouver le même lien, mais l'ouverture d'une fenêtre lui retirerait le résultat dont il a
besoin. Elle dépendrait aussi d'un bureau graphique, absent de certains postes où la CLI tourne.

## Décision

`lien-espece` et `lien-participation` écrivent l'URL seule sur stdout. Elles emploient les mêmes
services de construction de liens que l'interface, sans ouvrir de navigateur ni consulter le réseau.
Une fiche absente rend un refus explicite, code 2, sans URL. L'identifiant de passage est local au
workspace ; la commande ne cherche pas une participation sur la plateforme.

## Conséquences

Le script peut copier, stocker ou ouvrir le lien à son choix. La préférence de source des fiches
d'espèce reste celle de l'application. Les tests Java et Bats vérifient les URL, les refus et les
codes de sortie ; l'absence d'effet de bord graphique ou réseau se relit dans les deux commandes,
faute d'un garde qui la démontre à lui seul.
