# Le motif du critère de fin

`critere-de-fin.motif` porte **une seule ligne** : l'expression régulière qui reconnaît qu'un lot dit
comment on saura qu'il est fini.

## Pourquoi un fichier, et pas deux constantes

Deux dispositifs la lisent, `rappelle-le-critere-de-fin.sh` et `loupe-4992-lots-sans-critere.py`.
Chacun portait la sienne, et elles avaient déjà divergé sur deux caractères le jour de leur écriture.
Une règle qui vit dans deux gardes voit sa divergence ne faire aucun bruit (#4837), et c'est
exactement ce qui est arrivé : la quatrième formulation manquait aux deux, et rien ne pouvait le dire.

Un seul fichier rend la divergence **impossible** plutôt que surveillée.

## Ce que le dialecte impose

Le motif doit se lire à l'identique par `grep -iE` sous `LC_ALL=C` et par le module `re` de Python.
Deux conséquences :

- **pas de `\w`**, absent des expressions régulières étendues POSIX. On écrit `[a-z]`.
- **pas de classe contenant un caractère accentué.** Sous `LC_ALL=C`, `[eè]` est une classe d'octets
  où le `è` compte pour ses deux octets pris séparément. On écrit l'alternance, `(è|e)`.

Les deux dispositifs sont insensibles à la casse, l'un par `grep -i`, l'autre par `re.I`. Ce pli ne
vaut que pour l'ASCII sous `LC_ALL=C`, d'où les alternances explicites sur les lettres accentuées.

## Les cinq formulations, et d'où elles viennent

| Forme | D'où elle vient |
|---|---|
| `Fini quand` | le mot de `ouvrir-un-chantier`, « comment on saura qu'il est **fini** » |
| `Fait quand` | le mot de la même phrase, « son **fait-quand** » |
| `Comment on saura que chaque lot est fini` | une section entière, employée par deux chantiers |
| `critère de fin` | le mot de `AGENTS.md` depuis #4975 |
| `Ce que je vérifierai` | la ligne du bloc d'ouverture que `CLAUDE.md` prescrit, et que la même page désigne comme le critère à recopier dans le corps |

La cinquième manquait, et c'est celle que le dépôt prescrit à ses propres agents : neuf rappels à
tort sur les douze lots du chantier #4980 (#4995).

**Une sixième apparaîtra.** Signaler à tort coûte un commentaire inutile, se taire à tort ne coûte
rien de plus. Le motif reste donc généreux, et il s'ajoute ici, à un seul endroit.
