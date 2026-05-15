# M5 - Panneau de filtre des observations

> **Type** : panneau latéral / popover, déclenché depuis [M4 - Validation](M4-validation.md)
> **Parcours couverts** : [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E4.S2 Filtrer les observations](../Story%20mapping.md#e4s2-filtrer-les-observations-5-pts)

## Wireframe

```
┌──────────────────────────────────┐
│  🔍 Filtrer les observations  ✕ │
├──────────────────────────────────┤
│                                  │
│  📊 Par taxon                    │
│  ┌────────────────────────────┐  │
│  │ [Filtre rapide____________]│  │
│  ├────────────────────────────┤  │
│  │ [✓] noise           (2102) │  │
│  │ [✓] piaf            ( 649) │  │
│  │ [X] Pippip          ( 638) │  │
│  │ [X] Nyclei          ( 139) │  │
│  │ [X] Tadten          (  89) │  │
│  │ [X] Rhihip          (  80) │  │
│  │ [X] Tetvir          (  67) │  │
│  │ [X] Phanan          (  47) │  │
│  │ ...                         │  │
│  └────────────────────────────┘  │
│  [Tout cocher]  [Tout décocher] │
│  [Cocher uniquement chiroptères]│
│                                  │
│  🎯 Par probabilité Tadarida    │
│  Min : [▬▬▬▬▬●▬▬▬▬] 0.50         │
│       0                    1     │
│                                  │
│  ✅ Par statut de validation     │
│  ( ) Tous                        │
│  (●) Non passées en revue        │
│  ( ) Validées                    │
│  ( ) Corrigées                   │
│                                  │
│  🕐 Par plage horaire (SHOULD)   │
│  De [20:00▼] à [03:00▼]          │
│                                  │
│  ─────────────────────────────── │
│  3 filtres actifs                │
│  Affiche : 1 247 / 4 031 obs     │
│                                  │
│  [↺ Réinitialiser]  [Appliquer]  │
└──────────────────────────────────┘
```

## Composants

| Composant | Rôle | Données | Notes |
|---|---|---|---|
| Bouton `✕` | Ferme le panneau sans appliquer si l'utilisateur a fait des modifs | — | Demande confirmation si modifs en attente |
| **Filtre par taxon** | Multi-sélection | liste des taxa présents dans la session avec leur effectif | Trié par effectif décroissant |
| Champ recherche taxon | Filtre rapide dans la liste | texte | Utile quand on veut isoler un genre précis |
| Boutons raccourcis | Sélections groupées | `Tout cocher`, `Tout décocher`, `Cocher uniquement chiroptères` | Le 3e exclut `noise` et `piaf` (préset très utilisé) |
| **Filtre par probabilité** | Seuil minimum | slider 0-1 avec affichage de la valeur | Utile pour tri par confiance |
| **Filtre par statut** | Radio | `Tous / Non passées / Validées / Corrigées` | Statut côté utilisateur (cf. M4) |
| Filtre par plage horaire (SHOULD - non MVP) | Borne start/end | menus heures | Dérivé de la plage temporelle de chaque obs (croisement avec horodatage du WAV) - peut être différé |
| Compteur en pied | Bilan vivant | `nb filtres actifs`, `obs affichées / obs totales` | Mis à jour à chaque modification |
| Bouton `Appliquer` | Persiste les filtres et ferme | — | Raccourci `Entrée` |
| Bouton `↺ Réinitialiser` | Efface tous les filtres | — | Confirmation si plus de 3 filtres actifs |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Toggle d'un taxon | Compteur en pied recalculé immédiatement |
| Mouvement du slider probabilité | Compteur recalculé en temps réel |
| Saisie dans le champ recherche taxon | Filtre la liste affichée sans modifier les sélections |
| Clic raccourci `Cocher uniquement chiroptères` | Décoche `noise` et `piaf`, coche tout le reste |
| Clic `Appliquer` | Ferme le panneau, refilter la TableView de M4, scroll en haut |
| Clic `Réinitialiser` | Tous les taxa cochés, slider à 0, statut `Tous`, plage horaire vide |
| Esc | Annule et ferme |

## États

| État | Apparence |
|---|---|
| Aucun filtre actif | Pas d'indicateur sur le bouton Filtres de M4 |
| Filtres actifs | Badge sur bouton `[🔍 Filtres ▼ (3)]` indiquant le nombre |
| Filtre incohérent (0 obs résultantes) | Compteur en pied : `0 / 4031 obs`, fond du compteur en orange, bouton `Appliquer` reste actif (l'utilisateur peut décider d'avoir 0 obs) |

## Persistance

Les filtres actifs sont **mémorisés par session** : si on revient sur M4 d'une autre session puis on rouvre celle-ci, les derniers filtres appliqués sont restaurés. Cela évite à Karim de recliquer 5 fois après chaque navigation.

## À ne PAS faire

- Pas de panneau modal qui bloque la fenêtre principale : laisser visible la TableView pour permettre la rétroaction visuelle pendant la modification des filtres.
- Pas de séparer les filtres dans plusieurs onglets : tout doit tenir sur un panneau scrollable d'un coup d'œil.
- Pas de pré-cocher `Cocher uniquement chiroptères` au premier lancement : laisser le choix à l'utilisateur (Marie veut peut-être justement écouter un piaf douteux).
