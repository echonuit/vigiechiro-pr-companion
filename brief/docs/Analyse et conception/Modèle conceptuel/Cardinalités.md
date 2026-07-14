# Cardinalités

Tableau récapitulatif des cardinalités d'association du modèle conceptuel. Le diagramme complet est sur la [page d'accueil](index.md) (ou en [vue plein écran](Diagramme%20-%20plein%20écran.md)).

| De | Association | Vers | Cardinalité | Sens métier |
|---|---|---|---|---|
| [Utilisateur](C1%20-%20Utilisateur.md) | possède | [Site de suivi](C2%20-%20Site%20de%20suivi.md) | 1..* | Marie en a 1, Karim 2-3, Samuel 36+ |
| [Site de suivi](C2%20-%20Site%20de%20suivi.md) | contient | [Point d'écoute](C3%20-%20Point%20d%27écoute.md) | 1..* | un site sans point n'a pas de sens |
| [Point d'écoute](C3%20-%20Point%20d%27écoute.md) | fait l'objet de | [Passage](C5%20-%20Passage.md) | 0..* | un point peut n'avoir aucun passage encore |
| [Enregistreur](C4%20-%20Enregistreur.md) | a produit | [Passage](C5%20-%20Passage.md) | 1..* | un même enregistreur peut faire plusieurs nuits |
| [Enregistreur](C4%20-%20Enregistreur.md) | porte | [Micro](C4bis%20-%20Micro.md) | 1..1 | un PR a un micro courant ; un changement de micro = mise à jour de cette association |
| [Passage](C5%20-%20Passage.md) | produit | [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) | 1..1 | un passage donne exactement une session d'enregistrement |
| [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) | contient | [Enregistrement original](C7%20-%20Enregistrement%20original.md) | 1..* | typiquement plusieurs centaines à plusieurs milliers |
| [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) | contient | [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) | 1..* | typiquement 1,3 × le nombre d'enregistrements originaux |
| [Enregistrement original](C7%20-%20Enregistrement%20original.md) | découpé en | [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) | 1..* | un enregistrement original donne 1 à N séquences, une par tranche de 5 s réelles, ralenties ×10 |
| [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) | référence | [Journal du capteur](C9%20-%20Journal%20du%20capteur.md) | 1..1 | un seul journal par passage |
| [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md) | référence | [Relevé climatique](C10%20-%20Relevé%20climatique.md) | 0..1 | absent si la sonde T°/H est défaillante |
| [Passage](C5%20-%20Passage.md) | à vérifier par | [Sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md) | 0..1 | créée au moment de la vérification utilisateur |
| [Sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md) | porte sur | [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) | 1..* | typiquement 10-30 séquences |
| [Passage](C5%20-%20Passage.md) | annoté par | [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md) | 0..1 | rempli après retour Tadarida (SHOULD) |
| [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md) | agrège | [Observation](C13%20-%20Observation.md) | 1..* | plusieurs milliers par fichier de résultats |
| [Observation](C13%20-%20Observation.md) | détectée dans | [Séquence d'écoute](C8%20-%20Séquence%20d%27écoute.md) | 1..1 | chaque observation référence une séquence précise |
| [Observation](C13%20-%20Observation.md) | classée comme | [Taxon](C14%20-%20Taxon.md) | 1..1 | au moment de l'import du fichier de résultats |
| [Taxon](C14%20-%20Taxon.md) | appartient | [Groupe taxonomique](C15%20-%20Groupe%20taxonomique.md) | 1..1 | exemple : Pippip → Pipistrellus → Vespertilionidae |

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
