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

Les neuf refus, et ce qui prouve qu'ils manquaient :

| Refus | Ce qu'il attrape | Pourquoi il manquait |
|---|---|---|
| en-tête | un fichier sans en-tête analysable, ou sans `type` | rien ne contrôlait la forme |
| rattachement | une ADR sans article, ou visant un article absent | le lien vivait dans la prose |
| statut et graphe | une ADR `stable` qu'une autre renverse | zéro `deprecated` pour 21 amendements |
| succession | une `deprecated` qui ne nomme pas ce qui la remplace | les annulations n'avaient pas de cible |
| confiance | `certaine` sans applicateur, `probable` sans cliquet | le niveau se déclarait sans gage |
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

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
DECISIONS = RACINE / "dev-docs" / "decisions"
CONSTITUTION = RACINE / "CONSTITUTION.md"
NAV = RACINE / "mkdocs-dev.yml"
RESERVES = {"index.md", "log.md"}

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
                valeur = {cle.strip(): _valeur(reste)} if _ and not item.startswith('"') else _valeur(item)
                if not isinstance(champs.get(courant_cle), list):
                    champs[courant_cle] = []
                champs[courant_cle].append(valeur)
            else:
                cle, _, reste = nu.partition(":")
                if not _:
                    raise EnteteInvalide(f"ligne {numero} : « {nu[:40] } » n'est pas « clé: valeur »")
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


def articles(chemin: pathlib.Path = None) -> set[str]:
    """Les codes d'article que la constitution déclare."""
    return set(ARTICLE.findall((chemin or CONSTITUTION).read_text(encoding="utf-8")))


def heuristiques_connues(annexe: pathlib.Path = None) -> list[str]:
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


def _entetes(decisions: pathlib.Path = None) -> dict[str, dict]:
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


def suspects_ergonomie(decisions: pathlib.Path = None) -> list[str]:
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


def heuristiques_sans_emploi(decisions: pathlib.Path = None,
                             annexe: pathlib.Path = None) -> list[str]:
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


class EnteteInvalide(ValueError):
    """L'en-tête sort de la forme que le dépôt écrit."""


def verifie(decisions: pathlib.Path = None, constitution: pathlib.Path = None,
            nav: pathlib.Path = None, plancher: int = None,
            annexe: pathlib.Path = None) -> list[str]:
    """Les manquements du paquet, un par ligne. Liste vide : le paquet est conforme."""
    decisions = decisions or DECISIONS
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
            "le contrôle des clés ne vérifierait rien")

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
                    f"{f.name} : « heuristiques » doit être une liste, même à une seule entrée")
            else:
                for cle in brut:
                    if cle not in vocabulaire:
                        fautes.append(
                            f"{f.name} : heuristique « {cle} » hors du vocabulaire clos ; "
                            f"l'annexe en tient {len(vocabulaire)}")

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
        vus = set(re.findall(r"decisions/([a-z0-9][^\s:]*\.md)", chemin_nav.read_text(encoding="utf-8")))
        for orpheline in sorted(noms - vus):
            fautes.append(f"{orpheline} : absente de la navigation du site")
    return fautes


def _fixture(d: str, documents: dict[str, str], plancher: int, annexe: bool = True) -> list[str]:
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
            encoding="utf-8")
    return verifie(decisions, const, nav, plancher=plancher, annexe=fichier_annexe)


