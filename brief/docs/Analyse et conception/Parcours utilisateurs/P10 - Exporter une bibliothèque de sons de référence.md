# P10 - Exporter une bibliothèque de sons de référence 🎼

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Samuel. **Objectifs qualité visés** : aucun direct - c'est une fonctionnalité bonus.

Samuel a validé plusieurs centaines d'observations sur la saison. Il veut **constituer une petite bibliothèque de sons de référence** par espèce (les meilleurs exemples qu'il a entendus) pour la transmettre à un débutant qu'il forme, ou pour son propre usage pédagogique.

1. Pendant la validation (parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)), Samuel marque certaines observations comme « **séquence de référence** » via un bouton dédié.
2. Quand il a fini de constituer sa sélection, il ouvre le menu « **Exporter** » → « **Bibliothèque de sons de référence** ».
3. L'application produit un dossier organisé par espèce :
   ```
   bibliotheque/
     Pippip - Pipistrellus pipistrellus/
       Car640380-2026-Pass2-Z1-...20260422_212817_003.wav
       Car640380-2026-Pass2-Z1-...20260423_001435_001.wav
     Nyclei - Nyctalus leisleri/
       ...
   ```
4. Samuel peut zipper le dossier et le partager.

## Variante

Au lieu d'un dossier de fichiers WAV, l'application peut produire un **document récapitulatif** (HTML ou PDF) avec, par espèce : nom latin, nom vernaculaire, exemples de spectrogrammes, lien vers les fichiers WAV correspondants. Plus utile pour la transmission pédagogique. À arbitrer selon la complexité.
