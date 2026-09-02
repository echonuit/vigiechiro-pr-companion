# P10 - Exporter une bibliothèque de sons de référence 🎼

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Samuel. **Objectifs qualité visés** : aucun direct - c'est une fonctionnalité bonus.

Samuel a validé plusieurs centaines d'observations sur la saison. Il veut **constituer une petite bibliothèque de sons de référence** par espèce (les meilleurs exemples qu'il a entendus) pour la transmettre à un débutant qu'il forme, ou pour son propre usage pédagogique.

1. Pendant la validation (parcours [P7](P7%20-%20Valider%20les%20resultats%20Tadarida.md)), Samuel marque certaines observations comme « **séquence de référence** » via un bouton dédié.
2. Quand il a fini de constituer sa sélection, il ouvre le menu « **Exporter** » → « **Bibliothèque de sons de référence** ».
3. L'application produit une **archive ZIP** (le récapitulatif à la racine, les sons sous `sons/`),
   écrite avec progression annulable comme l'export « observations + sons » (P13) :
   ```
   bibliotheque-sons.zip
   ├── bibliotheque-sons.csv     (taxon, séquence source, fichier, fréquence, commentaire)
   └── sons/
       ├── Car640380-2026-Pass2-Z1-...20260422_212817_003.wav
       └── Car640380-2026-Pass2-Z1-...20260423_001435_001.wav
   ```
4. Samuel transmet l'archive telle quelle. Un son dont le fichier a quitté le disque est **compté**
   dans le bilan, sans bloquer l'export.

> **Reste à faire** : le rangement des sons **par espèce** (sous-dossiers `sons/<taxon>/`) demandé par
> [E8.S2](../Story%20mapping/E8%20-%20Productivite%20avancee%20Tadarida.md#e8s2) n'est pas livré : les
> sons sont à plat sous `sons/`, le taxon vit dans le CSV.

## Variante

Au lieu d'un dossier de fichiers WAV, l'application peut produire un **document récapitulatif** (HTML ou PDF) avec, par espèce : nom latin, nom vernaculaire, exemples de spectrogrammes, lien vers les fichiers WAV correspondants. Plus utile pour la transmission pédagogique. À arbitrer selon la complexité.
