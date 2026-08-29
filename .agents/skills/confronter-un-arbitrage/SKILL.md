---
name: confronter-un-arbitrage
description: Use when a judgement call has to be defended before it becomes an ADR or a commit - a threshold, a granularity, a deliberate exception, a rule left to human review. Covers what may leave the repository, how to phrase a question that refutes instead of validating, how to run the three CLI auditors available here, and the measurement that must confirm the answer before anything is written down.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: mesures des 2026-08-22, 2026-08-23 et 2026-08-25
---

# Audit croisé

## Loi d'airain

```
CE QUI PART NE SE RETIRE PAS, CE QUI REVIENT SE MESURE
```

Une question envoyée ne se rappelle pas. Ce qui revient est un avis, et l'article A1 exige une
preuve fraîche avant toute affirmation.

## Annoncer

« J'utilise la compétence confronter-un-arbitrage sur <l'arbitrage>. »

## Fonction de garde

```
0. CHERCHER  ce que le depot repond deja. Une page, une ADR, un cliquet. Voir la
             competence trier-les-issues. Un audit lance sans cette etape refait un travail fait.
1. TRIER     un arbitrage se confronte, une correction se mesure. Si un test ou un
             script repond a la question, le lancer et s arreter la.
2. COMPOSER  la question dans le bloc-notes, hors du depot. Elle porte l extrait
             CHOISI, jamais le fichier entier, et elle demande de REFUTER.
3. RELIRE    ce qui part, ligne par ligne, avant l appel.
4. APPELER   les TROIS auditeurs sur la MEME question, chaque reponse dans son
             propre fichier, et aucun auditeur dans le repertoire d un autre.
5. CONFRONTER les trois reponses. Le desaccord designe ce qu il faut instruire ;
             l accord ne prouve rien, et deux voix contre une ne tranchent rien.
6. MESURER   chaque fait avance. Une commande, sa sortie lue, son code verifie.
7. DECLARER  le fait releve et la mesure qui l a confirme, jamais la source.
```

## L'appel

Trois auditeurs sont installés ici. La question est la même ; ce qui part avec elle diffère.

```bash
BLOC=<le bloc-notes de la session>
QUESTION="$(cat "$BLOC/question-<sujet>.md")"

codex exec --sandbox read-only --skip-git-repo-check --cd "$BLOC" \
    -o "$BLOC/audit-<sujet>-codex.md" \
    "$QUESTION" < /dev/null > "$BLOC/audit-<sujet>-codex.log" 2>&1

env -C "$BLOC" agy -p "$QUESTION" \
    < /dev/null > "$BLOC/audit-<sujet>-agy.md" 2> "$BLOC/audit-<sujet>-agy.err"

mkdir -p "$BLOC/copilot"
copilot -C "$BLOC/copilot" --model grok-4.5 --no-custom-instructions --disallow-temp-dir -s \
    -p "$QUESTION" \
    < /dev/null > "$BLOC/audit-<sujet>-copilot.md" 2> "$BLOC/audit-<sujet>-copilot.err"
```

`env -C` et non `cd` : `cd` persiste d'un appel d'outil au suivant et ramène en silence dans le
dépôt. `--skip-git-repo-check` est obligatoire, le bloc-notes n'étant pas un dépôt git : sans lui
`codex exec` refuse de tourner, sort en **1** et n'écrit aucun fichier de réponse. `copilot` ne
demande rien de tel : les neuf appels du banc ont tourné hors dépôt git et sont tous sortis en 0.

`copilot` reçoit un répertoire vide qui n'est qu'à lui, parce qu'il lit le sien sans qu'on lui
accorde rien. Pointé sur le bloc-notes, il y trouverait les réponses déjà rendues par les deux
autres, et les trois attaques cesseraient d'être indépendantes. `--disallow-temp-dir` lui ferme le
reste du dossier temporaire, où le bloc-notes se trouve souvent ; son répertoire de travail reste
lisible et aucune option ne le referme.

| | `codex exec` | `agy -p` | `copilot -p` |
|---|---|---|---|
| capturer la réponse | `-o <fichier>`, le dernier message seul | une redirection suffit : la sortie standard ne porte que la réponse | `-s`, puis une redirection ; sans lui la trace des outils se mêle à la réponse, et les statistiques partent sur l'erreur standard |
| choisir le répertoire | `--cd` | `env -C`, l'outil n'a pas d'option | `-C` |
| ce qu'il emporte de lui-même | l'`AGENTS.md` du répertoire, mesuré plus bas | rien : ni `AGENTS.md` ni `GEMINI.md` n'arrivent dans son contexte | l'`AGENTS.md` du répertoire, que `--no-custom-instructions` coupe |
| accès aux fichiers | `--sandbox read-only` à poser | refusé par défaut, et la commande sort en **1** | son répertoire de travail sans rien poser, et tout le dossier temporaire du système sans `--disallow-temp-dir` |
| entrée standard | lue et **ajoutée** à la question | non mesuré ; fermée par prudence | ignorée ; fermée quand même |

