---
type: adr
title: "Le nom d'une compétence dit le geste, sauf quand le métier en a déjà un"
status: stable
article: A30
chantier: "#4565, chantier #4511 (mise en service d'OpenSpec)"
decided_at: 2026-08-26
verification: humaine
loupe:
  - "scripts/methode/synchronise-adaptateurs.py"
verification_note: "aucun motif textuel ne décide si un nom désigne un geste ou une chose ; le générateur tient la cohérence des dossiers, la relecture tient le nom"
relations:
  prolonge: ["4516-une-commande-nomme-un-geste"]
verified:
  - by: human:nedseb
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Le nom d'une compétence dit le geste, sauf quand le métier en a déjà un

## Contexte

L'[ADR 4516](4516-une-commande-nomme-un-geste.md) a posé « une commande nomme un geste, pas un
fournisseur » pour les six flux d'OpenSpec. Les treize compétences maison n'avaient jamais été
passées à ce filtre : cinq nommaient déjà un geste, huit nommaient une chose. Le dépôt était à moitié
cohérent, et personne ne l'avait décidé : les noms s'étaient posés au fil des chantiers.

**Le comptage aurait envoyé au mauvais endroit.** Un relevé naïf rend 353 occurrences pour les huit,
dont 178 pour le seul mot `mutation`. L'ouverture des citations en rend **22 qui désignent vraiment
une compétence**, dont deux dans des ADR immuables. Le reste nomme autre chose : l'étape 0 « le
triage », le profil Maven `mutation`, le profil Maven `recette-filmee`. Un comptage n'est pas une
lecture, et c'est la lecture qui a rendu ce lot faisable.

## Décision

**Le nom d'une compétence dit le geste qu'on fait.** Cinq changent.

| Avant | Après | Citations réécrites |
|---|---|---|
| `humaniseur` | `humaniser` | 11 |
| `triage` | `trier-les-issues` | 4 |
| `revue-visuelle` | `revoir-les-ecrans` | 3 |
| `audit-croise` | `confronter-un-arbitrage` | 1 |
| `recette-filmee` | `filmer-une-recette` | 1 |

### Trois ne changent pas, et c'est la moitié qu'on oublie

`tdd`, `mutation` et `worktree` gardent leur nom. Ils désignent un métier ou un objet que la
profession nomme ainsi, et les traduire en verbe les rendrait moins reconnaissables, pas plus. Le but
de la règle est qu'on **trouve** la compétence, pas qu'on admire la règle.

Cette décision ne laisse aucune trace dans le dépôt, puisqu'elle ne change rien. C'est exactement
pourquoi elle est écrite ici : sans cela, le prochain qui relira les noms rouvrira la question sans
savoir qu'elle a été tranchée.

### Ce que le refus de renommer coûte, et qui le paie

`mutation` nomme aussi un profil Maven. Le même jeton entre apostrophes désigne donc deux choses, et
seules trois de ses sept citations sont la compétence. Le coût est assumé : le contexte lève
l'ambiguïté, et renommer une technique que la profession appelle ainsi la rendrait introuvable.

`recette-filmee` portait la même collision, quatre citations sur cinq étant le profil Maven. Là, le
renommage la **retire** : le profil garde son nom, la compétence prend le geste. La collision n'était
pas la raison du renommage, mais elle le rend plus utile qu'il n'y paraissait.

### Ce que « l'humaniseur » garde

Le titre de l'article A31, « La prose visible se relit à l'humaniseur », ne change pas. Il emploie un
nom commun pour l'instrument, ce qui reste vrai quel que soit le nom du dossier. De même pour
l'[ADR 4343](4343-la-prose-visible-se-relit-a-l-humaniseur.md), immuable, qui consigne ce qui était
vrai quand elle a été écrite. **Une ADR ne se réécrit pas parce qu'un dossier a bougé.** Ce qui
change est ce qui nomme la compétence : la ligne du corps d'A31, le tableau d'`AGENTS.md`, les
renvois entre compétences.

## Conséquences

- Les cinq dossiers du fonds sont déplacés, leur en-tête `name:` suit, et les adaptateurs sont
  régénérés. Un renommage de compétence renomme aussi la commande, puisqu'elles s'invoquent au nom nu.
- **Le générateur ne voit pas une copie devenue orpheline.** `synchronise-adaptateurs.py` n'itère que
  sur la source : après un renommage, `.claude/skills/<ancien>` survivait et le garde restait vert en
  annonçant « adaptateurs à jour ». Les cinq orphelins sont retirés à la main ici ; le mécanisme est
  traité par #4593. C'est l'article A3 sous un troisième visage, après #4566 : un dispositif qui
  compare deux arbres doit regarder dans les deux sens.
- Vérification **humaine**, et elle ne se mécanisera pas : aucun motif textuel ne décide si un nom
  désigne un geste ou une chose. Le générateur tient la cohérence des dossiers, la relecture tient le
  nom.
