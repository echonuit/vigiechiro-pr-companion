# ADR 3389 - Ce que l'application affiche tient dans la police embarquée

- **Statut** : Accepté - 2026-08-06
- **Chantier** : #3389, suite de l'[ADR 3361](3361-la-typographie-est-embarquee.md)
- **Vérification** : certaine - `PoliceCouvreLIhmTest#la_police_couvre_tout_ce_qui_s_affiche`

## Contexte

L'[ADR 3361](3361-la-typographie-est-embarquee.md) embarque Noto Sans pour que le produit cesse de
dépendre de la police de la machine, et l'[ADR 3374](3374-une-fenetre-porte-son-habillage-ou-elle-n-est-pas-le-produit.md)
la fait appliquer à **toute** fenêtre. Reste une porte ouverte que ni l'une ni l'autre ne ferme : un
caractère **absent de la fonte** part en **repli** vers une police du système. Deux machines ne
replient pas sur la même, et **rien ne le signale** - le glyphe s'affiche, simplement pas le même.

Le symptôme repéré par #3389 était le `≤` de « Archives de dépôt Tadarida (≤ 700 Mo) », visiblement
plus gras en CI qu'en local. Lecture de la table `cmap` : **huit** caractères d'IHM manquaient à la
fonte - `→ ⚠ ☰ ≥ ≤ ▸ ← −` - dans une quarantaine de messages.

!!! note "Deux chiffres faux avant celui-ci"
    Un premier relevé annonçait **26** caractères : il comptait les commentaires et les messages de
    journal. Un second, resserré aux seuls `setText`, en trouvait **2** : il manquait tout ce qui se
    construit par concaténation. Le bon compte se lit sur les **littéraux, commentaires ôtés**, dans le
    périmètre défini ci-dessous.

## Décision

**Tout ce que JavaFX affiche s'écrit avec des caractères que la police embarquée couvre.** Un
caractère non couvert se remplace, il ne s'embarque pas.

Les pictogrammes (`→ ← ▸ ⚠ ☰`) suivent l'[ADR 0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md)
- un pictogramme d'IHM est une **icône**, pas un caractère - et les signes mathématiques deviennent du
français : « au moins 700 Mo », « Intervalle conseillé : au moins 1 mois ».

Embarquer deux fontes de plus (Noto Sans Symbols 2, Noto Sans Math) aurait laissé le texte intact pour
quelques centaines de kilo-octets. Écarté : on aurait embarqué deux fichiers pour **huit glyphes**, et
la question se reposerait au neuvième.

### Le périmètre s'arrête à ce que JavaFX rend

**Une sortie terminal n'est pas concernée.** Une commande écrit dans une console dont la police nous
échappe : `TexteCompteRendu` le documente déjà pour ses marqueurs de sévérité (`✓ · ⚠ ✗`), et ils
restent. Le garde exclut donc `cli/`, `perf/`, `commun/api/` et les journaux des outils de capture.

**Deux fichiers portent ces caractères comme donnée**, non comme affichage :
`RetourOperation.GLYPHES_DE_SEVERITE` est la liste des glyphes **refusés** en tête de message. Les
citer est son objet.

**La documentation non plus** : elle est rendue par un navigateur. « menu ☰ » y devient toutefois
« menu principal (☰) », pour que le vocabulaire du produit et celui de sa doc concordent - l'icône
reste comme indice visuel.

## Conséquences

- **la fidélité du rendu ne dépend plus des glyphes utilisés**, et pas seulement de la famille
  déclarée. C'est la dernière des trois causes de #3389 qui passait encore dans les aperçus ;
- **trois cadenas 🔒** de `PassageViewModel` sont tombés au passage : l'ADR 0035 les interdisait déjà,
  et personne ne l'avait vu. Un cliquet trouve ce qu'une relecture ne cherche pas ;
- `PoliceCouvreLIhmTest` **nomme le caractère et le fichier** quand il refuse. Il vérifie en outre
  qu'il sait encore voir ce qu'il cherche - il exige que `é` passe et que `≤` échoue - faute de quoi
  une police remplacée le rendrait vert pour la pire des raisons.

## Ce que cette ADR ne traite pas

Les deux autres causes de #3389 - la **langue** et le **fuseau** de la machine - sont fixées dans
`capture-screenshots.sh`, donc pour la CI **et** pour un poste, puisque les deux lancent ce script. Un
réglage posé dans le workflow n'aurait discipliné que la CI, et déplacé l'écart au lieu de le
supprimer.

⚠️ **Le produit, lui, reste sensible au fuseau de la machine qui l'exécute.** Épingler les captures ne
le corrige pas. Le fuseau d'une nuit est-il celui du site d'écoute ou celui de l'observateur qui
dépouille ? C'est une question de conception, ouverte en **#3406**.
