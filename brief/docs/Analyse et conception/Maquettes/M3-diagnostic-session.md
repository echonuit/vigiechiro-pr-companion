# M3 - Diagnostic de session

> **Type** : onglet de [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P5 Suivi du matériel](../Parcours%20utilisateurs.md#p5-suivi-du-materiel)
> **Stories couvertes** : [E6.S1 Courbe T°/H](../Story%20mapping.md#e6s1-visualiser-la-courbe-th-dune-session-5-pts), [E6.S2 Niveau de batterie](../Story%20mapping.md#e6s2-voir-le-niveau-de-batterie-debutfin-2-pts), [E6.S3 Évènements anormaux](../Story%20mapping.md#e6s3-lister-les-evenements-anormaux-du-logpr-3-pts) (COULD)

## Wireframe

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ← Retour au journal           Session du 22/04/2026 - PR n° 1925492          │
├──────────────────────────────────────────────────────────────────────────────┤
│  ╔═════════════╦═════════════╗                                                │
│  ║ Métadonnées ║  Diagnostic ║                                                │
│  ╚═════════════╩═════════════╝                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  🔋 Batteries                                                        │    │
│  │     ┌───────────────────────────────────────────────────────────┐   │    │
│  │     │ Interne   début 4.1 V (90 %)   →   fin 4.0 V (90 %)  ✅  │   │    │
│  │     │ Externe   début 0.0 V ( 0 %)   →   fin 0.0 V ( 0 %)  ⚪  │   │    │
│  │     └───────────────────────────────────────────────────────────┘   │    │
│  │                                                                      │    │
│  │  🌡️ Température sur la nuit                  ☔ Hygrométrie sur la nuit│    │
│  │     ┌──────────────────────────┐         ┌──────────────────────────┐│    │
│  │     │       ╱╲          ╱╲     │         │   ╱╲                  ╱╲ ││    │
│  │     │  ╱╲╱╲    ╲   ╱╲╱╲    ╲   │         │ ╱     ╲╱╲╱╲╱╲    ╱╲╱   ╲ ││    │
│  │     │ ╱        ╲╱╱       ╲    │         │           23°C       ╲   ││    │
│  │     │              19°C        │         │              60 %        ││    │
│  │     │  20:25                07:48│         │  20:25                07:48 ││   │
│  │     └──────────────────────────┘         └──────────────────────────┘│    │
│  │     min 17.4°C • moy 18.6°C • max 19.4°C   min 58 % • moy 60 % • max 64 % │    │
│  │                                                                      │    │
│  │  ⚠️  Évènements anormaux extraits du LogPR                           │    │
│  │     ┌────────────────────────────────────────────────────────────┐   │    │
│  │     │ Aucun évènement anormal détecté sur cette nuit. ✅         │   │    │
│  │     └────────────────────────────────────────────────────────────┘   │    │
│  │     ou                                                                │    │
│  │     ┌────────────────────────────────────────────────────────────┐   │    │
│  │     │ 22/04 23:14:02 │ Réveil non programmé        │ ⚠️           │   │    │
│  │     │ 23/04 02:47:31 │ Erreur écriture SD (1 fichier perdu)│ ⚠️    │   │    │
│  │     │ 23/04 04:12:18 │ Redémarrage suite à anomalie│ 🔴           │   │    │
│  │     └────────────────────────────────────────────────────────────┘   │    │
│  │                                                                      │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  [📥 Exporter le diagnostic en CSV]                                          │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Bloc Batteries (E6.S2) | État début/fin de chaque batterie | tension V + pourcentage + indicateur ✅/⚠️/🔴 | Lu depuis les lignes `Bat. Interne X.XV (Y%)` du LogPR ; seuils : ≥80 % ✅, 50-80 % ⚠️, <50 % 🔴 |
| Graphique Température (E6.S1) | Évolution T° sur la nuit | courbe simple | Une mesure toutes les 600 s (cf. THLog.csv) |
| Graphique Hygrométrie (E6.S1) | Évolution humidité sur la nuit | courbe simple | Idem |
| Stats T° / H (sous chaque graphe) | Min, moyenne, max | calcul simple | |
| Bloc Évènements anormaux (E6.S3, COULD) | Détection des anomalies dans le LogPR | tableau date / description / sévérité | Si rien : message « Aucun évènement anormal détecté ✅ » |
| Bouton `📥 Exporter le diagnostic en CSV` | Sortie des graphes + anomalies en CSV | — | Optionnel - utile pour Karim qui prépare un rapport client |

## Catégories d'évènements anormaux à détecter

| Pattern dans le LogPR | Catégorie | Sévérité |
|---|---|---|
| `Wakeup by ALARM... Cpt N` (N > 1) ou hors plage horaire programmée | Réveil non programmé | ⚠️ |
| `Erreur écriture SD` ou `Carte SD pleine` | Erreur SD | ⚠️ ou 🔴 selon le contexte |
| `Redémarrage` non précédé d'une mise en veille | Redémarrage anormal | 🔴 |
| `Bat. Interne` < 30 % en cours de nuit | Batterie critique en cours de capture | 🔴 |
| Mise en veille avant l'horaire programmé | Mise en veille prématurée | ⚠️ |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Survol d'un point sur les graphes T°/H | Tooltip affichant la valeur précise + horodatage |
| Clic sur une ligne d'évènement anormal | Ouvre une popup avec le bloc complet du LogPR autour de l'évènement (3 lignes avant, 3 après) |
| Clic `📥 Exporter le diagnostic en CSV` | Dialogue de sauvegarde, format `<session>_diagnostic.csv` |
| Clic onglet `Métadonnées` | Retour à [M2](M2-detail-session.md) |

## États

| État | Apparence |
|---|---|
| Pas de THLog.csv (sonde absente ou défaillante) | Graphes T°/H remplacés par : « Pas de données environnementales pour cette session (sonde absente ou défaillante). » |
| Pas de batterie externe | Ligne « Externe » du bloc Batteries grisée avec mention « non utilisée » |

## À ne PAS faire

- Pas de séries temporelles compliquées (zoom, brush, sélection) - on reste sur des graphes simples lisibles d'un coup d'œil.
- Pas de mélanger T° et H sur le même axe Y (sauf si vous proposez un design BACI propre, mais à arbitrer en équipe).
- Pas de masquer le bloc « Aucun évènement anormal » : un retour positif explicite rassure l'utilisateur (« je n'ai pas oublié de regarder, c'est juste qu'il n'y a rien »).
