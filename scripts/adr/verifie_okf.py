#!/usr/bin/env python3
"""Garde du paquet OKF que forme `dev-docs/decisions` (chantier A).

Avant ce garde, rien ne contrôlait la forme d'un en-tête d'ADR : 172 documents se déclaraient tous
« Accepté », et le lecteur devait croire chaque ligne sur parole. La conversion en champs typés
n'apporte rien par elle-même ; ce sont les refus ci-dessous qui la rendent utile.

**Le lecteur YAML est volontairement étroit et strict.** Il ne connaît que la forme que le dépôt
écrit, et il refuse tout le reste au lieu de deviner. Un analyseur tolérant rendrait un en-tête
approximatif « lisible », donc vert, et la conformité ne voudrait plus rien dire. La sévérité du
lecteur EST le premier des huit contrôles. Aucune dépendance hors stdlib, comme ses voisins :
`lint.yml` n'installe rien, et un garde qui exige un paquet absent ne garde rien du tout.

Les dix refus, et ce qui prouve qu'ils manquaient :

| Refus | Ce qu'il attrape | Pourquoi il manquait |
|---|---|---|
| en-tête | un fichier sans en-tête analysable, ou sans `type` | rien ne contrôlait la forme |
| rattachement | une ADR sans article, ou visant un article absent | le lien vivait dans la prose |
| statut et graphe | une ADR `stable` qu'une autre renverse | zéro `deprecated` pour 21 amendements |
| succession | une `deprecated` qui ne nomme pas ce qui la remplace | les annulations n'avaient pas de cible |
| confiance | `certaine` sans applicateur, `probable` sans cliquet | le niveau se déclarait sans gage |
| gage qui juge | un `enforced_by` qu'aucune demande ne peut faire rougir | le seul contrôle vérifiait que le fichier **existe** |
| atteignabilité | une ADR absente de `index.md` ou de la nav | 172 fichiers, aucun contrôle |
| liens | un renvoi croisé vers un fichier absent | OKF le tolère, le dépôt ne doit pas |
| cliquet de corpus | une disparition silencieuse d'ADR | une consolidation peut perdre une décision |
| heuristiques | une clé hors du vocabulaire clos, ou déclarée en scalaire | une faute de frappe créait une heuristique de plus, en silence |

Le garde porte aussi deux CONSTATS, qui ne refusent pas : les heuristiques que rien ne sert, et les
ADR d'un article d'usage qui n'en déclarent aucune. Le second est sous cliquet (article A29).
"""

import argparse
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import tempfile

from _commun import DECISIONS, rapporte, sort_si_contrat_demande

RACINE = pathlib.Path(__file__).resolve().parents[2]
# `DECISIONS` s importe : le corpus se declare dans `_commun` et nulle part ailleurs (ADR 4586).
# `RACINE` reste, car les trois chemins ci-dessous ne sont pas le corpus des ADR.
CONSTITUTION = RACINE / "CONSTITUTION.md"
NAV = RACINE / "mkdocs-dev.yml"
RESERVES = {"index.md", "log.md"}

# Un gage qui ne s'execute pas. Un document se lit, il ne juge rien : l'ADR 4993 nommait 119 lignes
# de prose en applicateur, et son propre `verified:` disait pourtant `humain:porteur-du-produit`.
GAGES_INERTES = {".md", ".txt", ".json", ".csv", ".rst", ".adoc"}

# Les declencheurs qu'une DEMANDE produit. Un atelier qui n'en porte aucun ne tournera jamais sur une
# pull request : il peut etre vert, rouge ou absent sans qu'aucune demande en sache rien. L'ADR 3802
# nommait un atelier `schedule` + `workflow_dispatch`, donc un detecteur hebdomadaire.
DECLENCHEURS_DE_DEMANDE = {"pull_request", "push", "merge_group", "pull_request_target"}

ATELIERS = ".github/workflows"

# Le point d'entree qui fait d'un script un juge plutot qu'une bibliotheque. L'ADR 5239 nommait
# `.github/assets/mesure_pixels.py`, importe par trois autres scripts et lance par aucun : c'etait
# le code REGI, pas son juge.
POINT_D_ENTREE = re.compile(r'if __name__ == ["\']__main__["\']')

# Assemblee plutot qu'ecrite d'un bloc : ce garde porte lui-meme l'option, et une constante litterale
# le ferait se reconnaitre comme juge pour la mauvaise raison. Meme idiome que `_forge.py`, qui vit
# dans un autre paquet et ne s'importe pas d'ici.
OPTION_AUTO_TEST = "--auto" + "-test"

# Le corpus ne descend jamais sous ce plancher sans qu'on l'ait décidé. Un cliquet, pas un nombre
# exact : une ADR nouvelle est un progrès, une ADR disparue est une décision perdue.
PLANCHER_CORPUS = 194

