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

Les huit refus, et ce qui prouve qu'ils manquaient :

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
"""

import argparse
import pathlib
import re
import sys
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


def verifie(decisions: pathlib.Path = None, constitution: pathlib.Path = None,
            nav: pathlib.Path = None, plancher: int = None) -> list[str]:
    """Les manquements du paquet, un par ligne. Liste vide : le paquet est conforme."""
    decisions = decisions or DECISIONS
    plancher = PLANCHER_CORPUS if plancher is None else plancher
    connus = articles(constitution)
    fichiers = sorted(f for f in decisions.glob("*.md") if f.name not in RESERVES)
    noms = {f.name for f in fichiers}
    fautes: list[str] = []

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


def _fixture(d: str, documents: dict[str, str], plancher: int) -> list[str]:
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
    return verifie(decisions, const, nav, plancher=plancher)


MODELE = (
    "---\ntype: adr\ntitle: \"Témoin\"\nstatus: stable\narticle: A1\n"
    "verification: certaine\nenforced_by:\n  - \"TemoinTest#cas\"\n"
    "verified:\n  - by: machine:ci\n    at: 2026-08-20\n---\n\n# Témoin\n\n## Contexte\n\nRien.\n"
)


def auto_test() -> int:
    """Casse à la main ce que chaque refus prétend attraper, et exige qu'il rougisse.

    Sans ce banc, les huit refus seraient huit affirmations. Un garde qui n'a jamais été vu rouge sur
    sa propre mutation ne dit pas ce qu'il vérifie : il dit seulement qu'il a tourné.

    Le dernier cas est le contrôle de non-vacuité : un paquet SAIN doit rester vert. Un garde qui
    rougit de toute façon rougirait aussi sur les mutations, et son rouge ne prouverait rien.
    """
    echecs = []

    def cas(titre: str, documents: dict[str, str], attendu: str | None, plancher: int = 1) -> None:
        with tempfile.TemporaryDirectory() as d:
            fautes = _fixture(d, documents, plancher)
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
    cas("corpus sain", {"0001-t.md": MODELE, "0002-s.md": MODELE}, None, plancher=2)

    if echecs:
        print(f"\n{len(echecs)} cas en échec : {', '.join(echecs)}", file=sys.stderr)
        return 1
    print("\nAuto-test concluant : les huit refus rougissent, et un paquet sain reste vert.")
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
