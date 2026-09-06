## Context

Voir `proposal.md` - Why. Ce qui suit ne porte que sur les décisions techniques.

Trois faits mesurés le 2026-09-06 les contraignent :

- `core.hooksPath = .githooks` vit dans `.git/config`, donc **partagé par tous les worktrees** ;
- `.githooks/post-checkout` **détecte déjà** la création d'un worktree - il l'identifie par le SHA nul
  `0000...` et sort, son objet étant `target/` ;
- `pyproject.toml` porte déjà `[dependency-groups]` et une table `[tool.vigiechiro.modules]` qu'un
  garde lit, posée par #5008 pour ce défaut exact appliqué aux **modules**.

## Goals / Non-Goals

**Goals.** Qu'un verdict local porte sur le code et non sur l'état du poste. Que le nom d'un outil
prescrit soit celui qui l'installe.

**Non-Goals.** Toucher à la CI, qui installe déjà tout. Rendre la porte capable de tout poser :
plusieurs prérequis n'ont pas de commande, et leur refus reste le bon geste.

## Decisions

### Le crochet pose ce qui est bon marché, la porte ce qui dépend du diff

**Retenu.** `post-checkout` pose openspec et ruff à la création d'un worktree - six secondes une fois,
au moment où personne n'attend. La porte pose `target/pmd.xml`, et **seulement si le diff touche du
`.java`**.

Le partage se fait sur un critère net : le crochet ne sait rien du diff, la porte le sait déjà.

**Tout dans la porte** - écarté. Les six secondes se repaieraient à chaque appel, et la porte est
lancée plusieurs fois par lot. Elle deviendrait aussi un outil qui installe à chaque exécution, ce
qu'un lecteur n'attend pas d'une commande qui dit « juger ».

**Tout dans le crochet** - écarté. PMD coûte une à deux minutes ; les imposer à un `worktree add` pour
un lot de prose est le contraire du service rendu, et le crochet ne peut pas savoir ce que la branche
contiendra.

**Partager l'installation entre worktrees par un lien** - écarté, et c'est #4849 qui l'avait pressenti
en l'appelant « la plus tentante et probablement la mauvaise » : une branche qui change la version
épinglée serait éprouvée contre celle d'une autre, ce que le garde existe pour empêcher. La mesure la
clôt définitivement - elle échangeait ce risque contre **2 secondes**.

### Le crochet n'installe pas ce qu'il ne peut pas vérifier

Le crochet tourne sans que personne le regarde. Il SHALL donc rester **silencieux quand tout va
bien**, dire ce qu'il pose, et ne jamais bloquer : `post-checkout` a déjà ce dessin pour son `clean`,
et son commentaire l'écrit - « en silence et sans bloquer ».

**Une seule ligne d'avertissement plutôt qu'un mur d'erreurs**, comme lui.

### Les outils se déclarent dans `pyproject.toml`, à côté des modules

**Retenu.** `graphifyy` entre dans `[dependency-groups]`, et la correspondance distribution ->
**commande** rejoint `[tool.vigiechiro.modules]`, qui porte déjà distribution -> **module**.

Un second fichier de manifeste - écarté : le dépôt a déjà refusé cette duplication en écrivant
« une liste recopiée serait un second inventaire à tenir ».

**Point ouvert** : la table actuelle s'appelle `modules` et dirait désormais aussi des commandes. La
renommer touche un garde ; en ajouter une seconde à côté est plus sûr. À trancher en réalisant.

### Le dépôt ne lance pas `graphify install`, il le propose

**Tranché par la mesure, le 2026-09-06.** La conception hésitait entre trois façons d'accorder
`graphify install --project` avec `synchronise-adaptateurs.py`. La question ne se pose plus : le dépôt
ne lance pas cette commande du tout.

Lancée dans un worktree propre, elle écrit **quatre** choses, dont trois inattendues : dix lignes dans
`CLAUDE.md` qui **dupliquent** un texte déjà présent dans `AGENTS.md`, un `.claude/CLAUDE.md`, un
`.claude/skills/graphify/` qui fait rougir le garde de synchronisation, et des hooks `PreToolUse`
lançant `graphify hook-guard` avant **chaque** Bash, Grep, Read et Glob.

Le dernier point décide, et il déborde du sujet : changer ce qu'un agent exécute à chaque appel
d'outil n'est pas une déclaration de dépendance. C'est un **choix individuel**, qui appartient à qui
travaille sur le dépôt.

**Ce que le dépôt fait** : il nomme la distribution (`graphifyy`), la commande (`graphify`), et
mentionne que `graphify install --project` existe pour qui le veut. **Ce qu'il ne fait pas** : le
lancer, l'exiger, ou faire rougir quoi que ce soit sur son absence.

**Conséquence heureuse** : le conflit avec `synchronise-adaptateurs.py` disparaît sans être arbitré.
Le garde continue de tenir les compétences que le dépôt écrit, et aucune compétence tierce n'entre
dans son périmètre - puisqu'aucune n'y est déposée.

**Une trouvaille au passage.** Le texte de `AGENTS.md` sur graphify est manifestement la **sortie**
d'un `graphify install` passé, recopiée à la main. C'est pourquoi il ne nomme pas `graphifyy` : la
commande ne le mentionne pas non plus. Une prescription née d'une sortie d'outil hérite de ses trous.

### Le venv de `ruff` vit hors du dépôt, mais son chemin est déclaré

`~/.venv-outils`, comme la compétence `ouvrir-une-pr` le prescrit déjà. Le poser **dans** le dépôt le
ferait entrer dans les corpus balayés par les gardes, et il faudrait l'exclure partout.

### La préparation est idempotente et le prouve

Reposer coûte du temps et masque une régression : une préparation qui réinstalle à chaque appel rend
le second appel indiscernable du premier. Chaque étape SHALL **constater avant de poser**, et le dire.

## Risks / Trade-offs

**Aucun gardien en CI.** Le runner installe tout lui-même et ne joue jamais ce chemin. Le dispositif
repose donc entièrement sur son auto-test, qui doit être vu rouge sur sa propre mutation (article A2)
plutôt que cru sur parole.

**Le crochet n'est actif qu'après un `./mvnw`.** C'est `git-build-hook-maven-plugin` qui pose
`core.hooksPath`. Un contributeur qui clone et lance la porte avant tout `./mvnw` n'a pas le crochet -
la porte doit donc rester capable de conclure sans lui, et non le supposer.

**Une commande d'installation dépend du réseau.** Elle échoue en avion, derrière un proxy, ou quand un
registre est en panne. C'est pourquoi une exigence porte explicitement sur l'échec : dire, poursuivre,
et laisser le garde refuser.