ARTICLE = re.compile(r"^###\s+(A\d+)\s*:", re.M)

# L annexe close des heuristiques d ergonomie. Le vocabulaire vit LA et nulle part ailleurs : le
# garde le lit plutot que d en tenir une copie, sans quoi les deux listes deriveraient l une de
# l autre sans que rien ne le dise.
ANNEXE_HEURISTIQUES = RACINE / "dev-docs" / "ergonomie" / "heuristiques.md"

# Une cle du vocabulaire, telle que l annexe la pose : premiere colonne, entre accents graves.
CLE_HEURISTIQUE = re.compile(r"^\| `([a-z0-9-]+)` \|", re.M)

# Le debut de la matrice engendree par `matrice-ergonomie.py`, ecrit dans la meme annexe. Le
# vocabulaire se lit AVANT elle : elle repete les memes cles sous la meme forme.
DEBUT_MATRICE = "<!-- matrice engendree : ne pas editer a la main -->"

# Les huit articles qui touchent l USAGE. Une ADR rattachee a l un d eux et qui ne declare aucune
# heuristique est un SUSPECT, pas une faute : un refus sec rendrait le garde rouge sur tout le
# corpus le jour de sa pose, et il serait desactive dans la semaine (article A9).
ARTICLES_D_USAGE = {"A12", "A13", "A14", "A15", "A18", "A19", "A23", "A28"}

# Le numero de l ADR qui porte ce cliquet. Ici l identite d une ADR est son numero, non son slug.
ADR_ERGONOMIE = "4342"
RENVOI = re.compile(r"\]\((\d[a-z0-9-]*\.md)(?:#[^)]*)?\)")
# Les verbes qui DÉPASSENT une décision, par opposition à ceux qui la citent ou la prolongent.
DEPASSEMENT = {"renverse", "remplace", "annule"}


class EnteteInvalide(ValueError):
    """L'en-tête sort de la forme que le dépôt écrit."""


def _valeur(brut: str):
    brut = brut.strip()
    if brut.startswith('"') and brut.endswith('"') and len(brut) >= 2:
        return brut[1:-1].replace('\\"', '"').replace("\\\\", "\\")
    if brut.startswith("[") and brut.endswith("]"):
        dedans = brut[1:-1].strip()
        return [_valeur(x) for x in _decoupe(dedans)] if dedans else []
    return brut


def _decoupe(ligne: str) -> list[str]:
    """Découpe une liste en ligne, sans couper à l'intérieur des guillemets."""
    morceaux, courant, dans = [], "", False
    for c in ligne:
        if c == '"':
            dans = not dans
        if c == "," and not dans:
            morceaux.append(courant)
            courant = ""
            continue
        courant += c
    if courant.strip():
        morceaux.append(courant)
    return morceaux


def lit_entete(texte: str) -> dict:
    """L'en-tête YAML d'un document, dans le sous-ensemble que le dépôt écrit.

    Lève `EnteteInvalide` sur tout ce qui sort de cette forme, plutôt que de deviner.
    """
    if not texte.startswith("---\n"):
        raise EnteteInvalide("aucun en-tête YAML en tête de fichier")
    fin = texte.find("\n---\n", 4)
    if fin == -1:
        raise EnteteInvalide("en-tête YAML non refermé")
    champs: dict = {}
    courant_cle = None
    for numero, ligne in enumerate(texte[4:fin].split("\n"), 2):
        if not ligne.strip() or ligne.lstrip().startswith("#"):
            continue
        creux = len(ligne) - len(ligne.lstrip(" "))
        nu = ligne.strip()
        if creux == 0:
            cle, _, reste = nu.partition(":")
            if not _:
                raise EnteteInvalide(f"ligne {numero} : « {nu[:40]} » n'est pas « clé: valeur »")
            courant_cle = cle.strip()
            champs[courant_cle] = _valeur(reste) if reste.strip() else None
        elif creux == 2 and courant_cle:
            if nu.startswith("- "):
                item = nu[2:]
                cle, _, reste = item.partition(":")
                valeur = (
                    {cle.strip(): _valeur(reste)}
                    if _ and not item.startswith('"')
                    else _valeur(item)
                )
                if not isinstance(champs.get(courant_cle), list):
                    champs[courant_cle] = []
                champs[courant_cle].append(valeur)
            else:
                cle, _, reste = nu.partition(":")
                if not _:
                    raise EnteteInvalide(
                        f"ligne {numero} : « {nu[:40]} » n'est pas « clé: valeur »"
                    )
                if not isinstance(champs.get(courant_cle), dict):
                    champs[courant_cle] = {}
                champs[courant_cle][cle.strip()] = _valeur(reste)
        elif creux == 4 and isinstance(champs.get(courant_cle), list) and champs[courant_cle]:
            cle, _, reste = nu.partition(":")
            if not _ or not isinstance(champs[courant_cle][-1], dict):
                raise EnteteInvalide(f"ligne {numero} : continuation inattendue")
            champs[courant_cle][-1][cle.strip()] = _valeur(reste)
        else:
            raise EnteteInvalide(f"ligne {numero} : indentation de {creux} inattendue")
    return champs