MODELE = (
    "---\ntype: adr\ntitle: \"Témoin\"\nstatus: stable\narticle: A1\n"
    "verification: certaine\nenforced_by:\n  - \"TemoinTest#cas\"\n"
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

    def cas(titre: str, documents: dict[str, str], attendu: str | None, plancher: int = 1,
            annexe: bool = True) -> None:
        with tempfile.TemporaryDirectory() as d:
            fautes = _fixture(d, documents, plancher, annexe=annexe)
        vu = any(attendu in f for f in fautes) if attendu else not fautes
        etat = ("rouge" if vu else "VERT, ce qui est le défaut") if attendu else (
            "vert" if vu else f"ROUGE sans motif : {fautes[:2]}")
        print(f"  {'✔' if vu else '✘'} {titre:32} -> {etat}")
        if not vu:
            echecs.append(titre)

    cas("en-tête absent", {"0001-t.md": MODELE.replace("---\ntype: adr", "type: adr", 1)},
        "en-tête illisible")
    cas("aucun type", {"0001-t.md": MODELE.replace("type: adr\n", "", 1)}, "aucun champ « type »")
    cas("article absent", {"0001-t.md": MODELE.replace("article: A1\n", "", 1)}, "aucun article")
    cas("article inconnu", {"0001-t.md": MODELE.replace("article: A1", "article: A99")},
        "absent de la constitution")
    cas("niveau inconnu", {"0001-t.md": MODELE.replace("verification: certaine", "verification: peut-etre")},
        "inconnu")
    cas("certaine sans applicateur",
        {"0001-t.md": MODELE.replace("enforced_by:\n  - \"TemoinTest#cas\"\n", "")},
        "sans applicateur")
    cas("probable sans cliquet",
        {"0001-t.md": MODELE.replace("verification: certaine", "verification: probable")},
        "sans cliquet")
    cas("humaine qui nomme un applicateur",
        {"0001-t.md": MODELE.replace("verification: certaine", "verification: humaine")},
        "est-ce une loupe")
    cas("aucune vérification",
        {"0001-t.md": MODELE.replace("verified:\n  - by: machine:ci\n    at: 2026-08-20\n", "")},
        "aucune trace")
    cas("renvoi cassé",
        {"0001-t.md": MODELE.replace("Rien.", "Voir [ailleurs](9999-absente.md).")}, "n'existe pas")
    cas("renversée mais en vigueur",
        {"0001-t.md": MODELE,
         "0002-s.md": MODELE.replace("verified:", "relations:\n  renverse: [\"0001\"]\nverified:")},
        "encore « stable »", plancher=2)
    cas("corpus sous son plancher", {"0001-t.md": MODELE}, "plancher", plancher=2)
    cas("heuristique hors du vocabulaire clos",
        {"0001-t.md": MODELE.replace("article: A1", 'heuristiques: ["nielsen-42"]\narticle: A1')},
        "hors du vocabulaire clos")
    cas("heuristiques déclarées en scalaire",
        {"0001-t.md": MODELE.replace("article: A1", 'heuristiques: "nielsen-1"\narticle: A1')},
        "doit être une liste")
    # Le vocabulaire vit dans l annexe. Sans elle, le controle des cles laisserait tout passer :
    # un garde qui peut ne rien verifier le dit, et ici il refuse.
    cas("annexe des heuristiques absente",
        {"0001-t.md": MODELE}, "le contrôle des clés ne vérifierait rien", annexe=False)
    cas("une clé du vocabulaire est acceptée",
        {"0001-t.md": MODELE.replace("article: A1", 'heuristiques: ["gestalt-cloture"]\narticle: A1')},
        None)
    cas("corpus sain", {"0001-t.md": MODELE, "0002-s.md": MODELE}, None, plancher=2)

    if echecs:
        print(f"\n{len(echecs)} cas en échec : {', '.join(echecs)}", file=sys.stderr)
        return 1
    # Les deux controles qui ne passent pas par `verifie()` : l un rend des suspects sous cliquet,
    # l autre un simple constat. Ils s eprouvent donc sur un corpus jetable, faute de quoi ils ne
    # seraient tenus par rien.
    def sonde(titre: str, obtenu, attendu) -> None:
        ok = obtenu == attendu
        print(f"  {'✔' if ok else '✘'} {titre:32} -> {'vert' if ok else f'{obtenu} au lieu de {attendu}'}")
        if not ok:
            echecs.append(titre)

    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d) / "decisions"
        r.mkdir(parents=True)
        (r / "sans-heuristique.md").write_text(MODELE.replace("article: A1", "article: A12"),
                                               encoding="utf-8")
        (r / "hors-usage.md").write_text(MODELE, encoding="utf-8")
        sonde("suspects : l'ADR d'usage nue est vue",
              [s.split("  ")[0] for s in suspects_ergonomie(r)], ["sans-heuristique.md"])
        sonde("suspects : l'ADR hors usage est épargnée",
              len(suspects_ergonomie(r)), 1)

    print("\nAuto-test concluant : chaque refus rougit sur sa propre violation, et un paquet sain reste vert.")
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
    print(f"\nErgonomie : {len(connues) - len(orphelines)} heuristique(s) servie(s) sur "
          f"{len(connues)}.")
    if orphelines:
        print("  Aucune décision ne sert : " + ", ".join(orphelines))

    # Contrôle 3 : les ADR d'un article d'usage qui ne déclarent rien, sous cliquet.
    print()
    return rapporte(ADR_ERGONOMIE, "ADR d'un article d'usage sans heuristique déclarée",
                    suspects_ergonomie(), apercu=12)


if __name__ == "__main__":
    sys.exit(main())
