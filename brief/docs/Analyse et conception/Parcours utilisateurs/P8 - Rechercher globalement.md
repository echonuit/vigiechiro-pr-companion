# P8 - Rechercher globalement 🔍

[← Retour au sommaire des parcours](index.md) · **Transverse** · accessible depuis tout écran

> **Persona principal** : tous. **Objectifs qualité servis** : [O2 Facilité d'apprentissage](../../Objectifs%20qualites/Objectifs%20qualites/O2.md) (atteindre n'importe quel élément sans connaître l'arborescence).

Dès qu'un utilisateur gère plus de quelques sites, retrouver un site, un point ou un passage précis en naviguant écran par écran devient fastidieux. Une **recherche globale** est posée en haut à droite du bandeau de l'application, présente sur **tous les écrans**, et permet de **sauter directement** à l'élément voulu.

1. Depuis n'importe quel écran, l'utilisateur clique le champ **« Rechercher »** (ou tape **Ctrl+F**) et saisit quelques lettres.
2. La liste de résultats se remplit **au fil de la frappe**, **insensible à la casse et aux accents** (« etang » trouve « Étang »). Elle interroge :
    - les **sites** (par n° de carré ou nom),
    - les **points d'écoute** (par code ou description),
    - les **passages** (par carré, code de point, n° de passage, année ou date).
3. Les résultats sont **groupés par type** (Sites, puis Points, puis Passages), chaque groupe avec son en-tête ; chaque ligne montre un libellé principal et un détail de contexte.
4. L'utilisateur choisit un résultat - **à la souris** ou **au clavier** (**↓** pour entrer dans la liste, **Entrée** pour ouvrir, **Échap** pour fermer). L'application ouvre alors **l'écran de l'élément** (la fiche du site ou l'écran du passage).

## Règles métier visibles

- R1 / R2 : les éléments retrouvés respectent le format de carré (6 chiffres) et de code point (lettre + chiffre).
- La saisie est **anti-rebondie** (les frappes rapides sont regroupées en une seule recherche) et le nombre de résultats par type est **borné**, pour garder la liste lisible.

## Accessibilité

Chaque résultat est exposé aux **lecteurs d'écran** (libellé accessible) ; la recherche se pilote **entièrement au clavier**, sans dépendre du seul repère visuel.
