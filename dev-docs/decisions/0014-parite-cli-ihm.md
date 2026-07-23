# ADR 0014 — Toute capacité métier est offerte aussi en ligne de commande (parité CLI ↔ IHM)

- **Statut** : Accepté — rétroactif
- **Chantier** : CLI socle #619/#659 ; parité #1304 ; passe 2 du cycle de chantier
- **Vérification** : humaine — la parité CLI/IHM est une passe de clôture (passe 2), une obligation de processus, pas un invariant du code à un instant donné

## Contexte

L'application a une IHM JavaFX, mais aussi une **CLI** (`fr.univ_amu.iut.cli`, picocli). Le risque, quand deux surfaces coexistent, est qu'elles **divergent** : une capacité ajoutée à l'IHM (archiver, réactiver, reconstruire, diagnostiquer…) reste inaccessible en ligne de commande, et l'automatisation, les tests de bout en bout et la recette scriptée s'appauvrissent.

## Décision

Quand un chantier ajoute ou change une **capacité métier**, la CLI doit en exposer l'**équivalent** - même comportement, mêmes règles, mêmes formats - **au même moment**. Cette exigence est une **passe obligatoire** de la clôture de tout chantier (passe 2, « cohérence CLI ↔ UI ») : soit l'aligner tout de suite si c'est petit, soit **créer une issue** sinon. « Sans objet » n'est admis que pour un chantier purement présentationnel.

## Conséquences

- La CLI reste un **miroir fidèle** du métier : `archiver`, `reactiver`, `reconstruire-passage`, `diagnostiquer`, `audit-coherence`… suivent l'IHM.
- Les deux surfaces partagent le **même service** métier ; seule la présentation diffère (une modale ici, du texte ou du JSON là). La logique n'est écrite qu'une fois.
- La CLI devient un levier de **test et de recette** (parcours scriptables, sondes de contrat) que l'IHM seule ne permettrait pas.

## Alternatives écartées

- **Laisser la CLI dériver, la rattraper « plus tard ».** « Plus tard » n'arrive pas ; l'écart se creuse et la CLI devient un piège (des commandes qui ne font plus ce que l'IHM fait).
- **Dupliquer la logique par surface.** Deux implémentations d'une même règle divergent immanquablement ; le service partagé garantit un comportement unique.
