---
type: adr
title: "La doctrine d'une passe vit dans la compétence qu'on convoque, et la page n'en garde que le renvoi"
status: stable
article: A3
chantier: "#4828"
decided_at: 2026-08-31
verification: certaine
enforced_by:
  - "scripts/methode/concordances-du-cycle.py"
verified:
  - by: machine:ci
    at: 2026-08-31
---

# La doctrine d'une passe vit dans la compétence qu'on convoque, et la page n'en garde que le renvoi

## Contexte

L'[ADR 4515](4515-adopter-un-arbre-amont-quand-il-doit-parler-notre-cycle.md) a posé, pour les six
compétences OpenSpec, que « la doctrine devait entrer dans les fichiers que l'agent lit **au moment
du geste**, ou n'exister nulle part ».

Le plus gros corpus de méthode du dépôt était resté dehors. Au 30 août 2026, **dix passes de clôture
sur quatorze** n'avaient de détail que dans `dev-docs/cycle-de-chantier.md`, une page que rien
n'oblige à ouvrir : le seul lien du corps de l'orchestrateur vers elle pointait le modèle de clôture,
et le champ `origine:` de son en-tête est de la métadonnée que onze compétences portent sans qu'aucune
ne demande d'ouvrir le fichier.

Un agent qui suivait l'orchestrateur à la lettre exécutait dix passes **sans description**.

## Le défaut, et pourquoi une colonne annoncée ne le disait pas

Le tableau des passes portait une colonne « Compétence » qui annonçait plus qu'elle ne tenait. Trois
formes s'y mêlaient, et une seule était juste :

| Ce que la colonne annonçait | Ce qu'il en était |
|---|---|
| `ecrire-une-adr`, `revoir-les-ecrans`, `openspec-archive-change` | **portaient** la passe, étapes numérotées à l'appui |
| `humaniser` pour les passes 3, 4, 5 et 12 | grille de prose de 984 lignes, **trois** occurrences du mot « passe », aucune sur celles-ci |
| `tdd` et `mutation` pour la passe 6 | `tdd` n'en mentionne aucune ; `mutation` en parle pour dire **l'inverse**, « Pas à la clôture » |
| `trier-les-issues` pour la passe 9 | six étapes numérotées, **celles du triage à l'ouverture** |

Le dernier cas est le plus retors : la compétence porte quelque chose, et l'on ne remarque pas que
c'est autre chose.

## Décision

**La doctrine d'une passe vit dans la compétence que l'orchestrateur convoque à son tour, et la page
du cycle n'en garde que le renvoi.**

L'ADR 4515 n'est pas étendue par analogie mais par le même motif : une compétence se **convoque**,
une page de `dev-docs/` demande une lecture délibérée que rien ne réclame.

Trois règles en découlent, chacune payée par un défaut mesuré :

**Une compétence d'appui se déclare comme telle.** `humaniser` reste nommée pour les passes de prose,
`tdd` et `mutation` pour la passe 6, mais **en appui** : l'article A31 couvre ce qu'elles écrivent
sans qu'elles portent la passe.

**Ce qui décide qu'une compétence serve une ou deux passes est le geste**, non le nombre
([ADR 4902](4902-une-competence-par-geste-pas-par-passe.md)).

**L'énoncé général reste chez l'orchestrateur, la preuve va chez la passe dont elle est la trouvaille**
([ADR 4890](4890-l-enonce-vit-chez-l-orchestrateur-la-preuve-chez-la-passe.md)).

## Comment elle est vérifiée

`concordances-du-cycle.py` refuse qu'une passe du tableau nomme une compétence qui n'existe pas, et
trois autres concordances avec elle. Il ne vérifie pas qu'une compétence **dise vrai** : c'est écrit
dans son en-tête.

## Conséquences

**586 lignes de sections sont devenues 56 lignes de renvois**, et dix-sept renvois d'issue ont été
retirés sans qu'aucun ne soit perdu : chaque section a été confrontée à sa compétence avant la coupe.

**Les titres `### N.` de la page restent.** `passes-citees-existent.py` en dérive la liste des passes
qui existent ; les supprimer lui ferait conclure qu'aucune n'existe.

**Le coût est réel et il se paie une fois.** Neuf compétences écrites, quatre enrichies, trois remises
en appui, sur une journée. Ce que cela achète est qu'aucune passe ne s'exécute plus sur son seul nom.

## Alternatives écartées

- **Renvoyer depuis l'orchestrateur vers la page.** C'est ce qui existait : un lien, vers le modèle de
  clôture seulement, et onze `origine:` que personne ne suit.
- **Épaissir l'orchestrateur.** Il redeviendrait la page qu'on ne lit pas, à un autre endroit.
- **Une compétence par passe, sans exception.** Les passes 0 et 11 sont les deux bouts d'un même
  geste, et les séparer les ferait se renvoyer l'une à l'autre à chaque décision trouvée.
