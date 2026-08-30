---
type: adr
title: "Un banc supplée ce que la machine n'a pas, et le déclare là où le clip se lit"
status: stable
article: A4
chantier: "#4447 (EPIC #4416)"
decided_at: 2026-08-30
verification: humaine
verified:
  - by: humain:relecture
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un banc supplée ce que la machine n'a pas, et le déclare là où le clip se lit

## Contexte

Le palier de #4447 a filmé neuf gestes. **Quatre fois**, le banc a buté sur une frontière qu'aucune
machine d'intégration ne franchit, et quatre fois la même décision a été prise sans être écrite :

| Ce que le banc ne peut pas faire | Ce qu'il a fait | Où |
|---|---|---|
| appeler Open-Meteo | substituer le **port** de relevé | `S2-30` |
| ouvrir un vrai dialogue modal - `Alert.showAndWait()` fige TestFX | substituer le **confirmateur**, capturer sa question | `S2-33`, `S3-12` |
| jouer un son, faute de carte son | cliquer « Lecture », puis **poser** `playing` à vrai | `S3-12`, `S3-40` |
| lancer l'analyse Tadarida | **semer** les détections sur le passage importé | `S6-04` → `S6-09` |

Mesuré à chaque fois : le clic sur « Lecture » laisse `playing` à faux, le composant retombant deux
fois ; la nuit nominale importée rend **zéro** série d'espèces et l'état vide.

## Le défaut que cela évite, et celui que cela crée

Sans suppléance, ces cas sont **infilmables** alors que le produit est en bon état : le geste devient
absent du clip pour une raison qui n'est pas le produit.

Avec suppléance et sans déclaration, le clip devient ce que l'ADR 4142 refuse - **convaincant et
creux**. Il montre un écran juste, et le spectateur croit avoir vu ce que le banc n'a pas joué.

## Décision

**1. La suppléance se fait à la frontière que la machine ne franchit pas, jamais en deçà.** On
substitue le port, le confirmateur, le périphérique - **pas** le comportement du produit. Le câblage
réel prend le relais : `playingProperty` déclenche le marquage d'écoute, le confirmateur reçoit la
vraie question, les observations semées passent par les vrais services.

Le partage est net : bouchonner `ServicePassage` ou `ServiceActivite` **n'est pas** une suppléance,
c'est fabriquer l'écran. L'ADR 4142 le traite déjà.

**2. Ce que la suppléance retire au clip se déclare sur la page des clips**, à côté du lecteur, et non
dans le code seul. C'est là qu'on regarde, donc c'est là que la réserve doit être lisible.

La formule est toujours la même : *ce que le clip démontre*, puis *ce qu'il ne démontre pas*. « Une
séquence écoutée change ce que Régénérer coûte » **et** « le banc ne démontre pas que le son sorte ».

**3. Le geste de l'utilisateur reste joué.** On clique « Lecture » avant de poser `playing` ; on
importe pour de vrai avant de semer les détections. Un banc qui sauterait le geste pour n'en poser que
la conséquence ne filmerait plus un parcours, mais son résultat.

## Ce que cette ADR ne mécanise pas

Rien ne détecte une suppléance non déclarée. `verification: humaine` : le motif est qu'aucun code ne
sait distinguer un port substitué pour franchir une frontière d'un port substitué par commodité - c'est
l'intention qui les sépare, et elle ne se lit pas.

Le garde le plus proche reste `PageDesClipsTest`, qui exige qu'un clip déclaré soit montré quelque
part ; il ne lit pas ce que la page en dit.
