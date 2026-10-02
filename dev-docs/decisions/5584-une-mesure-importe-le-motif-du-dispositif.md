---
type: adr
title: "Une mesure importe le motif du dispositif, ou elle l'ancre : un motif retapé rend un chiffre plausible et faux"
status: stable
article: A5
chantier: "#5584, passe 11 de sa clôture (cinq motifs retapés en une journée, tous mesurés)"
decided_at: 2026-10-02
verification: humaine
verification_note: "aucun motif ne distingue un motif retapé d'un motif importé : les deux sont du code qui compte. Ce qui se vérifie est la CONCORDANCE de deux mesures obtenues par des chemins différents, et c'est ce qui a attrapé les cinq instances. La règle se tient par la relecture de qui mesure, et rien ne la refuse"
relations:
  complete: ["3627-une-mesure-dit-ce-qu-elle-n-a-pas-pu-lire"]
verified:
  - by: human:nedseb
    at: 2026-10-02
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-02
---

# Une mesure importe le motif du dispositif, ou elle l'ancre : un motif retapé rend un chiffre plausible et faux

## Contexte

Le chantier #5584 traitait les dispositifs qui rendent un verdict sans avoir exercé ce qu'ils jugent.
Sa clôture en a produit **cinq instances de plus, toutes dans mes propres mesures, en une journée** -
et une mesure dont le motif est retapé est exactement cela : un verdict qui n'a pas exercé son sujet.

| Ce que je voulais compter | Ce que le motif retapé a rendu | Le vrai |
|---|---|---|
| les cas tués par chaque mutation | une colonne entière de `?` | la clé disait `un seul muet est nommé`, le source `un seul muet : seul le muet est nommé` |
| les lots fermés par chaque commit | #5335 et #5684 attribués au commit de #5211 | ce corps **documente** le motif en citant `« Closes #5335 »` |
| les fichiers de capacité utilisateur | **16** | `docs/` matchait `dev-docs/` ; ancré en `^docs/`, c'est **0** |
| les cas de test ajoutés | **77**, puis **114** | l'alternation cherchait `joue(` et mon aide s'appelle `joue_demande(` |
| les lots dont le critère vit en commentaire | **3**, et 0 sans critère | le rappel **cite les formulations qu'il cherche** : 2 et 1 |

Aucune de ces cinq n'a rougi. Toutes ont rendu un nombre d'allure plausible.

## Décision

**Une mesure importe le motif du dispositif qu'elle mesure, ou elle ancre le sien.** Importer veut dire
charger le module et appeler sa fonction, pas recopier son expression. Ancrer veut dire `^` et `$`, et
un chemin de fichier se compare par son préfixe complet.

**Et un texte qui documente un motif contient le motif.** C'est la forme la plus coûteuse des cinq,
parce qu'elle fait compter l'instrument comme une réponse : le rappel du critère de fin cite « comment
on saura qu'il est fini », un corps de demande cite « Closes #5335 » pour montrer ce que son
dispositif en fait. Toute mesure qui balaie de la prose doit donc écarter la prose des dispositifs,
par leur marque quand ils en portent une.

## Ce que la décision ne dit pas

**Elle n'interdit pas d'écrire un motif.** Un dispositif neuf en écrit un, et c'est son travail. Ce
qu'elle interdit est de **réécrire** celui d'un dispositif existant pour le mesurer.

**Et elle ne promet pas qu'un motif importé soit juste.** Il est seulement le même que celui qui juge,
ce qui rend la mesure et le verdict cohérents. Un motif faux importé rend une mesure fausse et un
verdict faux, et c'est préférable : l'écart entre les deux est ce qui se remarque.

## Conséquences

**Ce qui attrape ces cinq-là n'est pas la vigilance.** Quatre ont été trouvées par une **divergence** :
deux chiffres obtenus par des chemins différents, 77 contre 114, ou un signe négatif là où j'avais
ajouté des cas. Une mesure qui ne se contredit jamais n'a été faite qu'une fois.

Le geste pratique : quand un chiffre surprend, le refaire par un autre chemin **avant** de le publier,
et quand il ne surprend pas, demander ce qui le confirmerait. Le contrôle le moins cher est une
**soustraction** - brut moins retirés égale reste - qui attrape le mauvais instrument gratuitement.

**La limite se déclare.** Une mesure borne sa population et dit ce qu'elle n'a pas pu lire
(ADR 3627) ; celle-ci ajoute qu'elle dit **avec quoi** elle a compté. Les deux répondent à la même
question posée à deux endroits : d'où vient ce nombre.
