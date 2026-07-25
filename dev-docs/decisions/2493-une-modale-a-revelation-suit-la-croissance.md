# ADR 2493 — Une modale à révélation suit la croissance de son contenu

- **Statut** : Accepté — 2026-07-25
- **Chantier** : #2493 (issu du fix connexion #2486, du patron #1534)
- **Vérification** : probable — `scripts/adr/2493-modale-suit-croissance.py` (cliquet : 3)

## Contexte

Une modale est dimensionnée à son **ouverture**, sur le contenu visible à cet instant. Un bandeau de retour qui paraît **ensuite** - un message d'erreur de validation, un résultat de récupération GPS, un compte rendu serveur - agrandit la mise en page sans agrandir la fenêtre. Les boutons du bas (« Valider », « Créer », « Appliquer ») passent alors **sous la ligne de flottaison**, hors d'atteinte.

Le patron `Modales.suivreLaCroissance` corrige exactement cela : il fait suivre à la fenêtre la croissance de son contenu, en gérant la subtilité du `wrapText` (un libellé enroulé n'a de hauteur qu'après la passe de mise en page). Il a été écrit pour ce défaut (#1534).

Mais chaque modale doit **penser à le câbler**, et rien ne le vérifiait. Le défaut est revenu : la modale de connexion (#2486), puis un audit a trouvé **trois autres** modales dans le même cas (Rattachement, ModalePoint, ModaleSite). Deux fois, à des mois d'écart, sur un helper qui existe.

## Décision

**Toute modale qui révèle du contenu après son ouverture appelle `Modales.suivreLaCroissance`.** « Révéler » couvre un bandeau de retour (`BandeauRetour`, `LibelleRetour`) ou toute bascule `managed=false → true` d'un nœud placé au-dessus d'une barre d'actions.

Le câblage se pose dans `initialize()`, sur la propriété qui déclenche la révélation :

```java
Modales.suivreLaCroissance(racine, bandeauStatut.managedProperty());
```

Un garde-fou `probable` (`scripts/adr/2493-modale-suit-croissance.py`) liste les controllers de modale qui révèlent un bandeau sans câbler le suivi ; le portail qualité fait rougir la CI dès qu'un cas s'ajoute.

## Conséquences

- Un retour qui paraît dans une modale n'y pousse plus les boutons de validation hors du cadre.
- La vérification est `probable`, pas `certaine` : « est-ce une modale ? » est approché par le nom du controller (`*Modale*Controller`), et un popup nommé autrement serait manqué ; « révèle-t-elle un bandeau qui pousse des boutons ? » se lit à l'intention. Un humain confirme les suspects.
- Le cliquet démarre à **3** (la dette trouvée à l'audit) et descend à mesure que les modales sont câblées.

## Alternatives écartées

- **Réserver la place du bandeau (managed=true figé).** Un bandeau `wrapText` a une hauteur variable (1 à 3 lignes) : réserver une place fixe gaspille de l'espace quand il est vide, ou rogne quand il est haut. Faire suivre la croissance s'ajuste à la hauteur réelle.
- **Un `ScrollPane` autour du contenu de la modale.** Déplace le problème : les boutons du bas passeraient sous une barre de défilement au lieu de disparaître. Une modale de saisie doit montrer ses actions, pas les faire défiler.
- **Une `certaine` par test.** « Cette classe est-elle une modale, et ce nœud pousse-t-il vraiment des boutons ? » demande un jugement ; un test déterministe se tromperait dans les deux sens. D'où `probable` avec un humain dans la boucle.
