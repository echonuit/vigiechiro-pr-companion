# M7 - Modale de chargement du CSV Tadarida

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E2.S4 Charger un CSV d'observations Tadarida](../Story%20mapping.md#e2s4-charger-un-csv-dobservations-tadarida-5-pts)

## Étape 1 - Sélection du fichier

```
┌──────────────────────────────────────────────────────────────────────┐
│  📊 Charger les observations Tadarida                             ✕  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Session cible : du 22/04/2026 - PR n° 1925492                        │
│                                                                      │
│  Sélectionnez le fichier CSV téléchargé depuis VigieChiro            │
│  (généralement nommé `<uuid>-participation-<uuid>-observations.csv`).│
│                                                                      │
│       ┌────────────────────────────────────────────────────────┐    │
│       │                                                        │    │
│       │              📄                                        │    │
│       │                                                        │    │
│       │      Glisser-déposer le fichier CSV ici               │    │
│       │                                                        │    │
│       │                  ou                                    │    │
│       │                                                        │    │
│       │           [Parcourir...]                               │    │
│       │                                                        │    │
│       └────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ℹ️ L'application accepte les deux formats produits par VigieChiro : │
│     • `*-observations.csv`  (avec guillemets)                       │
│     • `*-observations_Vu.csv` (déjà passé en validation)            │
│                                                                      │
│                                            [Annuler]                 │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 2 - Vérification (après sélection)

```
┌──────────────────────────────────────────────────────────────────────┐
│  📊 Charger les observations Tadarida                             ✕  │
├──────────────────────────────────────────────────────────────────────┤
│  ✅ Fichier reconnu                                                  │
│     8a4fa63b…-participation-69e9db61…-observations.csv (1.2 Mo)      │
│                                                                      │
│  Format : observations brutes (avec guillemets)                      │
│  Encodage : UTF-8                                                    │
│  Lignes lues : 4 031 observations                                    │
│                                                                      │
│  Croisement avec la session :                                        │
│   ✅ 4 031 / 4 031 observations correspondent à un WAV de la session │
│                                                                      │
│  Distribution des taxa :                                             │
│   • noise   2 102 (52 %)    • Pippip  638 (16 %)                     │
│   • piaf      649 (16 %)    • Nyclei  139 ( 3 %)                     │
│   • Tadten     89 ( 2 %)    • Rhihip   80 ( 2 %)                     │
│   • 22 autres taxa  ............ 363 ( 9 %)                          │
│                                                                      │
│  ⚠️ Si vous chargez ce CSV alors qu'un autre est déjà présent pour   │
│     cette session, il sera **remplacé**.                              │
│   ( ) Remplacer les observations existantes                          │
│   (●) Fusionner (garder les validations utilisateur déjà saisies)    │
│                                                                      │
│                              [Annuler]    [Charger ce CSV]           │
└──────────────────────────────────────────────────────────────────────┘
```

## Composants & comportements

| Composant | Rôle | Notes |
|---|---|---|
| Zone drag-and-drop / `Parcourir...` | Sélection du fichier CSV | Filtre extension `.csv` |
| Encart `formats acceptés` | Aide pédagogique | Précise la différence entre `observations.csv` et `observations_Vu.csv` |
| Bloc identification fichier | Affichage du nom + taille | Le nom contenant deux UUID est tronqué visuellement, tooltip = nom complet |
| Bloc Format / Encodage | Diagnostic du parser | `observations brutes` vs `format _Vu` détecté |
| Bloc Croisement | Validation de cohérence | Indique combien d'obs trouvent leur WAV de session |
| Bloc Distribution | Aperçu rapide | Top 6 taxa + agrégat des autres |
| Choix Remplacer / Fusionner | Politique de conflit | Visible uniquement si CSV déjà présent ; **Fusionner par défaut** pour ne jamais perdre les validations utilisateur (cf. [O7](../../Objectifs%20qualités/Objectifs%20qualités/O7.md)) |

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Format CSV illisible | « ❌ Ce fichier n'est pas un CSV Tadarida valide. Colonnes attendues : `nom du fichier;temps_debut;…`. Trouvé : `…` » |
| Encodage non UTF-8 | « ⚠️ L'encodage détecté est ISO-8859-1. L'application va tenter une conversion. [Continuer] [Annuler] » |
| Aucune obs ne croise un WAV de la session | « ❌ Aucune des 4 031 observations ne correspond à un WAV de cette session. Vérifiez que vous chargez le CSV sur la **bonne session**. [Annuler] » |
| Croisement partiel (<100 %) | Bloc Croisement en orange : « ⚠️ 3 824 / 4 031 obs correspondent (95 %). 207 obs réfèrent à des WAV introuvables. [Voir le détail] [Charger quand même] » |

## États

| État | Apparence |
|---|---|
| Format `_Vu.csv` détecté | Bloc Format : « format de validation (`_Vu.csv`) - les champs `observateur_*` non vides seront importés comme validations existantes » |
| CSV déjà présent | Bandeau d'avertissement + choix Remplacer/Fusionner visible |
| Premier chargement | Choix Remplacer/Fusionner masqué |

## À ne PAS faire

- Pas de chargement automatique « magique » au moment de l'import du dossier de session : le CSV vient plus tard, après passage par VigieChiro, c'est une étape consciente.
- Pas de fusionner sans le dire : afficher explicitement la politique choisie (Remplacer / Fusionner).
- Pas de masquer les obs qui ne croisent pas un WAV : c'est un signal de problème d'import qu'il faut rendre visible.
