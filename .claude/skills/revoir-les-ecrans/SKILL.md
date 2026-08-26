---
name: revoir-les-ecrans
description: Use at closure, right after the harmonisation pass, to inspect every visible consequence of the chantier state by state. A tested gesture is not a looked-at screen; five real UI defects were found by opening a capture and none by a test.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Revue visuelle

## Loi d'airain

```
UN GESTE TESTÉ N'EST PAS UN ÉCRAN REGARDÉ
```

Un test vérifie qu'un bouton **fait** ce qu'il doit. Il ne vérifie pas qu'on peut **lire** ce qu'il
dit.

## Annoncer

« J'utilise la compétence revoir-les-ecrans sur les conséquences visibles de <le chantier>. »

## Pourquoi ici, et pas plus tôt

La passe d'harmonisation touche volontiers au CSS partagé et aux composants du socle : c'est **elle**
qui est la plus à même de casser un écran sans casser un test. On regarde donc **après** elle.

## Fonction de garde

```
1. INVENTORIER chaque ETAT qu un ecran touche peut prendre : donnee presente ou
               absente, GPS renseigne ou non, liste vide ou pleine, calcul
               disponible ou non, theme clair ou sombre.
2. COUVRIR     un etat montre nulle part est un angle mort. CREER la capture
               manquante avant d aller plus loin.
3. REGENERER   les captures concernees.
4. OUVRIR      chacune, une par une. Les ouvrir, pas les lister.
5. RECADRER    et agrandir avant toute affirmation d absence ou de troncature.
6. RELIRE      les libelles que la capture montre. L article A31 les couvre, et
               l ecran est le seul endroit ou on les lit comme l utilisateur.
7. CORRIGER    ce qui se corrige ; le reste part en issue a la passe suivante.
```

## Ce qu'on cherche, par ordre de fréquence constatée

1. du **texte coupé** : libellé, consigne, bouton. Une ellipse est un aveu ;
2. un **glyphe absent** : emoji, symbole ;
3. un **écart entre la capture et le produit** : une capture *reconstruite* quelque part au lieu
   d'être *rendue* **mentira** tôt ou tard ;
4. une **régression de style** après factorisation CSS ;
5. un **écran de la doc qui ne ressemble plus** à ce que le chantier a livré.

## Les cinq défauts qu'aucun test n'a vus

Tous trouvés en ouvrant une capture, alors que les gestes concernés étaient couverts.

| Défaut | Ce qu'il révèle |
|---|---|
| Un libellé tronqué | puis **le même** sur un autre écran, préexistant |
| Une consigne rognée par le bouton voisin | la place n'est pas testée |
| Un emoji qui ne se rend pas | le glyphe manque à la police |
| Une capture de doc qui avait dérivé | elle montrait un protocole **qui n'existe pas** |
| Une confirmation destructive entière cachée | la doc montrait un produit disparu |

## Regarder d'assez près pour que l'affirmation tienne

Un aperçu s'ouvre à sa taille naturelle, où un glyphe fait douze pixels. À cette échelle, **un
pictogramme monochrome fin est indistinguable du vide** : on conclut « absent » sur ce qui est
simplement discret.

Une clôture a publié trois « preuves » d'absence dont **une seule** était exacte. Recadrés et
agrandis trois fois, deux des glyphes se rendaient, et un troisième se rendait **en forme
méconnaissable**, ce que personne n'avait envisagé.

**Avant d'écrire qu'un élément manque, qu'un texte est coupé ou qu'une couleur a bougé : recadrer la
zone et l'agrandir.** Trente secondes, et l'affirmation devient un constat.

**Un pictogramme a trois issues, pas deux** : rendu, absent, ou **déformé**. La troisième est la
pire pour l'utilisateur, puisqu'elle se lit comme une faute de frappe dans le libellé.

## La capture est une documentation vivante

Une conséquence visible qui n'a **pas** de capture n'est **pas** documentée : elle dérivera en
silence, et le prochain qui lira la doc verra un produit qui n'existe plus.

Une capture ajoutée devient une **validation visuelle rejouable** : graine déterministe, entrée au
manifeste, insertion dans la doc. Elle est régénérée à chaque construction et recontrôlée à chaque
chantier suivant.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Les tests passent, l'écran va bien » | Aucun des cinq défauts n'a fait rougir quoi que ce soit |
| « J'ai regardé les captures » | Ouvertes une par une, ou listées ? |
| « Ce glyphe est absent » | Recadrez et agrandissez avant de l'écrire |
| « Cet écran n'a pas changé » | L'harmonisation touche au CSS partagé |
| « Cet état n'a pas de capture, tant pis » | Un état montré nulle part est un angle mort |