def articles(chemin: pathlib.Path | None = None) -> set[str]:
    """Les codes d'article que la constitution déclare."""
    return set(ARTICLE.findall((chemin or CONSTITUTION).read_text(encoding="utf-8")))


def heuristiques_connues(annexe: pathlib.Path | None = None) -> list[str]:
    """Le vocabulaire clos, dans l ordre de l annexe. Liste vide si l annexe manque.

    La lecture s arrete au marqueur de la matrice engendree : celle-ci porte les memes cles entre
    accents graves, et sans cette borne le vocabulaire doublait - 36 cles pour 18. Le compte annonce
    dans les refus devenait faux, et chaque heuristique apparaissait deux fois dans le rapport.
    """
    annexe = annexe or ANNEXE_HEURISTIQUES
    if not annexe.exists():
        return []
    texte = annexe.read_text(encoding="utf-8")
    if DEBUT_MATRICE in texte:
        texte = texte[: texte.index(DEBUT_MATRICE)]
    return CLE_HEURISTIQUE.findall(texte)


def _articles_de(entete: dict) -> set[str]:
    """L article de rattachement, et ceux qu une fusion a fait entrer."""
    codes = {entete.get("article")} if entete.get("article") else set()
    return codes | set(entete.get("articles_absorbes") or [])


def _entetes(decisions: pathlib.Path | None = None) -> dict[str, dict]:
    """Les en-tetes lisibles du corpus, par nom de fichier."""
    lus = {}
    for f in sorted((decisions or DECISIONS).glob("*.md")):
        if f.name in RESERVES:
            continue
        try:
            lus[f.name] = lit_entete(f.read_text(encoding="utf-8"))
        except EnteteInvalide:
            continue
    return lus


def suspects_ergonomie(decisions: pathlib.Path | None = None) -> list[str]:
    """Les ADR d un article d usage qui ne declarent aucune heuristique.

    Un suspect par ADR. Le grain compte : le cliquet doit descendre d un cran par decision lue,
    pas par paquet de fichiers.
    """
    trouves = []
    for nom, e in sorted(_entetes(decisions).items()):
        if _articles_de(e) & ARTICLES_D_USAGE and not e.get("heuristiques"):
            article = e.get("article") or "?"
            trouves.append(f"{nom}  ({article})")
    return trouves


def heuristiques_sans_emploi(
    decisions: pathlib.Path | None = None, annexe: pathlib.Path | None = None
) -> list[str]:
    """Les heuristiques du vocabulaire qu aucune decision ne sert.

    Ce n est PAS une faute : le jour ou le produit n a rien a decider sur l aide et la
    documentation, le silence est la bonne reponse. Encore faut-il le voir.
    """
    servies = set()
    for e in _entetes(decisions).values():
        for cle in e.get("heuristiques") or []:
            servies.add(cle)
    return [c for c in heuristiques_connues(annexe) if c not in servies]


# Un renvoi vers une ADR VOISINE : aucune barre oblique, donc le meme dossier. Depuis que
# l identite est le slug, il ne commence plus par un chiffre ; exiger un chiffre rendrait
# « 0 renvoi » sur un corpus qui n en manque aucun, soit la forme exacte du succes.
RENVOI = re.compile(r"\]\(([a-z0-9][a-z0-9-]*\.md)(?:#[^)]*)?\)")
# Les verbes qui DÉPASSENT une décision, par opposition à ceux qui la citent ou la prolongent.
DEPASSEMENT = {"renverse", "remplace", "annule"}


def ateliers_de_demande(racine: pathlib.Path) -> list[pathlib.Path]:
    """Les ateliers qu'une demande declenche, lus a la racine donnee.

    La lecture s'arrete a `jobs:`, parce que la cle `on:` d'un atelier vit au-dessus et qu'une etape
    peut parfaitement contenir le mot `push` sans etre un declencheur.
    """
    dossier = racine / ATELIERS
    rendus = []
    for f in sorted(dossier.glob("*.yml")) if dossier.is_dir() else []:
        tete = f.read_text(encoding="utf-8").split("\njobs:")[0]
        if DECLENCHEURS_DE_DEMANDE & set(re.findall(r"^\s{2}([a-z_]+):", tete, re.M)):
            rendus.append(f)
    return rendus


