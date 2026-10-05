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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import DECISIONS, cas_d_auto_test, sort_si_contrat_demande
from _commun.relations import est_subie, verbes

TITRE_ENCART = '!!! warning "Ce qui fait foi aujourd\'hui"'

# `SUBIES`, `est_subie` et la lecture du bloc viennent de `_commun/relations.py` depuis #5579 :
# elles etaient ecrites ici ET dans `verifie_okf.py`, et les deux ne lisaient pas la meme chose.

RESERVES = {"index.md", "log.md"}

# Combien de lignes peuvent separer le titre de l encart. Deux suffisent : la ligne vide, et une
# marge. Au-dela, l encart n est plus « sous le titre », il est dans le corps.
MARGE = 3


def relations(texte: str) -> dict[str, list[str]]:
    """Les relations declarees en en-tete, par verbe. Le nom reste, la regle est commune (#5579).

    Son motif d avant, `[a-zé_]+`, ratait « complète » a l accent grave et « fait évoluer » a
    l espace : neuf ADR sur 101 etaient lues autrement que par `lit_entete`.
    """
    return verbes(texte)


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
        # `est_subie` et non `v in SUBIES` : la comparaison litterale ratait « amendée_par »,
        # que le motif VOYAIT pourtant. C etait la, et non dans le motif, que le defaut de
        # #5579 vivait, et le garde sortait vert sur une ADR amendee sans encart.
        attendues = [c for v, cs in relations(texte).items() if est_subie(v) for c in cs]
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
    # Les cas passent par le fonds (#5461) : leur expression est DIFFEREE, donc celle qui lève
    # nomme son cas au lieu d'arrêter le témoin. La fabrique l'appelle AUSSITÔT, ce qui préserve
    # l'ordre d'évaluation, plusieurs de ces auto-tests réécrivant leur fixture entre deux cas.
    verifie, echecs = cas_d_auto_test()
    joues = [0]

    def cas_de(libelle, juger):
        joues[0] += 1
        verifie(libelle, juger, True)

    with tempfile.TemporaryDirectory() as d:
        r = _fixture(d, {"sujet.md": _saine(), "voisine.md": "---\ntype: adr\n---\n\n# Voisine\n"})
        cas_de(
            "un corpus sain est vert",
            lambda: fautes(r) == [],
        )

        (r / "sujet.md").write_text(_saine(encart_pose=False), encoding="utf-8")
        cas_de(
            "une relation subie sans encart rougit",
            lambda: any("aucun encart" in f for f in fautes(r)),
        )

        (r / "sujet.md").write_text(_saine(relation=""), encoding="utf-8")
        cas_de(
            "un encart sans relation declaree rougit",
            lambda: any("sans aucune relation" in f for f in fautes(r)),
        )

        # #5579, et c est LE cas du lot : la meme ADR, la meme absence d encart, mais la relation
        # ecrite avec son accent. Avant le remede, `v in SUBIES` ne la reconnaissait pas et ce garde
        # sortait VERT sur une ADR amendee qui n annoncait rien sous son titre.
        (r / "sujet.md").write_text(
            _saine(relation='relations:\n  amendée_par: ["voisine"]\n', encart_pose=False),
            encoding="utf-8",
        )
        cas_de(
            "une relation subie ACCENTUEE sans encart rougit aussi",
            lambda: any("aucun encart" in f for f in fautes(r)),
        )
        # Le contraste dans l autre sens : une relation EXERCEE ne doit rien exiger, sans quoi le
        # remede aurait simplement rendu le garde bavard.
        (r / "sujet.md").write_text(
            _saine(relation='relations:\n  amende: ["voisine"]\n', encart_pose=False),
            encoding="utf-8",
        )
        cas_de(
            "une relation EXERCEE sans encart reste verte",
            lambda: fautes(r) == [],
        )
        # Et la regle commune joue ses propres cas ICI : sans un joueur, elle serait un gage inerte,
        # ce que `verifie_gages_joues.py` refuse depuis #5594.
        from _commun import relations as regle

        cas_de(
            "la regle commune des relations tient ses cas",
            lambda: [libelle for libelle, tenu in regle.verifie_grammaire() if not tenu] == [],
        )

        (r / "sujet.md").write_text(
            _saine().replace("[ADR](voisine.md)", "[ADR](autre.md)"), encoding="utf-8"
        )
        (r / "autre.md").write_text("---\ntype: adr\n---\n\n# Autre\n", encoding="utf-8")
        f = fautes(r)
        cas_de(
            "une cible declaree absente de l encart rougit",
            lambda: any("n annonce pas" in x for x in f),
        )
        cas_de(
            "une cible de l encart non declaree rougit",
            lambda: any("qui n est pas declaree" in x for x in f),
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
        cas_de(
            "de la prose glissee dans l encart rougit",
            lambda: any("qui n est pas declaree" in x for x in fautes(r)),
        )

        (r / "sujet.md").write_text(
            _saine().replace(
                "# Un titre\n", "# Un titre\n\nUn paragraphe.\n\nUn autre.\n\nUn troisieme.\n"
            ),
            encoding="utf-8",
        )
        cas_de(
            "un encart loin du titre rougit",
            lambda: any("lignes du titre" in x for x in fautes(r)),
        )

    if echecs():
        print(
            f"\n{joues[0]} cas en echec : le garde ne detecte plus ce qu il annonce.",
            file=sys.stderr,
        )
        return 1
    # ⟨il comptait deja, et ne le disait qu a l ECHEC⟩ Son compte servait au message rouge et
    # restait tu au vert, donc un cas qui disparaissait ne se voyait pas (#5744).
    print(
        f"\n{joues[0]} cas joue(s) : le garde de l encart de revision detecte ses"
        " violations temoins."
    )
    return 0


CONTRAT = {
    "geste": "ADR depassee dont l encart n annonce pas ce qui est declare",
    "population": "les ADR de dev-docs/decisions",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_encart_de_revision.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
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
