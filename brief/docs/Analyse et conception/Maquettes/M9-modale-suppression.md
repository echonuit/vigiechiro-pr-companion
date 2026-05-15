# M9 - Modale de suppression d'une session

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md) (ou en sélection multiple depuis [M1](M1-accueil.md))
> **Stories couvertes** : [E1.S5 Supprimer une session](../Story%20mapping.md#e1s5-supprimer-une-session-3-pts)

## Étape 1 - Choix du périmètre

```
┌──────────────────────────────────────────────────────────────────────┐
│  🗑 Supprimer la session                                          ✕  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Vous êtes sur le point de supprimer :                               │
│                                                                      │
│   📌 Session du 22/04/2026 - PR n° 1925492                           │
│      • 1 572 WAV bruts (5.0 Go)                                      │
│      • 4 031 observations                                            │
│      • 1 269 validations utilisateur                                 │
│      • Tag : Carré 640380 - Pass 2                                   │
│                                                                      │
│  Que voulez-vous supprimer ?                                         │
│                                                                      │
│   (●) Seulement les métadonnées de l'application                    │
│       (la session disparaît du journal, les fichiers WAV restent     │
│       sur votre disque dans `/home/marie/PR_avril/22-04`)            │
│                                                                      │
│   ( ) Tout effacer, y compris les fichiers WAV                       │
│       (libère ~5 Go d'espace disque - ⚠️ irréversible)               │
│                                                                      │
│                              [Annuler]    [⟶ Continuer]              │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 2 - Confirmation finale

```
┌──────────────────────────────────────────────────────────────────────┐
│  ⚠️ Confirmation requise                                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   Vous êtes sur le point d'effacer définitivement :                  │
│                                                                      │
│   • Toutes les métadonnées de la session du 22/04/2026               │
│   • 1 269 validations utilisateur                                    │
│   • 22 corrections de taxon                                          │
│   • 18 commentaires libres                                           │
│                                                                      │
│   ⚠️ Cette action est **irréversible**.                               │
│                                                                      │
│   Pour confirmer, tapez le numéro de PR : `1925492`                  │
│   [_______________________________________]                          │
│                                                                      │
│                       [Annuler]    [🗑 Supprimer définitivement]     │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 3 - Confirmation de suppression

```
┌──────────────────────────────────────────────────────────────────────┐
│  ✅ Session supprimée                                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   La session du 22/04/2026 a été retirée du journal.                 │
│                                                                      │
│   • Métadonnées effacées                                             │
│   • 5.0 Go libérés sur le disque (option « tout effacer »)           │
│                                                                      │
│                                                          [Fermer]    │
└──────────────────────────────────────────────────────────────────────┘
```

## Composants & comportements

| Étape | Composant | Notes |
|---|---|---|
| 1 | Bloc rappel session | Affiche les éléments qui seront supprimés (volume, validations, tag) pour donner pleinement conscience de l'enjeu |
| 1 | Choix radio périmètre | Par défaut : **« Seulement les métadonnées »** (le moins destructif). L'option « Tout effacer » est explicite |
| 1 | Précision sur le chemin | Quand on sélectionne « Seulement métadonnées », montrer où resteront les WAV |
| 2 | Demande de saisie du n° PR | Garde-fou anti-clic-distrait : oblige à taper exactement le n° de série du PR pour confirmer |
| 2 | Bouton `Supprimer définitivement` | **Désactivé** tant que le n° PR n'est pas correctement saisi |
| 3 | Toast ou modale courte | Confirmation visible mais brève |

## Comportement en sélection multiple

Si plusieurs sessions sont sélectionnées dans [M1](M1-accueil.md) avant l'action `Supprimer` :

- Étape 1 affiche un **bloc rappel agrégé** : `5 sessions sélectionnées, 8 014 WAV, 25 Go au total, 4 327 validations utilisateur`.
- Étape 2 demande de taper `SUPPRIMER` (le mot, pas un n° PR) pour confirmer.
- Étape 3 récapitule par session : `5 sessions supprimées, 25 Go libérés`.

## États

| État | Apparence |
|---|---|
| Saisie incorrecte du n° PR (étape 2) | Bouton `Supprimer` reste désactivé, hint en dessous : « Tapez exactement le numéro de série pour confirmer » |
| Suppression d'une session déjà exportée | Étape 1 ajoute un encart : « ℹ️ Cette session a déjà été exportée vers VigieChiro (le fichier `_Vu.csv` n'est PAS supprimé). » |
| Erreur d'effacement disque | Étape 3 : « ⚠️ Métadonnées supprimées, mais certains fichiers n'ont pas pu être effacés (permissions). [Voir le détail] » |

## À ne PAS faire

- Pas de garde-fou unique ([Annuler] / [Supprimer]) : Marie peut cliquer trop vite. Le 2 clics + saisie du n° PR est intentionnel.
- Pas d'option « Cocher pour me souvenir de cette confirmation » : la confirmation doit être systématique.
- Pas de bouton rouge énorme « SUPPRIMER » qui attire l'œil : le bouton de confirmation est sobre, désactivé par défaut.