def _nomme_par(cible: str, texte: str, profondeur_min: int = 2) -> bool:
    """Un atelier nomme un gage par son chemin, ou par un repertoire qui le contient.

    `bats --jobs 4 src/test/bats` joue `src/test/bats/cli.bats` sans jamais l'ecrire. Chercher le
    chemin exact rendait donc ce gage introuvable, et c'etait le seul faux positif de la mesure
    d'ouverture. On remonte les ancetres, mais jamais jusqu'a `src` ou `scripts` : un segment unique
    apparait partout, et tout le depot deviendrait « joue ».
    """
    if cible in texte:
        return True
    parts = pathlib.PurePath(cible).parts
    return any("/".join(parts[:k]) in texte for k in range(len(parts) - 1, profondeur_min - 1, -1))


def _contexte_des_gages(racine: pathlib.Path) -> tuple[list[pathlib.Path], str, set[str]]:
    """Ce qu'il faut savoir du depot pour juger un gage : qui tourne, et qui lance quoi.

    Se calcule UNE fois par passe. La boucle des cliquets entre ici parce qu'un garde qu'elle balaie
    est joue sur chaque demande sans qu'aucun atelier ne l'ecrive : dix des soixante-deux gages `.py`
    du corpus sont dans ce cas, et les compter absents aurait fait dix faux refus.
    """
    ateliers = ateliers_de_demande(racine)
    texte = "\n".join(f.read_text(encoding="utf-8") for f in ateliers)
    boucle = {p.relative_to(racine).as_posix() for p in (racine / "scripts/adr").glob("[0-9]*.py")}
    boucle |= {
        p.relative_to(racine).as_posix() for p in (racine / "scripts/adr").glob("loupe-*.py")
    }
    return ateliers, texte, boucle


def refus_du_gage(gage: str, racine: pathlib.Path, contexte) -> str | None:
    """La forme sous laquelle ce gage ne peut pas rougir, ou None s'il juge vraiment.

    Nommer la FORME est le service rendu (ADR 4918) : « ce gage ne juge pas » laisserait l'auteur
    chercher, alors que chaque diagnostic dit quoi faire. QUATRE formes ne peuvent pas rougir, et
    elles ne sont pas quatre facons de dire la meme chose ; l'ordre va du plus informatif au moins
    informatif, car les dernieres attrapent ce que les premieres ont laisse passer. Un cinquieme
    refus, le gage INTROUVABLE, double ici `DocumentationAJourTest` : ce garde resout les chemins de
    toute facon, et laisser passer un gage absent serait plus etrange que le dire deux fois.
    """
    ateliers, texte, boucle = contexte
    cible = gage.split("#")[0]
    if "/" not in cible:
        # `DecisionsRespecteesTest#aucun_cycle` : la suite Java entiere, que `maven.yml` joue par
        # `./mvnw -B test` SANS `-Dtest`. Cent quarante-quatre gages sur deux cent dix-huit.
        return None
    if pathlib.PurePath(cible).suffix in GAGES_INERTES:
        return "gage non executable, un document ne juge rien"
    chemin = racine / cible
    if not chemin.exists():
        return "gage introuvable"
    if pathlib.PurePath(cible).suffix == ".java":
        return None
    if cible.startswith(ATELIERS):
        if chemin in ateliers:
            return None
        return "atelier qu'aucune demande ne declenche"
    if pathlib.PurePath(cible).suffix == ".py":
        source = chemin.read_text(encoding="utf-8")
        if not POINT_D_ENTREE.search(source) and OPTION_AUTO_TEST not in source:
            return "gage qui est le code regi, pas son juge"
    if _nomme_par(cible, texte) or cible in boucle:
        return None
    return "gage qu'aucune demande n'invoque"


