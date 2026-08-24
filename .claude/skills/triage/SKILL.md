---
name: triage
description: Use before opening a chantier or claiming an issue, to decide whether there is anything to open at all. Covers sweeping open issues by concept rather than keyword, finding the live EPIC that already covers the need, checking what is already claimed, and querying the repository knowledge graph for what grep cannot relate.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Triage

## Loi d'airain

```
UN AUDIT PRODUIT UN COMPTAGE, PAS UNE LECTURE
```

Un comptage exact peut mélanger deux problèmes de profondeur différente dont l'un a déjà son
analyse ailleurs. Avant d'ouvrir quoi que ce soit, on cherche ce qui existe déjà.

## Annoncer

« J'utilise la compétence triage pour décider s'il y a lieu d'ouvrir sur <le sujet>. »

## Pourquoi cette étape existe

Une passe de clôture a compté 28 endroits écrivant leur sévérité dans du texte, et en a fait une
issue. Une autre issue couvrait déjà **six des huit cas les plus profonds**, avec un remède plus
juste, et son prérequis a fusionné pendant que le doublon s'écrivait. Le recoupement n'a été vu
qu'en lisant un commit apparu sur `main`.

**Rien ne garantit qu'une issue soit rattachée au bon chantier.** Elles naissent une par une, avec
le vocabulaire du chantier qui les a trouvées plutôt que celui du problème qu'elles décrivent. Deux
issues sur le même sujet, écrites depuis deux angles, ne se ressemblent pas.

## Fonction de garde

```
1. BALAYER   les issues ouvertes, pas seulement celles qu on croit concernees.
             Le tri se fait par CONCEPT, pas par mot-cle.
2. CHERCHER  les EPIC vivants qui couvriraient deja le besoin, et les issues FERMEES
             qui l ont differe. « differe de #N » signale un parent dont la moitie
             restante n a plus de toit.
3. VERIFIER  ce qui est deja pris : gh issue list --assignee "*", et git worktree list.
             Une revendication ANCIENNE se verifie au lieu de se croire.
4. DECIDER   du rattachement : une issue appartient au chantier qui traite sa CAUSE,
             pas a celui qui a remarque son symptome.
5. RECADRER  titre ET corps des issues deplacees. Un recadrage laisse en commentaire
             sous un corps perime ne recadre rien.
```

Quand deux chantiers se recoupent, **découper le périmètre explicitement** et l'écrire dans les
deux, plutôt que de laisser la fusion arbitrer.

## Interroger le graphe, pour ce que `grep` ne relie pas

Le tri par concept se heurte à un outil qui ne cherche que des chaînes.

```bash
graphify query "<question>" --budget 2500
graphify path "A" "B"
graphify explain "<concept>"
```

Ce qu'il donne et que `grep` ne peut pas :

- les arêtes `calls` descendent à la **méthode** : qui appelle réellement ceci, et ce code sert-il
  encore ;
- les arêtes `conceptually_related_to` répondent à « **qui d'autre fait X ?** » quand X est une
  idée et non un identifiant ;
- il **traverse les corpus** : quelles maquettes décrivent ce composant, quelles pages documentent
  cet écran, quelles ADR citent ce workflow.

Trois moments l'appellent, et ce sont les trois où l'on croit déjà savoir : l'**ouverture**,
l'**investigation d'un défaut**, l'**audit global**.

### Ce qu'il a trouvé et que les `grep` avaient manqué

| Question | Ce que `grep` donnait | Ce que le graphe a montré |
|---|---|---|
| Qui dérive un département ? | deux écritures de la règle | une **troisième**, qui ne cite aucun des noms cherchés, et sans aucun appelant |
| Qui écrit dans `sauvegardes/` ? | le service cité par l'issue | **deux** autres sources d'accumulation |
| Que fait `release.yml` ? | l'en-tête du fichier | un **troisième** job absent de l'en-tête, et deux ADR le référençant |

### Deux limites, et la seconde décide de la lecture

- **Il ne modélise que notre code.** Aucun nœud pour le JDK ni les bibliothèques : demander qui
  appelle `Files.readAllLines` rend zéro, et un zéro se lit comme une absence. Sur ces
  questions-là, `grep` est le bon outil.
- **Sa sortie est une hypothèse, jamais un inventaire.** Il photographie un commit, donc il
  vieillit, et une part de ses arêtes est inférée. Il **oriente** la recherche, il ne remplace pas
  la lecture. Un zéro se confirme à la main.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai cherché, rien ne couvre ça » | Avez-vous cherché par concept, ou par mot-clé ? |
| « Le graphe rend zéro, donc ça n'existe pas » | Zéro sur du code externe est une absence de modèle |
| « Cette issue est libre, personne n'est assigné » | L'assignation est muette. Lire `git worktree list` |
| « Elle est revendiquée depuis longtemps » | Vérifier : branche vivante ? PR ouverte ? |
| « J'ai compté 28 occurrences » | Un comptage n'est pas une lecture |
