# ⚡ [vérification] Mesurer les performances réelles

Le brief fixe des objectifs d'**efficience** (ISO 25010) : tenir la charge d'une **nuit volumineuse**
(O3) et d'une **base réaliste** (O5, ~1000 passages / ~4000 observations). Des **outils de mesure**
sont fournis — utilisez-les sur **votre machine** (ou une machine de l'IUT, représentative).

## Ce qu'il faut faire

1. Lisez `docs/benchmarks/README.md` (méthode + cibles chiffrées).
2. **Générez un jeu de données** réaliste avec l'outil fourni `perf/outils/GenerateurJeuDeDonnees`.
3. **Mesurez** avec les bancs fournis `perf/outils/BancMesure` (tri/filtre/sélection, cible O5) et
   `perf/outils/BancImport` (import d'une nuit volumineuse, cible O3). Les outils tournent depuis la
   ligne de commande (voir la doc des benchmarks).
4. **Comparez** vos mesures aux cibles du brief et **consignez** les résultats (tableau) dans le
   compte rendu d'équipe. Si une cible n'est pas tenue, identifiez le point chaud (un index manquant ?
   une requête non bornée ?) et proposez une piste.

## Critères d'acceptation

- [ ] Un **tableau de mesures** (sélection, tri/filtre, import) est produit, daté, avec la machine de
      référence indiquée.
- [ ] Chaque mesure est **comparée** à la cible correspondante (tenue / non tenue + commentaire).

## Definition of Done

- [ ] Les résultats sont consignés (compte rendu ou `docs/benchmarks/`).
- [ ] Si une cible n'est pas tenue, une **issue d'amélioration** est ouverte avec le point chaud
      identifié.
