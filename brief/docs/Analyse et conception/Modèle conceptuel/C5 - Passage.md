# C5 - Passage

L'unité métier centrale : une nuit complète d'enregistrement sur un point d'un site, avec un enregistreur, lors d'un n° de passage donné dans une année.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° de passage | entier | typiquement 1 ou 2 | Le protocole impose deux passages annuels (cf. [R3](Règles%20métier.md#r3)). |
| année | entier | 4 chiffres | Ex. 2026. |
| date d'enregistrement | date | obligatoire | Date du **soir** où l'enregistrement démarre. |
| heure de début | heure | obligatoire | Lue du journal du capteur, donc exprimée dans le fuseau du **site d'écoute** - pas dans celui du poste qui dépouille ([ADR 3406](https://companion-dev.echonuit.fr/decisions/3406-une-nuit-porte-le-fuseau-de-son-site/)). |
| heure de fin | heure | obligatoire | Lue du journal du capteur, même fuseau que l'heure de début. |
| paramètres d'acquisition | structure | extraits du journal du capteur | Fe, FL, FPH, S.R., gain, bande de fréquence, durée enregistrement, seuil SD. Sérialisés tels quels. Un capteur laissé plusieurs nuits au même point **repose ses paramètres à chaque redémarrage**, si bien qu'un journal en porte autant que de sessions : le passage retient ceux de **sa** nuit, c'est-à-dire la dernière configuration posée au plus tard ce soir-là (cf. [E2.S9](../Story%20mapping/E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s9)). |
| statut d'avancement | énum | `Importé` / `Transformé` / `Vérifié` / `Prêt à déposer` / **`Dépôt en cours`** / `Déposé` | Progression de la chaîne. **`Dépôt en cours`** est né du dépôt par API : le téléversement a commencé et est **reprenable** (cf. voisins ci-dessous). Le passage ne devient `Déposé` que lorsque toutes les unités sont en ligne. |
| verdict final de vérification | énum | `Non vérifié` / `OK` / `Utilisable` / `Inexploitable` | **Dérivé** des verdicts par fichier son de la [sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md), **surchargeable** à la main. Un passage `Inexploitable` ne peut pas être déposé ([R14](Règles%20métier.md#r14)). |
| commentaire de session | texte | optionnel, ≤ 2000 car. | Météo, intervention humaine, anomalie matérielle, etc. |
| données météo structurées | structure | optionnelles | T° début/fin nuit, couverture nuageuse, vent. À aligner sur les champs Vigie-Chiro pour faciliter le dépôt. |
| date de dépôt sur Vigie-Chiro | datetime | optionnelle | Tracée au dépôt. |
| participation opportuniste | booléen | par défaut faux | La nuit a bien été enregistrée, mais **hors protocole** : elle ne compte pas comme passage 1 ou 2, et [R3](Règles%20métier.md#r3) / [R4](Règles%20métier.md#r4) sont muettes pour elle (cf. [R34](Règles%20métier.md#r34)). Se déclare à l'import, dans la modale du passage, ou se **dérive** d'un carré de tiers ([R35](Règles%20métier.md#r35)). |

> **Note importante** : ce que les anciennes maquettes appelaient « session » est désormais nommé **passage** pour rester cohérent avec le vocabulaire Vigie-Chiro.

## Règles applicables

- [R3](Règles%20métier.md#r3) - fenêtres temporelles des passages 1 et 2 (alerte sans bloquer).
- [R4](Règles%20métier.md#r4) - intervalle ≥ 1 mois entre les deux passages d'un site.
- [R5](Règles%20métier.md#r5) - unicité du quadruplet `(Site, Point, Année, n° de passage)`.
- [R14](Règles%20métier.md#r14) - un passage `Inexploitable` ne peut pas être déposé.
- [R34](Règles%20métier.md#r34) - une nuit **opportuniste** est hors décompte du protocole, et exemptée de [R3](Règles%20métier.md#r3) / [R4](Règles%20métier.md#r4).

## Voisins dans le modèle

- **Sur** un [Point d'écoute](C3%20-%20Point%20d%27écoute.md).
- **Regroupé par** 0..1 [Campagne](C16%20-%20Campagne.md) : rattachement **facultatif**, purement organisationnel. Supprimer la campagne détache le passage sans l'effacer.
- **Produit par** un [Enregistreur](C4%20-%20Enregistreur.md).
- **Produit** exactement 1 [Session d'enregistrement](C6%20-%20Session%20d%27enregistrement.md).
- **À vérifier par** 0..1 [Sélection d'écoute](C11%20-%20Sélection%20d%27écoute.md).
- **Annoté par** 0..1 [Résultats d'identification](C12%20-%20Résultats%20d%27identification.md).
- **Déployé avec** 0..1 **Matériel micro** (table `passage_equipment`, 1:1) : position (sol / canopée), hauteur, type de micro **de cette nuit-là**. L'information micro a migré de l'enregistreur vers le déploiement d'un passage (EPIC #543).
- **Déposé par** 0..N **Unité de dépôt** (table `depot_unite`) et 0..1 **Plan de dépôt** (`depot_plan`) : le dépôt reprenable, unité par unité (archive ZIP ou séquence WAV), avec l'empreinte de la liste source. Support de l'état `Dépôt en cours`.
- **Traité par** 0..1 **Traitement de participation** (table `participation_traitement`) : l'**état relevé** du calcul Tadarida côté serveur (planifié / en cours / fini / erreur), avec la date de notre dernière lecture. C'est un **cache d'observation**, jamais faisant autorité.
- **Ancré par** 0..1 [Lien VigieChiro](index.md) (`vigiechiro_link`, entité `passage`) : l'`_id` de la **participation** créée au dépôt, prérequis de l'import des résultats.

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
