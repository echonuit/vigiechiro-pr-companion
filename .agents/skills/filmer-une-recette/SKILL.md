---
name: filmer-une-recette
description: Use when a user journey must be proven to actually run, not merely to pass. Covers the filmed bench, the luminance threshold, the auto-test, and the negative control that makes the whole verdict meaningful.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: scripts/doc-video/filme-un-parcours.sh, .github/workflows/recette-filmee.yml
---

# Recette filmée

## Loi d'airain

```
UN BANC QUI NE PILOTE RIEN REND UNE VIDÉO PARFAITEMENT VALIDE, ET VIDE
```

Un parcours ne se prouve pas par un test vert : un test peut passer sur un écran qui n'a jamais rien
affiché. Le banc filme l'exécution réelle **et** prouve d'abord qu'il sait échouer.

## Annoncer

« J'utilise la compétence filmer-une-recette pour <le parcours>. »

## Le témoin, qui est la pièce maîtresse

Le même banc est rejoué **sans gestionnaire de fenêtres**, configuration où le lancement **doit**
échouer.

> S'il rend un vert dans ce mode, le workflow se déclare **en erreur**.

Cela signifierait que la vérification du pointeur ne garde rien et que tous ses verts sont creux.
Le dispositif prouve donc sa propre capacité à rougir **avant** qu'on lise son verdict.

Le gestionnaire de fenêtres est installé **à part**, précisément pour que le témoin puisse s'en
passer.

## Fonction de garde

```
1. AUTO-TEST  lancer `filme-un-parcours.sh --auto-test`. Il eprouve les REFUS du banc,
              il ne monte aucune carte.
2. TEMOIN     rejouer sans gestionnaire de fenetres. Un vert ici est un ECHEC.
3. FILMER     le parcours reel.
4. JUGER      sur l image, au seuil de luminance, pas sur le code de sortie du test.
5. CONSERVER  la video et ses reperes.
```

## Les réglages, et pourquoi ils valent ce qu'ils valent

| Réglage | Valeur | Raison |
|---|---|---|
| Gestionnaire de fenêtres | **openbox** | `matchbox` **maximise** tout ce qu'il affiche et fausse les cadrages |
| Seuil de luminance | **20** | le noir vaut 16 ; en dessous de 20, un écran vide passerait pour un écran plein |
| Cadence | **12 images/s** | assez pour lire un enchaînement, moitié moins lourd que 25. Une vidéo de documentation se regarde, elle ne s'analyse pas image par image |
| Fenêtre | **1280 × 860** | la taille à laquelle l'utilisateur ouvre le produit |

## Ce que le banc ne couvre pas

Les défauts propres à l'**emballage** : police absente du Flatpak, module oublié au `jlink`. Ils
restent couverts par les vérificateurs de démarrage et d'affichage, **et par eux seuls**. Le dire
ici évite de croire le banc plus large qu'il n'est.

## Pièges connus

| Piège | Symptôme | Remède |
|---|---|---|
| `openbox` absent du poste | le banc échoue localement mais passe en CI | l'installer, ou ne juger qu'en CI |
| Une seule liste de dépendances | `udisksctl` réclamé sur un runner qui ne monte aucune carte | deux listes : ce que le banc exige, ce que l'auto-test exige |
| Juger sur le code de sortie | une vidéo vide et valide passe au vert | juger sur l'image |

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Le test du parcours est vert » | Il peut passer sur un écran resté noir |
| « Le film existe, donc ça a marché » | Un banc qui ne pilote rien produit un film valide et vide |
| « Le témoin n'est pas nécessaire cette fois » | Sans lui, aucun vert du banc ne vaut |
| « Ça marche chez moi » | `openbox` est peut-être absent du poste. La CI fait foi |
