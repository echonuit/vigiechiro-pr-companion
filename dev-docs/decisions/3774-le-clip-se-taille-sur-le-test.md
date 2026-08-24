---
type: adr
title: "Le clip se taille sur le test, et son contrôle porte sur la couverture"
status: stable
article: A4
chantier: "#3774, tranche (b) de l'EPIC #3667"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - ".github/scripts/lance-test-filme.sh"
verified:
  - by: machine:ci
    at: 2026-08-16
---

# Le clip se taille sur le test, et son contrôle porte sur la couverture

## Contexte

Un film de séance sortait d'un bloc. Personne ne regardera trente minutes pour trancher un cas : un
livrable dont l'usage coûte plus cher que la manipulation qu'il remplace n'est pas un livrable.

Trois choix se posaient, et chacun avait une réponse plausible et fausse.

## Décision

**1. `t0` se mesure, il ne se suppose pas.** Le journal consigne des instants d'horloge ; la vidéo se
compte depuis son début. L'image 0 n'est **pas** à l'instant où l'on a lancé `ffmpeg` : il
s'initialise, et cette latence varie. On la rend sans objet plutôt que de l'estimer - on connaît
l'instant où l'on demande l'arrêt et la durée du fichier obtenu, donc l'image 0 est à
`arrêt − durée`.

L'heure se relève **avant** l'attente de finalisation : `ffmpeg` cesse de capturer quand il lit `q`
et met encore une seconde à écrire son index.

**2. Le clip se taille sur le TEST, l'index se lit par CAS.** Un test cite plusieurs cas, un cas est
couvert par plusieurs tests. Découper par cas demanderait de concaténer des fenêtres éparses ; le
test, lui, est ce que la JVM sait **borner**. L'index fait la correspondance, et c'est gratuit.

Les clips se taillent dans le **brut**, avant la coupe par luminance : sinon il faudrait propager le
décalage qu'elle introduit, et l'oublier ne produirait pas de panne mais des extraits pris à côté.

**3. ⚠️ Le contrôle porte sur la COUVERTURE, pas sur la clarté des clips.** Exiger qu'un clip soit
clair ferait rougir un test de ViewModel, qui cite des cas et n'ouvre légitimement aucune fenêtre :
un garde qui crie sur du bon travail est un garde qu'on apprend à ignorer.

Ce qui est exigé : les images où quelque chose est à l'écran doivent tomber **dans** les plages
calculées. Un `t0` faux les fait toutes tomber à côté ; une séance sans aucune fenêtre n'a rien à
couvrir et ne déclenche rien.

**4. Les plages sont celles de TOUS les tests, pas seulement des tests cités.** Le journal décrit la
séance entière ; c'est l'index qui ne retient que les cas.

## Conséquences

- Un montage refusé ne laisse **aucun** clip et fait échouer le lancement, même quand les tests sont
  verts - c'est justement le cas où personne n'irait vérifier.
- Le nombre d'images utiles est rendu **avec** la couverture, jamais déduit : une couverture parfaite
  sur zéro image serait indiscernable d'un contrôle qui ne s'est pas exécuté.
- L'index ne donne **aucune position** dans le film livré : celui-ci est écourté par luminance, si
  bien qu'une position calculée sur le brut y serait fausse.

## Ce que le point 4 a coûté avant d'être compris

La première séance **réelle** a refusé un alignement pourtant correct : 16 % de couverture. Les
plages ne venaient alors que des tests cités, alors que `ConnexionModaleViewTest` compte dix tests
dont trois annotés - les sept autres ouvrent aussi des fenêtres.

⚠️ C'était **le travers que ce même contrôle évitait par ailleurs**, revenu par la fenêtre du
dénominateur. Aucun film fabriqué ne pouvait le montrer : l'auto-test fournissait un journal où tous
les tests étaient cités.

Leçon générale : un dispositif éprouvé uniquement sur des données qu'il a lui-même fabriquées ne
rencontre que les situations qu'on a su imaginer.

## Alternatives écartées

- **Un clip par cas**, par concaténation. Coûteux, et sans gain : l'index rend le même service.
- **Exiger la clarté de chaque clip**. Voir point 3 : rougirait sur du travail correct.
- **Estimer la latence de démarrage de `ffmpeg`** plutôt que de mesurer `t0`. Une constante calibrée
  sur une machine ne vaut pas sur une autre, et l'erreur ne produit pas de panne : elle déplace.