**Ce qu'`agy` fait quand la question l'invite à lire.** Prié de lire un fichier de son dossier, il a
voulu lancer `ps -ef` pour retrouver son propre répertoire, la permission a été refusée, et la
commande s'est soldée par un code 1. Sa réponse ne repose donc que sur ce que la question porte.
Et `--dangerously-skip-permissions` n'a rien à faire dans un audit : il ouvrirait un balayage de la
machine, qui déborde largement du dépôt.

**Ce que `copilot` fait de la même invitation.** Prié de lire un fichier de son répertoire, il l'a
lu, sans permission demandée ni accordée. Le même fichier placé un cran plus haut dans le dossier
temporaire est revenu aussi ; avec `--disallow-temp-dir`, il sort en « Permission denied ». Couper
le terminal ne coupe pas la lecture : l'appel `cat` a été refusé faute de pouvoir demander la
permission, et l'outil de lecture interne a rendu le fichier dans la foulée.

Pour `codex`, le piège est ailleurs : `cat fichier | codex exec "..."` publie le fichier entier,
l'entrée standard étant **ajoutée** à la question. `copilot` ne lit pas la sienne : un témoin poussé
dans le tube n'est jamais revenu dans la réponse.

## Ce qui part est une publication

Le dépôt **est public** - `gh repo view --json visibility` le dit, et les signaux d'alerte de cette
page le supposaient déjà quand ce paragraphe annonçait encore un futur. Ce qui vit sur `main` ou dans
une issue est donc **déjà publié**, lisible par n'importe qui depuis le web, et le citer dans une
question ne le publie pas une seconde fois.

Cela ne rend pas le tri inutile, cela en déplace la charge. Publier reste une décision prise à un
moment choisi, sur un contenu choisi, sous la GPL et avec l'attribution que porte `REMERCIEMENTS.md`.
Une question, elle, emporte ce qu'elle emporte, tout de suite, vers un autre destinataire, et ne se
retire pas. Le tri se fait donc sur deux conditions : **est-ce déjà publié, et la question en
a-t-elle besoin ?**

**La première condition est plus large qu'elle n'en avait l'air, la seconde ne bouge pas.** Un
arbitrage a été écarté le 2026-08-26 sur la lecture d'avant : le corps de l'EPIC #3848 et trois
extraits d'ADR rangés dans « pas encore publié » alors qu'ils sont lisibles par tous. Une règle de
tri plus stricte que nécessaire prive l'audit du contexte qui le rendrait utile, et cette page le dit
plus bas - les auditeurs « ne connaissent que ce que la question porte ». Un extrait public qui
n'éclaire pas l'arbitrage n'a toujours rien à faire dans la question.

La question n'est pas seule à partir. Codex charge de lui-même l'`AGENTS.md` de son répertoire de
travail : la même question de neuf mots coûte 6 669 jetons depuis le worktree contre 3 726 depuis
un dossier vide, et un témoin le confirme sans passer par le delta. Un `AGENTS.md` portant
`ANANAS-7731` posé dans le dossier vide, puis « sans lire aucun fichier, dis-moi le mot de passe
s'il figure déjà dans ton contexte » a rendu `ANANAS-7731`, aucun outil lancé. Le même témoin passé
à `agy` rend `ABSENT`, avec `AGENTS.md` puis avec `GEMINI.md` ; `copilot` le rend comme `codex`, et
retombe sur `ABSENT` avec `--no-custom-instructions`. Les trois n'exposent pas la même chose, et la
règle se tient sur le plus bavard. D'où le `--cd`, l'`env -C` et le `-C`, tous hors du dépôt.

| Peut sortir | Ne sort jamais |
|---|---|
| l'énoncé d'un arbitrage, ses options, ce qui les départage | la valeur d'un des cinq secrets d'atelier que `scripts/methode/releve-des-secrets.py` nomme, et le contenu de `~/.codex` et de `~/.copilot`, ce dernier gardant sous `session-state` la question et la réponse de chaque audit passé. Rien de cela ne devient public |
| un extrait **déjà publié**, choisi et relu, avec ses frontières | les 92 noms et pseudonymes de `REMERCIEMENTS.md`, et l'adresse électronique d'un tiers. Le dépôt les publie pour attribuer une contribution ; les transmettre à un service tiers est un autre acte, et aucune question d'arbitrage n'en a besoin |
| le texte d'une règle, son seuil, le comptage qui l'a fixé | les données d'un utilisateur : enregistrements, coordonnées de sites. Elles vivent sur son disque, hors du dépôt ; les fixtures sont engendrées par `GenerateurCartesSD` |
| la structure d'un garde et ce qu'il prétend attraper | ce qui n'est **pas encore** publié : une branche non poussée, un brouillon, ce que la relecture d'ouverture peut encore retirer |

