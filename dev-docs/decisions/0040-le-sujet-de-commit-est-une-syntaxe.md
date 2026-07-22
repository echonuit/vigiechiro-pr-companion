# ADR 0040 — Le sujet d'un commit est une syntaxe, pas une phrase française

- **Statut** : Accepté — 2026-07-20
- **Chantier** : EPIC #2104 / lot 0 (#2105)
- **Vérification** : certaine — `.github/scripts/verifie-titre-pr.sh`

## Contexte

Le dépôt n'a **plus publié aucune version entre le 18 et le 20 juillet 2026**, alors que **58 commits
de type releasable** s'y sont accumulés. Le job `release` a fini **vert** à chaque push :

```
[semantic-release] Analyzing commit: fix(captures) : refuser une capture…
[semantic-release] Analysis of 153 commits complete: no release
```

La cause tient dans un espace. Le dépôt écrit en français, et le français met une espace insécable
avant les deux-points. La pratique a donc glissé, sans que personne ne le décide, de `type(scope):`
vers `type(scope) :`. Conventional Commits n'admet pas cette forme. Le parser de semantic-release@24
en fait la démonstration :

| Sujet | `type` reconnu |
|---|---|
| `fix(captures) : refuser une capture…` | **`null`** |
| `fix(captures): refuser une capture…` | `fix` |

Sur les 400 derniers commits : **136 non conformes, 10 conformes**. Le dernier commit conforme date du
18 juillet, exactement la date du dernier tag. `CONTRIBUTING.md` documentait pourtant la bonne forme
depuis toujours : la dérive s'est faite **contre** une règle écrite, ce qui est précisément le cas
qu'une règle écrite ne suffit pas à empêcher.

Le vrai défaut n'est pas la dérive, c'est son **silence**. Un dépôt qui ne publie plus rien présentait
exactement la même CI verte qu'un dépôt qui publie : « aucun changement pertinent » est un verdict de
succès. Aucun contrôle ne portait sur la convention, et le seul signal - l'absence de release - est
une **non-occurrence**, ce que personne ne surveille.

## Décision

**Dans un sujet de commit, `:` est un token de syntaxe, pas une ponctuation de phrase.** La règle
typographique française de l'espace avant les deux-points ne s'y applique pas, pas plus qu'elle ne
s'applique au `:` d'un `switch` Java ou au `:` d'un `key: value` YAML. La forme est `type(scope): sujet`,
et le sujet lui-même reste du français, avec sa typographie.

Trois conséquences.

**1. Le contrôle porte sur le titre de la PR, pas sur les commits.** Le dépôt fusionne en **squash**
avec `squash_merge_commit_title = PR_TITLE` : le titre de la PR devient le sujet du commit sur `main`,
et les messages des commits de branche sont écartés à la fusion. Valider les commits individuels
policerait des messages jetés, tout en laissant passer le seul texte que semantic-release lira.
`.github/workflows/titre-pr.yml` valide donc le titre, et lui seul.

**2. Le parser reste élargi, à titre transitoire.** `.releaserc.json` accepte l'espace
(`headerPattern` avec `\s*:`), sur le `commit-analyzer` **et** sur le `release-notes-generator` : sans
le second, la version sortirait avec des notes vides. Cet élargissement existe pour **rattraper les 58
commits bloqués**, vérifié sur la vraie plage `v2.16.1..HEAD` (159 commits : aucune release avec le
parser par défaut, `minor` avec le parser élargi). Il devient inutile dès que le garde-fou du titre a
fait son effet, mais il est conservé comme filet : il ne coûte rien, et il évite qu'un contournement
du contrôle re-fige la publication.

**3. Le message d'erreur nomme la cause.** Un titre refusé pour cette raison précise affiche la forme
attendue et l'explication du `:`-token. Une règle contre-intuitive doit se réexpliquer au moment où
elle mord, sinon elle se fait contourner.

## Conséquences

**Ce qu'on gagne.** La publication redémarre, et une dérive de convention devient **visible à la PR**
plutôt qu'invisible pendant deux jours. Le garde-fou a été vérifié en le voyant rouge sur la forme
exacte qui a arrêté la release, et sur onze autres cas dont un titre porteur d'une tentative
d'injection de commande.

**Ce qu'on perd.** Les 58 commits rattrapés portent des sujets non conformes : ils apparaîtront dans
les notes de la version de rattrapage tels qu'ils ont été écrits. Le journal de cette version sera
donc plus long et moins homogène que d'habitude. C'est le prix du rattrapage, préféré à la perte pure
et simple de deux jours d'historique.

**Ce qui reste ouvert.** Le contrôle ne s'applique qu'aux PR. Un push direct sur `main` y échapperait,
mais c'est déjà interdit par ailleurs (`CLAUDE.md`, garde-fous non négociables).

**Une leçon plus générale.** Ce chantier a été ouvert sur trois autres sujets - artefacts autonomes,
découpage de la CI, montée de version - et a trouvé celui-ci en cartographiant. Un pipeline dont la
sortie normale est *ne rien faire* a besoin d'un contrôle qui distingue « rien à publier » de
« incapable de publier ». La même question se pose pour les autres étapes silencieuses de la chaîne.
