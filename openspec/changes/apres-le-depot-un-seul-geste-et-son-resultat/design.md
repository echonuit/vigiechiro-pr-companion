## Context

Le geste existe et fonctionne : `SuiviTraitementUI.lancer` appelle la plateforme hors du fil JavaFX, puis relit l'état de l'analyse, que la carte « Traitement Vigie-Chiro » affiche. Trois choses le rendent illisible (voir `proposal.md`) :

- deux boutons l'offrent : celui de l'étape 4 (`EtapeDeposerUI`) et celui du compte rendu du dépôt (`CompteRenduDepotUI.actions`) ;
- son résultat passe par le retour d'opération de `DepotViewModel`, que l'écran affiche dans le bandeau du haut de page, hors de vue quand on vient de cliquer en bas ;
- le bouton n'est bloqué que pour une nuit déjà analysée ou en échec (`TraitementViewModel.relanceBloquee`) : une analyse planifiée le laisse cliquable, d'où les deux `400 Already PLANIFIE` du journal de #5597.

Le titre de l'étape 4 est un libellé figé de `Lot.fxml`.

## Goals / Non-Goals

**Goals :**
- Un seul bouton, un titre qui dit son geste, un résultat lisible là où l'on a cliqué.
- Un état « analyse demandée » qui vient du relevé de la plateforme, donc qui survit à la réouverture.

**Non-Goals :**
- Aucun sondage automatique de la plateforme : la règle « on n'interroge le serveur que sur demande » reste.
- L'import des observations à la fin de l'analyse (#5784), qui vient après ce changement.
- La ligne de commande ne change pas : elle dit déjà chaque issue et le motif d'un refus.

## Decisions

**D1. Le bouton gardé est celui de l'étape 4.** Décision du porteur, le 3 octobre 2026. C'est l'étape dont le geste est le sujet, et la carte du traitement, juste en dessous, porte la suite. Écarté : garder celui du compte rendu, qui n'existe qu'après un dépôt mené dans cette session, et disparaît donc à la réouverture.

**D2. Le résultat du lancement a sa propre zone, dans l'étape 4.** Un libellé sous le bouton, alimenté par une propriété distincte du retour d'opération général. Le bandeau du haut ne le répète pas : deux endroits pour un même résultat obligeraient à les tenir d'accord. Écartés : faire défiler l'écran jusqu'au bandeau, qui déplacerait l'utilisateur loin de son geste ; une fenêtre modale, qui interromprait pour une information sans décision à prendre.

**D3. « Analyse demandée » se lit dans le relevé, pas dans la réponse au clic.** `TraitementViewModel` expose un état dérivé du dernier relevé : planifiée, en cours, ou relancée par la plateforme. Le bouton se désactive quand cet état ou le blocage existant (`relanceBloquee`) est vrai. Le relevé qui suit déjà chaque lancement suffit à l'alimenter, et le dernier relevé enregistré le restitue à la réouverture. Écarté : bloquer sur la réponse `ACCEPTE` elle-même, qui serait perdue à la fermeture de l'écran et contredirait une plateforme qui aurait, entre-temps, fini ou échoué.

**D4. Un refus cite le motif de la plateforme.** `ResultatLancement` le porte déjà, et la commande l'affiche : l'écran le taisait. Le texte de l'écran rejoint celui de la commande sur ce point.

**D5. Le titre de l'étape 4 se lie au lien de participation.** Le même lien qui change déjà le texte et l'icône du bouton change le titre : un seul critère pour les trois.

## Risks / Trade-offs

- [Le relevé qui suit un lancement accepté échoue (plateforme injoignable)] : l'état « analyse demandée » reste inconnu et le bouton se réactive. Un second clic reçoit « analyse déjà demandée », que la zone de l'étape 4 dit sans ambiguïté. → Accepté : ne rien affirmer qu'on n'a pas relevé.
- [Une nuit en échec reste bloquée] : comportement actuel, conservé ; la commande garde `--forcer`. → Hors de ce changement.
- [Les captures du lot bougent] : l'étape 4 change de titre sur les états connectés. → Régénérées et relues, comme pour le lot 16.

## Open Questions

Aucune qui change les specs ou le découpage. Les textes de la zone de retour et de l'explication du bouton grisé restent à valider par le porteur : c'est la tâche 2.1, avant le code du groupe 2, comme pour les libellés du lot 16.
