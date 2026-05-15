# M2 - Fiche détail d'une session

> **Type** : vue secondaire (ouverte depuis [M1 - Accueil](M1-accueil.md))
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E1.S2](../Story%20mapping.md#e1s2-voir-le-detail-dune-session-3-pts), [E1.S3](../Story%20mapping.md#e1s3-annoter-une-session-avec-un-commentaire-libre-2-pts), [E1.S4](../Story%20mapping.md#e1s4-marquer-une-session-comme-validation-terminee-2-pts)

## Wireframe

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ← Retour au journal           Session du 22/04/2026 - PR n° 1925492          │
├──────────────────────────────────────────────────────────────────────────────┤
│  ╔═════════════╦═════════════╗                                                │
│  ║ Métadonnées ║  Diagnostic ║                                                │
│  ╚═════════════╩═════════════╝                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  📅 Capture                                                          │    │
│  │     Début   : 22/04/2026 20:25:53                                    │    │
│  │     Fin     : 23/04/2026 07:48:00                                    │    │
│  │     Durée   : 11 h 22 min                                            │    │
│  │                                                                      │    │
│  │  📡 Acquisition                                                      │    │
│  │     PR n°            : 1925492                                       │    │
│  │     Fréquence éch.  : 384 kHz                                        │    │
│  │     Bande de freq.  : 8 - 120 kHz                                    │    │
│  │     Gain            : 0 dB                                           │    │
│  │     Filtre passe-haut : 0 (aucun)                                    │    │
│  │     Sensibilité      : 16 dB                                          │    │
│  │                                                                      │    │
│  │  📁 Fichiers                                                         │    │
│  │     Dossier source  : /home/marie/PR_avril/22-04           [📂 Ouvrir]│    │
│  │     WAV bruts       : 1572                                           │    │
│  │     WAV redécoupés  : 2114 (Tadarida)                                │    │
│  │     Observations    : 4031 (dont 1247 validées, 22 corrigées)        │    │
│  │                                                                      │    │
│  │  🏷️  Tag (optionnel)                                                  │    │
│  │     [ Carré 640380 - Pass 2___________________________ ▼]            │    │
│  │                                                                      │    │
│  │  📝 Commentaire libre                                                │    │
│  │     ┌────────────────────────────────────────────────────────────┐   │    │
│  │     │ Nuit dégagée, légère brise. Capture lancée avant la pluie.│   │    │
│  │     │ RAS sur le matériel.                                       │   │    │
│  │     └────────────────────────────────────────────────────────────┘   │    │
│  │                                                                      │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  [📊 Charger CSV Tadarida]   [🎧 Ouvrir la validation]   [📤 Exporter]   [🗑 Supprimer] │
│                                                                              │
│  Statut : 🟡 Validation en cours - 1 269 / 4 031 observations passées en revue│
│  [✅ Marquer comme validation terminée]                                      │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Composants

| Composant | Rôle | Données affichées | Notes |
|---|---|---|---|
| Bouton `← Retour au journal` | Revient à [M1](M1-accueil.md) | — | Persiste les filtres actifs sur M1 |
| Titre de page | Identifie la session affichée | date + n° PR | |
| **Onglets** `Métadonnées / Diagnostic` | Bascule entre les deux vues | — | Onglet `Diagnostic` → [M3](M3-diagnostic-session.md) |
| Bloc Capture | Plage temporelle | début, fin, durée | Extrait du LogPR |
| Bloc Acquisition | Paramètres techniques du PR | Fe, bande, gain, FPH, sensibilité | Extrait de la ligne `Paramètres : ...` du LogPR |
| Bloc Fichiers | Volumétrie + accès au dossier source | nb WAV bruts, nb WAV kal, nb obs (avec décompte validées/corrigées) | Bouton `📂 Ouvrir` lance l'explorateur OS sur le dossier |
| Champ Tag | Tag de la session (E1.S6 SHOULD) | combobox + auto-complétion | Masquer si E1.S6 non livrée |
| **Champ Commentaire libre** | Annotation libre (E1.S3) | TextArea multi-ligne, max 2000 caractères | Sauvegarde immédiate au blur |
| Boutons d'action principale | Actions sur la session | 4 boutons | cf. tableau Interactions |
| Statut + barre de progression | Avancement de la validation | `nb_validées / nb_total` + jauge | |
| Bouton `✅ Marquer comme validation terminée` | E1.S4 | — | Désactivé si toutes les obs n'ont pas été passées en revue, **avec avertissement** au clic (cf. interaction) |

## Interactions

| Action utilisateur | Comportement attendu |
|---|---|
| Clic onglet `Diagnostic` | Bascule vers [M3 - Diagnostic](M3-diagnostic-session.md) (même fenêtre) |
| Saisie dans le commentaire | Sauvegarde immédiate (debounce ~500 ms), pas de bouton « Enregistrer » |
| Saisie dans le tag | Auto-complétion sur les tags existants, création d'un nouveau tag si inexistant |
| Clic `📂 Ouvrir` | Lance l'explorateur de fichiers de l'OS sur le dossier source |
| Clic `📊 Charger CSV Tadarida` | Ouvre [M7 - Modale de chargement CSV](M7-modale-csv-tadarida.md) |
| Clic `🎧 Ouvrir la validation` | Ouvre [M4 - Vue de validation](M4-validation.md) (désactivé si CSV non chargé) |
| Clic `📤 Exporter` | Ouvre [M8 - Modale d'export](M8-modale-export.md) (désactivé si statut ≠ Validée ou Exportée) |
| Clic `🗑 Supprimer` | Ouvre [M9 - Modale de suppression](M9-modale-suppression.md) |
| Clic `✅ Marquer validation terminée` | Si toutes les obs validées → applique direct. Sinon → modale d'avertissement « Il reste N observations non passées en revue. Continuer quand même ? [Continuer] [Annuler] » |
| Clic `← Retour au journal` | Retour à [M1](M1-accueil.md), pas de confirmation (tout est sauvegardé) |

## États

| État | Apparence |
|---|---|
| CSV Tadarida non chargé | Bloc Fichiers : `Observations : pas encore chargées`. Boutons `🎧 Ouvrir validation` et `📤 Exporter` désactivés (avec tooltip explicatif). |
| Statut = Importée | Statut affiché : `⬛ Importée - en attente du CSV Tadarida` |
| Statut = Validation en cours | Barre de progression visible |
| Statut = Validée | Bouton `✅ Marquer validation terminée` remplacé par badge `✅ Validation terminée le JJ/MM/AAAA HH:MM` + bouton `↩ Reprendre la validation` (réversible) |
| Statut = Exportée | Badge supplémentaire `📤 Exportée le JJ/MM/AAAA HH:MM vers /chemin/_Vu.csv` ; bouton `📤 Exporter` devient `📤 Ré-exporter` |
| Modifications post-export | Bandeau orange : `⚠️ Vous avez modifié des observations depuis le dernier export. [Ré-exporter]` |

## À ne PAS faire

- Pas de bouton « Enregistrer » sur le commentaire ou le tag (cf. [O7](../../Objectifs%20qualités/Objectifs%20qualités/O7.md) - persistance immédiate).
- Pas de masquer les paramètres techniques (Fe, gain…) dans un onglet « avancé » : Karim et Samuel les regardent souvent.
- Pas de remplacer le bouton `🗑 Supprimer` par une icône seule : action dangereuse, le mot doit être visible.
