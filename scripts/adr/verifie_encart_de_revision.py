#!/usr/bin/env python3
"""Une ADR depassee le dit sous son titre, et l encart n annonce que ce qui est declare.

Le corpus amende par une NOUVELLE ADR : une ADR acceptee ne se reecrit pas. Cette regle est bonne,
mais elle a un cout que rien ne payait - le lecteur qui ouvre l ADR amendee ne voit rien. La relation
vivait dans l en-tete, verifiee par `verifie_okf.py`, et invisible sur la page. Vingt-trois ADR
etaient dans ce cas, et une seule portait une marque.

Elles portent desormais, juste sous leur titre, un encart « Ce qui fait foi aujourd hui » : une
entree par relation subie, avec sa date, l ADR qui l amende, et ce que l amendement change. Le lecteur
n a plus qu un document a lire.

Ce garde tient les deux sens de la promesse.

- Toute relation SUBIE declaree en en-tete - `amendee_par`, `completee_par`, `remplacee_par` - a son
  entree dans l encart. Sans quoi la declaration retombe dans l invisible d ou elle vient.
- L encart n annonce QUE des relations declarees. C est le second devoir, et il n est pas decoratif :
  `cliquet-longueur-des-adr.py` retire l encart de son decompte, au motif qu il ne raconte pas la
  decision. Sans ce controle, l encart deviendrait l endroit ou l on range la prose qui depasse.

L encart se place sous le TITRE, et le garde l exige : un amendement qu on decouvre a la ligne 72 ne
fait pas foi. Le corpus en porte un exemple, place au milieu du corps, que personne ne rencontrait.
"""

import pathlib
import re
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import DECISIONS

TITRE_ENCART = '!!! warning "Ce qui fait foi aujourd\'hui"'

# Les relations que l ADR SUBIT, par opposition a celles qu elle exerce.
SUBIES = ("amendee_par", "completee_par", "remplacee_par")

RESERVES = {"index.md", "log.md"}

# Combien de lignes peuvent separer le titre de l encart. Deux suffisent : la ligne vide, et une
# marge. Au-dela, l encart n est plus « sous le titre », il est dans le corps.
MARGE = 3


def relations(texte: str) -> dict[str, list[str]]:
    """Les relations declarees en en-tete, par verbe."""
    m = re.search(r"^relations:\s*$", texte, re.M)
    if not m:
        return {}
    bloc = texte[m.end() :].split("\n---", 1)[0]
    return {
        verbe: [c.strip().strip('"') for c in cibles.split(",") if c.strip()]
        for verbe, cibles in re.findall(r"^\s{2}([a-zé_]+):\s*\[([^\]]*)\]", bloc, re.M)
    }


def encart(texte: str) -> tuple[list[str], int] | tuple[None, None]:
    """Les cibles citees par l encart, et sa distance au titre. `(None, None)` s il n y en a pas."""
    debut = texte.find(TITRE_ENCART)
    if debut < 0:
        return None, None
    titre = re.search(r"^# .+$", texte, re.M)
    distance = texte[:debut].count("\n") - (texte[: titre.start()].count("\n") if titre else 0)
    lignes = texte[debut:].split("\n")
    fin = 1
    for i, ligne in enumerate(lignes[1:], 1):
        if ligne.strip() and not ligne.startswith("    "):
            break
        fin = i + 1
    corps = "\n".join(lignes[:fin])
    return re.findall(r"\]\(([a-z0-9][a-z0-9-]*)\.md\)", corps), distance


def fautes(racine: pathlib.Path | None = None) -> list[str]:
    racine = racine or DECISIONS
    trouvees = []
    for chemin in sorted(racine.glob("*.md")):
        if chemin.name in RESERVES:
            continue
        texte = chemin.read_text(encoding="utf-8")
        attendues = [c for v, cs in relations(texte).items() if v in SUBIES for c in cs]
        citees, distance = encart(texte)
        if attendues and citees is None:
            trouvees.append(
                f"{chemin.name} : {len(attendues)} relation(s) subie(s) declaree(s), aucun encart "
                f"« Ce qui fait foi aujourd'hui » sous le titre"
            )
            continue
        if citees is None:
            continue
        if not attendues:
            trouvees.append(
                f"{chemin.name} : un encart de revision sans aucune relation subie declaree"
            )
            continue
        if distance is not None and distance > MARGE:
            trouvees.append(
                f"{chemin.name} : l encart est a {distance} lignes du titre, il doit le suivre"
            )
        for cible in attendues:
            if cible not in citees:
                trouvees.append(
                    f"{chemin.name} : l encart n annonce pas « {cible} », pourtant declaree"
                )
        for cible in citees:
            if cible not in attendues:
                trouvees.append(
                    f"{chemin.name} : l encart annonce « {cible} », qui n est pas declaree"
                )
    return trouvees