def verifie(
    decisions: pathlib.Path | None = None,
    constitution: pathlib.Path | None = None,
    nav: pathlib.Path | None = None,
    plancher: int | None = None,
    annexe: pathlib.Path | None = None,
    racine: pathlib.Path | None = None,
) -> list[str]:
    """Les manquements du paquet, un par ligne. Liste vide : le paquet est conforme.

    `racine` est le depot contre lequel les gages se resolvent. Elle s'injecte pour que le banc
    puisse monter un depot jetable : sans elle, eprouver le refus d'un atelier hors demande exigerait
    d'en poser un vrai dans `.github/workflows`, donc de rendre le depot faux pour le tester.
    """
    decisions = decisions or DECISIONS
    racine = racine or RACINE
    contexte = _contexte_des_gages(racine)
    plancher = PLANCHER_CORPUS if plancher is None else plancher
    connus = articles(constitution)
    vocabulaire = heuristiques_connues(annexe)
    fichiers = sorted(f for f in decisions.glob("*.md") if f.name not in RESERVES)
    noms = {f.name for f in fichiers}
    fautes: list[str] = []

    # Sans annexe, le contrôle des clés serait vide et ne dirait rien. Un dispositif qui peut ne
    # rien vérifier le dit (article A3, ADR 2748), et ici il refuse plutôt que de passer au vert.
    if not vocabulaire:
        fautes.append(
            "annexe : le vocabulaire des heuristiques est introuvable ou vide ; "
            "le contrôle des clés ne vérifierait rien"
        )

    # 8. Cliquet de corpus : une décision ne disparaît pas sans qu'on l'ait décidé.
    if len(fichiers) < plancher:
        fautes.append(f"corpus : {len(fichiers)} ADR pour un plancher de {plancher}")

    entetes = {}
    for f in fichiers:
        # 1. Conformité : l'en-tête s'analyse, et il porte un `type`.
        try:
            e = entetes[f.name] = lit_entete(f.read_text(encoding="utf-8"))
        except EnteteInvalide as erreur:
            fautes.append(f"{f.name} : en-tête illisible ({erreur})")
            continue
        if not e.get("type"):
            fautes.append(f"{f.name} : aucun champ « type »")
        # 2. Rattachement : l'article existe et il est déclaré.
        article = e.get("article")
        if not article:
            fautes.append(f"{f.name} : aucun article de rattachement")
        elif article not in connus:
            fautes.append(f"{f.name} : article « {article} » absent de la constitution")
        # 5. Confiance : le niveau déclaré est gagé.
        niveau = e.get("verification")
        if niveau not in ("certaine", "probable", "humaine"):
            fautes.append(f"{f.name} : niveau de vérification « {niveau} » inconnu")
        if niveau == "certaine" and not e.get("enforced_by"):
            fautes.append(f"{f.name} : « certaine » sans applicateur nommé")
        # Nommer un applicateur n'est pas en avoir un. Le seul controle qui existait, dans
        # `DocumentationAJourTest`, verifie que le fichier nomme EXISTE ; exister n'est pas juger.
        # Trois ADR sur 187 nommaient un gage qu'aucune demande ne pouvait faire rougir, et elles
        # echouaient de trois facons qui ne se ressemblent pas (#5483). Mesure a la pose : zero
        # refus sur les 218 gages des 184 `certaine`, et les trois cas d'avant #5490 rouges.
        if niveau == "certaine":
            for gage in e.get("enforced_by") or []:
                forme = refus_du_gage(gage, racine, contexte)
                if forme:
                    fautes.append(f"{f.name} : {forme} ({gage})")
        if niveau == "probable" and e.get("ratchet") is None:
            fautes.append(f"{f.name} : « probable » sans cliquet déclaré")
        # Une ADR `humaine` ne peut pas nommer d'applicateur : si quelque chose l'appliquait, elle
        # ne serait pas `humaine`. Ce qu'elle nomme est une LOUPE, qui aide à regarder sans rien
        # tenir. La confusion est passee inapercue a la conversion, et la matrice a fini par
        # declarer un article « tenu par » une capture d'ecran.
        if niveau == "humaine" and e.get("enforced_by"):
            fautes.append(f"{f.name} : « humaine » qui nomme un applicateur ; est-ce une loupe ?")
        if not e.get("verified"):
            fautes.append(f"{f.name} : aucune trace de vérification")
        # 9. Les heuristiques déclarées appartiennent au vocabulaire CLOS de l'annexe, et se
        #    déclarent en LISTE, même à une seule entrée. Une faute de frappe qui passerait
        #    créerait une heuristique de plus en silence, et le regroupement, seul service rendu,
        #    cesserait de fonctionner.
        brut = e.get("heuristiques")
        if brut is not None:
            if not isinstance(brut, list):
                fautes.append(
                    f"{f.name} : « heuristiques » doit être une liste, même à une seule entrée"
                )
            else:
                for cle in brut:
                    if cle not in vocabulaire:
                        fautes.append(
                            f"{f.name} : heuristique « {cle} » hors du vocabulaire clos ; "
                            f"l'annexe en tient {len(vocabulaire)}"
                        )

        # 4. Succession : une décision dépassée nomme ce qui la remplace.
        if e.get("status") == "deprecated":
            liens = e.get("relations") or {}
            if not any(liens.get(v) for v in ("remplacee_par", "renversee_par")):
                fautes.append(f"{f.name} : « deprecated » sans successeur nommé")
        # 7. Liens : tout renvoi croisé résout.
        for cible in RENVOI.findall(f.read_text(encoding="utf-8")):
            if cible not in noms and cible not in RESERVES:
                fautes.append(f"{f.name} : renvoi vers « {cible} », qui n'existe pas")

    # 3. Statut et graphe : ce qu'une autre ADR dépasse ne peut pas rester en vigueur.
    for nom, e in entetes.items():
        for verbe, cibles in (e.get("relations") or {}).items():
            if verbe not in DEPASSEMENT:
                continue
            for cible in cibles if isinstance(cibles, list) else [cibles]:
                vise = next((n for n in noms if n.startswith(f"{cible}-")), None)
                if vise and entetes.get(vise, {}).get("status") == "stable":
                    fautes.append(f"{vise} : encore « stable » alors que {nom} la {verbe}")

    # 6. Atteignabilité : aucune ADR orpheline de l'index ni de la navigation.
    index = decisions / "index.md"
    if index.exists():
        cites = set(RENVOI.findall(index.read_text(encoding="utf-8")))
        for orpheline in sorted(noms - cites):
            fautes.append(f"{orpheline} : absente de index.md")
    chemin_nav = nav or NAV
    if chemin_nav.exists():
        vus = set(
            re.findall(r"decisions/([a-z0-9][^\s:]*\.md)", chemin_nav.read_text(encoding="utf-8"))
        )
        for orpheline in sorted(noms - vus):
            fautes.append(f"{orpheline} : absente de la navigation du site")
    return fautes


