---
type: adr
title: "Une commande nomme un geste, et cite la compétence au lieu de la recopier"
status: stable
article: A5
chantier: "#4516, chantier #4511 (mise en service d'OpenSpec)"
decided_at: 2026-08-26
verification: certaine
enforced_by:
  - "scripts/methode/verifie-adoption-openspec.py"
relations:
  amende: ["4515-adopter-un-arbre-amont-quand-il-doit-parler-notre-cycle", "4339-un-arbre-repris-d-un-outil-amont-se-declare"]
verified:
  - by: machine:ci
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Une commande nomme un geste, et cite la compétence au lieu de la recopier

## Contexte

L'[ADR 4515](4515-adopter-un-arbre-amont-quand-il-doit-parler-notre-cycle.md) a adopté les six
compétences `openspec-*` : réécrites en français, tissées au cycle du dépôt. Les six commandes
`.claude/commands/opsx/` sont restées en anglais amont. Le même flux existait donc en deux versions,
et c'est la version périmée qu'un humain invoquait à la main. Cinq compétences sur six renvoyaient
vers elle, soit dix-sept renvois `/opsx:` dans le fonds.

Le lot devait apprendre au générateur à engendrer les commandes depuis les compétences. Deux mesures
ont écarté ce chemin.

**Le format est déclaré hérité par l'outillage** : « The `.claude/commands/` directory is a legacy
format. For new skills, use the `.claude/skills/<name>/SKILL.md` directory format. Both are loaded
identically. » Une compétence est déjà une commande.

**Renommer les dossiers ferait cohabiter deux familles.** Témoin qui mord : après renommage,
`openspec update --force` recrée les six compétences à nom d'outil en anglais, et les six renommées
survivent. Douze là où il en faut six.

## Décision

**Une commande nomme le geste, tient en une ligne, et délègue à sa compétence.** Six relais
remplacent le dossier `opsx/`, dont le préfixe venait de l'outil et non d'un choix.

| On tape | Ouvre |
|---|---|
| `/instruire` | `openspec-explore` |
| `/proposer` | `openspec-propose` |
| `/realiser` | `openspec-apply-change` |
| `/reprendre` | `openspec-update-change` |
| `/fusionner` | `openspec-sync-specs` |
| `/archiver` | `openspec-archive-change` |

C'est l'article A5 appliqué à un artefact d'agent : un inventaire ne se duplique pas, il se cite. Le
flux vit dans la compétence, à un seul endroit, et le relais n'a rien à rattraper quand elle change.

### Les dossiers gardent leur nom d'outil, et c'est protecteur

Le nom visible est celui de la commande, pas celui du dossier. Garder `openspec-*` sur les dossiers
fait que l'outil **écrase** nos fichiers au lieu de les doubler en silence. Un écrasement se
détecte ; un doublon se découvre trop tard.

### Ce qui reste amont se réduit à rien

`REPRIS` de `4366-avertissement-en-pictogramme.py` devient vide, et les trois exemptions `AMONT` de
`2843-tiret-cadratin.py` tombent avec le dossier qu'elles nommaient. Aucun texte OpenSpec n'est plus
repris verbatim ; seuls les trois diagrammes Mocodo restent exemptés.

## Ce que l'adoption coûte, et qui le paie

Adopter, c'est devenir l'auteur. `openspec update --force` rend les six fichiers à l'anglais amont,
et la commande fait exactement ce qu'elle promet : c'est nous qui avons changé de camp.

`verifie-adoption-openspec.py` exige dans chaque compétence deux marqueurs que l'amont ne peut pas
produire, `langue: fr` et `origine:`, dans les deux arbres. Mesuré sur une copie jetable au foyer
isolé, `HOME` et `XDG_CONFIG_HOME` déplacés, parce que la commande écrit dans la configuration
globale du poste :

| Geste | Marqueurs | Garde |
|---|---|---|
| `openspec update` | 12/12 | vert |
| `openspec update --force` | 0/12 | rouge |

Le contrôle négatif compte autant que le rouge. Un garde qui rougirait sur un geste anodin serait
désactivé en trois semaines, et on serait revenu au point de départ en ayant payé le trajet.

Le garde refuse **par entrée de corpus** et non sur le total. Une liste de chemins dont un membre a
disparu rend encore des fichiers, et un refus sur le total resterait vert en n'ayant vérifié que la
moitié de sa portée : c'est l'article A3. Les deux gardes OpenSpec antérieurs portent ce défaut,
relevé en écrivant celui-ci et traité par #4566.

## Une décision de ne pas faire

Le générateur n'apprend rien. `synchronise-adaptateurs.py` reste un copieur d'octets entre
`.agents/skills` et `.claude/skills`, et son en-tête reste vrai. Un troisième artefact engendré dans
un format qu'on nous dit d'abandonner aurait été une dette écrite exprès.
