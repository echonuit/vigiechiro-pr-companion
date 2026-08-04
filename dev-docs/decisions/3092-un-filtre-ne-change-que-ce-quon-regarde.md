# ADR 3092 - Un filtre ne change que **ce qu'on regarde**, jamais le verdict porté sur l'ensemble

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3092, passe 2 de la clôture
- **Vérification** : certaine - `CliAuditTest#audit_filtre_par_gravite`

## Contexte

Six écrans filtrent une table, et deux d'entre eux portent en plus un **jugement sur l'ensemble** :

- « Audit de cohérence » annonce « 12 constats : 3 erreurs, 0 avertissement, 9 infos », et sa commande
  jumelle `audit-coherence` rend `1` quand le workspace porte au moins une erreur ;
- « Ma saison » annonce « 14 points suivis, 21 faits, 3 à refaire, 4 à réaliser », et `solde-saison`
  imprime le même en-tête.

Quand l'utilisateur pose un filtre, une question se pose sur ces deux nombres : **suivent-ils la
sélection, ou restent-ils sur l'ensemble ?**

Les deux réponses se défendent. « Le résumé décrit ce que je regarde » est cohérent avec un tableau de
bord qui se recalcule. « Le résumé décrit ma saison » est cohérent avec un solde, qui est un fait
indépendant de ce qu'on affiche.

La question s'est posée trois fois pendant le chantier, et une quatrième fois en portant les filtres
en ligne de commande - où elle prend une forme plus dure, parce que le **code de sortie** est lu par des
scripts.

## Décision

**Un filtre ne touche que la liste affichée.** Le résumé, le verdict et le code de sortie continuent de
se calculer sur l'ensemble.

Concrètement :

- `AuditViewModel` expose `constats()` (l'audit entier, d'où viennent le résumé et le verdict « sain »)
  **et** `constatsFiltres()` (ce que la table montre) ;
- `SaisonViewModel` expose `lignes()` **et** `lignesFiltrees()`, l'en-tête se calculant sur la première ;
- `audit-coherence --gravite INFO` **rend `1`** si le workspace porte une erreur, même si la liste
  affichée n'en montre aucune ;
- `solde-saison --lieu` imprime le solde de la **saison entière** au-dessus des points retenus.

Corollaire, sans lequel la décision devient un piège : **un filtre qui ne retient rien le dit**. Les
deux commandes impriment une phrase (« Aucun constat ne correspond aux filtres », « aucun point ne
correspond aux filtres ») au lieu de laisser un en-tête suivi du vide.

## Pourquoi

**Masquer des lignes n'efface pas des constats.** C'est la raison de fond : un filtre est un instrument
d'observation, pas une action sur les données. Un audit dont le verdict suivrait le filtre dirait
« sain » à qui a demandé à ne voir que les infos - et l'utilisateur qui a filtré est précisément celui
qui a le plus de raisons de croire ce verdict, puisqu'il vient d'agir.

**Le code de sortie est lu par une machine, qui n'a pas vu le filtre.** Un script d'intégration continue
(`audit-coherence --gravite ERREUR || alerter`) lirait `0` sur un workspace abîmé si le filtre changeait
le verdict. La forme la plus utile de la commande deviendrait la plus dangereuse.

**Les deux surfaces doivent répondre pareil.** L'écran et la ligne de commande posent la même question
au même service ; deux réponses différentes à « est-ce sain ? » selon la surface est une dette qu'aucun
test ne rattrape, parce que chaque surface est verte de son côté.

## Conséquences

- Un ViewModel de table filtrable expose **deux listes**, et la doc-comment de chacune dit laquelle
  nourrit quoi. Les brancher à l'envers est silencieux : la table montrerait tout, le résumé compterait
  la sélection.
- Le filtrage CLI s'applique **au rendu**, jamais au rapport rendu par le service. Reconstruire le
  record de résultat avec les lignes retenues ferait mentir l'en-tête, ses compteurs se déduisant de ses
  lignes (`SoldeSaison#pointsSuivis`).
- Cette décision **ne s'applique pas** aux écrans sans jugement d'ensemble (Sons & validation, Espèces &
  observations, Carte & passages, Activité) : ils n'ont rien à préserver, la question ne s'y pose pas.

## Alternatives écartées

**Le résumé suit la sélection.** Cohérent pour un tableau de bord, mais il faudrait alors dire quelque
part que le verdict ne parle plus de la saison - et le code de sortie de la CLI resterait indéfendable.

**Le filtre est refusé quand un verdict est affiché.** Écarté sans hésiter : ce serait retirer à
l'utilisateur l'outil pour la seule raison qu'on ne sait pas quoi faire d'un chiffre.
