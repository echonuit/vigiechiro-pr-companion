# M1 - Accueil / Journal des sessions

> **Type** : vue principale (premier écran à l'ouverture de l'application)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E1.S1 Voir le journal](../Story%20mapping.md#e1s1-voir-le-journal-de-mes-sessions-3-pts), [E1.S6 Tagger par chantier](../Story%20mapping.md#e1s6-tagger-une-session-par-chantier-projet-8-pts) (SHOULD)

## Wireframe

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🦇  VigieChiro PR Companion                              [⚙ Préférences]  ─□✕│
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Mes sessions                              [+ Importer une nuit]  [↻ Actualiser] │
│  ──────────────────────────────────────────────────────────────────────────  │
│                                                                              │
│  Filtre rapide :  Statut [Tous          ▼]   Tag [Aucun     ▼]    🔍 [_____] │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │ 📅 Date ▼   │ PR n°    │ Durée │ WAV  │ Obs.  │ Tag           │ Statut    │    │
│  ├──────────────────────────────────────────────────────────────────────┤    │
│  │ 2026-04-22  │ 1925492  │ 11h22 │ 1572 │ 4031  │ Carré 640380  │ ⬛ Importée│    │
│  │ 2026-04-15  │ 1925492  │ 11h05 │ 1408 │  3621 │ Carré 640380  │ 🟡 En cours│    │
│  │ 2026-04-08  │ 1925492  │ 10h47 │  892 │  2104 │ Carré 640380  │ ✅ Validée │    │
│  │ 2026-03-28  │ 1925487  │ 09h38 │  421 │   985 │ Test_Maison   │ 📤 Exportée│    │
│  │ 2026-03-22  │ 1925487  │ 10h11 │ 1109 │  3204 │ —             │ 📤 Exportée│    │
│  │ ...                                                                  │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  5 sessions • 4 402 observations • 3 nuits restant à valider                 │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Barre de titre | Identité visuelle, accès aux préférences | nom de l'app, bouton ⚙ | |
| Bouton `+ Importer une nuit` | Déclenche [M6 - Modale d'import](M6-modale-import.md) | — | Action principale, mise en avant visuelle |
| Bouton `↻ Actualiser` | Recharge la liste depuis la base | — | Utile si plusieurs imports en parallèle |
| Filtre statut | Restreint la liste aux sessions de tel statut | menu déroulant : `Tous / Importée / En cours / Validée / Exportée` | |
| Filtre tag | Restreint aux sessions taggées | auto-complétion sur les tags utilisés (E1.S6, SHOULD) | À masquer si E1.S6 non livrée |
| Champ recherche `🔍` | Filtre full-text (n° PR, date, tag) | — | Optionnel |
| **Tableau des sessions** | Liste tabulaire triée par date décroissante par défaut | une ligne par session | Colonnes triables au clic sur l'en-tête |
| Colonne Date | date de capture (nuit) | format `AAAA-MM-JJ` | |
| Colonne PR n° | numéro de série du PR | extrait du LogPR ou du nom de fichier | |
| Colonne Durée | durée de la session de capture | format `HHhMM` | calculée début → fin du LogPR |
| Colonne WAV | nombre de fichiers WAV bruts | entier | |
| Colonne Obs. | nombre d'observations Tadarida (vide si CSV non chargé) | entier ou `—` | |
| Colonne Tag | libellé tag (E1.S6) | texte ou `—` | À masquer si E1.S6 non livrée |
| Colonne Statut | état du cycle de vie de la session | enum + icône colorée | |
| Barre de statut | Bilan global | nb de sessions, nb d'obs total, nb de sessions à valider | Mise à jour réactive |

## Légende des statuts

| Icône | Statut | Signification |
|---|---|---|
| ⬛ | Importée | WAV + LogPR importés, pas de CSV Tadarida |
| 🟡 | En cours | CSV chargé, validation en cours |
| ✅ | Validée | Toutes les observations passées en revue |
| 📤 | Exportée | CSV `_Vu` produit, prêt à téléverser sur VigieChiro |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Clic sur une ligne du tableau | Ouvre [M2 - Détail de session](M2-detail-session.md) |
| Double-clic sur une ligne | Ouvre directement [M4 - Vue de validation](M4-validation.md) si CSV chargé |
| Clic en-tête de colonne | Tri par cette colonne (toggle asc/desc, indicateur ▲/▼) |
| Clic `+ Importer une nuit` | Ouvre [M6 - Modale d'import](M6-modale-import.md) |
| Drag-and-drop d'un dossier sur la fenêtre | Équivalent à `+ Importer une nuit` avec dossier pré-rempli |
| Sélection d'un statut dans le filtre | Filtre client-side immédiat, pas de rechargement |
| Sélection multiple (Ctrl+clic, Maj+clic) | Active une barre d'actions contextuelles : `Comparer` (E6.S4 WON'T), `Supprimer` (E1.S5) |

## États

| État | Apparence |
|---|---|
| Application vide (aucune session) | Tableau remplacé par un encart central : « Aucune session importée pour le moment. Cliquez sur **+ Importer une nuit** pour démarrer. » |
| Filtre vide (aucun résultat) | Tableau remplacé par : « Aucune session ne correspond aux filtres. [Réinitialiser les filtres] » |
| Import en cours en arrière-plan | Barre de statut affiche : « ⏳ Import de la session du 2026-04-22… 47 % » |

## À ne PAS faire

- Pas de menu hamburger : les actions doivent être visibles directement.
- Pas d'icônes seules sans label dans le tableau (Marie ne doit jamais deviner la signification d'un pictogramme).
- Pas de pagination : on affiche toutes les sessions, le tri/filtre suffit même à plusieurs centaines de lignes (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)).
