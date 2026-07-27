# C16 - Campagne

Un **regroupement facultatif de passages** relevant du même suivi : « Suivi ENS 2026 », « Thèse Samuel - saison 2 », « Carrés opportunistes 2026 ». La campagne ne change **rien** au protocole : elle sert à **retrouver** et à **piloter** un sous-ensemble de nuits parmi tout ce qu'un observateur accumule au fil des années.

Elle est née du besoin de lire le [solde de saison](../Maquettes/M-Saison.md) **campagne par campagne** : un observateur qui suit à la fois ses carrés personnels et ceux d'une structure ne veut pas un décompte qui mélange les deux.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom | texte | obligatoire | Intitulé libre, choisi par l'observateur (ex. « Suivi ENS 2026 »). |
| année | entier | 4 chiffres | L'année de référence du suivi. Elle n'a **pas** à coïncider avec l'année des passages rattachés : rien n'interdit de rattacher une nuit de 2025 à une campagne 2026. |
| commentaire | texte | optionnel | Contexte, commanditaire, périmètre. |

> **Objet purement organisationnel.** La campagne n'entre dans **aucune** règle métier : elle ne conditionne ni le rattachement d'un passage, ni les fenêtres [R3](Règles%20métier.md#r3) / [R4](Règles%20métier.md#r4), ni le dépôt. Supprimer une campagne **détache** ses passages, elle n'en efface aucun.

## Voisins dans le modèle

- **Regroupe** 0..N [Passages](C5%20-%20Passage.md), et un passage relève de **0 ou 1** campagne. Le rattachement est facultatif des deux côtés : un observateur peut ne jamais créer de campagne, et une campagne peut rester vide.

## Où elle apparaît

- **Le passage** : le rattachement se choisit dans « Modifier le passage ».
- **[M-Multisite](../Maquettes/M-MultiSite.md)** : une colonne « Campagne », triable, et un filtre.
- **[M-Saison](../Maquettes/M-Saison.md)** : un **filtre**, pas une colonne. Une ligne de solde porte deux passages, qui peuvent relever de campagnes différentes : il n'y a pas de « campagne de la ligne » à afficher.

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
