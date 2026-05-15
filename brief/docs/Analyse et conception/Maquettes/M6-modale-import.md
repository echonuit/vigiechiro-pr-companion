# M6 - Modale d'import d'une session

> **Type** : modale plein écran ou centrée, déclenchée depuis [M1 - Accueil](M1-accueil.md)
> **Parcours couverts** : [P1 Première utilisation](../Parcours%20utilisateurs.md#p1-premiere-utilisation), [P2 Cycle régulier](../Parcours%20utilisateurs.md#p2-cycle-regulier)
> **Stories couvertes** : [E2.S1 Importer un dossier](../Story%20mapping.md#e2s1-importer-un-dossier-de-session-8-pts), [E2.S5 Reprendre import interrompu](../Story%20mapping.md#e2s5-reprendre-un-import-interrompu-3-pts) (COULD)

## Étape 1 - Sélection du dossier

```
┌──────────────────────────────────────────────────────────────────────┐
│  📥 Importer une nuit de capture                                  ✕  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Sélectionnez le dossier qui contient les fichiers de votre nuit    │
│  de capture (WAV, LogPR, THLog).                                     │
│                                                                      │
│       ┌────────────────────────────────────────────────────────┐    │
│       │                                                        │    │
│       │              📂                                        │    │
│       │                                                        │    │
│       │      Glisser-déposer un dossier ici                   │    │
│       │                                                        │    │
│       │                  ou                                    │    │
│       │                                                        │    │
│       │           [Parcourir...]                               │    │
│       │                                                        │    │
│       └────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ℹ️ Structure attendue :                                             │
│  • un fichier `LogPR<n>.txt`                                         │
│  • un fichier `*_THLog.csv`                                          │
│  • un dossier `wav/` (ou WAV à la racine)                            │
│                                                                      │
│                                            [Annuler]                 │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 2 - Vérification (après sélection valide)

```
┌──────────────────────────────────────────────────────────────────────┐
│  📥 Importer une nuit de capture                                  ✕  │
├──────────────────────────────────────────────────────────────────────┤
│  ✅ Dossier reconnu : /home/marie/PR_avril/22-04                     │
│                                                                      │
│  Détecté :                                                           │
│   • LogPR1925492.txt           ✅                                    │
│   • PaRecPR1925492_THLog.csv   ✅                                    │
│   • wav/  →  1 572 fichiers .wav (5.0 Go)                            │
│                                                                      │
│  Métadonnées extraites :                                             │
│   • PR n°               : 1925492                                    │
│   • Date de capture     : 22/04/2026 20:25 → 23/04/2026 07:48        │
│   • Durée               : 11 h 22 min                                │
│   • Paramètres acquis.  : Fe 384 kHz • 8-120 kHz • Gain 0 dB         │
│   • Carré (depuis nom)  : 640380                                     │
│                                                                      │
│  Cette session sera enregistrée comme : `Session 2026-04-22 PR1925492`│
│                                                                      │
│  Tag (optionnel)                                                     │
│  [ Carré 640380 - Pass 2___________________________ ▼]               │
│                                                                      │
│                              [Annuler]    [Importer cette session]   │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 3 - Progression

```
┌──────────────────────────────────────────────────────────────────────┐
│  📥 Import en cours                                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   Session du 22/04/2026 - PR n° 1925492                              │
│                                                                      │
│   ████████████████████████░░░░░░░░░░░░░░░░░░  47 %                   │
│                                                                      │
│   Étape : Indexation des WAV                                         │
│   742 / 1 572 fichiers traités                                       │
│   Temps écoulé : 0:38   |   Restant estimé : 0:42                    │
│                                                                      │
│                                       [Importer en arrière-plan]     │
│                                       [Annuler l'import]             │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 4 - Récapitulatif (succès)

```
┌──────────────────────────────────────────────────────────────────────┐
│  ✅ Import terminé                                                    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   Session du 22/04/2026 - PR n° 1925492 importée avec succès.        │
│                                                                      │
│   • 1 572 WAV indexés                                                │
│   • 70 mesures T°/H                                                  │
│   • 0 anomalie détectée dans le LogPR                                │
│                                                                      │
│   Prochaine étape : récupérez le CSV d'observations Tadarida sur     │
│   VigieChiro et cliquez sur « Charger CSV Tadarida » dans la fiche   │
│   de la session.                                                     │
│                                                                      │
│                                  [Voir la session]   [Fermer]        │
└──────────────────────────────────────────────────────────────────────┘
```

## Composants & comportements

| Étape | Composant clé | Comportement |
|---|---|---|
| 1 | Zone drag-and-drop | Accepte un dossier (pas un fichier seul). Visuel actif au survol. |
| 1 | Bouton `Parcourir...` | Ouvre le sélecteur de dossier OS natif |
| 1 | Encart `Structure attendue` | Pédagogique - aide Marie à savoir ce qu'elle doit fournir |
| 2 | Liste des fichiers détectés | ✅ vert si trouvé, ❌ rouge si manquant, ⚠️ orange si incertain |
| 2 | Bloc Métadonnées | Affiche ce qui a été extrait. Permet de détecter une erreur de dossier avant import |
| 2 | Champ Tag | Pré-rempli si possible depuis le nom de fichier (ex. `Car640380` → `Carré 640380`) |
| 3 | Barre de progression | Détaillée : nb fichiers / total, étape en cours, temps estimé |
| 3 | `Importer en arrière-plan` | Réduit la modale, l'import continue, M1 affiche `⏳ Import en cours` dans la barre de statut |
| 3 | `Annuler l'import` | Confirmation requise. Marque la session en `❌ Import incomplet` (pour E2.S5) |
| 4 | Bouton `Voir la session` | Ouvre [M2](M2-detail-session.md) sur la session importée |

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Aucun LogPR détecté | Étape 1 : « ❌ Ce dossier ne contient pas de fichier `LogPR*.txt`. Êtes-vous sûr de la structure ? » + lien vers l'aide |
| Aucun WAV trouvé | Étape 1 : « ❌ Aucun fichier `.wav` trouvé dans ce dossier ni dans `wav/`. » |
| Session déjà importée | Étape 2 : « ⚠️ Une session avec le même PR n° et la même date de capture existe déjà. [Annuler] [Importer en double] [Voir l'existante] » |
| Erreur disque pendant l'import | Étape 3 : « ❌ Erreur d'écriture en base à 47 %. La session est marquée « Import incomplet », vous pourrez réessayer. » |

## Reprise d'un import interrompu (E2.S5, COULD)

Si une session est marquée `❌ Import incomplet` dans M1, un clic dessus propose une modale réduite :

```
┌──────────────────────────────────────────────────────────────┐
│  ⚠️ Import incomplet                                          │
├──────────────────────────────────────────────────────────────┤
│  L'import de la session du 22/04/2026 (PR 1925492) a été     │
│  interrompu à 47 %.                                          │
│                                                              │
│  Que voulez-vous faire ?                                     │
│   ( ) Reprendre l'import là où il s'est arrêté               │
│   (●) Recommencer l'import depuis zéro                       │
│   ( ) Supprimer cette session incomplète                     │
│                                                              │
│                                  [Annuler]   [Confirmer]     │
└──────────────────────────────────────────────────────────────┘
```

## À ne PAS faire

- Pas de modale qui se bloque pendant l'import : autoriser explicitement `Importer en arrière-plan`.
- Pas de barre de progression sans information textuelle : Marie veut savoir ce qui se passe (« Indexation des WAV », « Lecture du LogPR »…).
- Pas d'auto-sélection magique du dossier (par scan du système) : c'est intrusif et fragile.
