---
type: adr
title: "Un clip tourné contre la plateforme ne se range pas avec les autres"
status: stable
article: A5
chantier: "#4291"
decided_at: 2026-08-24
verification: certaine
enforced_by:
  - ".github/scripts/verifie-portee-des-secrets.sh"
verified:
  - by: machine:ci
    at: 2026-08-24
relations:
  prolonge: ["4142", "4134"]
---

# Un clip tourné contre la plateforme ne se range pas avec les autres

## Contexte

L'[ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md) a mesuré que sur les 360 cas de recette non
couverts, environ **220** vivent dans des sessions dont l'objet est **hors de l'application**. Filmés
contre un bouchon, ils donnent un clip convaincant et **creux** : muet sur son propre objet.

Le bouchon n'est pas un homme de paille. `stub_vigiechiro.py` fait 135 lignes et ne sert que la
ressource `sites` : il n'exige aucun `If-Match`, ne rejette jamais un `max_results` au-delà de 100, et
ne connaît ni les dates RFC 1123 ni le refus de `numero`. Le vert des scénarios dit donc que notre
client parle à **notre idée** de la plateforme.

Filmer contre la vraie plateforme lève cette limite, et en crée trois autres.

## Décision

**1. Un clip connecté ne se range pas avec les autres, et ne se compare pas.**

Il va sur sa propre pré-version, `clips-connectes`, et `comparer-tournages.yml` **refuse** cette source
en disant pourquoi.

La raison n'est pas le rangement : son écran suit des **données vivantes**. Deux tournages du même
commit peuvent différer parce qu'une nuit a été traitée entre les deux, et le plancher de bruit de
l'[ADR 4287](4287-un-ecart-se-lit-contre-le-plancher-de-son-cas.md) - médiane 0,008 %, pire cas
0,809 % - mesurerait alors la plateforme au lieu du rendu. Le plancher par cas n'y répond pas : un cas
connecté aurait un bruit énorme et se retrouverait durablement en bas du classement, y compris le jour
où il change pour une vraie raison.

C'est la raison qui a fait préfixer `bash-` et `java-` en #4258, **en pire** : là on écrasait des
fichiers, ici on fausserait un verdict sans que rien ne bouge, puisque les deux dispositifs produisent
des clips parfaitement valides.

**2. Le secret ne dépasse pas son pas, parce que rien d'autre ne peut le rattraper.**

Posé dans l'`env:` d'un job, un jeton n'est pas offert au pas qui en a besoin mais à **tout ce que le
job exécute**. Trois pas, dont aucun ne se voit en lisant le YAML : `ConnexionModule.jetonPonctuel()`
lit `System.getenv`, les forks surefire **héritent** de l'environnement du job, et
`ConnexionModule.urlDeBase()` vaut la **production** par défaut.

⚠️ Une parade structurelle a été cherchée, puis **retirée après mesure** : un compte rétrogradé au rôle
`Lecteur`, pour que le serveur refuse d'écrire plutôt que notre discipline. `grep -rn "roles=.Lecteur"`
est **vide**. Le rôle est déclaré dans `ROLE_RULES` et **aucune route ne l'accepte**, `GET /moi`
compris. Un jeton en lecture seule **n'existe pas** sur cette plateforme.

La lecture seule ne tient donc que par le câblage, et une propriété qui ne tient que par la discipline
appelle un garde ([ADR 4235](4235-le-garde-d-abord-l-abstraction-ensuite.md)) : `verifie-portee-des-secrets.sh`
refuse tout secret `VIGIECHIRO_*` au-dessus d'un pas.

Et le garde ne suffit pas seul. Le banc **lie sa propre source de jeton** plutôt que d'emprunter celle
du processus : c'est l'[ADR 4134](4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md) d'un
cran plus haut - là c'était la fenêtre primaire de TestFX, ici l'environnement du processus.

**3. Le jeton du tournage meurt avec le run, donc il ne peut pas être celui du contrat.**

Le tournage produit une **image**, et une image ne se masque pas : le masquage de GitHub ne couvre que
les journaux. À terme ces clips sont publiés, et une fuite sur le tag d'une version est définitive.

Le jeton est donc révoqué en fin de run (`POST /logout`, `always()`), ce qui ramène la fenêtre
d'exposition de quatorze jours à la durée d'un tournage. Un jeton révocable ne peut pas être celui du
contrat hebdomadaire, qui vit de durer : d'où un **second secret sur le même compte**, la plateforme
stockant une carte de jetons par compte.

⚠️ L'axe n'est **pas** l'accès. Même compte, mêmes droits, et un second secret de dépôt n'isole rien
puisque tout workflow du dépôt lit tout secret du dépôt. L'axe est le **cycle de vie**, et c'est la
seule raison qui justifie deux secrets.

## Ce que le premier tir a appris, et qu'aucun vert local n'aurait dit

Le tournage connecté a rendu `Tests run: 0` et un **BUILD SUCCESS**. Poser `-Dsurefire.groups` ne lève
pas l'exclusion : surefire n'avait rien sélectionné, et seul l'oracle a rougi, à l'étape suivante, sur
un index absent.

Un dispositif qui ne sélectionne rien **réussit tranquillement**. C'est la forme d'échec que ce dépôt
traque partout, et elle s'est produite ici sur le dispositif même qui devait la traquer.

Le tir a aussi prouvé ce qu'aucun auto-test ne prouve : la révocation a tourné **alors que le film
avait échoué**, et la plateforme a rendu `200`.

## Ce qui n'est pas gardé, et qui le sait

Les décisions **2** et **3** portent chacune leur garde autotesté, vues rouges sur mutation.

La décision **1** ne l'était pas quand cette ADR a été écrite, et c'était le plus sournois des trois
trous relevés en passe 6 : le refus de `clips-connectes` vivait dans un `if:` de workflow, et rien
n'aurait rougi si on l'avait retiré - la comparaison serait revenue mesurer la plateforme en croyant
mesurer le rendu.

Elle l'est depuis #4331. `verifie-decisions-du-tournage-connecte.sh` tient les trois décisions de
cette ADR, et il ne **relit** pas le refus : celui-ci vit dans un `run:`, donc du shell, et le garde
l'extrait et le **lance** contre la source `clips-connectes` pour lire son message.

Cette section est laissée telle qu'elle a été pensée, au passé, plutôt que supprimée : ce qu'elle
disait était vrai, et un lecteur qui arrive par #4331 doit pouvoir retrouver pourquoi ce garde
existe.