def _fixture(
    d: str,
    documents: dict[str, str],
    plancher: int,
    annexe: bool = True,
    fichiers: dict[str, str] | None = None,
) -> list[str]:
    """Monte un paquet jetable et rend les manquements que le garde y voit.

    La constitution et la navigation vivent HORS du dossier des décisions, comme dans le dépôt.
    Les poser dedans les ferait lire comme des ADR : la première version de ce banc le faisait, et
    le cas « corpus sain » rougissait pour une raison qui n'avait rien à voir avec son sujet.
    """
    racine = pathlib.Path(d)
    decisions = racine / "decisions"
    decisions.mkdir(exist_ok=True)
    for nom, contenu in documents.items():
        (decisions / nom).write_text(contenu, encoding="utf-8")
    # Les gages se resolvent contre CETTE racine. Un cas qui eprouve le refus d'un atelier hors
    # demande ecrit donc un vrai `.github/workflows/*.yml` ici, et le garde le lit comme il lirait
    # celui du depot. C'est ce qui evite la faute de #5491, ou un temoin batissait sa fixture depuis
    # la constante du garde et ne pouvait donc pas voir que cette constante etait fausse.
    for chemin, contenu in (fichiers or {}).items():
        cible = racine / chemin
        cible.parent.mkdir(parents=True, exist_ok=True)
        cible.write_text(contenu, encoding="utf-8")
    index = "".join(f"- [x]({n})\n" for n in documents)
    (decisions / "index.md").write_text(index, encoding="utf-8")
    const = racine / "CONSTITUTION.md"
    const.write_text("### A1 : Un témoin\n", encoding="utf-8")
    nav = racine / "nav.yml"
    nav.write_text("".join(f"  - x: decisions/{n}\n" for n in documents), encoding="utf-8")
    # L annexe des heuristiques, sous la MEME forme que la vraie : le controle des cles lit sa
    # premiere colonne. `annexe=False` monte le cas ou elle manque, qui doit refuser.
    fichier_annexe = racine / "heuristiques.md"
    if annexe:
        fichier_annexe.write_text(
            "| Clé | Nom |\n|---|---|\n| `nielsen-1` | Un témoin |\n| `gestalt-cloture` | Un autre |\n",
            encoding="utf-8",
        )
    return verifie(decisions, const, nav, plancher=plancher, annexe=fichier_annexe, racine=racine)


MODELE = (
    '---\ntype: adr\ntitle: "Témoin"\nstatus: stable\narticle: A1\n'
    'verification: certaine\nenforced_by:\n  - "TemoinTest#cas"\n'
    "verified:\n  - by: machine:ci\n    at: 2026-08-20\n---\n\n# Témoin\n\n## Contexte\n\nRien.\n"
)