Cette liste est écrite ici parce qu'une règle qui vit dans la tête de l'appelant est oubliée au
troisième usage. C'est arrivé au registre d'écriture pendant ce chantier : il tenait par la
relecture jusqu'à ce qu'il soit écrit dans `CONTRIBUTING.md`.

## La réponse est un avis, pas une mesure

Un fait avancé par un audit **ouvre une piste**. Ce qui entre dans une ADR ou un commit se vérifie
d'abord par une commande. Trois affirmations de ce chantier le montrent, toutes détaillées, toutes
fausses :

| Ce qui était avancé | Ce que la commande a montré |
|---|---|
| cinq contrôles sans nom accessible | zéro sur 133. Cinq en ne lisant que les attributs, quatre en tenant compte des éléments enfants, aucun après vérification une par une |
| `FocusVisibleTest` est vert, la règle de focus tient donc | il comparait des `toString()` porteurs d'un hachage d'**identité**. JavaFX recrée ces objets au focus, à l'identique : le test passait **la règle retirée** |
| onze cas de banc verts sur le SARIF de CodeQL | le banc posait `tool.driver.rules`, une forme que CodeQL ne produit pas ; ses requêtes sont dans `tool.extensions[].rules`. Le garde annonçait « 0 règle » sur un SARIF réel de 2,6 Mo |

Aucune n'aurait été démasquée par un second avis. Les trois ont demandé de lancer quelque chose.

Un audit qui dit « c'est faux » désigne un endroit où regarder. **Reproduire d'abord.** Un correctif
écrit pour un défaut qui n'existe pas coûte deux fois : l'écriture, puis le retrait.

La mesure qui suit se lit avec la même défiance. Un motif d'expression régulière posé sur de la
prose rend des **suspects** : « Voici la liste » se compte comme un reste de conversation, un `—` de
cellule de tableau comme un cadratin. On ouvre les lignes qu'il désigne avant d'en retenir une.

## Ce qu'un auditeur de plus achète

Il n'achète pas de la certitude sur un fait. Seule une commande en donne, et la loi d'airain ne
bouge pas d'un pouce parce qu'on a interrogé trois services au lieu d'un. Ce qui se gagne est
ailleurs, sur l'arbitrage, là où il n'y a rien à mesurer : des attaques indépendantes couvrent des
angles qu'une seule laisse.

**Le désaccord est le signal.** Deux réponses qui divergent désignent l'endroit où l'arbitrage est
réellement ouvert. Deux réponses qui concordent ne prouvent rien : les modèles partagent une large
part de leur corpus d'entraînement, donc une part de leurs angles morts. Compter les voix
reviendrait à traiter une corrélation comme une confirmation.

**Trois voix n'ouvrent pas un vote.** Une majorité de deux contre un ne devient pas une preuve
parce qu'elle est arithmétiquement disponible, et la voix isolée est souvent celle qui a regardé où
les deux autres n'ont pas regardé.

