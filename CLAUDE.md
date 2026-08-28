# CLAUDE.md : VigieChiro PR Companion

La méthode de ce dépôt est dans **[AGENTS.md](AGENTS.md)**. Lisez-le d'abord : il vaut pour tous les
agents, et rien d'important n'est répété ici.

Ce fichier ne porte que ce qui est propre à Claude Code.

## Les compétences

Elles sont découvertes dans `.claude/skills/`, qui est une **copie** de `.agents/skills/`. Le fonds
est dans `.agents/` : une compétence se corrige là, et la copie suit.

Le garde `scripts/methode/synchronise-adaptateurs.py --verifie` refuse une copie absente ou périmée.
Après avoir touché une compétence, lancer le script sans argument.

Les six compétences OpenSpec sont en plus exposées en commandes, nommées par le **geste** et non
par le fournisseur : `/instruire`, `/proposer`, `/realiser`, `/reprendre`, `/fusionner`,
`/archiver`. Chacune tient en une ligne et ne fait qu'ouvrir sa compétence, où vit le flux.

## Au commencement de CHAQUE issue : UN bloc, puis TU T'ARRÊTES

**Consigne d'agent, pas règle du dépôt.** Elle n'a pas d'équivalent dans `CONTRIBUTING.md` et n'a pas à en avoir : elle règle la façon dont nous travaillons ensemble.

Avant la première ligne de code, produire **un seul bloc** - il vaut réservation, résumé et plan à la fois - puis le déposer en commentaire sur l'issue, assigner l'issue, et attendre mon accord explicite. Un geste, pas deux : une cérémonie qu'on saute parce qu'elle est longue ne se tiendra pas mieux en s'allongeant.

```markdown
**Pris par** : chantier #<numéro de l'EPIC> · branche `<nom-de-branche>`
**Ce qu'il y a à faire** : <une phrase, dans les termes du problème>
**Pourquoi maintenant** : <ce qui la rend traitable ou urgente>
**Dans quelle continuité** : <l'issue qu'elle suit, ce qu'elle permet ensuite>
**Périmètre** : <ce que je touche> ; **hors périmètre** : <ce que je ne touche pas, et qui pourrait tenter>
**Plan** : <3 à 6 étapes>
**Ce que je vérifierai** : <le dispositif, et comment je le verrai rouge>
**Question ouverte** : <s'il y en a une ; sinon retirer la ligne>
```

**Un numéro d'EPIC, pas un thème.** La première ligne porte le chantier auquel l'issue
**appartient**, et le sas des suites n'en est pas un : on y consigne une trouvaille, on n'y prend rien.
Une issue de suite n'a donc pas de chantier par construction, et le geste attendu est d'en ouvrir un.
Vécu le 27 août : trois sessions ont pris des issues avec `Pris par : sas des suites #4562`, et il a
fallu les reprendre. C'était moi pour trois d'entre elles. La compétence `ouvrir-une-issue` porte la
règle et sa raison.

**Le moment de dérive est le milieu, pas l'ouverture.** À l'ouverture tu es attentif ; c'est en découvrant un défaut adjacent que tu élargis sans le dire. Tout changement de périmètre en cours d'issue se re-demande : tu le poses, tu proposes, tu attends. Tu ne l'absorbes pas. Vécu : la réécriture de `dev-docs/captures.md` a été fondue dans la PR #3483 sans que la question soit posée - c'était défendable, et ce n'était pas ta décision.

**Sur session longue, cette cérémonie est la première à sauter.** Si tu enchaînes issue → PR → CI → fusion → issue suivante depuis un moment, c'est précisément là qu'il faut la reposer, pas là où elle devient facultative.

## Le détail des trois premières lignes du bloc

Avant la première ligne de code, énoncer trois choses : **ce qu'il y a à faire** (une phrase, dans les termes du problème et non de la solution) ; **pourquoi maintenant** (ce qui la rend traitable : un prérequis fusionné, une mesure qui vient de tomber, ou urgente) ; **dans quelle continuité** elle s'inscrit (de quel chantier elle vient, quelle issue elle suit, ce qu'elle rend possible ensuite). Le troisième est celui qu'on saute en enchaînant issue → PR → CI → fusion → issue suivante, et le seul qui ne se retrouve pas après coup.

**Et se signaler** : déposer ces trois phrases en commentaire sur l'issue, avec le chantier, la branche et le remède envisagé, puis assigner l'issue. Les deux, pas l'un ou l'autre : l'assignee est le signal qui se filtre (`gh issue list --assignee "*"` = « voici tout ce qui est pris »), le commentaire porte ce que l'assignee ne dit pas. Annoncer le remède est le vrai gain : deux personnes peuvent voir le même défaut et imaginer deux corrections dont l'une est meilleure ; annoncées, le désaccord se règle avant le code, pas au moment de choisir laquelle des deux branches on jette. **Un signalement se relâche** : quand on s'arrête (reporté, bloqué, abandonné), retirer l'assignee et le dire, car une revendication oubliée est pire que rien : elle fait passer une issue libre pour prise. Et il ne couvre que « cette issue est-elle prise ? », non « est-ce la même que celle-là sous d'autres mots ? » : cette seconde question reste le travail de l'étape 0.