def _fixture(d: str, documents: dict[str, str]) -> pathlib.Path:
    racine = pathlib.Path(d)
    for nom, contenu in documents.items():
        (racine / nom).write_text(contenu, encoding="utf-8")
    return racine


def _saine(
    relation: str = 'relations:\n  amendee_par: ["voisine"]\n', encart_pose: bool = True
) -> str:
    tete = f"---\ntype: adr\n{relation}---\n\n# Un titre\n"
    bloc = (
        (
            f"\n{TITRE_ENCART}\n    **Amendee le 2026-01-01** par [ADR](voisine.md) :\n"
            "    ce qui change.\n"
        )
        if encart_pose
        else ""
    )
    return tete + bloc + "\nDu corps ordinaire.\n"


def _auto_test() -> int:
    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = _fixture(d, {"sujet.md": _saine(), "voisine.md": "---\ntype: adr\n---\n\n# Voisine\n"})
        cas.append(("un corpus sain est vert", fautes(r) == []))

        (r / "sujet.md").write_text(_saine(encart_pose=False), encoding="utf-8")
        cas.append(
            ("une relation subie sans encart rougit", any("aucun encart" in f for f in fautes(r)))
        )

        (r / "sujet.md").write_text(_saine(relation=""), encoding="utf-8")
        cas.append(
            (
                "un encart sans relation declaree rougit",
                any("sans aucune relation" in f for f in fautes(r)),
            )
        )

        (r / "sujet.md").write_text(
            _saine().replace("[ADR](voisine.md)", "[ADR](autre.md)"), encoding="utf-8"
        )
        (r / "autre.md").write_text("---\ntype: adr\n---\n\n# Autre\n", encoding="utf-8")
        f = fautes(r)
        cas.append(
            ("une cible declaree absente de l encart rougit", any("n annonce pas" in x for x in f))
        )
        cas.append(
            (
                "une cible de l encart non declaree rougit",
                any("qui n est pas declaree" in x for x in f),
            )
        )

        # La faille que l exclusion du cliquet de longueur ouvrirait : de la prose rangee dans l
        # encart sous couvert d un lien. Elle est fermee par le controle ci-dessus, et ce cas le dit.
        (r / "sujet.md").write_text(
            _saine().replace(
                "    ce qui change.\n",
                "    ce qui change.\n    **Amendee** par [ADR](autre.md) : de la prose de plus.\n",
            ),
            encoding="utf-8",
        )
        cas.append(
            (
                "de la prose glissee dans l encart rougit",
                any("qui n est pas declaree" in x for x in fautes(r)),
            )
        )

        (r / "sujet.md").write_text(
            _saine().replace(
                "# Un titre\n", "# Un titre\n\nUn paragraphe.\n\nUn autre.\n\nUn troisieme.\n"
            ),
            encoding="utf-8",
        )
        cas.append(
            ("un encart loin du titre rougit", any("lignes du titre" in x for x in fautes(r)))
        )

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en echec : le garde ne detecte plus ce qu il annonce.",
            file=sys.stderr,
        )
        return 1
    print("\nLe garde de l encart de revision detecte ses violations temoins.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    trouvees = fautes()
    for f in trouvees:
        print(f"  {f}", file=sys.stderr)
    if trouvees:
        print(f"\n{len(trouvees)} encart(s) de revision en defaut.", file=sys.stderr)
        sys.exit(1)
    print(
        "Encarts de revision : chaque relation subie est annoncee sous son titre, et rien de plus."
    )
    sys.exit(0)