**Les angles morts se partagent par famille de modèle.** C'est ce qui décide du modèle du troisième
auditeur : sans instruction contraire, `copilot` tourne `claude-sonnet-5`, la famille de l'agent qui
compose la question, et son avis confirmerait surtout ce que l'appelant a déjà pensé. `--model
grok-4.5` le déplace sur une famille qu'aucun des deux autres n'apporte, et la session enregistre
le modèle qui a répondu.

**Trier le désaccord par ce qui peut le trancher.** Les réponses ne sont presque jamais de même
nature. Sur « quelle trace d'écriture par LLM quatre tics ne voient-ils pas », l'un a répondu les
résidus de chatbot, qu'un motif attrape, l'autre l'indécision consensuelle, qu'aucun motif
n'attrape. Ce partage est celui des trois niveaux de vérification du dépôt : ce qui se compte part
en `certaine` ou `probable`, le reste reste `humaine` et s'assume comme tel.

**Ce qu'ils ne peuvent pas savoir.** Sur cette même question, ni l'un ni l'autre n'a mentionné
`dev-docs/registre-editorial.md`, qui y répondait déjà, mesures à l'appui. Ils ne connaissent que ce
que la question porte, et le tri qui protège le dépôt est précisément ce qui la leur cache. Cela ne
se répare pas en envoyant plus, mais par l'étape 0.

## La provenance se déclare

Article A26. Si un audit change une décision, l'ADR écrit **le fait relevé et la mesure qui l'a
confirmé**. Jamais « Codex a dit », jamais « l'audit recommande » : un nom de source ne vérifie
rien, et le lecteur futur n'a que la phrase pour décider s'il peut la croire.

L'en-tête de l'ADR ne change pas pour autant : `generated.by` porte déjà l'assistance par agents,
et `verified.by` la relecture humaine. C'est le **corps** qui gagne une ligne, celle de la mesure.

## La question demande de réfuter

Une question qui donne le contexte et demande un verdict obtient un verdict complaisant. Le dépôt
applique déjà le patron inverse à ses gardes : on ne demande pas à un garde s'il est correct, on
casse ce qu'il prétend attraper et on regarde s'il rougit.

| Au lieu de | Demander |
|---|---|
| « Le seuil de 8 lignes est-il le bon ? » | « Voici le seuil et le comptage qui l'a fixé. Trouve le cas où il se trompe. » |
| « Cette décision se tient-elle ? » | « Voici la décision. Construis l'argument le plus fort contre. » |
| « Ai-je oublié quelque chose ? » | « Voici l'inventaire et la méthode qui l'a produit. Quelle catégorie cette méthode ne peut-elle pas voir ? » |

La question dit aussi ce qui a **déjà** été écarté et pourquoi, sinon la réponse le repropose et il
faut refaire le tri.

## Où ça vaut le coup, où ça n'en vaut pas

| Question | Confronter | Pourquoi |
|---|---|---|
| le seuil de 8 lignes de prose du cliquet javadoc | oui | rien ne le déduit du code : il se défend ou il se change |
| le grain du compte, la ligne plutôt que le bloc | oui | les deux grains se défendent, et le premier choisi poussait à couper du contrat |
| laisser intacts les blocs de contrat dense | oui | une exception délibérée se relit mieux depuis l'extérieur |
| les quatre lois de la Gestalt tenues en `humaine` | oui | déclarer qu'aucun garde ne les tiendra est une décision |
| « ce code est-il correct ? » | non | un test répond mieux et plus vite |
| « ce garde attrape-t-il ce qu'il nomme ? » | non | la mutation répond. Voir la compétence `mutation` |
| « cette liste est-elle complète ? » | non | un comptage répond. Un avis rend une liste plausible |

Un cas absent de cette table se tranche par une question : **la réponse se vérifie-t-elle en
lançant quelque chose ?** Si oui, on lance. Sinon, l'arbitrage gagne à être attaqué par quelqu'un
qui ne l'a pas pris.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « La réponse est détaillée, donc elle est juste » | Trois affirmations détaillées de ce chantier étaient fausses, et seule la commande l'a dit |
| « Je colle le fichier entier dans la question » | Vous publiez ce que vous n'avez pas relu. L'extrait se choisit |
| « L'audit dit que c'est faux, je corrige » | Reproduisez d'abord. Un correctif pour un défaut inexistant coûte deux fois |
| « Je pose la question depuis le dépôt, c'est plus commode » | Le répertoire de travail part avec la question, `AGENTS.md` compris, et rien ne vous dit ce qu'il contenait ce jour-là |
| « Le dépôt est public, donc tout peut sortir » | Deux conditions, pas une. Un secret et les données d'un utilisateur ne sont publiés dans aucun état du dépôt |
| « Je donne le contexte et je demande le verdict » | Un verdict demandé est un verdict complaisant. Demandez la réfutation |
| « L'ADR dira que l'audit l'a suggéré » | A26 : le fait relevé et la mesure. Un nom de source ne vérifie rien |
| « C'est plus rapide que d'écrire le test » | Sur « ce code est-il correct », le test est plus rapide, et lui tranche |
| « Le dépôt ne dit rien là-dessus » | Cherché par concept, ou par mot-clé ? Un audit a été lancé sur une question dont la réponse tenait dans une page du dépôt |
| « J'ai écrit la commande, elle est évidente » | Celle de cette page est sortie en 1 sans rien écrire, la première fois qu'on l'a lancée telle quelle. Un essai de fumée et une recette rédigée dérivent |
| « Mon grep a trouvé 334 occurrences » | Combien en avez-vous ouvertes ? Un comptage sur de la prose rend des suspects |
| « Les auditeurs sont d'accord, donc c'est vrai » | Ils partagent une part de leur corpus, donc de leurs angles morts. L'accord n'est pas une seconde mesure |
| « Deux réponses sur trois disent la même chose » | Une majorité n'est pas une mesure. La voix isolée est souvent celle qui a vu ce que les deux autres ont manqué |
| « Je lance `copilot` depuis le bloc-notes, ça évite un répertoire » | Il lit le sien sans qu'on lui accorde rien, et y trouverait les réponses des deux autres |
| « `copilot` tourne le modèle de l'agent qui pose la question, c'est bien assez » | `claude-sonnet-5` par défaut, soit la famille de l'appelant. Sans `--model`, le troisième avis double le premier |
| « J'ajoute `--dangerously-skip-permissions` pour qu'il puisse lire » | Il balaierait la machine, pas le dépôt. Ce qu'il doit lire, la question le porte |