def auto_test() -> int:
    """Casse à la main ce que chaque refus prétend attraper, et exige qu'il rougisse.

    Sans ce banc, chaque refus serait une affirmation. Un garde qui n'a jamais été vu rouge sur
    sa propre mutation ne dit pas ce qu'il vérifie : il dit seulement qu'il a tourné.

    Le dernier cas est le contrôle de non-vacuité : un paquet SAIN doit rester vert. Un garde qui
    rougit de toute façon rougirait aussi sur les mutations, et son rouge ne prouverait rien.
    """
    echecs = []

    def cas(
        titre: str,
        documents: dict[str, str],
        attendu: str | None,
        plancher: int = 1,
        annexe: bool = True,
        fichiers: dict[str, str] | None = None,
    ) -> None:
        with tempfile.TemporaryDirectory() as d:
            fautes = _fixture(d, documents, plancher, annexe=annexe, fichiers=fichiers)
        vu = any(attendu in f for f in fautes) if attendu else not fautes
        etat = (
            ("rouge" if vu else "VERT, ce qui est le défaut")
            if attendu
            else ("vert" if vu else f"ROUGE sans motif : {fautes[:2]}")
        )
        print(f"  {'✔' if vu else '✘'} {titre:32} -> {etat}")
        if not vu:
            echecs.append(titre)

    cas(
        "en-tête absent",
        {"0001-t.md": MODELE.replace("---\ntype: adr", "type: adr", 1)},
        "en-tête illisible",
    )
    cas("aucun type", {"0001-t.md": MODELE.replace("type: adr\n", "", 1)}, "aucun champ « type »")
    cas("article absent", {"0001-t.md": MODELE.replace("article: A1\n", "", 1)}, "aucun article")
    cas(
        "article inconnu",
        {"0001-t.md": MODELE.replace("article: A1", "article: A99")},
        "absent de la constitution",
    )
    cas(
        "niveau inconnu",
        {"0001-t.md": MODELE.replace("verification: certaine", "verification: peut-etre")},
        "inconnu",
    )
    cas(
        "certaine sans applicateur",
        {"0001-t.md": MODELE.replace('enforced_by:\n  - "TemoinTest#cas"\n', "")},
        "sans applicateur",
    )
    cas(
        "probable sans cliquet",
        {"0001-t.md": MODELE.replace("verification: certaine", "verification: probable")},
        "sans cliquet",
    )
    cas(
        "humaine qui nomme un applicateur",
        {"0001-t.md": MODELE.replace("verification: certaine", "verification: humaine")},
        "est-ce une loupe",
    )
    cas(
        "aucune vérification",
        {"0001-t.md": MODELE.replace("verified:\n  - by: machine:ci\n    at: 2026-08-20\n", "")},
        "aucune trace",
    )
    cas(
        "renvoi cassé",
        {"0001-t.md": MODELE.replace("Rien.", "Voir [ailleurs](9999-absente.md).")},
        "n'existe pas",
    )
    cas(
        "renversée mais en vigueur",
        {
            "0001-t.md": MODELE,
            "0002-s.md": MODELE.replace("verified:", 'relations:\n  renverse: ["0001"]\nverified:'),
        },
        "encore « stable »",
        plancher=2,
    )
    cas("corpus sous son plancher", {"0001-t.md": MODELE}, "plancher", plancher=2)
    cas(
        "heuristique hors du vocabulaire clos",
        {"0001-t.md": MODELE.replace("article: A1", 'heuristiques: ["nielsen-42"]\narticle: A1')},
        "hors du vocabulaire clos",
    )
    cas(
        "heuristiques déclarées en scalaire",
        {"0001-t.md": MODELE.replace("article: A1", 'heuristiques: "nielsen-1"\narticle: A1')},
        "doit être une liste",
    )
    # Le vocabulaire vit dans l annexe. Sans elle, le controle des cles laisserait tout passer :
    # un garde qui peut ne rien verifier le dit, et ici il refuse.
    cas(
        "annexe des heuristiques absente",
        {"0001-t.md": MODELE},
        "le contrôle des clés ne vérifierait rien",
        annexe=False,
    )
    cas(
        "une clé du vocabulaire est acceptée",
        {
            "0001-t.md": MODELE.replace(
                "article: A1", 'heuristiques: ["gestalt-cloture"]\narticle: A1'
            )
        },
        None,
    )

    # Les quatre formes sous lesquelles un gage NOMME ne juge pas, et les deux contrastes sans
    # lesquels le refus serait juste une facon de tout refuser. Aucun de ces cas ne partage de
    # constante avec le garde : chacun ecrit un vrai atelier ou un vrai script dans le depot
    # jetable, et le garde les lit comme il lit ceux du depot. Ils portent toute la charge de
    # preuve, parce que le lot 1 (#5490) a supprime les trois seuls positifs vivants du corpus.
    def avec_gage(chemin: str) -> str:
        return MODELE.replace('"TemoinTest#cas"', f'"{chemin}"')

    NOCTURNE = 'name: n\non:\n  schedule:\n    - cron: "0 3 * * *"\njobs:\n  x:\n    runs-on: u\n'
    SUR_DEMANDE = "name: d\non:\n  pull_request:\njobs:\n  x:\n    runs-on: u\n"

    cas(
        "gage de prose",
        {"0001-t.md": avec_gage("dev-docs/note.md")},
        "non executable",
        fichiers={"dev-docs/note.md": "Une note, et rien qui s execute.\n"},
    )
    cas(
        "gage : atelier hors demande",
        {"0001-t.md": avec_gage(".github/workflows/nocturne.yml")},
        "aucune demande ne declenche",
        fichiers={".github/workflows/nocturne.yml": NOCTURNE},
    )
    cas(
        "gage : atelier sur demande accepte",
        {"0001-t.md": avec_gage(".github/workflows/porte.yml")},
        None,
        fichiers={".github/workflows/porte.yml": SUR_DEMANDE},
    )
    cas(
        "gage : bibliotheque sans entree",
        {"0001-t.md": avec_gage("scripts/mesure.py")},
        "code regi",
        fichiers={
            "scripts/mesure.py": "def mesure():\n    return 1\n",
            ".github/workflows/porte.yml": SUR_DEMANDE.replace(
                "runs-on: u", "runs-on: u\n    steps:\n      - run: python scripts/mesure.py"
            ),
        },
    )
    cas(
        "gage : script qu aucune demande n invoque",
        {"0001-t.md": avec_gage("scripts/orphelin.py")},
        "n'invoque",
        fichiers={
            "scripts/orphelin.py": 'def f():\n    return 1\n\n\nif __name__ == "__main__":\n    f()\n',
            ".github/workflows/porte.yml": SUR_DEMANDE,
        },
    )
    cas(
        "gage : script joue par une demande accepte",
        {"0001-t.md": avec_gage("scripts/juge.py")},
        None,
        fichiers={
            "scripts/juge.py": 'def f():\n    return 1\n\n\nif __name__ == "__main__":\n    f()\n',
            ".github/workflows/porte.yml": SUR_DEMANDE.replace(
                "runs-on: u", "runs-on: u\n    steps:\n      - run: python scripts/juge.py"
            ),
        },
    )
    cas("corpus sain", {"0001-t.md": MODELE, "0002-s.md": MODELE}, None, plancher=2)

    if echecs:
        print(f"\n{len(echecs)} cas en échec : {', '.join(echecs)}", file=sys.stderr)
        return 1

    # Les deux controles qui ne passent pas par `verifie()` : l un rend des suspects sous cliquet,
    # l autre un simple constat. Ils s eprouvent donc sur un corpus jetable, faute de quoi ils ne
    # seraient tenus par rien.
    def sonde(titre: str, obtenu, attendu) -> None:
        ok = obtenu == attendu
        print(
            f"  {'✔' if ok else '✘'} {titre:32} -> {'vert' if ok else f'{obtenu} au lieu de {attendu}'}"
        )
        if not ok:
            echecs.append(titre)

    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d) / "decisions"
        r.mkdir(parents=True)
        (r / "sans-heuristique.md").write_text(
            MODELE.replace("article: A1", "article: A12"), encoding="utf-8"
        )
        (r / "hors-usage.md").write_text(MODELE, encoding="utf-8")
        sonde(
            "suspects : l'ADR d'usage nue est vue",
            [s.split("  ")[0] for s in suspects_ergonomie(r)],
            ["sans-heuristique.md"],
        )
        sonde("suspects : l'ADR hors usage est épargnée", len(suspects_ergonomie(r)), 1)

    print(
        "\nAuto-test concluant : chaque refus rougit sur sa propre violation, et un paquet sain reste vert."
    )
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Garde du paquet OKF de dev-docs/decisions")
    p.add_argument("--auto-test", action="store_true", help="éprouve les refus sur des fixtures")
    args = p.parse_args()
    if args.auto_test:
        return auto_test()
    fautes = verifie()
    if fautes:
        print(f"{len(fautes)} manquement(s) au format OKF :")
        for f in fautes:
            print(f"  {f}")
        return 1
    total = len([f for f in DECISIONS.glob("*.md") if f.name not in RESERVES])
    print(f"{total} ADR conformes : en-tête, rattachement, confiance, atteignabilité, liens.")

    # Contrôle 2 : ce que le vocabulaire couvre, et ce que rien ne sert. Sans rougir : c'est un
    # manque à connaître, pas une faute à corriger.
    connues = heuristiques_connues()
    orphelines = heuristiques_sans_emploi()
    print(
        f"\nErgonomie : {len(connues) - len(orphelines)} heuristique(s) servie(s) sur "
        f"{len(connues)}."
    )
    if orphelines:
        print("  Aucune décision ne sert : " + ", ".join(orphelines))

    # Contrôle 3 : les ADR d'un article d'usage qui ne déclarent rien, sous cliquet.
    print()
    return rapporte(
        ADR_ERGONOMIE,
        "ADR d'un article d'usage sans heuristique déclarée",
        suspects_ergonomie(),
        apercu=12,
        # L'unite n'est pas le fichier balaye mais l'ADR LISIBLE : `_entetes()` est deja le
        # corpus que ce controle lit, et l'extraire une seconde fois le compterait deux fois.
        lus=len(_entetes()),
    )


CONTRAT = {
    "geste": "ADR d un article d usage sans heuristique declaree",
    "population": "les ADR de dev-docs/decisions, hors reservees",
    "dispositif": "cliquet",
    "seuil": "71, polarite=descend",
    "temoin": "scripts/adr/verifie_okf.py --auto-test",
    "decision": "ADR 4342",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(main())
