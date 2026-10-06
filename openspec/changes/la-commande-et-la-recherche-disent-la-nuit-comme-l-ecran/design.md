## Context

`Horodatage.dateSeule` et `Horodatage.heureCourte` sont les deux lecteurs que les lots 31 et 38 ont posés. Les
deux sites de ce changement recopient encore la valeur de la base au lieu de passer par eux.

## Decisions

**La flèche de `statut-passage` reste « → ».** L'écran écrit « -> ». Le porteur a tranché le 6 octobre 2026 :
un terminal et un écran n'ont pas à écrire le même signe, ce sont les heures qui doivent être les mêmes.

**Les heures réalignées gardent leurs secondes.** `metadonnees-passage` et le compte rendu d'envoi disent les
heures d'une nuit avant et après leur réalignement sur les enregistrements. Ce réalignement peut se jouer à la
seconde : « 21:30:00 → 21:30:07 » deviendrait « 21:30 → 21:30 », et la correction ne se verrait plus. La commande
et l'écran y disent déjà la même chose. Ce site est lu, et laissé.

**La recherche compare les deux formes, elle n'en remplace aucune.** La forme française s'ajoute aux champs
comparés à la requête, à côté de la forme ISO. Retirer l'ISO casserait la requête `2026-06` qu'un test tient et
que des habitués tapent peut-être.

## Risks / Trade-offs

Une requête faite de chiffres et de barres, comme `06/2026`, trouve maintenant des nuits : c'est l'effet voulu.
Elle ne trouve rien de plus dans les autres champs, qui ne portent pas de barre.
