# M4 - Vue principale de validation

> **Type** : vue principale (l'écran le plus utilisé pendant la SAE)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier), [P3 Validation approfondie](../Parcours%20utilisateurs.md#p3-validation-approfondie)
> **Stories couvertes** : [E4.S1 Liste obs](../Story%20mapping.md#e4s1-voir-la-liste-des-observations-dune-session-3-pts), [E4.S3 Valider](../Story%20mapping.md#e4s3-valider-une-observation-tadarida-est-correct-3-pts), [E4.S4 Corriger](../Story%20mapping.md#e4s4-corriger-une-observation-proposer-un-autre-taxon-3-pts), [E4.S5 Commenter obs](../Story%20mapping.md#e4s5-annoter-une-observation-avec-un-commentaire-libre-2-pts) (SHOULD), [E3.S1 Lecture audio](../Story%20mapping.md#e3s1-lecture-audio-ralentie-dun-wav-8-pts), [E3.S2 Vitesse](../Story%20mapping.md#e3s2-regler-la-vitesse-de-lecture-3-pts) (SHOULD), [E3.S4 Navigation](../Story%20mapping.md#e3s4-lecture-des-observations-adjacentes-2-pts) (SHOULD)

## Wireframe

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│  ← Détail session    Validation - Session du 22/04/2026 - PR n° 1925492        ?  ─□✕   │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│  [🔍 Filtres ▼]  4 031 obs • 1 269 validées • 22 corrigées • 2 740 à voir                │
│  ┌────────────────────────────────────────────┬────────────────────────────────────────┐ │
│  │ Fichier ▲ │ Début │ Fin   │ Freq │ Tax. │ │  Détail de l'observation               │ │
│  │           │ (s)   │ (s)   │ (Hz) │ Tad. │ │  ──────────────────────────────────────│ │
│  ├────────────────────────────────────────────┤                                        │ │
│  │ ...20262_000 │ 0.4 │ 2.5 │ 153  │ noise │█│  📁 Car...20260422_202817_000.wav     │ │
│  │ ...20262_000 │ 0.2 │ 0.3 │   9  │ piaf  │ │  ⏱ de 0.7 s à 1.2 s (durée 0.5 s)     │ │
│  │ ...20264_000 │ 2.1 │ 2.2 │ 188  │ noise │ │  📊 Fréquence médiane : 47 kHz        │ │
│  │ ...20264_000 │ 0.5 │ 4.4 │   8  │ piaf  │ │                                        │ │
│  │ ◀ ...20281_000 │ 0.7 │ 1.2 │  47  │ Pippip │  Tadarida → Pippip (probabilité 0.45)│ │
│  │ ...20281_000 │ 1.1 │ 1.4 │  41  │ Pippip │ │  Pipistrellus pipistrellus           │ │
│  │ ...20283_000 │ 0.3 │ 1.8 │ 153  │ noise │ │                                        │ │
│  │ ...20283_000 │ 0.4 │ 2.2 │  41  │ Pippip│ │  ┌────────────────────────────────┐ │ │ │
│  │ ...20284_000 │ 0.6 │ 0.9 │  19  │ Tadten│ │  │ ▆▆▇█▇▆▅▃▂▁ ▁▂▃▅▆▇█▇▆▆▆▅▄▃▂▁    │ │ │ │
│  │ ...20284_000 │ 0.2 │ 4.1 │ 154  │ noise │ │  │ Forme d'onde (E3.S3, COULD)    │ │ │ │
│  │ ...                                       │ │  └────────────────────────────────┘ │ │ │
│  │                                           │ │                                      │ │ │
│  └────────────────────────────────────────────┘ │  ▶ Lecture ralentie  ×[10▼] 0:02/0:05│ │
│                                                 │  [⏮]  [⏯]  [⏭]   [↺ Recommencer]    │ │
│   Statut affiché : [Tous ▼]                     │                                      │ │
│                                                 │  ──────────────────────────────────  │ │
│   Légende :                                     │  Votre validation                    │ │
│     ●  Non passée en revue                      │   (●) Tadarida est correct (Pippip)  │ │
│     ✅ Validée (Tadarida correct)               │   ( ) Corriger en   [Pipkuh    ▼]    │ │
│     🔄 Corrigée (autre taxon)                   │       Probabilité   [0.85___]        │ │
│     💬 Avec commentaire                          │                                      │ │
│                                                 │  📝 Commentaire (E4.S5)              │ │
│                                                 │  ┌────────────────────────────────┐ │ │
│                                                 │  │ Pic 39 kHz, morphologie atypique│ │ │
│                                                 │  └────────────────────────────────┘ │ │
│                                                 │                                      │ │
│                                                 │  [✅ Valider]   [↺ Réinitialiser]    │ │
│                                                 └────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

## Composants

### Panneau gauche - Liste des observations

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| Bouton `[🔍 Filtres ▼]` | Ouvre [M5 - Panneau filtre](M5-filtre-observations.md) | indicateur du nb de filtres actifs si > 0 | |
| Compteur global | Bilan de la session | `total / validées / corrigées / à voir` | Mis à jour réactivement |
| **TableView** | Liste paginée virtuellement | une ligne par observation | E4.S1 |
| Colonne Fichier | Nom du WAV (tronqué) | suffixe + `_000` | Tooltip = nom complet |
| Colonne Début/Fin | Bornes temporelles dans le WAV | secondes (1 décimale) | |
| Colonne Freq | Fréquence médiane | Hz (entier) | |
| Colonne Tax. Tad. | Taxon Tadarida | code 6 lettres | Tooltip = nom latin complet |
| Colonne probabilité (non visible mais triable) | Probabilité Tadarida | float 0-1 | Visualisable via tri |
| Indicateur de statut (1re colonne) | État de validation | ● ✅ 🔄 💬 selon `validation_state` | combiné avec couleur de fond ligne |

### Panneau droit - Détail de l'observation sélectionnée

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| En-tête fichier | Identification | nom WAV cliquable (ouvre dans l'OS) | |
| Plage temporelle | Bornes dans le WAV | `de X.X s à Y.Y s (durée Z.Z s)` | |
| Fréquence médiane | Métrique Tadarida | `xx kHz` | |
| Bloc proposition Tadarida | Lecture du CSV | code + nom latin + probabilité | Couleur indicateur de confiance |
| Forme d'onde | Visualisation audio (E3.S3 COULD) | rendu simple PNG ou Canvas | À masquer si E3.S3 non livrée |
| **Lecteur audio** | Lecture ralentie (E3.S1, E3.S2) | bouton ▶, sélecteur vitesse, position courante / durée | E3.S2 vitesse réglable dans [×5, ×10, ×20] |
| Boutons navigation | E3.S4 (SHOULD) | ⏮ obs précédente, ⏯ play/pause, ⏭ obs suivante, ↺ recommencer | Raccourcis clavier : flèches haut/bas pour précédente/suivante, espace pour play/pause |
| Bloc validation | Saisie utilisateur | radio buttons + combobox + champ probabilité | E4.S3, E4.S4 |
| Champ commentaire (E4.S5) | Annotation libre | TextArea | SHOULD - masquer si non livré |
| Bouton `✅ Valider` | Persiste la décision | — | Désactivé tant que rien n'a été choisi |
| Bouton `↺ Réinitialiser` | Annule la décision | — | Re-passe à `non passée en revue` |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Sélection d'une ligne | Charge le détail dans le panneau droit + sélection de l'audio |
| Tri par colonne (clic en-tête) | Tri immédiat (E4.S1, performance < 100 ms cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)) |
| Clic `[🔍 Filtres ▼]` | Ouvre [M5](M5-filtre-observations.md) (panneau latéral ou popover) |
| Clic ▶ | Démarre la lecture audio à la vitesse sélectionnée |
| Sélection vitesse | Change le facteur de ralentissement, sans interrompre la lecture (E3.S2) |
| Clic ⏭ | Sélectionne l'obs suivante dans la liste filtrée |
| Choix d'un autre taxon | Le statut « Tadarida est correct » bascule automatiquement sur « Corriger » |
| Clic `✅ Valider` | Persiste, marque l'obs comme `validée` ou `corrigée` selon le radio actif, puis avance automatiquement à l'obs suivante |
| Saisie commentaire | Sauvegarde immédiate (debounce ~500 ms) |
| Raccourcis clavier | `↓` / `↑` = navigation, `espace` = play/pause, `Entrée` = valider, `c` = focus champ commentaire |

## États

| État | Apparence |
|---|---|
| Aucune obs sélectionnée | Panneau droit affiche : « Sélectionnez une observation à gauche pour commencer la validation. » |
| Lecture en cours | Bouton ▶ devient ⏸, position courante mise à jour 10× par seconde |
| Audio absent (WAV manquant) | Lecteur remplacé par : « ⚠️ Le fichier WAV correspondant est introuvable dans `kal/`. Vérifiez l'import. » |
| Liste vide après filtre | TableView remplacée par : « Aucune observation ne correspond aux filtres actifs. [Effacer les filtres] » |
| Validation enregistrée | Toast discret en bas à droite : « ✅ Observation validée » (1 seconde) |

## Performance attendue

- Affichage initial : < 1 s sur le sample (473 obs).
- Tri / filtre : < 100 ms sur le full dataset (4031 obs) - cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md).
- Démarrage lecture audio : < 200 ms après clic.
- Décodage WAV ralenti : sans glitch perceptible (cf. [O4](../../Objectifs%20qualités/Objectifs%20qualités/O4.md)).

## À ne PAS faire

- Pas de modale au moment de valider une observation : la validation doit être au clic, sans interstitiel (Marie traite 200 obs par session).
- Pas de scroll infini dans la liste : si la performance le permet pas, virtualiser la TableView (le composant JavaFX `TableView` le fait nativement).
- Pas d'affichage du panneau de détail dans une fenêtre séparée : split-pane vertical fixe pour garder le contexte.
