## Context

`Horodatage.dateSeule` et `ColonneDate.analyser` font tous deux `LocalDate.parse`, qui refuse un instant. Le
premier rend alors la chaîne telle quelle, le second `null`. Deux services écrivent la date de dépôt par
`horloge.maintenant().toString()`.

## Decisions

**Corriger le lecteur, pas chaque site.** `Horodatage.dateDe` lit une date seule ou un instant local, et les
deux lecteurs passent par lui. Écarté : changer ce que les services écrivent, qui demanderait une migration des
bases existantes et laisserait les anciennes valeurs illisibles.

**Ne pas lire un instant avec décalage.** Couper un instant de la plateforme au `T` change le jour dès que le
décalage traverse minuit (#4017). Ces instants passent par `dateMuraleLisible`, avec leur fuseau.

**Des témoins dans la forme de la production.** Le défaut a tenu parce que les tests donnaient aux lecteurs une
date seule. Le témoin neuf construit sa valeur par `LocalDateTime#toString`, comme les services.

**La colonne « Nuit du » devient une colonne de date.** Elle passe par `ColonneDate`, qui affiche en français
et trie en date, au lieu d'une colonne de texte.

## Risks / Trade-offs

La date de dépôt perd son heure à l'affichage. Elle reste dans le `--json` et dans la base. Aucune surface ne
l'affichait volontairement : la forme brute la laissait voir par accident.
